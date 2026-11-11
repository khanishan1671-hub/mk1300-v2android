package com.ishan.mk1300

import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ishan.mk1300.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

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

    data class KeyDef(val index: Int, val name: String, val width: Float, val row: Int)

    private val keyboardLayout = listOf(
        // Row 0
        KeyDef(21, "Esc", 1f, 0), KeyDef(22, "1", 1f, 0), KeyDef(23, "2", 1f, 0),
        KeyDef(24, "3", 1f, 0), KeyDef(25, "4", 1f, 0), KeyDef(26, "5", 1f, 0),
        KeyDef(27, "6", 1f, 0), KeyDef(28, "7", 1f, 0), KeyDef(29, "8", 1f, 0),
        KeyDef(30, "9", 1f, 0), KeyDef(31, "0", 1f, 0), KeyDef(32, "-", 1f, 0),
        KeyDef(33, "=", 1f, 0), KeyDef(34, "Back", 2f, 0),
        
        // Row 1
        KeyDef(42, "Tab", 1.5f, 1), KeyDef(43, "Q", 1f, 1), KeyDef(44, "W", 1f, 1),
        KeyDef(45, "E", 1f, 1), KeyDef(46, "R", 1f, 1), KeyDef(47, "T", 1f, 1),
        KeyDef(48, "Y", 1f, 1), KeyDef(49, "U", 1f, 1), KeyDef(50, "I", 1f, 1),
        KeyDef(51, "O", 1f, 1), KeyDef(52, "P", 1f, 1), KeyDef(53, "[", 1f, 1),
        KeyDef(54, "]", 1f, 1), KeyDef(55, "\\", 1.5f, 1),

        // Row 2
        KeyDef(63, "Caps", 1.75f, 2), KeyDef(64, "A", 1f, 2), KeyDef(65, "S", 1f, 2),
        KeyDef(66, "D", 1f, 2), KeyDef(67, "F", 1f, 2), KeyDef(68, "G", 1f, 2),
        KeyDef(69, "H", 1f, 2), KeyDef(70, "J", 1f, 2), KeyDef(71, "K", 1f, 2),
        KeyDef(72, "L", 1f, 2), KeyDef(73, ";", 1f, 2), KeyDef(74, "'", 1f, 2),
        KeyDef(76, "Enter", 2.25f, 2),

        // Row 3
        KeyDef(84, "Shift", 2.25f, 3), KeyDef(86, "Z", 1f, 3), KeyDef(87, "X", 1f, 3),
        KeyDef(88, "C", 1f, 3), KeyDef(89, "V", 1f, 3), KeyDef(90, "B", 1f, 3),
        KeyDef(91, "N", 1f, 3), KeyDef(92, "M", 1f, 3), KeyDef(93, ",", 1f, 3),
        KeyDef(94, ".", 1f, 3), KeyDef(95, "/", 1f, 3), KeyDef(97, "Shift", 2.75f, 3),

        // Row 4
        KeyDef(105, "Ctrl", 1.25f, 4), KeyDef(106, "Win", 1.25f, 4), KeyDef(107, "Alt", 1.25f, 4),
        KeyDef(111, "Space", 6.25f, 4), KeyDef(115, "Alt", 1.25f, 4), KeyDef(116, "Menu", 1f, 4),
        KeyDef(117, "Ctrl", 1.25f, 4), KeyDef(118, "Fn", 1.5f, 4)
    )

    // Store RGB for each key. Initialized to unlit / zeroed.
    private val keyColors = Array(119) { byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()) } 
    private val keyButtons = mutableMapOf<Int, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbController = UsbRgbController(this)
        binding.spinnerEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, effectModes)

        buildRealKeyboard()
        setupListeners()
        updateHexFromSliders()
        updatePreviewBoxOnly()
    }

    private fun buildRealKeyboard() {
        binding.keyRowContainer.removeAllViews()
        binding.keyRowContainer.orientation = LinearLayout.VERTICAL
        keyButtons.clear()
        
        val rowContainers = Array(5) { 
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }
        
        for (row in rowContainers) {
            binding.keyRowContainer.addView(row)
        }

        val density = resources.displayMetrics.density

        for (keyDef in keyboardLayout) {
            val btn = Button(this).apply {
                text = keyDef.name
                textSize = 9f
                setPadding(0, 0, 0, 0)
                isAllCaps = false
            }

            val widthPx = (keyDef.width * 42f * density).roundToInt()
            val heightPx = (42f * density).roundToInt()

            val params = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                setMargins(2, 2, 2, 2)
            }
            btn.layoutParams = params

            updateKeyButtonVisual(btn, keyColors[keyDef.index])
            
            btn.setOnClickListener {
                val r = binding.seekR.progress.toByte()
                val g = binding.seekG.progress.toByte()
                val b = binding.seekB.progress.toByte()
                keyColors[keyDef.index] = byteArrayOf(r, g, b)

                updateKeyButtonVisual(btn, keyColors[keyDef.index])
                sendPerKeyPacket(keyDef.index, r, g, b)
            }
            
            keyButtons[keyDef.index] = btn
            rowContainers[keyDef.row].addView(btn)
        }
    }

    private fun updateKeyButtonVisual(btn: Button, colorBytes: ByteArray) {
        val rInt = colorBytes[0].toInt() and 0xFF
        val gInt = colorBytes[1].toInt() and 0xFF
        val bInt = colorBytes[2].toInt() and 0xFF
        
        if (rInt == 0 && gInt == 0 && bInt == 0) {
            btn.setBackgroundColor(Color.parseColor("#12141C"))
            btn.setTextColor(Color.parseColor("#4B5565"))
        } else {
            btn.setBackgroundColor(Color.rgb(rInt, gInt, bInt))
            val luminance = rInt * 0.299 + gInt * 0.587 + bInt * 0.114
            btn.setTextColor(if (luminance > 140) Color.BLACK else Color.WHITE)
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
                binding.btnToggleMode.text = "SWITCH TO GLOBAL EFFECTS"
                
                // Switch hardware to UserLight mode (index 15) so custom keys display
                binding.spinnerEffect.setSelection(15) 
                sendGlobalConfigPacket()
            } else {
                binding.layoutGlobal.visibility = View.VISIBLE
                binding.layoutPerKey.visibility = View.GONE
                binding.btnToggleMode.text = "SWITCH TO PER-KEY MATRIX"
                sendGlobalConfigPacket()
            }
        }

        // CLEAR ALL / BLANK SLATE
        binding.btnClearAll.setOnClickListener {
            for (i in 0 until 119) {
                keyColors[i] = byteArrayOf(0, 0, 0)
            }
            // Update visual keys in UI
            for ((idx, btn) in keyButtons) {
                updateKeyButtonVisual(btn, keyColors[idx])
            }
            sendAllKeysPacket()
            log("🧹 Cleared all keys to unlit state.")
        }

        binding.btnLuck.setOnClickListener {
            val randomR = Random.nextInt(256)
            val randomG = Random.nextInt(256)
            val randomB = Random.nextInt(256)
            binding.seekR.progress = randomR
            binding.seekG.progress = randomG
            binding.seekB.progress = randomB
            updateHexFromSliders()
            updatePreviewBoxOnly()
            if (!isPerKeyMode) {
                sendGlobalConfigPacket()
            }
            log("🎲 Color: R=$randomR G=$randomG B=$randomB")
        }

        val rgbSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUI) {
                    updateHexFromSliders()
                    updatePreviewBoxOnly()
                    if (!isPerKeyMode) sendGlobalConfigPacket()
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
                        updatePreviewBoxOnly()
                        if (!isPerKeyMode) sendGlobalConfigPacket()
                    } catch (e: Exception) {}
                }
            }
        })

        val basicSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isPerKeyMode) sendGlobalConfigPacket()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        binding.seekBrightness.setOnSeekBarChangeListener(basicSeekListener)
        binding.seekSpeed.setOnSeekBarChangeListener(basicSeekListener)

        binding.spinnerEffect.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (!isPerKeyMode) sendGlobalConfigPacket()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
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

    // Always updates the active color preview box regardless of mode
    private fun updatePreviewBoxOnly() {
        val r = binding.seekR.progress
        val g = binding.seekG.progress
        val b = binding.seekB.progress
        binding.colorPreview.setBackgroundColor(Color.rgb(r, g, b))
    }

    private fun scanAndConnect() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val devices = (getSystemService(USB_SERVICE) as android.hardware.usb.UsbManager).deviceList.values
                if (devices.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        log("No USB devices found. Check OTG cable & settings.")
                    }
                    return@launch
                }

                val target = devices.firstOrNull { it.vendorId == TARGET_VID && it.productId == TARGET_PID } ?: devices.firstOrNull()
                if (target == null) {
                    withContext(Dispatchers.Main) { log("No target device found.") }
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
                        binding.tvStatus.text = "ONLINE // ${target.productName ?: "MK1300"}"
                        binding.tvStatus.setTextColor(Color.parseColor("#00FFA3"))
                        binding.tvStatus.setBackgroundColor(Color.parseColor("#102820"))
                        log("Device synced & interface claimed successfully.")
                        sendGlobalConfigPacket()
                    } else {
                        binding.tvStatus.text = "SYNC FAILED"
                        binding.tvStatus.setTextColor(Color.parseColor("#FF0055"))
                        binding.tvStatus.setBackgroundColor(Color.parseColor("#2A1522"))
                        log("openDevice() failed.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { log("SCAN ERROR: ${e.message}") }
            }
        }
    }

    private fun sendGlobalConfigPacket() {
        try {
            if (activeDevice == null) return
            val r = binding.seekR.progress
            val g = binding.seekG.progress
            val b = binding.seekB.progress
            val color = Color.rgb(r, g, b)

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
                    log(if (success) "Sent Global Packet [Mode $mode]" else "Failed to send packet")
                }
            }
        } catch (e: Exception) {
            log("UI ERROR: ${e.message}")
        }
    }

    private fun sendPerKeyPacket(keyIndex: Int, r: Byte, g: Byte, b: Byte) {
        if (activeDevice == null) return
        
        val offset = 3 * keyIndex
        val packet = ByteArray(63) { 0 }
        packet[0] = 20
        packet[1] = 3
        packet[2] = (offset and 0xFF).toByte()
        packet[3] = ((offset shr 8) and 0xFF).toByte()
        packet[7] = r
        packet[8] = g
        packet[9] = b

        val finalCmd = byteArrayOf(6) + packet
        
        lifecycleScope.launch(Dispatchers.IO) {
            usbController.sendPacket(finalCmd)
            withContext(Dispatchers.Main) {
                log("Per-Key Dispatched: KeyIndex #$keyIndex")
            }
        }
    }

    private fun sendAllKeysPacket() {
        if (activeDevice == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val totalBytes = 119 * 3 
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
                    val colorChannel = byteIndex % 3 
                    packet[7 + j] = if (keyIndex < keyColors.size) keyColors[keyIndex][colorChannel] else 0
                }

                val finalCmd = byteArrayOf(6) + packet
                usbController.sendPacket(finalCmd)
            }
            withContext(Dispatchers.Main) {
                log("All-Key Chunks Dispatched (357 bytes).")
            }
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
