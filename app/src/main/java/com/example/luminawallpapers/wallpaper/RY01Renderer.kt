package com.example.luminawallpapers.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.luminawallpapers.data.LiveWallpaperSettings
import com.example.luminawallpapers.util.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class RY01Theme(val label: String) {
    COZY_PEACH("Cozy Peach Dawn"),
    LAVENDER_HAZE("Lavender Haze"),
    MATCHA_HONEY("Matcha & Honey"),
    DYNAMIC_TIME("Dynamic Day/Night")
}

class RY01Renderer(private val context: Context? = null) {

    val gridWidth = 180
    val gridHeight = 390

    var currentTheme = RY01Theme.COZY_PEACH
    var weatherMode = WeatherMode.CLEAR_NIGHT
    var currentTemp = "22°C"
    var cityName = "Hyderabad"
    var batteryPercent = 94
    var unlockCount = 28

    // Productivity Telemetry
    var telemetryMode = 0 // 0: UV Index, 1: AQI, 2: Rain %
    var countdownLabel = "BIRTHDAY"
    var countdownDays = 14
    var showHoursCountdown = false
    var waterGlasses = 4
    var waterGoal = 8

    // Sun settings (Stationary locked at 124, 86)
    val sunCenterX = 124
    val sunCenterY = 86
    val sunRadius = 28

    // Animation & State
    private var animTime = 0f
    private var lastFrameTime = System.currentTimeMillis()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    // Virtual 180 x 390 framebuffer for 100% pixel-perfect 8-bit graphics
    private val virtualBitmap: Bitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
    private val pixelBuffer = IntArray(gridWidth * gridHeight)
    private val srcRect = Rect(0, 0, gridWidth, gridHeight)
    private val dstRect = RectF()
    private val scalingPaint = Paint().apply { isFilterBitmap = false }

    // 4x4 Bayer Dithering Matrix
    private val bayerMatrix = intArrayOf(
         0,  8,  2, 10,
        12,  4, 14,  6,
         3, 11,  1,  9,
        15,  7, 13,  5
    )

    // Puffy Clouds
    data class Lobe(val relX: Int, val relY: Int, val radius: Int)
    data class Cloud(var x: Float, val y: Float, val speed: Float, val templateIdx: Int)

    private val cloudTemplates = listOf(
        listOf(Lobe(0, 4, 7), Lobe(8, 0, 10), Lobe(16, 4, 8)),
        listOf(Lobe(0, 5, 8), Lobe(9, 1, 11), Lobe(20, 0, 13), Lobe(31, 5, 9)),
        listOf(Lobe(0, 6, 9), Lobe(10, 2, 12), Lobe(22, 0, 15), Lobe(35, 2, 13), Lobe(46, 6, 9))
    )
    private val clouds = mutableListOf<Cloud>()

    // Sparkles & Swallows
    data class Sparkle(val x: Int, val y: Int, var phase: Float, val speed: Float)
    private val sparkles = mutableListOf<Sparkle>()

    // Birds / Swallows
    data class Bird(val startX: Float, val startY: Float, var flapPhase: Float)
    private val birds = listOf(
        Bird(42f, 55f, 0.0f),
        Bird(49f, 60f, 0.5f),
        Bird(55f, 63f, 1.0f),
        Bird(82f, 115f, 1.5f),
        Bird(89f, 120f, 2.0f)
    )

    // Weather Rain / Snow Particles & Splashes
    data class WeatherDrop(var x: Float, var y: Float, var speedY: Float, var speedX: Float)
    data class RainSplash(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float)

    private val weatherDrops = mutableListOf<WeatherDrop>()
    private val rainSplashes = mutableListOf<RainSplash>()
    private var lightningTimer = 0f
    private var thunderFlashAlpha = 0f

    init {
        // Clouds initial distribution
        clouds.add(Cloud(-12f, 45f, 0.8f, 0))
        clouds.add(Cloud(80f, 125f, 1.2f, 1))
        clouds.add(Cloud(88f, 88f, 0.6f, 1))
        clouds.add(Cloud(110f, 220f, 1.0f, 0))
        clouds.add(Cloud(-22f, 235f, 0.7f, 2))
        clouds.add(Cloud(95f, 290f, 1.1f, 1))
        clouds.add(Cloud(-10f, 320f, 0.9f, 2))
        clouds.add(Cloud(40f, 350f, 0.7f, 2))

        // Sparkles
        val sparkleCoords = listOf(
            Pair(32, 38), Pair(68, 190), Pair(155, 40), Pair(14, 138),
            Pair(160, 185), Pair(22, 242), Pair(18, 340), Pair(110, 48),
            Pair(75, 295), Pair(145, 330), Pair(85, 230), Pair(170, 120)
        )
        val rng = Random(42)
        for ((sx, sy) in sparkleCoords) {
            sparkles.add(Sparkle(sx, sy, rng.nextFloat() * 6.28f, 1.2f + rng.nextFloat() * 1.5f))
        }

        // Weather drops with diagonal speed
        for (i in 0 until 50) {
            weatherDrops.add(WeatherDrop(rng.nextFloat() * gridWidth, rng.nextFloat() * gridHeight, 150f + rng.nextFloat() * 70f, -25f - rng.nextFloat() * 15f))
        }

        context?.let { loadFromSettings(it) }
    }

