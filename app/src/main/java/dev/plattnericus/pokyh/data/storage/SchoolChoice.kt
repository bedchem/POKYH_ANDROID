package dev.plattnericus.pokyh.data.storage

import android.content.Context
import dev.plattnericus.pokyh.data.model.School
import java.util.Calendar
import org.json.JSONObject

/**
 * The school picked on the sign-in screen — the web's `lib/school-choice.ts`: remembered on the
 * device until the end of the school year (31 August), and for at least 30 days, so the next
 * sign-in starts at the password. Only what WebUntis' public school search shows is kept (name,
 * address, server), nothing about the person.
 */
object SchoolChoice {
    private const val PREFS = "pokyh_school"
    private const val KEY = "choice"
    private const val MIN_DAYS = 30L

    /** End of the school year the date lies in (31 August, 23:59:59), at least 30 days ahead. */
    private fun expiry(now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val year = if (c.get(Calendar.MONTH) >= Calendar.SEPTEMBER) c.get(Calendar.YEAR) + 1 else c.get(Calendar.YEAR)
        val end = Calendar.getInstance().apply { set(year, Calendar.AUGUST, 31, 23, 59, 59) }.timeInMillis
        return maxOf(end, now + MIN_DAYS * 24 * 60 * 60 * 1000)
    }

    fun read(context: Context): School? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = JSONObject(prefs.getString(KEY, null) ?: return null)
        if (o.getLong("expires") <= System.currentTimeMillis()) {
            prefs.edit().remove(KEY).apply()
            return null
        }
        School(o.getString("loginName"), o.getString("server"), o.optString("name"), o.optString("address"))
    }.getOrNull()

    fun save(context: Context, school: School) {
        val o = JSONObject()
            .put("loginName", school.loginName)
            .put("server", school.server)
            .put("name", school.name.take(200))
            .put("address", school.address.take(300))
            .put("expires", expiry())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
