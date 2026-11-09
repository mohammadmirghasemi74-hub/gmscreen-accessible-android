package com.gmscreen.accessible

import android.app.Application
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.util.AppConfig

class GMScreenApplication : Application() {

    lateinit var appConfig: AppConfig
        private set

    lateinit var repository: StbRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appConfig = AppConfig(this)
        repository = StbRepository(this)
    }

    companion object {
        lateinit var instance: GMScreenApplication
            private set
    }
}
