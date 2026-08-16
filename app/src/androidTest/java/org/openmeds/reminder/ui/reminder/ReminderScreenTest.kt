package org.openmeds.reminder.ui.reminder

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.ui.theme.MedicationReminderTheme

class ReminderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionsAreVisibleAndReachMinimumTouchTarget() {
        composeTestRule.setContent {
            MedicationReminderTheme {
                ReminderScreen(state = reminderState(), onTake = {}, onSnooze = {}, onSkip = {}, onAllHandled = {})
            }
        }
        composeTestRule.onNodeWithText("已服用").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
    }

    private fun reminderState() = ReminderUiState(
        items = listOf(
            ReminderItem(
                eventId = 1L,
                medicationName = "药A",
                doseText = "1 片",
                scheduledTime = LocalTime.of(9, 0),
                state = DoseState.PENDING
            )
        )
    )
}
