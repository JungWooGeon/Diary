package com.pass.diary.core.sync

import com.pass.diary.core.light.LightMixer
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** entries.json in a 내보내기 zip. [entries] use the Drive file format (shared/schema/entry.schema.json). */
@Serializable
data class ArchiveDoc(val format: String = FORMAT, val version: Int = 1, val exportedAt: Long, val entries: List<EntryDoc>) {
    companion object { const val FORMAT = "oni-diary-export" }
}

data class ImportResult(val added: Int, val updated: Int, val skipped: Int)

/**
 * 내 파일로 내보내기 / 가져오기 (no Drive needed): a zip with entries.json, photos/<id>.jpg as stored, diary.html
 * (readable, printable) and README.txt. The web writes the same entries.json (web/src/archive.ts).
 */
object DiaryArchive {
    /** Ids become file names (photos/<id>.jpg on disk): no separators, no "..". */
    private val SAFE_ID = Regex("^(?!.*\\.\\.)[A-Za-z0-9_:.-]{1,80}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Live entries (and 휴지통 ones when [includeTrash]); content-less tombstones never. Returns how many went in. */
    suspend fun export(store: SyncLocalStore, out: OutputStream, includeTrash: Boolean, now: Long = System.currentTimeMillis()): Int {
        val docs = store.allEntries().filter { !it.deleted || (includeTrash && it.trashedAt != null) }
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            put("entries.json", DriveJson.encodeToString(ArchiveDoc.serializer(), ArchiveDoc(exportedAt = now, entries = docs)).encodeToByteArray())
            val photos = docs.flatMap { it.photos }.map { it.id }.distinct().filter { store.hasPhoto(it) }
            for (id in photos) put("photos/$id.jpg", store.photoBytes(id))
            put("diary.html", html(docs, photos.toSet()).encodeToByteArray())
            put("README.txt", README.encodeToByteArray())
        }
        return docs.size
    }

