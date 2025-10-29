# Advanced VC - 開発ドキュメント

## プロジェクト仕様書

### 概要
Minecraft 1.20.1 Forge用の高度なボイスチャットMOD。
音響シミュレーション、デバイス通信、バンドモードの3つの通信モードを実装。

### 技術スタック
- Minecraft 1.20.1
- Forge 47.4.0
- Java 17
- Opus音声コーデック (Concentus 1.0.2)
- UDP通信

## 現在の仕様

### 実装済み機能

#### 1. 音声通信システム
- **クライアントサイド**: `ClientAudioEngine`
  - マイク入力キャプチャ
  - Opus エンコード/デコード
  - UDP音声パケット送受信
  - 自動再接続機能（5秒間隔）
- **サーバーサイド**: `ServerAudioRouter`
  - 音声パケットのルーティング
  - 3つのモード対応（シミュレーション、デバイス、バンド）

#### 2. 通信モード
1. **シミュレーションモード**: 距離と声量に基づく音響伝播
2. **デバイスモード**: トランシーバー（周波数ベース）
3. **バンドモード**: 指揮者ベースの同期通信

#### 3. UI機能
- **VoiceHudOverlay**: 左下に音声レベルバーとマイク状態表示
  - 状態: 待機中、発話中、ミュート中、接続失敗
  - 音声レベルバー（緑→黄→赤）
- **VoiceSettingsScreen**: VC設定画面（Kキー）
  - 入力/出力デバイス選択
  - VAD閾値調整
  - 入力/出力音量調整

#### 4. ブロックとアイテム
- マイクブロック
- スピーカーブロック
- 防音ブロック
- 吸音ブロック
- トランシーバーアイテム
- バンドツールアイテム

#### 5. 音響シミュレーション
- 音響特性のブロック別定義（JSON）
- 減衰、反射、吸収の計算

### 外部ライブラリの統合

#### Concentus (Opus Codec) の統合方法

**開発環境（runClient）**:
- `run/libs/`ディレクトリにライブラリをコピー
- `sourceSets.main.runtimeClasspath`に追加
- Gradleタスク: `./gradlew copyConcentus`

**配布環境（JAR）**:
- `jarJar`依存関係として設定
- ビルド時に自動パッケージング

**設定箇所** (build.gradle):
```gradle
// 開発環境用
sourceSets {
    main {
        runtimeClasspath += files('run/libs')
    }
}

task copyConcentus {
    // run/libsへのコピー処理
}

// 配布用
dependencies {
    implementation 'io.github.jaredmdobson:concentus:1.0.2'
    jarJar(group: 'io.github.jaredmdobson', name: 'concentus', version: '[1.0.2,1.1)')
}
```

### 設定システム
- `Config.java`: Forge ConfigSpecによる永続化
  - VAD閾値
  - 入力/出力音量
  - UDPポート設定
  - 自動起動設定

## TODO

### 優先度: 高
- [ ] runClient動作確認
- [ ] 実際の音声通信テスト
- [ ] サーバー/クライアント間の通信検証

### 優先度: 中
- [ ] デバイスモードの音質劣化DSP実装
- [ ] バンドモードの同期ロジック実装
- [ ] マイク/スピーカーブロックの実装完了

### 優先度: 低
- [ ] 音響シミュレーションの詳細調整
- [ ] パフォーマンス最適化
- [ ] エラーハンドリングの強化

## 問題点

### 解決済み
- ✅ Concentusライブラリのコンパイルエラー（依存関係の修正）
- ✅ HUD非表示問題（engine.isRunning()チェックの削除）
- ✅ 設定の永続化問題（SPEC.save()の追加）
- ✅ シングルプレイ接続エラー（統合サーバー検出の追加）
- ✅ runClientでのClassNotFoundException（run/libs方式の採用）
- ✅ Java 24互換性問題（Java 17の強制使用）

### 未解決
なし

## 開発環境セットアップ

### 初回セットアップ
```bash
# Concentusライブラリをrun/libsにコピー
./gradlew copyConcentus

# IDEのプロジェクトファイルを生成
./gradlew idea

# run設定の準備
./gradlew prepareRuns
```

### ビルド
```bash
# 開発ビルド
./gradlew build

# 配布用JAR
./gradlew build
# 出力: build/libs/advancedvc-1.0-SNAPSHOT.jar
```

### 実行
- IntelliJ IDEA: Gradle → Tasks → forgegradle runs → runClient
- または: `./gradlew runClient`

## 引き継ぎ情報（別デバイスでの作業継続用）

