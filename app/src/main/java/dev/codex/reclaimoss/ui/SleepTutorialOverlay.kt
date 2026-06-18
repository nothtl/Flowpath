package dev.codex.reclaimoss.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
enum class TutorialStep(val stepNumber: Int) {
    DURATION(0),
    DAYS(1),
    WINDOW(2),
    SAVE(3),
    MISSING_DAYS(4),
    TASK_NAME(10),
    TASK_OPTIONS(11),
    TASK_RULES(12),
    TASK_SAVE(13);

    val title: String
        @Composable get() = when (this) {
            DURATION -> "How long do you sleep?"
            DAYS -> "Which days of the week?"
            WINDOW -> "When can you sleep?"
            SAVE -> "Save your sleep schedule"
            MISSING_DAYS -> "Missing days"
            TASK_NAME -> "Name and duration"
            TASK_OPTIONS -> "Schedule & repeat"
            TASK_RULES -> "Rules"
            TASK_SAVE -> "Save your task"
        }

    val description: String
        @Composable get() = when (this) {
            DURATION -> "How many hours do you usually sleep? Adjust the wheel below to match your typical night."
            DAYS -> "Tap the days you want this sleep schedule to cover. You can add different sleep times for different days later."
            WINDOW -> "Drag the slider handles to set the earliest bedtime and latest wake-up time. Your sleep will be scheduled somewhere inside this range."
            SAVE -> "Tap \"Save sleep\" to create your sleep schedule. Repeat for each day of the week."
            MISSING_DAYS -> "Some days still need a sleep schedule. Tap the unselected days above to set them up."
            TASK_NAME -> "Give your task a name and set how long it takes. Flowpath automatically schedules tasks around your sleep and other events."
            TASK_OPTIONS -> "Expand Schedule & Repeat to set the first occurrence date, time mode, and how the task repeats. Set a deadline if needed."
            TASK_RULES -> "Expand Rules to configure overlap policy, splitting, dependencies, and reminders. The defaults work for most tasks."
            TASK_SAVE -> "Tap Save Task. Flowpath finds the best available time in your schedule and fits the task in."
        }
}

fun taskCreationSteps(): List<TutorialStep> = listOf(TutorialStep.TASK_NAME, TutorialStep.TASK_OPTIONS, TutorialStep.TASK_RULES, TutorialStep.TASK_SAVE)
fun missingDaysSteps(missingCount: Int): List<TutorialStep> = listOf(TutorialStep.DURATION, TutorialStep.DAYS, TutorialStep.WINDOW, TutorialStep.SAVE)
fun singleMissingStep(): List<TutorialStep> = listOf(TutorialStep.MISSING_DAYS)

@Composable
fun SleepTutorialOverlay(
    step: TutorialStep,
    sectionBounds: Rect?,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (sectionBounds == null) return

    val dimColor = Color.Black.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
    ) {
        val screenHeight = constraints.maxHeight.toFloat()
        val screenWidth = constraints.maxWidth.toFloat()

        // Dimmed background with cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val holePadding = 8.dp.toPx()
            val holeRect = Rect(
                left = sectionBounds.left - holePadding,
                top = sectionBounds.top - holePadding,
                right = sectionBounds.right + holePadding,
                bottom = sectionBounds.bottom + holePadding,
            )
            val holeCornerRadius = 20.dp.toPx()

            // Draw dim overlay with a hole cut out
            val fullRect = Path().apply { addRect(Rect(Offset.Zero, Size(screenWidth, screenHeight))) }
            val hole = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        holeRect, holeCornerRadius, holeCornerRadius,
                    ),
                )
            }
            val dimWithHole = Path().apply {
                op(fullRect, hole, PathOperation.Difference)
            }
            drawPath(path = dimWithHole, color = dimColor)

            // Subtle highlight ring around the hole
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = holeRect.topLeft,
                size = holeRect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(holeCornerRadius),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Tooltip card — position near the highlighted section
        val sectionCenterY = sectionBounds.top + (sectionBounds.bottom - sectionBounds.top) / 2f
        val tooltipOnBottom = sectionCenterY < screenHeight * 0.55f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (tooltipOnBottom) (sectionBounds.bottom / LocalDensity.current.density).dp + 12.dp else 0.dp,
                    bottom = if (!tooltipOnBottom) ((screenHeight - sectionBounds.top) / LocalDensity.current.density).dp + 12.dp else 0.dp,
                    start = 20.dp,
                    end = 20.dp,
                ),
            contentAlignment = if (tooltipOnBottom) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { currentStep ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Step dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TutorialStep.entries.forEach { s ->
                                Surface(
                                    modifier = Modifier
                                        .size(if (s == currentStep) 7.dp else 5.dp)
                                        .clip(CircleShape),
                                    color = if (s.ordinal <= currentStep.ordinal)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant,
                                ) {}
                                if (s.ordinal < TutorialStep.entries.lastIndex) {
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }

                        Text(
                            currentStep.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            currentStep.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Exit")
                            }
                            Button(
                                onClick = if (isLastStep) onDismiss else onNext,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(if (isLastStep) "Done" else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}
