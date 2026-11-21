package com.pass.diary

import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.pass.diary.core.billing.AdManager
import com.pass.diary.core.billing.Billing
import com.pass.diary.core.billing.LocalAdManager
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.designsystem.OdLook
import com.pass.diary.core.designsystem.OdTheme
import com.pass.diary.core.designsystem.LocalWindowLayout
import com.pass.diary.core.designsystem.WindowLayout
import com.pass.diary.core.designsystem.currentWindowLayout
import com.pass.diary.core.light.Palette
import com.pass.diary.core.model.Gating
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.pass.diary.core.legacy.LegacyMigrationResult
import com.pass.diary.core.model.Settings
import com.pass.diary.core.model.ThemeMode
import com.pass.diary.core.security.LockManager
import com.pass.diary.core.security.LockScreen
import com.pass.diary.core.security.PinRecovery
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncLocalStore
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    startup: Startup,
    private val settingsRepo: SettingsRepository,
    billing: Billing,
    entries: EntryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {
    private val prefs = AppPrefs(context)
    val onboarded = prefs.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** [ai] = the consent page's "온이 답장·감정 추천 받기": sets every 설정 › AI switch (crisis detection is not one). */
    fun finishOnboarding(reminder: Boolean, ai: Boolean) = viewModelScope.launch { settingsRepo.setAiAll(ai); prefs.finishOnboarding(reminder) }
    val ready = MutableStateFlow(false)
    val migrated = MutableStateFlow<LegacyMigrationResult?>(null)
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val premium = billing.isPremium

    /** 꾸미기 as shown: premium/streak/trial gating applied (a finished trial or refund falls back to the free choice). */
    val look = combine(settingsRepo.settings, billing.isPremium, entries.observeRange("0000-01-01", "9999-12-31").map { l -> Gating.bestStreak(l.map { it.date }) }) { s, p, streak ->
        Gating.effective(s, p, streak, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            migrated.value = startup.run()
            ready.value = true
        }
    }
}

data class SecureWindow(val flagSecure: Boolean, val recentsScreenshot: Boolean)

/**
 * [secureScreen] = 설정's "화면 캡처 막기" (FLAG_SECURE: no screenshots, blank in recents). With a PIN on, the recents
 * thumbnail must never show a diary whatever that toggle says: API 33+ hides just the thumbnail, older phones need FLAG_SECURE.
 */
fun secureWindow(secureScreen: Boolean, pinLock: Boolean, sdk: Int) = SecureWindow(
    flagSecure = secureScreen || (pinLock && sdk < 33),
    recentsScreenshot = !pinLock && !secureScreen,
)