    fun loadFromSettings(ctx: Context) {
        val settings = LiveWallpaperSettings(ctx)
        currentTheme = try {
            RY01Theme.valueOf(settings.ry01Theme)
        } catch (e: Exception) {
            RY01Theme.COZY_PEACH
        }
        waterGlasses = settings.ry01WaterGlasses
        waterGoal = settings.ry01WaterGoal
        countdownLabel = settings.ry01CountdownLabel
        countdownDays = settings.ry01CountdownDays
        telemetryMode = settings.ry01TelemetryMode
        cityName = settings.cityName
        currentTemp = settings.currentTemp
        weatherMode = settings.weatherMode

        // Screen unlocks
        unlockCount = UsageStatsHelper.getDailyUnlockCount(ctx)
    }

    fun persistSettings(ctx: Context) {
        val settings = LiveWallpaperSettings(ctx)
        settings.ry01Theme = currentTheme.name
        settings.ry01WaterGlasses = waterGlasses
        settings.ry01WaterGoal = waterGoal
        settings.ry01CountdownLabel = countdownLabel
        settings.ry01CountdownDays = countdownDays
        settings.ry01TelemetryMode = telemetryMode
        settings.weatherMode = weatherMode
    }

    // -------------------------------------------------------------
    // Touch Interaction
    // -------------------------------------------------------------

    fun onTouch(normX: Float, normY: Float, ctx: Context? = context): Boolean {
        val gx = (normX * gridWidth).toInt()
        val gy = (normY * gridHeight).toInt()

        // 1. Weather & Telemetry Row (x: 10..125, y: 138..150)
        if (gx in 10..125 && gy in 138..150) {
            telemetryMode = (telemetryMode + 1) % 3
            vibrateTick(ctx)
            ctx?.let { persistSettings(it) }
            return true
        }

        // 2. Countdown Row (x: 10..125, y: 150..163)
        if (gx in 10..125 && gy in 150..163) {
            showHoursCountdown = !showHoursCountdown
            vibrateTick(ctx)
            return true
        }

        // 3. Water Habit Tracker (x: 10..115, y: 202..218)
        if (gx in 10..115 && gy in 202..218) {
            waterGlasses = if (waterGlasses >= waterGoal) 0 else waterGlasses + 1
            vibrateTick(ctx)
            ctx?.let { persistSettings(it) }
            return true
        }

        // 4. Sun Touch Zone (dx*dx + dy*dy <= 34*34)
        val sDx = gx - sunCenterX
        val sDy = gy - sunCenterY
        if (sDx * sDx + sDy * sDy <= 34 * 34) {
            // Cycle color theme on sun tap
            val themes = RY01Theme.values()
            val nextIdx = (currentTheme.ordinal + 1) % themes.size
            currentTheme = themes[nextIdx]
            vibrateTick(ctx)
            ctx?.let { persistSettings(it) }
            return true
        }

        return false
    }

