package com.pass.diary

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.pass.diary.core.designsystem.LocalSelectedRow
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.PaneDivider
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.readableWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.pass.diary.core.billing.LocalAdsAllowed
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniMono
import com.pass.diary.feature.calendar.CalendarRoute
import com.pass.diary.feature.calendar.CalendarScreen
import com.pass.diary.feature.calendar.SearchRoute
import com.pass.diary.feature.calendar.SearchScreen
import com.pass.diary.feature.my.AiSettingsRoute
import com.pass.diary.feature.my.AiSettingsScreen
import com.pass.diary.feature.my.DecorateRoute
import com.pass.diary.feature.my.ExportRoute
import com.pass.diary.feature.my.ExportScreen
import com.pass.diary.feature.my.TrashRoute
import com.pass.diary.feature.my.TrashScreen
import com.pass.diary.feature.my.DecorateScreen
import com.pass.diary.feature.my.LicensesRoute
import com.pass.diary.feature.my.LicensesScreen
import com.pass.diary.feature.my.LockSettingsRoute
import com.pass.diary.feature.my.LockSettingsScreen
import com.pass.diary.feature.my.MyRoute
import com.pass.diary.feature.my.MyScreen
import com.pass.diary.feature.my.PremiumRoute
import com.pass.diary.feature.my.PremiumScreen
import com.pass.diary.feature.my.ReminderSettingsRoute
import com.pass.diary.feature.my.ReminderSettingsScreen
import com.pass.diary.feature.my.WakeRoute
import com.pass.diary.feature.my.WakeScreen
import com.pass.diary.feature.report.LettersRoute
import com.pass.diary.feature.report.LettersScreen
import com.pass.diary.feature.report.ReportRoute
import com.pass.diary.feature.report.ReportScreen
import com.pass.diary.feature.report.YearLightRoute
import com.pass.diary.feature.report.YearLightScreen
import com.pass.diary.feature.today.TodayRoute
import com.pass.diary.feature.today.TodayScreen
import com.pass.diary.feature.write.ChatRoute
import com.pass.diary.feature.write.ChatScreen
import com.pass.diary.feature.write.ReplyRoute
import com.pass.diary.feature.write.ReplyScreen
import com.pass.diary.feature.write.WriteRoute
import com.pass.diary.feature.write.WriteScreen
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector?, val odIcon: OdIcon?)

private val tabs = listOf(
    Tab(TodayRoute, TodayRoute::class, "오늘", OniMono, null),
    Tab(CalendarRoute, CalendarRoute::class, "캘린더", null, OdIcon.Cal),
    Tab(ReportRoute, ReportRoute::class, "리포트", null, OdIcon.Chart),
    Tab(MyRoute, MyRoute::class, "마이", null, OdIcon.User),
)

