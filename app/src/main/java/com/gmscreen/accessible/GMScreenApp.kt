package com.gmscreen.accessible

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.util.AppConfig
import com.gmscreen.accessible.util.AppLogger

class GMScreenApplication : Application() {

    lateinit var appConfig: AppConfig
        private set

    lateinit var repository: StbRepository
        private set

    var isAppInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedActivities = 0
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = true
            }
            override fun onActivityPaused(activity: Activity) {
                resumedActivities--
                if (resumedActivities <= 0) {
                    resumedActivities = 0
                    isAppInForeground = false
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e("FATAL_CRASH", "Unhandled crash on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        appConfig = AppConfig(this)
        repository = StbRepository(this)
        com.gmscreen.accessible.util.TtsHelper.init(this)
        AppLogger.i("App", "GMScreen Application started (v2.3.33)")
    }

    companion object {
        lateinit var instance: GMScreenApplication
            private set
    }
}
