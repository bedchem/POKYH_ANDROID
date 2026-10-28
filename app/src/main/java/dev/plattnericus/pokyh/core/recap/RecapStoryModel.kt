package dev.plattnericus.pokyh.core.recap

import dev.plattnericus.pokyh.data.backend.BackendClient
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The pages of a recap story — the web's `lib/recap-story.ts`: which pages there are, what each
 * says (keys of the recap texts with their values) and how the mascot reacts. Every figure has
 * its own tiers, from the crown for not one hour missed to covering its eyes above 25 %. A page
 * without data is left out.
 */
object RecapStoryModel {
    enum class Mood { Wave, Legend, Great, Good, Ok, Meh, Worried, Alarm, Proud, Love, Focused, Sleepy, Party, Cool, Curious, Peek, Angry, Angel, Zap, Night, Early, Cheer }
    enum class Theme { Night, Ocean, Sunset, Forest, Dusk, Candy, Gold, Mint, Grape, Teal, Ember, Alarm, Brand, Sky, Rose }
    enum class Tier { Legend, Great, Good, Ok, Meh, Worried, Alarm }
    enum class Layout { Intro, Number, List, Summary }
    enum class Place { Bottom, Left, Right, Corner }

    data class Text(val key: String, val vars: Map<String, Any> = emptyMap())

    sealed interface Value {
        data class Count(val n: Double, val decimals: Int = 0, val prefix: String = "", val suffix: String = "", val from: Double = 0.0) : Value
        data class Words(val text: String) : Value
    }

    data class ListRow(val label: String, val labelKey: String? = null, val value: Double, val display: String)

    data class Slide(
        val id: String,
        val theme: Theme,
        val mood: Mood,
        val layout: Layout,
        val place: Place,
        val lead: Text? = null,
        val value: Value? = null,
        val unit: Text? = null,
        val body: List<Text> = emptyList(),
        val quip: Text? = null,
        val list: List<ListRow> = emptyList(),
        /** The absence rate against the 25 % limit. */
        val meter: Pair<Double, Double>? = null,
    )

    data class SummaryItem(val id: String, val label: String, val value: String)

    interface Fmt {
        /** YYYYMMDD → "Dienstag, 14. März" */
        fun date(n: Int): String
        /** YYYY-MM-DD → "1. September 2025" */
        fun day(iso: String): String
        /** Monday = 0 → "Montag" */
        fun weekday(i: Int): String
        /** Minutes of the day → "07:45" */
        fun time(minutes: Int): String
        fun num(n: Double, digits: Int = 0): String
        fun dish(name: BackendClient.RecapDishName): String
    }

    class Input(
        val title: String,
        val firstName: String,
        val periodFrom: String,
        val periodTo: String,
        val sections: List<String>,
        val untis: RecapFigures.Untis?,
        val pokyh: BackendClient.RecapPokyh?,
    )

    const val ABSENCE_LIMIT = 25.0

    fun absenceTier(rate: Double, missedMinutes: Int): Tier = when {
        missedMinutes <= 0 -> Tier.Legend
        rate < 2 -> Tier.Great
        rate < 5 -> Tier.Good
        rate < 10 -> Tier.Ok
        rate < 15 -> Tier.Meh
        rate < ABSENCE_LIMIT -> Tier.Worried
        else -> Tier.Alarm
    }

    private fun gradeTier(average: Double): String = when {
        average >= 9 -> "legend"
        average >= 8 -> "great"
        average >= 7 -> "good"
        average >= 6 -> "ok"
        else -> "low"
    }

    private fun tierMood(t: Tier) = Mood.valueOf(t.name)
    private fun tierTheme(t: Tier) = when (t) {
        Tier.Legend -> Theme.Gold; Tier.Great -> Theme.Mint; Tier.Good -> Theme.Teal; Tier.Ok -> Theme.Sky
        Tier.Meh -> Theme.Dusk; Tier.Worried -> Theme.Ember; Tier.Alarm -> Theme.Alarm
    }
    private fun tierQuip(t: Tier) = "quip${t.name}"
    private fun tierBody(t: Tier) = "rateBody${t.name}"

