# Steam Audio Java Bindings Test

このディレクトリには、Steam Audio Javaバインディングの統合テストが含まれています。

## テスト内容

`SteamAudioBindingsTest.java`は以下のテストを実行します：

1. **Native Library Loading** - ネイティブライブラリの読み込みテスト
2. **Context Initialization** - Contextの初期化と解放
3. **HRTF Creation** - HRTFの作成と解放
4. **Binaural Effect** - バイノーラルエフェクトの作成と解放
5. **Audio Processing** - 実際の音声処理（440Hz サイン波）
6. **Scene/Mesh API** - Scene/MeshのAPI基本テスト
7. **Audio Buffer Operations** - オーディオバッファの読み書きテスト
8. **New Effects** - 新しく追加されたエフェクト（Panning等）のテスト

## テスト実行方法

### 前提条件

- Steam Audio v4.7.0のネイティブライブラリが必要
- Windows、macOS、またはLinux環境

### 実行手順

#### 方法1: Gradleから実行

プロジェクトルートで以下を実行：

```bash
# Windowsの場合
./gradlew.bat :runSteamAudioTest

# Linux/macOSの場合
./gradlew :runSteamAudioTest
```

#### 方法2: 直接Javaで実行

テストクラスをコンパイルして実行：

```bash
# コンパイル
javac -cp "build/classes/java/main:lib/*" \
  src/test/java/jp/houlab/mochidsuki/advancedvc/client/audio/steamaudio/SteamAudioBindingsTest.java

# 実行（ネイティブライブラリのパスを指定）
java -Djna.library.path=/path/to/steamaudio/lib \
  -cp "build/classes/java/main:src/test/java:lib/*" \
  jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio.SteamAudioBindingsTest
```

#### 方法3: IDEから実行

IntelliJ IDEAやEclipseで`SteamAudioBindingsTest.java`を開き、`main`メソッドを実行します。

**重要**: ネイティブライブラリのパスを設定してください：
- VM Options: `-Djna.library.path=/path/to/steamaudio/lib`

## 期待される出力

すべてのテストが成功した場合：

```
=================================================
  Steam Audio Java Bindings Integration Test
=================================================

[TEST 1] Native Library Loading
  [✓] Native library loaded successfully

[TEST 2] Context Initialization
  [✓] Context created successfully
  [✓] Context released successfully

[TEST 3] HRTF Creation
  [✓] HRTF created successfully
  [✓] HRTF released successfully

[TEST 4] Binaural Effect
  [✓] Binaural effect created successfully
  [✓] Binaural effect released successfully

[TEST 5] Audio Processing (Sine Wave)
  [✓] Generated 440Hz sine wave input
  [✓] Binaural effect applied successfully
  [✓] Output contains non-zero samples (audio processed)
  Left channel energy:  XX.XXXXXX
  Right channel energy: XX.XXXXXX
  [✓] Spatial positioning correct (right > left)

[TEST 6] Scene/Mesh API (Basic)
  [✓] Scene created successfully
  [✓] Scene committed successfully
  [✓] Scene released successfully

[TEST 7] Audio Buffer Operations
  [✓] Write interleaved data to buffer
  [✓] Read interleaved data from buffer
  [✓] Data integrity verified (write/read match)

[TEST 8] New Effects API (Panning)
  [✓] Panning effect created successfully
  [✓] Panning effect released successfully

=================================================
  Test Summary
=================================================
Tests Passed: XX
Tests Failed: 0
Total Tests:  XX

Result: ALL TESTS PASSED ✓
```

## トラブルシューティング

### ネイティブライブラリが見つからない

**エラー**: `UnsatisfiedLinkError` または `Failed to load native library`

**解決策**:
1. Steam Audioのネイティブライブラリが正しい場所にあることを確認
2. `jna.library.path`システムプロパティを設定
3. または環境変数を設定：
   - Windows: `PATH`に`phonon.dll`のディレクトリを追加
   - Linux: `LD_LIBRARY_PATH`に`libphonon.so`のディレクトリを追加
   - macOS: `DYLD_LIBRARY_PATH`に`libphonon.dylib`のディレクトリを追加

### ARM64 (Apple Silicon) での注意

ARM64環境（Apple Silicon Mac等）では、Steam Audio v4.7.0に既知のバグがあります。
テストが失敗する場合があります。詳細はDEV.mdを参照してください。

### Context作成失敗

**エラー**: `iplContextCreate failed with status: X`

**原因**:
- ネイティブライブラリのバージョン不一致
- Steam Audioの初期化エラー

**解決策**:
- Steam Audio v4.7.0を使用していることを確認
- ライブラリファイルが破損していないか確認

## テストコードの拡張

新しいテストケースを追加する場合は、以下のパターンに従ってください：

```java
private static void testYourNewFeature() {
    System.out.println("[TEST X] Your New Feature");

    try {
        // テストコード

        if (success) {
            pass("Test passed message");
        } else {
            fail("Test failed message");
        }
    } catch (Exception e) {
        fail("Exception: " + e.getMessage());
        e.printStackTrace();
    }
    System.out.println();
}
```

そして`main`メソッドに追加：

```java
public static void main(String[] args) {
    // ... existing tests ...
    testYourNewFeature();
    // ... summary ...
}
```

## ライセンスと免責事項

このテストコードは、Steam Audio Javaバインディングの動作確認のみを目的としています。
Steam Audioは[Valve Corporation](https://valvesoftware.github.io/steam-audio/)によって開発されています。
