package com.stepnote.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepnote.app.data.preferences.UserPreferences
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.data.repository.StepRepository
import com.stepnote.app.domain.model.EnvironmentDiagnostic
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.ThemeMode
import com.stepnote.app.ui.privacy.PrivacyPolicyActivity
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class SettingsViewModel(
    private val prefsRepository: UserPreferencesRepository,
    private val stepRepository: StepRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _diagnostic = MutableStateFlow<EnvironmentDiagnostic?>(null)
    val diagnostic: StateFlow<EnvironmentDiagnostic?> = _diagnostic.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _diagnostic.value = stepRepository.inspectEnvironment()
        }
    }

    fun updateGoal(goal: Int) {
        viewModelScope.launch { prefsRepository.setDailyGoal(goal) }
    }

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun updateSourceScope(scope: SourceScope) {
        viewModelScope.launch {
            prefsRepository.setSourceScope(scope)
            refreshDiagnostics()
        }
    }

    fun updateRecordingSource(kind: StepSourceKind) {
        viewModelScope.launch {
            prefsRepository.setPreferredSourceKind(kind)
            refreshDiagnostics()
        }
    }

    fun updateStrideCm(cm: Int) {
        viewModelScope.launch { prefsRepository.setStrideCm(cm) }
    }

    fun updateCalorieSettings(enabled: Boolean, weightKg: Float?) {
        viewModelScope.launch { prefsRepository.setCalorieSettings(enabled, weightKg) }
    }

    fun clearLocalData(resetPreferences: Boolean) {
        viewModelScope.launch {
            stepRepository.clearLocalCache(resetPreferences)
            refreshDiagnostics()
        }
    }
}

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    diagnostic: EnvironmentDiagnostic?,
    onBack: () -> Unit,
    onUpdateGoal: (Int) -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateSourceScope: (SourceScope) -> Unit,
    onUpdateRecordingSource: (StepSourceKind) -> Unit,
    onUpdateStrideCm: (Int) -> Unit,
    onUpdateCalorieSettings: (Boolean, Float?) -> Unit,
    onClearLocalData: (Boolean) -> Unit,
    onRequestBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var weightText by remember(preferences.weightKg) {
        mutableStateOf(preferences.weightKg?.toInt()?.toString().orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "戻る",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = "設定",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Goal setting
        SectionCard(title = "1日の目標歩数") {
            Text(
                text = "${preferences.dailyGoal} 歩",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = preferences.dailyGoal.toFloat(),
                onValueChange = { onUpdateGoal((it / 500f).roundToInt() * 500) },
                valueRange = 1_000f..30_000f
            )
            Text(
                text = "1,000〜30,000 歩の範囲で設定できます。",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Theme mode
        SectionCard(title = "表示モード") {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateTheme(mode) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = mode.label, color = colors.textPrimary, fontSize = 14.sp)
                    Text(
                        text = if (preferences.themeMode == mode) "選択中" else "",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Data source scope
        SectionCard(title = "データ取得元") {
            SourceScope.entries.filter {
                it == SourceScope.THIS_PHONE_ONLY || diagnostic?.healthConnectStatusLabel == "利用可能" ||
                    preferences.sourceScope == it
            }.forEach { scope ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateSourceScope(scope) }
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = scope.label, color = colors.textPrimary, fontSize = 14.sp)
                        Text(
                            text = if (preferences.sourceScope == scope) "選択中" else "",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = if (scope == SourceScope.THIS_PHONE_ONLY) {
                            "初期設定。このスマートフォンが記録した歩数のみを集計します。"
                        } else {
                            "Health Connect 内の他アプリや端末を含む全対応データを優先順で重複排除して集計します。"
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                "設定を切り替えると、選んだ取得元の履歴に切り替わります。",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
            if (preferences.sourceScope == SourceScope.THIS_PHONE_ONLY && diagnostic?.isPlayServicesAvailable == true) {
                val usingRecording = preferences.preferredSourceKind == StepSourceKind.LOCAL_RECORDING
                OutlinedButton(
                    onClick = {
                        onUpdateRecordingSource(
                            if (usingRecording) StepSourceKind.HEALTH_CONNECT else StepSourceKind.LOCAL_RECORDING
                        )
                    }
                ) {
                    Text(if (usingRecording) "Health Connect を優先する" else "端末内の Recording API を使う")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Estimated stride
        SectionCard(title = "推定歩幅") {
            Text(
                text = "${preferences.strideCm} cm (推定値)",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = preferences.strideCm.toFloat(),
                onValueChange = { onUpdateStrideCm(it.roundToInt()) },
                valueRange = 40f..110f
            )
            Text(
                text = "移動距離は「歩数 × 推定歩幅」で算出される推定値です。",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Optional calories
        SectionCard(title = "推定消費エネルギー（任意）") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ホーム画面に表示する",
                    color = colors.textPrimary,
                    fontSize = 14.sp
                )
                Switch(
                    checked = preferences.showCalories,
                    onCheckedChange = { checked ->
                        val parsed = weightText.toFloatOrNull()
                        onUpdateCalorieSettings(checked, parsed?.takeIf { it in 25f..250f })
                    }
                )
            }
            if (preferences.showCalories) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { value ->
                        weightText = value.filter { it.isDigit() }
                        val weight = weightText.toFloatOrNull()?.takeIf { it in 25f..250f }
                        onUpdateCalorieSettings(true, weight)
                    },
                    label = { Text("体重 (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "初期設定はオフです。距離(km) × 体重(kg) × 0.55 の歩行近似式による参考値です。",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Diagnostics
        SectionCard(title = "端末・権限の診断") {
            if (diagnostic == null) {
                Text(text = "診断情報を読み込み中...", color = colors.textSecondary, fontSize = 13.sp)
            } else {
                DiagRow("Android API", "${diagnostic.androidVersion} (Ext ${diagnostic.upsideDownCakeExtension})")
                DiagRow("歩数センサー", if (diagnostic.hasStepCounterSensor) "搭載あり" else "未検出")
                DiagRow("Health Connect", diagnostic.healthConnectStatusLabel)
                DiagRow("READ_STEPS 権限", if (diagnostic.hasReadStepsPermission) "許可済" else "未許可")
                DiagRow("身体活動の認識", if (diagnostic.hasActivityRecognitionPermission) "許可済" else "未許可")
                DiagRow("背景読み取り権限", if (diagnostic.hasBackgroundReadPermission) "許可済" else "未許可 / 非対応")
                DiagRow("Play services", if (diagnostic.isPlayServicesAvailable) "利用可能" else "未対応")
                DiagRow("有効な記録方式", diagnostic.activeSourceKind.label)
                if (diagnostic.activeSourceKind == StepSourceKind.HEALTH_CONNECT &&
                    diagnostic.supportsBackgroundRead && diagnostic.hasReadStepsPermission &&
                    !diagnostic.hasBackgroundReadPermission
                ) {
                    Text(
                        "アプリを閉じている間の履歴保存やウィジェットの更新には、追加の許可が必要です。",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedButton(onClick = onRequestBackground) { Text("バックグラウンド読み取りを許可") }
                }
                if (!diagnostic.isPlayServicesAvailable) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")))
                        }
                    }) { Text("Google Play 開発者サービスの更新を確認") }
                }
                Text(
                    "記録が止まった場合は、権限と端末の省電力設定を確認してください。",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                DiagRow(
                    "最終同期",
                    diagnostic.lastSyncAt?.atZone(ZoneId.systemDefault())
                        ?.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) ?: "未同期"
                )
                if (diagnostic.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    diagnostic.notes.forEach { note ->
                        Text(text = "• $note", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // OS Health Connect & Privacy Policy & Clear cache
        SectionCard(title = "データ管理とプライバシー") {
            OutlinedButton(
                onClick = {
                    val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OS の Health Connect 設定を開く")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("プライバシーポリシーを読む")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showClearDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ローカルキャッシュと設定を削除", color = colors.warning)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("ローカルデータを削除しますか？") },
            text = {
                Text(
                    "このアプリ内のキャッシュ（Room DB）と設定（DataStore）を削除します。" +
                        "Health Connect や端末の記録基盤側にある元データは無断削除されません。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLocalData(true)
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.warning, contentColor = Color.White)
                ) {
                    Text("削除する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalStepnoteColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    val colors = LocalStepnoteColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
