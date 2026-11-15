package app.selene.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.ui.navigation.SeleneNavHost
import app.selene.ui.theme.SeleneTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PeriodDayFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: TestContainer
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        container = TestContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                SeleneTheme { SeleneNavHost() }
            }
        }
    }

    @After
    fun tearDown() = container.database.close()

    private fun day() = runBlocking { container.logRepository.getDay(today) }

    @Test
    fun addingAPeriodDayRequiresAMood() {
        compose.onNodeWithText("Noter mes règles").performClick()
        compose.onNodeWithText("Choisissez une humeur pour enregistrer le jour de règles.").assertIsDisplayed()
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Abondant").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Douleur, 2 sur 4, Modérée").performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Mal").performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").assertIsEnabled().performClick()
        compose.waitForIdle()

        val saved = day()
        assertEquals(PeriodLog(today, flow = 3, pain = 2, fatigue = 0), saved.period)
        assertEquals(Mood.BAD, saved.mood)
        compose.onNodeWithText("Jour 1").assertIsDisplayed()
    }

    @Test
    fun editingAPeriodDayFromItsDetail() {
        runBlocking { container.logRepository.savePeriodDay(PeriodLog(today, 2, 1, 1), Mood.NEUTRAL) }
        openTodayDetail()
        compose.onNodeWithText("Règles · flux moyen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Modifier : Règles · flux moyen").performClick()
        compose.onNodeWithText("Modifier").assertIsDisplayed()
        compose.onNodeWithContentDescription("Très abondant").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Très bien").performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitForIdle()

        assertEquals(4, day().period?.flow)
        assertEquals(Mood.VERY_GOOD, day().mood)
        compose.onNodeWithText("Règles · flux très abondant").assertIsDisplayed()
    }

    @Test
    fun deletingAPeriodDayAlsoDeletesItsMoodAndCanBeUndone() {
        runBlocking { container.logRepository.savePeriodDay(PeriodLog(today, 2, 1, 1), Mood.NEUTRAL) }
        openTodayDetail()
        compose.onNodeWithContentDescription("Supprimer : Règles · flux moyen").performClick()
        compose.onNodeWithText("Supprimer aussi l’humeur de ce jour").assertIsDisplayed()
        compose.onNode(hasText("Supprimer") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertNull(day().period)
        assertNull(day().mood)
        compose.onNodeWithText("Rien de noté ce jour-là.").assertIsDisplayed()

        compose.onNodeWithText("Annuler").performClick()
        compose.waitForIdle()
        assertEquals(Mood.NEUTRAL, day().mood)
        compose.onNodeWithText("Règles · flux moyen").assertIsDisplayed()
    }

    @Test
    fun theMoodOfAPeriodDayCannotBeDeletedAlone() {
        runBlocking { container.logRepository.savePeriodDay(PeriodLog(today, 2, 1, 1), Mood.NEUTRAL) }
        openTodayDetail()
        compose.onNodeWithText("Humeur : neutre").assertIsDisplayed()
        compose.onNodeWithContentDescription("Supprimer : Humeur : neutre").assertDoesNotExist()
        compose.onNodeWithText("Liée au jour de règles : supprimez les règles pour la retirer.").assertIsDisplayed()
    }

    @Test
    fun emptyStatesInviteToLogAndExplainTheTwoCycles() {
        compose.onNodeWithText("Notez le premier jour de vos règles pour voir votre cycle et les premières estimations.").assertIsDisplayed()
        compose.onNodeWithText("Analyse").performClick()
        compose.onNodeWithText("deux cycles complets", substring = true).assertIsDisplayed()
    }

    private fun openTodayDetail() {
        compose.onNodeWithText("Calendrier").performClick()
        compose.waitForIdle()
        val todayCell = hasContentDescription(DateFormats.dayMonth(today), substring = true) and
            hasContentDescription("aujourd’hui", substring = true)
        compose.onNodeWithTag(app.selene.ui.calendar.CALENDAR_LIST_TAG).performScrollToNode(todayCell)
        compose.onNode(todayCell).performClick()
        compose.waitForIdle()
    }
}
