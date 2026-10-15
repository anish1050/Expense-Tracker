package com.anish1050.ledger.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Inclusive epoch-millis range for a calendar period in the device zone.
data class Range(val from: Long, val to: Long)

object Period {
    fun day(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range =
        Range(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)

    fun month(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range {
        val start = date.withDayOfMonth(1)
        return Range(start.atStartOfDay(zone).toInstant().toEpochMilli(), start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun previousMonth(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range = month(date.minusMonths(1), zone)

    fun week(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return Range(start.atStartOfDay(zone).toInstant().toEpochMilli(), start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun year(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range {
        val start = date.withDayOfYear(1)
        return Range(start.atStartOfDay(zone).toInstant().toEpochMilli(), start.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }
}
