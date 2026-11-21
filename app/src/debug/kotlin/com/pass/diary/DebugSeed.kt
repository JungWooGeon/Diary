package com.pass.diary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EmotionKey.*
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Photo
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

/**
 * Debug-only sample data that mirrors the 시안 (this month + a year of light).
 *   adb shell am broadcast -n com.pass.diary/.DebugSeedReceiver [--ez today false] [--ez crisis true] [--ez clear true] [--es pin mood|oni|month]
 * Then open a screen: adb shell am start -n com.pass.diary/.MainActivity --es nav reply:seed-today-3 (see DiaryApp debugNav).
 */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: EntryRepository
    @Inject lateinit var replies: ReplyDao
    @Inject lateinit var db: DiaryDatabase
    @Inject lateinit var chat: com.pass.diary.core.data.db.ChatMessageDao

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("pin")?.let { w ->
            val cls = mapOf("mood" to MoodWidgetReceiver::class.java, "oni" to OniWidgetReceiver::class.java, "month" to MonthWidgetReceiver::class.java).getValue(w)
            context.getSystemService(android.appwidget.AppWidgetManager::class.java).requestPinAppWidget(android.content.ComponentName(context, cls), null, null)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.clearAllTables()
                AppPrefs(context).finishOnboarding(false)
                if (!intent.getBooleanExtra("clear", false)) seed(context, intent.getBooleanExtra("today", true), intent.getBooleanExtra("crisis", false))
                runCatching { updateWidgets(context) }
            } finally { pending.finish() }
        }
    }

    private fun e(k: EmotionKey, i: Int) = EntryEmotion(k, i)
    private fun act(v: String) = Tag(TagKind.ACTIVITY, v)
    private fun person(v: String) = Tag(TagKind.PERSON, v)
    private fun place(v: String) = Tag(TagKind.PLACE, v)

    private suspend fun seed(context: Context, withToday: Boolean, crisis: Boolean) {
        val today = LocalDate.now()
        val ym = today.withDayOfMonth(1)
        val photos = File(context.filesDir, "photos").apply { mkdirs() }
        fun photo(entryId: String, kind: Int): Photo {
            val id = "$entryId-p$kind"
            val f = File(photos, "$id.jpg")
            if (!f.exists()) f.outputStream().use { gradient(kind).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return Photo(id = id, entryId = entryId, localPath = f.absolutePath, w = 600, h = 600)
        }
        var n = 0
        suspend fun put(
            date: LocalDate, time: String, emotions: List<EntryEmotion>, title: String = "", body: String = "", tags: List<Tag> = emptyList(),
            photoKinds: List<Int> = emptyList(), reply: String? = null, weather: String? = null, id: String = "seed-${date}-$time",
        ) {
            val at = date.atTime(time.take(2).toInt(), time.takeLast(2).toInt()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val entry = Entry(
                id = id, date = date.toString(), time = time, createdAt = at, updatedAt = at, title = title, body = body, emotions = emotions,
                tags = tags, photos = photoKinds.map { photo(id, it) }, weather = weather,
            )
            repo.saveExternal(listOf(entry))
            if (reply != null) replies.upsert(ReplyEntity(id, reply, "template", null, at + 90_000, false))
            n++
        }
        fun d(day: Int) = ym.withDayOfMonth(day)

        // ---- a year of light (Jan … last month), seasonal like 06_리포트_2 ----
        val rnd = Random(11)
        var day = LocalDate.of(today.year, 1, 1)
        val keys = mapOf(
            EmotionGroup.CALM to listOf(COMFY, SERENE, RELIEVED, LIGHT), EmotionGroup.BRIGHT to listOf(JOY, PROUD, EXCITED, GRATEFUL),
            EmotionGroup.FLUTTER to listOf(FLUTTER, LOVE, HOPE, MISS), EmotionGroup.LOW to listOf(SAD, DOWN, LONELY, HURT),
            EmotionGroup.WORN to listOf(TIRED, ANXIOUS, ANNOYED, ANGRY),
        )
        val lines = mapOf(
            EmotionGroup.CALM to listOf("동네 한 바퀴 산책했다. 바람이 좋았다." to "산책", "오랜만에 집에서 푹 쉬었다." to null),
            EmotionGroup.BRIGHT to listOf("민지랑 점심 먹으면서 한참 웃었다." to "민지", "작은 일이지만 끝까지 해냈다." to null),
            EmotionGroup.FLUTTER to listOf("주말 여행 계획을 세웠다. 벌써 설렌다." to null, "오랜만에 연락이 왔다." to null),
            EmotionGroup.LOW to listOf("괜히 마음이 가라앉는 날이었다." to null, "비가 와서 하루 종일 집에 있었다." to null),
            EmotionGroup.WORN to listOf("야근하고 늦게 들어왔다." to "야근", "할 일이 너무 많아서 정신이 없었다." to "야근"),
        )
        while (day.isBefore(ym)) {
            val doy = day.dayOfYear
            if (rnd.nextFloat() > 0.14f) {
                val w = mapOf(
                    EmotionGroup.CALM to 1.0, EmotionGroup.LOW to 1.1 * Math.pow(Math.cos(doy / 58.0), 8.0),
                    EmotionGroup.WORN to 0.55 + 0.5 * Math.sin(doy / 365.0 * 6.28 * 3),
                    EmotionGroup.FLUTTER to 1.15 * Math.exp(-Math.pow((doy - 100) / 40.0, 2.0)),
                    EmotionGroup.BRIGHT to 1.2 * Math.exp(-Math.pow((doy - 140) / 45.0, 2.0)) + 0.4,
                )
                val g = w.maxBy { it.value + rnd.nextDouble() * 0.9 }.key
                val k = keys.getValue(g)[rnd.nextInt(4)]
                val (text, tag) = lines.getValue(g)[rnd.nextInt(2)]
                val hour = listOf(8, 12, 15, 19, 22, 23)[rnd.nextInt(6)]
                put(
                    day, "%02d:%02d".format(hour, rnd.nextInt(60)), listOf(e(k, 2 + rnd.nextInt(4))), body = text,
                    tags = listOfNotNull(tag?.let { if (it == "민지") person(it) else act(it) }),
                    reply = if (rnd.nextFloat() < 0.15f) "오늘 얘기 들려줘서 고마워. 내가 잘 간직해 둘게." else null,
                )
            }
            day = day.plusDays(1)
        }

        // ---- 1년 전 오늘 ----
        put(today.minusYears(1), "21:30", listOf(e(FLUTTER, 3)), body = "첫눈 온다고 해서 괜히 창밖만 보다가, 결국 민지랑 붕어빵 사 먹었다.", tags = listOf(person("민지")), photoKinds = listOf(2))
        put(LocalDate.of(today.year, 10, 27), "15:20", listOf(e(COMFY, 2)), "일요일 오후", "빨래 돌려 놓고 공원 산책. 아무것도 안 해도 되는 날.", listOf(act("산책")))
        // Longer past diaries: 비슷했던 날 / 뜻으로 찾기 need real text to compare.
        put(today.minusMonths(2).withDayOfMonth(9), "21:40", listOf(e(PROUD, 4)), "발표 끝",
            "한 달 내내 붙잡고 있던 발표를 드디어 끝냈다. 퇴근길에 강변을 따라 좀 걸었는데 바람이 시원해서 머리가 비워졌다. 별거 아닌 일인데 스스로 대견했다.", listOf(act("산책")))
        put(today.minusMonths(3).withDayOfMonth(4), "19:10", listOf(e(COMFY, 3), e(GRATEFUL, 2)),
            body = "점심시간에 회사 근처 공원을 한 바퀴 돌았다. 햇빛 받으면서 걸으니까 오전 내내 꼬였던 생각이 조금 풀렸다. 오후 회의도 생각보다 잘 넘어갔다.", tags = listOf(act("산책")))
        put(today.minusMonths(4).withDayOfMonth(17), "23:30", listOf(e(TIRED, 4), e(ANNOYED, 3)),
            body = "또 야근. 팀장님이 퇴근 직전에 자료를 다시 만들라고 했다. 집에 오니 열두 시가 다 됐고 저녁도 못 먹었다. 진짜 지친다.", tags = listOf(act("야근")))
        put(today.minusMonths(5).withDayOfMonth(2), "22:15", listOf(e(SAD, 3), e(HURT, 3)),
            body = "민지랑 사소한 걸로 말다툼을 했다. 내가 먼저 말을 세게 한 것 같아서 계속 마음에 걸린다. 내일 먼저 연락해 봐야겠다.", tags = listOf(person("민지")))
        put(today.minusMonths(6).withDayOfMonth(21), "20:00", listOf(e(JOY, 4), e(LOVE, 3)),
            body = "엄마 생신이라 온 가족이 갈비집에 모였다. 동생이 몰래 케이크를 준비해 와서 엄마가 눈물을 글썽였다. 오랜만에 다 같이 웃었다.", tags = listOf(person("엄마"), person("동생")))

        // ---- this month, like 05_캘린더_1 ----
        val month = listOf(
            Triple(1, "20:10", listOf(e(FLUTTER, 3))), Triple(2, "19:40", listOf(e(LIGHT, 4))), Triple(5, "23:10", listOf(e(TIRED, 3))),
            Triple(6, "12:30", listOf(e(COMFY, 3))), Triple(7, "16:00", listOf(e(SERENE, 3))), Triple(8, "13:00", listOf(e(JOY, 4))),
            Triple(9, "21:00", listOf(e(UNSURE, 3))), Triple(10, "08:30", listOf(e(ANXIOUS, 3))), Triple(12, "22:40", listOf(e(SAD, 3))),
            Triple(15, "14:00", listOf(e(PROUD, 4))), Triple(16, "18:50", listOf(e(SERENE, 3))), Triple(18, "09:10", listOf(e(TIRED, 4))),
        ).filter { it.first < today.dayOfMonth }
        val bodies = mapOf(
            1 to ("" to "오랜만에 좋아하는 밴드 공연 예매에 성공했다."), 2 to ("퇴사 1주년" to "작년 오늘을 생각하며 천천히 산책했다. 그때보다 훨씬 가볍다."),
            5 to ("" to "야근. 머리가 멍하다."), 6 to ("" to "점심에 회사 앞 공원 산책."), 7 to ("" to "토요일 아침, 카페에서 책 읽기."),
            8 to ("" to "민지랑 전시 보고 맛있는 거 먹었다."), 9 to ("" to "그냥 그런 하루."), 10 to ("" to "발표 전날이라 잠이 안 온다. 야근까지."),
            12 to ("" to "괜히 마음이 가라앉는 밤."), 15 to ("" to "드디어 운동 30일 채웠다! 민지가 축하해 줬다."),
            16 to ("비 그친 저녁" to "우산 접고 동네 한 바퀴 산책. 젖은 낙엽 냄새가 좋았다."), 18 to ("" to "야근 3일째. 커피만 세 잔."),
        )
        val tagsFor = mapOf(2 to listOf(act("산책")), 5 to listOf(act("야근")), 6 to listOf(act("산책")), 7 to listOf(place("카페")), 8 to listOf(person("민지")),
            10 to listOf(act("야근")), 15 to listOf(act("운동"), person("민지")), 16 to listOf(act("산책")), 18 to listOf(act("야근")))
        month.forEach { (dd, t, em) ->
            val (title, body) = bodies.getValue(dd)
            put(d(dd), t, em, title, body, tagsFor[dd].orEmpty(),
                reply = when (dd) { 16 -> "젖은 낙엽 냄새 얘기, 나도 맡아 본 것 같아. 오늘 제일 편안했던 순간은 그때였어?"; 2 -> "1년 동안 정말 많이 가벼워졌구나. 그 산책길, 나도 같이 걷고 싶다."; else -> null })
        }
        if (today.dayOfMonth > 3) {
            put(d(3), "09:00", listOf(e(GRATEFUL, 3)), body = "아침에 엄마가 보내 준 반찬.", tags = listOf(person("엄마")))
            put(d(3), "21:30", listOf(e(HOPE, 3)), body = "내일 면접. 잘할 수 있을 거야.")
        }
        if (today.dayOfMonth > 13) put(d(13), "23:20", listOf(e(SAD, 4), e(LONELY, 3)), body = "다들 바쁜 것 같아서 아무한테도 연락 못 했다.",
            reply = "많이 무거웠겠다. 다 털어놓지 않아도 괜찮아, 나 여기 불 켜 두고 있을게.")
        if (today.dayOfMonth > 14) { put(d(14), "12:10", listOf(e(COMFY, 3)), body = "점심 산책.", tags = listOf(act("산책"))); put(d(14), "22:00", listOf(e(DOWN, 2)), body = "일요일 밤의 우울.") }
        if (today.dayOfMonth > 19) {
            put(d(19), "10:30", listOf(e(FLUTTER, 4)), "다음 주 제주", "숙소 예약 끝! 동생이랑 둘이 가는 건 처음이다.", listOf(person("동생")), photoKinds = listOf(2),
                reply = "두근거리는 게 글자에서도 느껴져. 제주 다녀오면 꼭 들려줘.")
            put(d(19), "20:00", listOf(e(HOPE, 3)), body = "제주 맛집 리스트 정리.")
        }
        if (today.dayOfMonth > 20) put(d(20), "23:05", listOf(e(TIRED, 4)), "금요일은 원래 이래", "야근하고 들어오니까 열한 시. 씻고 바로 누웠다.", listOf(act("야근")),
            reply = "오늘 정말 애썼어. 짧게 써도 충분해. 이제 불 줄이고 푹 자자.")

        // ---- today, like 02_오늘 ----
        if (withToday) {
            put(today, "08:10", listOf(e(TIRED, 2)), weather = "맑음 4°", id = "seed-today-1")
            put(today, "13:40", listOf(e(COMFY, 3)), body = "점심 먹고 한강 산책.", tags = listOf(act("산책")), weather = "맑음 4°", id = "seed-today-2",
                reply = "점심에 걸은 길 얘기, 나까지 바람 쐰 기분이야. 내일도 잠깐 걸어 볼래?")
            put(
                today, "22:05", listOf(e(PROUD, 4), e(GRATEFUL, 3)), "한강 따라 걸은 날",
                "점심 먹고 회사 앞 한강 공원을 30분쯤 걸었다. 바람이 차가웠는데 이상하게 머리가 맑아졌다.\n밤에는 미뤄 둔 보고서를 드디어 끝냈다. 별거 아닌데 뿌듯해서 괜히 민지한테 자랑했다",
                listOf(act("산책"), place("한강")), photoKinds = listOf(0, 1), weather = "맑음 4°", id = "seed-today-3",
            )
        }
        if (withToday) listOf(
            "oni" to "오늘 하루 중에 제일 기억에 남는 장면 하나만 꼽아 줄래?", "user" to "점심에 한강 걸은 거. 바람 엄청 차가웠어",
            "oni" to "차가운 바람이었는데도 기억에 남았구나. 걸으면서 무슨 생각 했어?", "user" to "그냥 아무 생각 안 하려고 했는데 오히려 보고서 정리가 됐어 ㅋㅋ",
            "oni" to "머리가 맑아지니까 일이 풀린 거네. 그 보고서, 결국 어떻게 됐어?",
        ).forEachIndexed { i, (role, text) -> chat.insert(com.pass.diary.core.data.db.ChatMessageEntity(entryId = "seed-today-3", role = role, text = text, at = i.toLong())) }
        // An untitled draft with no feeling picked: write:seed-draft shows the light following the text and title chips.
        if (withToday) put(today, "23:10", emptyList(), body = "오늘 민지랑 성수에서 전시 보고 카페 갔다. 사람은 많았는데 그림이 너무 예뻐서 계속 사진 찍었다.\n" +
            "다음 달에 또 가자고 약속했다. 벌써 기다려진다!", id = "seed-draft")
        if (crisis) put(today.minusDays(1), "23:50", listOf(e(DOWN, 5)), body = "요즘 너무 힘들다. 그냥 다 그만두고 죽고 싶다는 생각이 든다.", id = "seed-crisis")
        android.util.Log.i("DebugSeed", "seeded $n entries")
    }

    private fun gradient(kind: Int): Bitmap {
        val p = listOf(
            intArrayOf(0xFFF6D7B0.toInt(), 0xFFE9A98A.toInt(), 0xFF9C6E78.toInt(), 0xFF4E4258.toInt()),
            intArrayOf(0xFFE6EEDC.toInt(), 0xFFB9CFA7.toInt(), 0xFF7C9B72.toInt(), 0xFF4C5E47.toInt()),
            intArrayOf(0xFFDCE8F2.toInt(), 0xFFA9C3DA.toInt(), 0xFF6E8FAE.toInt(), 0xFFE8DCC8.toInt()),
        )[kind % 3]
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 600f, 600f, Paint().apply { shader = LinearGradient(0f, 0f, 120f, 600f, p, floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP) })
        c.drawRect(0f, 0f, 600f, 600f, Paint().apply { shader = RadialGradient(180f, 420f, 330f, 0x59FFF0D2, 0, Shader.TileMode.CLAMP) })
        return bmp
    }
}