    private fun hours(minutes: Int) = (minutes / 60.0).roundToInt()
    private fun maxIndex(values: List<Int>): Int = values.indices.fold(0) { best, i -> if (values[i] > values[best]) i else best }

    private fun schoolSlides(u: RecapFigures.Untis, withAbsences: Boolean, f: Fmt): List<Slide> {
        val s = u.school ?: return emptyList()
        if (s.lessons == 0) return emptyList()
        val out = ArrayList<Slide>()
        val missed = if (withAbsences) u.absences?.missedMinutes ?: 0 else 0
        val attended = maxOf(0, s.minutes - missed)
        val body = mutableListOf(Text("hoursBody", mapOf("days" to f.num(s.days.toDouble()), "lessons" to f.num(s.lessons.toDouble()))))
        if (!u.complete) body.add(Text("partial", mapOf("n" to (u.weeksTotal - u.weeksLoaded))))
        out.add(Slide(
            "hours", Theme.Ocean, Mood.Proud, Layout.Number, Place.Bottom,
            lead = Text("hoursLead"), value = Value.Count(hours(attended).toDouble()), unit = Text("hoursUnit"), body = body,
            quip = Text("hoursQuip", mapOf("days" to f.num(attended / 60.0 / 24, 1))),
        ))
        s.subjects.firstOrNull()?.let { top ->
            out.add(Slide(
                "subject", Theme.Sunset, Mood.Love, Layout.List, Place.Corner,
                lead = Text("subjectLead"), value = Value.Words(top.name),
                unit = Text("subjectUnit", mapOf("hours" to f.num(hours(top.value).toDouble()), "lessons" to f.num(top.lessons.toDouble()))),
                list = s.subjects.map { ListRow(it.name, value = it.value.toDouble(), display = "${f.num(hours(it.value).toDouble())} h") },
                quip = Text("subjectQuip"),
            ))
        }
        s.teachers.firstOrNull()?.let { teacher ->
            val room = s.rooms.firstOrNull()
            out.add(Slide(
                "teacher", Theme.Forest, Mood.Wave, Layout.Number, Place.Right,
                lead = Text("teacherLead"), value = Value.Words(teacher.name),
                unit = Text("teacherUnit", mapOf("n" to f.num(teacher.lessons.toDouble()))),
                body = if (room != null) listOf(Text("teacherRoom", mapOf("room" to room.name, "n" to f.num(room.lessons.toDouble())))) else emptyList(),
                quip = Text("teacherQuip"),
            ))
        }
        s.longestDay?.let { (date, start, end) ->
            out.add(Slide(
                "longest", Theme.Dusk, Mood.Sleepy, Layout.Number, Place.Left,
                lead = Text("longestLead"), value = Value.Words(f.date(date)),
                unit = Text("longestUnit", mapOf("start" to f.time(start), "end" to f.time(end))),
                body = if (s.earliest != null && s.latest != null) listOf(Text("longestBody", mapOf("early" to f.time(s.earliest.second), "late" to f.time(s.latest.second)))) else emptyList(),
                quip = Text("longestQuip"),
            ))
        }
        val subs = when {
            s.substitutions == 1 -> listOf(Text("cancelledSubsOne"))
            s.substitutions > 1 -> listOf(Text("cancelledSubs", mapOf("n" to f.num(s.substitutions.toDouble()))))
            else -> emptyList()
        }
        out.add(Slide(
            "cancelled",
            if (s.cancelled == 0) Theme.Teal else Theme.Candy,
            if (s.cancelled == 0) Mood.Cool else if (s.cancelled >= 30) Mood.Party else Mood.Cheer,
            Layout.Number, Place.Bottom,
            lead = Text("cancelledLead"), value = Value.Count(s.cancelled.toDouble()),
            unit = Text(if (s.cancelled == 1) "cancelledUnitOne" else "cancelledUnit"),
            body = if (s.cancelled == 0) listOf(Text("cancelledNone")) + subs else subs,
            quip = Text(if (s.cancelled == 0) "cancelledQuipNone" else if (s.cancelled >= 30) "cancelledQuipMany" else "cancelledQuipSome"),
        ))
        return out
    }

