package com.pass.diary.core.data

import java.io.File

/** 계정·데이터 삭제: the app's private storage, emptied in place (the caller restarts the process right after). */
object AppDataWiper {
    /**
     * Deletes everything under [dataDir] except the paths in [keep] (relative, "/"-separated) and `lib`
     * (a link to the APK's native libraries, not ours). Returns false if something could not be deleted.
     */
    fun wipe(dataDir: File, keep: Set<String> = emptySet()): Boolean {
        fun clear(dir: File, rel: String): Boolean = (dir.listFiles() ?: emptyArray()).map { f ->
            val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
            when {
                path == "lib" || path in keep -> true
                f.isDirectory && keep.any { it.startsWith("$path/") } -> clear(f, path)
                else -> f.deleteRecursively()
            }
        }.all { it }
        return clear(dataDir, "")
    }
}
