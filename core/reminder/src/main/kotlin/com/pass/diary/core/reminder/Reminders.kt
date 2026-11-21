package com.pass.diary.core.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.Insights
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

private val Context.reminderStore by preferencesDataStore("reminder")
private val ENABLED = booleanPreferencesKey("enabled")
private val HOUR = intPreferencesKey("hour")
private val MINUTE = intPreferencesKey("minute")
private val DAYS = stringPreferencesKey("days") // DayOfWeek values, e.g. "12345"
private val SKIP = booleanPreferencesKey("skip_if_written")
private val YEAR_AGO = booleanPreferencesKey("year_ago")
private val RECENT = stringPreferencesKey("recent_lines")
private val AUTO = booleanPreferencesKey("auto_time")
private val WEEKLY = booleanPreferencesKey("weekly_letter")
private val FORECAST = booleanPreferencesKey("forecast")
private val FORECAST_SENT = longPreferencesKey("forecast_sent_day")

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDeps { fun entries(): EntryRepository }

object Reminders {
    /** MainActivity extra: "write" (new entry), "write:<id>" (open entry), "today" (quick mood on 오늘). */
    const val EXTRA_OPEN = "open"
    private const val CHANNEL = "reminder"
    private const val ID_REMIND = 5101
    private const val ID_YEAR = 5102
    private const val ID_LETTER = 5103
    private const val ID_FORECAST = 5104
    private const val CODE_INSIGHT = 1
    internal const val EXTRA_AT = "at"
    internal const val EXTRA_INSIGHT = "insight"
    /** Android 12+ refuses shorter windows anyway. */
    private const val WINDOW_MS = 10 * 60_000L
    /** An alarm delivered this late (phone dozing all night) is dropped rather than shown after midnight. */
    private const val TOO_LATE_MS = 2 * 60 * 60_000L

    fun prefs(context: Context): Flow<ReminderPrefs> = context.reminderStore.data.map { p ->
        val d = ReminderPrefs()
        ReminderPrefs(
            enabled = p[ENABLED] ?: d.enabled, hour = p[HOUR] ?: d.hour, minute = p[MINUTE] ?: d.minute,
            days = p[DAYS]?.mapNotNull { it.digitToIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }?.toSet()?.ifEmpty { null } ?: d.days,
            skipIfWritten = p[SKIP] ?: d.skipIfWritten, yearAgo = p[YEAR_AGO] ?: d.yearAgo,
            auto = p[AUTO] ?: d.auto, weekly = p[WEEKLY] ?: d.weekly, forecast = p[FORECAST] ?: d.forecast,
        )
    }

    suspend fun update(context: Context, change: (ReminderPrefs) -> ReminderPrefs) {
        val n = change(prefs(context).first())
        context.reminderStore.edit {
            it[ENABLED] = n.enabled; it[HOUR] = n.hour; it[MINUTE] = n.minute
            it[DAYS] = n.days.sorted().joinToString("") { d -> d.value.toString() }
            it[SKIP] = n.skipIfWritten; it[YEAR_AGO] = n.yearAgo
            it[AUTO] = n.auto; it[WEEKLY] = n.weekly; it[FORECAST] = n.forecast
        }
        schedule(context, n)
    }

    /** App start, boot, clock/zone change, app update: alarms don't survive those (or force-stop). */
    suspend fun reschedule(context: Context) = schedule(context, prefs(context).first())

