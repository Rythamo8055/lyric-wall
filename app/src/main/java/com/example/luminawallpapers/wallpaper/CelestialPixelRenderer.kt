package com.example.luminawallpapers.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.luminawallpapers.util.IndianSeasonHelper
import com.example.luminawallpapers.util.LunarPhaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class WeatherMode(val label: String) {
    CLEAR_NIGHT("Clear Night"),
    CLOUDY("Cloudy Sky"),
    RAIN("Pixel Rain"),
    SNOW("Pixel Snow"),
    THUNDER("Storm")
}

class CelestialPixelRenderer {

    // 8-bit aesthetic virtual grid (180 x 390 for crisp, authentic 8-bit pixel proportions)
    val gridWidth = 180
    val gridHeight = 390

    var weatherMode = WeatherMode.CLEAR_NIGHT
    var showHud = true
    var showClock = true
    var showDate = true
    var showBattery = true
    var showWeatherTag = true
    var showIndianSeason = true
    var indianSeasonName = "SHARAD"
    var currentTemp = "22°C"
    var cityName = "Hyderabad"
    var hudPosition = "LEFT_RAIL" // "LEFT_RAIL", "CENTER", "TOP_RIGHT"
    var moonScale = 1.0f // Dynamic moon size (0.6f .. 1.6f)
    var lunarPhase = 0.5f // 0.0=New Moon, 0.125=Waxing Arc, 0.25=Half, 0.5=Full Moon, 0.75=Last Quarter, 0.875=Waning Arc
    var cloudSpeedScale = 1.0f
    var starSpeedScale = 1.0f

    var batteryPercent = 85
    var isCharging = false

    private val bgPaint = Paint().apply { isAntiAlias = false }
    private val pixelPaint = Paint().apply { isAntiAlias = false }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val tempRect = RectF()
    private val srcRect = Rect()

    // Base Moon: Upper right quadrant (x=124, y=86, baseRadius=28)
    private val baseMoonCenterX = 124
    private val baseMoonCenterY = 86
    private val baseMoonRadius = 28

    // Cached Moon Sprite for 0% CPU overhead
    private var cachedMoonBitmap: Bitmap? = null
    private var cachedMoonScale = -1f
    private var cachedLunarPhase = -1f
    private var cachedMoonRadius = 0

    // Cached Cloud Sprites
    private val cachedCloudBitmaps = mutableListOf<Bitmap>()
    private val cloudWidths = mutableListOf<Int>()
    private val cloudHeights = mutableListOf<Int>()

    // Cached HUD Bitmap for 0% CPU overhead (re-rendered only when clock/status changes)
    private var cachedHudBitmap: Bitmap? = null
    private var cachedHudKey: String = ""
    private val hudSrcRect = Rect()
    private val hudDstRect = RectF()

    // Pre-allocated buffers for zero GC allocations during render loop
    private val starPtsBright = FloatArray(400)
    private val starPtsDim = FloatArray(400)
    private val rainLinePts = FloatArray(400)
    private val starPaintBright = Paint().apply { isAntiAlias = false; strokeWidth = 2f }
    private val starPaintDim = Paint().apply { isAntiAlias = false; strokeWidth = 2f }
    private val rainPaint = Paint().apply { isAntiAlias = false; strokeWidth = 2f }

    // Twinkling Stars
    data class Star(
        val x: Int,
        val y: Int,
        val type: Int, // 0: single dot, 1: 4-pixel cross (+), 2: 2x2 micro cluster
        var phase: Float,
        val speed: Float,
        val baseBrightness: Float
    )

    private val stars = mutableListOf<Star>()

    // Puffy 8-Bit Pixel Clouds with sub-pixel float movement
    data class PuffyCloud(
        var x: Float,
        val y: Float,
        val templateIndex: Int,
        val speed: Float,
        val alpha: Int,
        val layer: Int
    )

    data class Lobe(
        val relX: Int,
        val relY: Int,
        val radius: Int
    )

    private val clouds = mutableListOf<PuffyCloud>()