    private fun absenceSlides(u: RecapFigures.Untis, f: Fmt): List<Slide> {
        val a = u.absences ?: return emptyList()
        val out = ArrayList<Slide>()
        val missedHours = a.missedMinutes / 60.0
        if (a.rate != null && a.possibleMinutes != null && a.possibleMinutes > 0) {
            val tier = absenceTier(a.rate, a.missedMinutes)
            val left = maxOf(0.0, a.possibleMinutes * ABSENCE_LIMIT / 100 - a.missedMinutes) / 60
            out.add(Slide(
                "rate", tierTheme(tier), tierMood(tier), Layout.Number, if (tier == Tier.Alarm || tier == Tier.Worried) Place.Right else Place.Bottom,
                lead = Text("rateLead"),
                value = Value.Count(a.rate, decimals = if (a.rate == 0.0) 0 else 1, suffix = " %"),
                unit = Text("rateUnit"),
                meter = a.rate to ABSENCE_LIMIT,
                body = listOf(Text(tierBody(tier), mapOf("hours" to f.num(missedHours.roundToInt().toDouble()), "left" to f.num(floor(left))))),
                quip = Text(tierQuip(tier)),
            ))
        } else {
            val tier = if (a.missedMinutes == 0) Tier.Legend else Tier.Ok
            out.add(Slide(
                "missed", tierTheme(tier), tierMood(tier), Layout.Number, Place.Bottom,
                lead = Text("missedLead"), value = Value.Count(missedHours.roundToInt().toDouble()), unit = Text("missedUnit"),
                body = if (a.missedMinutes == 0) listOf(Text("missedNone")) else emptyList(),
                quip = Text(tierQuip(tier)),
            ))
        }
        if (a.missedMinutes <= 0) return out
        val excusedShare = (a.excusedMinutes.toDouble() / a.missedMinutes * 100).roundToInt()
        val open = when (a.openEntries) {
            0 -> Text("detailsAllDone")
            1 -> Text("detailsOpenOne")
            else -> Text("detailsOpen", mapOf("n" to f.num(a.openEntries.toDouble())))
        }
        if (a.entries > 0) {
            out.add(Slide(
                "details", if (a.openEntries == 0) Theme.Mint else Theme.Ember, if (a.openEntries == 0) Mood.Good else Mood.Curious, Layout.Number, Place.Left,
                lead = Text("detailsLead"), value = Value.Count(a.entries.toDouble()),
                unit = if (a.entries == 1) Text("detailsUnitOne") else Text("detailsUnit", mapOf("days" to f.num(a.missedDays.toDouble()))),
                body = listOf(Text("detailsExcused", mapOf("n" to excusedShare)), open),
                quip = Text(if (a.openEntries == 0) "detailsQuipDone" else "detailsQuipOpen"),
            ))
        }
        a.bySubject.firstOrNull()?.let { subject ->
            val day = maxIndex(a.byWeekday.toList())
            out.add(Slide(
                "pattern", Theme.Rose, Mood.Peek, Layout.Number, Place.Right,
                lead = Text("patternLead"), value = Value.Words(subject.name),
                unit = Text("patternUnit", mapOf("hours" to f.num(maxOf(1, (subject.value / 60.0).roundToInt()).toDouble()))),
                body = if (a.byWeekday[day] > 0) listOf(Text("patternBody", mapOf("weekday" to f.weekday(day)))) else emptyList(),
                quip = Text(if (day == 0) "patternQuipMonday" else if (day == 4) "patternQuipFriday" else "patternQuip"),
            ))
        }
        return out
    }

