package com.wa.beastclient.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.domain.scheduling.ScheduledMessage
import com.wa.beastclient.domain.scheduling.ScheduledRepeatType
import com.wa.beastclient.domain.scheduling.formatScheduledDateTime
import com.wa.beastclient.ui.theme.WaColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleMessageDialog(
    chatJid: String,
    chatTitle: String,
    initialMessage: ScheduledMessage? = null,
    initialText: String = "",
    colors: WaColors,
    onDismiss: () -> Unit,
    onSave: (ScheduledMessage) -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    var text by remember { mutableStateOf(initialMessage?.text ?: initialText) }
    var repeatType by remember { mutableStateOf(initialMessage?.repeatType ?: ScheduledRepeatType.ONCE) }
    var selectedDays by remember { mutableStateOf(initialMessage?.customDaysOfWeek?.toSet() ?: emptySet()) }

    val initialCalendar = remember {
        Calendar.getInstance().apply {
            if (initialMessage != null && initialMessage.scheduledEpochMs > System.currentTimeMillis()) {
                timeInMillis = initialMessage.scheduledEpochMs
            } else {
                // Default to 1 hour from now, rounded to next 5 minutes
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, (get(Calendar.MINUTE) / 5) * 5)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    var scheduledYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var scheduledMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var scheduledDay by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }
    var scheduledHour by remember { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var scheduledMinute by remember { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }

    val formattedDate = remember(scheduledYear, scheduledMonth, scheduledDay) {
        val c = Calendar.getInstance().apply {
            set(scheduledYear, scheduledMonth, scheduledDay)
        }
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(c.time)
    }

    val formattedTime = remember(scheduledHour, scheduledMinute) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, scheduledHour)
            set(Calendar.MINUTE, scheduledMinute)
        }
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        SimpleDateFormat(pattern, Locale.getDefault()).format(c.time)
    }

    val daysOfWeek = remember {
        listOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (initialMessage != null) "Edit scheduled message" else "Schedule message",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (chatTitle.isNotBlank()) {
                    Text(
                        text = "To: $chatTitle",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Message Text Field
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message text") },
                    placeholder = { Text("Type a message to send later...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                // Date & Time Selectors
                Text(
                    text = "Date & Time",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Date picker button
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    scheduledYear = y
                                    scheduledMonth = m
                                    scheduledDay = d
                                },
                                scheduledYear,
                                scheduledMonth,
                                scheduledDay
                            ).apply {
                                datePicker.minDate = System.currentTimeMillis() - 1000L
                            }.show()
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accentGreen
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formattedDate,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }

                    // Time picker button
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    scheduledHour = h
                                    scheduledMinute = min
                                },
                                scheduledHour,
                                scheduledMinute,
                                is24Hour
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accentGreen
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formattedTime,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }

                // Repeat Type
                Text(
                    text = "Repeat",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduledRepeatType.values().forEach { type ->
                        val selected = repeatType == type
                        FilterChip(
                            selected = selected,
                            onClick = { repeatType = type },
                            label = { Text(type.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Custom Days Selector if CUSTOM_DAYS
                if (repeatType == ScheduledRepeatType.CUSTOM_DAYS) {
                    Text(
                        text = "Repeat on days",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        daysOfWeek.forEach { (calDay, label) ->
                            val isDaySelected = selectedDays.contains(calDay)
                            FilterChip(
                                selected = isDaySelected,
                                onClick = {
                                    selectedDays = if (isDaySelected) {
                                        selectedDays - calDay
                                    } else {
                                        selectedDays + calDay
                                    }
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank()) return@TextButton
                    val finalCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, scheduledYear)
                        set(Calendar.MONTH, scheduledMonth)
                        set(Calendar.DAY_OF_MONTH, scheduledDay)
                        set(Calendar.HOUR_OF_DAY, scheduledHour)
                        set(Calendar.MINUTE, scheduledMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val scheduledMs = finalCal.timeInMillis
                    val item = (initialMessage ?: ScheduledMessage(
                        chatJid = chatJid,
                        chatTitle = chatTitle,
                        text = text.trim(),
                        scheduledEpochMs = scheduledMs
                    )).copy(
                        text = text.trim(),
                        scheduledEpochMs = scheduledMs,
                        repeatType = repeatType,
                        customDaysOfWeek = if (repeatType == ScheduledRepeatType.CUSTOM_DAYS) selectedDays.toList() else emptyList(),
                        status = com.wa.beastclient.domain.scheduling.ScheduledStatus.PENDING,
                        failureReason = null
                    )
                    onSave(item)
                    onDismiss()
                },
                enabled = text.isNotBlank() && (repeatType != ScheduledRepeatType.CUSTOM_DAYS || selectedDays.isNotEmpty())
            ) {
                Text(
                    text = if (initialMessage != null) "Update" else "Schedule",
                    color = colors.accentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}
