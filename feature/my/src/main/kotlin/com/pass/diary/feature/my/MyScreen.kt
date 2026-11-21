package com.pass.diary.feature.my

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.CardHeader
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.ListRow
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdToggle
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.PremiumBadge
import com.pass.diary.core.designsystem.SegTabs
import com.pass.diary.core.designsystem.TabHeader
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.Settings
import com.pass.diary.core.model.ThemeMode
import com.pass.diary.core.security.LockManager
import com.pass.diary.core.security.PinSetup
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncEngine
import com.pass.diary.core.sync.SyncError
import com.pass.diary.core.sync.SyncLocalStore
import com.pass.diary.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@Serializable data object MyRoute
@Serializable data object PremiumRoute
@Serializable data object WakeRoute
@Serializable data object LockSettingsRoute
@Serializable data object DecorateRoute
@Serializable data object AiSettingsRoute

@HiltViewModel
class MyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    val lock: LockManager,
    private val sync: SyncEngine,
    private val auth: DriveAuth,
    private val store: SyncLocalStore,
    val models: ModelStateProvider,
    private val repository: EntryRepository,
) : ViewModel() {
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val syncStatus = sync.status
    val account = MutableStateFlow<String?>(null)

    /** (diaries, photos) for the backup card. */
    val counts = repository.observeRange("0000-01-01", "9999-12-31")
        .map { l -> l.size to l.sumOf { it.photos.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trashCount = repository.observeTrash().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        account.value = store.meta(DriveAuth.META_ACCOUNT)
        runCatching { sync.refreshStatus() }
    }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(m) }
    fun setSecure(on: Boolean) = viewModelScope.launch { settingsRepo.setSecureScreen(on) }
    fun setRelock(sec: Int) = viewModelScope.launch { settingsRepo.setRelockAfterSec(sec) }
    fun setPin(pin: String) = viewModelScope.launch { lock.setPin(pin) }
    fun setAiReply(on: Boolean) = viewModelScope.launch { settingsRepo.setAiReply(on) }
    fun setAiSuggest(on: Boolean) = viewModelScope.launch { settingsRepo.setAiSuggest(on) }
    fun setAiChat(on: Boolean) = viewModelScope.launch { settingsRepo.setAiChat(on) }
    fun setHideWidget(on: Boolean) = viewModelScope.launch { settingsRepo.setHideWidgetContent(on) }

    /** A different Google account than the one this phone backed up to before: the user decides, never a silent merge. */
    data class AccountChange(val email: String, val previous: String, val previousClean: Boolean?)
    val accountChange = MutableStateFlow<AccountChange?>(null)
    private var switchAfterConsent = false

    /** Sign in (also "다시 연결") → [AccountChange] if the account differs → drive.appdata consent (if needed) → first sync. */
    fun signIn(activity: Activity, webClientId: String, launchConsent: (android.app.PendingIntent) -> Unit, onError: () -> Unit) =
        viewModelScope.launch {
            runCatching {
                val email = auth.pickAccount(activity, webClientId)
                val previous = auth.previousAccount(email)
                if (previous != null) accountChange.value = AccountChange(email, previous, auth.previousAccountWasClean())
                else connect(email, launchConsent)
            }.onFailure { onError() }
        }

    /** "이 폰 일기를 새 계정에도 백업": keep everything local, merge with whatever the new Drive has. */
    fun mergeInto(change: AccountChange, launchConsent: (android.app.PendingIntent) -> Unit) = viewModelScope.launch {
        accountChange.value = null
        runCatching { connect(change.email, launchConsent) }
    }

    /** "새 계정의 일기로 바꾸기": this phone's diaries are wiped and the new account's Drive is restored after a restart. */
    fun switchTo(change: AccountChange, launchConsent: (android.app.PendingIntent) -> Unit) = viewModelScope.launch {
        accountChange.value = null
        runCatching {
            auth.connect(change.email)
            val consent = auth.authorize()
            if (consent != null) { switchAfterConsent = true; launchConsent(consent) } else wipeForRestore()
        }
    }

    private suspend fun connect(email: String, launchConsent: (android.app.PendingIntent) -> Unit) {
        auth.connect(email)
        account.value = store.meta(DriveAuth.META_ACCOUNT)
        val consent = auth.authorize()
        if (consent != null) launchConsent(consent) else SyncScheduler.syncNow(context)
    }

    fun onConsent(data: Intent?) {
        val ok = auth.onConsentResult(data)
        if (switchAfterConsent) { switchAfterConsent = false; if (ok) viewModelScope.launch { wipeForRestore() }; return }
        if (ok) SyncScheduler.syncNow(context)
    }

    /** Keeps AI models and sync_meta (the new account, its consent already given); on restart the full listing restores. */
    private suspend fun wipeForRestore() {
        SyncScheduler.cancelAll(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.closeDatabase()
            com.pass.diary.core.data.AppDataWiper.wipe(context.dataDir, setOf("files/models", "shared_prefs/oni_model.xml", "files/datastore/sync_meta.preferences_pb"))
        }
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let { context.startActivity(Intent.makeRestartActivityTask(it)) }
        Runtime.getRuntime().exit(0)
    }

    fun syncNow() = SyncScheduler.syncNow(context)
    fun setPhotosOff(off: Boolean) = viewModelScope.launch { sync.setPhotosOff(off); if (!off) SyncScheduler.syncNow(context) }
    fun photosOffAndBackUp() = viewModelScope.launch { sync.setPhotosOff(true); SyncScheduler.syncNow(context) }
    fun signOut() = viewModelScope.launch { auth.signOut(); SyncScheduler.cancelAll(context); refresh() }

    val deleting = MutableStateFlow(false)

    /**
     * 계정·데이터 삭제: Drive backup files (if signed in) → revoke Drive access & sign out → every local file
     * (keeping the downloaded AI models when [keepModels]). [onWiped] must restart the process right away.
     * If Drive can't be reached nothing is deleted, so the user can retry instead of losing the way to their backup.
     */
    fun deleteEverything(keepModels: Boolean, onFailed: () -> Unit, onWiped: () -> Unit) = viewModelScope.launch {
        deleting.value = true
        if (auth.isSignedIn()) {
            if (runCatching { sync.deleteRemote() }.isFailure) { deleting.value = false; onFailed(); return@launch }
            auth.revokeAndSignOut()
        }
        SyncScheduler.cancelAll(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.closeDatabase()
            com.pass.diary.core.data.AppDataWiper.wipe(context.dataDir, if (keepModels) setOf("files/models", "shared_prefs/oni_model.xml") else emptySet())
        }
        onWiped()
    }
}