    /** Notifications allowed: POST_NOTIFICATIONS on 13+, and not switched off in system settings. */
    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** True when asking for POST_NOTIFICATIONS can still help (13+ and not granted). */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * A 10-minute window alarm, not an exact one: Play only grants SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM to alarm-clock
     * style apps, and a diary nudge doesn't need the second. setWindow bounds the delay (setAndAllowWhileIdle's heuristic
     * window grows with the time until the alarm, i.e. hours for a daily one). In deep Doze it waits for the next
     * maintenance window or the screen turning on, which for a "come write" nudge is when it's useful anyway.
     * WorkManager would also work but adds its own deferral and can't wake the device at a set clock time.
     */
    private suspend fun schedule(context: Context, p: ReminderPrefs) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        val days = p.alarmDays
        if (days == null) am.cancel(alarmIntent(context, 0))
        else {
            // E12: the learned time when 자동 is on and there's enough to learn from; the user's own time otherwise.
            val time = (if (p.auto) usualTime(context) else null) ?: p.time
            val at = ReminderLogic.next(now, time, days).toInstant().toEpochMilli()
            am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, alarmIntent(context, at))
        }
        // E9 / E10 ride a second alarm of the same kind (no new permission, no exact alarms).
        val insight = ReminderLogic.nextInsight(now, p)
        if (insight == null) am.cancel(alarmIntent(context, 0, CODE_INSIGHT))
        else insight.toInstant().toEpochMilli().let { am.setWindow(AlarmManager.RTC_WAKEUP, it, WINDOW_MS, alarmIntent(context, it, CODE_INSIGHT)) }
    }

    private fun alarmIntent(context: Context, at: Long, code: Int = 0) = PendingIntent.getBroadcast(
        context, code, Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_AT, at).putExtra(EXTRA_INSIGHT, code == CODE_INSIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun entries(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, ReminderDeps::class.java).entries()

    private suspend fun allEntries(context: Context) = entries(context).observeRange("0000-01-01", "9999-12-31").first()

    /** E12: when the user usually starts writing (null = fewer than 7 days to learn from). */
    suspend fun usualTime(context: Context): java.time.LocalTime? = Insights.usualTime(allEntries(context), LocalDate.now())

    /** E10 일요일 밤 (a letter only for a week with records: no guilt for a quiet one) · E9 마음 예보 (≤ once a week). */
    internal suspend fun fireInsight(context: Context, scheduledAt: Long) {
        val p = prefs(context).first()
        schedule(context, p)
        if (scheduledAt > 0 && System.currentTimeMillis() - scheduledAt > TOO_LATE_MS) return
        if (!canNotify(context)) return
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val all = allEntries(context)
        if (ReminderLogic.isLetterTime(now)) {
            val monday = today.with(java.time.DayOfWeek.MONDAY).toString()
            if (p.weekly && all.any { it.date >= monday }) letter(context)
            return
        }
        val last = context.reminderStore.data.first()[FORECAST_SENT] ?: Long.MIN_VALUE
        if (p.forecast && today.toEpochDay() - last >= ReminderLogic.FORECAST_EVERY_DAYS && Insights.forecast(all, today) != null) {
            context.reminderStore.edit { it[FORECAST_SENT] = today.toEpochDay() }
            forecast(context)
        }
    }

    /** Fixed lines, never diary content or mood (the shade isn't behind the app lock). */
    @SuppressLint("MissingPermission")
    private fun letter(context: Context) {
        val n = builder(context)
            .setContentTitle("이번 주 온이 편지가 도착했어")
            .setContentText("한 주 동안 켜 준 빛을 모아 편지로 써 뒀어.")
            .setContentIntent(open(context, 4, "letter"))
            .build()
        NotificationManagerCompat.from(context).notify(ID_LETTER, n)
    }

    @SuppressLint("MissingPermission")
    private fun forecast(context: Context) {
        val n = builder(context)
            .setContentTitle("온이")
            .setContentText("오늘의 마음 예보가 있어. 살짝 보고 갈래?")
            .setContentIntent(open(context, 5, "today"))
            .build()
        NotificationManagerCompat.from(context).notify(ID_FORECAST, n)
    }

    internal suspend fun fire(context: Context, scheduledAt: Long) {
        val p = prefs(context).first()
        schedule(context, p) // tomorrow's (or the next chosen day's) first
        if (scheduledAt > 0 && System.currentTimeMillis() - scheduledAt > TOO_LATE_MS) return
        if (!canNotify(context)) return
        val entries = entries(context)
        val today = LocalDate.now()
        val written = entries.observeDay(today.format(EntryRepository.DATE)).first().isNotEmpty()
        if (ReminderLogic.shouldRemind(p, today.dayOfWeek, written)) remind(context)
        if (p.yearAgo) entries.observeDay(today.minusYears(1).format(EntryRepository.DATE)).first().firstOrNull()?.let { yearAgo(context, it.id) }
    }

    /** A fixed line, never diary content (safe with the app lock). */
    @SuppressLint("MissingPermission") // canNotify() checked by the caller
    private suspend fun remind(context: Context) {
        val recent = context.reminderStore.data.first()[RECENT]?.split(',')?.mapNotNull(String::toIntOrNull).orEmpty()
        val i = ReminderLogic.pick(ReminderLogic.LINES.size, recent)
        context.reminderStore.edit { it[RECENT] = ReminderLogic.remember(recent, i).joinToString(",") }
        val line = ReminderLogic.LINES[i]
        val n = builder(context)
            .setContentTitle("온이")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setContentIntent(open(context, 1, "write"))
            .addAction(0, "기분만 남기기", open(context, 2, "today"))
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMIND, n)
    }

    /** No preview of that day's entry, lock or not: the lock screen and shade aren't behind the app lock. */
    @SuppressLint("MissingPermission")
    private fun yearAgo(context: Context, entryId: String) {
        val n = builder(context)
            .setContentTitle("1년 전 오늘, 이런 하루였어")
            .setContentText("그날의 기록이 남아 있어. 살짝 열어 볼래?")
            .setContentIntent(open(context, 3, "write:$entryId"))
            .build()
        NotificationManagerCompat.from(context).notify(ID_YEAR, n)
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "기록 알림", NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_oni)
            .setColor(0xFFF5A957.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
    }

    private fun open(context: Context, code: Int, target: String): PendingIntent {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .putExtra(EXTRA_OPEN, target)
            // An open app gets onNewIntent (back stack and drafts kept) instead of a fresh activity.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val at = intent.getLongExtra(Reminders.EXTRA_AT, 0)
        if (intent.getBooleanExtra(Reminders.EXTRA_INSIGHT, false)) Reminders.fireInsight(context, at) else Reminders.fire(context, at)
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async { Reminders.reschedule(context) }
}

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch { try { runCatching { block() } } finally { pending.finish() } }
}