    // Weather Particles
    data class WeatherParticle(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var speedX: Float,
        val length: Int,
        val color: Int
    )

    private val weatherParticles = mutableListOf<WeatherParticle>()

    // Interactive Shooting Stars
    data class ShootingStar(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var length: Float,
        var life: Float,
        val maxLife: Float
    )

    private val shootingStars = mutableListOf<ShootingStar>()

    // Touch Ripple effect on moon
    private var moonRippleRadius = 0f
    private var moonRippleAlpha = 0f

    // Time formatting
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private var lastUpdateTime = System.currentTimeMillis()

    init {
        initializeStars()
        initializeCachedCloudSprites()
        initializePuffyClouds()
        initializeWeatherParticles()
    }

    private fun initializeStars() {
        stars.clear()
        val rng = Random(1337)

        // 8-Bit Cross stars (+)
        val crossLocations = listOf(
            Pair(32, 38), Pair(68, 190), Pair(70, 300), Pair(155, 40),
            Pair(14, 138), Pair(160, 185), Pair(44, 98), Pair(148, 115),
            Pair(110, 38), Pair(22, 242), Pair(170, 268), Pair(90, 218),
            Pair(18, 340), Pair(162, 355), Pair(55, 360), Pair(140, 315)
        )
        crossLocations.forEach { (x, y) ->
            stars.add(
                Star(
                    x = x,
                    y = y,
                    type = 1,
                    phase = rng.nextFloat() * 6.28f,
                    speed = 0.8f + rng.nextFloat() * 1.0f,
                    baseBrightness = 0.8f + rng.nextFloat() * 0.2f
                )
            )
        }

        // Single pixel and micro-dot stars (.)
        for (i in 0 until 80) {
            val sx = rng.nextInt(4, gridWidth - 4)
            val sy = rng.nextInt(4, gridHeight - 4)

            val dx = sx - baseMoonCenterX
            val dy = sy - baseMoonCenterY
            if (dx * dx + dy * dy > (baseMoonRadius + 10) * (baseMoonRadius + 10)) {
                stars.add(
                    Star(
                        x = sx,
                        y = sy,
                        type = if (rng.nextInt(12) == 0) 2 else 0,
                        phase = rng.nextFloat() * 6.28f,
                        speed = 0.5f + rng.nextFloat() * 1.2f,
                        baseBrightness = 0.4f + rng.nextFloat() * 0.6f
                    )
                )
            }
        }
    }