// ======================================================================= 마이 홈

@Composable
fun MyScreen(
    onPremium: () -> Unit, onWake: () -> Unit, onLock: () -> Unit, onDecorate: () -> Unit, onLicenses: () -> Unit, onAi: () -> Unit, onReminder: () -> Unit = {},
    onTrash: () -> Unit = {}, onExport: () -> Unit = {},
    vm: MyViewModel = hiltViewModel(),
) {
    val c = Od.c
    val ctx = LocalContext.current
    val premium = Od.premium
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lockOn by vm.lock.enabled.collectAsStateWithLifecycle()
    val bio by vm.lock.biometricEnabled.collectAsStateWithLifecycle()
    val models by vm.models.models.collectAsStateWithLifecycle()
    val trashCount by vm.trashCount.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }
    if (confirmWipe) WipeDialog(vm, onDismiss = { confirmWipe = false })
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding()) {
        TabHeader("마이")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumHero(onPremium)
            BackupCard(vm)
            OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                ListRow(OdIcon.Sparkle, "온이 깨우기", wakeValue(models), onWake)
                ListRow(OdIcon.Lock, "잠금", if (!lockOn) "꺼짐" else if (bio) "PIN · 지문" else "PIN", onLock)
                ListRow(OdIcon.Palette, "꾸미기", themeName(settings.themeMode), onDecorate)
                ListRow(OdIcon.Bell, "알림", reminderValue(), onReminder)
                ListRow(OdIcon.Download, "내보내기", "내 파일로", onExport)
                ListRow(OdIcon.Trash, "휴지통", trashCount.takeIf { it > 0 }?.let { "${it}개" }, onTrash)
                // ponytail: 감정 이름·색 편집 aren't built yet; their rows come back with them.
                // PDF 일기책 is premium and ships in v2.1 (PLAN §6): locked state only for now.
                ListRow(OdIcon.Doc, "PDF 일기책", if (premium) "v2.1에서 열려요" else "프리미엄", {
                    if (premium) Toast.makeText(ctx, "PDF 일기책은 다음 업데이트에서 열려요", Toast.LENGTH_SHORT).show() else onPremium()
                })
                ListRow(OdIcon.Sparkle, "AI 기능", aiValue(settings), onAi)
                ListRow(OdIcon.Trash, "계정·데이터 삭제", null, { confirmWipe = true })
                ListRow(OdIcon.Doc, "이용약관", null, { openUrl(ctx, ctx.getString(com.pass.diary.core.designsystem.R.string.terms_url)) })
                ListRow(OdIcon.Doc, "개인정보 처리방침", null, { openUrl(ctx, ctx.getString(com.pass.diary.core.designsystem.R.string.privacy_policy_url)) })
                val ads = com.pass.diary.core.billing.LocalAdManager.current
                val activity = LocalActivity.current
                if (ads != null && activity != null && ads.privacyOptionsRequired(ctx)) ListRow(OdIcon.Doc, "광고 개인정보 옵션", null, { ads.showPrivacyOptions(activity) })
                ListRow(OdIcon.Doc, "오픈소스 라이선스", null, onLicenses)
            }
            Text(
                "온이는 의료·상담 서비스가 아니에요. 설정에서 AI를 끌 수 있어요.",
                style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
}

