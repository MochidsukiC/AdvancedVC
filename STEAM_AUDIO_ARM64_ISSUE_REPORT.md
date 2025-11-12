# Steam Audio ARM64互換性問題 - 技術報告書

## エグゼクティブサマリー

Minecraft 1.20.1 Forge MOD「Advanced VC」において、Steam Audio v4.7.0をJNA（Java Native Access）経由で使用する際、ARM64 Mac環境（Apple Silicon M4）で`iplBinauralEffectApply()`呼び出し時にSEGFAULT（SIGSEGV）が発生する問題に直面しています。x64環境では正常に動作すると予想されますが、ARM64環境で6回以上の修正試行にも関わらず解決できていません。

## プロジェクト概要

**プロジェクト名**: Advanced VC
**目的**: Minecraft用高品質ボイスチャットMOD
**技術スタック**:
- Minecraft 1.20.1 / Forge 47.4.0
- Java 17 (OpenJDK 17.0.15, Microsoft Build)
- JNA 5.14.0 (Java Native Access)
- Steam Audio v4.7.0 (Valve Software)
- macOS 15.6 (Darwin 24.6.0) on Apple M4 (ARM64)

## 問題の詳細

### クラッシュ概要

**エラー**: `SIGSEGV (0xb)` - セグメンテーションフォルト
**発生箇所**: `libphonon.dylib+0x6b414`
**関数**: `api::CBinauralEffect::apply(IPLBinauralEffectParams*, IPLAudioBuffer*, IPLAudioBuffer*)+0x40`
**レジスタ状態**: `x8=0x0000000000000000` (NULL)
**原因**: NULLポインタアクセス (si_addr: 0x0000000000000008)

### スタックトレース

```
C  [libphonon.dylib+0x6b414]  api::CBinauralEffect::apply(IPLBinauralEffectParams*, IPLAudioBuffer*, IPLAudioBuffer*)+0x40
C  [jna6501402439482543661.tmp+0x1004c]  ffi_prep_closure_loc+0x15a4
C  [jna6501402439482543661.tmp+0xea18]  ffi_call+0x520
J  com.sun.jna.Native.invokeInt(Lcom/sun/jna/Function;JI[Ljava/lang/Object;)I
j  jp.houlab.mochidsuki.advancedvc.client.audio.AudioPlayerSteamAudio.lambda$mixPositionalAudio$2()V+602
j  jp.houlab.mochidsuki.advancedvc.client.audio.AudioPlayerSteamAudio.mixPositionalAudio([I)V+62
```

### 問題の核心

JNAで`IPLAudioBuffer`構造体の`data`フィールド（`float**`型、ポインタのポインタ）をARM64環境で正しく扱えていません。Steam Audioのネイティブコードが、このポインタをデリファレンスする際にNULLポインタにアクセスしています。

## 技術的詳細

### IPLAudioBuffer構造体（Steam Audio C API）

Steam Audio公式ドキュメントによる定義:

```c
typedef struct IPLAudioBuffer {
    IPLint32 numChannels;     // Number of audio channels (4 bytes)
    IPLint32 numSamples;      // Number of samples per channel (4 bytes)
    IPLfloat32** data;        // Array of channel pointers (8 bytes on ARM64)
} IPLAudioBuffer;
```

- **サイズ**: 16バイト (int32 + int32 + pointer64)
- **アライメント**: ARM64では8バイトアライメント
- **`data`**: `float**`型 = `float*`配列へのポインタ（チャンネルごとのサンプル配列）

### 現在のJNA実装

```java
@Structure.FieldOrder({"numChannels", "numSamples", "data"})
class IPLAudioBuffer extends Structure {
    public int numChannels;     // 4バイト (offset 0)
    public int numSamples;      // 4バイト (offset 4)
    public Pointer data;        // 8バイト (offset 8)

    public IPLAudioBuffer() {
        super(Structure.ALIGN_DEFAULT);
    }

    @Override
    public int size() {
        return 16; // int(4) + int(4) + Pointer(8)
    }
}
```

### iplAudioBufferAllocate()の使用

```java
// 入力バッファ（モノラル）
inBuffer = new IPLAudioBuffer();
inBuffer.numChannels = 1;
inBuffer.numSamples = 960; // 20ms @ 48kHz
inBuffer.write();

int result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(
    context,
    1,      // numChannels
    960,    // numSamples
    inBuffer.getPointer()
);

inBuffer.read(); // ネイティブから構造体を読み戻す
// この時点で inBuffer.data は有効なポインタ (例: native@0x1515c1800)
```

