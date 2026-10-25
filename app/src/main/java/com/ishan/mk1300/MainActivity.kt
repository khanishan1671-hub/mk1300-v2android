package com.ishan.mk1300

import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private var isUpdatingUI = false // Prevents infinite loops between Hex and Sliders

    private val TARGET_VID = 0x0000 
    private val TARGET_PID = 0x0000

    private val effectModes = listOf(
        "1: Static Core", "2: Cyber Breath", "3: Swing", "4: Neon Spin",
        "5: Data Stream", "6: Bloom", "7: UDWawe", "8: Cross",
        "9: Rain", "10: Meteor", "11: TrigSpread", "12: TrigSpreadReverse",
        "13: TrigSingle", "14: Soduko", "15: Tide", "16: UserLight",
        "0: System Dark"
    )

    // FIX: Maps the UI dropdown position (0-16) to the actual hardware values (1-16, then 0)
    private val hardwareModes = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbController = UsbRgbController(this)

        binding.spinnerEffect.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, effectModes
        )

        setupListeners()
        updateHexFromSliders() // Initialize
    }

    private fun setupListeners() {
        binding.btnScanConnect.setOnClickListener { 
            try { scanAndConnect() } catch (e: Exception) { log("CRITICAL: ${e.message}") }
        }

        // RGB Slider Listener
        val rgbSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUI) {
                    updateHexFromSliders()
                    updateColorPreviewAndSend()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekR.setOnSeekBarChangeListener(rgbSeekListener)
        binding.seekG.setOnSeekBarChangeListener(rgbSeekListener)
        binding.seekB.setOnSeekBarChangeListener(rgbSeekListener)

        // Hex Input Listener
        binding.etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingUI) return
                val hex = s.toString().replace("#", "").trim()
                if (hex.length == 6) {
                    try {
                        val r = Integer.parseInt(hex.substring(0, 2), 16)
                        val g = Integer.parseInt(hex.substring(2, 4), 16)
                        val b = Integer.parseInt(hex.substring(4, 6), 16)
                        isUpdatingUI = true
                        binding.seekR.progress = r
                        binding.seekG.progress = g
                        binding.seekB.progress = b
                        isUpdatingUI = false
                        updateColorPreviewAndSend()
                    } catch (e: Exception) {} // Ignore invalid hex
                }
            }
        })

        val basicSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) updateColorPreviewAndSend()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekBrightness.setOnSeekBarChangeListener(basicSeekListener)
        binding.seekSpeed.setOnSeekBarChangeListener(basicSeekListener)

        binding.spinnerEffect.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    updateColorPreviewAndSend()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        )

        binding.btnLuck.setOnClickListener {
            isUpdatingUI = true
            binding.seekR.progress = (0..255).random()
            binding.seekG.progress = (0..255).random()
            binding.seekB.progress = (0..255).random()
            isUpdatingUI = false
            updateHexFromSliders()
            updateColorPreviewAndSend()
            log("🎲 Luck roll applied!")
        }
    }

    private fun updateHexFromSliders() {
        val r = binding.seekR.progress
        val g = binding.seekG.progress
        val b = binding.seekB.progress
        val hex = String.format("#%02X%02X%02X", r, g, b)
        
        isUpdatingUI = true
        binding.etHex.setText(hex)
        binding.etHex.setSelection(binding.etHex.text.length) // Put cursor at end
        isUpdatingUI = false
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
                withContext(Dispatchers.Main) { log("SCAN ERROR: ${e.message}") }
            }
        }
    }

    private fun updateColorPreviewAndSend() {
        try {
            val r = binding.seekR.progress
            val g = binding.seekG.progress
            val b = binding.seekB.progress
            
            // Update Preview
            val color = Color.rgb(r, g, b)
            binding.colorPreview.setBackgroundColor(color)

            val device = activeDevice ?: return
            
            // Fix effect mode mapping
            val uiPosition = binding.spinnerEffect.selectedItemPosition
            val mode = hardwareModes[uiPosition]
            
            val brightness = binding.seekBrightness.progress
            val speed = binding.seekSpeed.progress

            // Convert RGB to HSV for the hardware
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            val h = (hsv[0] / 360f * 255f).roundToInt().coerceIn(0, 255)
            val s = (hsv[1] * 255f).roundToInt().coerceIn(0, 255)
            val v = (hsv[2] * 255f).roundToInt().coerceIn(0, 255)

            val packet = RgbPacketBuilder.buildPacket(mode, brightness, speed, h, s, v)

            lifecycleScope.launch(Dispatchers.IO) {
                val success = usbController.sendPacket(packet)
                withContext(Dispatchers.Main) {
                    log(if (success) "Packet sent (Mode $mode | RGB $r,$g,$b)" else "Packet failed")
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
