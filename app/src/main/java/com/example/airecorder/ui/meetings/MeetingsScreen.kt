package com.example.airecorder.ui.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.airecorder.domain.model.Meeting
import com.example.airecorder.util.formatDateTime
import com.example.airecorder.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeetingsScreen(
    paddingValues: PaddingValues,
    uiState: MeetingsUiState,
    onQueryChange: (String) -> Unit,
    onDeleteMeeting: (Long) -> Unit,
    onMeetingClick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recordings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        if (uiState.meetings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("No recordings yet", color = Color(0xFF8A94A6))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var lastSection: String? = null
                items(uiState.meetings, key = { it.id }) { meeting ->
                    val section = meeting.createdAt.sectionTitle()
                    if (section != lastSection) {
                        lastSection = section
                        Text(
                            text = section,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF7B8598),
                        )
                    }
                    MeetingListItem(
                        meeting = meeting,
                        onDelete = { onDeleteMeeting(meeting.id) },
                        onClick = { onMeetingClick(meeting.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingListItem(
    meeting: Meeting,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFEF4444), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Delete",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.End,
                    )
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF7B8598),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meeting.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF20263A))
                    Text(
                        "${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(meeting.createdAt))} • ${meeting.durationMs.formatDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7B8598),
                    )
                    Text(
                        if (meeting.recordingMode == com.example.airecorder.domain.model.RecordingMode.MIC) "Mic recording" else "Playback capture",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4A79D9),
                    )
                }
            }
        }
    }
}

private fun Long.sectionTitle(): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = this@sectionTitle }
    return when {
        now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR) -> "Today"
        now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) - date.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        else -> formatDateTime()
    }
}