    private fun gradeSlides(u: RecapFigures.Untis, f: Fmt): List<Slide> {
        val g = u.grades ?: return emptyList()
        if (g.count == 0) return emptyList()
        val tier = gradeTier(g.average)
        val mood = when (tier) { "legend" -> Mood.Legend; "great" -> Mood.Great; "good" -> Mood.Good; "ok" -> Mood.Ok; else -> Mood.Worried }
        val quip = "gradesQuip" + tier.replaceFirstChar { it.uppercase() }
        val out = arrayListOf(Slide(
            "grades", if (tier == "low") Theme.Ember else Theme.Grape, mood, Layout.Number, Place.Bottom,
            lead = Text("gradesLead"), value = Value.Count(g.count.toDouble()),
            unit = Text(if (g.count == 1) "gradesUnitOne" else "gradesUnit"),
            body = listOf(
                Text("gradesBody", mapOf("avg" to f.num(g.average, 2))),
                if (g.negative > 0) Text("gradesMixed", mapOf("pos" to f.num(g.positive.toDouble()), "neg" to f.num(g.negative.toDouble()))) else Text("gradesAllPositive"),
            ),
            quip = Text(quip),
        ))
        g.best?.let { best ->
            out.add(Slide(
                "best", Theme.Gold, if (gradeTier(best.average) == "legend") Mood.Party else Mood.Proud, Layout.List, Place.Corner,
                lead = Text("bestLead"), value = Value.Words(best.name),
                unit = if (best.count == 1) Text("bestUnitOne", mapOf("avg" to f.num(best.average, 2))) else Text("bestUnit", mapOf("avg" to f.num(best.average, 2), "n" to best.count)),
                list = g.subjects.take(4).map { ListRow(it.name, value = it.average, display = f.num(it.average, 2)) },
                body = g.top?.let { (v, name, date) -> listOf(Text("bestTop", mapOf("value" to f.num(v, if (v % 1 != 0.0) 2 else 0), "subject" to name, "date" to f.date(date)))) } ?: emptyList(),
                quip = Text("bestQuip"),
            ))
        }
        return out
    }

    private fun examSlides(u: RecapFigures.Untis, f: Fmt): List<Slide> {
        val exams = u.exams ?: 0
        val homework = u.homework ?: 0
        if (exams == 0 && homework == 0) return emptyList()
        val examFirst = exams > 0
        return listOf(Slide(
            "exams", Theme.Teal, Mood.Focused, Layout.Number, Place.Right,
            lead = Text("examsLead"), value = Value.Count((if (examFirst) exams else homework).toDouble()),
            unit = Text(if (examFirst) (if (exams == 1) "examsUnitOne" else "examsUnit") else "homeworkUnit"),
            body = if (examFirst && homework > 0) listOf(Text("examsHomework", mapOf("n" to f.num(homework.toDouble())))) else emptyList(),
            quip = Text("examsQuip"),
        ))
    }

    private fun classregSlides(u: RecapFigures.Untis): List<Slide> {
        val n = u.classreg ?: return emptyList()
        val level = if (n == 0) "None" else if (n <= 3) "Few" else "Many"
        return listOf(Slide(
            "classreg",
            when (level) { "None" -> Theme.Sky; "Few" -> Theme.Dusk; else -> Theme.Ember },
            when (level) { "None" -> Mood.Angel; "Few" -> Mood.Meh; else -> Mood.Angry },
            Layout.Number, Place.Bottom,
            lead = Text("classregLead"), value = Value.Count(n.toDouble()),
            body = listOf(Text("classreg$level")),
            quip = Text("classregQuip$level"),
        ))
    }

