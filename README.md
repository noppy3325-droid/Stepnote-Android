# Stepnote

### シンプルな Android 歩数計

Stepnote は今日の歩数を大きく見やすく表示する歩数計です。スマホを持って歩けば、目標までの進み具合や過去の記録も確認できます。

スマートウォッチやアカウント登録は必要ありません。散歩や通勤でどれくらい歩いたかを、気軽に振り返れます。

[APK をダウンロード](https://github.com/noppy3325-droid/Stepnote-Android/releases/download/v0.1.1/Stepnote-v0.1.1-debug.apk) · [リリース一覧](https://github.com/noppy3325-droid/Stepnote-Android/releases) · [検証レポート](TEST_REPORT.md)

## できること

- 今日の歩数と目標までの進捗を大きく表示
- 日・週・月・年のグラフとカレンダーで歩数を確認
- 散歩など、好きな区間の歩数をスタートからストップまで計測
- 身長と体重から移動距離と消費カロリーを推定
- ホーム画面のウィジェットで歩数と目標への進捗を確認
- ライト・ダーク・システム設定に合わせた表示

歩数や設定、入力した身長・体重は端末内に保存されます。Stepnote はアカウント登録やインターネット通信権限を必要としません。

## はじめる

Android 14 以降のスマートフォンで使えます。

1. [v0.1.1 の APK](https://github.com/noppy3325-droid/Stepnote-Android/releases/download/v0.1.1/Stepnote-v0.1.1-debug.apk) をダウンロードしてインストールします。
2. Stepnote を開き、歩数の読み取りを許可します。
3. スマホを持って歩くと、今日の画面に記録が表示されます。

配布中の APK はデバッグ版です。インストール時に確認画面が表示されたら、ダウンロードに使ったブラウザやファイルアプリからのインストールを許可してください。

## 歩数について

初期設定では Health Connect が記録した、このスマホの歩数を表示します。設定から Health Connect の全対応データや、Google Fit が Health Connect に共有した歩数も選べます。Google Fit を使う場合は、Fit の設定で Health Connect との同期を有効にしてください。

歩数の反映には時間がかかる場合があります。取得元や同期のタイミングによって、Google Fit などの表示と差が出ることがあります。区間計測も、歩数データが反映された後に更新できます。

バックグラウンドでの読み取りは、対応端末で追加の許可を設定できます。端末の省電力設定や強制停止によって、同期やウィジェットの更新が遅れる場合があります。実機での継続計測と歩数精度は引き続き検証中です。詳しくは [検証レポート](TEST_REPORT.md) をご覧ください。

推定歩幅は身長の約 41.5% です。消費カロリーは「距離（km）× 体重（kg）× 0.55」で概算しています。

## 開発する

Kotlin と Jetpack Compose を使った Android ネイティブアプリです。データ取得に Health Connect と Recording API on mobile、端末内の保存に Room と DataStore、定期更新に WorkManager、ウィジェットに Jetpack Glance を使用しています。

**必要な環境**

- JDK 17 または 21
- Android SDK Platform 37.0
- Android Studio Panda 3 Patch 1 以降
- Android 14（API 34）以降の端末

Android Studio でこのリポジトリを開くか、プロジェクトのルートで実行してください。

```bash
# macOS / Linux
./gradlew assembleDebug testDebugUnitTest

# Windows PowerShell
.\gradlew.bat assembleDebug testDebugUnitTest
```

生成された APK は `app/build/outputs/apk/debug/app-debug.apk` にあります。USB デバッグを許可した端末には `adb install -r app/build/outputs/apk/debug/app-debug.apk` でインストールできます。実機テストは `connectedDebugAndroidTest` で実行できます。

Windows で日本語を含むパスから単体テストを実行すると、Gradle のテストワーカーがクラスを見つけられない場合があります。その場合は半角英数字のパスを使うか、PowerShell で空いているドライブ文字を一時的に割り当ててください。

```powershell
subst S: (Get-Location).Path
Push-Location S:\
try { .\gradlew.bat assembleDebug testDebugUnitTest } finally { Pop-Location; subst S: /D }
```

## フィードバック

使ってみて気になったことや、あったらうれしい機能は [Issues](https://github.com/noppy3325-droid/Stepnote-Android/issues) で教えてください。