/** App shell: 4 tabs + the raised 쓰기 button (시안 nav); every other screen is full screen above them. */
@Composable
fun DiaryApp(
    start: Any = TodayRoute, nav: NavHostController = rememberNavController(), debugNav: String? = null, adsAllowed: Boolean = true,
    entries: com.pass.diary.core.data.EntryRepository? = null,
) {
    // Debug builds: `adb shell am start -n com.pass.diary/.MainActivity --es nav reply:<id>`. Any build: 기록 알림 taps
    // (Reminders.EXTRA_OPEN: "write", "write:<id>", "today").
    androidx.compose.runtime.LaunchedEffect(debugNav) {
        val (r, arg) = (debugNav ?: return@LaunchedEffect).split(":", limit = 2).let { it[0] to it.getOrNull(1) }
        nav.navigate(
            when (r) {
                "calendar" -> CalendarRoute; "search" -> SearchRoute(arg); "report" -> ReportRoute; "year" -> YearLightRoute; "letters" -> LettersRoute
                "my" -> MyRoute; "premium" -> PremiumRoute; "wake" -> WakeRoute; "lock" -> LockSettingsRoute; "decorate" -> DecorateRoute; "licenses" -> LicensesRoute; "ai" -> AiSettingsRoute; "reminder" -> ReminderSettingsRoute
                "trash" -> TrashRoute; "export" -> ExportRoute
                "write" -> WriteRoute(entryId = arg); "question" -> WriteRoute(question = arg); "chat" -> ChatRoute(arg); "reply" -> ReplyRoute(arg!!)
                else -> TodayRoute
            },
        ) { if (r == "today") { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true } }
    }
    val back by nav.currentBackStackEntryAsState()
    val dest = back?.destination
    val onTab = tabs.any { t -> dest?.hasRoute(t.type) == true }
    val layout = Od.layout
    // Tablets, unfolded foldables and landscape phones: the tabs and 쓰기 move into a rail on the left.
    val rail = onTab && layout.rail
    val goTab: (Any) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Box(Modifier.fillMaxSize().background(Od.c.bg)) {
      Row(Modifier.fillMaxSize()) {
        if (rail) SideRail(dest, onTab = goTab, onWrite = { nav.navigate(WriteRoute()) })
        // Side insets (landscape cutout / 3-button nav bar) are kept clear here; the rail takes the start side itself.
        Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal))) {
      CompositionLocalProvider(LocalAdsAllowed provides (adsAllowed && AdAllowlist.allows(dest?.route))) {
        NavHost(
            nav, startDestination = start, modifier = Modifier.fillMaxSize(),
            // README §6: 300 ms fade + 8dp rise between screens.
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 100 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            composable<TodayRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                TodayScreen(
                    onWrite = { mood -> nav.navigate(WriteRoute(mood = mood)) },
                    onWriteQuestion = { q -> nav.navigate(WriteRoute(question = q)) },
                    onOpen = { nav.navigate(WriteRoute(entryId = it)) },
                    onLetters = { nav.navigate(LettersRoute) },
                    onBackup = { goTab(MyRoute) },
                )
            }
            composable<CalendarRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                CalendarScreen(
                    onOpen = { nav.navigate(WriteRoute(entryId = it)) },
                    onWriteOn = { nav.navigate(WriteRoute(date = it)) },
                    onSearch = { nav.navigate(SearchRoute()) },
                )
            }
            composable<SearchRoute> { SearchScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(WriteRoute(entryId = it)) }) }
            composable<ReportRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                ReportScreen(onLetters = { nav.navigate(LettersRoute) }, onYear = { nav.navigate(YearLightRoute) })
            }
            composable<YearLightRoute> { Readable { YearLightScreen(onBack = { nav.popBackStack() }, onPremium = { nav.navigate(PremiumRoute) }) } }
            composable<LettersRoute> { Readable { LettersScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(WriteRoute(entryId = it)) }) } }
            composable<MyRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                if (layout.twoPane) MyTwoPane(nav)
                else MyScreen(
                    onPremium = { nav.navigate(PremiumRoute) }, onWake = { nav.navigate(WakeRoute) },
                    onLock = { nav.navigate(LockSettingsRoute) }, onDecorate = { nav.navigate(DecorateRoute) },
                    onLicenses = { nav.navigate(LicensesRoute) }, onAi = { nav.navigate(AiSettingsRoute) },
                    onReminder = { nav.navigate(ReminderSettingsRoute) },
                    onTrash = { nav.navigate(TrashRoute) }, onExport = { nav.navigate(ExportRoute) },
                )
            }
            composable<PremiumRoute> { Readable { PremiumScreen(onClose = { nav.popBackStack() }) } }
            composable<WakeRoute> { Readable { WakeScreen(onBack = { nav.popBackStack() }) } }
            composable<LockSettingsRoute> { Readable { LockSettingsScreen(onBack = { nav.popBackStack() }) } }
            composable<DecorateRoute> { Readable { DecorateScreen(onBack = { nav.popBackStack() }, onPremium = { nav.navigate(PremiumRoute) }) } }
            composable<LicensesRoute> { Readable { LicensesScreen(onBack = { nav.popBackStack() }) } }
            composable<ReminderSettingsRoute> { Readable { ReminderSettingsScreen(onBack = { nav.popBackStack() }) } }
            composable<TrashRoute> { Readable { TrashScreen(onBack = { nav.popBackStack() }) } }
            composable<ExportRoute> { Readable { ExportScreen(onBack = { nav.popBackStack() }) } }
            composable<AiSettingsRoute> { Readable { AiSettingsScreen(onBack = { nav.popBackStack() }, onWake = { nav.navigate(WakeRoute) }) } }
            composable<WriteRoute> {
                WriteScreen(
                    onDone = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate(ReplyRoute(id)) { popUpTo<WriteRoute> { inclusive = true } } },
                    onChat = { id -> nav.navigate(ChatRoute(entryId = id)) },
                )
            }
            composable<ChatRoute> {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onComposed = { id ->
                        // Opened from an editor: that editor goes too, so 저장 → 답장 → 확인 lands on home, not on it.
                        nav.popBackStack<ChatRoute>(inclusive = true)
                        if (nav.currentBackStackEntry?.destination?.hasRoute<WriteRoute>() == true) nav.popBackStack()
                        nav.navigate(WriteRoute(entryId = id))
                    },
                    onWake = { nav.navigate(WakeRoute) },
                )
            }
            composable<ReplyRoute> {
                ReplyScreen(
                    onClose = { nav.popBackStack() },
                    onOpenEntry = { id -> nav.navigate(WriteRoute(entryId = id)) { popUpTo<ReplyRoute> { inclusive = true } } },
                    onLetters = { nav.navigate(LettersRoute) { popUpTo<ReplyRoute> { inclusive = true } } },
                )
            }
        }
      }
        }
      }
        // Status-bar scrim: scrolled content never runs under the clock/icons.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Od.c.bg.copy(alpha = 0.94f)))
        if (onTab && !layout.rail) BottomBar(dest, Modifier.align(Alignment.BottomCenter), onTab = goTab, onWrite = { nav.navigate(WriteRoute()) })
        TrashUndo(entries, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (onTab && !layout.rail) layout.bottomBar + 24.dp else 24.dp))
    }
}