    private fun initializeCachedCloudSprites() {
        cachedCloudBitmaps.clear()
        cloudWidths.clear()
        cloudHeights.clear()

        val templates = listOf(
            listOf(Lobe(0, 4, 7), Lobe(8, 0, 10), Lobe(16, 4, 8)),
            listOf(Lobe(0, 5, 8), Lobe(9, 1, 11), Lobe(20, 0, 13), Lobe(31, 5, 9)),
            listOf(Lobe(0, 6, 9), Lobe(10, 2, 12), Lobe(22, 0, 15), Lobe(35, 2, 13), Lobe(46, 6, 9))
        )

        for (tmpl in templates) {
            val minX = tmpl.minOf { it.relX - it.radius }
            val maxX = tmpl.maxOf { it.relX + it.radius }
            val minY = tmpl.minOf { it.relY - it.radius }
            val maxY = tmpl.maxOf { it.relY + it.radius }

            val width = maxX - minX + 1
            val height = maxY - minY + 1
            cloudWidths.add(width)
            cloudHeights.add(height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val p = Paint().apply { isAntiAlias = false }

            val highlight = Color.argb(255, 235, 235, 245)
            val bodyColor = Color.argb(255, 145, 145, 155)
            val shadowColor = Color.argb(255, 75, 75, 85)

            for (row in minY..maxY) {
                for (col in minX..maxX) {
                    var inside = false
                    var isTopHighlight = false
                    var isBottomShadow = (row >= maxY - 2)

                    for (lobe in tmpl) {
                        val ldx = col - lobe.relX
                        val ldy = row - lobe.relY
                        if (ldx * ldx + ldy * ldy <= lobe.radius * lobe.radius) {
                            inside = true
                            if (ldy <= -lobe.radius + 1) {
                                isTopHighlight = true
                            }
                        }
                    }

                    if (inside) {
                        p.color = when {
                            isTopHighlight -> highlight
                            isBottomShadow -> shadowColor
                            else -> bodyColor
                        }
                        val bx = (col - minX).toFloat()
                        val by = (row - minY).toFloat()
                        canvas.drawRect(bx, by, bx + 1f, by + 1f, p)
                    }
                }
            }
            cachedCloudBitmaps.add(bitmap)
        }
    }

    private fun initializePuffyClouds() {
        clouds.clear()
        val rng = Random(999)

        // Layer 0: Deep background clouds (darker, slower)
        for (i in 0 until 4) {
            val tmplIndex = rng.nextInt(cachedCloudBitmaps.size)
            clouds.add(
                PuffyCloud(
                    x = rng.nextInt(-40, gridWidth).toFloat(),
                    y = 50f + i * 80f + rng.nextInt(-15, 15),
                    templateIndex = tmplIndex,
                    speed = 0.6f + rng.nextFloat() * 0.4f,
                    alpha = 140,
                    layer = 0
                )
            )
        }

        // Layer 1: Foreground clouds (lighter, passing over moon)
        for (i in 0 until 3) {
            val tmplIndex = rng.nextInt(cachedCloudBitmaps.size)
            clouds.add(
                PuffyCloud(
                    x = rng.nextInt(-40, gridWidth).toFloat(),
                    y = 75f + i * 95f + rng.nextInt(-15, 15),
                    templateIndex = tmplIndex,
                    speed = 1.2f + rng.nextFloat() * 0.6f,
                    alpha = 210,
                    layer = 1
                )
            )
        }
    }

    private fun initializeWeatherParticles() {
        weatherParticles.clear()
        val rng = Random(888)
        for (i in 0 until 60) {
            weatherParticles.add(
                WeatherParticle(
                    x = rng.nextFloat() * gridWidth,
                    y = rng.nextFloat() * gridHeight,
                    speedY = 80f + rng.nextFloat() * 50f,
                    speedX = -8f + rng.nextFloat() * 4f,
                    length = 3 + rng.nextInt(3),
                    color = Color.rgb(180, 210, 240)
                )
            )
        }
    }

    fun onTouch(xNorm: Float, yNorm: Float) {
        val gx = (xNorm * gridWidth).toInt()
        val gy = (yNorm * gridHeight).toInt()

        val r = (baseMoonRadius * moonScale).toInt()
        val dx = gx - baseMoonCenterX
        val dy = gy - baseMoonCenterY
        if (dx * dx + dy * dy <= (r + 6) * (r + 6)) {
            moonRippleRadius = 1f
            moonRippleAlpha = 1f
            cycleWeather()
        } else {
            spawnShootingStar(gx.toFloat(), gy.toFloat())
        }
    }

    fun cycleWeather() {
        val modes = WeatherMode.values()
        val nextIndex = (weatherMode.ordinal + 1) % modes.size
        weatherMode = modes[nextIndex]
    }

    private fun spawnShootingStar(targetX: Float, targetY: Float) {
        val angle = 2.2f + (Random.nextFloat() - 0.5f) * 0.3f
        val speed = 140f + Random.nextFloat() * 50f
        val startDist = 50f
        val startX = targetX - cos(angle) * startDist
        val startY = targetY - sin(angle) * startDist

        shootingStars.add(
            ShootingStar(
                x = startX,
                y = startY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                length = 12f + Random.nextFloat() * 8f,
                life = 1f,
                maxLife = 0.7f + Random.nextFloat() * 0.3f
            )
        )
    }

    fun update(deltaTimeSec: Float) {
        // Update Stars twinkle with scale
        for (star in stars) {
            star.phase = (star.phase + star.speed * starSpeedScale * deltaTimeSec) % 6.283f
        }

        // Update Clouds with silky sub-pixel continuous drift
        for (cloud in clouds) {
            cloud.x += cloud.speed * cloudSpeedScale * deltaTimeSec
            val w = cloudWidths.getOrElse(cloud.templateIndex) { 40 }
            if (cloud.x > gridWidth + w) {
                cloud.x = -w - 30f
            }
        }

        // Update Weather Particles
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) {
            for (p in weatherParticles) {
                p.y += p.speedY * deltaTimeSec
                p.x += p.speedX * deltaTimeSec
                if (p.y > gridHeight) {
                    p.y = -5f
                    p.x = Random.nextFloat() * gridWidth
                }
            }
        } else if (weatherMode == WeatherMode.SNOW) {
            for (p in weatherParticles) {
                p.y += (p.speedY * 0.35f) * deltaTimeSec
                p.x += sin(p.y * 0.08f) * 10f * deltaTimeSec
                if (p.y > gridHeight) {
                    p.y = -4f
                    p.x = Random.nextFloat() * gridWidth
                }
            }
        }

        // Update Shooting Stars
        val it = shootingStars.iterator()
        while (it.hasNext()) {
            val ss = it.next()
            ss.x += ss.vx * deltaTimeSec
            ss.y += ss.vy * deltaTimeSec
            ss.life -= (1f / ss.maxLife) * deltaTimeSec
            if (ss.life <= 0f) {
                it.remove()
            }
        }

        // Update Moon Ripple
        if (moonRippleAlpha > 0f) {
            moonRippleRadius += 45f * deltaTimeSec
            moonRippleAlpha -= 1.3f * deltaTimeSec
            if (moonRippleAlpha < 0f) moonRippleAlpha = 0f
        }
    }

