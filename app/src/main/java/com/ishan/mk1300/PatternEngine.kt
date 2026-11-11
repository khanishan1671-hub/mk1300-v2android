package com.ishan.mk1300

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

object PatternEngine {
    // Data class matching your keyboard layout exactly
    data class KeyPos(val index: Int, val name: String, val x: Float, val y: Float, val w: Float)

    // Note: I am using the exact x/y coordinates from kicktroll's layout for math
    val layout = listOf(
        KeyPos(21, "Esc", 0f, 0f, 1f), KeyPos(22, "1", 1f, 0f, 1f), KeyPos(23, "2", 2f, 0f, 1f),
        KeyPos(24, "3", 3f, 0f, 1f), KeyPos(25, "4", 4f, 0f, 1f), KeyPos(26, "5", 5f, 0f, 1f),
        KeyPos(27, "6", 6f, 0f, 1f), KeyPos(28, "7", 7f, 0f, 1f), KeyPos(29, "8", 8f, 0f, 1f),
        KeyPos(30, "9", 9f, 0f, 1f), KeyPos(31, "0", 10f, 0f, 1f), KeyPos(32, "-", 11f, 0f, 1f),
        KeyPos(33, "=", 12f, 0f, 1f), KeyPos(34, "Back", 13f, 0f, 2f),
        KeyPos(42, "Tab", 0f, 1f, 1.5f), KeyPos(43, "Q", 1.5f, 1f, 1f), KeyPos(44, "W", 2.5f, 1f, 1f),
        KeyPos(45, "E", 3.5f, 1f, 1f), KeyPos(46, "R", 4.5f, 1f, 1f), KeyPos(47, "T", 5.5f, 1f, 1f),
        KeyPos(48, "Y", 6.5f, 1f, 1f), KeyPos(49, "U", 7.5f, 1f, 1f), KeyPos(50, "I", 8.5f, 1f, 1f),
        KeyPos(51, "O", 9.5f, 1f, 1f), KeyPos(52, "P", 10.5f, 1f, 1f), KeyPos(53, "[", 11.5f, 1f, 1f),
        KeyPos(54, "]", 12.5f, 1f, 1f), KeyPos(55, "\\", 13.5f, 1f, 1.5f),
        KeyPos(63, "Caps", 0f, 2f, 1.75f), KeyPos(64, "A", 1.75f, 2f, 1f), KeyPos(65, "S", 2.75f, 2f, 1f),
        KeyPos(66, "D", 3.75f, 2f, 1f), KeyPos(67, "F", 4.75f, 2f, 1f), KeyPos(68, "G", 5.75f, 2f, 1f),
        KeyPos(69, "H", 6.75f, 2f, 1f), KeyPos(70, "J", 7.75f, 2f, 1f), KeyPos(71, "K", 8.75f, 2f, 1f),
        KeyPos(72, "L", 9.75f, 2f, 1f), KeyPos(73, ";", 10.75f, 2f, 1f), KeyPos(74, "'", 11.75f, 2f, 1f),
        KeyPos(76, "Enter", 12.75f, 2f, 2.25f),
        KeyPos(84, "Shift", 0f, 3f, 2.25f), KeyPos(86, "Z", 2.25f, 3f, 1f), KeyPos(87, "X", 3.25f, 3f, 1f),
        KeyPos(88, "C", 4.25f, 3f, 1f), KeyPos(89, "V", 5.25f, 3f, 1f), KeyPos(90, "B", 6.25f, 3f, 1f),
        KeyPos(91, "N", 7.25f, 3f, 1f), KeyPos(92, "M", 8.25f, 3f, 1f), KeyPos(93, ",", 9.25f, 3f, 1f),
        KeyPos(94, ".", 10.25f, 3f, 1f), KeyPos(95, "/", 11.25f, 3f, 1f), KeyPos(97, "Shift", 12.25f, 3f, 2.75f),
        KeyPos(105, "Ctrl", 0f, 4f, 1.25f), KeyPos(106, "Win", 1.25f, 4f, 1.25f), KeyPos(107, "Alt", 2.5f, 4f, 1.25f),
        KeyPos(111, "Space", 3.75f, 4f, 6.25f), KeyPos(115, "Alt", 10f, 4f, 1.25f), KeyPos(116, "Menu", 11.25f, 4f, 1f),
        KeyPos(117, "Ctrl", 12.25f, 4f, 1.25f), KeyPos(118, "Fn", 13.5f, 4f, 1.5f)
    )

    // Helper functions
    private fun nx(k: KeyPos) = (k.x + k.w / 2f) / 15f
    private fun ny(k: KeyPos) = (k.y + 0.5f) / 5f
    
    private fun hsl(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(((r1 + m) * 255).roundToInt().coerceIn(0,255), ((g1 + m) * 255).roundToInt().coerceIn(0,255), ((b1 + m) * 255).roundToInt().coerceIn(0,255))
    }

    private fun isMod(k: KeyPos) = k.name in listOf("Esc", "Tab", "Caps", "Shift", "Ctrl", "Win", "Alt", "Menu", "Fn", "Enter", "Space")
    private fun isNum(k: KeyPos) = k.name in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=")

    // --- THE PRESETS ---
    fun getPreset(name: String): Map<Int, Triple<Int, Int, Int>> {
        val map = mutableMapOf<Int, Triple<Int, Int, Int>>()
        
        for (k in layout) {
            val color = when (name) {
                "Cyberpunk" -> {
                    if (isMod(k)) hsl(300f, 1f, 0.50f)
                    else if (isNum(k)) hsl(55f, 1f, 0.50f)
                    else hsl(186f, 1f, 0.50f)
                }
                "Dracula" -> {
                    if (k.name in listOf("Enter", "Esc")) hsl(135f, 1f, 0.45f)
                    else if (isMod(k)) hsl(326f, 1f, 0.62f)
                    else hsl(265f + nx(k) * 15f, 0.60f, 0.38f)
                }
                "Matrix" -> {
                    hsl(120f + nx(k) * 20f, 1f, 0.25f + abs(sin(nx(k) * 12f)) * 0.30f)
                }
                "Vaporwave" -> {
                    val d = nx(k) * 0.5f + ny(k) * 0.5f
                    hsl(310f - d * 60f, 0.85f, 0.55f)
                }
                "Synthwave" -> {
                    val t = ny(k)
                    val h = if (t < 0.3f) 200f else if (t < 0.6f) 270f else 330f
                    hsl(h, 1f, 0.42f + (1 - t) * 0.08f)
                }
                "WASD" -> {
                    if (k.index in listOf(44, 64, 65, 66)) hsl(186f, 1f, 0.55f)
                    else if (k.name == "Space") hsl(186f, 0.6f, 0.30f)
                    else hsl(220f, 0.50f, 0.08f + ny(k) * 0.05f)
                }
                "Sunset" -> hsl(0f + nx(k) * 50f, 1f, 0.48f)
                "Ocean" -> {
                    val t = 1 - ny(k)
                    hsl(200f + t * 20f, 1f, 0.12f + t * 0.42f)
                }
                "Neon Noir" -> {
                    if (k.index in listOf(21, 34, 76, 84, 97, 111)) hsl(300f, 1f, 0.55f)
                    else if (!isMod(k) && !isNum(k)) hsl(220f, 0.40f, 0.08f)
                    else Triple(0, 0, 0)
                }
                "Holographic" -> {
                    val d = nx(k) * 0.6f + ny(k) * 0.4f
                    hsl((d * 360f) % 360f, 1f, 0.52f)
                }
                else -> hsl(0f, 0f, 1f) // Default white
            }
            map[k.index] = color
        }
        return map
    }
}
