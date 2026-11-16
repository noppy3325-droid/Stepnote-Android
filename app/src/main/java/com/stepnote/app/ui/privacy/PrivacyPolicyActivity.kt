package com.stepnote.app.ui.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepnote.app.ui.theme.LocalStepnoteColors
import com.stepnote.app.ui.theme.StepnoteTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StepnoteTheme {
                val colors = LocalStepnoteColors.current
                Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Stepnote プライバシーポリシー",
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = """
                                1. 外部送信を行いません
                                Stepnote はインターネット通信権限を持たず、歩数・設定・体重などのデータを開発者や第三者のサーバーへ一切送信しません。

                                2. 使用する権限とその目的
                                ・READ_STEPS: Health Connect から歩数を読み取り、日次・時間帯別の集計を表示するためのみに使用します（書き込み権限 WRITE_STEPS は要求しません）。
                                ・READ_HEALTH_DATA_IN_BACKGROUND: 画面を開いていない間もホーム画面ウィジェットの表示を更新するために使用します（任意）。
                                ・ACTIVITY_RECOGNITION: Recording API フォールバック使用時に端末の歩数変化を購読するためのみに使用します。

                                3. ローカルキャッシュと削除
                                グラフやウィジェットの高速表示のため、集計結果を端末内の Room データベースにキャッシュします。設定画面の「ローカルキャッシュと設定を削除」を実行するとアプリ内のキャッシュはすべて消去されます。Health Connect 本体のデータは無断で削除しません。
                            """.trimIndent(),
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { finish() }) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }
}
