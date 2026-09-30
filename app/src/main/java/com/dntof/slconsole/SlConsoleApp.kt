package com.dntof.slconsole

import android.app.Application
import com.dntof.slconsole.data.local.ServerStore
import com.dntof.slconsole.data.local.SettingsStore

class SlConsoleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

object ServiceLocator {
    lateinit var serverStore: ServerStore
        private set
    lateinit var settingsStore: SettingsStore
        private set

    fun init(app: Application) {
        serverStore = ServerStore(app)
        settingsStore = SettingsStore(app)
    }
}
