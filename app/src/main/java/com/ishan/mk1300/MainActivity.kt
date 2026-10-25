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

    // Data classes to hold kicktroll's exact layout
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

    // Max index is 118, so we need at least 119 slots.
    private val keyColors = Array(119) { byteArrayOf(0.toByte(), 255.toByte(), 255.toByte()) } 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbController = UsbRgbController(this)
        binding.spinnerEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, effectModes)

        buildRealKeyboard()
        setupListeners()
        updateHexFromSliders()
    }

    private fun buildRealKeyboard() {
        binding.keyRowContainer.removeAllViews()
        
        // We create 5 vertical columns (one for each row of the keyboard)
        val rowContainers = Array(5) { LinearLayout(this) }
        
        for (i in 0 until 5) {
            rowContainers[i].orientation = LinearLayout.VERTICAL
            rowContainers[i].layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            binding.keyRowContainer.addView(rowContainers[i])
        }

        // Now populate each row
        for (keyDef in keyboardLayout) {
            val btn = Button(this)
            btn.text = keyDef.name
            btn.textSize = 10f
            btn.setPadding(0, 0, 0, 0)
            btn.isAllCaps = false

            // Convert the float width (e.g., 1.5f) to pixels
            val widthPx = (keyDef.width * 45).toInt() // 45dp base width for a standard key
            val heightPx = 45 * resources.displayMetrics.density.toInt()

            val params = LinearLayout.LayoutParams(widthPx, heightPx)
            params.setMargins(3, 3, 3, 3)
            btn.layoutParams = params

            // Set initial color
            val c = keyColors[keyDef.index]
            btn.setBackgroundColor(Color.rgb(c[0].toInt(), c[1].toInt(), c[2].toInt()))
            
            // On click, send color to THAT specific index
            btn.setOnClickListener {
                val r = binding.seekR.progress.toByte()
                val g = binding.seekG.progress.toByte()
                val b = binding.seekB.progress.toByte()
                keyColors[keyDef.index] = byteArrayOf(r, g, b)
                btn.setBackgroundColor(Color.rgb(r.toInt(), g.toInt(), b.toInt()))
                sendPerKeyPacket(keyDef.index, r, g, b)
            }
            
            rowContainers[keyDef.row].addView(btn)
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
                
                // Force UserLight mode to accept per-key
                binding.spinnerEffect.setSelection(15) // "16: UserLight"
                updateColorPreviewAndSend() 
            } else {
                binding.layoutGlobal.visibility = View.VISIBLE
                binding.layoutPerKey.visibility = View.GONE
                binding.btnToggleMode.text = "Switch to Per-Key Matrix"
                updateColorPreviewAndSend()
            }
        }

        binding.btnApplyAll.setOnClickListener {
            val r = binding.seekR.progress.toByte()
            val g = binding.seekG.progress.toByte()
            val b = binding.seekB.progress.toByte()
            for (i in 0 until 119) {
                keyColors[i] = byteArrayOf(r, g, b)
            }
            buildRealKeyboard() // Update UI colors
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
                        log("openDevice() failed.")
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
                    log(if (success) "Global Packet sent (Mode $mode)" else "Global Packet failed")
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
                log("Per-Key Packet: Index $keyIndex")
            }
        }
    }

    private fun sendAllKeysPacket() {
        if (activeDevice == null) return

        // Max index 118 = 119 total keys. 119 * 3 = 357 total bytes.
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
                // Check bounds just in case
                packet[7 + j] = if (keyIndex < keyColors.size) keyColors[keyIndex][colorChannel] else 0
            }

            val finalCmd = byteArrayOf(6) + packet
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
