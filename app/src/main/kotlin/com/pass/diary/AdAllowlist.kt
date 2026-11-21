package com.pass.diary

import com.pass.diary.feature.calendar.CalendarRoute
import com.pass.diary.feature.report.ReportRoute
import kotlinx.serialization.serializer

/**
 * The only screens where an ad slot may render (PLAN §4, ARCHITECTURE §7). DiaryApp turns this into LocalAdsAllowed;
 * everything else — write, chat, reply/crisis, lock, onboarding (outside DiaryApp: default false) — shows no ads.
 * Search, letters, year light and premium aren't listed either. Add a screen here only with a PLAN change.
 *
 * Navigation builds a type-safe route string from the route's serial name, a compile-time constant that survives R8
 * (KClass.qualifiedName does not: minified it is "a.b.c", and the allowlist would silently match nothing).
 */
object AdAllowlist {
    val routes: Set<String> = setOf(serializer<CalendarRoute>(), serializer<ReportRoute>()).map { it.descriptor.serialName }.toSet()

    /** [route] = NavDestination.route ("com.pass.diary.feature.write.WriteRoute?mood=joy" for type-safe routes). */
    fun allows(route: String?): Boolean = route != null && route.substringBefore('?').substringBefore('/') in routes
}
