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
import com.example.luminawallpapers.util.LunarPhaseHelper
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

    var currentTheme = RY01Theme.DYNAMIC_TIME
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

    // Astronomical Lunar Phase (0.0 to 1.0, synced with real astronomical ephemeris)
    var lunarPhase = LunarPhaseHelper.getCurrentLunarPhase()

    // Day/Night Engine & Smooth Cross-Fading (0.0 = Pure Day, 0.5 = Sunset/Dusk, 1.0 = Pure Starry Night)
    var currentNightFactor = 0f
    var previewStep = 0 // 0: Auto (real-time clock), 1: Day, 2: Sunset, 3: Night

    // Celestial body settings (STATIONARY locked at 124, 86 for 0% drift between Sun and Moon)
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

    // Daytime Sparkles
    data class Sparkle(val x: Int, val y: Int, var phase: Float, val speed: Float)
    private val sparkles = mutableListOf<Sparkle>()

    // Nighttime 65-Star Dynamic Retro Starfield
    data class RetroStar(val x: Int, val y: Int, val phase: Float, val speed: Float, val isCross: Boolean)
    private val stars = mutableListOf<RetroStar>()

    // Birds / Swallows (Daytime)
    data class Bird(val startX: Float, val startY: Float, var flapPhase: Float)
    private val birds = listOf(
        Bird(42f, 55f, 0.0f),
        Bird(49f, 60f, 0.5f),
        Bird(55f, 63f, 1.0f),
        Bird(82f, 115f, 1.5f),
        Bird(89f, 120f, 2.0f)
    )

    // Fireflies (Nighttime)
    data class Firefly(var x: Float, var y: Float, val vx: Float, val vy: Float, var phase: Float, val pulseSpeed: Float)
    private val fireflies = mutableListOf<Firefly>()

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

        // Daytime Sparkles
        val sparkleCoords = listOf(
            Pair(32, 38), Pair(68, 190), Pair(155, 40), Pair(14, 138),
            Pair(160, 185), Pair(22, 242), Pair(18, 340), Pair(110, 48),
            Pair(75, 295), Pair(145, 330), Pair(85, 230), Pair(170, 120)
        )
        val rng = Random(42)
        for ((sx, sy) in sparkleCoords) {
            sparkles.add(Sparkle(sx, sy, rng.nextFloat() * 6.28f, 1.2f + rng.nextFloat() * 1.5f))
        }

        // Nighttime Starfield (65 procedural stars across the upper sky)
        val starRng = Random(1337)
        for (i in 0 until 65) {
            val sx = starRng.nextInt(3, gridWidth - 3)
            val sy = starRng.nextInt(4, 175)
            val dSunSq = (sx - sunCenterX) * (sx - sunCenterX) + (sy - sunCenterY) * (sy - sunCenterY)
            if (dSunSq > (sunRadius + 4) * (sunRadius + 4)) {
                stars.add(RetroStar(sx, sy, starRng.nextFloat() * 6.28f, 0.8f + starRng.nextFloat() * 1.6f, starRng.nextFloat() < 0.22f))
            }
        }

        // Nighttime Fireflies
        for (i in 0 until 16) {
            fireflies.add(Firefly(
                x = starRng.nextFloat() * (gridWidth - 20) + 10f,
                y = starRng.nextFloat() * 180f + 165f,
                vx = (starRng.nextFloat() - 0.5f) * 7f,
                vy = (starRng.nextFloat() - 0.5f) * 5f,
                phase = starRng.nextFloat() * 6.28f,
                pulseSpeed = 1.2f + starRng.nextFloat() * 1.8f
            ))
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
            RY01Theme.DYNAMIC_TIME
        }
        waterGlasses = settings.ry01WaterGlasses
        waterGoal = settings.ry01WaterGoal
        countdownLabel = settings.ry01CountdownLabel
        countdownDays = settings.ry01CountdownDays
        telemetryMode = settings.ry01TelemetryMode
        cityName = settings.cityName
        currentTemp = settings.currentTemp
        weatherMode = settings.weatherMode

        // Real Astronomical Lunar Phase sync
        lunarPhase = if (settings.autoLunarPhase) {
            LunarPhaseHelper.getCurrentLunarPhase()
        } else {
            settings.lunarPhaseFraction
        }

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

        // 3. Big Clock (x: 10..125, y: 164..188) -> Cycle Color Theme
        if (gx in 10..125 && gy in 164..188) {
            val themes = RY01Theme.values()
            val nextIdx = (currentTheme.ordinal + 1) % themes.size
            currentTheme = themes[nextIdx]
            vibrateTick(ctx)
            ctx?.let { persistSettings(it) }
            return true
        }

        // 4. Water Habit Tracker (x: 10..115, y: 202..218)
        if (gx in 10..115 && gy in 202..218) {
            waterGlasses = if (waterGlasses >= waterGoal) 0 else waterGlasses + 1
            vibrateTick(ctx)
            ctx?.let { persistSettings(it) }
            return true
        }

        // 5. Stationary Celestial Body Touch Zone (dx*dx + dy*dy <= 34*34)
        // Single tap cycles Day/Sunset/Night preview and Dynamic Auto!
        val sDx = gx - sunCenterX
        val sDy = gy - sunCenterY
        if (sDx * sDx + sDy * sDy <= 34 * 34) {
            previewStep = (previewStep + 1) % 4
            vibrateTick(ctx)
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

    private fun getTargetNightFactor(): Float {
        return when (previewStep) {
            1 -> 0.0f  // Forced Daytime
            2 -> 0.5f  // Forced Sunset / Dusk
            3 -> 1.0f  // Forced Deep Starry Night
            else -> {
                // Dynamic time calculation based on real system clock
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val timeMinutes = hour * 60 + minute

                when {
                    // Dawn: 05:00 (300m) to 07:00 (420m) -> 1.0 down to 0.0
                    timeMinutes in 300..420 -> (420 - timeMinutes) / 120.0f
                    // Day: 07:01 to 17:29 -> 0.0
                    timeMinutes in 421..1049 -> 0.0f
                    // Dusk / Sunset: 17:30 (1050m) to 19:30 (1170m) -> 0.0 up to 1.0
                    timeMinutes in 1050..1170 -> (timeMinutes - 1050) / 120.0f
                    // Deep Night: 19:31 to 04:59 -> 1.0
                    else -> 1.0f
                }
            }
        }
    }

    fun update(dtSec: Float) {
        animTime += dtSec

        // Butter-smooth transition between Day and Night
        val targetNight = getTargetNightFactor()
        val lerpSpeed = if (previewStep != 0) 5.0f else 1.8f
        currentNightFactor += (targetNight - currentNightFactor) * (dtSec * lerpSpeed).coerceAtMost(1f)

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

        // Nighttime Fireflies drift & bob
        if (currentNightFactor > 0.1f) {
            for (f in fireflies) {
                f.x += f.vx * dtSec
                f.y += f.vy * dtSec + sin(animTime * 2.0f + f.phase) * 0.15f
                if (f.x < 5f) f.x = (gridWidth - 10).toFloat()
                if (f.x > gridWidth - 5) f.x = 10f
                if (f.y < 160f) f.y = 350f
                if (f.y > 365f) f.y = 170f
            }
        }

        // Rain/Snow particles
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) {
            for (p in weatherDrops) {
                p.y += p.speedY * dtSec
                p.x += p.speedX * dtSec
                if (p.y > gridHeight - 6) {
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
                s.vy += 80f * dtSec
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

        // 1. Generate Bayer Dithered Sky Gradient (Seamless Day-Sunset-Night Cross-Fade)
        renderSkyGradient()

        // 2. Dynamic 65-Star Starfield (Fades in smoothly at Night)
        renderStarfield()

        // 3. Stationary Celestial Body (0% Drift Sun morphing into 3D Lunar Moon at 124, 86)
        renderCelestialBody()

        // 4. Adaptable Clouds (Day peach/white transitioning to Moonlit silver/indigo)
        renderClouds()

        // 5. Swallows (Day) & Fireflies (Night)
        renderSwallows()
        renderFireflies()
        renderSparkles()

        // 6. Rain/Snow Particles
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER || weatherMode == WeatherMode.SNOW) {
            renderWeatherParticles()
        }

        // Thunder flash
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

        // 7. Auto-Contrast Productivity OLED HUD
        renderHUD()

        // Write pixel buffer into virtual bitmap
        virtualBitmap.setPixels(pixelBuffer, 0, gridWidth, 0, 0, gridWidth, gridHeight)

        // Scale up to device screen using nearest-neighbor (0% blur, razor-sharp pixels)
        dstRect.set(0f, 0f, screenW.toFloat(), screenH.toFloat())
        canvas.drawBitmap(virtualBitmap, srcRect, dstRect, scalingPaint)
    }

    private data class ColorStop(val pos: Float, val r: Int, val g: Int, val b: Int)

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val a1 = (c1 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 ushr 24) and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        val a = (a1 + (a2 - a1) * t).toInt().coerceIn(0, 255)
        val r = (r1 + (r2 - r1) * t).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpStops(s1: List<ColorStop>, s2: List<ColorStop>, t: Float): List<ColorStop> {
        val result = mutableListOf<ColorStop>()
        val count = minOf(s1.size, s2.size)
        for (i in 0 until count) {
            val a = s1[i]
            val b = s2[i]
            val pos = a.pos + (b.pos - a.pos) * t
            val r = (a.r + (b.r - a.r) * t).toInt().coerceIn(0, 255)
            val g = (a.g + (b.g - a.g) * t).toInt().coerceIn(0, 255)
            val bl = (a.b + (b.b - a.b) * t).toInt().coerceIn(0, 255)
            result.add(ColorStop(pos, r, g, bl))
        }
        return result
    }

    private fun getSkyStops(): List<ColorStop> {
        if (weatherMode == WeatherMode.RAIN) {
            return listOf(
                ColorStop(0.00f, 108, 120, 150),
                ColorStop(0.25f, 140, 150, 178),
                ColorStop(0.52f, 174, 182, 204),
                ColorStop(0.75f, 204, 210, 224),
                ColorStop(0.90f, 224, 228, 236),
                ColorStop(1.00f, 236, 240, 246)
            )
        } else if (weatherMode == WeatherMode.THUNDER) {
            return listOf(
                ColorStop(0.00f, 48, 52, 75),
                ColorStop(0.30f, 78, 84, 110),
                ColorStop(0.60f, 118, 124, 150),
                ColorStop(0.85f, 158, 164, 185),
                ColorStop(1.00f, 188, 194, 210)
            )
        } else if (weatherMode == WeatherMode.SNOW) {
            return listOf(
                ColorStop(0.00f, 155, 175, 205),
                ColorStop(0.35f, 185, 200, 222),
                ColorStop(0.70f, 215, 228, 242),
                ColorStop(1.00f, 240, 246, 255)
            )
        }

        // Defined Palettes
        val peachStops = listOf(
            ColorStop(0.00f, 162, 172, 222),
            ColorStop(0.24f, 202, 168, 206),
            ColorStop(0.48f, 238, 178, 186),
            ColorStop(0.70f, 254, 202, 168),
            ColorStop(0.88f, 255, 226, 182),
            ColorStop(1.00f, 255, 242, 208)
        )
        val lavenderStops = listOf(
            ColorStop(0.00f, 142, 150, 210),
            ColorStop(0.30f, 175, 160, 215),
            ColorStop(0.60f, 210, 185, 225),
            ColorStop(0.85f, 235, 205, 220),
            ColorStop(1.00f, 250, 225, 215)
        )
        val matchaStops = listOf(
            ColorStop(0.00f, 148, 185, 172),
            ColorStop(0.35f, 185, 210, 175),
            ColorStop(0.65f, 220, 225, 180),
            ColorStop(0.85f, 245, 230, 185),
            ColorStop(1.00f, 255, 242, 210)
        )

        // Sunset / Dusk Palette
        val sunsetStops = listOf(
            ColorStop(0.00f, 85, 80, 145),
            ColorStop(0.24f, 155, 95, 135),
            ColorStop(0.48f, 230, 120, 110),
            ColorStop(0.70f, 250, 175, 120),
            ColorStop(0.88f, 255, 205, 140),
            ColorStop(1.00f, 255, 225, 165)
        )

        // Deep Starry Midnight Palette
        val nightStops = listOf(
            ColorStop(0.00f, 6, 8, 22),
            ColorStop(0.24f, 14, 18, 38),
            ColorStop(0.48f, 25, 28, 54),
            ColorStop(0.70f, 38, 40, 68),
            ColorStop(0.88f, 48, 50, 78),
            ColorStop(1.00f, 62, 60, 88)
        )

        val baseDayStops = when (currentTheme) {
            RY01Theme.COZY_PEACH -> peachStops
            RY01Theme.LAVENDER_HAZE -> lavenderStops
            RY01Theme.MATCHA_HONEY -> matchaStops
            RY01Theme.DYNAMIC_TIME -> peachStops
        }

        // Seamless 3-way interpolation: Day -> Sunset -> Night
        return if (currentNightFactor <= 0.5f) {
            val t = currentNightFactor * 2f
            lerpStops(baseDayStops, sunsetStops, t)
        } else {
            val t = (currentNightFactor - 0.5f) * 2f
            lerpStops(sunsetStops, nightStops, t)
        }
    }

    private fun renderSkyGradient() {
        val stops = getSkyStops()

        for (y in 0 until gridHeight) {
            val t = y.toFloat() / (gridHeight - 1)

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

    // -------------------------------------------------------------
    // Starfield & Fireflies
    // -------------------------------------------------------------

    private fun renderStarfield() {
        if (currentNightFactor <= 0.05f) return
        for (s in stars) {
            val twinkle = (sin(animTime * s.speed + s.phase) * 0.45f + 0.55f) * currentNightFactor
            if (twinkle > 0.20f) {
                val a = (twinkle * 255).toInt().coerceIn(0, 255)
                val starCol = if (twinkle > 0.85f) Color.argb(a, 255, 255, 255) else Color.argb(a, 205, 222, 255)
                setPixel(s.x, s.y, starCol)
                if (s.isCross && twinkle > 0.70f) {
                    val dimCross = Color.argb((a * 0.60f).toInt(), 190, 215, 255)
                    setPixel(s.x - 1, s.y, dimCross)
                    setPixel(s.x + 1, s.y, dimCross)
                    setPixel(s.x, s.y - 1, dimCross)
                    setPixel(s.x, s.y + 1, dimCross)
                }
            }
        }
    }

    private fun renderFireflies() {
        if (currentNightFactor <= 0.20f) return
        val ffAlpha = ((currentNightFactor - 0.20f) / 0.80f).coerceIn(0f, 1f)
        for (f in fireflies) {
            val pulse = (sin(animTime * f.pulseSpeed + f.phase) * 0.5f + 0.5f) * ffAlpha
            if (pulse > 0.15f) {
                val a = (pulse * 255).toInt().coerceIn(0, 255)
                val ffCol = Color.argb(a, 225, 255, 130)
                val fx = f.x.toInt()
                val fy = f.y.toInt()
                setPixel(fx, fy, ffCol)
                if (pulse > 0.72f) {
                    val aura = Color.argb((a * 0.4f).toInt(), 180, 240, 100)
                    setPixel(fx - 1, fy, aura)
                    setPixel(fx + 1, fy, aura)
                    setPixel(fx, fy - 1, aura)
                    setPixel(fx, fy + 1, aura)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Stationary Celestial Morphing (Sun <-> Moon at 124, 86)
    // -------------------------------------------------------------

    private fun renderCelestialBody() {
        // Daytime Sun (fades out as currentNightFactor -> 1.0)
        if (currentNightFactor < 0.98f) {
            renderSun(1f - currentNightFactor)
        }
        // Nighttime 3D Moon with Astronomical Terminator & Craters (fades in as currentNightFactor -> 1.0)
        if (currentNightFactor > 0.02f) {
            renderMoon(currentNightFactor)
        }
    }

    private fun renderSun(alphaRatio: Float) {
        val aInt = (alphaRatio * 255).toInt().coerceIn(0, 255)
        val sunCore = Color.argb(aInt, 255, 255, 255)
        val sunInner = Color.argb(aInt, 255, 250, 218)
        val sunMid = Color.argb(aInt, 255, 228, 162)
        val sunRim = Color.argb(aInt, 254, 194, 142)
        val sunAura = Color.argb((alphaRatio * 200).toInt(), 255, 225, 170)
        val sunRay = Color.argb((alphaRatio * 220).toInt(), 255, 245, 210)

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

    private fun renderMoon(alphaRatio: Float) {
        val aInt = (alphaRatio * 255).toInt().coerceIn(0, 255)
        val r = sunRadius
        val s = 1.0f

        val phaseAngle = (lunarPhase % 1.0f) * 6.28318530718f
        val isWaxing = (lunarPhase % 1.0f) < 0.5f

        val highlightColor = Color.argb(aInt, 255, 255, 255)
        val lightColor = Color.argb(aInt, 230, 238, 252)
        val shadeColor = Color.argb(aInt, 170, 180, 204)
        val deepCraterColor = Color.argb(aInt, 115, 125, 150)
        val moonAura = Color.argb((alphaRatio * 85).toInt(), 170, 205, 250)
        val moonGlow = Color.argb((alphaRatio * 45).toInt(), 140, 180, 240)

        // Concentric Moonlit Starlight Halo Rings
        for (haloR in intArrayOf(r + 5, r + 13, r + 22)) {
            val minD = (haloR - 1) * (haloR - 1)
            val maxD = haloR * haloR
            val auraColor = if (haloR == r + 5) moonAura else moonGlow
            for (dy in -haloR..haloR) {
                for (dx in -haloR..haloR) {
                    val dSq = dx * dx + dy * dy
                    if (dSq in minD..maxD && (dx + dy) % 3 == 0) {
                        setPixel(sunCenterX + dx, sunCenterY + dy, auraColor)
                    }
                }
            }
        }

        // Procedural 3D Spherical Moon Disk with Authentic Lunar Terminator & 4 Craters
        for (dy in -r..r) {
            val dySq = dy * dy
            if (dySq > r * r) continue

            val halfWidth = sqrt((r * r - dySq).toDouble()).toFloat()
            if (halfWidth <= 0.01f) continue

            for (dx in -r..r) {
                val distSq = dx * dx + dySq
                if (distSq <= r * r) {
                    val u = (dx / halfWidth).coerceIn(-1.0f, 1.0f)

                    val isIlluminated = if (isWaxing) {
                        u >= cos(phaseAngle)
                    } else {
                        u <= -cos(phaseAngle)
                    }

                    if (isIlluminated) {
                        val isRimHighlight = (isWaxing && dx > r * 0.7f && dy < 0) || (!isWaxing && dx < -r * 0.7f && dy < 0)
                        val isBottomShade = dy > r * 0.55f || (isWaxing && dx < 0 && dy > 0) || (!isWaxing && dx > 0 && dy > 0)

                        // 4 Procedural Lunar Craters
                        val c1x = (dx + 8 * s)
                        val c1y = (dy + 5 * s)
                        val isCrater1 = (c1x * c1x + c1y * c1y) <= (5 * s) * (5 * s)

                        val c2x = (dx - 7 * s)
                        val c2y = (dy + 12 * s)
                        val isCrater2 = (c2x * c2x + c2y * c2y) <= (4 * s) * (4 * s)

                        val c3x = (dx + 11 * s)
                        val c3y = (dy - 9 * s)
                        val isCrater3 = (c3x * c3x + c3y * c3y) <= (3.5 * s) * (3.5 * s)

                        val c4x = (dx - 12 * s)
                        val c4y = (dy - 6 * s)
                        val isCrater4 = (c4x * c4x + c4y * c4y) <= (3 * s) * (3 * s)

                        val col = when {
                            isRimHighlight -> highlightColor
                            isCrater1 || isCrater2 || isCrater3 || isCrater4 -> deepCraterColor
                            isBottomShade -> shadeColor
                            else -> lightColor
                        }
                        setPixel(sunCenterX + dx, sunCenterY + dy, col)
                    } else {
                        // Soft 8-bit unlit lunar rim for true spherical volume
                        if (distSq == r * r || distSq == (r - 1) * (r - 1)) {
                            setPixel(sunCenterX + dx, sunCenterY + dy, Color.argb((alphaRatio * 35).toInt(), 180, 200, 240))
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Clouds
    // -------------------------------------------------------------

    private fun renderClouds() {
        val isRainy = weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER
        val isSnow = weatherMode == WeatherMode.SNOW

        val cloudHiDay = when {
            isRainy -> Color.argb(255, 224, 232, 244)
            isSnow -> Color.argb(255, 248, 252, 255)
            else -> Color.argb(255, 255, 255, 255)
        }
        val cloudBodyDay = when {
            isRainy -> Color.argb(248, 178, 190, 210)
            isSnow -> Color.argb(248, 220, 230, 245)
            else -> Color.argb(248, 254, 238, 236)
        }
        val cloudShdwDay = when {
            isRainy -> Color.argb(235, 134, 146, 172)
            isSnow -> Color.argb(235, 185, 200, 222)
            else -> Color.argb(235, 215, 192, 218)
        }

        val cloudHiNight = Color.argb(255, 215, 230, 255)
        val cloudBodyNight = Color.argb(240, 48, 56, 82)
        val cloudShdwNight = Color.argb(235, 30, 35, 54)

        val cloudHi = lerpColor(cloudHiDay, cloudHiNight, currentNightFactor)
        val cloudBody = lerpColor(cloudBodyDay, cloudBodyNight, currentNightFactor)
        val cloudShdw = lerpColor(cloudShdwDay, cloudShdwNight, currentNightFactor)

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
        val swallowAlpha = ((0.65f - currentNightFactor) / 0.65f).coerceIn(0f, 1f)
        if (swallowAlpha <= 0.02f) return
        val birdCol = Color.argb((swallowAlpha * 240).toInt(), 118, 98, 138)
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
        val sparkleAlpha = ((0.70f - currentNightFactor) / 0.70f).coerceIn(0f, 1f)
        if (sparkleAlpha <= 0.02f) return

        for (s in sparkles) {
            val twinkle = (sin(s.phase) * 0.5f + 0.5f) * sparkleAlpha
            if (twinkle > 0.4f) {
                val a = (twinkle * 255).toInt().coerceIn(0, 255)
                val col = if (twinkle > 0.8f) Color.argb(a, 255, 255, 255) else Color.argb((a * 0.86f).toInt(), 255, 235, 175)
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

        // Seamless Auto-Contrast Color Morphing from Day Plum to Night Luminous Starlight
        val hudMainDay = Color.argb(255, 60, 48, 80)
        val hudSubDay = Color.argb(240, 95, 78, 115)
        val hudAccentDay = Color.argb(255, 205, 95, 112)
        val hudWaterDay = Color.argb(255, 79, 175, 219)
        val hudShadowDay = Color.argb(180, 255, 242, 235)

        val hudMainNight = Color.argb(255, 248, 250, 255)
        val hudSubNight = Color.argb(240, 175, 192, 225)
        val hudAccentNight = Color.argb(255, 255, 135, 150)
        val hudWaterNight = Color.argb(255, 105, 205, 255)
        val hudShadowNight = Color.argb(220, 6, 8, 16)

        // S-curve contrast transition so text never loses contrast in twilight
        val textT = if (currentNightFactor < 0.35f) {
            0.0f
        } else if (currentNightFactor > 0.65f) {
            1.0f
        } else {
            (currentNightFactor - 0.35f) / 0.30f
        }

        val hudMain = lerpColor(hudMainDay, hudMainNight, textT)
        val hudSub = lerpColor(hudSubDay, hudSubNight, textT)
        val hudAccent = lerpColor(hudAccentDay, hudAccentNight, textT)
        val hudWater = lerpColor(hudWaterDay, hudWaterNight, textT)
        val hudShadow = lerpColor(hudShadowDay, hudShadowNight, textT)

        // Line 1: Weather & Telemetry (Single Tap to cycle)
        val teleText = when (telemetryMode) {
            0 -> if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) "UV 1 LOW" else if (currentNightFactor > 0.5f) "UV 0 NIGHT" else "UV 3 MOD"
            1 -> if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) "AQI 22 FRESH" else "AQI 42 GOOD"
            else -> if (weatherMode == WeatherMode.RAIN) "RAIN 85%" else if (weatherMode == WeatherMode.THUNDER) "STORM 95%" else if (weatherMode == WeatherMode.SNOW) "SNOW 80%" else "RAIN 10%"
        }
        val weatherTag = when (weatherMode) {
            WeatherMode.RAIN -> "RAIN"
            WeatherMode.THUNDER -> "STORM"
            WeatherMode.SNOW -> "SNOW"
            WeatherMode.CLOUDY -> "CLOUDY"
            WeatherMode.CLEAR_NIGHT -> if (currentNightFactor > 0.5f) "MOON" else "CLEAR"
        }
        val previewTag = when (previewStep) {
            1 -> " [DAY]"
            2 -> " [DUSK]"
            3 -> " [NIGHT]"
            else -> ""
        }
        val line1 = "${cityName.take(3).uppercase()} $currentTemp $weatherTag$previewTag • $teleText"
        drawText(line1, hudX, hudY, hudSub, 1, hudShadow)

        // Line 2: Custom Countdown (Single Tap to toggle days vs hours)
        val candleCol = lerpColor(Color.argb(255, 255, 215, 100), Color.argb(255, 255, 235, 140), currentNightFactor)
        drawCakeIcon(hudX, hudY + 9, hudAccent, candleCol)
        val countText = if (showHoursCountdown) {
            "${countdownLabel.uppercase()} IN ${countdownDays * 24}H"
        } else {
            "${countdownLabel.uppercase()} IN $countdownDays DAYS"
        }
        drawText(countText, hudX + 8, hudY + 10, hudAccent, 1, hudShadow)

        // Line 3: Big Monospace Clock (HH:mm) - Tap to cycle themes!
        val currentTimeStr = timeFormat.format(Date())
        drawText(currentTimeStr, hudX, hudY + 22, hudMain, 3, hudShadow)

        // Line 4: Date & Moon Phase label at night
        val currentDateStr = dateFormat.format(Date()).uppercase()
        val line4 = if (currentNightFactor > 0.6f) {
            val phaseLabel = LunarPhaseHelper.getPhaseType(lunarPhase).label.uppercase()
            "$currentDateStr • $phaseLabel"
        } else {
            currentDateStr
        }
        drawText(line4, hudX, hudY + 42, hudSub, 1, hudShadow)

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
