package com.ishan.mk1300

import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ishan.mk1300.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbController: UsbRgbController
    private var activeDevice: UsbDevice? = null

    private val TARGET_VID = 0x0000 
    private val TARGET_PID = 0x0000

    private val effectModes = listOf(
        "1: Static Core", "2: Cyber Breath", "3: Swing", "4: Neon Spin",
        "5: Data Stream", "6: Bloom", "7: UDWawe", "8: Cross",
        "9: Rain", "10: Meteor", "11: TrigSpread", "12: TrigSpreadReverse",
        "13: TrigSingle", "14: Soduko", "15: Tide", "16: UserLight",
        "0: System Dark"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbController = UsbRgbController(this)

        binding.spinnerEffect.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, effectModes
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnScanConnect.setOnClickListener { 
            try {
                scanAndConnect() 
            } catch (e: Exception) {
                log("CRITICAL CRASH PREVENTED: ${e.message}")
            }
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) updateColorPreviewAndSend()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekBrightness.setOnSeekBarChangeListener(seekListener)
        binding.seekSpeed.setOnSeekBarChangeListener(seekListener)
        binding.seekHue.setOnSeekBarChangeListener(seekListener)

        binding.spinnerEffect.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    updateColorPreviewAndSend()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        )

        binding.btnLuck.setOnClickListener {
            val hue = (0..255).random()
            binding.seekHue.progress = hue
            updateColorPreviewAndSend()
            log("🎲 Luck roll → Hue $hue")
        }
    }

    private fun scanAndConnect() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val devices = (getSystemService(USB_SERVICE) as android.hardware.usb.UsbManager).deviceList.values
                withContext(Dispatchers.Main) {
                    if (devices.isEmpty()) {
                        log("No USB devices found. Check OTG connection.")
                        return@withContext
                    }
                    val sb = StringBuilder("Detected devices:\n")
                    devices.forEach { d ->
                        sb.append("VID=0x${d.vendorId.toString(16).uppercase()} PID=0x${d.productId.toString(16).uppercase()} — ${d.productName ?: "Unknown"}\n")
                    }
                    log(sb.toString())
                }

                val target = devices.firstOrNull {
                    it.vendorId == TARGET_VID && it.productId == TARGET_PID
                } ?: devices.firstOrNull()

                if (target == null) {
                    withContext(Dispatchers.Main) { log("No target device selected.") }
                    return@launch
                }

                val granted = usbController.requestPermission(target)
                if (!granted) {
                    withContext(Dispatchers.Main) { log("USB permission denied.") }
                    return@launch
                }

                val opened = usbController.openDevice(target)
                withContext(Dispatchers.Main) {
                    if (opened) {
                        activeDevice = target
                        binding.tvStatus.text = "Connected: ${target.productName ?: "USB Device"}"
                        binding.tvStatus.setTextColor(Color.GREEN)
                        log("Device opened and interface claimed.")
                        updateColorPreviewAndSend()
                    } else {
                        binding.tvStatus.text = "Failed to open device."
                        binding.tvStatus.setTextColor(Color.RED)
                        log("openDevice() failed. The vendor interface may not exist.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("SCAN ERROR: ${e.message}")
                }
            }
        }
    }

    private fun updateColorPreviewAndSend() {
        try {
            val hue = binding.seekHue.progress
            val color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 1f, 1f))
            binding.colorPreview.setBackgroundColor(color)

            val device = activeDevice ?: return
            val mode = binding.spinnerEffect.selectedItemPosition
            val brightness = binding.seekBrightness.progress
            val speed = binding.seekSpeed.progress

            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            val h = (hsv[0] / 360f * 255f).roundToInt().coerceIn(0, 255)
            val s = (hsv[1] * 255f).roundToInt().coerceIn(0, 255)
            val v = (hsv[2] * 255f).roundToInt().coerceIn(0, 255)

            val packet = RgbPacketBuilder.buildPacket(mode, brightness, speed, h, s, v)

            lifecycleScope.launch(Dispatchers.IO) {
                val success = usbController.sendPacket(packet)
                withContext(Dispatchers.Main) {
                    log(if (success) "Packet sent (mode=$mode, HSV=$h/$s/$v)" else "Packet failed")
                }
            }
        } catch (e: Exception) {
            log("UI ERROR: ${e.message}")
        }
    }

    private fun log(msg: String) {
        binding.tvLog.append("\n$msg")
    }

    override fun onDestroy() {
        super.onDestroy()
        usbController.close()
    }
}