fun openUrl(ctx: Context, url: String) {
    // Custom Tabs: the legal pages live on the web, so policy edits never need an app update.
    runCatching { androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, android.net.Uri.parse(url)) }
        .recoverCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        .onFailure { Toast.makeText(ctx, "브라우저를 열 수 없어요", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun WipeDialog(vm: MyViewModel, onDismiss: () -> Unit) {
    val c = Od.c
    val ctx = LocalContext.current
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf("") }
    var keepModels by remember { mutableStateOf(true) }
    val signedIn = vm.account.collectAsStateWithLifecycle().value != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("모든 기록을 지울까요?", style = OdType.title3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "이 폰의 일기·사진·답장·설정이 모두 지워지고 처음 화면으로 돌아가요." +
                        (if (signedIn) " 구글 드라이브에 백업된 일기도 함께 지워지고 백업 연결이 해제돼요." else "") +
                        " 지운 기록은 되돌릴 수 없어요.",
                    style = OdType.body2, color = c.ink2,
                )
                Text("지우려면 아래에 \"${com.pass.diary.core.security.WIPE_PHRASE}\"를 입력해요", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold))
                androidx.compose.material3.OutlinedTextField(typed, { typed = it }, singleLine = true, enabled = !deleting, placeholder = { Text(com.pass.diary.core.security.WIPE_PHRASE) })
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !deleting) { keepModels = !keepModels },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Checkbox(!keepModels, { keepModels = !it }, enabled = !deleting)
                    Text("내려받은 AI 모델도 지우기", style = OdType.body2)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = typed.trim() == com.pass.diary.core.security.WIPE_PHRASE && !deleting,
                onClick = {
                    vm.deleteEverything(
                        keepModels,
                        onFailed = { Toast.makeText(ctx, "드라이브 백업을 지우지 못했어요. 인터넷 연결을 확인하고 다시 해 주세요.", Toast.LENGTH_LONG).show() },
                        onWiped = {
                            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component?.let { ctx.startActivity(Intent.makeRestartActivityTask(it)) }
                            Runtime.getRuntime().exit(0)
                        },
                    )
                },
            ) { Text(if (deleting) "지우는 중…" else "모두 지우기", color = if (typed.trim() == com.pass.diary.core.security.WIPE_PHRASE && !deleting) c.ink else c.ink3, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !deleting) { Text("취소", color = c.ink2) } },
        containerColor = c.surface,
    )
}