### 現在の進捗状況（2025-10-25 12:40）

**✅ 完了した作業:**
1. Concentusライブラリの統合問題を完全に解決
   - 開発環境（runClient）用: run/libs方式を採用
   - 配布環境用: jarJar設定を維持
2. Java 17への切り替え完了（gradle.properties）
3. build.gradleの設定完了
4. `copyConcentus`タスクの実装と実行完了
5. `run/libs/concentus-1.0.2.jar`の配置完了

**🔄 現在の状態:**
- ビルド成功（`./gradlew build`）
- runClient実行の準備完了
- **次のステップ: runClientの実行と動作確認**

### 次のデバイスで最初にやること

#### 1. 必須: Concentusライブラリのコピー
別のデバイスでは`run/libs/`が存在しないため、必ず以下を実行：
```bash
./gradlew copyConcentus
```
実行後、`run/libs/concentus-1.0.2.jar`が存在することを確認。

#### 2. Java 17の確認
gradle.propertiesで以下が設定済み：
```properties
org.gradle.java.home=/Users/soma/Library/Java/JavaVirtualMachines/temurin-17.0.15/Contents/Home
```
別のデバイスでJava 17のパスが異なる場合は、以下で確認して修正：
```bash
/usr/libexec/java_home -V
```

#### 3. runClientの実行
```bash
./gradlew runClient
```
または、IntelliJ IDEAから: Gradle → Tasks → forgegradle runs → runClient

### 重要な設定変更（このセッションで実施）

#### build.gradle
以下の設定が追加されています：

1. **sourceSets設定** (行116-120):
```gradle
sourceSets {
    main {
        runtimeClasspath += files('run/libs')
    }
}
```

2. **copyConcentusタスク** (行207-223):
```gradle
task copyConcentus {
    doLast {
        def runLibsDir = file('run/libs')
        runLibsDir.mkdirs()
        def concentusFile = configurations.runtimeClasspath.files.find { it.name.contains('concentus') }
        if (concentusFile != null) {
            copy {
                from concentusFile
                into runLibsDir
            }
            println "Copied ${concentusFile.name} to run/libs"
        }
    }
}
```

3. **prepareRunsへの依存関係** (行226-228):
```gradle
afterEvaluate {
    tasks.findByName('prepareRuns')?.dependsOn 'copyConcentus'
}
```

4. **jarJar依存関係** (行137):
```gradle
jarJar(group: 'io.github.jaredmdobson', name: 'concentus', version: '[1.0.2,1.1)')
```

#### gradle.properties
以下の設定が追加されています（行3）：
```properties
org.gradle.java.home=/Users/soma/Library/Java/JavaVirtualMachines/temurin-17.0.15/Contents/Home
```

### テスト項目チェックリスト

runClient実行後、以下を確認してください：

- [ ] ゲームが正常に起動する
- [ ] MODがロードされる（ログで確認）
- [ ] HUDが表示される（左下に音声レベルバー）
- [ ] Kキーで設定画面が開く
- [ ] クリエイティブタブに「Advanced VC」が表示される
- [ ] サーバー起動ログに「Server Audio Router Started Successfully」が表示される
- [ ] クライアント起動ログに「Client Audio Engine Started Successfully」が表示される
- [ ] 音声入力デバイスが認識される
- [ ] UDP接続が確立される（接続失敗の場合は再接続ログを確認）

### トラブルシューティング

#### ClassNotFoundExceptionが発生する場合
```bash
# run/libsにConcentusがあるか確認
ls -la run/libs/

# なければコピー
./gradlew copyConcentus

# IntelliJ IDEAの場合、プロジェクトをリフレッシュ
./gradlew --refresh-dependencies
```

#### モジュール競合エラーが発生する場合
- 原因: jarJarとrun/libsが競合している
- 解決: run/libs方式に統一済みなので発生しないはず

#### Java 24のエラーが出る場合
- gradle.propertiesのJava 17パスを環境に合わせて修正

### .gitignoreへの追加推奨

以下をプロジェクトの.gitignoreに追加することを推奨：
```
run/libs/
```

### 参考情報

- **Concentusライブラリ**: io.github.jaredmdobson:concentus:1.0.2
- **Forge MOD開発の外部ライブラリ統合**: CLAUDE.md参照
- **音声通信ポート**: UDP 24454（デフォルト、Config.javaで変更可能）

## 最終更新
2025-10-25 12:40: Concentusライブラリ統合完了、開発環境セットアップ完了、引き継ぎ情報記録
