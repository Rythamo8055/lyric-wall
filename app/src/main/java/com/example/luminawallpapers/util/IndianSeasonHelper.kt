package com.example.luminawallpapers.util

import java.util.Calendar
import java.util.Date

/**
 * Indian Astronomical Seasons (Ritus):
 * The traditional Indian lunar-solar calendar divides the year into 6 seasons (Ritus),
 * each lasting approximately two solar months.
 */
enum class IndianSeason(
    val englishName: String,
    val sanskritName: String,
    val teluguName: String,
    val description: String,
    val climateSummary: String
) {
    VASANTA(
        englishName = "Vasanta (Spring)",
        sanskritName = "वसंत",
        teluguName = "వసంతం",
        description = "Spring / Blossoming Season",
        climateSummary = "Warm breezes & blossoming skies"
    ),
    GRISHMA(
        englishName = "Grishma (Summer)",
        sanskritName = "ग्रीष्म",
        teluguName = "గ్రీష్మం",
        description = "Summer / Heat Season",
        climateSummary = "Blazing starry nights & dry heat"
    ),
    VARSHA(
        englishName = "Varsha (Monsoon)",
        sanskritName = "वर्षा",
        teluguName = "వర్ష రుతువు",
        description = "Monsoon / Rainy Season",
        climateSummary = "Thunder showers & dense storm clouds"
    ),
    SHARAD(
        englishName = "Sharad (Autumn)",
        sanskritName = "शरद",
        teluguName = "శరద్ రుతువు",
        description = "Autumn / Post-Monsoon Season",
        climateSummary = "Crystal clear skies & radiant harvest moon"
    ),
    HEMANTA(
        englishName = "Hemanta (Pre-Winter)",
        sanskritName = "हेमंत",
        teluguName = "హేమంతం",
        description = "Pre-Winter / Frost Season",
        climateSummary = "Cool dewy nights & crisp twinkling stars"
    ),
    SHISHIRA(
        englishName = "Shishira (Winter)",
        sanskritName = "शिशिर",
        teluguName = "శిశిరం",
        description = "Winter / Cold Season",
        climateSummary = "Chilly breeze & deep winter night skies"
    );

    val shortLabel: String
        get() = name // e.g. "SHARAD", "VARSHA"
}

object IndianSeasonHelper {

    /**
     * Computes the Indian Season (Ritu) based on Gregorian date.
     * In the Indian calendar:
     * - Vasanta: ~March 15 to May 14 (Chaitra - Vaishakha)
     * - Grishma: ~May 15 to July 15 (Jyeshtha - Ashadha)
     * - Varsha:  ~July 16 to September 15 (Shravana - Bhadrapada)
     * - Sharad:  ~September 16 to November 14 (Ashwina - Kartika) -> Current Season!
     * - Hemanta: ~November 15 to January 14 (Margashirsha - Pushya)
     * - Shishira:~January 15 to March 14 (Magha - Phalguna)
     */
    fun getCurrentSeason(date: Date = Date()): IndianSeason {
        val cal = Calendar.getInstance().apply { time = date }
        val month = cal.get(Calendar.MONTH) // 0-indexed: 0 = Jan, 8 = Sept, 11 = Dec
        val day = cal.get(Calendar.DAY_OF_MONTH)

        return when (month) {
            Calendar.JANUARY -> if (day < 15) IndianSeason.HEMANTA else IndianSeason.SHISHIRA
            Calendar.FEBRUARY -> IndianSeason.SHISHIRA
            Calendar.MARCH -> if (day < 15) IndianSeason.SHISHIRA else IndianSeason.VASANTA
            Calendar.APRIL -> IndianSeason.VASANTA
            Calendar.MAY -> if (day < 15) IndianSeason.VASANTA else IndianSeason.GRISHMA
            Calendar.JUNE -> IndianSeason.GRISHMA
            Calendar.JULY -> if (day < 16) IndianSeason.GRISHMA else IndianSeason.VARSHA
            Calendar.AUGUST -> IndianSeason.VARSHA
            Calendar.SEPTEMBER -> if (day < 16) IndianSeason.VARSHA else IndianSeason.SHARAD
            Calendar.OCTOBER -> IndianSeason.SHARAD
            Calendar.NOVEMBER -> if (day < 15) IndianSeason.SHARAD else IndianSeason.HEMANTA
            Calendar.DECEMBER -> IndianSeason.HEMANTA
            else -> IndianSeason.SHARAD
        }
    }

    /**
     * Formats season header for HUD display, e.g. "SHARAD RITU"
     */
    fun getHudSeasonTag(date: Date = Date()): String {
        val season = getCurrentSeason(date)
        return "${season.name} RITU"
    }
}
