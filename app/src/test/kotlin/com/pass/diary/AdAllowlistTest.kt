package com.pass.diary

import com.pass.diary.feature.calendar.CalendarRoute
import com.pass.diary.feature.calendar.SearchRoute
import com.pass.diary.feature.my.LockSettingsRoute
import com.pass.diary.feature.my.PremiumRoute
import com.pass.diary.feature.report.ReportRoute
import com.pass.diary.feature.report.YearLightRoute
import com.pass.diary.feature.today.TodayRoute
import com.pass.diary.feature.write.ChatRoute
import com.pass.diary.feature.write.ReplyRoute
import com.pass.diary.feature.write.WriteRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.serializer
import org.junit.Test

class AdAllowlistTest {
    // What Navigation actually puts in NavDestination.route: the serial name (R8-proof), not the (minified) class name.
    @OptIn(kotlinx.serialization.InternalSerializationApi::class)
    private fun route(k: kotlin.reflect.KClass<*>, args: String = "") = k.serializer().descriptor.serialName + args

    @Test fun onlyCalendarAndReport() {
        assertTrue(AdAllowlist.allows(route(CalendarRoute::class)))
        assertTrue(AdAllowlist.allows(route(ReportRoute::class)))
        assertTrue(AdAllowlist.routes.size == 2)
    }

    /** PLAN §4: never on write / save / reply (incl. crisis card) / lock / onboarding. */
    @Test fun neverOnProtectedScreens() {
        listOf(
            route(WriteRoute::class, "?entryId=x&mood=joy"), route(ChatRoute::class, "?entryId=x"), route(ReplyRoute::class, "/abc"),
            route(LockSettingsRoute::class), route(TodayRoute::class), route(YearLightRoute::class), route(PremiumRoute::class),
            route(SearchRoute::class, "?q=x"), null, "", "onboarding", "lock",
        ).forEach { assertFalse("ads allowed on $it", AdAllowlist.allows(it)) }
    }

    /** Review #11: the allowlist must not depend on class names, which R8 renames. */
    @Test fun usesSerialNamesNotClassNames() {
        assertTrue(AdAllowlist.routes == setOf("com.pass.diary.feature.calendar.CalendarRoute", "com.pass.diary.feature.report.ReportRoute"))
    }

    @Test fun prefixIsNotEnough() {
        assertFalse(AdAllowlist.allows(route(CalendarRoute::class) + "Fake"))
    }
}
