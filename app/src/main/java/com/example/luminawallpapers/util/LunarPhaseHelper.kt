package com.example.luminawallpapers.util

import java.util.Date

enum class LunarPhaseType(val label: String, val pakshamName: String, val phaseFraction: Float) {
    NEW_MOON("New Moon", "Amavasya", 0.0f),
    WAXING_CRESCENT("Waxing Crescent Arc", "Shukla Paksha Prathama", 0.125f),
    FIRST_QUARTER("First Quarter Half", "Shukla Paksha Ashtami", 0.25f),
    WAXING_GIBBOUS("Waxing Gibbous", "Shukla Paksha Trayodashi", 0.375f),
    FULL_MOON("Full Moon", "Pournami (Purnima)", 0.5f),
    WANING_GIBBOUS("Waning Gibbous", "Krishna Paksha Dvitiya", 0.625f),
    THIRD_QUARTER("Third Quarter Half", "Krishna Paksha Ashtami", 0.75f),
    WANING_CRESCENT("Waning Crescent Arc", "Krishna Paksha Chaturdashi", 0.875f)
}

object LunarPhaseHelper {

    // Known reference New Moon epoch: Jan 11, 2024, 11:57 UTC
    private const val REFERENCE_NEW_MOON_MS = 1704974220000L
    private const val SYNODIC_MONTH_MS = 29.53058770576 * 24 * 60 * 60 * 1000.0

    /**
     * Calculates astronomical lunar phase fraction (0.0 to 1.0)
     * 0.0 = New Moon (Amavasya)
     * 0.25 = First Quarter (Half Moon)
     * 0.5 = Full Moon (Pournami)
     * 0.75 = Last Quarter (Half Moon)
     * 1.0 = New Moon
     */
    fun getCurrentLunarPhase(date: Date = Date()): Float {
        val diff = date.time - REFERENCE_NEW_MOON_MS
        val cycles = diff / SYNODIC_MONTH_MS
        var fraction = (cycles - kotlin.math.floor(cycles)).toFloat()
        if (fraction < 0f) fraction += 1.0f
        return fraction
    }

    fun getPhaseType(fraction: Float): LunarPhaseType {
        val f = fraction % 1.0f
        return when {
            f < 0.0625f || f >= 0.9375f -> LunarPhaseType.NEW_MOON
            f < 0.1875f -> LunarPhaseType.WAXING_CRESCENT
            f < 0.3125f -> LunarPhaseType.FIRST_QUARTER
            f < 0.4375f -> LunarPhaseType.WAXING_GIBBOUS
            f < 0.5625f -> LunarPhaseType.FULL_MOON
            f < 0.6875f -> LunarPhaseType.WANING_GIBBOUS
            f < 0.8125f -> LunarPhaseType.THIRD_QUARTER
            else -> LunarPhaseType.WANING_CRESCENT
        }
    }

    fun getPakshamDescription(fraction: Float): String {
        val type = getPhaseType(fraction)
        val isShukla = fraction < 0.5f
        val paksha = if (isShukla) "Shukla Paksham (Waxing)" else "Krishna Paksham (Waning)"
        return "${type.label} • $paksha"
    }
}
