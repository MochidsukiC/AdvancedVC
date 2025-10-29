# Calude Code 指示書 (Instructions for Agent)

## 基本的な指示 (Core Instructions)

- **言語**: すべてのやり取りは、必ず日本語で行うこと。 (All interactions must be conducted in Japanese.)
- **思考時の参照**: 本プロジェクトについての情報はDEV.mdに記載されています。思考時は、必ずこれを参照してください。
- **定期的なデバッグ**: 巨大な実装を行う際、Calude Codeには出来ないデバッグを途中で行う必要がある場合は、手順を示した上でユーザーにデバッグを要求してください。

## タスク開始時の指示
- **初期状態の確認**: DEV.mdを確認し、本プロジェクトの概要と状況を確認する。詳細は #プロジェクト管理とドキュメンテーション を確認すること。

## タスク終了時の指示
- **状態の更新を記録**: DEV.mdに忘れずに変更内容を記載し、本プロジェクトの概要と状況を更新する。 #プロジェクト管理とドキュメンテーション を確認すること。


## プロジェクト管理とドキュメンテーション (Project Management and Documentation)

- **目的**: このプロジェクトに関する情報（概要、仕様、進捗、課題）を一元的に管理し、異なるAIアシスタントセッション間や、他のAI（例：Gemini Code Assistant）との連携をスムーズにすることを目的とします。
- **記録ファイル**: プロジェクトのルートにある `DEV.md` ファイルを、常にプロジェクトのマスタードキュメントとして扱うこと。
- **記録のタイミング**: 私からの指示や会話の内容を分析し、以下の情報を `DEV.md` に **逐次、追記・更新** すること。特に、タスク完了時は、書き込みの必要がないかよく確認すること。
    - **仕様 (Specifications)**: 新しく決定した機能の仕様や、既存仕様の変更点。
    - **TODO**: これから実装すべきタスクや、修正が必要な項目。
    - **問題点 (Issues)**: 発見されたバグ、技術的な課題、検討が必要な事項。

### `DEV.md` への書き込みルール

- **簡潔性の維持**: `DEV.md`はプロジェクトの「現在の状態」を把握するためのものです。変更履歴や実装過程の詳細なログは記載せず、「プロジェクト仕様書」「現在の仕様」「問題」「TODO」の4つのセクションに必要な情報のみを簡潔にまとめてください。
- 変更点を追記する際は、Markdown形式で見やすく整理すること。
- 既存の内容と矛盾が生じないよう、常にファイル全体をコンテキストとして理解してから追記すること。
- 私から `DEV.md` への書き込み指示がなかった場合でも、会話の中から重要だと判断した情報は自律的に記録すること。

## Forge MOD開発における外部ライブラリの扱い方 (External Library Handling in Forge Mod Development)

ForgeGradleの開発環境（runClient）で外部ライブラリ（JAR）を使用する際の**正しい方法**：

### 問題点
- 通常の`implementation`や`runtimeOnly`だけでは、ForgeGradleのrunClient実行時にライブラリがクラスパスに含まれない
- JARファイルをmodsブロックのsourceとして追加しようとしても失敗する
- ForgeのモジュールクラスローダーがJAR内のクラスを認識しない

### 解決方法（参考: AutoEconomicManagementMod）

1. **`run/libs`ディレクトリを作成し、ライブラリをコピー**:
```gradle
task copyProcessingCore(type: Copy) {
    from files('libs/core-4.4.4.jar')
    into file('run/libs')
    doLast {
        println "Copied Processing core to run/libs for development environment"
    }
}
```

2. **sourceSetsでruntimeClasspathに`run/libs`を追加**:
```gradle
sourceSets {
    main {
        runtimeClasspath += configurations.runtimeClasspath
        // Explicitly add run/libs directory to classpath for development
        runtimeClasspath += files('run/libs')
    }
}
```

3. **実行前にコピータスクを実行**:
```bash
./gradlew forge:copyProcessingCore
./gradlew forge:runClient
```

### この方式の利点
- 開発環境（runClient）でもライブラリが正しく読み込まれる
- 本番環境（JAR配布）では従来通りjarタスクの設定で依存関係を含める
- ForgeGradleのクラスローダーと互換性がある
- ライブラリの競合が発生しにくい

### 注意事項
- `run/libs`ディレクトリは`.gitignore`に追加すること
- 各開発者が初回セットアップ時に`copyProcessingCore`タスクを実行する必要がある