/** "휴지통으로 옮겼어요 · 되돌리기" after any 지우기, wherever the user lands next. */
@Composable
private fun TrashUndo(entries: com.pass.diary.core.data.EntryRepository?, modifier: Modifier) {
    val host = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(entries) {
        entries?.trashed?.collect { id ->
            host.currentSnackbarData?.dismiss()
            scope.launch {
                val r = host.showSnackbar("휴지통으로 옮겼어요", actionLabel = "되돌리기", duration = androidx.compose.material3.SnackbarDuration.Short)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) entries.restore(id)
            }
        }
    }
    androidx.compose.material3.SnackbarHost(host, modifier)
}

/** Secondary full-screen pages on a big screen: one centered column instead of lines across the whole tablet. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
    if (!Od.layout.rail) return content()
    Box(Modifier.fillMaxSize().background(Od.c.bg)) { Box(Modifier.readableWidth(720.dp).fillMaxHeight()) { content() } }
}

private val myRows = mapOf("wake" to "온이 깨우기", "lock" to "잠금", "decorate" to "꾸미기", "ai" to "AI 기능", "reminder" to "알림", "export" to "내보내기", "trash" to "휴지통", "licenses" to "오픈소스 라이선스")

/** 마이 on a wide screen: settings list on the left, the chosen page on the right (its row highlighted). */
@Composable
private fun MyTwoPane(nav: NavHostController) {
    var sel by rememberSaveable { mutableStateOf<String?>("wake") }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f)) {
            CompositionLocalProvider(LocalSelectedRow provides myRows[sel]) {
                MyScreen(
                    onPremium = { nav.navigate(PremiumRoute) }, onWake = { sel = "wake" }, onLock = { sel = "lock" },
                    onDecorate = { sel = "decorate" }, onLicenses = { sel = "licenses" }, onAi = { sel = "ai" }, onReminder = { sel = "reminder" },
                    onTrash = { sel = "trash" }, onExport = { sel = "export" },
                )
            }
        }
        PaneDivider()
        Box(Modifier.weight(0.58f)) {
            val close = { sel = null }
            when (sel) {
                "wake" -> WakeScreen(onBack = close)
                "lock" -> LockSettingsScreen(onBack = close)
                "decorate" -> DecorateScreen(onBack = close, onPremium = { nav.navigate(PremiumRoute) })
                "ai" -> AiSettingsScreen(onBack = close, onWake = { sel = "wake" })
                "licenses" -> LicensesScreen(onBack = close)
                "reminder" -> ReminderSettingsScreen(onBack = close)
                "trash" -> TrashScreen(onBack = close)
                "export" -> ExportScreen(onBack = close)
                else -> PaneEmpty("왼쪽에서 살펴볼 설정을 골라 주세요")
            }
        }
    }
}

