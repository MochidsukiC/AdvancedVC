# Advanced VC - macOS セットアップガイド

## macOSでマイクを使用する方法

macOS 10.14 Mojave以降では、アプリケーションがマイクにアクセスする際に明示的な許可が必要です。

**重要**: Advanced VCは、ランチャー（Prism Launcher、Minecraft Launcher、など）に許可を付与すれば自動的に動作します。

## セットアップ手順（3ステップ）

### ステップ1: システム環境設定を開く

1. Apple メニュー > システム環境設定（またはシステム設定）
2. 「プライバシーとセキュリティ」を選択
3. 左側のメニューから「マイク」を選択

### ステップ2: ランチャーに許可を付与

右側のリストで、使用するランチャーを探してチェックを入れます：

- **「Prism Launcher」**（推奨）
- または **「Minecraft」**
- または **「Minecraft Launcher」**
- または **「java」**

**注意**: リストに表示されない場合は、一度Minecraftを起動してマイクを使用しようとすると、リストに表示される場合があります。

### ステップ3: Minecraftを再起動

1. ランチャーを完全に終了
2. 実行中のMinecraftプロセスも終了
3. ランチャーから再起動

### 動作確認

以下の方法で、マイクが正常に動作していることを確認できます：

1. **メニューバーのマイクインジケータ**
   - ゲーム中、メニューバー右上にオレンジ色のマイクアイコンが表示される
   - これが表示されれば、マイクが正常に動作しています

2. **ゲーム内の音声設定画面**
   - Kキーを押して音声設定画面を開く
   - 入力デバイスを選択
   - 話してみて、音声レベルバーが反応するか確認

## トラブルシューティング

### マイクが動作しない

#### 症状: メニューバーのマイクインジケータが表示されない

これは、macOSがマイクアクセスを許可していないことを意味します。

**解決方法**:
1. システム環境設定 > プライバシーとセキュリティ > マイク を開く
2. ランチャーにチェックが入っているか確認
3. チェックを入れていない場合は、チェックを入れる
4. Minecraftを完全に再起動

#### 症状: 音声レベルバーが動かない

**解決方法1: 入力デバイスを確認**
1. 音声設定画面（Kキー）を開く
2. 正しい入力デバイスが選択されているか確認
3. 別のデバイスに切り替えてみる

**解決方法2: マイク許可をリセット**

ターミナルで以下を実行（パスワードが必要）:
```bash
# すべてのマイク許可をリセット
sudo tccutil reset Microphone
```

その後、システム環境設定でランチャーに再度チェックを入れてください。

### ログを確認

問題が解決しない場合は、Minecraftのログを確認してください：

**ログの場所**:
- Prism Launcher: `~/Library/Application Support/PrismLauncher/instances/[インスタンス名]/.minecraft/logs/latest.log`
- 標準Launcher: `~/Library/Application Support/minecraft/logs/latest.log`

**確認すべきログメッセージ**:
```
[INFO] Detected macOS environment
[INFO] Opening microphone with buffer size: ...
[INFO] Microphone opened successfully
[INFO] Starting microphone...
[INFO] Microphone started successfully
[INFO] Microphone capture started
```

エラーの場合:
```
[ERROR] Failed to start microphone capture: ...
[ERROR] Microphone access failed on macOS
```

エラーログの内容を確認し、上記のトラブルシューティングを試してください。

## 技術的背景

### なぜ自動化できないのか？

macOS Sequoia (15.6)以降では、以下の理由により、MODのコードから自動的にマイク許可を取得することができません：

1. **プロセス識別の問題**
   - Minecraftは深くネストされたJavaバイナリから起動されます
   - macOSはこのプロセスを「通常のアプリケーション」として認識しません

2. **TCC（Transparency, Consent, and Control）の制限**
   - 別プロセスからの許可要求は、Minecraftプロセスに適用されません
   - 許可は、実際のマイクアクセス時（`microphone.open()`）にOSレベルでチェックされます

3. **業界標準のアプローチ**
   - **Simple Voice Chat (SVC)**: 手動設定を案内
   - **Plasmo Voice**: 手動設定を案内
   - **Advanced VC**: 同様のアプローチを採用

### Advanced VCの実装方針

- **事前の許可チェックを行わない**: 不要かつ有害（正常な初期化を阻害）
- **実際のマイク初期化を必ず実行**: `microphone.open()`/`microphone.start()`を呼び出す
- **macOSが自動的にチェック**: OSレベルで許可がチェックされ、許可されていれば動作、ダメなら例外が発生
- **例外発生時のみ案内**: `LineUnavailableException`が発生した場合のみ、詳細な案内を表示

この方式により、ランチャーに許可が付与されていれば、**自動的にマイクが動作**します。

## サポート

問題が解決しない場合は、以下の情報を含めてIssueを作成してください：

- macOSバージョン（ターミナルで `sw_vers` を実行）
- 使用しているランチャー（Prism Launcher、Minecraft Launcher、など）
- Minecraftバージョン
- Advanced VC MODバージョン
- `logs/latest.log`の該当部分
- システム環境設定のマイク許可リストのスクリーンショット

---

**最終更新**: 2025-11-13
**対象macOSバージョン**: macOS 10.14 Mojave以降（macOS 15.6 Sequoiaで動作確認済み）
**動作確認**: Prism Launcher + Advanced VC MODで正常動作を確認