    private fun pokyhSlides(p: BackendClient.RecapPokyh, f: Fmt, schoolDays: Int): List<Slide> {
        val t = p.totals
        val c = p.created
        val madeCount = if (c != null) c.todosDone + c.reminders + c.comments + c.ratings else 0
        if (t == null || t.activeDays == 0) {
            if (madeCount > 0 || p.favouriteDish != null || p.covered == null) return emptyList()
            return listOf(Slide(
                "pokyh-none", Theme.Night, Mood.Curious, Layout.Number, Place.Right,
                lead = Text("pokyhNoneLead"), unit = Text("pokyhNoneUnit"), body = listOf(Text("pokyhNoneBody")), quip = Text("pokyhNoneQuip"),
            ))
        }
        val out = ArrayList<Slide>()
        val zap = schoolDays > 0 && t.activeDays >= schoolDays * 0.6
        out.add(Slide(
            "days", Theme.Brand, if (zap) Mood.Zap else Mood.Wave, Layout.Number, Place.Bottom,
            lead = Text("daysLead"), value = Value.Count(t.activeDays.toDouble()),
            unit = Text(if (t.activeDays == 1) "daysUnitOne" else "daysUnit"),
            body = listOf(if (t.longestStreak == 1) Text("daysBodyOne") else Text("daysBody", mapOf("n" to f.num(t.longestStreak.toDouble())))),
            quip = Text(if (zap) "daysQuipZap" else "daysQuip"),
        ))
        val weekdays = p.weekdays
        val day = if (weekdays.isEmpty()) 0 else maxIndex(weekdays)
        val hoursOfDay = p.hours
        if (p.usageConsent && t.activeSeconds >= 60 && hoursOfDay != null) {
            val hour = maxIndex(hoursOfDay)
            val night = hour >= 21 || hour < 5
            val early = hour in 5..7
            val asHours = t.activeSeconds >= 3600
            out.add(Slide(
                "time", if (night) Theme.Night else if (early) Theme.Sky else Theme.Sunset, if (night) Mood.Night else if (early) Mood.Early else Mood.Cool, Layout.Number, Place.Left,
                lead = Text("timeLead"),
                value = Value.Count((if (asHours) (t.activeSeconds / 3600.0) else (t.activeSeconds / 60.0)).roundToInt().toDouble()),
                unit = Text(if (asHours) "timeUnitHours" else "timeUnitMinutes"),
                body = listOf(Text("timeBody", mapOf("hour" to hour, "weekday" to f.weekday(day)))),
                quip = Text(if (night) "timeQuipNight" else if (early) "timeQuipEarly" else "timeQuipDay"),
            ))
        } else if ((weekdays.getOrNull(day) ?: 0) > 0) {
            out.add(Slide(
                "when", Theme.Sky, Mood.Cool, Layout.Number, Place.Left,
                lead = Text("whenLead"), value = Value.Words(f.weekday(day)),
                unit = Text("whenUnit", mapOf("n" to f.num(weekdays[day].toDouble()))), quip = Text("whenQuip"),
            ))
        }
        val made = if (c != null) listOf("madeTodos" to c.todosDone, "madeReminders" to c.reminders, "madeComments" to c.comments, "madeRatings" to c.ratings) else emptyList()
        val rows = made.filter { it.second > 0 }.map { (key, n) -> ListRow("", labelKey = key, value = n.toDouble(), display = f.num(n.toDouble())) }
        if (rows.isNotEmpty() || p.favouriteDish != null) {
            out.add(Slide(
                "made", Theme.Mint, if (p.favouriteDish != null) Mood.Love else Mood.Cheer, Layout.List, Place.Corner,
                lead = Text("madeLead"), list = rows,
                body = p.favouriteDish?.let { listOf(Text("madeDish", mapOf("dish" to f.dish(it.name)))) } ?: emptyList(),
                quip = Text("madeQuip"),
            ))
        }
        val rank = p.rank
        if (rank != null && rank.topPercent <= 50) {
            val legend = rank.topPercent <= 5
            out.add(Slide(
                "rank", Theme.Gold, if (legend) Mood.Legend else if (rank.topPercent <= 20) Mood.Party else Mood.Great, Layout.Number, Place.Bottom,
                lead = Text("rankLead"), value = Value.Count(rank.topPercent.toDouble(), prefix = "Top ", suffix = " %", from = 100.0),
                unit = Text("rankUnit"), quip = Text(if (legend) "rankQuipLegend" else "rankQuip"),
            ))
        }
        return out
    }

