package com.stepnote.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepnote.app.domain.model.ThemeMode
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    initialGoal: Int,
    initialTheme: ThemeMode,
    onRequestHealthPermission: () -> Unit,
    onComplete: (goal: Int, theme: ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableIntStateOf(initialGoal) }
    var theme by rememberSaveable { mutableStateOf(initialTheme) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step ${step + 1} / 3",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            when (step) {
                0 -> {
                    Text(
                        text = "スマホだけで、\n意識せず歩数を記録。",
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "アカウント登録もスマートウォッチも不要です。Android の記録基盤を使い、静かに日ごとの歩数をまとめます。",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                1 -> {
                    Text(
                        text = "歩数データへの\nアクセス許可",
                        color = colors.textPrimary,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "歩数の表示には Health Connect の読み取り許可が必要です。端末によっては、Google Play 開発者サービスで歩数を記録するために身体活動へのアクセスを求めます。データは端末に保存します。",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRequestHealthPermission,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("権限の許可へ進む", modifier = Modifier.padding(vertical = 6.dp))
                    }
                }

                2 -> {
                    Text(
                        text = "目標と見た目を設定",
                        color = colors.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(text = "1日の目標歩数", color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$goal 歩",
                                color = colors.textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = goal.toFloat(),
                                onValueChange = { goal = (it / 500f).roundToInt() * 500 },
                                valueRange = 1_000f..30_000f
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            OutlinedButton(
                                onClick = { theme = mode },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "自動"
                                        ThemeMode.LIGHT -> "ライト"
                                        ThemeMode.DARK -> "ダーク"
                                    },
                                    color = if (theme == mode) colors.accent else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { onComplete(goal, theme) }) {
                Text("あとで設定する", color = colors.textSecondary)
            }
            Button(
                onClick = {
                    if (step < 2) step += 1 else onComplete(goal, theme)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White
                )
            ) {
                Text(if (step < 2) "次へ" else "はじめる")
            }
        }
    }
}