private fun aiValue(s: Settings) = listOf(s.aiReply, s.aiSuggest, s.aiChat).count { it }.let { if (it == 3) "켜짐" else if (it == 0) "꺼짐" else "일부 켜짐" }

private fun themeName(m: ThemeMode) = when (m) { ThemeMode.SYSTEM -> "시스템"; ThemeMode.LIGHT -> "라이트"; ThemeMode.DARK -> "다크"; ThemeMode.PAPER -> "종이" }

private fun wakeValue(models: List<ModelCard>): String {
    val s = models.map { it.state }
    s.filterIsInstance<ModelState.Downloading>().firstOrNull()?.let { d ->
        val m = models.first { it.state == d }
        return "내려받는 중 ${percent(d.downloadedBytes, m.sizeBytes)}%"
    }
    return if (s.any { it == ModelState.Ready }) "깨어 있어요" else "자는 중"
}

private fun percent(done: Long, total: Long) = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)

@Composable
private fun PremiumHero(onPremium: () -> Unit) {
    val c = Od.c
    val brush = Brush.linearGradient(if (c.dark) listOf(Color(0xFF3A2F55), Color(0xFF2B2444)) else listOf(Color(0xFFFFF4DE), Color(0xFFFFE8C7)))
    Box(Modifier.fillMaxWidth().cardShadow().clip(RoundedCornerShape(20.dp)).background(brush)) {
        OniFigure(null, OniExpression.CHEER, 104.dp, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        Column(Modifier.padding(18.dp)) {
            PremiumBadge()
            if (Od.premium) {
                Text("프리미엄 사용 중", style = OdType.title2, modifier = Modifier.padding(top = 8.dp))
                Text("광고 없이, 평생 그대로예요", style = OdType.caption1, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("3,300원으로\n평생 광고 없이", style = OdType.title2, modifier = Modifier.padding(top = 8.dp).width(200.dp))
                Text("폰을 바꿔도 그대로예요", style = OdType.caption1, color = c.ink, modifier = Modifier.padding(top = 4.dp))
                OdButton("자세히 보기", onPremium, Modifier.padding(top = 12.dp), small = true, height = 38.dp)
            }
        }
    }
}

@Composable
internal fun Modifier.cardShadow(): Modifier =
    if (Od.c.dark) this else this.dropShadow(RoundedCornerShape(20.dp), Shadow(radius = 20.dp, color = Color(0x0F2E2622), offset = DpOffset(0.dp, 6.dp)))

private fun backupTime(at: Long): String {
    val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = at }
    val time = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date(at))
    val day = when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "오늘"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "어제"
        else -> SimpleDateFormat("M월 d일", Locale.KOREAN).format(Date(at))
    }
    return "$day $time"
}

