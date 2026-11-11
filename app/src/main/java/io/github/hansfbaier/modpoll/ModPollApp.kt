package io.github.hansfbaier.modpoll

import android.app.Application
import android.content.Context
import io.github.hansfbaier.modpoll.data.ProfileStore
import io.github.hansfbaier.modpoll.modbus.TrafficRecorder
import io.github.hansfbaier.modpoll.usb.UsbSerialManager

class AppContainer(
    context: Context,
) {
    val profileStore = ProfileStore(context.applicationContext)
    val traffic = TrafficRecorder()
    val usb = UsbSerialManager(context.applicationContext)
    val appContext: Context = context.applicationContext
}

class ModPollApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
