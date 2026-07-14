# Hop Dialog Constructor Verifier Plugin
## ⬇️ ダウンロード (Download)

| 📦 配布パッケージ (Plugin Package)                                                                                           | 🛠️ 対応環境 (Target IDE) |
|:----------------------------------------------------------------------------------------------------------------------| :--- |
| [**Download Latest ZIP**](https://github.com/yasuoz/hop-migration-assistant/raw/refs/heads/main/dist/hop-migration-assistant-1.0.0.zip) | IntelliJ IDEA 2025.2.x 〜 2026.x (Java 21+) |
同じZIPファイルを、本プロジェクトをPULLしてbuildPluginすることでも生成できます。

### 🛠️ インストール手順 (Installation)
1. IntelliJ IDEAのメニューから **ファイル (File)** ➔ **⚙設定 (Settings)** (Macは環境設定) を開きます。
2. 左側メニューから **プラグイン (Plugins)** を選択します。
3. 画面中央上部にある **歯車マーク(⚙️)** のアイコンをクリックし、メニューから **🔌ディスクからプラグインをインストール... (Install Plugin from Disk...)** を選択します。
4. ビルドされた**ZIP ファイル**を選択して「OK」を押します。
5. **IntelliJ IDEAを再起動** します。

---

Apache Hop 2.xのプラグイン開発において、ダイアログクラス(`IActionDialog` / `ITransformDialog`)が満たすべき**規定のコンストラクター引数および専用フィールドを自動検証・一発自動生成する**ためのIntelliJ IDEAプラグインです。

Dialogのコンストラクタは実行時に初めてNoSuchMethodExceptionが発生して気づく構造のため、エディタ上で警告と修正案を提示することでコーディングを支援します。

---

## ✨ 主な機能

- 🎬 **ActionDialog検証**: `Shell`, `具象Action`, `WorkflowMeta`, `IVariables` の厳格な並び順を検証。
- 🔄 **TransformDialog検証**: `Shell`, `IVariables`, `具象Meta`, `PipelineMeta` の厳格な並び順を検証。
- 💡 **Quick Fix**: クラス名に波線警告が出ます。`Alt + Enter` を押すことで、**「既存の変数定義(フィールド)のすぐ後ろ、かつメソッドの直前」**の位置へコンストラクタを自動生成します。

---

## 🚀 ビルドとインストール手順(開発者向け)

本プロジェクトは **完全ローカル環境自動引用型(Git共有可能仕様)** で組まれているため、特別なローカルパスの書き換えや重いSDKのダウンロード待ちをすることなく、以下の手順で即座にビルド・導入が可能です。

### 1. プラグインのビルド (ZIP書き出し)
プロジェクトをIntelliJ IDEAで開き、画面右端の `Gradle` タブ、またはターミナルから以下のビルドタスクを実行します。

```bash
./gradlew buildPlugin
```

ビルドが成功すると、distディレクトリにインストール用のZIPパッケージが自動生成されます。
📁 [hop-migration-assistant-0.0.1.zip](dist) (バージョン名は環境による)

## 🛠️ 開発時の注意点

本リポジトリはJetBrains公式の最新プラグインテンプレートをベースに構築されています。
将来的にJetBrains側がビルドツール（`org.jetbrains.intellij.platform`）の仕様や Gradle バージョン、最新の Java 互換（Java 21以降）のアップデートを配給した際は、以下のGitコマンドでいつでも本家の最新状態を合流(Sync)できます。

```bash
# 最初の一度だけ、本家を上流(upstream)として登録
git remote add upstream https://github.com/JetBrains/intellij-platform-plugin-template.git

# 本家の最新アップデートを自環境へマージ(追随)する場合
git fetch upstream
git merge upstream/main
```

## 🛠️ 開発時のメモ
標準出力にデバッグ情報を出力した場合、ログは以下のところに書き出されています。
# 【テスト環境用】プロジェクトフォルダ直下のサンドボックス内に生成されます
%PROJECT_ROOT%\build\idea-sandbox\system\log\idea.log
# 【本番環境用】ユーザーフォルダー階層内に生成されます
%LOCALAPPDATA%\JetBrains\IntelliJIdea2025.3\log\idea.log
