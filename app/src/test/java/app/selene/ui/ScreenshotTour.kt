package app.selene.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.selene.ui.navigation.SeleneNavHost
import app.selene.ui.theme.SeleneTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Dev tool: renders the main screens to PNG. Run with -Pselene.screenshots=<dir>. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTour {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tour() {
        val dir = System.getProperty("selene.screenshots")
        assumeTrue(dir != null)
        val container = TestContainer(ApplicationProvider.getApplicationContext())
        val today = LocalDate.now()
        runBlocking {
            val repo = container.logRepository
            val lengths = listOf(29, 27, 28, 30, 28, 27)
            var start = today.minusDays(20L + lengths.sum())
            for (l in lengths + 0) {
                repeat(5) { i ->
                    val d = start.plusDays(i.toLong())
                    if (!d.isAfter(today)) repo.savePeriod(PeriodLog(d, listOf(3, 3, 2, 1, 1)[i], 2 - i / 2))
                }
                if (l > 0) {
                    repo.saveSymptoms(SymptomLog(start.plusDays(l - 2L), moods = setOf(Mood.SAD), symptoms = setOf(Symptom.CRAMPS)))
                    repo.saveSymptoms(SymptomLog(start.plusDays(l - 6L), moods = setOf(Mood.GOOD)))
                }
                start = start.plusDays(l.toLong())
            }
            repo.addEvent(today.minusDays(3), "Nuit blanche")
            repo.saveSymptoms(
                SymptomLog(
                    today.minusDays(3),
                    moods = setOf(Mood.DRAINED, Mood.ANXIOUS),
                    symptoms = setOf(Symptom.HEADACHE, Symptom.NAUSEA, Symptom.SPOTTING),
                    irritability = 2,
                    fatigue = 3,
                    note = "Réveillée à 4 h",
                ),
            )
        }
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) { SeleneTheme { SeleneNavHost() } }
        }
        fun shot(name: String) {
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("1-cycle")
        compose.onNodeWithText("Calendrier").performClick(); shot("2-calendar")
        compose.onNodeWithText("Analyse").performClick(); shot("3-analysis")
        compose.onNodeWithText("Comment c’est calculé ?").performScrollTo(); shot("3b-analysis-method")
        compose.onNodeWithText("Découpage en cycles").performClick()
        compose.onNodeWithText("Vos données restent sur votre téléphone").performScrollTo(); shot("3c-analysis-method-end")
        compose.onNodeWithText("Réglages").performClick(); shot("4-settings")
        compose.onNodeWithContentDescription("Ajouter").performClick(); shot("5-entry")
        compose.onNodeWithText("Symptômes").performClick(); shot("6-entry-symptoms")
        compose.onNodeWithText("Peau").performScrollTo(); shot("7-entry-symptoms-2")
        compose.onNodeWithText("Autre").performScrollTo(); shot("8-entry-symptoms-3")
        compose.onNodeWithText("Événement").performScrollTo().performClick(); shot("9-entry-event")
        compose.onNodeWithContentDescription("Fermer").performClick()
        compose.onNodeWithText("Calendrier").performClick()
        compose.waitForIdle()
        val day = hasContentDescription(DateFormats.dayMonth(today.minusDays(3)), substring = true)
        compose.onNodeWithTag(app.selene.ui.calendar.CALENDAR_LIST_TAG).performScrollToNode(day)
        compose.onNode(day).performClick(); shot("10-day-detail")
    }

    /** Launcher icon as the system draws it: circle mask, rounded-square mask, and themed (monochrome). */
    @Test
    fun launcherIcon() {
        val dir = System.getProperty("selene.screenshots")
        assumeTrue(dir != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = 432
        fun layer(id: Int, tint: Int? = null): android.graphics.drawable.Drawable =
            context.getDrawable(id)!!.mutate().apply {
                setBounds(0, 0, size, size)
                tint?.let { setTint(it) }
            }
        fun render(name: String, mask: android.graphics.Path, background: Int, foreground: android.graphics.drawable.Drawable) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.clipPath(mask)
            canvas.drawColor(background)
            foreground.draw(canvas)
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val circle = android.graphics.Path().apply { addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW) }
        val squircle = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.3f, size * 0.3f, android.graphics.Path.Direction.CW)
        }
        val bg = context.getColor(app.selene.R.color.background)
        render("icon-circle", circle, bg, layer(app.selene.R.drawable.ic_launcher_foreground))
        render("icon-squircle", squircle, bg, layer(app.selene.R.drawable.ic_launcher_foreground))
        render("icon-themed", circle, 0xFF2E3A33.toInt(), layer(app.selene.R.drawable.ic_launcher_monochrome, 0xFFC6E8CF.toInt()))
    }
}
