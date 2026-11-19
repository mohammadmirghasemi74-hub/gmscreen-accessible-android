package com.gmscreen.accessible

import android.app.Application
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.util.AppConfig
import com.gmscreen.accessible.util.AppLogger

class GMScreenApplication : Application() {

    lateinit var appConfig: AppConfig
        private set

    lateinit var repository: StbRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e("FATAL_CRASH", "Unhandled crash on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        appConfig = AppConfig(this)
        repository = StbRepository(this)
        AppLogger.i("App", "GMScreen Application started (v2.3.20)")
    }

    companion object {
        lateinit var instance: GMScreenApplication
            private set
    }
}