### iplBinauralEffectApply()の呼び出し

```java
// デバッグログ: data pointer=native@0x1515c1800（有効なアドレス）
int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectApply(
    source.binauralEffect,    // Pointer (有効)
    params,                   // IPLBinauralEffectParams (by-value)
    source.inBuffer.getPointer(),   // Pointer to IPLAudioBuffer
    source.outBuffer.getPointer()   // Pointer to IPLAudioBuffer
);
// ↑ ここでSEGFAULT発生
```

### クラッシュ時のレジスタ状態

```
x0=0x000000012b873940  (有効なアドレス)
x1=0x0000600001df6fc0  (有効なアドレス - IPLBinauralEffectParams)
x2=0x00006000155330b0  (有効なアドレス - inBuffer)
x3=0x00006000155330d0  (有効なアドレス - outBuffer)
x8=0x0000000000000000  (NULL) ← ここが問題
```

**推測**: Steam Audioのネイティブコード内で、`IPLAudioBuffer.data`（`float**`）をデリファレンスする際、実際には`NULL`が渡されているか、ARM64のABIとJNAのポインタ処理に不整合がある。

## これまでの試行錯誤（6回以上）

### 試行1: 引数定義の変更
- **変更**: `iplBinauralEffectApply()`の引数を`IPLAudioBuffer`（by-value）から`Pointer`（by-reference）に変更
- **結果**: クラッシュ継続

### 試行2: Structure.write()の追加
- **変更**: `iplBinauralEffectApply()`呼び出し前に`inBuffer.write()`と`outBuffer.write()`を追加
- **意図**: JNA構造体の変更をネイティブメモリに同期
- **結果**: クラッシュ継続

### 試行3: write()の削除、read()のみに戻す
- **変更**: `write()`を削除し、`read()`のみで構造体を読み取る
- **結果**: クラッシュ継続

### 試行4: デバッグログの追加
- **変更**: バッファポインタとフィールド値を詳細にログ出力
- **発見**: バッファは正しく割り当てられている（`data pointer=native@0x1515c1800`）が、クラッシュは継続
- **結果**: クラッシュ継続

### 試行5: read()の追加
- **変更**: `iplBinauralEffectApply()`直前に`inBuffer.read()`と`outBuffer.read()`を追加
- **意図**: ネイティブメモリから最新の構造体を読み直す
- **結果**: クラッシュ継続

### 試行6: 構造体レイアウトの明示的制御
- **変更**: `@Structure.FieldOrder`アノテーション、`ALIGN_DEFAULT`、`size()`オーバーライドを追加
- **意図**: ARM64のメモリレイアウトとアライメントを明示的に制御
- **結果**: クラッシュ継続（x8レジスタが依然としてNULL）

## 仮説

### 仮説1: float**のポインタのポインタ処理の問題
JNAの`Pointer`型は単一レベルのポインタ（`void*`）を表現できますが、`float**`（ポインタのポインタ）をARM64環境で正しく扱えていない可能性があります。

**理由**:
- `iplAudioBufferAllocate()`でバッファを割り当てた後、`data`ポインタは有効なアドレスを指している（ログで確認済み）
- しかし、Steam Audioのネイティブコードが`data`をデリファレンスすると`NULL`が得られる
- これは、JNAが`data`ポインタを正しくネイティブメモリに書き込んでいないか、ARM64のABIに従っていない可能性を示唆

### 仮説2: ARM64 ABIとJNAの不整合
ARM64 Procedure Call Standard (AAPCS64)では、構造体の渡し方に特定のルールがあります。JNAがこれに完全に準拠していない可能性があります。

**特に問題になり得る点**:
- 構造体のアライメント（8バイト境界）
- 構造体内のパディング
- ポインタのエンディアン（ARM64はリトルエンディアン）

### 仮説3: iplAudioBufferAllocate()の不適切な使用
Steam Audio公式ドキュメントを確認すると、`iplAudioBufferAllocate()`の第4引数は`IPLAudioBuffer*`（構造体へのポインタ）です。JNAで`Structure.getPointer()`を使用していますが、ARM64環境でこれが正しく動作していない可能性があります。

## 調査依頼事項

Gemini DeepResearchに以下の調査を依頼します：

