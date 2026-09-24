package com.example.luminawallpapers.ui.screens

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.luminawallpapers.data.LiveWallpaperSettings
import com.example.luminawallpapers.data.WeatherRepository
import com.example.luminawallpapers.service.LuminaLiveWallpaperService
import androidx.compose.material.icons.rounded.Smartphone
import com.example.luminawallpapers.ui.components.ApplyWallpaperSheet
import com.example.luminawallpapers.util.IndianSeason
import com.example.luminawallpapers.util.IndianSeasonHelper
import com.example.luminawallpapers.util.LocationHelper
import com.example.luminawallpapers.util.LunarPhaseHelper
import com.example.luminawallpapers.util.UsageStatsHelper
import com.example.luminawallpapers.util.WallpaperHelper
import com.example.luminawallpapers.util.WallpaperTarget
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Schedule
import com.example.luminawallpapers.wallpaper.CelestialPixelRenderer
import com.example.luminawallpapers.wallpaper.CosmicWildernessRenderer
import com.example.luminawallpapers.wallpaper.LY02ProductivityMode
import com.example.luminawallpapers.wallpaper.WeatherMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWallpaperPreviewScreen(
    wallpaperId: String = "w1",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { LiveWallpaperSettings(context) }

    val isLy02 = WallpaperHelper.isLy02(wallpaperId)

    val celestialRenderer = remember {
        CelestialPixelRenderer().apply {
            val lower = wallpaperId.lowercase()
            when {
                lower.contains("varsha") || lower == "w2" -> {
                    indianSeasonName = "VARSHA"
                    weatherMode = WeatherMode.RAIN
                }
                lower.contains("shishira") || lower == "w4" -> {
                    indianSeasonName = "SHISHIRA"
                    weatherMode = WeatherMode.SNOW
                }
                lower.contains("vasanta") || lower == "w5" -> {
                    indianSeasonName = "VASANTA"
                    weatherMode = WeatherMode.CLEAR_NIGHT
                }
                lower.contains("sharad") || lower == "w3" -> {
                    indianSeasonName = "SHARAD"
                    weatherMode = WeatherMode.CLEAR_NIGHT
                }
                else -> {
                    weatherMode = settings.weatherMode
                    indianSeasonName = settings.currentIndianSeason.name
                }
            }
            moonScale = settings.moonSizeScale
            lunarPhase = settings.lunarPhaseFraction
            showClock = settings.showClock
            showDate = settings.showDate
            showBattery = settings.showBattery
            showWeatherTag = settings.showWeatherTag
            showIndianSeason = settings.showIndianSeason
            cityName = settings.cityName
            currentTemp = settings.currentTemp
            hudPosition = settings.hudPosition
            cloudSpeedScale = settings.cloudSpeedScale
            starSpeedScale = settings.starSpeedScale
        }
    }

    val cosmicRenderer = remember {
        CosmicWildernessRenderer(context).apply {
            showProductivityHud = settings.showProductivityHud
            showScreenTime = settings.showScreenTime
            showUnlocks = settings.showUnlocks
            showTopApps = settings.showTopApps
            starSpeedScale = settings.starSpeedScale
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var showApplySheet by remember { mutableStateOf(false) }
    var isApplyingStatic by remember { mutableStateOf(false) }

    // LY01 Dynamic State
    var moonScaleState by remember { mutableFloatStateOf(settings.moonSizeScale) }
    var autoLunarPhaseState by remember { mutableStateOf(settings.autoLunarPhase) }
    var lunarPhaseSlider by remember { mutableFloatStateOf(settings.lunarPhaseFraction) }
    var selectedWeather by remember { mutableStateOf(settings.weatherMode) }
    var clockEnabled by remember { mutableStateOf(settings.showClock) }
    var dateEnabled by remember { mutableStateOf(settings.showDate) }
    var batteryEnabled by remember { mutableStateOf(settings.showBattery) }
    var weatherTagEnabled by remember { mutableStateOf(settings.showWeatherTag) }
    var indianSeasonEnabled by remember { mutableStateOf(settings.showIndianSeason) }
    var selectedSeasonMode by remember { mutableStateOf(settings.indianSeasonMode) }
    var ultraBatterySaver by remember { mutableStateOf(settings.ultraBatterySaver) }
    var autoWeather by remember { mutableStateOf(settings.autoWeatherEnabled) }
    var useGps by remember { mutableStateOf(settings.useGpsLocation) }
    var cityInput by remember { mutableStateOf(settings.cityName) }
    var hudPosState by remember { mutableStateOf(settings.hudPosition) }
    var isFetchingWeather by remember { mutableStateOf(false) }

    // LY02 Dynamic State
    var showProductivityHudState by remember { mutableStateOf(settings.showProductivityHud) }
    var showScreenTimeState by remember { mutableStateOf(settings.showScreenTime) }
    var showUnlocksState by remember { mutableStateOf(settings.showUnlocks) }
    var showTopAppsState by remember { mutableStateOf(settings.showTopApps) }
    var starSpeedScaleState by remember { mutableFloatStateOf(settings.starSpeedScale) }
    var productivityModeState by remember { mutableStateOf(cosmicRenderer.productivityMode) }
    var isLanternLitState by remember { mutableStateOf(cosmicRenderer.isLanternLit) }

    fun performGpsWeatherSync() {
        isFetchingWeather = true
        scope.launch {
            val loc = LocationHelper.getBestLocation(context)
            if (loc != null) {
                val res = WeatherRepository.fetchWeatherForCoordinates(loc.latitude, loc.longitude, settings.cityName)
                isFetchingWeather = false
                res.onSuccess { weather ->
                    settings.cityName = weather.city
                    settings.currentTemp = weather.tempC
                    settings.weatherMode = weather.mode
                    cityInput = weather.city
                    celestialRenderer.cityName = weather.city
                    celestialRenderer.currentTemp = weather.tempC
                    celestialRenderer.weatherMode = weather.mode
                    selectedWeather = weather.mode
                    Toast.makeText(context, "Weather synced: ${weather.city} (${weather.tempC})", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    val cityRes = WeatherRepository.fetchWeatherForCity(settings.cityName)
                    cityRes.onSuccess { weather ->
                        settings.cityName = weather.city
                        settings.currentTemp = weather.tempC
                        settings.weatherMode = weather.mode
                        celestialRenderer.cityName = weather.city
                        celestialRenderer.currentTemp = weather.tempC
                        celestialRenderer.weatherMode = weather.mode
                        selectedWeather = weather.mode
                    }
                }
            } else {
                val cityRes = WeatherRepository.fetchWeatherForCity(settings.cityName)
                isFetchingWeather = false
                cityRes.onSuccess { weather ->
                    settings.cityName = weather.city
                    settings.currentTemp = weather.tempC
                    settings.weatherMode = weather.mode
                    celestialRenderer.cityName = weather.city
                    celestialRenderer.currentTemp = weather.tempC
                    celestialRenderer.weatherMode = weather.mode
                    selectedWeather = weather.mode
                    Toast.makeText(context, "Weather synced: ${weather.city} (${weather.tempC})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            useGps = true
            settings.useGpsLocation = true
            performGpsWeatherSync()
        } else {
            useGps = false
            settings.useGpsLocation = false
            Toast.makeText(context, "Location permission denied; using ${settings.cityName}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!isLy02 && settings.useGpsLocation && LocationHelper.hasLocationPermission(context)) {
            performGpsWeatherSync()
        }
    }

    // 60/120 FPS animation loop
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                frameTick = time
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Native Canvas Drawing with Sub-Pixel Smoothness
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLy02) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (isLy02) {
                                val feedback = cosmicRenderer.onTouch(offset.x, offset.y, size.width, size.height)
                                productivityModeState = cosmicRenderer.productivityMode
                                isLanternLitState = cosmicRenderer.isLanternLit
                                if (feedback.isNotEmpty()) {
                                    Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val nx = offset.x / size.width.toFloat()
                                val ny = offset.y / size.height.toFloat()
                                celestialRenderer.onTouch(nx, ny)
                                selectedWeather = celestialRenderer.weatherMode
                                settings.weatherMode = celestialRenderer.weatherMode
                            }
                        },
                        onDoubleTap = {
                            showControls = !showControls
                        }
                    )
                }
        ) {
            val currentTick = frameTick
            if (currentTick >= 0L) {
                drawContext.canvas.nativeCanvas.let { nativeCanvas ->
                    if (isLy02) {
                        cosmicRenderer.update(0.016f)
                        cosmicRenderer.draw(nativeCanvas, size.width.toInt(), size.height.toInt(), System.currentTimeMillis())
                    } else {
                        celestialRenderer.render(nativeCanvas, size.width.toInt(), size.height.toInt())
                    }
                }
            }
        }

        // Top Scrim
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Bottom Scrim
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

        // Top Bar Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                val screenTitle = when {
                    isLy02 -> "LY02: Cosmic Wilderness"
                    wallpaperId == "w2" || wallpaperId.contains("varsha") -> "LY01: Varsha Monsoon"
                    wallpaperId == "w3" || wallpaperId.contains("sharad") -> "LY01: Sharad Moonlight"
                    wallpaperId == "w4" || wallpaperId.contains("shishira") -> "LY01: Shishira Frost"
                    wallpaperId == "w5" || wallpaperId.contains("vasanta") -> "LY01: Vasanta Twilight"
                    else -> "LY01: Celestial Night"
                }
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = { showCustomizationSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Customize",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Bottom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hint message
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NightsStay,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isLy02) "Tap HUD to switch mode • Tap Tent/Lake/Sky • Double tap to hide UI" else "Tap moon to cycle weather • Tap sky for shooting stars",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Quick Action Pills Carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLy02) {
                        FilterChip(
                            selected = showProductivityHudState,
                            onClick = {
                                showProductivityHudState = !showProductivityHudState
                                cosmicRenderer.showProductivityHud = showProductivityHudState
                                settings.showProductivityHud = showProductivityHudState
                            },
                            label = { Text("Productivity HUD", fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                labelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = true,
                            onClick = {
                                val nextMode = when (productivityModeState) {
                                    LY02ProductivityMode.FULL_TELEMETRY -> LY02ProductivityMode.DAILY_GOAL
                                    LY02ProductivityMode.DAILY_GOAL -> LY02ProductivityMode.MINIMAL_TIME
                                    LY02ProductivityMode.MINIMAL_TIME -> LY02ProductivityMode.FULL_TELEMETRY
                                }
                                productivityModeState = nextMode
                                cosmicRenderer.productivityMode = nextMode
                                cosmicRenderer.refreshProductivityStats()
                                Toast.makeText(context, "Mode: ${nextMode.title}", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Mode: ${productivityModeState.title}", fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                labelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                UsageStatsHelper.incrementUnlockCount(context)
                                cosmicRenderer.refreshProductivityStats()
                                Toast.makeText(context, "Unlock count updated: ${cosmicRenderer.productivityStats.unlockCount}", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("⚡ Test Unlock (+1)", fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                labelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                UsageStatsHelper.forceMidnightReset(context)
                                cosmicRenderer.refreshProductivityStats()
                                Toast.makeText(context, "12:00 AM Midnight Reset applied: 00h 00m & 0 unlocks", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🌙 Test Midnight Reset", fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                labelColor = Color.White
                            )
                        )
                    } else {
                        WeatherMode.values().forEach { mode ->
                            val isSelected = selectedWeather == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedWeather = mode
                                    celestialRenderer.weatherMode = mode
                                    settings.weatherMode = mode
                                },
                                label = { Text(mode.label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = Color.Black.copy(alpha = 0.5f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // Quick Customize pill
                    FilterChip(
                        selected = false,
                        onClick = { showCustomizationSheet = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Customize...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                // Unified Apply Button: Opens Target Selection Sheet (Home Only / Lock Only / Both / Live)
                Button(
                    onClick = { showApplySheet = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply Wallpaper...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Live Wallpaper Customization Modal Bottom Sheet
        if (showCustomizationSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showCustomizationSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (isLy02) {
                        // ==========================================
                        // LY02 PRODUCTIVITY & TELEMETRY CONTROLS
                        // ==========================================
                        Text(
                            text = "LY02 Productivity & Cosmic Tuning",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Curved Pixel Telemetry with Zero-Drain OLED #000000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Productivity Display Mode Selector
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Productivity Display Mode",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LY02ProductivityMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = productivityModeState == mode,
                                            onClick = {
                                                productivityModeState = mode
                                                cosmicRenderer.productivityMode = mode
                                                cosmicRenderer.refreshProductivityStats()
                                            },
                                            label = { Text(mode.title, fontSize = 11.sp) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Telemetry Components Toggles
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Daily Screen Time", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Shows today's active screen-on time in curved pixel font", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = showScreenTimeState,
                                        onCheckedChange = { enabled ->
                                            showScreenTimeState = enabled
                                            cosmicRenderer.showScreenTime = enabled
                                            settings.showScreenTime = enabled
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unlock Counter", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Live unlock & lock count tracked passively", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = showUnlocksState,
                                        onCheckedChange = { enabled ->
                                            showUnlocksState = enabled
                                            cosmicRenderer.showUnlocks = enabled
                                            settings.showUnlocks = enabled
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Top 3 Applications", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Most used apps with curved monochrome progress meters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = showTopAppsState,
                                        onCheckedChange = { enabled ->
                                            showTopAppsState = enabled
                                            cosmicRenderer.showTopApps = enabled
                                            settings.showTopApps = enabled
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tent Warm Lantern Glow", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Toggle campsite lantern & campfire embers (or tap tent)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = isLanternLitState,
                                        onCheckedChange = { lit ->
                                            isLanternLitState = lit
                                            cosmicRenderer.isLanternLit = lit
                                        }
                                    )
                                }
                            }
                        }

                        // 3. Testing & Midnight Auto-Reset Actions
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Interactivity & Reset Testing",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )

                                Button(
                                    onClick = {
                                        UsageStatsHelper.forceMidnightReset(context)
                                        cosmicRenderer.refreshProductivityStats()
                                        Toast.makeText(context, "12:00 AM Midnight Reset: 00h 00m & 0 unlocks", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Test 12:00 AM Midnight Reset")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val newCount = UsageStatsHelper.incrementUnlockCount(context)
                                        cosmicRenderer.refreshProductivityStats()
                                        Toast.makeText(context, "Simulated Unlock: count is now $newCount", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simulate Phone Unlock (+1)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Manage Android Usage Access Permission")
                                }
                            }
                        }

                        // 4. Star Twinkle Speed Slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Star Twinkle Speed", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("${(starSpeedScaleState * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = starSpeedScaleState,
                                onValueChange = { speed ->
                                    starSpeedScaleState = speed
                                    cosmicRenderer.starSpeedScale = speed
                                    settings.starSpeedScale = speed
                                },
                                valueRange = 0.2f..2.5f,
                                steps = 10
                            )
                        }
                    } else {
                        // ==========================================
                        // LY01 CELESTIAL CONTROLS
                        // ==========================================
                        Text(
                            text = "LY01 Customization & Tuning",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 1. Moon Size Slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Moon Diameter Scale", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("${(moonScaleState * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = moonScaleState,
                                onValueChange = { scale ->
                                    moonScaleState = scale
                                    celestialRenderer.moonScale = scale
                                    settings.moonSizeScale = scale
                                },
                                valueRange = 0.6f..1.6f,
                                steps = 10
                            )
                        }

                        // 2. Dynamic Moon Phase
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Astronomical Lunar Phase (Paksham)", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Syncs with today's real moon arc & paksham", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = autoLunarPhaseState,
                                        onCheckedChange = { enabled ->
                                            autoLunarPhaseState = enabled
                                            settings.autoLunarPhase = enabled
                                            if (enabled) {
                                                val livePhase = LunarPhaseHelper.getCurrentLunarPhase()
                                                lunarPhaseSlider = livePhase
                                                celestialRenderer.lunarPhase = livePhase
                                                settings.lunarPhaseFraction = livePhase
                                            }
                                        }
                                    )
                                }

                                if (!autoLunarPhaseState) {
                                    Slider(
                                        value = lunarPhaseSlider,
                                        onValueChange = { phase ->
                                            lunarPhaseSlider = phase
                                            celestialRenderer.lunarPhase = phase
                                            settings.lunarPhaseFraction = phase
                                        },
                                        valueRange = 0.0f..1.0f,
                                        steps = 15
                                    )
                                }
                            }
                        }

                        // 3. Indian Seasons
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Indian Ritu Seasons Overlay", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Vasanta, Varsha, Sharad, Shishira ritus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = indianSeasonEnabled,
                                        onCheckedChange = { enabled ->
                                            indianSeasonEnabled = enabled
                                            celestialRenderer.showIndianSeason = enabled
                                            settings.showIndianSeason = enabled
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply Static Snapshot Bottom Sheet (Home Screen / Lock Screen / Both Screens / Live)
        if (showApplySheet) {
            ApplyWallpaperSheet(
                onDismissRequest = {
                    if (!isApplyingStatic) showApplySheet = false
                },
                onSelectTarget = { target ->
                    isApplyingStatic = true
                    scope.launch {
                        val dm = context.resources.displayMetrics
                        val width = dm.widthPixels.coerceAtLeast(1080)
                        val height = dm.heightPixels.coerceAtLeast(1920)
                        val uri = WallpaperHelper.resolveUri(wallpaperId)
                        val bitmap = WallpaperHelper.renderProceduralBitmap(context, uri, width, height)
                        val result = WallpaperHelper.applyBitmapWallpaper(context, bitmap, target)
                        isApplyingStatic = false
                        showApplySheet = false
                        if (result.isSuccess) {
                            val targetName = when (target) {
                                WallpaperTarget.HOME -> "Home Screen"
                                WallpaperTarget.LOCK -> "Lock Screen"
                                WallpaperTarget.BOTH -> "Home & Lock Screens"
                            }
                            Toast.makeText(context, "Wallpaper applied to $targetName!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to apply wallpaper: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onLiveWallpaperClick = { liveTarget ->
                    showApplySheet = false
                    WallpaperHelper.activateLiveWallpaper(context, wallpaperId, liveTarget)
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, LuminaLiveWallpaperService::class.java)
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            context.startActivity(fallbackIntent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "Could not open Live Wallpaper chooser", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                isLoading = isApplyingStatic
            )
        }
    }
}
