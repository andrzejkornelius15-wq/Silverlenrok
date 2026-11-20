package com.workout.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workout.tracker.data.*
import com.workout.tracker.domain.ScheduleEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutTrackerApp()
        }
    }
}

private val GreenBg = Color(0xFFDCFCE7)
private val GreenFg = Color(0xFF166534)
private val AmberBg = Color(0xFFFEF3C7)
private val AmberFg = Color(0xFF92400E)
private val RedBg = Color(0xFFFEE2E2)
private val RedFg = Color(0xFF991B1B)
private val NavyBg = Color(0xFF1E293B)
private val TealAccent = Color(0xFF0F766E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTrackerApp() {
    val context = LocalContext.current
    var plans by remember { mutableStateOf(ScheduleEngine.defaultPlans()) }
    var customLogMap by remember { mutableStateOf<Map<String, WorkoutLogEntry>>(emptyMap()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Kalendarz, 1: Dziennik, 2: Plany i Eksport
    var selectedDate by remember { mutableStateOf(ScheduleEngine.nextTrainingDayOnOrAfter(LocalDate.of(2026, 11, 19))) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val scheduleStart = remember(plans) {
        plans.mapNotNull { it.startDate }.minOrNull() ?: LocalDate.now()
    }
    val trainingDates = remember(scheduleStart) {
        ScheduleEngine.generateTrainingDates(scheduleStart, totalSessions = 312)
    }

    val allEntries = remember(plans, customLogMap, trainingDates) {
        val result = ArrayList<WorkoutLogEntry>(trainingDates.size * 8)
        for (d in trainingDates) {
            val dayExisting = (1..8).mapNotNull { slot -> customLogMap["${d}_$slot"] }
            result.addAll(ScheduleEngine.buildDayEntries(d, plans, dayExisting))
        }
        result
    }

    val entriesByDate = remember(allEntries) {
        allEntries.groupBy { it.date }
    }

    fun updateEntry(updated: WorkoutLogEntry) {
        customLogMap = customLogMap.toMutableMap().apply {
            put(updated.id, updated)
        }
    }

    fun exportCsvAndShare() {
        val file = FileExportHelper.exportCsvToFile(context, allEntries)
        exportMessage = "Zapisano CSV: ${file.name}"
        val intent = Intent.createChooser(
            FileExportHelper.shareFileIntent(context, file, "text/csv"),
            "Eksportuj dziennik treningowy (CSV)"
        )
        context.startActivity(intent)
    }

    fun exportJsonAndShare() {
        val file = FileExportHelper.exportJsonBackupToFile(context, plans, customLogMap.values)
        exportMessage = "Zapisano kopię JSON: ${file.name}"
        val intent = Intent.createChooser(
            FileExportHelper.shareFileIntent(context, file, "application/json"),
            "Eksportuj kopię planów i wpisów (JSON)"
        )
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Plan Treningowy",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = ::exportCsvAndShare) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = "Eksportuj do pliku CSV",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBg)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    label = { Text("Kalendarz") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
                    label = { Text("Dziennik") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    label = { Text("Plany") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> CalendarScreen(
                    scheduleStart = scheduleStart,
                    selectedDate = selectedDate,
                    entriesByDate = entriesByDate,
                    onSelectDate = { d ->
                        selectedDate = d
                        selectedTab = 1
                    }
                )
                1 -> DailyJournalScreen(
                    selectedDate = selectedDate,
                    trainingDates = trainingDates,
                    entries = entriesByDate[selectedDate] ?: emptyList(),
                    plans = plans,
                    onChangeDate = { selectedDate = it },
                    onUpdateEntry = ::updateEntry
                )
                2 -> PlansDashboardScreen(
                    plans = plans,
                    allEntries = allEntries,
                    exportMessage = exportMessage,
                    onUpdatePlan = { idx, newPlan ->
                        plans = plans.toMutableList().apply { set(idx, newPlan) }
                    },
                    onSetAllSameDate = { dtFrom, dtTo ->
                        plans = plans.map { it.copy(startDate = dtFrom, endDate = dtTo) }
                    },
                    onExportCsv = ::exportCsvAndShare,
                    onExportJson = ::exportJsonAndShare
                )
            }
        }
    }
}

@Composable
fun CalendarScreen(
    scheduleStart: LocalDate,
    selectedDate: LocalDate,
    entriesByDate: Map<LocalDate, List<WorkoutLogEntry>>,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstMonday = remember(scheduleStart) {
        scheduleStart.minusDays((scheduleStart.dayOfWeek.value - 1).toLong())
    }
    val weeks = remember(firstMonday) {
        (0 until 60).map { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            (0 until 7).map { d -> monday.plusDays(d.toLong()) }
        }
    }
    val dayNames = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
    val fmt = DateTimeFormatter.ofPattern("dd.MM")

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LegendChip("✔ Wykonane", GreenBg, GreenFg)
            LegendChip("⚠ Nie do końca", AmberBg, AmberFg)
            LegendChip("✖ Niewykonane", RedBg, RedFg)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            dayNames.forEachIndexed { idx, name ->
                val isTrainCol = idx in listOf(1, 3, 5)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isTrainCol) TealAccent else Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isTrainCol) Color.White else Color(0xFF334155)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(weeks) { weekDays ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekDays.forEach { date ->
                        val isTrain = ScheduleEngine.isTrainingDay(date)
                        val dayEntries = entriesByDate[date] ?: emptyList()
                        val state = ScheduleEngine.computeDayState(date, dayEntries)
                        val isSelected = date == selectedDate

                        val (bg, fg) = when {
                            !isTrain -> Color(0xFFF8FAFC) to Color(0xFF94A3B8)
                            state == DayCompletionState.ALL_DONE -> GreenBg to GreenFg
                            state == DayCompletionState.PARTIAL -> AmberBg to AmberFg
                            state == DayCompletionState.NONE_DONE -> RedBg to RedFg
                            else -> Color(0xFFF0FDFA) to NavyBg
                        }

                        val activeExercises = dayEntries.filter { it.exerciseName.isNotBlank() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(bg, RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) NavyBg else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectDate(date) }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = date.format(fmt),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (!isTrain) {
                                Text("Reg.", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            } else if (activeExercises.isEmpty()) {
                                Text("—", fontSize = 10.sp, color = fg)
                            } else {
                                activeExercises.forEach { item ->
                                    val prefix = if (item.status == SessionStatus.DONE) "✔ " else "• "
                                    Text(
                                        text = "$prefix${item.exerciseName}",
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = fg,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DailyJournalScreen(
    selectedDate: LocalDate,
    trainingDates: List<LocalDate>,
    entries: List<WorkoutLogEntry>,
    plans: List<PlanConfig>,
    onChangeDate: (LocalDate) -> Unit,
    onUpdateEntry: (WorkoutLogEntry) -> Unit
) {
    val idx = trainingDates.indexOf(selectedDate)
    val prevDate = if (idx > 0) trainingDates[idx - 1] else null
    val nextDate = if (idx >= 0 && idx + 1 < trainingDates.size) trainingDates[idx + 1] else null

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(enabled = prevDate != null, onClick = { prevDate?.let(onChangeDate) }) {
                Text("Poprzedni")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selectedDate.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(selectedDate.dayOfWeek.name, fontSize = 11.sp, color = Color.Gray)
            }
            Button(enabled = nextDate != null, onClick = { nextDate?.let(onChangeDate) }) {
                Text("Następny")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.id }) { entry ->
                JournalSlotCard(entry = entry, plans = plans, onSave = onUpdateEntry)
            }
        }
    }
}

@Composable
fun JournalSlotCard(
    entry: WorkoutLogEntry,
    plans: List<PlanConfig>,
    onSave: (WorkoutLogEntry) -> Unit
) {
    var weightText by remember(entry.id, entry.weightKg) {
        mutableStateOf(if (entry.weightKg > 0.0) entry.weightKg.toString() else "")
    }
    var s1Text by remember(entry.id, entry.set1) { mutableStateOf(if (entry.set1 > 0) entry.set1.toString() else "") }
    var s2Text by remember(entry.id, entry.set2) { mutableStateOf(if (entry.set2 > 0) entry.set2.toString() else "") }
    var s3Text by remember(entry.id, entry.set3) { mutableStateOf(if (entry.set3 > 0) entry.set3.toString() else "") }
    var s4Text by remember(entry.id, entry.set4) { mutableStateOf(if (entry.set4 > 0) entry.set4.toString() else "") }
    var notesText by remember(entry.id, entry.notes) { mutableStateOf(entry.notes) }

    val statusColor = when (entry.status) {
        SessionStatus.DONE -> GreenFg
        SessionStatus.PLANNED -> AmberFg
        SessionStatus.SKIPPED -> RedFg
        SessionStatus.OPTIONAL -> Color(0xFF475569)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${entry.positionInDay}. ${entry.planCode ?: "Opcjonalne"}",
                    fontWeight = FontWeight.Bold,
                    color = NavyBg
                )
                Text(
                    text = entry.status.label,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Text(
                text = if (entry.exerciseName.isNotBlank()) entry.exerciseName else "(Wolna pozycja — wybierz lub wpisz ćwiczenie)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionStatus.entries.forEach { st ->
                    FilterChip(
                        selected = entry.status == st,
                        onClick = { onSave(entry.copy(status = st)) },
                        label = { Text(st.label, fontSize = 11.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        onSave(entry.copy(weightKg = it.toDoubleOrNull() ?: 0.0))
                    },
                    label = { Text("Kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f)
                )
                SeriesField("S1", s1Text) { v ->
                    s1Text = v
                    onSave(entry.copy(set1 = v.toIntOrNull() ?: 0))
                }
                SeriesField("S2", s2Text) { v ->
                    s2Text = v
                    onSave(entry.copy(set2 = v.toIntOrNull() ?: 0))
                }
                SeriesField("S3", s3Text) { v ->
                    s3Text = v
                    onSave(entry.copy(set3 = v.toIntOrNull() ?: 0))
                }
                SeriesField("S4", s4Text) { v ->
                    s4Text = v
                    onSave(entry.copy(set4 = v.toIntOrNull() ?: 0))
                }
            }

            Text(
                text = "Serie: ${entry.completedSets} | Powt.: ${entry.totalReps} | Tonaż: ${entry.tonnageKg.toInt()} kg | 1RM: ${String.format("%.1f", entry.estimated1Rm)} kg",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    onSave(entry.copy(notes = it))
                },
                label = { Text("Notatki") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RowScope.SeriesField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

@Composable
fun PlansDashboardScreen(
    plans: List<PlanConfig>,
    allEntries: List<WorkoutLogEntry>,
    exportMessage: String?,
    onUpdatePlan: (Int, PlanConfig) -> Unit,
    onSetAllSameDate: (LocalDate, LocalDate) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit
) {
    val summaries = remember(plans, allEntries) {
        ScheduleEngine.computePlanSummaries(plans, allEntries)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Eksport danych do pliku", fontWeight = FontWeight.Bold, color = NavyBg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportCsv, modifier = Modifier.weight(1f)) {
                            Text("Eksportuj CSV")
                        }
                        OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                            Text("Kopia JSON")
                        }
                    }
                    if (exportMessage != null) {
                        Text(exportMessage, fontSize = 12.sp, color = TealAccent, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                    Text("Szybkie akcje dla wszystkich 8 planów", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            val d0 = LocalDate.of(2026, 11, 19)
                            onSetAllSameDate(d0, d0.plusMonths(3))
                        }
                    ) {
                        Text("Ustaw wszystkie 8 planów na ten sam zakres (od 2026-11-19)")
                    }
                }
            }
        }

        items(summaries.indices.toList()) { idx ->
            val row = summaries[idx]
            var nameInput by remember(row.plan.code, row.plan.exerciseName) {
                mutableStateOf(row.plan.exerciseName)
            }
            var fromInput by remember(row.plan.code, row.plan.startDate) {
                mutableStateOf(row.plan.startDate?.toString() ?: "")
            }
            var toInput by remember(row.plan.code, row.plan.endDate) {
                mutableStateOf(row.plan.endDate?.toString() ?: "")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.plan.code} (${row.durationWeeks} tyg.)", fontWeight = FontWeight.Bold, color = NavyBg)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            onUpdatePlan(idx, row.plan.copy(exerciseName = it))
                        },
                        label = { Text("Nazwa ćwiczenia") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromInput,
                            onValueChange = {
                                fromInput = it
                                val parsed = runCatching { LocalDate.parse(it.trim()) }.getOrNull()
                                onUpdatePlan(idx, row.plan.copy(startDate = parsed))
                            },
                            label = { Text("Data od (RRRR-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toInput,
                            onValueChange = {
                                toInput = it
                                val parsed = runCatching { LocalDate.parse(it.trim()) }.getOrNull()
                                onUpdatePlan(idx, row.plan.copy(endDate = parsed))
                            },
                            label = { Text("Data do (RRRR-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Zaplan.: ${row.plannedSessions} | Wyk.: ${row.doneSessions} | Powt.: ${row.totalReps} | Tonaż: ${row.totalTonnageKg.toInt()} kg | 1RM: ${String.format("%.1f", row.bestEstimated1Rm)} kg",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
