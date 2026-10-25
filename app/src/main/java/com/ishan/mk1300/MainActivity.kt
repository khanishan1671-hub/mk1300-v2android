package com.ishan.mk1300

import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
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
    private var isUpdatingUI = false 
    private var isPerKeyMode = false

    private val TARGET_VID = 0x0000 
    private val TARGET_PID = 0x0000

    private val effectModes = listOf(
        "1: Static Core", "2: Cyber Breath", "3: Swing", "4: Neon Spin",
        "5: Data Stream", "6: Bloom", "7: UDWawe", "8: Cross",
        "9: Rain", "10: Meteor", "11: TrigSpread", "12: TrigSpreadReverse",
        "13: TrigSingle", "14: Soduko", "15: Tide", "16: UserLight",
        "0: System Dark"
    )
    private val hardwareModes = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 0)

    // Store the color state for all 61 keys
    private val keyColors = Array(61) { byteArrayOf(0, 255, 255) } // Default Cyan
    
    // Labels for the grid (roughly matching the 61 keys)
    private val keyLabels = arrayOf(
        "Esc", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Back",
        "Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\",
        "Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter",
        "Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "RShift",
        "Ctrl", "Win", "Alt", "Space", "Alt", "Fn", "Menu", "Ctrl"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbController = UsbRgbController(this)
        binding.spinnerEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, effectModes)

        buildKeyGrid()
        setupListeners()
        updateHexFromSliders()
    }

    private fun buildKeyGrid() {
        binding.keyGrid.removeAllViews()
        for (i in 0 until 61) {
            val btn = Button(this)
            btn.text = if (i < keyLabels.size) keyLabels[i] else "K$i"
            btn.textSize = 9f
            btn.setPadding(0, 0, 0, 0)
            
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(2, 2, 2, 2)
            btn.layoutParams = params

            btn.setBackgroundColor(Color.rgb(keyColors[i][0].toInt(), keyColors[i][1].toInt(), keyColors[i][2].toInt()))
            
            btn.setOnClickListener {
                // Instantly send the current slider color to this specific key
                val r = binding.seekR.progress.toByte()
                val g = binding.seekG.progress.toByte()
                val b = binding.seekB.progress.toByte()
                keyColors[i] = byteArrayOf(r, g, b)
                btn.setBackgroundColor(Color.rgb(r.toInt(), g.toInt(), b.toInt()))
                sendPerKeyPacket(i, r, g, b)
            }
            binding.keyGrid.addView(btn)
        }
    }

    private fun setupListeners() {
        binding.btnScanConnect.setOnClickListener { 
            try { scanAndConnect() } catch (e: Exception) { log("CRITICAL: ${e.message}") }
        }

        binding.btnToggleMode.setOnClickListener {
            isPerKeyMode = !isPerKeyMode
            if (isPerKeyMode) {
                binding.layoutGlobal.visibility = View.GONE
                binding.layoutPerKey.visibility = View.VISIBLE
                binding.btnToggleMode.text = "Switch to Global Effects"
            } else {
                binding.layoutGlobal.visibility = View.VISIBLE
                binding.layoutPerKey.visibility = View.GONE
                binding.btnToggleMode.text = "Switch to Per-Key Matrix"
                updateColorPreviewAndSend() // Re-apply global effect
            }
        }

        binding.btnApplyAll.setOnClickListener {
            val r = binding.seekR.progress.toByte()
            val g = binding.seekG.progress.toByte()
            val b = binding.seekB.progress.toByte()
            for (i in 0 until 61) {
                keyColors[i] = byteArrayOf(r, g, b)
            }
            buildKeyGrid() // Update UI
            sendAllKeysPacket()
        }

        val rgbSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUI) {
                    updateHexFromSliders()
                    if (!isPerKeyMode) updateColorPreviewAndSend()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekR.setOnSeekBarChangeListener(rgbSeekListener)
        binding.seekG.setOnSeekBarChangeListener(rgbSeekListener)
        binding.seekB.setOnSeekBarChangeListener(rgbSeekListener)

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
                        if (!isPerKeyMode) updateColorPreviewAndSend()
                    } catch (e: Exception) {}
                }
            }
        })

        val basicSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isPerKeyMode) updateColorPreviewAndSend()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekBrightness.setOnSeekBarChangeListener(basicSeekListener)
        binding.seekSpeed.setOnSeekBarChangeListener(basicSeekListener)

        binding.spinnerEffect.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (!isPerKeyMode) updateColorPreviewAndSend()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        )
    }

    private fun updateHexFromSliders() {
        val r = binding.seekR.progress
        val g = binding.seekG.progress
        val b = binding.seekB.progress
        val hex = String.format("#%02X%02X%02X", r, g, b)
        isUpdatingUI = true
        binding.etHex.setText(hex)
        binding.etHex.setSelection(binding.etHex.text.length)
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

                val target = devices.firstOrNull { it.vendorId == TARGET_VID && it.productId == TARGET_PID } ?: devices.firstOrNull()
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

    // --- GLOBAL EFFECT LOGIC ---
    private fun updateColorPreviewAndSend() {
        try {
            val r = binding.seekR.progress
            val g = binding.seekG.progress
            val b = binding.seekB.progress
            val color = Color.rgb(r, g, b)
            binding.colorPreview.setBackgroundColor(color)

            val device = activeDevice ?: return
            val uiPosition = binding.spinnerEffect.selectedItemPosition
            val mode = hardwareModes[uiPosition]
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
                    log(if (success) "Global Packet sent (Mode $mode | RGB $r,$g,$b)" else "Global Packet failed")
                }
            }
        } catch (e: Exception) {
            log("UI ERROR: ${e.message}")
        }
    }

    // --- PER-KEY LOGIC (Translated from kicktroll) ---
    private fun sendPerKeyPacket(keyIndex: Int, r: Byte, g: Byte, b: Byte) {
        if (activeDevice == null) return
        
        // Ported from JS: setKeyColor(keyIndex, r, g, b)
        val offset = 3 * keyIndex
        val packet = ByteArray(63) { 0 }
        packet[0] = 20
        packet[1] = 3
        packet[2] = (offset and 0xFF).toByte()
        packet[3] = ((offset shr 8) and 0xFF).toByte()
        packet[7] = r
        packet[8] = g
        packet[9] = b

        // We wrap it in the generic command structure expected by our working usbController
        // cmdByte is 6, payload starts with our packet
        val finalCmd = byteArrayOf(6) + packet
        
        lifecycleScope.launch(Dispatchers.IO) {
            usbController.sendPacket(finalCmd)
            withContext(Dispatchers.Main) {
                log("Per-Key Packet sent: Key $keyIndex")
            }
        }
    }

    private fun sendAllKeysPacket() {
        if (activeDevice == null) return

        // Ported from JS: setAllKeyColors(colorArray, keyCount)
        val totalBytes = 61 * 3
        val chunkSize = 56
        val chunks = Math.ceil(totalBytes.toDouble() / chunkSize).toInt()

        for (i in 0 until chunks) {
            val offset = chunkSize * i
            val remaining = totalBytes - offset
            val thisChunk = Math.min(chunkSize, remaining)
            val isLast = (i == chunks - 1)

            val packet = ByteArray(63) { 0 }
            packet[0] = 18
            packet[1] = (if (isLast && remaining % chunkSize > 0) (remaining % chunkSize) + 3 else chunkSize + 3).toByte()
            packet[2] = (offset and 0xFF).toByte()
            packet[3] = ((offset shr 8) and 0xFF).toByte()

            for (j in 0 until thisChunk) {
                val byteIndex = offset + j
                val keyIndex = byteIndex / 3
                val colorChannel = byteIndex % 3 // 0=R, 1=G, 2=B
                packet[7 + j] = keyColors[keyIndex][colorChannel]
            }

            val finalCmd = byteArrayOf(6) + packet
            
            // Send each chunk (not async here to maintain order)
            usbController.sendPacket(finalCmd)
        }
        log("Sent ALL keys packet chunks.")
    }

    private fun log(msg: String) {
        binding.tvLog.append("\n$msg")
    }

    override fun onDestroy() {
        super.onDestroy()
        usbController.close()
    }
}
