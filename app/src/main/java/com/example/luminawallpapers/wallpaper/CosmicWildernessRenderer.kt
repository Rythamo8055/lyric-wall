package com.example.luminawallpapers.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.luminawallpapers.util.DailyProductivityStats
import com.example.luminawallpapers.util.UsageStatsHelper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class LY02ProductivityMode(val title: String) {
    FULL_TELEMETRY("Full Telemetry"),
    DAILY_GOAL("Focus Goal"),
    MINIMAL_TIME("Glance Clock")
}

class CosmicWildernessRenderer(private val context: Context? = null) {

    // Canvas coordinate reference: 1080 x 2400
    private val refWidth = 1080f
    private val refHeight = 2400f

    // Customization & Telemetry Settings
    var showProductivityHud = true
    var showScreenTime = true
    var showUnlocks = true
    var showTopApps = true
    var starSpeedScale = 1.0f
    var rippleSpeedScale = 1.0f
    var productivityMode = LY02ProductivityMode.FULL_TELEMETRY

    // Interactive States
    var isLanternLit = true
    private var cosmicPulseAlpha = 0.0f

    // Live Productivity Telemetry Cache
    var productivityStats: DailyProductivityStats = DailyProductivityStats(
        totalScreenTimeMillis = 3 * 3600_000 + 48 * 60_000,
        formattedScreenTime = "03h 48m",
        unlockCount = 42,
        topApps = listOf(
            com.example.luminawallpapers.util.AppUsageInfo("com.google.android.youtube", "YouTube", 84 * 60_000, "1h 24m", 0.70f),
            com.example.luminawallpapers.util.AppUsageInfo("com.android.chrome", "Chrome", 58 * 60_000, "58m", 0.48f),
            com.example.luminawallpapers.util.AppUsageInfo("com.whatsapp", "WhatsApp", 36 * 60_000, "36m", 0.30f)
        ),
        goalMinutes = 240,
        remainingMinutes = 12
    )