    fun render(canvas: Canvas, width: Int, height: Int) {
        val now = System.currentTimeMillis()
        val dt = ((now - lastUpdateTime) / 1000f).coerceIn(0.001f, 0.1f)
        lastUpdateTime = now
        update(dt)

        val px = width.toFloat() / gridWidth
        val py = height.toFloat() / gridHeight

        // 1. OLED Pure Pitch Black Background (0W on OLED display pixels)
        if (weatherMode == WeatherMode.THUNDER) {
            val isFlash = Random.nextInt(100) < 3
            bgPaint.color = if (isFlash) Color.rgb(45, 48, 65) else Color.BLACK
        } else {
            bgPaint.color = Color.BLACK
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw Stars (Batched Hardware-Accelerated Points)
        starPaintBright.strokeWidth = px
        starPaintDim.strokeWidth = px
        var brightCount = 0
        var dimCount = 0

        for (star in stars) {
            val twinkle = (sin(star.phase) * 0.5f + 0.5f)
            val brightness = (star.baseBrightness * 0.45f + twinkle * 0.55f).coerceIn(0.15f, 1.0f)
            val isBright = brightness > 0.6f

            val sx = star.x * px + px * 0.5f
            val sy = star.y * py + py * 0.5f

            if (isBright) {
                if (brightCount + 2 <= starPtsBright.size) {
                    starPtsBright[brightCount++] = sx
                    starPtsBright[brightCount++] = sy
                }
                if (star.type == 1 && twinkle > 0.4f && brightCount + 8 <= starPtsBright.size) {
                    // Cross arms (+)
                    starPtsBright[brightCount++] = (star.x - 1) * px + px * 0.5f
                    starPtsBright[brightCount++] = sy
                    starPtsBright[brightCount++] = (star.x + 1) * px + px * 0.5f
                    starPtsBright[brightCount++] = sy
                    starPtsBright[brightCount++] = sx
                    starPtsBright[brightCount++] = (star.y - 1) * py + py * 0.5f
                    starPtsBright[brightCount++] = sx
                    starPtsBright[brightCount++] = (star.y + 1) * py + py * 0.5f
                }
            } else {
                if (dimCount + 2 <= starPtsDim.size) {
                    starPtsDim[dimCount++] = sx
                    starPtsDim[dimCount++] = sy
                }
            }
        }

        if (brightCount > 0) {
            starPaintBright.color = Color.rgb(240, 240, 255)
            canvas.drawPoints(starPtsBright, 0, brightCount, starPaintBright)
        }
        if (dimCount > 0) {
            starPaintDim.color = Color.rgb(130, 135, 155)
            canvas.drawPoints(starPtsDim, 0, dimCount, starPaintDim)
        }

        // 3. Draw Dynamic Astronomical Moon (Terminator & Paksham Arc) with 0% CPU Cached Sprite
        val scaledRadius = (baseMoonRadius * moonScale).toInt().coerceIn(16, 50)
        drawCachedMoon(canvas, baseMoonCenterX, baseMoonCenterY, scaledRadius, px, py)

        // 4. Draw Touch Moon Ripple Ring
        if (moonRippleAlpha > 0f) {
            pixelPaint.color = Color.argb((moonRippleAlpha * 180).toInt(), 220, 240, 255)
            val rippleR = (scaledRadius + moonRippleRadius).toInt()
            for (angleStep in 0 until 40) {
                val ang = (angleStep / 40f) * 6.283f
                val rx = baseMoonCenterX + (cos(ang) * rippleR).toInt()
                val ry = baseMoonCenterY + (sin(ang) * rippleR).toInt()
                drawPixel(canvas, rx, ry, px, py)
            }
        }

        // 5. Draw Organic Puffy 8-Bit Clouds (1 draw call per cloud!)
        if (weatherMode != WeatherMode.CLEAR_NIGHT) {
            for (cloud in clouds) {
                val bmp = cachedCloudBitmaps.getOrNull(cloud.templateIndex) ?: continue
                val cw = cloudWidths[cloud.templateIndex]
                val ch = cloudHeights[cloud.templateIndex]

                val left = cloud.x * px
                val top = cloud.y * py
                val right = (cloud.x + cw) * px
                val bottom = (cloud.y + ch) * py

                bitmapPaint.alpha = cloud.alpha
                tempRect.set(left, top, right, bottom)
                srcRect.set(0, 0, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, srcRect, tempRect, bitmapPaint)
            }
        }

        // 6. Draw Weather Particles (Rain / Snow) with Hardware Batched Lines
        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.THUNDER) {
            rainPaint.color = Color.rgb(180, 210, 240)
            rainPaint.strokeWidth = px
            var lineIdx = 0
            for (p in weatherParticles) {
                if (lineIdx + 4 <= rainLinePts.size) {
                    val sx = p.x * px + px * 0.5f
                    val sy = p.y * py
                    rainLinePts[lineIdx++] = sx
                    rainLinePts[lineIdx++] = sy
                    rainLinePts[lineIdx++] = sx
                    rainLinePts[lineIdx++] = (p.y + p.length) * py
                }
            }
            if (lineIdx > 0) {
                canvas.drawLines(rainLinePts, 0, lineIdx, rainPaint)
            }
        } else if (weatherMode == WeatherMode.SNOW) {
            pixelPaint.color = Color.rgb(225, 235, 255)
            for (p in weatherParticles) {
                drawFloatPixel(canvas, p.x, p.y, px, py)
            }
        }

        // 7. Draw Shooting Stars with float trajectory
        for (ss in shootingStars) {
            val alpha = (ss.life * 255).toInt().coerceIn(0, 255)
            pixelPaint.color = Color.argb(alpha, 255, 255, 255)
            val segs = ss.length.toInt()
            for (s in 0 until segs) {
                val sx = ss.x - ss.vx * 0.03f * s
                val sy = ss.y - ss.vy * 0.03f * s
                drawFloatPixel(canvas, sx, sy, px, py)
            }
        }

        // 8. Productivity Retro HUD (Cached 1-Blit Draw)
        if (showHud) {
            drawCachedHud(canvas, px, py)
        }
    }