    private fun vibrateTick(ctx: Context?) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                ctx?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20L)
            }
        } catch (e: Exception) {
            // Ignore haptic failures
        }
    }

    // -------------------------------------------------------------
    // Update & Logic
    // -------------------------------------------------------------

    fun update(dtSec: Float) {
        animTime += dtSec

        // Clouds horizontal drift
        for (c in clouds) {
            c.x += c.speed * dtSec * 3.5f
            if (c.x > gridWidth + 30) {
                c.x = -60f
            }
        }

        // Sparkles twinkle
        for (s in sparkles) {
            s.phase = (s.phase + s.speed * dtSec) % 6.283f
        }

        // Rain/Snow particles
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) {
            for (p in weatherDrops) {
                p.y += p.speedY * dtSec
                p.x += p.speedX * dtSec
                if (p.y > gridHeight - 6) {
                    // Spawn splash on ground
                    if (rainSplashes.size < 24 && Random.nextFloat() < 0.6f) {
                        rainSplashes.add(RainSplash(
                            x = p.x.coerceIn(2f, (gridWidth - 3).toFloat()),
                            y = (gridHeight - 4).toFloat(),
                            vx = (Random.nextFloat() - 0.5f) * 18f,
                            vy = -18f - Random.nextFloat() * 14f,
                            life = 0.16f,
                            maxLife = 0.16f
                        ))
                    }
                    p.y = -6f
                    p.x = Random.nextFloat() * (gridWidth + 40) - 20f
                }
            }

            // Occasional ambient lightning flash during thunderstorm
            if (weatherMode == WeatherMode.THUNDER) {
                lightningTimer += dtSec
                if (lightningTimer > 4.5f && Random.nextFloat() < 0.05f) {
                    thunderFlashAlpha = 0.40f
                    lightningTimer = 0f
                }
            }
        } else if (weatherMode == WeatherMode.SNOW) {
            for (p in weatherDrops) {
                p.y += (p.speedY * 0.35f) * dtSec
                p.x += sin(p.y * 0.08f) * 12f * dtSec
                if (p.y > gridHeight) {
                    p.y = -4f
                    p.x = Random.nextFloat() * gridWidth
                }
            }
        }

        // Update rain splashes
        if (rainSplashes.isNotEmpty()) {
            val it = rainSplashes.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.x += s.vx * dtSec
                s.y += s.vy * dtSec
                s.vy += 80f * dtSec // gravity pull down
                s.life -= dtSec
                if (s.life <= 0f) {
                    it.remove()
                }
            }
        }

        if (thunderFlashAlpha > 0f) {
            thunderFlashAlpha = (thunderFlashAlpha - dtSec * 3.5f).coerceAtLeast(0f)
        }
    }

    // -------------------------------------------------------------
    // Rendering Core (Virtual 180x390 Bitmap)
    // -------------------------------------------------------------

    fun render(canvas: Canvas, screenW: Int, screenH: Int) {
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameTime) / 1000f).coerceIn(0.001f, 0.1f)
        lastFrameTime = now
        update(dt)

        // 1. Generate Bayer Dithered Pastel Sky
        renderSkyGradient()

        // 2. Stationary Sun & Aura Rings
        renderSun()

        // 3. Clouds
        renderClouds()

        // 4. Swallows & Sparkles
        renderSwallows()
        renderSparkles()

        // 5. Rain/Snow Particles
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER || weatherMode == WeatherMode.SNOW) {
            renderWeatherParticles()
        }

        // Ambient lightning flash during thunderstorm
        if (thunderFlashAlpha > 0.01f) {
            val flashR = (255 * thunderFlashAlpha).toInt()
            val flashG = (255 * thunderFlashAlpha).toInt()
            val flashB = (255 * thunderFlashAlpha).toInt()
            for (i in pixelBuffer.indices) {
                val c = pixelBuffer[i]
                val r = (((c ushr 16) and 0xFF) + flashR).coerceAtMost(255)
                val g = (((c ushr 8) and 0xFF) + flashG).coerceAtMost(255)
                val b = ((c and 0xFF) + flashB).coerceAtMost(255)
                pixelBuffer[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // 6. Productivity HUD
        renderHUD()

        // Write pixel buffer into virtual bitmap
        virtualBitmap.setPixels(pixelBuffer, 0, gridWidth, 0, 0, gridWidth, gridHeight)

        // Scale up to device screen using nearest-neighbor (0% blur, razor-sharp pixels)
        dstRect.set(0f, 0f, screenW.toFloat(), screenH.toFloat())
        canvas.drawBitmap(virtualBitmap, srcRect, dstRect, scalingPaint)
    }

    private data class ColorStop(val pos: Float, val r: Int, val g: Int, val b: Int)

    private fun getSkyStops(): List<ColorStop> {
        if (weatherMode == WeatherMode.RAIN) {
            return listOf(
                ColorStop(0.00f, 108, 120, 150), // Moody slate dusk-blue
                ColorStop(0.25f, 140, 150, 178), // Overcast lavender grey
                ColorStop(0.52f, 174, 182, 204), // Soft petrichor mist
                ColorStop(0.75f, 204, 210, 224), // Foggy morning silver
                ColorStop(0.90f, 224, 228, 236), // Wet pavement reflection
                ColorStop(1.00f, 236, 240, 246)  // Bright horizon mist
            )
        } else if (weatherMode == WeatherMode.THUNDER) {
            return listOf(
                ColorStop(0.00f, 48, 52, 75),    // Deep stormy thunder navy
                ColorStop(0.30f, 78, 84, 110),   // Dark slate purple
                ColorStop(0.60f, 118, 124, 150), // Electric storm grey
                ColorStop(0.85f, 158, 164, 185), // Misty squall
                ColorStop(1.00f, 188, 194, 210)  // Pale horizon
            )
        } else if (weatherMode == WeatherMode.SNOW) {
            return listOf(
                ColorStop(0.00f, 155, 175, 205), // Crisp winter frost
                ColorStop(0.35f, 185, 200, 222), // Pastel icy blue
                ColorStop(0.70f, 215, 228, 242), // Frosted snow mist
                ColorStop(1.00f, 240, 246, 255)  // Pure winter dawn
            )
        }

        return when (currentTheme) {
            RY01Theme.COZY_PEACH -> listOf(
                ColorStop(0.00f, 162, 172, 222),
                ColorStop(0.24f, 202, 168, 206),
                ColorStop(0.48f, 238, 178, 186),
                ColorStop(0.70f, 254, 202, 168),
                ColorStop(0.88f, 255, 226, 182),
                ColorStop(1.00f, 255, 242, 208)
            )
            RY01Theme.LAVENDER_HAZE -> listOf(
                ColorStop(0.00f, 142, 150, 210),
                ColorStop(0.30f, 175, 160, 215),
                ColorStop(0.60f, 210, 185, 225),
                ColorStop(0.85f, 235, 205, 220),
                ColorStop(1.00f, 250, 225, 215)
            )
            RY01Theme.MATCHA_HONEY -> listOf(
                ColorStop(0.00f, 148, 185, 172),
                ColorStop(0.35f, 185, 210, 175),
                ColorStop(0.65f, 220, 225, 180),
                ColorStop(0.85f, 245, 230, 185),
                ColorStop(1.00f, 255, 242, 210)
            )
            RY01Theme.DYNAMIC_TIME -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                when (hour) {
                    in 6..10 -> listOf( // Morning Dawn
                        ColorStop(0.00f, 162, 172, 222),
                        ColorStop(0.24f, 202, 168, 206),
                        ColorStop(0.48f, 238, 178, 186),
                        ColorStop(0.70f, 254, 202, 168),
                        ColorStop(0.88f, 255, 226, 182),
                        ColorStop(1.00f, 255, 242, 208)
                    )
                    in 11..16 -> listOf( // Midday Clear Sky
                        ColorStop(0.00f, 130, 185, 240),
                        ColorStop(0.40f, 165, 210, 250),
                        ColorStop(0.75f, 205, 232, 255),
                        ColorStop(1.00f, 235, 245, 255)
                    )
                    in 17..19 -> listOf( // Golden Dusk
                        ColorStop(0.00f, 85, 80, 145),
                        ColorStop(0.30f, 155, 95, 135),
                        ColorStop(0.60f, 230, 120, 110),
                        ColorStop(0.85f, 250, 180, 115),
                        ColorStop(1.00f, 255, 220, 150)
                    )
                    else -> listOf( // Night
                        ColorStop(0.00f, 5, 8, 18),
                        ColorStop(0.35f, 12, 18, 32),
                        ColorStop(0.70f, 24, 28, 48),
                        ColorStop(1.00f, 38, 42, 65)
                    )
                }
            }
        }
    }

    private fun renderSkyGradient() {
        val stops = getSkyStops()

        for (y in 0 until gridHeight) {
            val t = y.toFloat() / (gridHeight - 1)

            // Linear interpolation across stops
            var r = stops.first().r.toFloat()
            var g = stops.first().g.toFloat()
            var b = stops.first().b.toFloat()

            for (i in 0 until stops.size - 1) {
                val s0 = stops[i]
                val s1 = stops[i + 1]
                if (t >= s0.pos && t <= s1.pos) {
                    val localT = (t - s0.pos) / (s1.pos - s0.pos)
                    r = s0.r + (s1.r - s0.r) * localT
                    g = s0.g + (s1.g - s0.g) * localT
                    b = s0.b + (s1.b - s0.b) * localT
                    break
                }
            }

            val bayerY = (y % 4) * 4
            val rowOffset = y * gridWidth

            for (x in 0 until gridWidth) {
                val dither = (bayerMatrix[bayerY + (x % 4)] - 8) * 0.35f
                val pr = ((r + dither).coerceIn(0f, 255f) / 3f).toInt() * 3
                val pg = ((g + dither).coerceIn(0f, 255f) / 3f).toInt() * 3
                val pb = ((b + dither).coerceIn(0f, 255f) / 3f).toInt() * 3

                pixelBuffer[rowOffset + x] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
            }
        }
    }

    private fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until gridWidth && y in 0 until gridHeight) {
            val a = (color ushr 24) and 0xFF
            if (a == 255) {
                pixelBuffer[y * gridWidth + x] = color
            } else if (a > 0) {
                val bg = pixelBuffer[y * gridWidth + x]
                val bgR = (bg ushr 16) and 0xFF
                val bgG = (bg ushr 8) and 0xFF
                val bgB = bg and 0xFF

                val fgR = (color ushr 16) and 0xFF
                val fgG = (color ushr 8) and 0xFF
                val fgB = color and 0xFF

                val invA = 255 - a
                val outR = (fgR * a + bgR * invA) / 255
                val outG = (fgG * a + bgG * invA) / 255
                val outB = (fgB * a + bgB * invA) / 255

                pixelBuffer[y * gridWidth + x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }

    private fun renderSun() {
        val sunCore = Color.argb(255, 255, 255, 255)
        val sunInner = Color.argb(255, 255, 250, 218)
        val sunMid = Color.argb(255, 255, 228, 162)
        val sunRim = Color.argb(255, 254, 194, 142)
        val sunAura = Color.argb(200, 255, 225, 170)
        val sunRay = Color.argb(220, 255, 245, 210)

        // Radial ray spikes
        val rayAngles = intArrayOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330)
        for (ang in rayAngles) {
            val rad = Math.toRadians(ang.toDouble())
            val rStart = sunRadius + 4
            val rEnd = sunRadius + if (ang % 60 == 0) 16 else 10
            var dist = rStart
            while (dist < rEnd) {
                val rx = (sunCenterX + dist * cos(rad)).toInt()
                val ry = (sunCenterY + dist * sin(rad)).toInt()
                setPixel(rx, ry, sunRay)
                dist += 2
            }
        }

        // Concentric aura rings
        for (haloR in intArrayOf(sunRadius + 6, sunRadius + 15, sunRadius + 24)) {
            val minD = (haloR - 1) * (haloR - 1)
            val maxD = haloR * haloR
            for (dy in -haloR..haloR) {
                for (dx in -haloR..haloR) {
                    val dSq = dx * dx + dy * dy
                    if (dSq in minD..maxD && (dx + dy) % 3 == 0) {
                        setPixel(sunCenterX + dx, sunCenterY + dy, sunAura)
                    }
                }
            }
        }

        // Concentric sun disk
        val rMidSq = (sunRadius * 0.76f) * (sunRadius * 0.76f)
        val rInnerSq = (sunRadius * 0.52f) * (sunRadius * 0.52f)
        val rCoreSq = (sunRadius * 0.30f) * (sunRadius * 0.30f)
        val rMaxSq = sunRadius * sunRadius

        for (dy in -sunRadius..sunRadius) {
            for (dx in -sunRadius..sunRadius) {
                val dSq = dx * dx + dy * dy
                if (dSq <= rMaxSq) {
                    val col = when {
                        dSq <= rCoreSq -> sunCore
                        dSq <= rInnerSq -> sunInner
                        dSq <= rMidSq -> sunMid
                        else -> sunRim
                    }
                    setPixel(sunCenterX + dx, sunCenterY + dy, col)
                }
            }
        }
    }

    private fun renderClouds() {
        val isRainy = weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER
        val isSnow = weatherMode == WeatherMode.SNOW
        val cloudHi = when {
            isRainy -> Color.argb(255, 224, 232, 244)
            isSnow -> Color.argb(255, 248, 252, 255)
            else -> Color.argb(255, 255, 255, 255)
        }
        val cloudBody = when {
            isRainy -> Color.argb(248, 178, 190, 210)
            isSnow -> Color.argb(248, 220, 230, 245)
            else -> Color.argb(248, 254, 238, 236)
        }
        val cloudShdw = when {
            isRainy -> Color.argb(235, 134, 146, 172)
            isSnow -> Color.argb(235, 185, 200, 222)
            else -> Color.argb(235, 215, 192, 218)
        }

        for (cloud in clouds) {
            val template = cloudTemplates[cloud.templateIdx]
            val cx = cloud.x.toInt()
            val cy = cloud.y.toInt()

            val minY = template.minOf { it.relY - it.radius }
            val maxY = template.maxOf { it.relY + it.radius }
            val minX = template.minOf { it.relX - it.radius }
            val maxX = template.maxOf { it.relX + it.radius }

            for (row in minY..maxY) {
                for (col in minX..maxX) {
                    var inside = false
                    var isTopHi = false
                    val isBotShdw = (row >= maxY - 2)

                    for (lobe in template) {
                        val ldx = col - lobe.relX
                        val ldy = row - lobe.relY
                        if (ldx * ldx + ldy * ldy <= lobe.radius * lobe.radius) {
                            inside = true
                            if (ldy <= -lobe.radius + 2) isTopHi = true
                        }
                    }

                    if (inside) {
                        val px = cx + col
                        val py = cy + row
                        val c = when {
                            isTopHi -> cloudHi
                            isBotShdw -> cloudShdw
                            else -> cloudBody
                        }
                        setPixel(px, py, c)
                    }
                }
            }
        }
    }

    private fun renderSwallows() {
        val birdCol = Color.argb(240, 118, 98, 138)
        for (b in birds) {
            val bx = b.startX.toInt()
            val by = (b.startY + sin(animTime * 2.5f + b.flapPhase) * 1.5f).toInt()

            val wingY = if (sin(animTime * 6f + b.flapPhase) > 0) -1 else 0
            val offsets = listOf(
                Pair(-2, wingY), Pair(2, wingY),
                Pair(-1, 0), Pair(1, 0),
                Pair(0, 1)
            )
            for ((ox, oy) in offsets) {
                setPixel(bx + ox, by + oy, birdCol)
            }
        }
    }

    private fun renderSparkles() {
        for (s in sparkles) {
            val twinkle = sin(s.phase) * 0.5f + 0.5f
            if (twinkle > 0.4f) {
                val col = if (twinkle > 0.8f) Color.WHITE else Color.argb(220, 255, 235, 175)
                setPixel(s.x, s.y, col)
                if (twinkle > 0.7f) {
                    setPixel(s.x - 1, s.y, col)
                    setPixel(s.x + 1, s.y, col)
                    setPixel(s.x, s.y - 1, col)
                    setPixel(s.x, s.y + 1, col)
                }
            }
        }
    }

    private fun renderWeatherParticles() {
        val rainHead = Color.argb(240, 215, 238, 255)
        val rainMid = Color.argb(190, 175, 208, 245)
        val rainTail = Color.argb(120, 145, 180, 230)
        val snowCol = Color.argb(240, 255, 255, 255)

        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) {
            for (p in weatherDrops) {
                val px = p.x.toInt()
                val py = p.y.toInt()
                setPixel(px, py, rainHead)
                setPixel(px + 1, py - 1, rainMid)
                setPixel(px + 2, py - 2, rainTail)
            }
            for (s in rainSplashes) {
                val alpha = (s.life / s.maxLife).coerceIn(0f, 1f)
                val splashCol = Color.argb((alpha * 220).toInt(), 215, 235, 255)
                setPixel(s.x.toInt(), s.y.toInt(), splashCol)
            }
        } else if (weatherMode == WeatherMode.SNOW) {
            for (p in weatherDrops) {
                val px = p.x.toInt()
                val py = p.y.toInt()
                setPixel(px, py, snowCol)
                setPixel(px + 1, py, snowCol)
                setPixel(px, py + 1, snowCol)
                setPixel(px + 1, py + 1, snowCol)
            }
        }
    }

    // -------------------------------------------------------------
    // Productivity HUD & Bitmap Font
    // -------------------------------------------------------------

    private fun renderHUD() {
        val hudX = 14
        val hudY = 142

        val hudMain = Color.argb(255, 60, 48, 80)
        val hudSub = Color.argb(240, 95, 78, 115)
        val hudAccent = Color.argb(255, 205, 95, 112)
        val hudWater = Color.argb(255, 79, 175, 219)
        val hudShadow = Color.argb(180, 255, 242, 235)

        // Line 1: Weather & Telemetry (Single Tap to cycle)
        val teleText = when (telemetryMode) {
            0 -> if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) "UV 1 LOW" else "UV 3 MOD"
            1 -> if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) "AQI 22 FRESH" else "AQI 42 GOOD"
            else -> if (weatherMode == WeatherMode.RAIN) "RAIN 85%" else if (weatherMode == WeatherMode.THUNDER) "STORM 95%" else if (weatherMode == WeatherMode.SNOW) "SNOW 80%" else "RAIN 10%"
        }
        val weatherTag = when (weatherMode) {
            WeatherMode.RAIN -> "RAIN"
            WeatherMode.THUNDER -> "STORM"
            WeatherMode.SNOW -> "SNOW"
            WeatherMode.CLOUDY -> "CLOUDY"
            WeatherMode.CLEAR_NIGHT -> "CLEAR"
        }
        val line1 = "${cityName.take(3).uppercase()} $currentTemp $weatherTag • $teleText"
        drawText(line1, hudX, hudY, hudSub, 1, hudShadow)

        // Line 2: Custom Countdown (Single Tap to toggle days vs hours)
        drawCakeIcon(hudX, hudY + 9, hudAccent, Color.argb(255, 255, 215, 100))
        val countText = if (showHoursCountdown) {
            "${countdownLabel.uppercase()} IN ${countdownDays * 24}H"
        } else {
            "${countdownLabel.uppercase()} IN $countdownDays DAYS"
        }
        drawText(countText, hudX + 8, hudY + 10, hudAccent, 1, hudShadow)

        // Line 3: Big Monospace Clock (HH:mm)
        val currentTimeStr = timeFormat.format(Date())
        drawText(currentTimeStr, hudX, hudY + 22, hudMain, 3, hudShadow)

        // Line 4: Date
        val currentDateStr = dateFormat.format(Date()).uppercase()
        drawText(currentDateStr, hudX, hudY + 42, hudSub, 1, hudShadow)

        // Line 5: Battery & Unlocks
        val batBar = when {
            batteryPercent >= 75 -> "[====]"
            batteryPercent >= 50 -> "[=== ]"
            batteryPercent >= 25 -> "[==  ]"
            else -> "[=   ]"
        }
        val line5 = "BAT $batteryPercent% $batBar • $unlockCount UNLOCKS"
        drawText(line5, hudX, hudY + 53, hudSub, 1, hudShadow)

        // Line 6: Water Habit Tracker (Single Tap + Haptic to log +1 cup)
        drawDropletIcon(hudX, hudY + 64, hudWater)
        drawText("WATER", hudX + 8, hudY + 65, hudWater, 1, hudShadow)
        drawProgressBar(hudX + 36, hudY + 66, waterGlasses, waterGoal, hudWater, hudSub)
        drawText("$waterGlasses/$waterGoal", hudX + 72, hudY + 65, hudSub, 1, hudShadow)
    }

    private fun drawCakeIcon(gx: Int, gy: Int, primaryCol: Int, candleCol: Int) {
        setPixel(gx + 2, gy, candleCol)
        setPixel(gx + 2, gy + 1, primaryCol)
        for (x in 0 until 5) setPixel(gx + x, gy + 2, candleCol)
        for (y in 3..5) {
            setPixel(gx, gy + y, primaryCol)
            setPixel(gx + 4, gy + y, primaryCol)
            setPixel(gx + 2, gy + y, primaryCol)
        }
        for (x in 0 until 5) setPixel(gx + x, gy + 6, primaryCol)
    }

    private fun drawDropletIcon(gx: Int, gy: Int, waterCol: Int) {
        val pixels = listOf(
            Pair(gx + 2, gy),
            Pair(gx + 2, gy + 1),
            Pair(gx + 1, gy + 2), Pair(gx + 2, gy + 2), Pair(gx + 3, gy + 2),
            Pair(gx, gy + 3), Pair(gx + 1, gy + 3), Pair(gx + 3, gy + 3), Pair(gx + 4, gy + 3),
            Pair(gx, gy + 4), Pair(gx + 1, gy + 4), Pair(gx + 2, gy + 4), Pair(gx + 3, gy + 4), Pair(gx + 4, gy + 4),
            Pair(gx + 1, gy + 5), Pair(gx + 2, gy + 5), Pair(gx + 3, gy + 5)
        )
        for ((px, py) in pixels) {
            setPixel(px, py, waterCol)
        }
        // Specular glint
        setPixel(gx + 2, gy + 3, Color.WHITE)
    }

    private fun drawProgressBar(gx: Int, gy: Int, current: Int, total: Int, filledCol: Int, emptyCol: Int) {
        val pipW = 3
        val pipH = 4
        val spacing = 1
        for (i in 0 until total) {
            val x0 = gx + i * (pipW + spacing)
            val isFilled = i < current
            for (dy in 0 until pipH) {
                for (dx in 0 until pipW) {
                    if (isFilled) {
                        setPixel(x0 + dx, gy + dy, filledCol)
                    } else {
                        // Outline pip
                        if (dy == 0 || dy == pipH - 1 || dx == 0 || dx == pipW - 1) {
                            setPixel(x0 + dx, gy + dy, emptyCol)
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Monospace 3x5 Bitmap Font Engine
    // -------------------------------------------------------------

    private val fontMap = mapOf(
        '0' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        '1' to arrayOf(intArrayOf(0,1,0), intArrayOf(1,1,0), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(1,1,1)),
        '2' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1)),
        '3' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1)),
        '4' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(0,0,1)),
        '5' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1)),
        '6' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        '7' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(0,1,0)),
        '8' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        '9' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1)),
        'A' to arrayOf(intArrayOf(0,1,0), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'B' to arrayOf(intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,1,0)),
        'C' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,0,0), intArrayOf(1,0,0), intArrayOf(1,1,1)),
        'D' to arrayOf(intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,0)),
        'E' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1)),
        'F' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,0), intArrayOf(1,0,0), intArrayOf(1,0,0)),
        'G' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        'H' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'I' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(1,1,1)),
        'J' to arrayOf(intArrayOf(0,0,1), intArrayOf(0,0,1), intArrayOf(0,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        'K' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,1,0), intArrayOf(1,0,0), intArrayOf(1,1,0), intArrayOf(1,0,1)),
        'L' to arrayOf(intArrayOf(1,0,0), intArrayOf(1,0,0), intArrayOf(1,0,0), intArrayOf(1,0,0), intArrayOf(1,1,1)),
        'M' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'N' to arrayOf(intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'O' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        'P' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,0,0)),
        'Q' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(0,0,1)),
        'R' to arrayOf(intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,1,0), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'S' to arrayOf(intArrayOf(1,1,1), intArrayOf(1,0,0), intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(1,1,1)),
        'T' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(0,1,0)),
        'U' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1)),
        'V' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(0,1,0)),
        'W' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(1,0,1)),
        'X' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(0,1,0), intArrayOf(1,0,1), intArrayOf(1,0,1)),
        'Y' to arrayOf(intArrayOf(1,0,1), intArrayOf(1,0,1), intArrayOf(1,1,1), intArrayOf(0,1,0), intArrayOf(0,1,0)),
        'Z' to arrayOf(intArrayOf(1,1,1), intArrayOf(0,0,1), intArrayOf(0,1,0), intArrayOf(1,0,0), intArrayOf(1,1,1)),
        ':' to arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(0), intArrayOf(1), intArrayOf(0)),
        '.' to arrayOf(intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(1)),
        ',' to arrayOf(intArrayOf(0), intArrayOf(0), intArrayOf(0), intArrayOf(1), intArrayOf(1)),
        '-' to arrayOf(intArrayOf(0,0,0), intArrayOf(0,0,0), intArrayOf(1,1,1), intArrayOf(0,0,0), intArrayOf(0,0,0)),
        '/' to arrayOf(intArrayOf(0,0,1), intArrayOf(0,1,0), intArrayOf(0,1,0), intArrayOf(1,0,0), intArrayOf(1,0,0)),
        '%' to arrayOf(intArrayOf(1,0,1), intArrayOf(0,0,1), intArrayOf(0,1,0), intArrayOf(1,0,0), intArrayOf(1,0,1)),
        '°' to arrayOf(intArrayOf(1,1), intArrayOf(1,1), intArrayOf(0,0), intArrayOf(0,0), intArrayOf(0,0)),
        '•' to arrayOf(intArrayOf(0), intArrayOf(0), intArrayOf(1), intArrayOf(0), intArrayOf(0)),
        '[' to arrayOf(intArrayOf(1,1), intArrayOf(1,0), intArrayOf(1,0), intArrayOf(1,0), intArrayOf(1,1)),
        ']' to arrayOf(intArrayOf(1,1), intArrayOf(0,1), intArrayOf(0,1), intArrayOf(0,1), intArrayOf(1,1)),
        '=' to arrayOf(intArrayOf(0,0,0), intArrayOf(1,1,1), intArrayOf(0,0,0), intArrayOf(1,1,1), intArrayOf(0,0,0)),
        ' ' to arrayOf(intArrayOf(0,0), intArrayOf(0,0), intArrayOf(0,0), intArrayOf(0,0), intArrayOf(0,0))
    )

    private fun drawText(text: String, startX: Int, startY: Int, color: Int, scale: Int = 1, shadowColor: Int? = null) {
        var cursorX = startX
        for (ch in text) {
            val glyph = fontMap[ch] ?: fontMap[ch.uppercaseChar()] ?: fontMap[' ']!!
            val charW = glyph[0].size
            val charH = glyph.size

            // Draw shadow first if requested
            if (shadowColor != null) {
                for (r in 0 until charH) {
                    for (c in 0 until charW) {
                        if (glyph[r][c] == 1) {
                            for (dy in 0 until scale) {
                                for (dx in 0 until scale) {
                                    setPixel(cursorX + c * scale + dx, startY + r * scale + dy + scale, shadowColor)
                                }
                            }
                        }
                    }
                }
            }

            // Draw character
            for (r in 0 until charH) {
                for (c in 0 until charW) {
                    if (glyph[r][c] == 1) {
                        for (dy in 0 until scale) {
                            for (dx in 0 until scale) {
                                setPixel(cursorX + c * scale + dx, startY + r * scale + dy, color)
                            }
                        }
                    }
                }
            }

            cursorX += charW * scale + scale
        }
    }
}