    /** The story's pages, in order: hello, the chosen parts, the summary. */
    fun build(input: Input, f: Fmt): List<Slide> {
        fun wants(s: String) = s in input.sections
        val u = input.untis
        val slides = arrayListOf(Slide(
            "intro", Theme.Night, Mood.Wave, Layout.Intro, Place.Bottom,
            lead = if (input.firstName.isNotEmpty()) Text("introHello", mapOf("name" to input.firstName)) else Text("introHelloAnon"),
            value = Value.Words(input.title),
            body = listOf(Text("introLead", mapOf("from" to f.day(input.periodFrom), "to" to f.day(input.periodTo)))),
            quip = Text("introQuip"),
        ))
        if (u != null && wants("school")) slides += schoolSlides(u, wants("absences"), f)
        if (u != null && wants("absences")) slides += absenceSlides(u, f)
        if (u != null && wants("grades")) slides += gradeSlides(u, f)
        if (u != null && wants("exams")) slides += examSlides(u, f)
        if (u != null && wants("classreg")) slides += classregSlides(u)
        val p = input.pokyh
        if (p != null && wants("pokyh")) slides += pokyhSlides(p, f, u?.school?.days ?: 0)
        slides += Slide("summary", Theme.Night, Mood.Party, Layout.Summary, Place.Corner, value = Value.Words(input.title), quip = Text("summaryQuip"))
        return slides
    }

    /** Every figure the summary can show, the most telling first. */
    fun summaryItems(input: Input, f: Fmt): List<SummaryItem> {
        val out = ArrayList<SummaryItem>()
        val u = input.untis
        val p = input.pokyh
        fun wants(s: String) = s in input.sections
        fun add(id: String, label: String, value: String) = out.add(SummaryItem(id, label, value))
        val school = u?.school
        if (school != null && wants("school") && school.lessons > 0) {
            add("hours", "cardHours", f.num(hours(maxOf(0, school.minutes - (if (wants("absences")) u.absences?.missedMinutes ?: 0 else 0))).toDouble()))
        }
        u?.absences?.rate?.let { r -> if (wants("absences")) add("rate", "cardRate", "${f.num(r, if (r == 0.0) 0 else 1)} %") }
        u?.grades?.let { g -> if (g.count > 0 && wants("grades")) add("average", "cardAverage", f.num(g.average, 2)) }
        p?.totals?.let { t -> if (t.activeDays > 0 && wants("pokyh")) add("days", "cardDays", f.num(t.activeDays.toDouble())) }
        p?.rank?.let { r -> if (r.topPercent <= 50 && wants("pokyh")) add("rank", "cardRank", "Top ${r.topPercent} %") }
        school?.subjects?.firstOrNull()?.let { if (wants("school")) add("subject", "cardSubject", it.name) }
        u?.grades?.best?.let { if (wants("grades")) add("best", "cardBest", it.name) }
        u?.exams?.takeIf { it > 0 }?.let { if (wants("exams")) add("exams", "cardExams", f.num(it.toDouble())) }
        u?.homework?.takeIf { it > 0 }?.let { if (wants("exams")) add("homework", "cardHomework", f.num(it.toDouble())) }
        u?.absences?.let { if (wants("absences")) add("missed", "cardMissed", f.num((it.missedMinutes / 60.0).roundToInt().toDouble())) }
        if (school != null && wants("school") && school.lessons > 0) add("cancelled", "cardCancelled", f.num(school.cancelled.toDouble()))
        school?.teachers?.firstOrNull()?.let { if (wants("school")) add("teacher", "cardTeacher", it.name) }
        p?.totals?.let { t -> if (t.longestStreak > 1 && wants("pokyh")) add("streak", "cardStreak", f.num(t.longestStreak.toDouble())) }
        p?.created?.let { c -> if (c.todosDone > 0 && wants("pokyh")) add("todos", "cardTodos", f.num(c.todosDone.toDouble())) }
        u?.classreg?.let { if (wants("classreg")) add("classreg", "cardClassreg", f.num(it.toDouble())) }
        return out
    }
}
