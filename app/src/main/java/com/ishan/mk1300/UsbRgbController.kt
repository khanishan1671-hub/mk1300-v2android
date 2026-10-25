package com.ishan.mk1300

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UsbRgbController(private val context: Context) {

    companion object {
        private const val TAG = "UsbRgb"
        private const val ACTION_USB_PERMISSION = "com.ishan.mk1300.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null

    var isConnected = false
        private set

    fun findDevice(vid: Int, pid: Int): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == vid && it.productId == pid
        }
    }

    suspend fun requestPermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { cont ->
        if (usbManager.hasPermission(device)) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                context.unregisterReceiver(this)
                val granted = intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true
                cont.resume(granted)
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, pi)
    }

    fun openDevice(device: UsbDevice): Boolean {
        // SAFETY NET: Wraps everything so it cannot crash the app
        try {
            var targetIntf: UsbInterface? = null
            var targetEp: UsbEndpoint? = null

            // Loop through all interfaces to find the one with an OUT endpoint
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        targetIntf = intf
                        targetEp = ep
                        break
                    }
                }
                if (targetIntf != null) break
            }

            if (targetIntf == null || targetEp == null) {
                Log.e(TAG, "No OUT endpoint found on any interface")
                return false
            }

            val conn = usbManager.openDevice(device) ?: run {
                Log.e(TAG, "openDevice failed")
                return false
            }

            // Try to claim it. If Android blocks it, the catch block will handle it.
            if (!conn.claimInterface(targetIntf, true)) {
                Log.e(TAG, "claimInterface failed. The system might be holding it.")
                conn.close()
                return false
            }

            connection = conn
            usbInterface = targetIntf
            outEndpoint = targetEp
            isConnected = true
            Log.d(TAG, "Successfully claimed interface ID: ${targetIntf.id}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "CRASH PREVENTED in openDevice: ${e.message}")
            return false
        }
    }

    fun sendPacket(packet: ByteArray): Boolean {
        val conn = connection ?: return false
        val ep = outEndpoint ?: return false

        return try {
            val sent = conn.bulkTransfer(ep, packet, packet.size, 1000)
            if (sent >= 0) {
                Log.d(TAG, "bulkTransfer sent $sent bytes")
                true
            } else {
                Log.w(TAG, "bulkTransfer failed ($sent), trying controlTransfer")
                tryControlTransfer(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendPacket error: ${e.message}")
            false
        }
    }

    private fun tryControlTransfer(packet: ByteArray): Boolean {
        val conn = connection ?: return false
        val intf = usbInterface ?: return false

        val requestType = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or 0x01
        val request = 0x09
        val value = 0x0200
        val index = intf.id

        val result = conn.controlTransfer(requestType, request, value, index, packet, packet.size, 1000)
        Log.d(TAG, "controlTransfer result: $result")
        return result >= 0
    }

    fun close() {
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close error: ${e.message}")
        } finally {
            connection = null
            usbInterface = null
            outEndpoint = null
            isConnected = false
        }
    }
}