### 1. Steam Audio + JNA + ARM64の先行事例
- **質問**: Steam Audioを**JNA経由**でARM64/Apple Silicon環境で使用した事例はあるか？
- **成功事例**: GitHubリポジトリ、フォーラム投稿、ブログ記事など
- **失敗事例**: 同様の問題に遭遇した報告と解決策（または回避策）

### 2. JNAでfloat**（ポインタのポインタ）を扱う方法
- **質問**: JNAで`float**`や`void**`など、ポインタのポインタを正しく扱う方法は？
- **ARM64での制約**: ARM64環境特有の制約や、x64との違い
- **代替手法**: `Structure`ではなく`Memory`クラスを使った手動メモリ管理の実装例

### 3. Steam Audio公式Java/Kotlinバインディング
- **質問**: Valve SoftwareまたはコミュニティによるSteam Audio公式Javaバインディングは存在するか？
- **JNI vs JNA**: JNI（Java Native Interface）を使った実装例
- **JavaCPP**: JavaCPPを使ったSteam Audioバインディングの事例

### 4. ARM64 ABIとJNAの互換性
- **質問**: JNA 5.xはARM64 Procedure Call Standard (AAPCS64)に完全対応しているか？
- **既知の問題**: JNAのGitHub IssuesやStackOverflowでのARM64関連の報告
- **代替ライブラリ**: JNAの代わりにJNI、JNR-FFI、Panama Foreign Function & Memory APIなどの選択肢

### 5. 類似プロジェクトの調査
- **質問**: Minecraftや他のJavaアプリケーションで、Steam Audioを使用した3D音響を実装している事例は？
- **Sound Physics Remastered**: OpenAL EFXを使用（Steam Audioではない）
- **他のMOD**: Simple Voice ChatなどのボイスチャットMODがSteam Audioを使用しているか？

### 6. デバッグ手法
- **質問**: JNAとネイティブライブラリの境界でのメモリ問題をデバッグする推奨手法は？
- **ツール**: lldb、Instruments、JNA debugging options
- **回避策**: Steam Audioの代わりに、OpenAL EFX、Google Resonance Audio、Miniaudioなどの代替ライブラリの比較

## 現在の回避策

ARM64環境では、システムアーキテクチャを自動検出し、Steam Audioの代わりに**OpenAL EFX**（業界標準の3D音響システム）を使用しています。

```java
private static boolean isArmArchitecture() {
    String arch = System.getProperty("os.arch", "").toLowerCase();
    return arch.contains("arm") || arch.contains("aarch");
}

public boolean useSteamAudio = !isArmArchitecture(); // ARM64ではfalse
```

**OpenAL EFXの特徴**:
- ✅ ARM64で完全動作
- ✅ 商用品質の3D音響とリバーブ
- ✅ 環境に応じた動的リバーブ
- ✅ オクルージョン・回折対応

ただし、ユーザーはSteam AudioのHRTF（Head-Related Transfer Function）による高品質バイノーラル再生を希望しており、可能であればARM64環境でもSteam Audioを使用したいと考えています。

## 期待される成果

1. **ARM64環境でSteam Audioを動作させる具体的な実装方法**
2. **JNAの代替（JNI、JavaCPP、Panama FFMなど）の実装例**
3. **同様の問題に遭遇した先行事例と解決策**
4. **技術的に不可能な場合の明確な理由と、実用的な代替案**

## 添付資料

### ファイルリスト
- `src/main/java/jp/houlab/mochidsuki/advancedvc/client/audio/steamaudio/SteamAudioLibrary.java` - JNA APIバインディング
- `src/main/java/jp/houlab/mochidsuki/advancedvc/client/audio/AudioPlayerSteamAudio.java` - Steam Audio統合コード
- `hs_err_pid7399.log` - JVMクラッシュレポート
- `DEV.md` - プロジェクト開発ドキュメント

### 参考リンク
- Steam Audio GitHub: https://github.com/ValveSoftware/steam-audio
- Steam Audio C API: https://valvesoftware.github.io/steam-audio/doc/capi/
- JNA GitHub: https://github.com/java-native-access/jna
- AAPCS64 (ARM ABI): https://github.com/ARM-software/abi-aa/blob/main/aapcs64/aapcs64.rst

---

**作成日**: 2025-11-12
**作成者**: Advanced VC開発チーム
**対象**: Gemini DeepResearch調査依頼