@Composable
private fun BackupCard(vm: MyViewModel) {
    val c = Od.c
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val clientId = stringResource(R.string.google_web_client_id)
    var message by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onConsent(it.data) }
    LaunchedEffect(status.running) { if (!status.running) vm.refresh() }
    val launchConsent: (android.app.PendingIntent) -> Unit = { pi -> consent.launch(IntentSenderRequest.Builder(pi).build()) }
    val signIn = {
        activity?.let { vm.signIn(it, clientId, launchConsent, { message = "로그인하지 못했어요. 다시 시도해 주세요" }) }
        Unit
    }
    OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("자동 백업", style = OdType.title3)
            if (account != null && status.lastBackupAt != null && status.error == null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF3F8A5A).copy(alpha = if (c.dark) 0.3f else 0.14f)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    OdIcon(OdIcon.Shield, size = 13.dp, stroke = 2f, tint = c.ok)
                    Text("검증됨", style = OdType.caption2.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = c.ok)
                }
            }
            Spacer(Modifier.weight(1f))
            OdToggle(account != null, { on -> if (on) { if (clientId.isNotBlank()) signIn() else message = "로그인 설정이 아직 준비되지 않았어요" } else vm.signOut() })
        }
        val countText = counts?.let { (n, p) -> " · 일기 ${n}개, 사진 ${p}장" } ?: ""
        val line = when {
            account == null -> "켜면 저장할 때마다 내 드라이브에 자동으로 백업해요"
            status.running -> "백업하는 중…"
            status.lastBackupAt != null -> "마지막 백업 ${backupTime(status.lastBackupAt!!)}$countText" + if (status.photosOff) " · 사진 제외" else ""
            else -> "아직 백업하지 않았어요$countText"
        }
        Text(line, style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
        Text(if (account != null) "내 Google 드라이브의 앱 전용 폴더에 보관돼요 · $account" else "내 Google 드라이브의 앱 전용 폴더에 보관돼요", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 2.dp))
        val err = status.error?.let {
            when (it) {
                SyncError.NOT_SIGNED_IN -> "드라이브 연결이 끊겼어요. 다시 연결하면 이어서 백업해요 (일기는 이 폰에 그대로 있어요)"
                SyncError.STORAGE_FULL -> "드라이브 저장 공간이 부족해요. 일기는 이 폰에 그대로 있고, 공간이 생기면 이어서 백업해요"
                SyncError.VERIFY_FAILED -> "백업 확인이 끝나지 않았어요. 곧 다시 시도할게요"
                SyncError.NETWORK -> "인터넷에 연결되면 이어서 백업할게요"
                SyncError.UNKNOWN -> "백업이 잠시 멈췄어요"
            }
        }
        val msg = err ?: message ?: if (account != null && status.pending > 0 && !status.running) "올릴 기록 ${status.pending}개가 기다리고 있어요" else null
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        @Composable fun Action(label: String, onClick: () -> Unit) = Text(
            label, style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (msg != null) Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = OdType.caption2, color = c.ink2, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                when {
                    account == null -> Unit
                    status.error == SyncError.NOT_SIGNED_IN -> Action("다시 연결") { message = null; signIn() }
                    status.error != SyncError.STORAGE_FULL -> Action("지금 백업") { message = null; vm.syncNow() }
                }
            }
            if (account != null && status.error == SyncError.STORAGE_FULL) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // Google One's storage page: what's using the 15GB, and where to free it up.
                Action("공간 확보 안내") { uriHandler.openUri("https://one.google.com/storage") }
                if (!status.photosOff) Action("사진 제외하고 일기만 백업") { vm.photosOffAndBackUp() } else Action("다시 시도") { vm.syncNow() }
            }
        }
        if (account != null) Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("사진도 백업", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold))
                if (status.photosOff) Text("끄면 일기 글만 올라가요. 사진은 이 폰에만 있어요", style = OdType.caption2, color = c.ink2)
            }
            OdToggle(!status.photosOff, { on -> vm.setPhotosOff(!on) })
        }
    }
    vm.accountChange.collectAsStateWithLifecycle().value?.let { change ->
        AccountChangeDialog(change, onMerge = { vm.mergeInto(change, launchConsent) }, onSwitch = { vm.switchTo(change, launchConsent) }, onDismiss = { vm.accountChange.value = null })
    }
}

