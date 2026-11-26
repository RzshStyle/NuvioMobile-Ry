package com.nuvio.app.features.collection

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ry: the Plume Picks lists as rows at the top of Home (New Movies, New Series and Old school
 * cartoons first). "View all" opens the list like a collection folder's row does.
 */
object PlumeHomeRows {
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sections = MutableStateFlow<List<HomeCatalogSection>>(emptyList())
    val sections: StateFlow<List<HomeCatalogSection>> = _sections.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val rows = PlumePicks.homeSources().map { (folderId, source) ->
                    async { runCatching { section(folderId, source) }.getOrNull() }
                }.awaitAll().filterNotNull()
                if (rows.isNotEmpty()) _sections.value = rows
                delay(if (rows.isEmpty()) 60_000L else REFRESH_MS)
            }
        }
    }

    private suspend fun section(folderId: String, source: CollectionSource): HomeCatalogSection? {
        val page = TmdbCollectionSourceResolver.resolve(source, page = 1)
        if (page.items.isEmpty()) return null
        val type = if (TmdbCollectionMediaType.fromString(source.mediaType) == TmdbCollectionMediaType.TV) "series" else "movie"
        val sourceKey = source.catalogRouteKey()
        return HomeCatalogSection(
            key = "plume_$sourceKey",
            title = source.title.orEmpty(),
            subtitle = "",
            addonName = "Plume Picks",
            target = CatalogTarget.CollectionSource(
                collectionId = PlumePicks.COLLECTION_ID,
                folderId = folderId,
                sourceKey = sourceKey,
                contentType = type,
                supportsPagination = true,
            ),
            items = page.items,
            hasMore = true,
        )
    }
}
