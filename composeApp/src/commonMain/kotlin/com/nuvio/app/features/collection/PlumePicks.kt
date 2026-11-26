package com.nuvio.app.features.collection

/**
 * Ry's own category rows (from the Plume Picks launcher), added once as a normal collection so
 * they sync with the Nuvio account. Same id and shape as the TV build's built-in copy.
 * Dates use {today}/{d-N} so "new" rows stay current; the region is the device's country.
 * To hide it, turn its row off in the Home settings; deleting it brings it back on next start.
 */
internal object PlumePicks {
    const val COLLECTION_ID = "ry-plume-picks"

    private const val WATCHABLE = "flatrate|free|ads|rent|buy"
    private const val NOT_TALK_NEWS_REALITY_SOAP = "10767|10763|10764|10766"

    private fun source(title: String, media: String, votes: Int, f: TmdbCollectionFilters, sort: String) =
        CollectionSource(
            provider = "tmdb",
            tmdbSourceType = TmdbCollectionSourceType.DISCOVER.name,
            title = title,
            mediaType = media,
            sortBy = sort,
            filters = f.copy(voteCountGte = votes, withWatchMonetizationTypes = WATCHABLE),
        )

    private fun movies(title: String, votes: Int, f: TmdbCollectionFilters = TmdbCollectionFilters(), sort: String = "popularity.desc") =
        source(title, "MOVIE", votes, f, sort)

    private fun series(title: String, votes: Int, f: TmdbCollectionFilters = TmdbCollectionFilters(), sort: String = "popularity.desc") =
        source(title, "TV", votes, f.copy(withoutGenres = f.withoutGenres ?: NOT_TALK_NEWS_REALITY_SOAP), sort)

    private fun g(ids: String) = TmdbCollectionFilters(withGenres = ids)

    val collection: Collection = Collection(
        id = COLLECTION_ID,
        title = "Plume Picks",
        viewMode = "ROWS",
        showAllTab = false,
        folders = listOf(
            CollectionFolder(
                id = "$COLLECTION_ID-new",
                title = "New & Popular",
                coverEmoji = "🆕",
                tileShape = "LANDSCAPE",
                sources = listOf(
                    movies("New Movies", 5, TmdbCollectionFilters(withReleaseType = "4|5", releaseDateGte = "{d-60}", releaseDateLte = "{today}")),
                    series("New Series", 10, TmdbCollectionFilters(releaseDateGte = "{d-21}", releaseDateLte = "{today}")),
                    movies("Popular movies", 150),
                    series("Popular series", 150),
                ),
            ),
            CollectionFolder(
                id = "$COLLECTION_ID-genres",
                title = "Genres",
                coverEmoji = "🎭",
                tileShape = "LANDSCAPE",
                sources = listOf(
                    movies("Comedy · Movies", 300, g("35")), series("Comedy · Series", 150, g("35")),
                    movies("Action · Movies", 300, g("28")), series("Action · Series", 150, g("10759")),
                    movies("Drama · Movies", 300, g("18")), series("Drama · Series", 150, g("18")),
                    movies("Crime & Thriller · Movies", 300, g("53|80")), series("Crime & Thriller · Series", 150, g("80")),
                    movies("Sci-Fi & Fantasy · Movies", 300, g("878|14")), series("Sci-Fi & Fantasy · Series", 150, g("10765")),
                ),
            ),
            CollectionFolder(
                id = "$COLLECTION_ID-kids",
                title = "Kids",
                coverEmoji = "🧸",
                tileShape = "LANDSCAPE",
                sources = listOf(
                    movies("Popular kids movies", 100, TmdbCollectionFilters(withGenres = "10751", certificationLte = "PG")),
                    series("Popular kids series", 20, g("10762")),
                    series(
                        "Old school cartoons", 0,
                        TmdbCollectionFilters(withGenres = "16,10762", releaseDateGte = "2000-01-01", releaseDateLte = "2019-12-31"),
                        sort = "vote_count.desc",
                    ),
                ),
            ),
        ),
    )

    /**
     * The lists as Home rows (folder id to list): New Movies, New Series and Old school cartoons
     * first, then the rest in the collection's order.
     */
    fun homeSources(): List<Pair<String, CollectionSource>> {
        val all = collection.folders.flatMap { f -> f.sources.map { f.id to it } }
        val first = listOf("New Movies", "New Series", "Old school cartoons")
        return first.mapNotNull { t -> all.firstOrNull { it.second.title == t } } + all.filter { it.second.title !in first }
    }
}