@Composable
private fun AccountChangeDialog(change: MyViewModel.AccountChange, onMerge: () -> Unit, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    val c = Od.c
    var confirmSwitch by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmSwitch) "이 폰의 일기를 지울까요?" else "다른 계정이에요", style = OdType.title3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!confirmSwitch) {
                    Text("전에는 ${change.previous}에 백업했어요. ${change.email}(으)로 어떻게 할까요?", style = OdType.body2, color = c.ink2)
                    OdButton("이 폰 일기를 이 계정에도 백업", onMerge, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth())
                    OdButton("이 계정의 일기로 바꾸기", { confirmSwitch = true }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        "이 폰의 일기·사진·설정을 지우고 ${change.email} 드라이브의 일기를 내려받아요. ${change.previous} 드라이브의 백업은 그대로 남아요." +
                            if (change.previousClean == true) "" else "\n\n${change.previous}에 아직 올라가지 않은 기록이 있었다면 사라져요.",
                        style = OdType.body2, color = c.ink2,
                    )
                }
            }
        },
        confirmButton = {
            if (confirmSwitch) androidx.compose.material3.TextButton(onClick = onSwitch) { Text("지우고 바꾸기", color = c.ink, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("취소", color = c.ink2) } },
        containerColor = c.surface,
    )
}

// ======================================================================= 온이 깨우기

private fun gb(bytes: Long) = "%.1fGB".format(bytes / 1e9)

@Composable
fun WakeScreen(onBack: () -> Unit, vm: MyViewModel = hiltViewModel()) {
    val c = Od.c
    val models by vm.models.models.collectAsStateWithLifecycle()
    val wifi by vm.models.wifiOnly.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    // The download runs as a foreground job with a progress notification: ask for the notification permission once it starts.
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val downloading = models.any { (it.state as? ModelState.Downloading)?.paused == false }
    androidx.compose.runtime.LaunchedEffect(downloading) {
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (downloading && android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) askNotify.launch(perm)
    }
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.Dusk, 0.6f, centerY = 140.dp, radius = 380.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            OdTopBar(title = "온이 깨우기", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OniFigure(null, OniExpression.SLEEP, 108.dp)
                    Column(Modifier.weight(1f)) {
                        Text("온이를 깨우면\n대화도 하고 답장도 써요", style = OdType.title2)
                        Text("한 번만 내려받으면 인터넷 없이 돌아가요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                models.forEachIndexed { i, m ->
                    ModelCardView(m, vm.models, if (i == 0) wifi else null, Modifier.padding(top = if (i == 0) 0.dp else 12.dp))
                }
                Row(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OdIcon(OdIcon.Shield, size = 18.dp)
                    Text("대화와 일기는 모두 이 폰 안에서만 처리돼요. 우리 서버로도 보내지 않아요.", style = OdType.caption2, color = c.ink2, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
private fun ModelCardView(m: ModelCard, p: ModelStateProvider, wifi: Boolean?, modifier: Modifier) {
    val c = Od.c
    val st = m.state
    val locked = st is ModelState.Unsupported || (m.premium && !Od.premium)
    OdCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.title, style = OdType.title3)
            if (m.premium) { Spacer(Modifier.width(8.dp)); PremiumBadge() }
            Spacer(Modifier.weight(1f))
            Text(m.modelName, style = OdType.caption2, color = c.ink2)
        }
        val ok = st !is ModelState.Unsupported
        Text(
            "약 ${gb(m.sizeBytes)} · RAM ${m.minRamGb}GB 이상" + if (ok && !m.premium) " · 이 폰 RAM ${p.deviceRamGb}GB ✓" else "",
            style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 2.dp),
        )
        when {
            st is ModelState.Unsupported -> Text(
                (if (m.premium) "더 길고 섬세하게 답장해요. " else "") + "이 폰(RAM ${st.deviceRamGb}GB)에서는 쓸 수 없어요. RAM ${m.minRamGb}GB 이상이 필요해요.",
                style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 10.dp),
            )
            locked -> {
                Text("더 길고 섬세하게 답장해요. 프리미엄에서 깨울 수 있어요.", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
            }
            st is ModelState.Downloading -> {
                val pct = percent(st.downloadedBytes, m.sizeBytes)
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                    Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(OdColors.BrandLight, OdColors.BrandGlow))))
                }
                Row(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text("$pct% · ${gb(st.downloadedBytes)} / ${gb(m.sizeBytes)}", style = OdType.body2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text(
                        if (st.paused) "일시정지됨" else st.etaSec?.let { "약 ${((it + 59) / 60).coerceAtLeast(1)}분 남음" } ?: "",
                        style = OdType.caption2, color = c.ink2,
                    )
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.paused) OdButton("이어 받기", { p.download(m.id) }, Modifier.weight(1f), ButtonKind.Secondary, small = true)
                    else OdButton("일시정지", { p.pause(m.id) }, Modifier.weight(1f), ButtonKind.Secondary, small = true, icon = OdIcon.Pause)
                    OdButton("취소", { p.cancel(m.id) }, Modifier.weight(1f), ButtonKind.Ghost, small = true)
                }
            }
            st == ModelState.Ready -> Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OdIcon(OdIcon.Check, size = 18.dp, stroke = 2.4f, tint = c.ok)
                Text("깨어 있어요", style = OdType.body2.copy(fontWeight = FontWeight.Bold), color = c.ok, modifier = Modifier.padding(start = 6.dp).weight(1f))
                OdButton("지우기", { p.delete(m.id) }, kind = ButtonKind.Ghost, small = true)
            }
            st is ModelState.Failed -> {
                Text(st.message, style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 12.dp))
                OdButton("다시 받기", { p.download(m.id) }, Modifier.padding(top = 12.dp).fillMaxWidth(), small = true)
            }
            else -> OdButton("내려받기", { p.download(m.id) }, Modifier.padding(top = 16.dp).fillMaxWidth(), small = true, icon = OdIcon.Download)
        }
        if (wifi != null && !locked) {
            Divider(Modifier.padding(top = 14.dp, bottom = 4.dp))
            ListRow(OdIcon.Wifi, "Wi-Fi에서만 받기", height = 46.dp, trailing = { OdToggle(wifi, p::setWifiOnly) })
        }
    }
}

