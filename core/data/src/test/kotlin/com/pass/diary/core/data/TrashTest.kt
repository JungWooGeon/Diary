package com.pass.diary.core.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.data.db.MIGRATION_1_2
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Photo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrashTest {
    private val ctx get() = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryDatabase::class.java).allowMainThreadQueries().build()
    private val repo = EntryRepository(db, db.entryDao())
    private val day = 24L * 60 * 60 * 1000

    @After fun close() = db.close()

    private fun saved(photo: File? = null): Entry = runBlocking {
        val e = EntryRepository.newEntry(date = "2026-11-21", now = 1_000).copy(title = "제목", body = "본문", emotions = listOf(EntryEmotion(EmotionKey.JOY)))
        val withPhoto = photo?.let { e.copy(photos = listOf(Photo(entryId = e.id, localPath = it.path, w = 1, h = 1))) } ?: e
        repo.save(withPhoto, now = 1_000).also { db.replyDao().upsert(ReplyEntity(it.id, "답장", "template", null, 1, false)) }
    }

    private fun live() = runBlocking { repo.observeRange("0000-01-01", "9999-12-31").first() }

    @Test fun trashHidesButKeepsEverythingAndRestoreBringsItBack() = runBlocking {
        val photo = File(ctx.filesDir, "p.jpg").apply { writeBytes(byteArrayOf(1)) }
        val e = saved(photo)
        repo.trash(e.id, now = 2_000)
        assertTrue(live().isEmpty())
        assertTrue(repo.search("본문", null).first().isEmpty())
        val t = repo.observeTrash().first().single()
        assertEquals("본문", t.body); assertEquals(2_000L, t.trashedAt); assertTrue(t.deleted); assertEquals(1, t.photos.size)
        assertTrue(photo.exists()); assertNotNull(db.replyDao().get(e.id))

        repo.restore(e.id, now = 3_000)
        val back = live().single()
        assertEquals("본문", back.body); assertNull(back.trashedAt); assertFalse(back.deleted); assertEquals(3_000L, back.updatedAt)
        assertTrue(repo.observeTrash().first().isEmpty())
    }

    @Test fun trashExpiresAfter30DaysIntoAContentlessTombstone() = runBlocking {
        val photo = File(ctx.filesDir, "q.jpg").apply { writeBytes(byteArrayOf(1)) }
        val e = saved(photo)
        repo.trash(e.id, now = 2_000)
        assertEquals(0, repo.purgeTrash(now = 2_000 + 29 * day))
        assertEquals("본문", repo.get(e.id)!!.body)
        assertEquals(1, repo.purgeTrash(now = 2_000 + 31 * day))
        val gone = repo.get(e.id)!!
        assertTrue(gone.deleted); assertNull(gone.trashedAt); assertEquals("", gone.body); assertEquals("", gone.title)
        assertTrue(gone.emotions.isEmpty() && gone.photos.isEmpty()); assertNull(db.replyDao().get(e.id)); assertFalse(photo.exists())
        assertEquals(2_000 + 30 * day, gone.updatedAt) // deterministic: every device writes the same tombstone
    }

    @Test fun permanentDeleteAndEmptyTrashClearContent() = runBlocking {
        val a = saved(); val b = saved()
        repo.trash(a.id, 2_000); repo.trash(b.id, 2_000)
        repo.delete(a.id, now = 3_000)
        assertEquals("", repo.get(a.id)!!.body); assertEquals(3_000L, repo.get(a.id)!!.updatedAt)
        assertEquals("본문", repo.get(b.id)!!.body) // the other trash entry is untouched
        repo.emptyTrash(now = 4_000)
        assertEquals("", repo.get(b.id)!!.body); assertTrue(repo.observeTrash().first().isEmpty())
    }

    @Test fun oldTombstonesStillGetScrubbedAndRemoteTrashKeepsContent() = runBlocking {
        val base = EntryRepository.newEntry(date = "2026-11-21", now = 1).copy(body = "옛 앱이 남긴 글", deleted = true)
        repo.saveExternal(listOf(base, base.copy(id = "t", trashedAt = 5)))
        assertEquals("", repo.get(base.id)!!.body)
        assertEquals("옛 앱이 남긴 글", repo.get("t")!!.body)
    }

    /** v1 DB (as shipped) → v2 with [MIGRATION_1_2]; Room checks the result against the exported 2.json schema. */
    @Test fun migratesVersion1() {
        val file = ctx.getDatabasePath("mig.db").apply { parentFile!!.mkdirs(); delete() }
        val schema = JSONObject(File("schemas/com.pass.diary.core.data.db.DiaryDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val t = entities.getJSONObject(i); val name = t.getString("tableName")
                old.execSQL(t.getString("createSql").replace("\${TABLE_NAME}", name))
                t.optJSONArray("indices")?.let { ix -> for (j in 0 until ix.length()) old.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name)) }
            }
            val setup = schema.getJSONArray("setupQueries"); for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO entry VALUES ('x','2026-01-01','09:00',0,1,2,1,'','','free','text',NULL,NULL)")
            old.execSQL("INSERT INTO entry VALUES ('y','2026-01-02','09:00',0,1,2,0,'t','b','free','text',NULL,NULL)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(ctx, DiaryDatabase::class.java, file.path).addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val r = EntryRepository(migrated, migrated.entryDao())
            runBlocking {
                assertNull(r.get("x")!!.trashedAt); assertTrue(r.get("x")!!.deleted) // old tombstone stays a hard tombstone
                assertEquals("b", r.get("y")!!.body)
                assertTrue(r.observeTrash().first().isEmpty())
            }
        } finally { migrated.close() }
    }
}
