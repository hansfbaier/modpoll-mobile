package com.modpoll.rtu

import android.app.Application
import android.content.Context
import com.modpoll.rtu.data.ProfileStore
import com.modpoll.rtu.modbus.TrafficRecorder
import com.modpoll.rtu.usb.UsbSerialManager

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