    /**
     * Merges a 내보내기 zip by id, newest updatedAt wins: an entry is taken only when this device doesn't have it or
     * has an older version, so a newer local edit (or a later 영구 삭제) is never overwritten. [open] is called twice
     * (entries.json first, then the photos), so photos stream one at a time instead of sitting in memory.
     */
    suspend fun import(store: SyncLocalStore, open: () -> InputStream): ImportResult {
        val archive = open().use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name == "entries.json" }
                    ?: throw IllegalArgumentException("entries.json이 없어요")
                // ponytail: whole entries.json in memory (a few MB for years of diaries); stream it if that ever hurts.
                DriveJson.decodeFromString(ArchiveDoc.serializer(), zip.readBytes().decodeToString())
            }
        }
        var added = 0; var updated = 0; var skipped = 0
        val take = archive.entries.filter { d ->
            val ok = SAFE_ID.matches(d.id) && DATE.matches(d.date) && d.photos.all { SAFE_ID.matches(it.id) }
            val local = if (ok) store.entry(d.id) else null
            (ok && (local == null || d.updatedAt > local.updatedAt)).also { if (!it) skipped++ else if (local == null) added++ else updated++ }
        }
        val wanted = take.flatMap { it.photos }.map { it.id }.filterNot { store.hasPhoto(it) }.toMutableSet()
        if (wanted.isNotEmpty()) open().use { input ->
            ZipInputStream(input).use { zip ->
                for (e in generateSequence { zip.nextEntry }) {
                    val id = e.name.removePrefix("photos/").removeSuffix(".jpg")
                    if (e.name.startsWith("photos/") && id in wanted) { store.savePhoto(id, zip.readBytes()); wanted -= id }
                }
            }
        }
        take.forEach { store.applyRemoteEntry(it) }
        return ImportResult(added, updated, skipped)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)

    internal fun html(docs: List<EntryDoc>, photos: Set<String>): String = buildString {
        append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>온이 일기</title><style>$CSS</style></head><body><h1>온이 일기</h1>")
        append("<p class=\"meta\">${docs.count { !it.deleted }}개의 기록</p>")
        for ((date, day) in docs.sortedWith(compareBy({ it.date }, { it.time })).groupBy { it.date }) {
            val label = runCatching { LocalDate.parse(date).format(DAY) }.getOrDefault(date)
            append("<section class=\"day\"><h2>${esc(label)}</h2>")
            for (d in day) {
                val entry = d.toEntry { "" }
                val light = LightMixer.mix(entry.emotions)
                append("<article style=\"border-color:${light.glowHex};background:${light.lightHex}22\">")
                append("<div class=\"time\"><span class=\"dot\" style=\"background:${light.glowHex}\"></span>${esc(d.time)}")
                if (d.trashedAt != null) append(" · 휴지통")
                append("</div>")
                if (d.title.isNotBlank()) append("<h3>${esc(d.title)}</h3>")
                if (entry.emotions.isNotEmpty()) {
                    append("<div class=\"chips\">")
                    for (e in entry.emotions) {
                        val c = LightMixer.mix(listOf(e))
                        append("<span class=\"chip\" style=\"background:${c.lightHex}\">${esc(e.key.label)} ${"●".repeat(e.intensity)}</span>")
                    }
                    append("</div>")
                }
                if (d.body.isNotBlank()) append("<p class=\"body\">${esc(d.body)}</p>")
                for (p in d.photos) if (p.id in photos) append("<img src=\"photos/${p.id}.jpg\" alt=\"\" loading=\"lazy\">")
                if (d.tags.isNotEmpty()) append("<p class=\"tags\">${d.tags.joinToString(" ") { "#" + esc(it.value) }}</p>")
                d.reply?.let { append("<blockquote>온이: ${esc(it.text)}</blockquote>") }
                append("</article>")
            }
            append("</section>")
        }
        append("</body></html>")
    }

    private const val CSS = "body{font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;max-width:720px;margin:0 auto;padding:24px 16px;color:#2b2630;background:#fbf8f4;line-height:1.7}" +
        "h1{font-size:28px;margin:0}.meta{color:#8a8290;margin:4px 0 24px}h2{font-size:17px;margin:32px 0 10px;color:#5b5363}" +
        "article{border-left:4px solid;border-radius:12px;padding:12px 16px;margin:0 0 12px;break-inside:avoid}" +
        ".time{font-size:13px;color:#8a8290;display:flex;align-items:center;gap:6px}.dot{width:10px;height:10px;border-radius:50%;display:inline-block}" +
        "h3{margin:6px 0 2px;font-size:17px}.chips{display:flex;flex-wrap:wrap;gap:6px;margin:6px 0}.chip{font-size:12px;padding:2px 10px;border-radius:999px}" +
        ".body{white-space:pre-wrap;margin:6px 0}img{max-width:100%;border-radius:10px;margin:6px 0;display:block}.tags{color:#8a8290;font-size:13px}" +
        "blockquote{margin:8px 0 0;padding:8px 12px;border-radius:10px;background:#00000008;font-size:14px}" +
        "@media print{body{background:#fff}article{-webkit-print-color-adjust:exact;print-color-adjust:exact}}"

    private val README = """
        온이 일기 내보내기
        ==================

        diary.html   이 파일을 열면 날짜별로 읽을 수 있어요. 인쇄(PDF로 저장)도 돼요.
        entries.json 일기 원본 데이터예요 (구글 드라이브 백업과 같은 형식).
        photos/      일기에 넣은 사진 원본이에요.

        온이 일기 앱 › 마이 › 내보내기 › 파일에서 가져오기 로 이 파일을 고르면
        일기를 되살릴 수 있어요. 이미 있는 일기는 더 최근에 고친 쪽이 남아요.

        이 파일에는 일기 내용이 그대로 들어 있어요. 다른 사람과 공유하지 않도록 조심해 주세요.
    """.trimIndent()
}
