package com.example.androidapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.Keep
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Constant representing the extra row (under 21:00) without a time label
const val EXTRA_ROW_HOUR = 22
private const val INITIAL_PAGE = 5000
private const val TOTAL_PAGES = 10000

/**
 * Data model for an event in the weekly calendar.
 */
@Keep
data class CalendarEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    val hour: Int, // 16 to 21, or EXTRA_ROW_HOUR (22) for extra row
    val color: Color = Color(0xFF3F51B5)
)

@Composable
fun WeeklyCalendarScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val baseWeekStart = remember {
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }

    val pagerState = rememberPagerState(
        initialPage = INITIAL_PAGE,
        pageCount = { TOTAL_PAGES }
    )

    val currentWeekStart = remember(pagerState.currentPage) {
        baseWeekStart.plusWeeks((pagerState.currentPage - INITIAL_PAGE).toLong())
    }

    // Live list synced with Firebase Cloud Firestore & local storage
    val events = remember { mutableStateListOf<CalendarEvent>() }

    LaunchedEffect(Unit) {
        CalendarStorage.listenToEvents(context) { updatedEvents ->
            events.clear()
            events.addAll(updatedEvents)
        }
    }

    var selectedSlot by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
    var selectedEventForDetails by remember { mutableStateOf<CalendarEvent?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val hours = (16..21).toList() + EXTRA_ROW_HOUR

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Week Navigation Header
        WeekHeader(
            currentWeekStart = currentWeekStart,
            onPreviousWeek = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            },
            onNextWeek = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            },
            onTodayClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(INITIAL_PAGE)
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Unified Vertical Scroll Container with Fixed Stationary Time Column on the Left
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Stationary Time Column (Does NOT scroll horizontally)
            TimeColumn(
                hours = hours,
                modifier = Modifier.width(54.dp)
            )

            // Horizontal Pager for 7 Day Columns ONLY (Swipes left/right horizontally)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val pageWeekStart = remember(pageIndex) {
                    baseWeekStart.plusWeeks((pageIndex - INITIAL_PAGE).toLong())
                }
                val pageDays = remember(pageWeekStart) {
                    (0..6).map { pageWeekStart.plusDays(it.toLong()) }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Days Header (Sun - Sat) for this week page
                    DaysHeaderRow(days = pageDays)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Grid Rows for 7 Days
                    hours.forEach { hour ->
                        DayGridRow(
                            days = pageDays,
                            events = events.filter { it.hour == hour },
                            onCellClick = { date ->
                                selectedSlot = Pair(date, hour)
                                showAddDialog = true
                            },
                            onEventClick = { event ->
                                selectedEventForDetails = event
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog to add event
    if (showAddDialog && selectedSlot != null) {
        val (slotDate, slotHour) = selectedSlot!!
        AddEventDialog(
            date = slotDate,
            hour = slotHour,
            onDismiss = { showAddDialog = false },
            onAddEvent = { title, color ->
                val newEvent = CalendarEvent(
                    title = title,
                    date = slotDate,
                    hour = slotHour,
                    color = color
                )
                events.add(newEvent)
                CalendarStorage.saveEvent(context, newEvent)
                showAddDialog = false
            }
        )
    }

    // Dialog to view, edit & delete event
    if (selectedEventForDetails != null) {
        val event = selectedEventForDetails!!
        EditEventDialog(
            event = event,
            onDismiss = { selectedEventForDetails = null },
            onUpdateEvent = { newTitle, newColor ->
                val updated = event.copy(title = newTitle, color = newColor)
                val index = events.indexOfFirst { it.id == event.id }
                if (index != -1) {
                    events[index] = updated
                }
                CalendarStorage.saveEvent(context, updated)
                selectedEventForDetails = null
            },
            onDeleteEvent = {
                events.removeAll { it.id == event.id }
                CalendarStorage.deleteEvent(context, event.id)
                selectedEventForDetails = null
            }
        )
    }
}

@Composable
fun WeekHeader(
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onTodayClick: () -> Unit
) {
    val weekEnd = currentWeekStart.plusDays(6)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    val headerTitle = if (currentWeekStart.month == weekEnd.month) {
        currentWeekStart.format(monthYearFormatter)
    } else {
        "${currentWeekStart.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))} - ${weekEnd.format(formatter)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${currentWeekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTodayClick) {
                Text("Today")
            }
            TextButton(onClick = onPreviousWeek) {
                Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onNextWeek) {
                Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TimeColumn(
    hours: List<Int>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // "Time" Header Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Hourly Labels
        hours.forEach { hour ->
            val isExtraRow = hour == EXTRA_ROW_HOUR
            val timeLabel = if (isExtraRow) "" else String.format(Locale.getDefault(), "%02d:00", hour)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DaysHeaderRow(
    days: List<LocalDate>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 4.dp)
    ) {
        // 7 Day columns
        days.forEach { date ->
            val isToday = date.isEqual(today)
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val dayNum = date.dayOfMonth.toString()

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNum,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DayGridRow(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    onCellClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
    ) {
        days.forEach { date ->
            val cellEvents = events.filter { it.date.isEqual(date) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    .clickable { onCellClick(date) }
                    .padding(2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (cellEvents.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        cellEvents.forEach { event ->
                            EventChip(
                                event = event,
                                onClick = { onEventClick(event) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventChip(
    event: CalendarEvent,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = event.color,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddEventDialog(
    date: LocalDate,
    hour: Int,
    onDismiss: () -> Unit,
    onAddEvent: (title: String, color: Color) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val colors = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
        Color(0xFFFB8C00), // Orange
        Color(0xFF8E24AA)  // Purple
    )
    var selectedColor by remember { mutableStateOf(colors.first()) }

    val isExtraRow = hour == EXTRA_ROW_HOUR
    val dialogTitle = if (isExtraRow) "Add Extra Text / Note" else "Add Event"
    val dateText = if (isExtraRow) {
        "${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))} (Extra Row)"
    } else {
        "${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))} at ${String.format(Locale.getDefault(), "%02d:00", hour)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isExtraRow) "Extra Text / Note" else "Event Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAddEvent(title.trim(), selectedColor)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditEventDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onUpdateEvent: (title: String, color: Color) -> Unit,
    onDeleteEvent: () -> Unit
) {
    var title by remember(event) { mutableStateOf(event.title) }
    val colors = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
        Color(0xFFFB8C00), // Orange
        Color(0xFF8E24AA)  // Purple
    )
    var selectedColor by remember(event) { mutableStateOf(event.color) }

    val isExtraRow = event.hour == EXTRA_ROW_HOUR
    val dateText = if (isExtraRow) {
        "${event.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))} (Extra Row)"
    } else {
        "${event.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))} at ${String.format(Locale.getDefault(), "%02d:00", event.hour)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit Event")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title / Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onUpdateEvent(title.trim(), selectedColor)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDeleteEvent,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun WeeklyCalendarPreview() {
    com.example.androidapp.ui.theme.AndroidAppTheme(darkTheme = true) {
        WeeklyCalendarScreen()
    }
}