    private fun drawPixel(canvas: Canvas, gx: Int, gy: Int, px: Float, py: Float) {
        if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) return
        tempRect.set(gx * px, gy * py, (gx + 1) * px, (gy + 1) * py)
        canvas.drawRect(tempRect, pixelPaint)
    }

    private fun drawFloatPixel(canvas: Canvas, fx: Float, fy: Float, px: Float, py: Float) {
        if (fx < -5f || fx >= gridWidth + 5f || fy < -5f || fy >= gridHeight + 5f) return
        val sx = fx * px
        val sy = fy * py
        tempRect.set(sx, sy, sx + px, sy + py)
        canvas.drawRect(tempRect, pixelPaint)
    }

    /**
     * Cached 8-Bit Lunar Phase Renderer:
     * Generates moon texture once when scale or phase changes, then draws with 1 single hardware-accelerated call.
     */
    private fun drawCachedMoon(canvas: Canvas, cx: Int, cy: Int, r: Int, px: Float, py: Float) {
        if (cachedMoonBitmap == null || cachedMoonScale != moonScale || cachedLunarPhase != lunarPhase || cachedMoonRadius != r) {
            regenerateMoonBitmap(r)
        }

        val bmp = cachedMoonBitmap ?: return
        val left = (cx - r) * px
        val top = (cy - r) * py
        val right = (cx + r + 1) * px
        val bottom = (cy + r + 1) * py

        bitmapPaint.alpha = 255
        tempRect.set(left, top, right, bottom)
        srcRect.set(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, srcRect, tempRect, bitmapPaint)
    }

    private fun regenerateMoonBitmap(r: Int) {
        cachedMoonScale = moonScale
        cachedLunarPhase = lunarPhase
        cachedMoonRadius = r

        val size = 2 * r + 1
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val moonCanvas = Canvas(bmp)
        val p = Paint().apply { isAntiAlias = false }

        val highlightColor = Color.rgb(255, 255, 255)
        val lightColor = Color.rgb(220, 220, 220)
        val shadeColor = Color.rgb(172, 172, 172)
        val deepCraterColor = Color.rgb(120, 120, 120)

        val s = r / 28f
        val phaseAngle = (lunarPhase % 1.0f) * 6.28318530718f
        val isWaxing = (lunarPhase % 1.0f) < 0.5f

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

                        p.color = when {
                            isRimHighlight -> highlightColor
                            isCrater1 || isCrater2 || isCrater3 || isCrater4 -> deepCraterColor
                            isBottomShade -> shadeColor
                            else -> lightColor
                        }
                        val bx = (dx + r).toFloat()
                        val by = (dy + r).toFloat()
                        moonCanvas.drawRect(bx, by, bx + 1f, by + 1f, p)
                    } else {
                        if (distSq == r * r || distSq == (r - 1) * (r - 1)) {
                            p.color = Color.argb(30, 200, 200, 230)
                            val bx = (dx + r).toFloat()
                            val by = (dy + r).toFloat()
                            moonCanvas.drawRect(bx, by, bx + 1f, by + 1f, p)
                        }
                    }
                }
            }
        }
        cachedMoonBitmap = bmp
    }

    private fun drawCachedHud(canvas: Canvas, px: Float, py: Float) {
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now).uppercase()
        val battStr = "$batteryPercent%"

        val phaseType = LunarPhaseHelper.getPhaseType(lunarPhase)
        val phaseLabel = when (phaseType) {
            com.example.luminawallpapers.util.LunarPhaseType.NEW_MOON -> "NEW MOON"
            com.example.luminawallpapers.util.LunarPhaseType.WAXING_CRESCENT -> "WAXING CRESCENT"
            com.example.luminawallpapers.util.LunarPhaseType.FIRST_QUARTER -> "FIRST QUARTER"
            com.example.luminawallpapers.util.LunarPhaseType.WAXING_GIBBOUS -> "WAXING GIBBOUS"
            com.example.luminawallpapers.util.LunarPhaseType.FULL_MOON -> "FULL MOON"
            com.example.luminawallpapers.util.LunarPhaseType.WANING_GIBBOUS -> "WANING GIBBOUS"
            com.example.luminawallpapers.util.LunarPhaseType.THIRD_QUARTER -> "THIRD QUARTER"
            com.example.luminawallpapers.util.LunarPhaseType.WANING_CRESCENT -> "WANING CRESCENT"
        }

        val weatherCondName = when (weatherMode) {
            WeatherMode.CLEAR_NIGHT -> "CLEAR"
            WeatherMode.CLOUDY -> "CLOUDY"
            WeatherMode.RAIN -> "RAIN"
            WeatherMode.SNOW -> "SNOW"
            WeatherMode.THUNDER -> "STORM"
        }

        val locWeatherLine = if (showWeatherTag) "${cityName.uppercase()} $currentTemp • $weatherCondName" else ""
        val lunarLine = if (showWeatherTag) phaseLabel else ""

        val hudKey = "$timeStr|$dateStr|$battStr|$locWeatherLine|$lunarLine|$showClock|$showDate|$showBattery|$showWeatherTag|$hudPosition"

        if (cachedHudBitmap == null || cachedHudKey != hudKey) {
            regenerateHudBitmap(timeStr, dateStr, battStr, locWeatherLine, lunarLine, hudKey)
        }

        val bmp = cachedHudBitmap ?: return
        hudSrcRect.set(0, 0, bmp.width, bmp.height)
        hudDstRect.set(0f, 0f, gridWidth * px, gridHeight * py)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(bmp, hudSrcRect, hudDstRect, bitmapPaint)
    }

    private fun regenerateHudBitmap(
        timeStr: String,
        dateStr: String,
        battStr: String,
        locWeatherLine: String,
        lunarLine: String,
        key: String
    ) {
        cachedHudKey = key
        val bmp = cachedHudBitmap ?: Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val hudCanvas = Canvas(bmp)
        val p = Paint().apply { isAntiAlias = false }

        val (startX, startY) = when (hudPosition) {
            "CENTER" -> Pair(24, 180)
            "TOP_RIGHT" -> Pair(95, 36)
            else -> Pair(14, 145) // LEFT_RAIL
        }

        var curY = startY

        if (showWeatherTag && locWeatherLine.isNotEmpty()) {
            drawPixelTextToBitmap(hudCanvas, locWeatherLine, startX, curY, Color.rgb(180, 205, 230), p, scale = 1)
            curY += 8
            drawPixelTextToBitmap(hudCanvas, lunarLine, startX, curY, Color.rgb(160, 175, 200), p, scale = 1)
            curY += 12
        }

        if (showClock) {
            drawPixelTextToBitmap(hudCanvas, timeStr, startX, curY, Color.rgb(245, 245, 250), p, scale = 3)
            curY += 20
        }

        if (showDate) {
            drawPixelTextToBitmap(hudCanvas, dateStr, startX, curY, Color.rgb(160, 165, 180), p, scale = 1)
            curY += 12
        }

        if (showBattery) {
            drawPixelTextToBitmap(hudCanvas, "BAT $battStr", startX, curY, Color.rgb(160, 220, 170), p, scale = 1)
            drawPixelBatteryIconToBitmap(hudCanvas, startX + 48, curY - 1, p)
        }

        cachedHudBitmap = bmp
    }

    private fun drawPixelBatteryIconToBitmap(canvas: Canvas, gx: Int, gy: Int, p: Paint) {
        p.color = Color.rgb(160, 220, 170)
        for (x in 0..7) {
            canvas.drawRect(gx + x.toFloat(), gy.toFloat(), gx + x + 1f, gy + 1f, p)
            canvas.drawRect(gx + x.toFloat(), gy + 4f, gx + x + 1f, gy + 5f, p)
        }
        for (y in 0..4) {
            canvas.drawRect(gx.toFloat(), gy + y.toFloat(), gx + 1f, gy + y + 1f, p)
            canvas.drawRect(gx + 7f, gy + y.toFloat(), gx + 8f, gy + y + 1f, p)
        }
        canvas.drawRect(gx + 8f, gy + 1f, gx + 9f, gy + 4f, p)

        val filledWidth = (batteryPercent / 20).coerceIn(1, 5)
        for (f in 1..filledWidth) {
            canvas.drawRect(gx + f.toFloat(), gy + 1f, gx + f + 1f, gy + 4f, p)
        }
    }

    private fun drawPixelTextToBitmap(
        canvas: Canvas,
        text: String,
        startX: Int,
        startY: Int,
        color: Int,
        p: Paint,
        scale: Int = 1
    ) {
        p.color = color
        var cursorX = startX
        for (ch in text) {
            val glyph = PixelFont.getGlyph(ch)
            for (row in 0 until 5) {
                for (col in 0 until 3) {
                    if ((glyph[row] and (1 shl (2 - col))) != 0) {
                        val rx = (cursorX + col * scale).toFloat()
                        val ry = (startY + row * scale).toFloat()
                        canvas.drawRect(rx, ry, rx + scale, ry + scale, p)
                    }
                }
            }
            cursorX += (3 * scale) + scale
        }
    }
}

