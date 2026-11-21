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
import com.pass.diary.feature.my.DecorateScreen
import com.pass.diary.feature.my.LicensesRoute
import com.pass.diary.feature.my.LicensesScreen
import com.pass.diary.feature.my.LockSettingsRoute
import com.pass.diary.feature.my.LockSettingsScreen
import com.pass.diary.feature.my.MyRoute
import com.pass.diary.feature.my.MyScreen
import com.pass.diary.feature.my.PremiumRoute
import com.pass.diary.feature.my.PremiumScreen
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
fun DiaryApp(start: Any = TodayRoute, nav: NavHostController = rememberNavController(), debugNav: String? = null, adsAllowed: Boolean = true) {
    // Debug builds only (MainActivity passes it): `adb shell am start -n com.pass.diary/.MainActivity --es nav reply:<id>`.
    androidx.compose.runtime.LaunchedEffect(debugNav) {
        val (r, arg) = (debugNav ?: return@LaunchedEffect).split(":", limit = 2).let { it[0] to it.getOrNull(1) }
        nav.navigate(
            when (r) {
                "calendar" -> CalendarRoute; "search" -> SearchRoute(arg); "report" -> ReportRoute; "year" -> YearLightRoute; "letters" -> LettersRoute
                "my" -> MyRoute; "premium" -> PremiumRoute; "wake" -> WakeRoute; "lock" -> LockSettingsRoute; "decorate" -> DecorateRoute; "licenses" -> LicensesRoute; "ai" -> AiSettingsRoute
                "write" -> WriteRoute(entryId = arg); "question" -> WriteRoute(question = arg); "chat" -> ChatRoute(arg); "reply" -> ReplyRoute(arg!!)
                else -> TodayRoute
            },
        )
    }
    val back by nav.currentBackStackEntryAsState()
    val dest = back?.destination
    val onTab = tabs.any { t -> dest?.hasRoute(t.type) == true }
    Box(Modifier.fillMaxSize().background(Od.c.bg)) {
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
            composable<YearLightRoute> { YearLightScreen(onBack = { nav.popBackStack() }, onPremium = { nav.navigate(PremiumRoute) }) }
            composable<LettersRoute> { LettersScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(WriteRoute(entryId = it)) }) }
            composable<MyRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                MyScreen(
                    onPremium = { nav.navigate(PremiumRoute) }, onWake = { nav.navigate(WakeRoute) },
                    onLock = { nav.navigate(LockSettingsRoute) }, onDecorate = { nav.navigate(DecorateRoute) },
                    onLicenses = { nav.navigate(LicensesRoute) }, onAi = { nav.navigate(AiSettingsRoute) },
                )
            }
            composable<PremiumRoute> { PremiumScreen(onClose = { nav.popBackStack() }) }
            composable<WakeRoute> { WakeScreen(onBack = { nav.popBackStack() }) }
            composable<LockSettingsRoute> { LockSettingsScreen(onBack = { nav.popBackStack() }) }
            composable<DecorateRoute> { DecorateScreen(onBack = { nav.popBackStack() }, onPremium = { nav.navigate(PremiumRoute) }) }
            composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
            composable<AiSettingsRoute> { AiSettingsScreen(onBack = { nav.popBackStack() }, onWake = { nav.navigate(WakeRoute) }) }
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
                    onComposed = { id -> nav.navigate(WriteRoute(entryId = id)) { popUpTo<ChatRoute> { inclusive = true } } },
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
        // Status-bar scrim: scrolled content never runs under the clock/icons.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars).background(Od.c.bg.copy(alpha = 0.94f)))
        if (onTab) BottomBar(dest, Modifier.align(Alignment.BottomCenter), onTab = { route ->
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }, onWrite = { nav.navigate(WriteRoute()) })
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
