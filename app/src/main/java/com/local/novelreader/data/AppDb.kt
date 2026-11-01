package com.local.novelreader.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE books(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author TEXT NOT NULL DEFAULT '',
                folder TEXT NOT NULL,
                format TEXT NOT NULL,
                encoding TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL DEFAULT 0,
                chapterCount INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL,
                lastReadAt INTEGER NOT NULL DEFAULT 0,
                progressChapter INTEGER NOT NULL DEFAULT 0,
                progressOffset INTEGER NOT NULL DEFAULT 0,
                progressPct REAL NOT NULL DEFAULT 0,
                groupName TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            """CREATE TABLE chapters(
                bookId INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                title TEXT NOT NULL,
                charCount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(bookId, idx)
            )"""
        )
        db.execSQL(CREATE_BOOKMARKS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(CREATE_BOOKMARKS)
    }

    fun insertBookmark(bookId: Long, chapter: Int, offset: Int, snippet: String): Long {
        val cv = ContentValues().apply {
            put("bookId", bookId)
            put("chapter", chapter)
            put("offset", offset)
            put("snippet", snippet)
            put("createdAt", System.currentTimeMillis())
        }
        return writableDatabase.insert("bookmarks", null, cv)
    }

    fun bookmarks(bookId: Long): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        readableDatabase.rawQuery(
            "SELECT id, chapter, offset, snippet, createdAt FROM bookmarks WHERE bookId=? ORDER BY chapter, offset",
            arrayOf(bookId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Bookmark(c.getLong(0), c.getInt(1), c.getInt(2), c.getString(3), c.getLong(4)))
            }
        }
        return out
    }

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id=?", arrayOf(id.toString()))
    }

    fun insertBook(book: Book, forcedId: Long? = null): Long {
        val cv = ContentValues().apply {
            if (forcedId != null) put("id", forcedId)
            put("title", book.title)
            put("author", book.author)
            put("folder", book.folder)
            put("format", book.format)
            put("encoding", book.encoding)
            put("sizeBytes", book.sizeBytes)
            put("chapterCount", book.chapterCount)
            put("addedAt", book.addedAt)
            put("lastReadAt", book.lastReadAt)
            put("progressChapter", book.progressChapter)
            put("progressOffset", book.progressOffset)
            put("progressPct", book.progressPct)
            put("groupName", book.groupName)
        }
        return writableDatabase.insert("books", null, cv)
    }

    fun insertChapters(bookId: Long, chapters: List<Chapter>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement("INSERT INTO chapters(bookId, idx, title, charCount) VALUES(?,?,?,?)")
            for (c in chapters) {
                stmt.clearBindings()
                stmt.bindLong(1, bookId)
                stmt.bindLong(2, c.idx.toLong())
                stmt.bindString(3, c.title)
                stmt.bindLong(4, c.charCount.toLong())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceChapters(bookId: Long, chapters: List<Chapter>) {
        val db = writableDatabase
        db.delete("chapters", "bookId=?", arrayOf(bookId.toString()))
        insertChapters(bookId, chapters)
    }

    fun updateChapterCount(bookId: Long, count: Int) {
        writableDatabase.update(
            "books",
            ContentValues().apply { put("chapterCount", count) },
            "id=?",
            arrayOf(bookId.toString())
        )
    }

    fun books(): List<Book> {
        val out = mutableListOf<Book>()
        readableDatabase.rawQuery("SELECT * FROM books ORDER BY CASE WHEN lastReadAt=0 THEN addedAt ELSE lastReadAt END DESC", null).use { c ->
            while (c.moveToNext()) out.add(c.toBook())
        }
        return out
    }

    fun book(id: Long): Book? {
        readableDatabase.rawQuery("SELECT * FROM books WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toBook() else null
        }
    }

    fun chapters(bookId: Long): List<Chapter> {
        val out = mutableListOf<Chapter>()
        readableDatabase.rawQuery("SELECT idx, title, charCount FROM chapters WHERE bookId=? ORDER BY idx", arrayOf(bookId.toString())).use { c ->
            while (c.moveToNext()) out.add(Chapter(c.getInt(0), c.getString(1), c.getInt(2)))
        }
        return out
    }

    fun updateProgress(bookId: Long, chapter: Int, offset: Int, pct: Float) {
        val cv = ContentValues().apply {
            put("progressChapter", chapter)
            put("progressOffset", offset)
            put("progressPct", pct)
            put("lastReadAt", System.currentTimeMillis())
        }
        writableDatabase.update("books", cv, "id=?", arrayOf(bookId.toString()))
    }

    fun deleteBook(bookId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chapters", "bookId=?", arrayOf(bookId.toString()))
            db.delete("bookmarks", "bookId=?", arrayOf(bookId.toString()))
            db.delete("books", "id=?", arrayOf(bookId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 恢复备份前清空整个书库。 */
    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chapters", null, null)
            db.delete("bookmarks", null, null)
            db.delete("books", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toBook(): Book = Book(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        author = getString(getColumnIndexOrThrow("author")),
        folder = getString(getColumnIndexOrThrow("folder")),
        format = getString(getColumnIndexOrThrow("format")),
        encoding = getString(getColumnIndexOrThrow("encoding")),
        sizeBytes = getLong(getColumnIndexOrThrow("sizeBytes")),
        chapterCount = getInt(getColumnIndexOrThrow("chapterCount")),
        addedAt = getLong(getColumnIndexOrThrow("addedAt")),
        lastReadAt = getLong(getColumnIndexOrThrow("lastReadAt")),
        progressChapter = getInt(getColumnIndexOrThrow("progressChapter")),
        progressOffset = getInt(getColumnIndexOrThrow("progressOffset")),
        progressPct = getFloat(getColumnIndexOrThrow("progressPct")),
        groupName = getString(getColumnIndexOrThrow("groupName"))
    )

    companion object {
        private const val DB_NAME = "library.db"
        private const val DB_VERSION = 2
        private const val CREATE_BOOKMARKS = """CREATE TABLE bookmarks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            bookId INTEGER NOT NULL,
            chapter INTEGER NOT NULL,
            offset INTEGER NOT NULL,
            snippet TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL
        )"""
    }
}
