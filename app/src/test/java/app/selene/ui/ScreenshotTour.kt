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
                    if (!d.isAfter(today)) repo.savePeriodDay(PeriodLog(d, listOf(3, 3, 2, 1, 1)[i], 2 - i / 2, 1), Mood.entries[(i + 1) % 5])
                }
                if (l > 0) {
                    repo.saveMood(start.plusDays(l - 2L), Mood.BAD)
                    repo.saveMood(start.plusDays(l - 6L), Mood.GOOD)
                }
                start = start.plusDays(l.toLong())
            }
            repo.addEvent(today.minusDays(3), "Nuit blanche")
            repo.saveMood(today.minusDays(3), Mood.BAD)
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
        compose.onNodeWithText("Réglages").performClick(); shot("4-settings")
        compose.onNodeWithContentDescription("Ajouter").performClick(); shot("5-entry")
        compose.onNodeWithText("Événement").performClick(); shot("6-entry-event")
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
