package com.taskmanager.android.domain

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DateUtilsTest {
    @Test
    fun `get milliseconds until next local midnight uses the device timezone`() {
        val now = ZonedDateTime.of(2026, 3, 15, 23, 59, 0, 0, ZoneId.of("Asia/Novosibirsk"))

        assertThat(getMillisecondsUntilNextLocalMidnight(now)).isEqualTo(60_000)
    }
}
