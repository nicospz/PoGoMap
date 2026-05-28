package dev.nicospz.pogomap.data

import dev.nicospz.pogomap.domain.BackendDropType
import dev.nicospz.pogomap.domain.POGO_REALITY_CHANNEL_ID
import dev.nicospz.pogomap.domain.PogoMapObject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job

class MapRepository(
    private val api: CampfireApi,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val cache = mutableMapOf<CacheKey, CacheEntry>()
    private var inFlight: Job? = null

    fun cancelInFlight() {
        inFlight?.cancel()
    }

    fun rememberInFlight(job: Job) {
        inFlight = job
    }

    suspend fun fetchMetadata(token: String): MetadataResult = api.fetchMetadata(token)

    suspend fun fetchVisibleObjects(
        token: String,
        s2Level: Int,
        s2CellIds: List<String>,
        dropTypes: Set<BackendDropType>,
        forceRefresh: Boolean = false,
    ): List<PogoMapObject> {
        val now = Instant.now(clock)
        val sortedDrops = dropTypes.map { it.wireName }.sorted()
        val results = mutableListOf<PogoMapObject>()
        val staleCellIds = mutableListOf<String>()

        s2CellIds.forEach { cellId ->
            val key = CacheKey(POGO_REALITY_CHANNEL_ID, s2Level, cellId, sortedDrops)
            val entry = if (forceRefresh) null else cache[key] ?: cache.entries.firstOrNull { (cachedKey, cachedEntry) ->
                cachedKey.channelId == POGO_REALITY_CHANNEL_ID &&
                    cachedKey.s2Level == s2Level &&
                    cachedKey.s2CellId == cellId &&
                    cachedKey.dropTypes.containsAll(sortedDrops) &&
                    Duration.between(cachedEntry.fetchedAt, now) < CACHE_TTL
            }?.value
            if (entry != null && Duration.between(entry.fetchedAt, now) < CACHE_TTL) {
                results += entry.objects
            } else {
                staleCellIds += cellId
            }
        }

        if (staleCellIds.isNotEmpty()) {
            val fetched = api.fetchMapObjects(token, s2Level, staleCellIds, dropTypes).objects
            val byCell = staleCellIds.associateWith { fetched }
            byCell.forEach { (cellId, objects) ->
                val key = CacheKey(POGO_REALITY_CHANNEL_ID, s2Level, cellId, sortedDrops)
                cache[key] = CacheEntry(now, objects)
                results += objects
            }
        }

        return results.distinctBy { it.id }
    }

    private data class CacheKey(
        val channelId: String,
        val s2Level: Int,
        val s2CellId: String,
        val dropTypes: List<String>,
    )

    private data class CacheEntry(
        val fetchedAt: Instant,
        val objects: List<PogoMapObject>,
    )

    companion object {
        private val CACHE_TTL: Duration = Duration.ofSeconds(60)
    }
}
