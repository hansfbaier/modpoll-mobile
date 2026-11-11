package io.github.hansfbaier.modpoll.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.hansfbaier.modpoll.data.SerialSettings
import io.github.hansfbaier.modpoll.modbus.UsbSerialTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class UsbSerialDeviceInfo(
    val device: UsbDevice,
    val driverName: String?,
    val displayName: String,
    val vendorId: Int,
    val productId: Int,
) {
    val supported: Boolean get() = driverName != null
    val key: String get() = "%04X:%04X:%s".format(vendorId, productId, device.deviceName)
    val vidsPid: String get() = "%04X:%04X".format(vendorId, productId)
}

class UsbSerialManager(
    private val context: Context,
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _devices = MutableStateFlow<List<UsbSerialDeviceInfo>>(emptyList())
    val devices: StateFlow<List<UsbSerialDeviceInfo>> = _devices.asStateFlow()

    private val hotplugReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context?,
                intent: Intent?,
            ) {
                refresh()
            }
        }

    init {
        refresh()
        val filter =
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        runCatching {
            ContextCompat.registerReceiver(context, hotplugReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    fun refresh() {
        val probed =
            try {
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            } catch (_: Exception) {
                emptyList()
            }
        val byDevice = probed.associateBy { it.device.deviceId }
        val list =
            usbManager.deviceList.values
                .map { device ->
                    val driver = byDevice[device.deviceId]
                    val name =
                        runCatching {
                            listOfNotNull(device.manufacturerName, device.productName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                        }.getOrNull().orEmpty().ifBlank { device.deviceName }
                    UsbSerialDeviceInfo(
                        device = device,
                        driverName = driver?.javaClass?.simpleName?.removeSuffix("Driver"),
                        displayName = name,
                        vendorId = device.vendorId,
                        productId = device.productId,
                    )
                }.sortedWith(compareByDescending<UsbSerialDeviceInfo> { it.supported }.thenBy { it.displayName })
        _devices.value = list
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    suspend fun ensurePermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        return suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.USB_PERMISSION"
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        receiverContext: Context?,
                        intent: Intent?,
                    ) {
                        val target =
                            if (Build.VERSION.SDK_INT >= 33) {
                                intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                            }
                        if (target != null && target.deviceId != device.deviceId) return
                        runCatching { context.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true)
                        }
                    }
                }
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(action),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    device.deviceId,
                    Intent(action).setPackage(context.packageName),
                    flags,
                )
            usbManager.requestPermission(device, pendingIntent)
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    fun openTransport(
        info: UsbSerialDeviceInfo,
        settings: SerialSettings,
    ): UsbSerialTransport =
        UsbSerialTransport(
            usbManager = usbManager,
            device = info.device,
            settings = settings,
            label = "${info.displayName} (${info.vidsPid})",
        )
}