    // Pre-allocated Paints for 60/120 FPS zero-allocation rendering
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private val primaryStrokePaint = Paint().apply {
        color = Color.rgb(240, 244, 252)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val secondaryStrokePaint = Paint().apply {
        color = Color.argb(220, 185, 195, 215)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val faintStrokePaint = Paint().apply {
        color = Color.argb(160, 120, 135, 165)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val maskFillPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Typography Paints (Scaled up for bold, crisp glanceability)
    private var pixelTypeface: Typeface? = null

    private val hudTitlePaint = Paint().apply {
        color = Color.argb(210, 160, 175, 205)
        textSize = 42f
        isAntiAlias = true
    }

    private val hudTimePaint = Paint().apply {
        color = Color.rgb(255, 255, 255)
        textSize = 125f
        isAntiAlias = true
    }

    private val hudAppTextPaint = Paint().apply {
        color = Color.rgb(245, 248, 255)
        textSize = 46f
        isAntiAlias = true
    }

    private val hudSubTextPaint = Paint().apply {
        color = Color.argb(230, 190, 202, 225)
        textSize = 40f
        isAntiAlias = true
    }

    private val hudBadgePaint = Paint().apply {
        color = Color.argb(220, 170, 185, 210)
        textSize = 34f
        isAntiAlias = true
    }

    private val barTrackPaint = Paint().apply {
        color = Color.argb(200, 36, 44, 62)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val barFillPaint = Paint().apply {
        color = Color.rgb(245, 248, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Reusable Path and RectF
    private val path = Path()
    private val tempRect = RectF()

    // Starfield Data
    data class Star(
        val x: Float,
        val y: Float,
        val type: Int, // 0: Dot, 1: Cross (+), 2: Sparkle (✦)
        val baseSize: Float,
        var phase: Float,
        val speed: Float,
        val baseAlpha: Float
    )

    private val stars = mutableListOf<Star>()

    // Interactive Shooting Stars
    data class ShootingStar(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        val length: Float,
        var alpha: Float,
        val decay: Float
    )

    private val shootingStars = mutableListOf<ShootingStar>()

    // Interactive Campfire Embers
    data class CampfireEmber(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        val decay: Float,
        val size: Float
    )

    private val embers = mutableListOf<CampfireEmber>()

    // Interactive Expanding Lake Ripple Waves
    data class InteractiveRipple(
        val x: Float,
        val y: Float,
        var radiusX: Float,
        var radiusY: Float,
        var alpha: Float,
        val maxRadius: Float
    )

    private val ripples = mutableListOf<InteractiveRipple>()

    init {
        initTypeface()
        initStarfield()
        refreshProductivityStats()
    }

    private fun initTypeface() {
        if (context != null) {
            try {
                pixelTypeface = Typeface.createFromAsset(context.assets, "fonts/Jersey15-Regular.ttf")
            } catch (e: Exception) {
                try {
                    pixelTypeface = Typeface.createFromAsset(context.assets, "fonts/PixelifySans.ttf")
                } catch (e2: Exception) {
                    pixelTypeface = Typeface.MONOSPACE
                }
            }
        } else {
            pixelTypeface = Typeface.MONOSPACE
        }

        pixelTypeface?.let {
            hudTitlePaint.typeface = it
            hudTimePaint.typeface = it
            hudAppTextPaint.typeface = it
            hudSubTextPaint.typeface = it
            hudBadgePaint.typeface = it
        }
    }

    private fun initStarfield() {
        stars.clear()
        val rng = Random(42)

        val crossStars = listOf(
            Triple(0.12f * refWidth, 0.61f * refHeight, 14f),
            Triple(0.34f * refWidth, 0.56f * refHeight, 11f),
            Triple(0.67f * refWidth, 0.54f * refHeight, 12f),
            Triple(0.75f * refWidth, 0.63f * refHeight, 11f),
            Triple(0.95f * refWidth, 0.64f * refHeight, 10f),
            Triple(0.86f * refWidth, 0.21f * refHeight, 13f),
            Triple(0.41f * refWidth, 0.35f * refHeight, 12f),
            Triple(0.15f * refWidth, 0.23f * refHeight, 9f),
            Triple(0.83f * refWidth, 0.46f * refHeight, 9f),
            Triple(0.50f * refWidth, 0.36f * refHeight, 9f)
        )

        for ((sx, sy, sz) in crossStars) {
            stars.add(Star(sx, sy, 1, sz, rng.nextFloat() * 6.28f, 1.2f + rng.nextFloat() * 1.5f, 0.95f))
        }

        for (i in 0 until 45) {
            val sx = rng.nextFloat() * refWidth
            val sy = (0.08f + rng.nextFloat() * 0.58f) * refHeight
            val distSat = kotlin.math.hypot(sx - 0.26f * refWidth, sy - 0.64f * refHeight)
            val distMoon = kotlin.math.hypot(sx - 0.63f * refWidth, sy - 0.59f * refHeight)
            if (distSat > 100f && distMoon > 80f) {
                val sz = 1.6f + rng.nextFloat() * 2.4f
                val spd = 1.0f + rng.nextFloat() * 2.0f
                val a = 0.6f + rng.nextFloat() * 0.4f
                stars.add(Star(sx, sy, 0, sz, rng.nextFloat() * 6.28f, spd, a))
            }
        }
    }

    fun refreshProductivityStats() {
        if (context != null) {
            productivityStats = UsageStatsHelper.getDailyProductivityStats(context)
        }
    }

    fun onTouch(x: Float, y: Float, screenW: Int, screenH: Int): String {
        val scale = screenW / refWidth
        val canvasX = x / scale
        val canvasY = y / scale
        val rng = Random.Default

        // 1. Touch Zone: HUD Area (Y: 200..750) -> Cycle Productivity View Mode!
        if (showProductivityHud && canvasY in 200f..750f) {
            productivityMode = when (productivityMode) {
                LY02ProductivityMode.FULL_TELEMETRY -> LY02ProductivityMode.DAILY_GOAL
                LY02ProductivityMode.DAILY_GOAL -> LY02ProductivityMode.MINIMAL_TIME
                LY02ProductivityMode.MINIMAL_TIME -> LY02ProductivityMode.FULL_TELEMETRY
            }
            refreshProductivityStats()
            return "Switched to ${productivityMode.title}"
        }

        // 2. Touch Zone: Campsite & Tent (X: 380..700, Y: 1800..2080) -> Toggle Lantern & Burst Embers
        val tentCx = 0.50f * refWidth
        val tentPeakY = 0.79f * refHeight
        val distToTent = kotlin.math.hypot(canvasX - tentCx, canvasY - (tentPeakY + 60f))
        if (distToTent < 140f) {
            isLanternLit = !isLanternLit
            // Spawn rising ember particles
            for (i in 0 until 18) {
                embers.add(
                    CampfireEmber(
                        x = tentCx + rng.nextFloat() * 60f - 30f,
                        y = 0.84f * refHeight - rng.nextFloat() * 20f,
                        vx = (rng.nextFloat() - 0.5f) * 1.8f,
                        vy = -(1.5f + rng.nextFloat() * 3.0f),
                        alpha = 1.0f,
                        decay = 0.015f + rng.nextFloat() * 0.02f,
                        size = 2.0f + rng.nextFloat() * 2.5f
                    )
                )
            }
            return if (isLanternLit) "Lantern Lit" else "Lantern Dimmed"
        }

        // 3. Touch Zone: Lake Surface (Y: 2000..2300) -> Propagating Water Waves
        if (canvasY in (0.84f * refHeight)..(0.95f * refHeight)) {
            ripples.add(
                InteractiveRipple(
                    x = canvasX,
                    y = canvasY,
                    radiusX = 15f,
                    radiusY = 4f,
                    alpha = 0.9f,
                    maxRadius = 180f + rng.nextFloat() * 80f
                )
            )
            return "Lake Ripple"
        }

        // 4. Touch Zone: Saturn & Moon (Y: 1300..1750) -> Cosmic Pulse & Meteor Shower
        val satX = 0.26f * refWidth
        val satY = 0.64f * refHeight
        val moonX = 0.63f * refWidth
        val moonY = 0.595f * refHeight
        if (kotlin.math.hypot(canvasX - satX, canvasY - satY) < 140f || kotlin.math.hypot(canvasX - moonX, canvasY - moonY) < 120f) {
            cosmicPulseAlpha = 1.0f
            // Launch meteor burst
            for (m in 0 until 3) {
                val startX = 200f + rng.nextFloat() * (refWidth - 400f)
                val startY = 250f + rng.nextFloat() * 400f
                val angle = Math.toRadians((32.0 + rng.nextDouble() * 20.0))
                val speed = 28f + rng.nextFloat() * 15f
                shootingStars.add(
                    ShootingStar(
                        x = startX,
                        y = startY,
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        length = 130f + rng.nextFloat() * 90f,
                        alpha = 1.0f,
                        decay = 0.035f + rng.nextFloat() * 0.02f
                    )
                )
            }
            return "Cosmic Meteor Burst"
        }

        // 5. Open Night Sky -> Single Shooting Star
        val angle = Math.toRadians((35.0 + rng.nextDouble() * 20.0))
        val speed = 26f + rng.nextFloat() * 15f
        shootingStars.add(
            ShootingStar(
                x = canvasX.coerceIn(100f, refWidth - 100f),
                y = (canvasY - 120f).coerceIn(150f, refHeight * 0.55f),
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                length = 120f + rng.nextFloat() * 80f,
                alpha = 1.0f,
                decay = 0.04f + rng.nextFloat() * 0.03f
            )
        )
        return "Shooting Star"
    }

    fun update(deltaSeconds: Float) {
        // Star twinkling
        for (star in stars) {
            star.phase += deltaSeconds * star.speed * starSpeedScale
            if (star.phase > 6.28318f) star.phase -= 6.28318f
        }

        // Cosmic pulse decay
        if (cosmicPulseAlpha > 0f) {
            cosmicPulseAlpha = (cosmicPulseAlpha - deltaSeconds * 1.5f).coerceAtLeast(0f)
        }

        // Shooting stars update
        val starIter = shootingStars.iterator()
        while (starIter.hasNext()) {
            val s = starIter.next()
            s.x += s.vx * deltaSeconds * 60f
            s.y += s.vy * deltaSeconds * 60f
            s.alpha -= s.decay * deltaSeconds * 60f
            if (s.alpha <= 0f || s.x > refWidth || s.y > refHeight) {
                starIter.remove()
            }
        }

        // Campfire embers update
        val emberIter = embers.iterator()
        while (emberIter.hasNext()) {
            val e = emberIter.next()
            e.x += (e.vx + sin(e.y * 0.05f) * 0.8f) * deltaSeconds * 60f
            e.y += e.vy * deltaSeconds * 60f
            e.alpha -= e.decay * deltaSeconds * 60f
            if (e.alpha <= 0f || e.y < 0.65f * refHeight) {
                emberIter.remove()
            }
        }

        // Interactive lake ripples update
        val rippleIter = ripples.iterator()
        while (rippleIter.hasNext()) {
            val r = rippleIter.next()
            r.radiusX += 55f * deltaSeconds
            r.radiusY += 14f * deltaSeconds
            r.alpha -= 0.65f * deltaSeconds
            if (r.alpha <= 0f || r.radiusX >= r.maxRadius) {
                rippleIter.remove()
            }
        }
    }

    fun draw(canvas: Canvas, screenW: Int, screenH: Int, currentTimeMillis: Long) {
        canvas.drawColor(Color.BLACK)

        canvas.save()
        val scaleX = screenW / refWidth
        val scaleY = screenH / refHeight
        canvas.scale(scaleX, scaleY)

        val timeSec = currentTimeMillis / 1000f

        // 1. Draw Starfield
        drawStarfield(canvas)

        // 2. Draw Shooting Stars
        drawShootingStars(canvas)

        // 3. Draw Celestial Bodies (Saturn, Moon, Cratered Orb)
        drawCelestialBodies(canvas, timeSec)

        // 4. Draw Mountain Range & Organic Hatching
        drawMountainRange(canvas)

        // 5. Draw Cloud Framing Silhouettes
        drawClouds(canvas)

        // 6. Draw Lake Water Ripples (Natural + Interactive)
        drawLakeRipples(canvas, timeSec)

        // 7. Draw Foreground Campsite (A-Frame Tent, Lantern, Embers)
        drawCampsite(canvas, timeSec)

        // 8. Draw Scaled-Up Glanceable Curved Pixel Productivity HUD
        if (showProductivityHud) {
            drawProductivityHud(canvas)
        }

        canvas.restore()
    }

    private fun drawStarfield(canvas: Canvas) {
        for (star in stars) {
            val twinkle = (sin(star.phase) * 0.35f + 0.65f) * star.baseAlpha
            val alphaInt = (twinkle * 255).toInt().coerceIn(0, 255)

            when (star.type) {
                1 -> {
                    primaryStrokePaint.alpha = alphaInt
                    val sz = star.baseSize
                    canvas.drawLine(star.x - sz, star.y, star.x + sz, star.y, primaryStrokePaint)
                    canvas.drawLine(star.x, star.y - sz, star.x, star.y + sz, primaryStrokePaint)

                    glowPaint.color = Color.argb(alphaInt, 245, 248, 255)
                    canvas.drawCircle(star.x, star.y, 2.5f, glowPaint)
                }
                else -> {
                    glowPaint.color = Color.argb(alphaInt, 235, 242, 255)
                    canvas.drawCircle(star.x, star.y, star.baseSize, glowPaint)
                }
            }
        }
    }

    private fun drawShootingStars(canvas: Canvas) {
        for (s in shootingStars) {
            val a = (s.alpha * 255).toInt().coerceIn(0, 255)
            primaryStrokePaint.alpha = a
            primaryStrokePaint.strokeWidth = 2.8f
            val tailX = s.x - s.vx * 2.8f
            val tailY = s.y - s.vy * 2.8f
            canvas.drawLine(tailX, tailY, s.x, s.y, primaryStrokePaint)

            glowPaint.color = Color.argb(a, 255, 255, 255)
            canvas.drawCircle(s.x, s.y, 3.8f, glowPaint)
        }
    }

    private fun drawCelestialBodies(canvas: Canvas, timeSec: Float) {
        primaryStrokePaint.alpha = 255
        secondaryStrokePaint.alpha = 220
        faintStrokePaint.alpha = 160

        // A. SATURN / RINGED PLANET (Left Sky)
        val satX = 0.26f * refWidth
        val satY = 0.64f * refHeight
        val satR = 0.075f * refWidth
        val ringRx = satR * 2.2f
        val ringRy = satR * 0.58f
        val innerRx = satR * 1.45f
        val innerRy = satR * 0.38f

        // Cosmic pulse wave if active
        if (cosmicPulseAlpha > 0f) {
            val pulseR = satR + (1.0f - cosmicPulseAlpha) * 80f
            glowPaint.color = Color.argb((cosmicPulseAlpha * 80).toInt(), 220, 235, 255)
            canvas.drawCircle(satX, satY, pulseR, glowPaint)
        }

        canvas.save()
        canvas.rotate(-26f, satX, satY)

        // 1. Back half of rings (angles 180..360)
        tempRect.set(satX - ringRx, satY - ringRy, satX + ringRx, satY + ringRy)
        canvas.drawArc(tempRect, 180f, 180f, false, primaryStrokePaint)
        tempRect.set(satX - innerRx, satY - innerRy, satX + innerRx, satY + innerRy)
        canvas.drawArc(tempRect, 180f, 180f, false, secondaryStrokePaint)

        // 2. Planet body
        canvas.drawCircle(satX, satY, satR, maskFillPaint)
        canvas.drawCircle(satX, satY, satR, primaryStrokePaint)

        // 3. Planet interior contour sketch lines
        for (i in 1..7) {
            val yOff = satR * (0.1f + i * 0.11f)
            val chordW = sqrt((satR * satR - yOff * yOff).coerceAtLeast(0f))
            canvas.drawLine(satX - chordW * 0.85f, satY + yOff, satX + chordW * 0.85f, satY + yOff, faintStrokePaint)
        }

        // 4. Front half of rings (angles 0..180)
        tempRect.set(satX - ringRx, satY - ringRy, satX + ringRx, satY + ringRy)
        canvas.drawArc(tempRect, 0f, 180f, false, primaryStrokePaint)
        tempRect.set(satX - innerRx, satY - innerRy, satX + innerRx, satY + innerRy)
        canvas.drawArc(tempRect, 0f, 180f, false, secondaryStrokePaint)

        // 5. Ring hatch strokes
        for (deg in 15..165 step 14) {
            val rad = Math.toRadians(deg.toDouble())
            val xOut = (satX + ringRx * cos(rad)).toFloat()
            val yOut = (satY + ringRy * sin(rad)).toFloat()
            val xIn = (satX + innerRx * cos(rad)).toFloat()
            val yIn = (satY + innerRy * sin(rad)).toFloat()
            canvas.drawLine(xIn, yIn, xOut, yOut, faintStrokePaint)
        }

        canvas.restore()

        // B. CRESCENT MOON (Mid Sky)
        val moonX = 0.63f * refWidth
        val moonY = 0.595f * refHeight
        val moonR = 0.045f * refWidth

        path.reset()
        tempRect.set(moonX - moonR, moonY - moonR, moonX + moonR, moonY + moonR)
        path.arcTo(tempRect, 270f, 180f, true)
        val offX = moonR * 0.55f
        val offY = moonR * -0.22f
        val inR = moonR * 0.90f
        tempRect.set(moonX - inR - offX, moonY - inR + offY, moonX + inR - offX, moonY + inR + offY)
        path.arcTo(tempRect, 90f, -180f, false)
        path.close()

        glowPaint.color = Color.rgb(240, 245, 255)
        canvas.drawPath(path, glowPaint)

        // C. CRATERED MOON / PLANET (Right Sky)
        val craterX = 0.685f * refWidth
        val craterY = 0.69f * refHeight
        val craterR = 0.05f * refWidth

        canvas.drawCircle(craterX, craterY, craterR, maskFillPaint)
        canvas.drawCircle(craterX, craterY, craterR, primaryStrokePaint)

        val craters = listOf(
            Triple(craterX - 0.015f * refWidth, craterY - 0.015f * refHeight, 0.014f * refWidth),
            Triple(craterX + 0.018f * refWidth, craterY - 0.012f * refHeight, 0.011f * refWidth),
            Triple(craterX - 0.005f * refWidth, craterY + 0.018f * refHeight, 0.016f * refWidth),
            Triple(craterX + 0.022f * refWidth, craterY + 0.014f * refHeight, 0.010f * refWidth)
        )
        for ((cx, cy, cr) in craters) {
            tempRect.set(cx - cr, cy - cr * 0.7f, cx + cr, cy + cr * 0.7f)
            canvas.drawOval(tempRect, secondaryStrokePaint)
            tempRect.set(cx - cr * 0.7f, cy - cr * 0.5f, cx + cr * 0.7f, cy + cr * 0.5f)
            canvas.drawArc(tempRect, 120f, 160f, false, faintStrokePaint)
        }
    }

    private fun drawMountainRange(canvas: Canvas) {
        val pLeftPeakX = 0.31f * refWidth
        val pLeftPeakY = 0.685f * refHeight
        val pCenterPeakX = 0.49f * refWidth
        val pCenterPeakY = 0.655f * refHeight
        val pRightPeakX = 0.71f * refWidth
        val pRightPeakY = 0.695f * refHeight

        path.reset()
        path.moveTo(0f, refHeight)
        path.lineTo(0f, 0.74f * refHeight)
        path.cubicTo(0.12f * refWidth, 0.73f * refHeight, 0.22f * refWidth, 0.70f * refHeight, pLeftPeakX, pLeftPeakY)
        path.cubicTo(0.35f * refWidth, 0.73f * refHeight, 0.42f * refWidth, 0.70f * refHeight, pCenterPeakX, pCenterPeakY)
        path.cubicTo(0.56f * refWidth, 0.73f * refHeight, 0.64f * refWidth, 0.72f * refHeight, pRightPeakX, pRightPeakY)
        path.cubicTo(0.80f * refWidth, 0.73f * refHeight, 0.90f * refWidth, 0.75f * refHeight, refWidth, 0.76f * refHeight)
        path.lineTo(refWidth, refHeight)
        path.close()
        canvas.drawPath(path, maskFillPaint)

        // Mountain Shading (Curved contour line hatching)
        for (i in 0..14) {
            val t = i / 14f
            val startX = pLeftPeakX + t * (0.07f * refWidth)
            val startY = pLeftPeakY + t * (0.055f * refHeight)
            val endX = startX + 22f
            val endY = startY + 38f
            canvas.drawLine(startX, startY, endX, endY, faintStrokePaint)
        }

        for (i in 0..24) {
            val t = i / 24f
            val startX = pCenterPeakX + t * (0.06f * refWidth)
            val startY = pCenterPeakY + t * (0.085f * refHeight)
            val endX = startX + 28f + (sin(t * 3.14f) * 14f)
            val endY = startY + 48f
            canvas.drawLine(startX, startY, endX, endY, secondaryStrokePaint)
        }

        // Mountain Outlines
        path.reset()
        path.moveTo(0f, 0.74f * refHeight)
        path.cubicTo(0.12f * refWidth, 0.73f * refHeight, 0.22f * refWidth, 0.70f * refHeight, pLeftPeakX, pLeftPeakY)
        path.cubicTo(0.35f * refWidth, 0.73f * refHeight, 0.42f * refWidth, 0.70f * refHeight, pCenterPeakX, pCenterPeakY)
        path.cubicTo(0.56f * refWidth, 0.73f * refHeight, 0.64f * refWidth, 0.72f * refHeight, pRightPeakX, pRightPeakY)
        path.cubicTo(0.80f * refWidth, 0.73f * refHeight, 0.90f * refWidth, 0.75f * refHeight, refWidth, 0.76f * refHeight)
        canvas.drawPath(path, primaryStrokePaint)

        // Center Peak Ridge Spine (Organic S-curve)
        path.reset()
        path.moveTo(pCenterPeakX, pCenterPeakY)
        path.cubicTo(0.505f * refWidth, 0.675f * refHeight, 0.495f * refWidth, 0.705f * refHeight, 0.53f * refWidth, 0.73f * refHeight)
        path.cubicTo(0.51f * refWidth, 0.75f * refHeight, 0.58f * refWidth, 0.74f * refHeight, 0.62f * refWidth, 0.735f * refHeight)
        canvas.drawPath(path, primaryStrokePaint)
    }

    private fun drawClouds(canvas: Canvas) {
        path.reset()
        path.moveTo(0f, 0.73f * refHeight)
        path.cubicTo(0.05f * refWidth, 0.72f * refHeight, 0.07f * refWidth, 0.75f * refHeight, 0.13f * refWidth, 0.745f * refHeight)
        path.cubicTo(0.18f * refWidth, 0.77f * refHeight, 0.24f * refWidth, 0.78f * refHeight, 0.26f * refWidth, 0.81f * refHeight)
        path.cubicTo(0.33f * refWidth, 0.80f * refHeight, 0.38f * refWidth, 0.83f * refHeight, 0.45f * refWidth, 0.845f * refHeight)
        canvas.drawPath(path, primaryStrokePaint)

        path.reset()
        path.moveTo(0.55f * refWidth, 0.845f * refHeight)
        path.cubicTo(0.62f * refWidth, 0.83f * refHeight, 0.67f * refWidth, 0.80f * refHeight, 0.74f * refWidth, 0.81f * refHeight)
        path.cubicTo(0.80f * refWidth, 0.78f * refHeight, 0.88f * refWidth, 0.71f * refHeight, 0.97f * refWidth, 0.71f * refHeight)
        path.lineTo(refWidth, 0.74f * refHeight)
        canvas.drawPath(path, primaryStrokePaint)
    }

    private fun drawCampsite(canvas: Canvas, timeSec: Float) {
        val tentCx = 0.50f * refWidth
        val tentPeakY = 0.79f * refHeight
        val tentBaseY = 0.845f * refHeight
        val tentHalfW = 0.085f * refWidth

        // Interactive Lantern / Campfire Glow
        if (isLanternLit) {
            val glowIntensity = (sin(timeSec * 2.8f) * 0.18f + 0.82f)
            glowPaint.color = Color.argb((45 * glowIntensity).toInt(), 255, 230, 180)
            canvas.drawCircle(tentCx, tentBaseY - 15f, 65f, glowPaint)
        }

        // Draw Rising Campfire Embers
        for (e in embers) {
            val a = (e.alpha * 255).toInt().coerceIn(0, 255)
            glowPaint.color = Color.argb(a, 255, 220, 160)
            canvas.drawCircle(e.x, e.y, e.size, glowPaint)
        }

        // Outer A-Frame
        path.reset()
        path.moveTo(tentCx - tentHalfW, tentBaseY)
        path.lineTo(tentCx, tentPeakY)
        path.lineTo(tentCx + tentHalfW, tentBaseY)
        path.close()
        canvas.drawPath(path, maskFillPaint)
        canvas.drawPath(path, primaryStrokePaint)

        // Center ridge line
        canvas.drawLine(tentCx, tentPeakY, tentCx, tentBaseY, primaryStrokePaint)

        // Doorway flaps
        val doorW = 0.038f * refWidth
        val doorTopY = tentPeakY + 0.015f * refHeight
        canvas.drawLine(tentCx - doorW, doorTopY, tentCx - doorW, tentBaseY, primaryStrokePaint)
        canvas.drawLine(tentCx + doorW, doorTopY, tentCx + doorW, tentBaseY, primaryStrokePaint)

        // Guy Lines
        path.reset()
        path.moveTo(tentCx - tentHalfW * 0.85f, tentBaseY - 0.005f * refHeight)
        path.quadTo(tentCx - tentHalfW * 1.1f, tentBaseY + 0.005f * refHeight, tentCx - tentHalfW * 1.35f, tentBaseY + 0.008f * refHeight)
        canvas.drawPath(path, secondaryStrokePaint)

        path.reset()
        path.moveTo(tentCx + tentHalfW * 0.85f, tentBaseY - 0.005f * refHeight)
        path.quadTo(tentCx + tentHalfW * 1.1f, tentBaseY + 0.005f * refHeight, tentCx + tentHalfW * 1.35f, tentBaseY + 0.008f * refHeight)
        canvas.drawPath(path, secondaryStrokePaint)

        // Guy line stakes
        canvas.drawLine(tentCx - tentHalfW * 1.35f, tentBaseY + 0.005f * refHeight, tentCx - tentHalfW * 1.35f, tentBaseY + 0.018f * refHeight, primaryStrokePaint)
        canvas.drawLine(tentCx + tentHalfW * 1.35f, tentBaseY + 0.005f * refHeight, tentCx + tentHalfW * 1.35f, tentBaseY + 0.018f * refHeight, primaryStrokePaint)
    }

    private fun drawLakeRipples(canvas: Canvas, timeSec: Float) {
        val rippleOsc = sin(timeSec * 1.5f * rippleSpeedScale) * 3f

        val ambientRipples = listOf(
            Triple(0.44f * refWidth, 0.56f * refWidth, 0.855f * refHeight),
            Triple(0.38f * refWidth, 0.62f * refWidth, 0.870f * refHeight),
            Triple(0.44f * refWidth, 0.47f * refWidth, 0.878f * refHeight),
            Triple(0.53f * refWidth, 0.56f * refWidth, 0.878f * refHeight),
            Triple(0.30f * refWidth, 0.43f * refWidth, 0.860f * refHeight),
            Triple(0.63f * refWidth, 0.70f * refWidth, 0.835f * refHeight),
            Triple(0.68f * refWidth, 0.82f * refWidth, 0.855f * refHeight),
            Triple(0.15f * refWidth, 0.22f * refWidth, 0.825f * refHeight),
            Triple(0.24f * refWidth, 0.35f * refWidth, 0.840f * refHeight),
            Triple(0.73f * refWidth, 0.79f * refWidth, 0.880f * refHeight),
            Triple(0.44f * refWidth, 0.47f * refWidth, 0.888f * refHeight),
            Triple(0.52f * refWidth, 0.56f * refWidth, 0.888f * refHeight)
        )

        for ((x1, x2, ry) in ambientRipples) {
            canvas.drawLine(x1, ry + rippleOsc, x2, ry + rippleOsc, primaryStrokePaint)
        }

        // Shoreline vertical grass reflection ticks
        canvas.drawLine(0.38f * refWidth, 0.853f * refHeight, 0.38f * refWidth, 0.860f * refHeight, secondaryStrokePaint)
        canvas.drawLine(0.40f * refWidth, 0.855f * refHeight, 0.40f * refWidth, 0.860f * refHeight, secondaryStrokePaint)
        canvas.drawLine(0.76f * refWidth, 0.844f * refHeight, 0.76f * refWidth, 0.850f * refHeight, secondaryStrokePaint)
        canvas.drawLine(0.775f * refWidth, 0.842f * refHeight, 0.775f * refWidth, 0.850f * refHeight, secondaryStrokePaint)

        // Draw Interactive Expanding Ripple Waves
        for (r in ripples) {
            val a = (r.alpha * 255).toInt().coerceIn(0, 255)
            primaryStrokePaint.alpha = a
            tempRect.set(r.x - r.radiusX, r.y - r.radiusY, r.x + r.radiusX, r.y + r.radiusY)
            canvas.drawOval(tempRect, primaryStrokePaint)
        }
        primaryStrokePaint.alpha = 255
    }

    private fun drawProductivityHud(canvas: Canvas) {
        val sx = 85f
        var sy = 240f
        val hudWidth = 910f

        when (productivityMode) {
            LY02ProductivityMode.FULL_TELEMETRY -> {
                // 1. Header: Daily Screen Time (42f) & Unlock Counter (40f)
                if (showScreenTime) {
                    canvas.drawText("DAILY SCREEN TIME", sx, sy, hudTitlePaint)
                }
                if (showUnlocks) {
                    val unlockText = "⚡ ${productivityStats.unlockCount} UNLOCKS TODAY"
                    canvas.drawText(unlockText, sx + 510f, sy, hudSubTextPaint)
                }

                // 2. Large Bold Glanceable Curved Pixel Time Readout (125f)
                if (showScreenTime) {
                    sy += 115f
                    canvas.drawText(productivityStats.formattedScreenTime, sx, sy, hudTimePaint)
                    canvas.drawText("● TAP HUD TO SWITCH", sx + 560f, sy - 15f, hudBadgePaint)
                }

                // 3. Subtle Horizontal Divider
                sy += 35f
                faintStrokePaint.color = Color.argb(140, 70, 85, 115)
                canvas.drawLine(sx, sy, sx + hudWidth, sy, faintStrokePaint)

                // 4. Top 3 Apps Breakdown (Prominent & Glanceable)
                if (showTopApps && productivityStats.topApps.isNotEmpty()) {
                    sy += 42f
                    canvas.drawText("TOP APPS", sx, sy, hudTitlePaint)
                    sy += 48f

                    for (app in productivityStats.topApps) {
                        // App Name (46f)
                        canvas.drawText(app.name, sx, sy, hudAppTextPaint)

                        // Progress Bar (Height: 20f, CornerRadius: 10f)
                        val bx1 = sx + 250f
                        val bx2 = sx + 740f
                        val bw = bx2 - bx1
                        val bh = 20f
                        val by = sy - 26f

                        // Background Bar
                        tempRect.set(bx1, by, bx2, by + bh)
                        canvas.drawRoundRect(tempRect, 10f, 10f, barTrackPaint)

                        // Active Monochrome Fill Bar
                        val fillW = (bw * app.percentageOfTop).coerceAtLeast(14f)
                        tempRect.set(bx1, by, bx1 + fillW, by + bh)
                        canvas.drawRoundRect(tempRect, 10f, 10f, barFillPaint)

                        // Duration Text (40f)
                        canvas.drawText(app.formattedDuration, sx + 770f, sy, hudSubTextPaint)

                        sy += 64f
                    }
                }
            }

            LY02ProductivityMode.DAILY_GOAL -> {
                // Focus Goal Mode
                canvas.drawText("WELLNESS FOCUS TARGET", sx, sy, hudTitlePaint)
                if (showUnlocks) {
                    canvas.drawText("⚡ ${productivityStats.unlockCount} UNLOCKS", sx + 580f, sy, hudSubTextPaint)
                }

                sy += 115f
                canvas.drawText(productivityStats.formattedScreenTime, sx, sy, hudTimePaint)
                canvas.drawText("/ 04h 00m GOAL", sx + 520f, sy - 15f, hudSubTextPaint)

                sy += 35f
                faintStrokePaint.color = Color.argb(140, 70, 85, 115)
                canvas.drawLine(sx, sy, sx + hudWidth, sy, faintStrokePaint)

                sy += 50f
                val usedRatio = (productivityStats.totalScreenTimeMillis / (productivityStats.goalMinutes * 60_000f)).coerceIn(0.05f, 1.0f)
                val pctUsed = (usedRatio * 100).toInt()

                // Wide Progress Bar
                val barW = hudWidth
                val barH = 26f
                tempRect.set(sx, sy, sx + barW, sy + barH)
                canvas.drawRoundRect(tempRect, 13f, 13f, barTrackPaint)

                tempRect.set(sx, sy, sx + barW * usedRatio, sy + barH)
                canvas.drawRoundRect(tempRect, 13f, 13f, barFillPaint)

                sy += 62f
                val statusText = if (productivityStats.remainingMinutes > 0) {
                    "$pctUsed% BUDGET USED  •  ${productivityStats.remainingMinutes}M REMAINING TODAY"
                } else {
                    "DAILY TARGET REACHED  •  TAKE A BREAK"
                }
                canvas.drawText(statusText, sx, sy, hudSubTextPaint)
            }

            LY02ProductivityMode.MINIMAL_TIME -> {
                // Minimalist Glance View
                canvas.drawText("TODAY", sx, sy, hudTitlePaint)
                if (showUnlocks) {
                    canvas.drawText("⚡ ${productivityStats.unlockCount} UNLOCKS", sx + 580f, sy, hudSubTextPaint)
                }

                sy += 135f
                hudTimePaint.textSize = 145f
                canvas.drawText(productivityStats.formattedScreenTime, sx, sy, hudTimePaint)
                hudTimePaint.textSize = 125f // restore

                sy += 30f
                canvas.drawText("ACTIVE SCREEN ON  •  TAP TO EXPAND", sx, sy, hudBadgePaint)
            }
        }
    }
}
