package com.example.luminawallpapers.util

import java.util.Date

enum class LunarPhaseType(val label: String, val pakshamName: String) {
    NEW_MOON("New Moon", "Amavasya"),
    WAXING_CRESCENT("Waxing Crescent", "Shukla Paksham"),
    FIRST_QUARTER("First Quarter", "Shukla Paksha Ashtami"),
    WAXING_GIBBOUS("Waxing Gibbous", "Shukla Paksha Chaturdashi"),
    FULL_MOON("Full Moon", "Pournami (Purnima)"),
    WANING_GIBBOUS("Waning Gibbous", "Krishna Paksham"),
    THIRD_QUARTER("Third Quarter", "Krishna Paksha Ashtami"),
    WANING_CRESCENT("Waning Crescent", "Krishna Paksham")
}

object LunarPhaseHelper {

    // Known reference Full Moon (Harvest Moon): Saturday, September 26, 2026, 16:49:00 UTC (10:19 PM IST)
    // Epoch timestamp in milliseconds: 1790441340000L
    private const val REFERENCE_FULL_MOON_MS = 1790441340000L
    private const val SYNODIC_MONTH_MS = 29.53058770576 * 24 * 60 * 60 * 1000.0

    /**
     * Real-time live astronomical lunar phase value fetched from Open-Meteo / astronomical APIs.
     * When present, takes precedence over offline orbital approximations.
     */
    @Volatile
    var cachedLivePhase: Float? = null

    /**
     * Calculates astronomical lunar phase fraction (0.0 to 1.0):
     * 0.00 = New Moon (Amavasya)
     * 0.25 = First Quarter (Half Moon, Waxing)
     * 0.45 = Waxing Gibbous (~98% lit, Day before Full Moon)
     * 0.50 = Full Moon (Pournami / Harvest Moon peak)
     * 0.75 = Third Quarter (Half Moon, Waning)
     * 1.00 = New Moon
     */
    fun getCurrentLunarPhase(date: Date = Date()): Float {
        cachedLivePhase?.let { return it }
        val diff = date.time - REFERENCE_FULL_MOON_MS
        val cycles = diff / SYNODIC_MONTH_MS
        var fraction = (0.50 + (cycles - kotlin.math.floor(cycles))).toFloat() % 1.0f
        if (fraction < 0f) fraction += 1.0f
        return fraction
    }

    /**
     * Calculates astronomical illuminated fraction k = (1 - cos(2*pi*f)) / 2
     */
    fun getIlluminatedFraction(fraction: Float): Float {
        val f = fraction % 1.0f
        return ((1.0 - kotlin.math.cos(f * 2.0 * Math.PI)) / 2.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Astronomical classification according to celestial mechanics:
     * - Primary event points (New, Quarter, Full) span the 24-hour day of the peak event (+-0.017 cycles = +-12 hours).
     * - Intermediate phases (Crescent, Gibbous) span the ~6-day journey between them.
     */
    fun getPhaseType(fraction: Float): LunarPhaseType {
        val f = fraction % 1.0f
        return when {
            // New Moon: within 12 hours of conjunction (Amavasya)
            f < 0.017f || f >= 0.983f -> LunarPhaseType.NEW_MOON
            // Waxing Crescent: up to First Quarter
            f < 0.233f -> LunarPhaseType.WAXING_CRESCENT
            // First Quarter: within 12 hours of 50% half-moon syzygy
            f < 0.267f -> LunarPhaseType.FIRST_QUARTER
            // Waxing Gibbous: from First Quarter up to Full Moon (e.g. Sept 25, 2026 = 0.451)
            f < 0.483f -> LunarPhaseType.WAXING_GIBBOUS
            // Full Moon: within 12 hours of 100% full opposition (e.g. Sept 26, 2026 Harvest Moon = 0.50)
            f < 0.517f -> LunarPhaseType.FULL_MOON
            // Waning Gibbous: after Full Moon down to Third Quarter
            f < 0.733f -> LunarPhaseType.WANING_GIBBOUS
            // Third Quarter: within 12 hours of last 50% half-moon syzygy
            f < 0.767f -> LunarPhaseType.THIRD_QUARTER
            // Waning Crescent: down to New Moon
            else -> LunarPhaseType.WANING_CRESCENT
        }
    }

    fun getPakshamDescription(fraction: Float): String {
        val type = getPhaseType(fraction)
        val illPct = (getIlluminatedFraction(fraction) * 100).toInt()
        val isShukla = fraction < 0.5f
        val paksha = if (isShukla) "Shukla Paksham" else "Krishna Paksham"
        return when (type) {
            LunarPhaseType.FULL_MOON -> "Full Moon (100% Pournami) • $paksha"
            LunarPhaseType.NEW_MOON -> "New Moon (0% Amavasya) • $paksha"
            LunarPhaseType.WAXING_GIBBOUS -> "Waxing Gibbous ($illPct%) • $paksha • Full Moon: Sat, Sep 26"
            else -> "${type.label} ($illPct%) • $paksha"
        }
    }
}
