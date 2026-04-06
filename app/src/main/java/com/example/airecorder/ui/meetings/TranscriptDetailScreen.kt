package com.example.airecorder.ui.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

@Composable
fun TranscriptDetailScreen(
    uiState: MeetingDetailUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val detail = uiState.detail ?: return
    DocumentDetailScreen(
        title = "Transcript",
        subtitle = detail.meeting.name,
        text = uiState.transcriptDraft.ifBlank { "No transcript available." },
        shareFilenamePrefix = "${detail.meeting.name}_transcript",
        shareSubject = "${detail.meeting.name} transcript",
        chooserTitle = "Share transcript",
        onBack = onBack,
    )
}

@Composable
internal fun DocumentDetailScreen(
    title: String,
    subtitle: String,
    text: String,
    shareFilenamePrefix: String,
    shareSubject: String,
    chooserTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7B8598),
                )
            }
            IconButton(onClick = { shareTextDocument(context, shareFilenamePrefix, text, shareSubject, chooserTitle) }) {
                Icon(Icons.Outlined.Share, contentDescription = chooserTitle)
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            factory = { viewContext ->
                ScrollView(viewContext).apply {
                    isVerticalScrollBarEnabled = true
                    scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
                    addView(
                        TextView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            textSize = 18f
                            setTextColor(android.graphics.Color.parseColor("#151220"))
                            setLineSpacing(0f, 1.25f)
                            setPadding(0, 0, 0, 48)
                        },
                    )
                }
            },
            update = { scrollView ->
                val textView = scrollView.getChildAt(0) as TextView
                textView.text = text
            },
        )
    }
}

private fun shareTextDocument(
    context: Context,
    filenamePrefix: String,
    text: String,
    subject: String,
    chooserTitle: String,
) {
    val safeName = filenamePrefix
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .ifBlank { "document" }
    val file = File(context.cacheDir, "$safeName.txt")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            chooserTitle,
        ),
    )
}
