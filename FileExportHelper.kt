package com.workout.tracker.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileExportHelper {
    private val jsonFormatter = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun exportCsvToFile(context: Context, allEntries: List<WorkoutLogEntry>): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outFile = File(exportDir, "dziennik_treningowy_$stamp.csv")

        val sb = StringBuilder()
        sb.appendLine("Data;Plan;PozycjaWDniu;Status;Cwiczenie;CiezarKg;Seria1;Seria2;Seria3;Seria4;WykonaneSerie;SumaPowtorzen;TonazKg;Szac1RM;Notatki")
        for (e in allEntries) {
            val cleanNotes = e.notes.replace(";", ",").replace("
", " ")
            val cleanExercise = e.exerciseName.replace(";", ",")
            sb.appendLine(
                listOf(
                    e.date.toString(),
                    e.planCode ?: "",
                    e.positionInDay.toString(),
                    e.status.label,
                    cleanExercise,
                    String.format("%.1f", e.weightKg),
                    e.set1.toString(),
                    e.set2.toString(),
                    e.set3.toString(),
                    e.set4.toString(),
                    e.completedSets.toString(),
                    e.totalReps.toString(),
                    String.format("%.1f", e.tonnageKg),
                    String.format("%.1f", e.estimated1Rm),
                    cleanNotes
                ).joinToString(";")
            )
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
        return outFile
    }

    fun exportJsonBackupToFile(
        context: Context,
        plans: List<PlanConfig>,
        customEntries: Collection<WorkoutLogEntry>
    ): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outFile = File(exportDir, "plan_treningowy_backup_$stamp.json")

        val payload = BackupPayload(
            exportedAtIso = LocalDateTime.now().toString(),
            plans = plans.map { SerializablePlanConfig.fromPlanConfig(it) },
            customLogEntries = customEntries.map { SerializableLogEntry.fromWorkoutLogEntry(it) }
        )
        outFile.writeText(jsonFormatter.encodeToString(payload), Charsets.UTF_8)
        return outFile
    }

    fun shareFileIntent(context: Context, file: File, mimeType: String): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
