package com.stepnote.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepnote.app.data.health.SourceFetchResult
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.data.repository.StepRepository
import com.stepnote.app.domain.model.WalkSession
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class WalkSessionViewModel(
    private val repository: StepRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {
    val session = preferences.walkSessionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _changing = MutableStateFlow(false)
    val changing = _changing.asStateFlow()
    private var readJob: Job? = null

    fun start() {
        val tappedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        changeSession {
            val previous = preferences.walkSessionFlow.first()
            if (previous != null && previous.stoppedAt == null) return@changeSession
            when (val result = repository.prepareWalkSession(tappedAt)) {
                is SourceFetchResult.Success -> preferences.saveWalkSession(previous, result.data)
                is SourceFetchResult.PermissionMissing -> _message.value = result.reason
                is SourceFetchResult.Unavailable -> _message.value = result.reason
                is SourceFetchResult.Failure -> _message.value = result.message
            }
        }
    }

    fun stop() {
        val tappedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        changeSession {
            val previous = preferences.walkSessionFlow.first() ?: return@changeSession
            val stopped = previous.stop(tappedAt)
            if (stopped != previous && preferences.saveWalkSession(previous, stopped)) {
                refreshAfterChange()
            }
        }
    }

    fun refresh(onlyRunning: Boolean = false) {
        if (_changing.value || readJob?.isActive == true) return
        readJob = viewModelScope.launch {
            val current = preferences.walkSessionFlow.first() ?: return@launch
            if (!onlyRunning || current.stoppedAt == null) read(current)
        }
    }

    private fun changeSession(action: suspend () -> Unit) {
        if (_changing.value) return
        _changing.value = true
        val activeRead = readJob
        activeRead?.cancel()
        _message.value = null
        viewModelScope.launch {
            try {
                activeRead?.join()
                action()
            } finally {
                _changing.value = false
            }
        }
    }

    private fun refreshAfterChange() {
        readJob = viewModelScope.launch {
            val current = preferences.walkSessionFlow.first() ?: return@launch
            read(current)
        }
    }

    private suspend fun read(current: WalkSession) {
        _message.value = null
        when (val result = repository.readWalkSession(current)) {
            is SourceFetchResult.Success -> {
                val count = result.data ?: return
                preferences.saveWalkSession(current, current.copy(steps = count))
            }
            is SourceFetchResult.PermissionMissing -> _message.value = result.reason
            is SourceFetchResult.Unavailable -> _message.value = result.reason
            is SourceFetchResult.Failure -> _message.value = result.message
        }
    }
}

@Composable
fun WalkSessionCard(
    session: WalkSession?,
    message: String?,
    changing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalStepnoteColors.current
    val zone = ZoneId.systemDefault()
    val clock = DateTimeFormatter.ofPattern("M/d HH:mm:ss").withZone(zone)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("区間計測", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (session == null) {
                Text("スタートからストップまでの歩数を表示します。", color = colors.textSecondary)
            } else {
                Text(
                    if (session.stoppedAt == null) "計測中" else "前回の計測",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                Text(
                    session.steps?.let { "${NumberFormat.getIntegerInstance().format(it)} 歩" } ?: "未取得",
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val end = session.stoppedAt?.let { " 〜 ${clock.format(it)}" } ?: " 〜"
                Text("${clock.format(session.startedAt)}$end", color = colors.textSecondary, fontSize = 12.sp)
                Text(session.scope.label, color = colors.textSecondary, fontSize = 12.sp)
                Text("歩数の反映に時間がかかることがあります。終了後も同じ区間を再取得できます。", color = colors.textSecondary, fontSize = 12.sp)
            }
            message?.let { Text(it, color = colors.warning, fontSize = 13.sp) }
            if (session != null && session.stoppedAt == null) {
                Button(onClick = onStop, enabled = !changing) { Text("ストップ") }
            } else {
                Button(onClick = onStart, enabled = !changing) {
                    Text(if (session == null) "スタート" else "新しい計測をスタート")
                }
            }
            if (session != null) {
                OutlinedButton(onClick = onRefresh, enabled = !changing) { Text("区間の歩数を更新") }
            }
        }
    }
}
