package com.pockethle.app

import android.content.Context
import java.io.File

/**
 * Stores custom cover art picked by the user for a game, as a plain
 * JPEG in the app's private files dir — completely separate from the
 * native library's `GameEntry.icon` (which comes from the .CAB itself).
 *
 * This keeps "choose cover" a purely client-side launcher feature: it
 * doesn't touch the Rust-side library schema, so it can't break
 * anything on the native side.
 */
object CoverStore {

    private fun coversDir(context: Context): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    fun coverFile(context: Context, gameId: String): File =
        File(coversDir(context), "$gameId.jpg")

    /** Copies [sourceStream] into this game's cover slot, overwriting any previous cover. */
    fun save(context: Context, gameId: String, sourceStream: java.io.InputStream) {
        val dest = coverFile(context, gameId)
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.outputStream().use { out -> sourceStream.copyTo(out) }
        tmp.renameTo(dest)
    }

    fun reset(context: Context, gameId: String) {
        coverFile(context, gameId).delete()
    }
}
