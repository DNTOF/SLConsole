package com.dntof.slconsole

import android.app.Application
import com.dntof.slconsole.data.local.ServerStore

class SlConsoleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

object ServiceLocator {
    lateinit var serverStore: ServerStore
        private set

    fun init(app: Application) {
        serverStore = ServerStore(app)
    }
}
