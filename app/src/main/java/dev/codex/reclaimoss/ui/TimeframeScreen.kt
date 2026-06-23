package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimeframeEditorScreen(
    padding: PaddingValues,
    initialDraft: TimeframeDraft,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: (TimeframeDraft) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var draft by rememberSaveable(
        initialDraft.id,
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { listOf(it.id, it.name, it.startDate.toString(), it.endDate.toString(), it.colorHex) },
            restore = {
                TimeframeDraft(
                    id = it[0] as String,
                    name = it[1] as String,
                    startDate = LocalDate.parse(it[2] as String),
                    endDate = LocalDate.parse(it[3] as String),
                    colorHex = it[4] as String,
                )
            },
        ),
    ) { mutableStateOf(initialDraft) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    var showStartSheet by rememberSaveable { mutableStateOf(false) }
    var showEndSheet by rememberSaveable { mutableStateOf(false) }
    var showColorSheet by rememberSaveable { mutableStateOf(false) }
    val palette = remember {
        listOf(
            "#F4B6D2", "#88D1FF", "#F7C948", "#74C69D",
            "#FF9F6E", "#9B8AFB", "#4CC9F0", "#F28482",
            "#90BE6D", "#577590", "#B8C0FF", "#F2B5D4",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (draft.id.isBlank()) "Add timeframe" else "Edit timeframe",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        CreateFormCard {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                SettingsSummaryRow(
                    title = "Start",
                    summary = formatter.format(draft.startDate),
                    onClick = { showStartSheet = true },
                )
                SettingsSummaryRow(
                    title = "End",
                    summary = formatter.format(draft.endDate),
                    onClick = { showEndSheet = true },
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showColorSheet = true },
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(parseTimeframeColor(draft.colorHex), RoundedCornerShape(999.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
                        )
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { onSave(draft) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }
            if (onDelete != null && draft.id.isNotBlank()) {
                OutlinedButton(
                    onClick = { onDelete(draft.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Delete")
                }
            }
        }
    }

    // Start date popup
    if (showStartSheet) {
        ModalBottomSheet(onDismissRequest = { showStartSheet = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Start date", style = MaterialTheme.typography.titleLarge)
                DateField(
                    label = "Start",
                    value = formatter.format(draft.startDate),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val selected = LocalDate.of(year, month + 1, day)
                                draft = draft.copy(
                                    startDate = selected,
                                    endDate = if (draft.endDate.isBefore(selected)) selected else draft.endDate,
                                )
                            },
                            draft.startDate.year,
                            draft.startDate.monthValue - 1,
                            draft.startDate.dayOfMonth,
                        ).show()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // End date popup
    if (showEndSheet) {
        ModalBottomSheet(onDismissRequest = { showEndSheet = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("End date", style = MaterialTheme.typography.titleLarge)
                DateField(
                    label = "End",
                    value = formatter.format(draft.endDate),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                draft = draft.copy(endDate = LocalDate.of(year, month + 1, day))
                            },
                            draft.endDate.year,
                            draft.endDate.monthValue - 1,
                            draft.endDate.dayOfMonth,
                        ).show()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Color popup
    if (showColorSheet) {
        ModalBottomSheet(onDismissRequest = { showColorSheet = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Color", style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    palette.forEach { hex ->
                        val selected = draft.colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(parseTimeframeColor(hex), RoundedCornerShape(999.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable { draft = draft.copy(colorHex = hex) },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.colorHex,
                    onValueChange = { draft = draft.copy(colorHex = it) },
                    label = { Text("Hex color") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
