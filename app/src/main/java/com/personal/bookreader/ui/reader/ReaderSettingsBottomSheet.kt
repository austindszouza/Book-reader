package com.personal.bookreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.bookreader.data.settings.ReaderSettings
import kotlin.math.roundToInt
import org.readium.r2.navigator.preferences.Theme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    onDismissRequest: () -> Unit,
    onThemeSelected: (Theme) -> Unit,
    onLiveFontSizeChange: (Double) -> Unit,
    onCommitFontSize: (Double) -> Unit,
    onLineHeightSelected: (Double?) -> Unit,
    onFontFamilySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderFontSize by remember(settings.fontSize) { mutableDoubleStateOf(settings.fontSize) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Reader Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Theme
            Text(
                text = "Theme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeOptionCard(
                    title = "Light",
                    backgroundColor = Color(Theme.LIGHT.backgroundColor),
                    contentColor = Color(Theme.LIGHT.contentColor),
                    selected = settings.theme == Theme.LIGHT,
                    onClick = { onThemeSelected(Theme.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionCard(
                    title = "Sepia",
                    backgroundColor = Color(Theme.SEPIA.backgroundColor),
                    contentColor = Color(Theme.SEPIA.contentColor),
                    selected = settings.theme == Theme.SEPIA,
                    onClick = { onThemeSelected(Theme.SEPIA) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionCard(
                    title = "Dark",
                    backgroundColor = Color(Theme.DARK.backgroundColor),
                    contentColor = Color(Theme.DARK.contentColor),
                    selected = settings.theme == Theme.DARK,
                    onClick = { onThemeSelected(Theme.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Font Size",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(sliderFontSize * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newSize = (sliderFontSize - 0.1).coerceIn(0.75, 2.50)
                        val rounded = (newSize * 100).roundToInt() / 100.0
                        sliderFontSize = rounded
                        onLiveFontSizeChange(rounded)
                        onCommitFontSize(rounded)
                    },
                    enabled = sliderFontSize > 0.75
                ) {
                    Text(
                        text = "A-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = sliderFontSize.toFloat(),
                    onValueChange = { newFloat ->
                        val rounded = (newFloat * 100).roundToInt() / 100.0
                        sliderFontSize = rounded
                        onLiveFontSizeChange(rounded)
                    },
                    onValueChangeFinished = {
                        onCommitFontSize(sliderFontSize)
                    },
                    valueRange = 0.75f..2.50f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val newSize = (sliderFontSize + 0.1).coerceIn(0.75, 2.50)
                        val rounded = (newSize * 100).roundToInt() / 100.0
                        sliderFontSize = rounded
                        onLiveFontSizeChange(rounded)
                        onCommitFontSize(rounded)
                    },
                    enabled = sliderFontSize < 2.50
                ) {
                    Text(
                        text = "A+",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Line Spacing
            Text(
                text = "Line Spacing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.lineHeight == null,
                    onClick = { onLineHeightSelected(null) },
                    label = { Text("Publisher default") }
                )
                FilterChip(
                    selected = settings.lineHeight == 1.2,
                    onClick = { onLineHeightSelected(1.2) },
                    label = { Text("1.2 (Tight)") }
                )
                FilterChip(
                    selected = settings.lineHeight == 1.4,
                    onClick = { onLineHeightSelected(1.4) },
                    label = { Text("1.4 (Normal)") }
                )
                FilterChip(
                    selected = settings.lineHeight == 1.6,
                    onClick = { onLineHeightSelected(1.6) },
                    label = { Text("1.6 (Relaxed)") }
                )
                FilterChip(
                    selected = settings.lineHeight == 1.8,
                    onClick = { onLineHeightSelected(1.8) },
                    label = { Text("1.8 (Loose)") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Font Family
            Text(
                text = "Font Family",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.fontFamily == null,
                    onClick = { onFontFamilySelected(null) },
                    label = { Text("Publisher default") }
                )
                FilterChip(
                    selected = settings.fontFamily == "sans-serif",
                    onClick = { onFontFamilySelected("sans-serif") },
                    label = { Text("Sans-Serif", fontFamily = FontFamily.SansSerif) }
                )
                FilterChip(
                    selected = settings.fontFamily == "serif",
                    onClick = { onFontFamilySelected("serif") },
                    label = { Text("Serif", fontFamily = FontFamily.Serif) }
                )
                FilterChip(
                    selected = settings.fontFamily == "monospace",
                    onClick = { onFontFamilySelected("monospace") },
                    label = { Text("Monospace", fontFamily = FontFamily.Monospace) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Helper text indicating override of publisher styles
            Text(
                text = "Custom font family or line spacing overrides the book's publisher styles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    backgroundColor: Color,
    contentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
