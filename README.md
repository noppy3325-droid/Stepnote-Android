# Stepnote

Stepnote は Android 14 以降で動く、スマートフォン向けの歩数計です。今日の歩数、日・週・月・年の履歴、月間カレンダー、ホーム画面ウィジェットを表示します。Kotlin、Compose、Room、DataStore、WorkManager、Jetpack Glance を使っています。

## APK をダウンロード

[Stepnote v0.1.1 の APK](https://github.com/noppy3325-droid/Stepnote-Android/releases/download/v0.1.1/Stepnote-v0.1.1-debug.apk) を Android 端末にダウンロードして開くとインストールできます。端末で確認画面が出た場合は、ダウンロードに使ったブラウザやファイルアプリからのインストールを許可してください。

配布ファイルはデバッグ版です。バージョンごとの動作確認の範囲は [検証レポート](TEST_REPORT.md) を参照してください。

## ビルドとインストール

JDK 17 または 21 と Android SDK Platform 37.0 を用意してください。Android Studio Panda 3 Patch 1 以降でこのフォルダーを開けます。Gradle Wrapper は 9.3.1、Android Gradle Plugin は 9.1.1、アプリの最小 SDK は 34、対象 SDK は 36 です。

Windows の PowerShell でプロジェクトのルートから実行します。SDK は Android Studio または環境変数 `ANDROID_HOME` で指定できます。

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

macOS と Linux では `./gradlew assembleDebug testDebugUnitTest` を使います。Debug APK のアプリ ID は `com.stepnote.app.debug` です。実機を USB 接続し、端末上で USB デバッグを許可してください。HyperOS で USB インストールの確認が表示された場合は、端末上で許可してください。

日本語を含む Windows のパスで単体テストが `ClassNotFoundException` になる場合は、半角英数字のパスからビルドしてください。このフォルダーのまま実行するには、空いているドライブ文字を使って一時的に割り当てます。

```powershell
subst S: (Get-Location).Path
Push-Location S:\
try { .\gradlew.bat assembleDebug testDebugUnitTest } finally { Pop-Location; subst S: /D }
```

実機の追加テストは `.\gradlew.bat connectedDebugAndroidTest` で実行できます。キャッシュのテストにはメモリ上のデータベースを使います。端末の Health Connect 集計テストは、対応端末で歩数とバックグラウンド読み取りの両方を許可済みの場合に実行し、端末の歩数値はテスト結果に出力しません。

## 記録方式と権限

- **このスマホのみ** が初期設定です。Health Connect が利用でき、Android 14 の SDK Extension が 22 以上の場合は、公開 API でこの端末の識別子を取得します。現在の端末識別子と従来の `android` 起源で `StepsRecord.COUNT_TOTAL` を集計します。自動計測そのものの要件は Extension 20 以上です。 [Android Developers の歩数取得ガイド](https://developer.android.com/health-and-fitness/health-connect/features/steps)
- **Health Connect のすべての対応データ** を設定で選ぶと、Health Connect の優先順位と重複排除が適用された集計値を表示します。設定変更後は選んだ取得元ごとの履歴に切り替わります。 [Health Connect の集計仕様](https://developer.android.com/health-and-fitness/health-connect/aggregate-data)
- **Google Fit の記録** を設定で選ぶと、Google Fit がヘルスコネクトに書き込んだ歩数のみを表示します。Google Fit のプロフィール → 設定 → ヘルスコネクトとの同期を有効にしてください。Fit アプリに表示される全ソースや未同期の歩数とは差が出ることがあります。 [Google Fit の同期手順](https://support.google.com/fit/answer/12830119?hl=ja)
- **Recording API on mobile** は端末の識別 API が使えない場合の代替です。対応する Google Play 開発者サービスと「身体活動」権限が必要です。購読開始後の直近約 10 日が API の読み取り範囲です。取得した履歴は端末内に保存します。 [Recording API on mobile](https://developer.android.com/health-and-fitness/recording-api)
- Health Connect の歩数読み取りを許可すると、対応端末で OS の収集が始まります。「バックグラウンド読み取り」は対応端末に限り、設定から追加で許可できます。前景への復帰と再読み込みは歩数読み取りだけで利用できます。追加許可がある場合や Recording API を使う場合は WorkManager が約 30 分間隔で当日と前日のキャッシュ更新を試みます。
- 歩数、設定、入力した体重は端末内に保存します。設定からキャッシュと設定を削除できます。OS 側の権限は端末設定で管理できます。Health Connect にある元データは削除後も残るため、再同期で表示に戻る場合があります。

## 計測と表示の制限

- 歩数の反映には収集元の処理に応じた遅延があります。実測の確認ではスマホを持って歩き、しばらく待ってから「今日」の更新ボタンを押します。
- Health Connect の標準権限で読める履歴は最初の権限付与の約 30 日前からです。追加の過去履歴権限は要求していません。取得済みの日だけで日平均を計算します。
- 日ごとの合計はローカル日付の境界で集計します。時間帯グラフは取得元の 1 時間バケットをローカル時刻に割り当てるため、時計が 30 分など 1 時間未満ずれる地域の切り替え日は、時間帯ごとの境界がずれることがあります。
- ウィジェットには最後に同期した当日の歩数を表示します。OS の省電力制御や強制停止により定期実行が遅れたり、止まったりする場合があります。権限の取り消しやセンサーのない端末でも収集が停止します。Xiaomi 端末で記録が止まった場合はアプリ内の診断から権限と対応状況を確認できます。
- 設定の「身長・体重」で入力を保存できます。推定距離は、歩数に身長の約 41.5% の歩幅を掛けて求めます。身長が未入力なら設定した歩幅を使います。体重を保存すると、推定消費カロリーを距離（km）× 体重（kg）× 0.55 で概算します。
- 「今日」の区間計測では、スタートとストップを押した時刻に基づいて、開始時に選ばれていた取得元の歩数を読み取ります。計測中や終了後に再取得でき、次の計測を始めるまで直前の結果を端末内に保存します。短い区間は取得元の記録間隔や同期遅延による誤差が出やすく、即時に結果が出ない場合があります。
- 実機で確認した範囲は [TEST_REPORT.md](TEST_REPORT.md) を参照してください。

## 次の改善候補

データの CSV エクスポート、文字サイズと読み上げの検証拡充、長期間の実機計測による電池消費と歩数精度の評価。
