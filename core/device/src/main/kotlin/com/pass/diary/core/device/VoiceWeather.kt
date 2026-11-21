package com.pass.diary.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pass.diary.core.data.Weather
import com.pass.diary.core.data.WeatherRepository
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

// ============================================================ 말로 쓰기 (ARCHITECTURE §6)

/**
 * Continuous dictation: recognizer sessions run back to back (each ends on a pause) and are stitched into one
 * transcript. On-device recognition when the phone has it; otherwise the system recognizer, which may use the
 * internet (notice shown once). Audio is never saved.
 */
private class Dictation(
    context: Context,
    onDevice: Boolean,
    private val onChange: (finalText: String, partial: String) -> Unit,
    private val onStop: () -> Unit,
) {
    private val recognizer: SpeechRecognizer =
        if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
    private val finals = StringBuilder()
    private var running = false
    val text get() = finals.toString()

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    finals.append(if (finals.isEmpty()) it else " $it")
                }
                onChange(text, "")
                if (running) recognizer.startListening(intent) // next session: keep stitching
            }
            override fun onPartialResults(partial: Bundle?) =
                onChange(text, partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            override fun onError(error: Int) {
                if (!running) return
                // A pause with nothing said: listen again. Anything else: stop and let the user resume.
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) recognizer.startListening(intent)
                else { running = false; onStop() }
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun start() { running = true; recognizer.startListening(intent) }
    fun stop() { running = false; recognizer.stopListening() }
    fun destroy() { running = false; recognizer.destroy() }

    companion object {
        fun onDeviceAvailable(context: Context) = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        fun anyAvailable(context: Context) = onDeviceAvailable(context) || SpeechRecognizer.isRecognitionAvailable(context)
    }
}

/**
 * "말로 쓰기": returns the action for the editor's button. Handles the mic permission, the one-time "인터넷을 쓸 수
 * 있어요" notice and the live panel. [onTranscript] receives the raw stitched transcript when the user taps 넣기; the
 * editor inserts it as-is (PLAN §3.2 기본 AI: 원문 그대로) — that callback is also the hook for AI tidy-up.
 */
@Composable
fun rememberDictation(onTranscript: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onTranscript)
    var open by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("dictation", Context.MODE_PRIVATE) }
    val begin = { if (!Dictation.onDeviceAvailable(context) && !prefs.getBoolean(KEY_NOTICE, false)) notice = true else open = true }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else Toast.makeText(context, "마이크를 허용하면 말로 쓸 수 있어요", Toast.LENGTH_SHORT).show()
    }
    val c = Od.c
    if (notice) AlertDialog(
        onDismissRequest = { notice = false },
        containerColor = c.surface,
        title = { Text("말로 쓰기 안내", style = OdType.title3) },
        text = { Text("이 폰은 기기 안 음성 인식을 지원하지 않아서 기본 음성 인식을 써요. 인식에 인터넷을 쓸 수 있어요. 녹음은 저장하지 않아요.", style = OdType.body2, color = c.ink2) },
        confirmButton = { TextButton({ notice = false; prefs.edit().putBoolean(KEY_NOTICE, true).apply(); open = true }) { Text("계속", style = OdType.buttonSm, color = c.ink) } },
        dismissButton = { TextButton({ notice = false }) { Text("취소", style = OdType.buttonSm, color = c.ink2) } },
    )
    if (open) DictationPanel(onDone = { t -> open = false; if (t.isNotBlank()) deliver(t) }, onCancel = { open = false })
    return remember {
        {
            when {
                !Dictation.anyAvailable(context) -> Toast.makeText(context, "이 폰에서는 음성 입력을 쓸 수 없어요", Toast.LENGTH_SHORT).show()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> begin()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

private const val KEY_NOTICE = "cloud_notice_shown"

@Composable
private fun DictationPanel(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val c = Od.c
    val context = LocalContext.current
    var finalText by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var stopped by remember { mutableStateOf(false) }
    val d = remember { Dictation(context, Dictation.onDeviceAvailable(context), { f, p -> finalText = f; partial = p }, { stopped = true }) }
    DisposableEffect(d) { d.start(); onDispose { d.destroy() } }
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val shown = listOf(finalText, partial).filter { it.isNotBlank() }.joinToString(" ")
    AlertDialog(
        onDismissRequest = {},
        containerColor = c.surface,
        icon = {
            Box(Modifier.size(56.dp).scale(if (stopped) 1f else pulse).clip(CircleShape).background(c.surface2), contentAlignment = Alignment.Center) {
                OdIcon(OdIcon.Mic, size = 26.dp)
            }
        },
        title = { Text(if (stopped) "잠깐 멈췄어요" else "듣고 있어요", style = OdType.title3) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 220.dp).verticalScroll(rememberScrollState())) {
                Text(
                    shown.ifBlank { if (stopped) "이어 말하기를 누르면 다시 들어요" else "편하게 말해 주세요. 잠깐 쉬어도 계속 들어요." },
                    style = OdType.diary, color = if (shown.isBlank()) c.ink2 else c.ink,
                )
            }
        },
        confirmButton = { TextButton({ d.stop(); onDone(shown) }) { Text("넣기", style = OdType.buttonSm, color = c.ink) } },
        dismissButton = {
            Row {
                if (stopped) TextButton({ stopped = false; d.start() }) { Text("이어 말하기", style = OdType.buttonSm, color = c.ink2) }
                TextButton(onCancel) { Text("취소", style = OdType.buttonSm, color = c.ink2) }
            }
        },
    )
}

// ============================================================ 날씨 (PLAN §3.1: 선택하면 그날 날씨를 자동 기록)

@HiltViewModel
class WeatherViewModel @Inject constructor(val repo: WeatherRepository) : ViewModel()

/**
 * "날씨": city-level location (permission asked on first tap; optional) → Open-Meteo for [date] → [onWeather] with the
 * Entry.weather string ("rain|12"). Denied / no fix / offline = a toast, nothing stored.
 */
@Composable
fun rememberWeatherAction(date: String, onWeather: (String) -> Unit, vm: WeatherViewModel = hiltViewModel()): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val day by rememberUpdatedState(date)
    val deliver by rememberUpdatedState(onWeather)
    var busy by remember { mutableStateOf(false) }
    val toast = { m: String -> Toast.makeText(context, m, Toast.LENGTH_SHORT).show() }
    val fetch = {
        if (!busy) {
            busy = true
            scope.launch {
                val loc = coarseLocation(context)
                val w = loc?.let { vm.repo.weather(day, it.latitude, it.longitude) }
                busy = false
                when {
                    loc == null -> toast("지금 위치를 알 수 없어요. 위치를 켜고 다시 눌러 주세요")
                    w == null -> toast("날씨를 가져오지 못했어요. 인터넷을 확인해 주세요")
                    else -> { deliver(w.encode()); toast("${w.label} · 날씨를 기록했어요") }
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetch() else toast("위치를 허용하면 그날 날씨를 기록해요")
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetch()
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

/** "비 12°" for the editor toolbar, or null. */
fun weatherLabel(weather: String?): String? = Weather.decode(weather)?.label

/** Newest cached network/passive/GPS fix (no Play services); a fresh one on API 30+ when nothing is cached. */
@SuppressLint("MissingPermission")
private suspend fun coarseLocation(context: Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val cached = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (cached != null || Build.VERSION.SDK_INT < 30) return cached
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { lm.isProviderEnabled(it) } ?: return null
    return suspendCancellableCoroutine { cont ->
        runCatching { lm.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) } }.onFailure { cont.resume(null) }
    }
}