/**
 * Lightweight 3x5 Monospace Pixel Font for Retro HUD
 */
object PixelFont {
    private val fontMap = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111),
        ':' to intArrayOf(0b000, 0b010, 0b000, 0b010, 0b000),
        'A' to intArrayOf(0b010, 0b101, 0b111, 0b101, 0b101),
        'B' to intArrayOf(0b110, 0b101, 0b110, 0b101, 0b110),
        'C' to intArrayOf(0b111, 0b100, 0b100, 0b100, 0b111),
        'D' to intArrayOf(0b110, 0b101, 0b101, 0b101, 0b110),
        'E' to intArrayOf(0b111, 0b100, 0b110, 0b100, 0b111),
        'F' to intArrayOf(0b111, 0b100, 0b110, 0b100, 0b100),
        'G' to intArrayOf(0b111, 0b100, 0b101, 0b101, 0b111),
        'H' to intArrayOf(0b101, 0b101, 0b111, 0b101, 0b101),
        'I' to intArrayOf(0b111, 0b010, 0b010, 0b010, 0b111),
        'J' to intArrayOf(0b001, 0b001, 0b001, 0b101, 0b111),
        'K' to intArrayOf(0b101, 0b110, 0b100, 0b110, 0b101),
        'L' to intArrayOf(0b100, 0b100, 0b100, 0b100, 0b111),
        'M' to intArrayOf(0b101, 0b111, 0b101, 0b101, 0b101),
        'N' to intArrayOf(0b110, 0b101, 0b101, 0b101, 0b101),
        'O' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        'P' to intArrayOf(0b111, 0b101, 0b111, 0b100, 0b100),
        'Q' to intArrayOf(0b111, 0b101, 0b101, 0b111, 0b001),
        'R' to intArrayOf(0b110, 0b101, 0b110, 0b101, 0b101),
        'S' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        'T' to intArrayOf(0b111, 0b010, 0b010, 0b010, 0b010),
        'U' to intArrayOf(0b101, 0b101, 0b101, 0b101, 0b111),
        'V' to intArrayOf(0b101, 0b101, 0b101, 0b101, 0b010),
        'W' to intArrayOf(0b101, 0b101, 0b101, 0b111, 0b101),
        'X' to intArrayOf(0b101, 0b101, 0b010, 0b101, 0b101),
        'Y' to intArrayOf(0b101, 0b101, 0b010, 0b010, 0b010),
        'Z' to intArrayOf(0b111, 0b001, 0b010, 0b100, 0b111),
        '•' to intArrayOf(0b000, 0b010, 0b010, 0b000, 0b000),
        '%' to intArrayOf(0b101, 0b001, 0b010, 0b100, 0b101),
        '°' to intArrayOf(0b110, 0b110, 0b000, 0b000, 0b000),
        ',' to intArrayOf(0b000, 0b000, 0b000, 0b010, 0b100),
        '-' to intArrayOf(0b000, 0b000, 0b111, 0b000, 0b000),
        ' ' to intArrayOf(0b000, 0b000, 0b000, 0b000, 0b000)
    )

    fun getGlyph(ch: Char): IntArray {
        return fontMap[ch.uppercaseChar()] ?: fontMap[' ']!!
    }
}
