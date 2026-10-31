package com.example.data

import android.content.Context
import java.io.File

/** One thing the user made or saved, shown in Library and Search. Built only from real data. */
data class LibraryEntry(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val timestampMillis: Long,
    val file: File? = null
) {
    enum class Kind(val label: String) { RECIPE("Recipes"), FILE("Files"), QUEST("Quests") }
}

/** Private, persistent folder for files the user creates (converter results). */
object LibraryStore {
    fun dir(context: Context): File = File(context.filesDir, "library").apply { mkdirs() }

    fun files(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun delete(context: Context, file: File): Boolean =
        file.canonicalPath.startsWith(dir(context).canonicalPath + File.separator) && file.delete()
}
