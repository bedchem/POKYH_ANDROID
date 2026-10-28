package dev.plattnericus.pokyh.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.LocalStrings
import dev.plattnericus.pokyh.core.i18n.stringsFor
import dev.plattnericus.pokyh.core.recap.RecapStoryModel
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.home.ExamBlock
import dev.plattnericus.pokyh.ui.home.HomeGreeting
import dev.plattnericus.pokyh.ui.home.HomeViewModel
import dev.plattnericus.pokyh.ui.home.MensaBlock
import dev.plattnericus.pokyh.ui.home.MessagesBlock
import dev.plattnericus.pokyh.ui.mensa.FoodStyle
import dev.plattnericus.pokyh.ui.mensa.PixelPlate
import dev.plattnericus.pokyh.ui.mensa.PlateSize
import dev.plattnericus.pokyh.ui.profile.AccountMenuButton
import dev.plattnericus.pokyh.ui.recap.RecapSlidePreview
import dev.plattnericus.pokyh.ui.theme.PokyhAppTheme
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.timetable.specialDaySpecFor
import dev.plattnericus.pokyh.data.untis.DayKind

/**
 * Debug builds only: the app's new pieces with sample data, to look at them without signing in
 * — the account menu (tap the picture), the bell, the Home blocks, the pixel plates, the special
 * days' scenes and recap pages. Started with
 * `adb shell am start -n dev.plattnericus.project.debug/dev.plattnericus.pokyh.debug.DebugGalleryActivity`
 * (`--ez dark true` for the dark theme).
 */
@AndroidEntryPoint
class DebugGalleryActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            val lang by I18n.lang.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
                PokyhAppTheme(if (dark) PokyhThemeMode.Dark else PokyhThemeMode.Light) {
                    Gallery()
                }
            }
        }
    }
}

private fun entry(date: Int, subject: String, start: Int, end: Int, exam: Boolean = false) = TimetableEntry(
    id = date + start, lessonId = 1, date = date, startTime = start, endTime = end,
    subjectName = subject, subjectLong = subject, teacherName = "Huber", roomName = "A2.05",
    isExam = exam, isCancelled = false, isSubstitution = false, isAdditional = false,
)

private fun dish(id: String, name: String, tags: List<String> = emptyList()) =
    Dish(id = id, name = name, category = "Hauptgericht", date = java.time.LocalDate.now().toString(), allergens = emptyList(), tags = tags)

@androidx.compose.runtime.Composable
private fun Gallery() {
    val today = java.time.LocalDate.now()
    val tomorrow = today.plusDays(2)
    val examDate = tomorrow.year * 10000 + tomorrow.monthValue * 100 + tomorrow.dayOfMonth
    val dishes = listOf(dish("d1", "Spaghetti Carbonara", listOf("pasta")), dish("d2", "Gemüselasagne", listOf("vegetarisch")), dish("d3", "Wiener Schnitzel"))
    val week = listOf(
        HomeViewModel.MensaWeekDay(
            date = kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth),
            weekday = "", dateLabel = "", isToday = true, isPast = false, dishes = dishes,
        ),
    )
    val messages = listOf(
        MessagePreview(1, "Elternsprechtag am Donnerstag", "", "Maria Gruber", 1, java.time.LocalDateTime.now().toString(), false, false),
        MessagePreview(2, "Ausflug nach Bozen", "", "Thomas Pichler", 2, java.time.LocalDateTime.now().minusDays(1).toString(), true, true),
    )
    Column(
        Modifier.fillMaxSize().background(PokyhTheme.colors.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            dev.plattnericus.pokyh.ui.recap.RecapHeaderButton()
            Box(Modifier.width(8.dp))
            dev.plattnericus.pokyh.ui.bell.NotificationBell(onNavigate = {})
            Box(Modifier.width(8.dp))
            AccountMenuButton(onNavigate = {})
        }
        HomeGreeting("Guten Morgen", "Anna", "Dienstag, 27. Oktober · 4BFS", canReport = true, showActions = true, wide = false, onWrite = {}, onReport = {})
        ExamBlock(exam = entry(examDate, "Mathematik", 800, 850, exam = true), loaded = true, unknown = false, onOpen = {})
        ExamBlock(exam = null, loaded = true, unknown = false, onOpen = {})
        MessagesBlock(loading = false, messages = messages, unavailable = false, onAll = {}, onOpen = {})
        MensaBlock(
            loading = false, week = week,
            ratings = mapOf("d1" to DishRatingsData(mapOf("a" to 4.0, "b" to 5.0))),
            ratingsLoading = false, ratingsUnavailable = false, onAll = {}, onOpen = {},
        )
        PixelPlate(style = FoodStyle.Pasta, seed = "d1", modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FoodStyle.entries.forEach { PixelPlate(style = it, seed = it.name, size = PlateSize.Plate, modifier = Modifier.width(72.dp)) }
        }
        Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DayKind.HOLIDAY to 12, DayKind.HOLIDAY to 4, DayKind.ALL_CANCELLED to 10, DayKind.ALL_REPLACEMENT to 10, DayKind.FULL_DAY_EVENT to 10).forEach { (kind, month) ->
                SpecialDayCard(spec = specialDaySpecFor(kind, month), modifier = Modifier.weight(1f).height(260.dp), compact = true, plate = true)
            }
        }
        RecapSlidePreview(
            RecapStoryModel.Slide(
                "rate", RecapStoryModel.Theme.Mint, RecapStoryModel.Mood.Great, RecapStoryModel.Layout.Number, RecapStoryModel.Place.Bottom,
                lead = RecapStoryModel.Text("rateLead"),
                value = RecapStoryModel.Value.Count(3.4, decimals = 1, suffix = " %"),
                unit = RecapStoryModel.Text("rateUnit"),
                meter = 3.4 to 25.0,
                body = listOf(RecapStoryModel.Text("rateBodyGood", mapOf("hours" to "12", "left" to "150"))),
                quip = RecapStoryModel.Text("quipGood"),
            ),
            modifier = Modifier.fillMaxWidth().height(640.dp),
        )
        RecapSlidePreview(
            RecapStoryModel.Slide("summary", RecapStoryModel.Theme.Night, RecapStoryModel.Mood.Party, RecapStoryModel.Layout.Summary, RecapStoryModel.Place.Corner, value = RecapStoryModel.Value.Words("Dein Schuljahr"), quip = RecapStoryModel.Text("summaryQuip")),
            summary = listOf(RecapStoryModel.SummaryItem("hours", "cardHours", "812"), RecapStoryModel.SummaryItem("rate", "cardRate", "3,4 %"), RecapStoryModel.SummaryItem("average", "cardAverage", "7,85")),
            modifier = Modifier.fillMaxWidth().height(640.dp),
        )
        Text("End of gallery", color = PokyhTheme.colors.textTertiary)
    }
}
