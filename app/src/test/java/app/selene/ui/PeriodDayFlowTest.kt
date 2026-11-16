package app.selene.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
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
    fun addingAPeriodDayOnlyAsksForFlowAndPain() {
        compose.onNodeWithText("Noter mes règles").performClick()
        compose.onNodeWithText("Humeur").assertDoesNotExist()
        compose.onNodeWithText("Fatigue").assertDoesNotExist()

        compose.onNodeWithContentDescription("Abondant").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Douleur, 2 sur 4, Modérée").performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").assertIsEnabled().performClick()
        compose.waitForIdle()

        val saved = day()
        assertEquals(PeriodLog(today, flow = 3, pain = 2), saved.period)
        assertNull(saved.symptoms)
        compose.onNodeWithText("Jour 1").assertIsDisplayed()
    }

    @Test
    fun addingSeveralSymptomsOfTheDay() {
        compose.onNodeWithContentDescription("Ajouter").performClick()
        compose.onNodeWithText("Symptômes").performClick()
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Triste").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Anxieuse").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Irritabilité, 3 sur 4, Forte").performScrollTo().performClick()
        // Touching the selected level again clears it.
        compose.onNodeWithContentDescription("Fatigue, 1 sur 4, Légère").performScrollTo().performClick().performClick()
        compose.onNodeWithContentDescription("Nausées").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Spotting (sang hors règles)").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Boutons").performScrollTo().performClick().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("  Mal au genou ")
        compose.onNodeWithText("Enregistrer").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(
            SymptomLog(
                today,
                moods = setOf(Mood.SAD, Mood.ANXIOUS),
                symptoms = setOf(Symptom.NAUSEA, Symptom.SPOTTING),
                irritability = 3,
                fatigue = null,
                note = "Mal au genou",
            ),
            day().symptoms,
        )
        assertNull(day().period)
    }

    @Test
    fun editingAPeriodDayFromItsDetail() {
        runBlocking { container.logRepository.savePeriod(PeriodLog(today, 2, 1)) }
        openTodayDetail()
        compose.onNodeWithText("Règles · flux moyen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Modifier : Règles · flux moyen").performClick()
        compose.onNodeWithText("Modifier").assertIsDisplayed()
        compose.onNodeWithContentDescription("Très abondant").performScrollTo().performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitForIdle()

        assertEquals(4, day().period?.flow)
        compose.onNodeWithText("Règles · flux très abondant").assertIsDisplayed()
    }

    @Test
    fun editingSymptomsFromTheDayDetailIsPrefilled() {
        runBlocking {
            container.logRepository.saveSymptoms(SymptomLog(today, moods = setOf(Mood.CRYING), symptoms = setOf(Symptom.CRAMPS)))
        }
        openTodayDetail()
        compose.onNodeWithText("Symptômes du jour").assertIsDisplayed()
        compose.onNodeWithText("En larmes").assertIsDisplayed()
        compose.onNodeWithText("Crampes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Modifier : Symptômes du jour").performClick()
        compose.onNodeWithContentDescription("En larmes").assertIsOn()
        compose.onNodeWithContentDescription("Crampes").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitForIdle()

        assertEquals(SymptomLog(today, moods = setOf(Mood.CRYING)), day().symptoms)
    }

    @Test
    fun deletingAPeriodDayKeepsTheSymptomsAndCanBeUndone() {
        runBlocking {
            container.logRepository.savePeriod(PeriodLog(today, 2, 1))
            container.logRepository.saveSymptoms(SymptomLog(today, moods = setOf(Mood.NEUTRAL)))
        }
        openTodayDetail()
        compose.onNodeWithContentDescription("Supprimer : Règles · flux moyen").performClick()
        compose.waitForIdle()

        assertNull(day().period)
        assertEquals(setOf(Mood.NEUTRAL), day().moods)
        compose.onNodeWithText("Règles · flux moyen").assertDoesNotExist()

        compose.onNodeWithText("Annuler").performClick()
        compose.waitForIdle()
        assertEquals(PeriodLog(today, 2, 1), day().period)
        compose.onNodeWithText("Règles · flux moyen").assertIsDisplayed()
    }

    @Test
    fun deletingSymptomsCanBeUndone() {
        runBlocking { container.logRepository.saveSymptoms(SymptomLog(today, moods = setOf(Mood.NEUTRAL))) }
        openTodayDetail()
        compose.onNodeWithContentDescription("Supprimer : Symptômes du jour").performClick()
        compose.waitForIdle()
        assertNull(day().symptoms)
        compose.onNodeWithText("Rien de noté ce jour-là.").assertIsDisplayed()
        compose.onNodeWithText("Annuler").performClick()
        compose.waitForIdle()
        assertEquals(setOf(Mood.NEUTRAL), day().moods)
    }

    @Test
    fun emptyStatesInviteToLogAndExplainTheTwoCycles() {
        compose.onNodeWithText("Notez le premier jour de vos règles pour voir votre cycle et les premières estimations.").assertIsDisplayed()
        compose.onNodeWithText("Analyse").performClick()
        compose.onAllNodesWithText("deux cycles complets", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun analysisExplainsTheWeightedAverageWithTheUsersCycles() {
        runBlocking {
            var start = today.minusDays(10L + 27 + 28 + 30)
            for (length in listOf(27, 28, 30, 0)) {
                container.logRepository.savePeriod(PeriodLog(start, 2, 0))
                start = start.plusDays(length.toLong())
            }
        }
        compose.onNodeWithText("Analyse").performClick()
        compose.onNodeWithText("Comment c’est calculé ?").performScrollTo().assertIsDisplayed()
        // (27×1 + 28×2 + 30×3) ÷ 6 = 28,8
        compose.onNodeWithText("(27×1 + 28×2 + 30×3) ÷ 6 = 28,8 jours").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sans pondération, la moyenne serait de 28,3 jours.").assertExists()
        // Other sections start collapsed and open on tap.
        compose.onNodeWithText("Un oubli de saisie ne coupe pas", substring = true).assertDoesNotExist()
        // Scroll to the last section first so the header is not under the bottom bar when tapped.
        compose.onNodeWithText("Vos données restent sur votre téléphone").performScrollTo()
        compose.onNodeWithText("Découpage en cycles").assertIsDisplayed().performClick()
        compose.onNodeWithText("un oubli de saisie ne coupe pas", substring = true).performScrollTo().assertIsDisplayed()
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
