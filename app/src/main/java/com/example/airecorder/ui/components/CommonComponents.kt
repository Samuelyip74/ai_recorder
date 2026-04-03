package com.example.airecorder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.airecorder.domain.model.SummaryStatus
import com.example.airecorder.domain.model.TranscriptStatus

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun EditableTextSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = title, modifier = modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            minLines = 5,
        )
    }
}

@Composable
fun StatusBadges(
    transcriptStatus: TranscriptStatus,
    summaryStatus: SummaryStatus,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBadge(label = transcriptStatus.toBadgeLabel("Transcript"), color = transcriptStatus.badgeColor())
        StatusBadge(label = summaryStatus.toBadgeLabel("Summary"), color = summaryStatus.badgeColor())
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Text(
        text = label,
        modifier = Modifier
            .background(color = color, shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
    )
}

private fun TranscriptStatus.badgeColor(): Color = when (this) {
    TranscriptStatus.NOT_STARTED -> Color(0xFFF3F4F6)
    TranscriptStatus.PROCESSING -> Color(0xFFDBEAFE)
    TranscriptStatus.COMPLETED -> Color(0xFFDCFCE7)
    TranscriptStatus.FAILED -> Color(0xFFFECACA)
}

private fun SummaryStatus.badgeColor(): Color = when (this) {
    SummaryStatus.NOT_STARTED -> Color(0xFFF3F4F6)
    SummaryStatus.PROCESSING -> Color(0xFFDBEAFE)
    SummaryStatus.COMPLETED -> Color(0xFFDCFCE7)
    SummaryStatus.FAILED -> Color(0xFFFECACA)
}

private fun TranscriptStatus.toBadgeLabel(prefix: String): String = when (this) {
    TranscriptStatus.NOT_STARTED -> prefix
    TranscriptStatus.PROCESSING -> "$prefix in progress"
    TranscriptStatus.COMPLETED -> prefix
    TranscriptStatus.FAILED -> "$prefix failed"
}

private fun SummaryStatus.toBadgeLabel(prefix: String): String = when (this) {
    SummaryStatus.NOT_STARTED -> prefix
    SummaryStatus.PROCESSING -> "$prefix in progress"
    SummaryStatus.COMPLETED -> prefix
    SummaryStatus.FAILED -> "$prefix failed"
}
