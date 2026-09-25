package com.example.luminawallpapers.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Base64
import com.example.luminawallpapers.data.LiveWallpaperSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Inflater

class RetroDeskCompanionRenderer(private val context: Context) : SensorEventListener {

    val gridWidth = 180
    val gridHeight = 400

    private val virtualBitmap: Bitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
    private val pixelArray = IntArray(gridWidth * gridHeight)
    private val srcRect = Rect(0, 0, gridWidth, gridHeight)
    private val dstRect = RectF()
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }

    enum class Theme(val label: String) {
        PEACH("Warm Peach"),
        SAKURA("Sakura Lavender"),
        MATCHA("Matcha Mint"),
        DARK("Midnight Dark")
    }

    var currentTheme = Theme.PEACH
    var cycleDay = 14
    var cycleLength = 28
    var stepsCount = 0
    var stepGoal = 10000
    var waterGlasses = 6
    var habit1Done = true
    var habit2Done = true
    var habit3Done = false

    var customDialogue = "YOU GOT THIS! ♡"
    var habit1Title = "VITAMINS"
    var habit2Title = "WATER"
    var habit3Title = "10K STEPS"

    // Animation & Dialogue Bubble state
    var speechBubbleText: String? = null
    var speechBubbleTimer = 0f
    var companionBounceTimer = 0f
    var companionBounceY = 0
    private var dialogueIndex = 0

    // Animation state
    private var blinkTimer = 0f
    private var isBlinking = false
    private var breathTimer = 0f
    private var lastUpdateTime = System.currentTimeMillis()

    // Hardware Step Counter Sensor
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var lastHardwareSensorReading = -1

    // Compressed 696-byte base companion artwork (100 x 90)
    private val baseArtIndices: ByteArray by lazy {
        try {
            val bytes = Base64.decode(BASE_ARTWORK_B64, Base64.DEFAULT)
            val inflater = Inflater()
            inflater.setInput(bytes)
            val result = ByteArray(90 * 100)
            inflater.inflate(result)
            inflater.end()
            result
        } catch (e: Exception) {
            ByteArray(90 * 100)
        }
    }

    init {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            stepSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            // Ignore sensor init failures
        }
        loadFromSettings()
    }

    fun loadFromSettings() {
        val settings = LiveWallpaperSettings(context)
        currentTheme = when (settings.os01Theme) {
            "SAKURA" -> Theme.SAKURA
            "MATCHA" -> Theme.MATCHA
            "DARK" -> Theme.DARK
            else -> Theme.PEACH
        }
        customDialogue = settings.os01CustomDialogue
        habit1Title = settings.os01Habit1Title
        habit2Title = settings.os01Habit2Title
        habit3Title = settings.os01Habit3Title

        // Automatic calendar-based cycle day!
        cycleDay = settings.getCalculatedCycleDay()
        cycleLength = settings.os01CycleLength.coerceIn(21, 35)
        stepGoal = settings.os01StepGoal.coerceAtLeast(1000)
        
        checkAndResetDailySteps()
        stepsCount = settings.os01TodaySteps
        
        waterGlasses = settings.os01WaterGlasses.coerceIn(0, 8)
        habit1Done = settings.os01Habit1
        habit2Done = (waterGlasses >= 8) || settings.os01Habit2
        habit3Done = (stepsCount >= stepGoal) || settings.os01Habit3
    }

    fun saveToSettings() {
        val settings = LiveWallpaperSettings(context)
        settings.os01Theme = currentTheme.name
        settings.os01CycleLength = cycleLength
        settings.os01StepGoal = stepGoal
        settings.os01TodaySteps = stepsCount
        settings.os01WaterGlasses = waterGlasses
        settings.os01Habit1 = habit1Done
        settings.os01Habit2 = habit2Done
        settings.os01Habit3 = habit3Done
        settings.os01CustomDialogue = customDialogue
        settings.os01Habit1Title = habit1Title
        settings.os01Habit2Title = habit2Title
        settings.os01Habit3Title = habit3Title
    }

    private fun checkAndResetDailySteps() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val settings = LiveWallpaperSettings(context)
        if (settings.os01StepDate != today) {
            settings.os01StepDate = today
            settings.os01TodaySteps = 0
            if (lastHardwareSensorReading >= 0) {
                settings.os01SensorBaseline = lastHardwareSensorReading
            }
            stepsCount = 0
            habit3Done = false
            settings.os01Habit3 = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSensorSteps = event.values[0].toInt()
            lastHardwareSensorReading = totalSensorSteps
            val settings = LiveWallpaperSettings(context)
            checkAndResetDailySteps()

            if (settings.os01SensorBaseline < 0) {
                settings.os01SensorBaseline = totalSensorSteps
                settings.os01TodaySteps = 0
            } else {
                val delta = (totalSensorSteps - settings.os01SensorBaseline).coerceAtLeast(0)
                settings.os01TodaySteps = delta
            }
            stepsCount = settings.os01TodaySteps
            if (stepsCount >= stepGoal) {
                habit3Done = true
                settings.os01Habit3 = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun cleanup() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun triggerCompanionReaction() {
        companionBounceTimer = 0.45f
        val curPhaseStr = when {
            cycleDay <= 5 -> "MENSTRUAL"
            cycleDay <= 13 -> "FOLLICULAR"
            cycleDay <= 16 -> "OVULATION"
            else -> "LUTEAL"
        }
        val pool = listOf(
            customDialogue.ifBlank { "YOU GOT THIS! ♡" },
            "STAY HYDRATED! 💧",
            "CYCLE: $curPhaseStr ♡",
            "REMEMBER TO REST!",
            "TAKE A DEEP BREATH",
            "YOU ARE AMAZING! ★",
            "KEEP SHINING! ✨"
        )
        speechBubbleText = pool[dialogueIndex % pool.size]
        dialogueIndex++
        speechBubbleTimer = 3.8f
    }

    // Touch Interaction
    fun onTouch(normX: Float, normY: Float): Boolean {
        val gx = (normX * gridWidth).toInt()
        val gy = (normY * gridHeight).toInt()

        // 1. Scaled Hero Desk Companion: Tap to animate + show comic speech bubble! (Y: 74..186)
        if (gx in 25..155 && gy in 74..186) {
            triggerCompanionReaction()
            return true
        }

        // 2. Step Widget: Tap to report real hardware step progress (NO mock increment!)
        if (gx in 14..166 && gy in 192..246) {
            val pct = ((stepsCount.toFloat() / stepGoal.coerceAtLeast(1)) * 100).toInt()
            speechBubbleText = "$stepsCount/$stepGoal STEPS! ($pct%)"
            speechBubbleTimer = 3.5f
            companionBounceTimer = 0.35f
            return true
        }

        // 3. Daily Wellness Checklist (Y: 252..316)
        if (gx in 14..166) {
            when (gy) {
                in 254..273 -> { // Habit 1
                    habit1Done = !habit1Done
                    saveToSettings()
                    speechBubbleText = if (habit1Done) "$habit1Title DONE! ★" else "$habit1Title REMINDER"
                    speechBubbleTimer = 2.5f
                    return true
                }
                in 274..293 -> { // Habit 2: water glasses tap to log +1 cup
                    waterGlasses = (waterGlasses + 1) % 9
                    habit2Done = (waterGlasses >= 8)
                    saveToSettings()
                    speechBubbleText = "WATER: $waterGlasses/8 CUPS" + (if (waterGlasses >= 8) " GOAL!" else "")
                    speechBubbleTimer = 2.5f
                    return true
                }
                in 294..316 -> { // Habit 3
                    habit3Done = !habit3Done
                    saveToSettings()
                    speechBubbleText = if (habit3Done) "$habit3Title DONE! ★" else "$habit3Title REMINDER"
                    speechBubbleTimer = 2.5f
                    return true
                }
            }
        }

        return false
    }

    fun update(dt: Float) {
        blinkTimer += dt
        if (!isBlinking && blinkTimer > 4.5f) {
            isBlinking = true
            blinkTimer = 0f
        } else if (isBlinking && blinkTimer > 0.16f) {
            isBlinking = false
            blinkTimer = 0f
        }
        breathTimer += dt * 1.5f

        // Interactive bounce spring
        if (companionBounceTimer > 0f) {
            companionBounceTimer -= dt
            val p = (companionBounceTimer / 0.45f).coerceIn(0f, 1f)
            companionBounceY = (-Math.sin(p * Math.PI) * 4.0).toInt()
        } else {
            companionBounceY = 0
        }

        // Speech bubble auto decay
        if (speechBubbleTimer > 0f) {
            speechBubbleTimer -= dt
            if (speechBubbleTimer <= 0f) {
                speechBubbleText = null
            }
        }
    }

    fun render(canvas: Canvas, screenW: Int, screenH: Int) {
        val now = System.currentTimeMillis()
        val dt = ((now - lastUpdateTime) / 1000f).coerceIn(0.001f, 0.1f)
        lastUpdateTime = now
        update(dt)

        renderVirtualGrid()

        dstRect.set(0f, 0f, screenW.toFloat(), screenH.toFloat())
        canvas.drawBitmap(virtualBitmap, srcRect, dstRect, bitmapPaint)
    }

    private inline fun putPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until gridWidth && y in 0 until gridHeight) {
            pixelArray[y * gridWidth + x] = color
        }
    }

    private fun drawRoundedCard(x1: Int, y1: Int, x2: Int, y2: Int, fillCol: Int, outlineCol: Int, shadowOffset: Int = 2) {
        if (shadowOffset > 0) {
            val shadowColor = Color.argb(45, 0, 0, 0)
            for (sy in (y1 + shadowOffset)..(y2 + shadowOffset)) {
                for (sx in (x1 + shadowOffset)..(x2 + shadowOffset)) {
                    if (sx > x2 || sy > y2) {
                        putPixel(sx, sy, shadowColor)
                    }
                }
            }
        }
        for (fy in y1..y2) {
            for (fx in x1..x2) {
                putPixel(fx, fy, fillCol)
            }
        }
        for (x in x1..x2) {
            putPixel(x, y1, outlineCol)
            putPixel(x, y2, outlineCol)
        }
        for (y in y1..y2) {
            putPixel(x1, y, outlineCol)
            putPixel(x2, y, outlineCol)
        }
    }

    private fun drawCleanText(text: String, startX: Int, startY: Int, color: Int, scale: Int = 1) {
        var cursorX = startX
        val upper = text.uppercase(Locale.US)
        for (i in 0 until upper.length) {
            val ch = upper[i]
            val glyph = FONT_4X6[ch] ?: FONT_4X6[' ']!!
            for (row in 0 until 6) {
                val line = glyph[row]
                for (col in 0 until 4) {
                    if (line[col] == '1') {
                        val rx = cursorX + col * scale
                        val ry = startY + row * scale
                        for (dy in 0 until scale) {
                            for (dx in 0 until scale) {
                                putPixel(rx + dx, ry + dy, color)
                            }
                        }
                    }
                }
            }
            cursorX += (4 * scale) + (1 * scale)
        }
    }

    private fun drawSpeechBubble(text: String, fillCol: Int, outlineCol: Int, textCol: Int) {
        val upper = text.uppercase(Locale.US)
        val textWidth = upper.length * 5 - 1
        val bubbleW = (textWidth + 10).coerceIn(40, 164)
        val bubbleH = 14
        val bubbleX1 = ((gridWidth - bubbleW) / 2).coerceIn(8, gridWidth - bubbleW - 8)
        val bubbleY1 = (69 + companionBounceY).coerceAtLeast(64)
        val bubbleX2 = bubbleX1 + bubbleW
        val bubbleY2 = bubbleY1 + bubbleH

        // Shadow
        val shadowColor = Color.argb(70, 0, 0, 0)
        for (sy in (bubbleY1 + 1)..(bubbleY2 + 1)) {
            for (sx in (bubbleX1 + 1)..(bubbleX2 + 1)) {
                putPixel(sx, sy, shadowColor)
            }
        }

        // Box Fill
        for (by in bubbleY1..bubbleY2) {
            for (bx in bubbleX1..bubbleX2) {
                putPixel(bx, by, fillCol)
            }
        }

        // Outline
        for (bx in bubbleX1..bubbleX2) {
            putPixel(bx, bubbleY1, outlineCol)
            putPixel(bx, bubbleY2, outlineCol)
        }
        for (by in bubbleY1..bubbleY2) {
            putPixel(bubbleX1, by, outlineCol)
            putPixel(bubbleX2, by, outlineCol)
        }

        // Pointer Tail pointing down to companion's head (at X=90, Y=bubbleY2+1..bubbleY2+3)
        val tailX = (gridWidth / 2) + 2
        for (ty in (bubbleY2 + 1)..(bubbleY2 + 3)) {
            val halfW = (bubbleY2 + 4 - ty)
            for (tx in (tailX - halfW)..(tailX + halfW)) {
                putPixel(tx, ty, fillCol)
            }
            putPixel(tailX - halfW, ty, outlineCol)
            putPixel(tailX + halfW, ty, outlineCol)
        }
        putPixel(tailX, bubbleY2 + 4, outlineCol)

        // Centered Text
        val textX = bubbleX1 + (bubbleW - textWidth) / 2
        val textY = bubbleY1 + 4
        drawCleanText(upper, textX, textY, textCol)
    }

    private fun renderVirtualGrid() {
        // Theme Colors
        val bgCol: Int
        val inkCol: Int
        val slateCol: Int
        val fillCol: Int
        val peachCol: Int
        val whiteCol = Color.rgb(255, 255, 255)
        val colMenst: Int
        val colFoll: Int
        val colOvul: Int
        val colLuteal: Int
        val stepAccent: Int

        when (currentTheme) {
            Theme.DARK -> {
                bgCol = Color.rgb(12, 14, 20)
                inkCol = Color.rgb(235, 240, 248)
                slateCol = Color.rgb(130, 142, 168)
                fillCol = Color.rgb(24, 28, 40)
                peachCol = Color.rgb(255, 195, 175)
                colMenst = Color.rgb(255, 83, 112)
                colFoll = Color.rgb(140, 230, 160)
                colOvul = Color.rgb(255, 205, 85)
                colLuteal = Color.rgb(180, 155, 245)
                stepAccent = Color.rgb(90, 200, 250)
            }
            Theme.SAKURA -> {
                bgCol = Color.rgb(252, 246, 248)
                inkCol = Color.rgb(45, 30, 58)
                slateCol = Color.rgb(155, 140, 175)
                fillCol = Color.rgb(248, 238, 248)
                peachCol = Color.rgb(245, 185, 175)
                colMenst = Color.rgb(225, 80, 115)
                colFoll = Color.rgb(245, 155, 180)
                colOvul = Color.rgb(245, 180, 55)
                colLuteal = Color.rgb(155, 135, 195)
                stepAccent = Color.rgb(135, 110, 205)
            }
            Theme.MATCHA -> {
                bgCol = Color.rgb(244, 248, 242)
                inkCol = Color.rgb(24, 46, 36)
                slateCol = Color.rgb(130, 165, 150)
                fillCol = Color.rgb(230, 242, 234)
                peachCol = Color.rgb(242, 195, 175)
                colMenst = Color.rgb(215, 80, 95)
                colFoll = Color.rgb(150, 205, 170)
                colOvul = Color.rgb(235, 175, 55)
                colLuteal = Color.rgb(115, 165, 145)
                stepAccent = Color.rgb(65, 160, 115)
            }
            Theme.PEACH -> {
                bgCol = Color.rgb(252, 246, 238)
                inkCol = Color.rgb(40, 30, 48)
                slateCol = Color.rgb(135, 145, 168)
                fillCol = Color.rgb(246, 238, 232)
                peachCol = Color.rgb(245, 180, 150)
                colMenst = Color.rgb(225, 80, 105)
                colFoll = Color.rgb(245, 160, 140)
                colOvul = Color.rgb(240, 165, 45)
                colLuteal = Color.rgb(130, 140, 175)
                stepAccent = Color.rgb(70, 160, 215)
            }
        }

        val cardFillCol = if (currentTheme == Theme.DARK) Color.rgb(20, 24, 34) else whiteCol
        val cardOutlineCol = if (currentTheme == Theme.DARK) Color.rgb(48, 56, 78) else inkCol

        // Fill Clean Canvas Background
        pixelArray.fill(bgCol)

        // (Native Android Status Bar is at top: NO wallpaper status bar duplication!)

        // 1. BIOLOGICALLY ACCURATE 28-DAY CYCLE CARD (Y: 18..68)
        drawRoundedCard(14, 18, 166, 68, fillCol = cardFillCol, outlineCol = cardOutlineCol, shadowOffset = 2)

        val curPhaseStr: String
        val curPhaseCol: Int
        when {
            cycleDay <= 5 -> {
                curPhaseStr = "MENSTRUAL"
                curPhaseCol = colMenst
            }
            cycleDay <= 13 -> {
                curPhaseStr = "FOLLICULAR"
                curPhaseCol = colFoll
            }
            cycleDay <= 16 -> {
                curPhaseStr = "OVULATION"
                curPhaseCol = colOvul
            }
            else -> {
                curPhaseStr = "LUTEAL"
                curPhaseCol = colLuteal
            }
        }

        // Row 1: Left status & Right countdown
        drawCleanText("DAY $cycleDay • $curPhaseStr", 22, 25, curPhaseCol)
        val daysLeft = (cycleLength - cycleDay).coerceAtLeast(0)
        drawCleanText("${daysLeft}D LEFT", 126, 25, slateCol)

        // 28 Biological Pips: 4 Phase Groups
        val pipY = 39
        val phaseGroups = listOf(
            PhaseGroup(1, 5, colMenst, 22),
            PhaseGroup(6, 13, colFoll, 45),
            PhaseGroup(14, 16, colOvul, 80),
            PhaseGroup(17, 28, colLuteal, 95)
        )

        for (group in phaseGroups) {
            val count = group.endDay - group.startDay + 1
            for (idx in 0 until count) {
                val d = group.startDay + idx
                val px = group.groupX + idx * 4
                val isActive = (d == cycleDay)
                val isPast = (d < cycleDay)

                if (isActive) {
                    for (dy in -2..6) {
                        for (dx in 0..3) {
                            putPixel(px + dx, pipY + dy, inkCol)
                        }
                    }
                    for (dy in -1..5) {
                        for (dx in 1..2) {
                            putPixel(px + dx, pipY + dy, group.color)
                        }
                    }
                    putPixel(px + 1, pipY + 2, whiteCol)
                    putPixel(px + 1, pipY - 3, inkCol)
                } else if (isPast) {
                    for (dy in 0..4) {
                        for (dx in 0..2) {
                            putPixel(px + dx, pipY + dy, group.color)
                        }
                    }
                } else {
                    for (dy in 0..4) {
                        for (dx in 0..2) {
                            if (dy == 0 || dy == 4 || dx == 0 || dx == 2) {
                                putPixel(px + dx, pipY + dy, slateCol)
                            } else {
                                putPixel(px + dx, pipY + dy, fillCol)
                            }
                        }
                    }
                }
            }
        }

        // Phase Labels placed accurately below their groups
        drawCleanText("MENST", 20, 52, colMenst)
        drawCleanText("FOLL", 48, 52, slateCol)
        drawCleanText("OVUL*", 78, 52, colOvul)
        drawCleanText("LUTEAL", 108, 52, slateCol)

        // 2. HERO CENTERPIECE: SCALED-UP COMPANION GIRL (1.25x scale: 125 x 112, Y: 74..186)
        val artInk = if (currentTheme == Theme.DARK) Color.rgb(220, 226, 238) else inkCol
        val artFill = if (currentTheme == Theme.DARK) Color.rgb(24, 28, 40) else fillCol
        val artWhite = if (currentTheme == Theme.DARK) Color.rgb(36, 44, 62) else whiteCol
        val artTheme = arrayOf(bgCol, artInk, slateCol, artFill, peachCol, artWhite)
        val targetArtW = 125
        val targetArtH = 112
        val artOx = (gridWidth - targetArtW) / 2 // Centered at X=27
        val artOy = 74 + companionBounceY
        val raw = baseArtIndices

        for (cy in 0 until targetArtH) {
            val origY = (cy * 90) / targetArtH
            val ty = artOy + cy
            for (cx in 0 until targetArtW) {
                val origX = (cx * 100) / targetArtW
                val tx = artOx + cx

                val idx = origY * 100 + origX
                val valCol = if (idx in raw.indices) raw[idx].toInt() and 0xFF else 0

                if (tx in 0 until gridWidth && ty in 0 until gridHeight) {
                    if (origY in 59..62 && origX in 21..24) {
                        putPixel(tx, ty, curPhaseCol)
                    } else if (origY in 65..68 && origX in 21..24) {
                        putPixel(tx, ty, stepAccent)
                    } else if (valCol != 0) {
                        // Eye blinking animation
                        if (isBlinking && origY in 44..45 && (origX in 52..54 || origX in 60..62)) {
                            putPixel(tx, ty, peachCol) // closed eyelid
                        } else {
                            val c = if (valCol < artTheme.size) artTheme[valCol] else whiteCol
                            putPixel(tx, ty, c)
                        }
                    }
                }
            }
        }

        // 3. HARDWARE STEP COUNTER WIDGET (Y: 192..246)
        drawRoundedCard(14, 192, 166, 246, fillCol = cardFillCol, outlineCol = cardOutlineCol, shadowOffset = 2)

        // Sneaker Icon
        val sx = 22
        val sy = 200
        val sneakerPts = arrayOf(
            Pair(sx, sy + 2), Pair(sx + 1, sy + 1), Pair(sx + 2, sy), Pair(sx + 3, sy),
            Pair(sx + 4, sy + 1), Pair(sx + 5, sy + 2), Pair(sx + 5, sy + 3),
            Pair(sx + 4, sy + 4), Pair(sx + 3, sy + 4), Pair(sx + 2, sy + 4),
            Pair(sx + 1, sy + 4), Pair(sx, sy + 4)
        )
        for (pt in sneakerPts) {
            putPixel(pt.first, pt.second, stepAccent)
        }

        val stepPct = ((stepsCount / stepGoal.toFloat()) * 100).toInt().coerceIn(0, 100)
        drawCleanText("$stepsCount STEPS", 32, 199, inkCol)
        drawCleanText("$stepPct%", 136, 199, stepAccent)

        // Progress Bar (X: 22..158, Y: 210..217)
        val barX1 = 22
        val barY1 = 210
        val barX2 = 158
        val barY2 = 217
        for (x in barX1..barX2) {
            putPixel(x, barY1, inkCol)
            putPixel(x, barY2, inkCol)
        }
        for (y in barY1..barY2) {
            putPixel(barX1, y, inkCol)
            putPixel(barX2, y, inkCol)
        }
        for (y in (barY1 + 1) until barY2) {
            for (x in (barX1 + 1) until barX2) {
                putPixel(x, y, fillCol)
            }
        }
        val fillLen = (((barX2 - barX1 - 1) * (stepPct / 100f))).toInt()
        for (y in (barY1 + 1) until barY2) {
            for (x in (barX1 + 1)..(barX1 + 1 + fillLen).coerceAtMost(barX2 - 1)) {
                putPixel(x, y, stepAccent)
            }
        }

        // Subtext (Zero collision: Left = KM / KCAL, Right = GOAL)
        val km = String.format(Locale.US, "%.1f", stepsCount * 0.00075f)
        val kcal = (stepsCount * 0.035f).toInt()
        drawCleanText("$km KM • $kcal KCAL", 22, 224, slateCol)
        drawCleanText("GOAL ${stepGoal / 1000}K", 122, 224, inkCol)

        // 4. DAILY WELLNESS & WATER CHECKLIST (Y: 252..316)
        drawRoundedCard(14, 252, 166, 316, fillCol = cardFillCol, outlineCol = cardOutlineCol, shadowOffset = 2)

        val h1Text = habit1Title.trim().take(12).uppercase(Locale.US)
        val h2Base = habit2Title.trim().take(10).uppercase(Locale.US)
        val h2Text = if (h2Base.contains("WATER") || h2Base.contains("HYDRAT")) "$h2Base $waterGlasses/8" else h2Base
        val h3Text = habit3Title.trim().take(12).uppercase(Locale.US)

        val habits = listOf(
            HabitItem(h1Text, if (habit1Done) "[X]" else "[ ]", curPhaseCol, booleanArrayOf(true, true, true, true, true, true, habit1Done)),
            HabitItem(h2Text, if (habit2Done) "[X]" else "[ ]", Color.rgb(65, 160, 215), booleanArrayOf(true, true, true, true, true, true, habit2Done)),
            HabitItem(h3Text, if (habit3Done) "[X]" else "[ ]", stepAccent, booleanArrayOf(true, true, false, true, true, true, habit3Done))
        )

        for (i in habits.indices) {
            val item = habits[i]
            val hy = 262 + i * 19
            drawCleanText(item.check, 22, hy, item.accentCol)
            drawCleanText(item.name, 38, hy, inkCol)
            // 7 streak dots on right side (X: 126..158)
            for (sIdx in item.streak.indices) {
                val dx = 126 + sIdx * 5
                val col = if (item.streak[sIdx]) item.accentCol else slateCol
                putPixel(dx, hy + 2, col)
                putPixel(dx + 1, hy + 2, col)
                putPixel(dx, hy + 3, col)
                putPixel(dx + 1, hy + 3, col)
            }
        }

        // 5. Interactive Comic Speech Bubble
        speechBubbleText?.let { msg ->
            val bubbleFill = if (currentTheme == Theme.DARK) Color.rgb(32, 38, 52) else whiteCol
            val bubbleOutline = if (currentTheme == Theme.DARK) Color.rgb(100, 120, 165) else inkCol
            val bubbleTextCol = if (currentTheme == Theme.DARK) Color.rgb(255, 255, 255) else inkCol
            drawSpeechBubble(msg, fillCol = bubbleFill, outlineCol = bubbleOutline, textCol = bubbleTextCol)
        }

        // (Y: 317..400 is dedicated CLEAR ZONE for Android home app dock icons!)

        // Write array directly into virtual bitmap for 0.0% CPU overhead
        virtualBitmap.setPixels(pixelArray, 0, gridWidth, 0, 0, gridWidth, gridHeight)
    }

    private data class PhaseGroup(val startDay: Int, val endDay: Int, val color: Int, val groupX: Int)
    private data class HabitItem(val name: String, val check: String, val accentCol: Int, val streak: BooleanArray)

    companion object {
        private const val BASE_ARTWORK_B64 = "eJztmIuOhCAMRVv0/795orz6oFIUkt2Eu5uZ6EgPlxZQAba2tra2tv6tMGot48ADYTaEx1vCwPt/KQOFkwX5wNj1mSEV4hqp4EKgFB/jh4aADhcxYIiiTZ2dc6f3vY/Sl1aup88S8llPzZ4m8U9h1zKmQxpjRdM1h3EAAC9jbNXBJ4aKR6u0d+03aCsaQkrgcobTyINnAlCXIPvqMQCCsSISCw3GvXC99KGWJHl23IeGahucFib4uNrDQcuTJSQzBnzgtR1wRoUTU2XCJ8bIBLHzcQc+z8vFed64eD48RHOq+qjbRxoyqD7GQup8FEbMQ54hBkOv1Zph5QNyRZXP3ED38q0PyN0XhaV8YBheG1ldERCv3SEfHQbUGjMYjnzYDCBzHG2GM6pURqXY/H74JUMeh7oJlQs+M9IKRaLkHFRPXxniOKTxgweGtUFaDKnKqJuFYhgdHPDBlihoMoacIHeiGAJWGCNO+BzCukMAOhg+J9pHc6qQbOXpnQujX2o6H0kVUc/lxyg6910M8XxOHsbKZtVsNsggvSRNMMdqMXCIIY5Jk3Jj8tTQxwgATR+dhrwMOgx+3G7SqFFeBh2Gx8enpw2vjy/vO5w+DvC98bAY/Ngc3sP6YSLjvcYY7540W3uU+dv1kmhg/ysKUvN9/A2t7TvPgbzRm0yaHXJra2tra55+oJ8Esw=="

        private val FONT_4X6 = mapOf(
            '0' to arrayOf("0110", "1001", "1001", "1001", "1001", "0110"),
            '1' to arrayOf("0010", "0110", "0010", "0010", "0010", "0111"),
            '2' to arrayOf("0110", "1001", "0001", "0010", "0100", "1111"),
            '3' to arrayOf("1110", "0001", "0110", "0001", "0001", "1110"),
            '4' to arrayOf("1001", "1001", "1111", "0001", "0001", "0001"),
            '5' to arrayOf("1111", "1000", "1110", "0001", "0001", "1110"),
            '6' to arrayOf("0110", "1000", "1110", "1001", "1001", "0110"),
            '7' to arrayOf("1111", "0001", "0010", "0100", "0100", "0100"),
            '8' to arrayOf("0110", "1001", "0110", "1001", "1001", "0110"),
            '9' to arrayOf("0110", "1001", "1001", "0111", "0001", "0110"),
            ':' to arrayOf("0000", "0110", "0110", "0000", "0110", "0110"),
            '.' to arrayOf("0000", "0000", "0000", "0000", "0110", "0110"),
            ',' to arrayOf("0000", "0000", "0000", "0000", "0110", "0100"),
            '•' to arrayOf("0000", "0000", "0110", "0110", "0000", "0000"),
            '%' to arrayOf("1001", "0010", "0100", "0100", "1001", "0000"),
            '°' to arrayOf("0110", "1001", "0110", "0000", "0000", "0000"),
            '-' to arrayOf("0000", "0000", "1111", "0000", "0000", "0000"),
            '+' to arrayOf("0000", "0010", "0111", "0010", "0000", "0000"),
            '/' to arrayOf("0001", "0010", "0100", "0100", "1000", "0000"),
            '[' to arrayOf("0110", "0100", "0100", "0100", "0100", "0110"),
            ']' to arrayOf("0110", "0010", "0010", "0010", "0010", "0110"),
            'A' to arrayOf("0110", "1001", "1111", "1001", "1001", "1001"),
            'B' to arrayOf("1110", "1001", "1110", "1001", "1001", "1110"),
            'C' to arrayOf("0111", "1000", "1000", "1000", "1000", "0111"),
            'D' to arrayOf("1110", "1001", "1001", "1001", "1001", "1110"),
            'E' to arrayOf("1111", "1000", "1110", "1000", "1000", "1111"),
            'F' to arrayOf("1111", "1000", "1110", "1000", "1000", "1000"),
            'G' to arrayOf("0111", "1000", "1011", "1001", "1001", "0111"),
            'H' to arrayOf("1001", "1001", "1111", "1001", "1001", "1001"),
            'I' to arrayOf("1110", "0100", "0100", "0100", "0100", "1110"),
            'J' to arrayOf("0001", "0001", "0001", "0001", "1001", "0110"),
            'K' to arrayOf("1001", "1010", "1100", "1010", "1001", "1001"),
            'L' to arrayOf("1000", "1000", "1000", "1000", "1000", "1111"),
            'M' to arrayOf("1001", "1111", "1111", "1001", "1001", "1001"),
            'N' to arrayOf("1001", "1101", "1011", "1001", "1001", "1001"),
            'O' to arrayOf("0110", "1001", "1001", "1001", "1001", "0110"),
            'P' to arrayOf("1110", "1001", "1110", "1000", "1000", "1000"),
            'Q' to arrayOf("0110", "1001", "1001", "1001", "0110", "0011"),
            'R' to arrayOf("1110", "1001", "1110", "1010", "1001", "1001"),
            'S' to arrayOf("0111", "1000", "0110", "0001", "0001", "1110"),
            'T' to arrayOf("1111", "0100", "0100", "0100", "0100", "0100"),
            'U' to arrayOf("1001", "1001", "1001", "1001", "1001", "0110"),
            'V' to arrayOf("1001", "1001", "1001", "1001", "0110", "0100"),
            'W' to arrayOf("1001", "1001", "1001", "1111", "1111", "1001"),
            'X' to arrayOf("1001", "1001", "0110", "0110", "1001", "1001"),
            'Y' to arrayOf("1001", "1001", "0110", "0010", "0010", "0010"),
            'Z' to arrayOf("1111", "0001", "0010", "0100", "1000", "1111"),
            '★' to arrayOf("0100", "1111", "0110", "1001", "0000", "0000"),
            '*' to arrayOf("0100", "1111", "0110", "1001", "0000", "0000"),
            ' ' to arrayOf("0000", "0000", "0000", "0000", "0000", "0000")
        )
    }
}