/** Empty detail pane: Oni dozing and one line. */
@Composable
internal fun PaneEmpty(text: String) {
    Column(Modifier.fillMaxSize().background(Od.c.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        OniFigure(null, OniExpression.SLEEP, 140.dp)
        Text(text, style = OdType.body2, color = Od.c.ink2, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Tablet / landscape navigation: 쓰기 on top (the raised button's place on phones), then the 4 tabs. */
@Composable
private fun SideRail(dest: NavDestination?, onTab: (Any) -> Unit, onWrite: () -> Unit) {
    val c = Od.c
    val short = Od.layout.short // landscape phone: tighter, so all four tabs fit
    Row(Modifier.fillMaxHeight().background(c.navBg)) {
        Column(
            Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .width(88.dp).verticalScroll(rememberScrollState()).padding(top = if (short) 8.dp else 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(remember { MutableInteractionSource() }, null, onClick = onWrite).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(if (short) 48.dp else 56.dp).clip(RoundedCornerShape(if (short) 16.dp else 18.dp)).background(c.primary), contentAlignment = Alignment.Center) {
                    OdIcon(OdIcon.Pen, size = 24.dp, stroke = 2f, tint = c.onPrimary, contentDescription = "쓰기")
                }
                Text("쓰기", style = OdType.tab, color = c.ink, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(if (short) 6.dp else 28.dp))
            tabs.forEach { t ->
                val on = dest?.hasRoute(t.type) == true
                val color = if (on) c.ink else c.ink3
                Column(
                    Modifier.padding(vertical = if (short) 2.dp else 6.dp).clickable(remember { MutableInteractionSource() }, null) { onTab(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 4.dp),
                ) {
                    Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.surface2 else Color.Transparent), contentAlignment = Alignment.Center) {
                        if (t.icon != null) Icon(t.icon, t.label, Modifier.size(24.dp), tint = color) else OdIcon(t.odIcon!!, size = 24.dp, tint = color)
                    }
                    Text(t.label, style = OdType.tab, color = color)
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
    }
}

/** 68dp bar + gesture inset, opaque paper/night (content scrolls under it unseen), hairline on top; 52dp 쓰기 button rises 26dp above it. */
@Composable
private fun BottomBar(dest: NavDestination?, modifier: Modifier, onTab: (Any) -> Unit, onWrite: () -> Unit) {
    val c = Od.c
    Column(modifier.fillMaxWidth().background(c.navBg)) {
        Divider()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.take(2).forEach { TabItem(it, dest) { onTab(it.route) } }
            Column(Modifier.width(60.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onWrite), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.offset(y = (-14).dp).size(52.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                    OdIcon(OdIcon.Pen, size = 24.dp, stroke = 2f, tint = c.onPrimary, contentDescription = "쓰기")
                }
                Text("쓰기", style = OdType.tab, color = c.ink, modifier = Modifier.offset(y = (-11).dp))
            }
            tabs.drop(2).forEach { TabItem(it, dest) { onTab(it.route) } }
        }
    }
}

@Composable
private fun TabItem(t: Tab, dest: NavDestination?, onClick: () -> Unit) {
    val c = Od.c
    val on = dest?.hasRoute(t.type) == true
    val color = if (on) c.ink else c.ink3
    Column(
        Modifier.width(60.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (t.icon != null) Icon(t.icon, t.label, Modifier.size(24.dp), tint = color) else OdIcon(t.odIcon!!, size = 24.dp, tint = color)
        Text(t.label, style = OdType.tab, color = color)
    }
}
