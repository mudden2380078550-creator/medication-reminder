package org.openmeds.reminder.ui.home

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.openmeds.reminder.ui.theme.MedicationReminderTheme

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addMedicationButtonReachesMinimumTouchTarget() {
        composeTestRule.setContent {
            MedicationReminderTheme {
                HomeScreen(state = HomeUiState(), onConfirmNextDose = {}, onAddMedication = {}, onMedicationClick = {})
            }
        }
        composeTestRule.onNodeWithText("添加药品").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
    }
}
