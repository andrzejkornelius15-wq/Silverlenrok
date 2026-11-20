package com.workout.tracker.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class SessionStatus(val label: String) {
    PLANNED("Planowany"),
    DONE("Wykonany"),
    OPTIONAL("Opcjonalne"),
    SKIPPED("Opuszczony")
}

enum class DayCompletionState {
    ALL_DONE,     // Zielony: wszystkie zaplanowane wykonane (wykonane > 0, planowane == 0)
    PARTIAL,      // Żółty: nie do końca (wykonane > 0, planowane > 0)
    NONE_DONE,    // Czerwony: żadne wykonane (wykonane == 0, planowane > 0)
    REST_OR_EMPTY // Szary / neutralny
}

data class PlanConfig(
    val code: String,          // "Plan 1" .. "Plan 8"
    val exerciseName: String,  // np. "Pompki", "Dipy (Pompki na poręczach)"
    val startDate: LocalDate?, // Data od
    val endDate: LocalDate?    // Data do
)

@Serializable
data class SerializablePlanConfig(
    val code: String,
    val exerciseName: String,
    val startDateIso: String? = null,
    val endDateIso: String? = null
) {
    fun toPlanConfig(): PlanConfig = PlanConfig(
        code = code,
        exerciseName = exerciseName,
        startDate = startDateIso?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
        endDate = endDateIso?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
    )

    companion object {
        fun fromPlanConfig(p: PlanConfig): SerializablePlanConfig = SerializablePlanConfig(
            code = p.code,
            exerciseName = p.exerciseName,
            startDateIso = p.startDate?.toString(),
            endDateIso = p.endDate?.toString()
        )
    }
}

data class WorkoutLogEntry(
    val id: String,
    val date: LocalDate,
    val planCode: String?,     // "Plan 1".."Plan 8" lub null dla pozycji opcjonalnej
    val positionInDay: Int,    // 1..8
    val status: SessionStatus,
    val exerciseName: String,
    val weightKg: Double = 0.0,
    val set1: Int = 0,
    val set2: Int = 0,
    val set3: Int = 0,
    val set4: Int = 0,
    val notes: String = ""
) {
    val completedSets: Int
        get() = listOf(set1, set2, set3, set4).count { it > 0 }

    val totalReps: Int
        get() = set1 + set2 + set3 + set4

    val tonnageKg: Double
        get() = if (weightKg > 0.0) weightKg * totalReps else 0.0

    val maxSetReps: Int
        get() = maxOf(set1, set2, set3, set4)

    val estimated1Rm: Double
        get() = if (weightKg > 0.0 && maxSetReps > 0) weightKg * (1.0 + maxSetReps / 30.0) else 0.0
}

@Serializable
data class SerializableLogEntry(
    val id: String,
    val dateIso: String,
    val planCode: String? = null,
    val positionInDay: Int,
    val statusName: String,
    val exerciseName: String,
    val weightKg: Double = 0.0,
    val set1: Int = 0,
    val set2: Int = 0,
    val set3: Int = 0,
    val set4: Int = 0,
    val notes: String = ""
) {
    fun toWorkoutLogEntry(): WorkoutLogEntry {
        val parsedStatus = runCatching { SessionStatus.valueOf(statusName) }
            .getOrElse { SessionStatus.PLANNED }
        return WorkoutLogEntry(
            id = id,
            date = LocalDate.parse(dateIso),
            planCode = planCode,
            positionInDay = positionInDay,
            status = parsedStatus,
            exerciseName = exerciseName,
            weightKg = weightKg,
            set1 = set1,
            set2 = set2,
            set3 = set3,
            set4 = set4,
            notes = notes
        )
    }

    companion object {
        fun fromWorkoutLogEntry(e: WorkoutLogEntry): SerializableLogEntry = SerializableLogEntry(
            id = e.id,
            dateIso = e.date.toString(),
            planCode = e.planCode,
            positionInDay = e.positionInDay,
            statusName = e.status.name,
            exerciseName = e.exerciseName,
            weightKg = e.weightKg,
            set1 = e.set1,
            set2 = e.set2,
            set3 = e.set3,
            set4 = e.set4,
            notes = e.notes
        )
    }
}

@Serializable
data class BackupPayload(
    val exportedAtIso: String,
    val plans: List<SerializablePlanConfig>,
    val customLogEntries: List<SerializableLogEntry>
)

data class PlanSummaryRow(
    val plan: PlanConfig,
    val firstSessionDate: LocalDate?,
    val lastSessionDate: LocalDate?,
    val durationWeeks: Double,
    val plannedSessions: Int,
    val doneSessions: Int,
    val totalSets: Int,
    val totalReps: Int,
    val avgRepsPerSet: Double,
    val maxWeightKg: Double,
    val totalTonnageKg: Double,
    val bestEstimated1Rm: Double
)
