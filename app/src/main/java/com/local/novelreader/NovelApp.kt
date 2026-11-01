package com.local.novelreader

import android.app.Application
import com.local.novelreader.data.AppDb
import com.local.novelreader.data.LibraryRepository

class NovelApp : Application() {
    lateinit var repo: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = LibraryRepository(this, AppDb(this))
    }
}