// ======================================================================= 잠금 설정

@Composable
fun LockSettingsScreen(onBack: () -> Unit, vm: MyViewModel = hiltViewModel()) {
    val c = Od.c
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lockOn by vm.lock.enabled.collectAsStateWithLifecycle()
    val bio by vm.lock.biometricEnabled.collectAsStateWithLifecycle()
    var settingPin by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.Dusk, 0.6f, centerY = 140.dp, radius = 380.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            OdTopBar(title = "잠금", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OniFigure(null, OniExpression.SLEEP, 96.dp)
                    Column(Modifier.weight(1f)) {
                        Text("온이가 일기를\n지키고 있을게요", style = OdType.title2)
                        Text("잠금은 무료예요. PIN은 이 폰에만 저장돼요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                    ListRow(OdIcon.Lock, "PIN 잠금", trailing = { OdToggle(lockOn, { if (it) settingPin = true else vm.lock.disable() }) })
                    if (lockOn && vm.lock.canUseBiometric()) ListRow(OdIcon.Finger, "지문·얼굴 인식", trailing = { OdToggle(bio, vm.lock::setBiometric) })
                    if (lockOn) ListRow(OdIcon.Refresh, "PIN 바꾸기", onClick = { settingPin = true })
                }
                if (lockOn) OdCard {
                    Text("다시 잠그기", style = OdType.title3)
                    Text("앱을 나갔다가 돌아올 때", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    val opts = listOf(0, 60, 300)
                    SegTabs(listOf("바로", "1분 뒤", "5분 뒤"), opts.indexOf(settings.relockAfterSec).coerceAtLeast(0), { vm.setRelock(opts[it]) })
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    ListRow(null, "최근 앱 화면 가리기", trailing = { OdToggle(settings.secureScreen, vm::setSecure) })
                    Text("최근 앱 목록에서 내용을 가리고 화면 캡처를 막아요", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(bottom = 8.dp))
                    Divider()
                    // Widgets read this; PIN lock on forces it (the toggle shows on and can't be turned off then).
                    ListRow(null, "위젯에 내용 숨기기", trailing = { OdToggle(settings.hideWidgetContent || lockOn, { if (!lockOn) vm.setHideWidget(it) }) })
                    Text(
                        if (lockOn) "잠금이 켜져 있어서 위젯 내용은 자동으로 숨겨요" else "감정 이름과 빛 색 대신 \"오늘 기록 3개\"처럼만 보여요",
                        style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    if (settingPin) Dialog(onDismissRequest = { settingPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // A full-screen dialog has its own window: give it the app's status bar icon color (white icons on paper otherwise).
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val dark = Od.c.dark
        androidx.compose.runtime.SideEffect {
            window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark }
        }
        PinSetup(onDone = { vm.setPin(it); settingPin = false }, onCancel = { settingPin = false })
    }
}


// ======================================================================= 설정 › AI

@Composable
fun AiSettingsScreen(onBack: () -> Unit, onWake: () -> Unit, vm: MyViewModel = hiltViewModel()) {
    val c = Od.c
    val s by vm.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.Dusk, 0.6f, centerY = 140.dp, radius = 380.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            OdTopBar(title = "AI 기능", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OniFigure(null, if (s.aiReply || s.aiSuggest || s.aiChat) OniExpression.DEFAULT else OniExpression.SLEEP, 96.dp)
                    Column(Modifier.weight(1f)) {
                        Text("온이가 얼마나\n도와줄지 정해요", style = OdType.title2)
                        Text("모두 끄면 순수한 일기장이 돼요. 모든 AI는 이 폰 안에서만 돌아요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    AiToggle("온이 답장", "저장하면 온이(AI)가 짧은 답장을 써 줘요", s.aiReply, vm::setAiReply)
                    Divider()
                    AiToggle("감정·태그 추천", "쓰는 동안 감정과 사람·장소 태그를 골라 보여 줘요", s.aiSuggest, vm::setAiSuggest)
                    Divider()
                    AiToggle("대화형 AI 사용", "깨운 온이가 답장·대화로 쓰기·말로 쓰기 정리를 맡아요. 끄면 준비된 문구로만 답해요", s.aiChat, vm::setAiChat)
                    if (s.aiChat) Divider()
                    if (s.aiChat) ListRow(null, "온이 깨우기 · 내려받기", null, onWake, height = 46.dp, trailing = { OdIcon(OdIcon.ChevR, size = 18.dp, tint = c.ink2) })
                }
                OdCard(flat = true, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OdIcon(OdIcon.Shield, size = 18.dp)
                        Text("안전 안내는 늘 켜져 있어요", style = OdType.title3)
                    }
                    Text(
                        "AI를 꺼도, 일기에 많이 힘든 마음이 보이면 109(자살예방상담)·1577-0199(정신건강 위기상담) 안내 카드를 보여 줘요. 이 확인은 폰 안의 단어 규칙으로만 하고, 일기는 어디에도 보내지 않아요.",
                        style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
                    )
                    Divider(Modifier.padding(vertical = 12.dp))
                    Text("온이는 의료·상담 서비스가 아니에요.", style = OdType.caption1, color = c.ink2)
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun AiToggle(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = OdType.body2.copy(fontWeight = FontWeight.SemiBold))
            Text(desc, style = OdType.caption2, color = Od.c.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        OdToggle(on, onChange)
    }
}