/** FragmentActivity (not just ComponentActivity) because BiometricPrompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var lock: LockManager
    @Inject lateinit var ads: AdManager
    @Inject lateinit var driveAuth: DriveAuth
    @Inject lateinit var syncStore: SyncLocalStore
    private val vm: MainViewModel by viewModels()
    private val forcedUpdate = ForcedUpdate(this)
    /** 기록 알림 tap target (Reminders.EXTRA_OPEN) or debug `--es nav`, cleared once DiaryApp has navigated. */
    private val openNav = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !vm.ready.value || vm.settings.value == null || vm.onboarded.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) forcedUpdate.check()
        if (savedInstanceState == null) {
            openNav.value = intent.getStringExtra(com.pass.diary.core.reminder.Reminders.EXTRA_OPEN) ?: if (BuildConfig.DEBUG) intent.getStringExtra("nav") else null
        }
        ads.gatherConsent(this) // also restores the premium purchase (ARCHITECTURE §7: every app open)
        setContent {
            val ready by vm.ready.collectAsStateWithLifecycle()
            val s by vm.settings.collectAsStateWithLifecycle()
            val settings = s ?: return@setContent
            val onboarded by vm.onboarded.collectAsStateWithLifecycle()
            if (!ready || onboarded == null) return@setContent
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT, ThemeMode.PAPER -> false
                ThemeMode.DARK -> true
            }
            val premium by vm.premium.collectAsStateWithLifecycle()
            val look = vm.look.collectAsStateWithLifecycle().value ?: settings
            // 꾸미기 applies app-wide: palette + font through OdLook (snapshot state), text size through fontScale.
            OdLook.palette = Palette[look.palette]
            OdLook.font = look.font
            val lockOn by lock.enabled.collectAsStateWithLifecycle()
            LaunchedEffect(look.palette, look.themeMode, settings.hideWidgetContent, lockOn) { runCatching { updateWidgets(applicationContext) } }
            val density = LocalDensity.current
            // Tablets / unfolded foldables: rail + panes (LocalWindowLayout) and type one step bigger.
            val window = currentWindowLayout()
            val scaled = Density(density.density, density.fontScale * Settings.TEXT_SCALES[look.textSize] * if (window.large) WindowLayout.LARGE_FONT_SCALE else 1f)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LaunchedEffect(settings.secureScreen, lockOn) { applySecure(secureWindow(settings.secureScreen, lockOn, Build.VERSION.SDK_INT)) }
            OdTheme(darkTheme = dark, premium = premium, paper = settings.themeMode == ThemeMode.PAPER) {
              CompositionLocalProvider(LocalDensity provides scaled, LocalAdManager provides ads, LocalWindowLayout provides window) {
                val locked by lock.locked.collectAsStateWithLifecycle()
                val bio by lock.biometricEnabled.collectAsStateWithLifecycle()
                // The lock is an overlay: the app below stays composed (NavController, back stack, a chat or draft in
                // progress survive a relock) but is hidden from touch and accessibility while locked.
                Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                    if (onboarded == false) Onboarding(onDone = vm::finishOnboarding)
                    else {
                        val open by openNav.collectAsStateWithLifecycle()
                        DiaryApp(debugNav = open, adsAllowed = !locked) // no ad under the lock
                        LaunchedEffect(open) { openNav.value = null }
                    }
                }
                if (locked) {
                    val title = stringResource(com.pass.diary.core.security.R.string.lock_biometric_title)
                    val negative = stringResource(com.pass.diary.core.security.R.string.lock_biometric_negative)
                    val prompt = { lock.promptBiometric(this, title, negative) }
                    LaunchedEffect(Unit) { prompt() }
                    val clientId = stringResource(com.pass.diary.feature.my.R.string.google_web_client_id)
                    val account by produceState<String?>(null) { value = syncStore.meta(DriveAuth.META_ACCOUNT) }
                    val recovery = PinRecovery(
                        driveAccount = account?.takeIf { clientId.isNotBlank() },
                        reauthenticate = { driveAuth.reauthenticate(this@MainActivity, clientId) },
                        wipeAppData = { getSystemService(ActivityManager::class.java).clearApplicationUserData() },
                    )
                    // A full-size pointer target on top: hit testing stops here, so no touch reaches the app underneath.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
                        LockScreen(lock, onBiometric = prompt.takeIf { bio && lock.canUseBiometric() }, onBack = { finish() }, recovery = recovery)
                    }
                }
                val m by vm.migrated.collectAsStateWithLifecycle()
                m?.let { r ->
                    AlertDialog(
                        onDismissRequest = { vm.migrated.value = null },
                        title = { Text(stringResource(R.string.migrated_title)) },
                        text = { Text(stringResource(R.string.migrated_body, r.migrated)) },
                        confirmButton = { TextButton(onClick = { vm.migrated.value = null }) { Text(stringResource(R.string.ok)) } },
                    )
                }
              }
            }
        }
    }

    private fun applySecure(w: SecureWindow) {
        if (w.flagSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(w.recentsScreenshot)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(com.pass.diary.core.reminder.Reminders.EXTRA_OPEN)?.let { openNav.value = it }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground(vm.settings.value?.relockAfterSec ?: Settings().relockAfterSec)
    }

    /** Also resumes an immediate update left in progress (Play's recommended onResume check). */
    override fun onResume() {
        super.onResume()
        forcedUpdate.check()
    }

    override fun onStop() {
        super.onStop()
        // Config changes (rotation) aren't "leaving the app".
        if (!isChangingConfigurations) lock.onBackground()
    }
}
