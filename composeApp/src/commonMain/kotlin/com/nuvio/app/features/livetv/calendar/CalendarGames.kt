package com.nuvio.app.features.livetv.calendar

import com.nuvio.app.features.livetv.sports.LeagueKind
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFeeds
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsDates

/**
 * Games for the teams you follow (not whole leagues, which would bury your shows), from a month
 * back to four months ahead. Leagues without teams (golf, racing, tennis, fights) are the
 * exception: their events show when you follow the league. They show on the Calendar as the same team-colour cards Sports uses.
 */
internal object CalendarGames {
    private const val DAY = 24 * 3_600_000L

    fun entries(known: List<SportsEvent>, follows: SportsFollows, now: Long): List<CalendarEntry> {
        val eventLeagues = (follows.leagues + follows.pins).filterTo(HashSet()) { SportsCatalog.league(it)?.kind == LeagueKind.EVENT }
        if (follows.teams.isEmpty() && eventLeagues.isEmpty()) return emptyList()
        val today = SportsDates.dayStart(0)
        val from = today - 30 * DAY
        val to = today + 120 * DAY
        return known.flatMap { ev ->
            val mine = ev.sides.firstOrNull { follows.followsTeam(ev.leagueId, it.team.id) }
            if (mine == null && ev.leagueId !in eventLeagues) return@flatMap emptyList()
            val day = SportsDates.dayOf(ev.startMs)
            // A tournament that runs for days (golf, tennis): on every day it's on, so it's still
            // there on its last day, not only on the day it started. (No time: Sports gives none.)
            val lastDay = ev.endMs?.takeIf { !ev.hasTime }?.let { SportsDates.dayOf(it) }
                ?.takeIf { it > day && it <= day + 30 * DAY } ?: day
            if (lastDay < from || day > to) return@flatMap emptyList()
            // Midday of each day, so a clock change doesn't skip or repeat a day.
            val days = generateSequence(day) { SportsDates.dayOf(it + DAY + DAY / 2) }.takeWhile { it <= lastDay }.toList()
            days.filter { it in from..to }.map { d ->
                CalendarEntry(
                    key = if (d == day) "game|${ev.id}" else "game|${ev.id}|$d", kind = CalendarKind.GAME, contentId = "game|${ev.id}", type = "game",
                    title = ev.title, poster = mine?.team?.logo ?: SportsFeeds.leagueLogos[ev.leagueId], background = null,
                    airMs = ev.startMs.takeIf { ev.hasTime }, dayMs = d,
                    network = ev.broadcasts.firstOrNull(), eventId = ev.id,
                )
            }
        }.sortedWith(compareBy<CalendarEntry> { it.dayMs }.thenBy { it.airMs ?: Long.MAX_VALUE })
    }
}
