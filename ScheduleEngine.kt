package com.workout.tracker.domain

import com.workout.tracker.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ScheduleEngine {
    val trainingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.TUESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.SATURDAY
    )

    fun defaultPlans(): List<PlanConfig> = listOf(
        PlanConfig("Plan 1", "Pompki", LocalDate.of(2026, 11, 19), LocalDate.of(2027, 2, 17)),
        PlanConfig("Plan 2", "Dipy (Pompki na poręczach)", LocalDate.of(2027, 2, 18), LocalDate.of(2027, 5, 19)),
        PlanConfig("Plan 3", "OHP (Wyciskanie nad głowę)", LocalDate.of(2027, 5, 20), LocalDate.of(2027, 8, 18)),
        PlanConfig("Plan 4", "Wiosłowanie sztangą", LocalDate.of(2027, 8, 19), LocalDate.of(2027, 11, 17)),
        PlanConfig("Plan 5", "Przysiad ze sztangą", LocalDate.of(2027, 11, 18), LocalDate.of(2028, 2, 16)),
        PlanConfig("Plan 6", "Podciąganie", LocalDate.of(2028, 2, 17), LocalDate.of(2028, 5, 17)),
        PlanConfig("Plan 7", "Martwy ciąg", null, null),
        PlanConfig("Plan 8", "Wznosy bokiem", null, null)
    )

    fun isTrainingDay(date: LocalDate): Boolean = date.dayOfWeek in trainingDays

    fun nextTrainingDayOnOrAfter(start: LocalDate): LocalDate {
        var cur = start
        while (!isTrainingDay(cur)) {
            cur = cur.plusDays(1)
        }
        return cur
    }

    fun generateTrainingDates(scheduleStart: LocalDate, totalSessions: Int = 312): List<LocalDate> {
        val result = ArrayList<LocalDate>(totalSessions)
        var cur = nextTrainingDayOnOrAfter(scheduleStart)
        for (i in 0 until totalSessions) {
            result.add(cur)
            cur = nextTrainingDayOnOrAfter(cur.plusDays(1))
        }
        return result
    }

    fun matchingPlansForDate(date: LocalDate, plans: List<PlanConfig>): List<PlanConfig> {
        return plans.filter { p ->
            val s = p.startDate
            val e = p.endDate
            s != null && e != null && !e.isBefore(s) && !date.isBefore(s) && !date.isAfter(e)
        }
    }

    fun buildDayEntries(
        date: LocalDate,
        plans: List<PlanConfig>,
        existingEntries: List<WorkoutLogEntry>
    ): List<WorkoutLogEntry> {
        val matched = matchingPlansForDate(date, plans).take(8)
        val existingBySlot = existingEntries.associateBy { it.positionInDay }
        val entries = ArrayList<WorkoutLogEntry>(8)

        for (slot in 1..8) {
            val plan = matched.getOrNull(slot - 1)
            val prev = existingBySlot[slot]

            if (plan != null) {
                val status = prev?.status ?: SessionStatus.PLANNED
                val exercise = prev?.exerciseName?.takeIf { it.isNotBlank() && prev.planCode == plan.code }
                    ?: plan.exerciseName
                entries.add(
                    WorkoutLogEntry(
                        id = prev?.id ?: "${date}_$slot",
                        date = date,
                        planCode = plan.code,
                        positionInDay = slot,
                        status = status,
                        exerciseName = exercise,
                        weightKg = prev?.weightKg ?: 0.0,
                        set1 = prev?.set1 ?: 0,
                        set2 = prev?.set2 ?: 0,
                        set3 = prev?.set3 ?: 0,
                        set4 = prev?.set4 ?: 0,
                        notes = prev?.notes ?: ""
                    )
                )
            } else {
                val status = prev?.status ?: SessionStatus.OPTIONAL
                entries.add(
                    WorkoutLogEntry(
                        id = prev?.id ?: "${date}_$slot",
                        date = date,
                        planCode = null,
                        positionInDay = slot,
                        status = status,
                        exerciseName = prev?.exerciseName ?: "",
                        weightKg = prev?.weightKg ?: 0.0,
                        set1 = prev?.set1 ?: 0,
                        set2 = prev?.set2 ?: 0,
                        set3 = prev?.set3 ?: 0,
                        set4 = prev?.set4 ?: 0,
                        notes = prev?.notes ?: ""
                    )
                )
            }
        }
        return entries
    }

    fun computeDayState(date: LocalDate, dayEntries: List<WorkoutLogEntry>): DayCompletionState {
        if (!isTrainingDay(date)) return DayCompletionState.REST_OR_EMPTY
        val plannedCount = dayEntries.count { it.status == SessionStatus.PLANNED }
        val doneCount = dayEntries.count { it.status == SessionStatus.DONE }
        return when {
            doneCount > 0 && plannedCount == 0 -> DayCompletionState.ALL_DONE
            doneCount > 0 && plannedCount > 0 -> DayCompletionState.PARTIAL
            doneCount == 0 && plannedCount > 0 -> DayCompletionState.NONE_DONE
            else -> DayCompletionState.REST_OR_EMPTY
        }
    }

    fun computePlanSummaries(plans: List<PlanConfig>, allEntries: List<WorkoutLogEntry>): List<PlanSummaryRow> {
        return plans.map { plan ->
            val firstSes = if (plan.startDate != null && plan.endDate != null && !plan.endDate.isBefore(plan.startDate)) {
                val cand = nextTrainingDayOnOrAfter(plan.startDate)
                if (!cand.isAfter(plan.endDate)) cand else null
            } else null

            val lastSes = if (plan.startDate != null && plan.endDate != null && !plan.endDate.isBefore(plan.startDate)) {
                var cur = plan.endDate
                while (!cur.isBefore(plan.startDate) && !isTrainingDay(cur)) {
                    cur = cur.minusDays(1)
                }
                if (!cur.isBefore(plan.startDate)) cur else null
            } else null

            val durationWeeks = if (plan.startDate != null && plan.endDate != null && !plan.endDate.isBefore(plan.startDate)) {
                Math.round(((ChronoUnit.DAYS.between(plan.startDate, plan.endDate) + 1) / 7.0) * 10.0) / 10.0
            } else 0.0

            val planEntries = allEntries.filter { it.planCode == plan.code }
            val plannedSessions = planEntries.size
            val doneSessions = planEntries.count { it.status == SessionStatus.DONE }
            val totalSets = planEntries.sumOf { it.completedSets }
            val totalReps = planEntries.sumOf { it.totalReps }
            val avgRepsPerSet = if (totalSets > 0) totalReps.toDouble() / totalSets.toDouble() else 0.0
            val maxWeightKg = planEntries.maxOfOrNull { it.weightKg } ?: 0.0
            val totalTonnageKg = planEntries.sumOf { it.tonnageKg }
            val best1Rm = planEntries.maxOfOrNull { it.estimated1Rm } ?: 0.0

            PlanSummaryRow(
                plan = plan,
                firstSessionDate = firstSes,
                lastSessionDate = lastSes,
                durationWeeks = durationWeeks,
                plannedSessions = plannedSessions,
                doneSessions = doneSessions,
                totalSets = totalSets,
                totalReps = totalReps,
                avgRepsPerSet = avgRepsPerSet,
                maxWeightKg = maxWeightKg,
                totalTonnageKg = totalTonnageKg,
                bestEstimated1Rm = best1Rm
            )
        }
    }
}
