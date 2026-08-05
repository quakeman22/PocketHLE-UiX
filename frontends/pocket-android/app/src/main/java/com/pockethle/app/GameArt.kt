package com.pockethle.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object GameArt {
    /** Custom cover (if the user picked one) takes precedence over the icon extracted from the .CAB. */
    fun load(context: Context, entry: GameEntry, libraryRoot: String): Bitmap? {
        val customCover = CoverStore.coverFile(context, entry.id)
        val iconFile = entry.icon?.let { File(libraryRoot, "games/${entry.id}/$it") }
        val artFile = customCover.takeIf { it.isFile } ?: iconFile?.takeIf { it.isFile }
        return artFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
}
