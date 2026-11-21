package com.pass.diary.core.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import kotlinx.coroutines.launch

/**
 * "PIN을 잊었어요". [driveAccount] = the Google account Drive backup uses (null = not signed in). [reauthenticate] runs
 * Credential Manager again and is true only for that same account; [wipeAppData] clears everything (the honest last resort).
 */
class PinRecovery(val driveAccount: String?, val reauthenticate: suspend () -> Boolean, val wipeAppData: () -> Unit)

/** Full-screen lock (시안 07_마이_4). Back closes the app (handled by the caller via [onBack]). */
@Composable
fun LockScreen(lock: LockManager, onBiometric: (() -> Unit)?, onBack: () -> Unit, recovery: PinRecovery? = null) {
    var message by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wrongFmt = stringResource(R.string.lock_wrong)
    val outFmt = stringResource(R.string.lock_locked_out)
    if (resetting) {
        PinSetup(onDone = { pin -> scope.launch { lock.resetPin(pin) } }, onCancel = { resetting = false })
        return
    }
    BackHandler(onBack = onBack)
    PinLayout(
        title = "온이가 지키고 있어요",
        message = message,
        onBiometric = onBiometric,
        footer = "PIN을 잊었어요",
        onFooter = { forgot = recovery != null },
        onComplete = { pin, reset ->
            scope.launch {
                when (val r = lock.tryUnlock(pin)) {
                    LockManager.Attempt.Ok -> Unit
                    is LockManager.Attempt.Wrong -> { message = wrongFmt.format(r.failures); reset() }
                    is LockManager.Attempt.LockedOut -> { message = outFmt.format((r.millis + 999) / 1000); reset() }
                }
            }
        },
    )
    if (forgot && recovery != null) {
        val account = recovery.driveAccount
        if (account != null) AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Google 계정으로 확인할게요", style = OdType.title3) },
            text = { Text("백업에 쓰는 계정($account)으로 다시 로그인하면 PIN을 새로 정할 수 있어요.", style = OdType.body2, color = Od.c.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    forgot = false
                    scope.launch {
                        if (runCatching { recovery.reauthenticate() }.getOrDefault(false)) resetting = true
                        else message = "백업에 쓰는 계정으로 확인하지 못했어요"
                    }
                }) { Text("계정으로 확인") }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.cancel)) } },
        ) else WipeDialog(onWipe = recovery.wipeAppData, onDismiss = { forgot = false })
    }
}

/** No Drive backup: the PIN can't be recovered. Wiping is offered only after typing [WIPE_PHRASE]. */
@Composable
private fun WipeDialog(onWipe: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN은 되찾을 수 없어요", style = OdType.title3) },
        text = {
            Column {
                Text(
                    "PIN은 이 폰에만 있고 드라이브 백업도 꺼져 있어서, 온이도 PIN을 알 수 없어요. 생각날 때까지 다시 시도해 보세요.\n\n" +
                        "처음부터 시작하려면 앱 데이터를 모두 지울 수 있어요. 일기·사진·설정이 전부 사라지고 되돌릴 수 없어요.",
                    style = OdType.body2, color = Od.c.ink2,
                )
                Text("지우려면 아래에 \"$WIPE_PHRASE\"를 입력해요", style = OdType.caption1, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onWipe, enabled = typed.trim() == WIPE_PHRASE) { Text("모두 지우기", color = if (typed.trim() == WIPE_PHRASE) Od.c.ink else Od.c.ink3) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("다시 시도할게요") } },
    )
}

const val WIPE_PHRASE = "모든 일기 지우기"

/** Two-step PIN setup (enter, confirm). */
@Composable
fun PinSetup(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_setup_mismatch)
    BackHandler(onBack = onCancel)
    PinLayout(
        title = stringResource(if (first == null) R.string.pin_setup_title else R.string.pin_setup_confirm),
        // Said before the PIN exists: there is no recovery without Drive backup.
        message = message ?: if (first == null) "PIN을 잊으면 드라이브 백업이 없을 때는 되찾을 수 없어요. 백업을 켜 두면 Google 계정으로 다시 정할 수 있어요." else null,
        onBiometric = null,
        footer = stringResource(R.string.cancel),
        onFooter = onCancel,
        onComplete = { pin, reset ->
            when (val f = first) {
                null -> { first = pin; message = null; reset() }
                pin -> onDone(pin)
                else -> { first = null; message = mismatch; reset() }
            }
        },
    )
}

@Composable
private fun PinLayout(
    title: String,
    message: String?,
    onBiometric: (() -> Unit)?,
    footer: String,
    onFooter: () -> Unit = {},
    onComplete: (pin: String, reset: () -> Unit) -> Unit,
) {
    val c = Od.c
    var pin by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.Dusk, 0.7f, centerY = 240.dp, radius = 420.dp)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OniFigure(null, OniExpression.SLEEP, 150.dp)
            Spacer(Modifier.height(6.dp))
            Text(title, style = OdType.title2)
            Text(message ?: "PIN 4자리를 입력해요", style = OdType.caption1, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(PinHasher.PIN_LENGTH) { i ->
                    val m = Modifier.size(14.dp).clip(CircleShape)
                    Box(if (i < pin.length) m.background(c.ink) else m.border(1.5.dp, c.ink3, CircleShape))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp)) {
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "back")
            Column(Modifier.padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            val enabled = k != "bio" || onBiometric != null
                            Box(
                                Modifier.weight(1f).height(62.dp).clip(CircleShape).clickable(enabled = enabled) {
                                    when (k) {
                                        "bio" -> onBiometric?.invoke()
                                        "back" -> pin = pin.dropLast(1)
                                        else -> if (pin.length < PinHasher.PIN_LENGTH) {
                                            pin += k
                                            if (pin.length == PinHasher.PIN_LENGTH) onComplete(pin) { pin = "" }
                                        }
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                when (k) {
                                    "bio" -> if (onBiometric != null) OdIcon(OdIcon.Finger, size = 28.dp, contentDescription = stringResource(R.string.lock_biometric))
                                    "back" -> OdIcon(OdIcon.Backspace, size = 26.dp, contentDescription = stringResource(R.string.lock_delete))
                                    else -> Text(k, style = OdType.title1.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                footer, style = OdType.caption2, color = c.ink2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).clickable(onClick = onFooter).padding(vertical = 6.dp),
            )
        }
    }
}
