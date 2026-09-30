package com.pydroidx.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.JsonReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** The official PyPI Simple Index, stored as names only. No giant list enters Compose state. */
class PyPiCatalog(context: Context) : SQLiteOpenHelper(context.applicationContext, "pypi-catalog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects (name TEXT PRIMARY KEY COLLATE NOCASE)")
        db.execSQL("CREATE TABLE catalog_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM projects", null).use {
        it.moveToFirst(); it.getInt(0)
    }

    fun search(prefix: String, limit: Int = 50): List<String> {
        val value = prefix.trim().lowercase()
        if (value.length < 2) return emptyList()
        val end = value + '\uffff'
        return readableDatabase.rawQuery(
            "SELECT name FROM projects WHERE name >= ? AND name < ? ORDER BY name LIMIT ?",
            arrayOf(value, end, limit.coerceIn(1, 100).toString())
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }

    /** Streams the official JSON response into a staging table; cancellation leaves the old index intact. */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val connection = URL("https://pypi.org/simple/").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.pypi.simple.v1+json")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        val db = writableDatabase
        try {
            if (connection.responseCode != 200) throw IllegalStateException("PyPI returned ${connection.responseCode}")
            if (!connection.url.toString().startsWith("https://pypi.org/simple/"))
                throw IllegalStateException("Unexpected package index redirect")
            db.execSQL("CREATE TABLE IF NOT EXISTS incoming_projects (name TEXT PRIMARY KEY COLLATE NOCASE)")
            db.execSQL("DELETE FROM incoming_projects")
            val stream = if (connection.contentEncoding?.equals("gzip", true) == true)
                GZIPInputStream(connection.inputStream) else connection.inputStream
            var count = 0
            db.beginTransaction()
            try {
            val insert = db.compileStatement("INSERT OR IGNORE INTO incoming_projects(name) VALUES(?)")
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "projects") { reader.skipValue(); continue }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (count % 1024 == 0) coroutineContext.ensureActive()
                        reader.beginObject()
                        var name: String? = null
                        while (reader.hasNext()) {
                            if (reader.nextName() == "name") name = reader.nextString() else reader.skipValue()
                        }
                        reader.endObject()
                        if (name != null && name.length <= 180) {
                            insert.bindString(1, name)
                            insert.executeInsert()
                            insert.clearBindings()
                            count++
                        }
                    }
                    reader.endArray()
                }
                reader.endObject()
            }
            insert.close()
            db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            if (count == 0) throw IllegalStateException("PyPI returned an empty catalog")
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM projects")
                db.execSQL("INSERT INTO projects SELECT name FROM incoming_projects")
                db.execSQL("DROP TABLE incoming_projects")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            count
        } finally { connection.disconnect() }
    }
}
