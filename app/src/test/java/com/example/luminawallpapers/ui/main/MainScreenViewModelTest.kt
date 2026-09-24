package com.example.luminawallpapers.ui.main

import com.example.luminawallpapers.util.UsageStatsHelper
import junit.framework.TestCase.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun testFormatDuration() {
        val threeHours48Min = 3 * 3600_000L + 48 * 60_000L
        assertEquals("03h 48m", UsageStatsHelper.formatDuration(threeHours48Min))
        
        val fiftyEightMin = 58 * 60_000L
        assertEquals("58m", UsageStatsHelper.formatDuration(fiftyEightMin))
    }
}
