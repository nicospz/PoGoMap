package dev.nicospz.pogomap.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nicospz.pogomap.BuildConfig
import dev.nicospz.pogomap.data.CampfireHttpException
import dev.nicospz.pogomap.data.MapRepository
import dev.nicospz.pogomap.data.MarkerTimeMode
import dev.nicospz.pogomap.data.SavedCameraPosition
import dev.nicospz.pogomap.data.TokenStore
import dev.nicospz.pogomap.data.UserPreferences
import dev.nicospz.pogomap.domain.AdvancedFilterState
import dev.nicospz.pogomap.domain.BackendDropType
import dev.nicospz.pogomap.domain.MapFilter
import dev.nicospz.pogomap.domain.MarkerItem
import dev.nicospz.pogomap.domain.PogoMapObject
import dev.nicospz.pogomap.domain.RaidInfo
import dev.nicospz.pogomap.domain.S2ViewportCoverer
import dev.nicospz.pogomap.domain.UniqueRaidBossHints
import dev.nicospz.pogomap.domain.UiFilter
import dev.nicospz.pogomap.domain.ViewportBounds
import dev.nicospz.pogomap.domain.isEggRaid
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val filters: Set<UiFilter> = emptySet(),
    val advancedFilters: AdvancedFilterState = AdvancedFilterState(),
    val savedCameraPosition: SavedCameraPosition? = null,
    val visibleObjects: List<PogoMapObject> = emptyList(),
    val markers: List<MarkerItem> = emptyList(),
    val selectedMarker: MarkerItem? = null,
    val token: String = "",
    val showSettings: Boolean = true,
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
    val uniqueRaidBossHints: UniqueRaidBossHints = UniqueRaidBossHints(),
    val s2CellLevel: Int? = null,
    val s2CellCount: Int = 0,
    val doneRaidIds: Set<String> = emptySet(),
    val markerTimeMode: MarkerTimeMode = MarkerTimeMode.Remaining,
)

class MainViewModel(
    private val tokenStore: TokenStore,
    private val repository: MapRepository,
    private val userPreferences: UserPreferences,
    private val coverer: S2ViewportCoverer = S2ViewportCoverer(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        MainUiState(
            filters = userPreferences.loadUiFilters(),
            advancedFilters = userPreferences.loadAdvancedFilters(),
            savedCameraPosition = userPreferences.loadCameraPosition(),
            token = tokenStore.getToken(),
            uniqueRaidBossHints = userPreferences.loadUniqueRaidBossHints(),
            doneRaidIds = userPreferences.loadDoneRaidIds(),
            markerTimeMode = userPreferences.loadMarkerTimeMode(),
        ),
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var cameraJob: Job? = null
    private var lastViewport: ViewportRequest? = null

    init {
        _state.update { it.copy(showSettings = it.token.isBlank()) }
        if (_state.value.token.isNotBlank()) {
            validateMetadataThenLoad()
        }
    }

    fun saveToken(token: String) {
        tokenStore.saveToken(token)
        _state.update {
            it.copy(
                token = token.trim(),
                showSettings = false,
                statusMessage = null,
            )
        }
        validateMetadataThenLoad()
    }

    fun clearToken() {
        tokenStore.clearToken()
        repository.cancelInFlight()
        _state.update {
            it.copy(
                token = "",
                markers = emptyList(),
                selectedMarker = null,
                showSettings = true,
                statusMessage = "Token cleared",
            )
        }
    }

    fun toggleSettings() {
        _state.update { it.copy(showSettings = !it.showSettings) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(showSettings = visible) }
    }

    fun setMarkerTimeMode(mode: MarkerTimeMode) {
        userPreferences.saveMarkerTimeMode(mode)
        _state.update { it.copy(markerTimeMode = mode) }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun toggleFilter(filter: UiFilter) {
        _state.update { current ->
            val nextFilters = if (current.filters == setOf(filter)) emptySet() else setOf(filter)
            userPreferences.saveUiFilters(nextFilters)
            val effectiveFilters = effectiveFiltersFor(nextFilters, lastViewport?.zoom)
            current.copy(
                filters = nextFilters,
                markers = MapFilter.markerItems(
                    current.visibleObjects,
                    effectiveFilters,
                    current.advancedFilters.withDerivedRaidPokemonTiers(current.visibleObjects),
                    current.uniqueRaidBossHints,
                ),
            )
        }
        retryLastViewport()
    }

    fun applyAdvancedFilters(filters: AdvancedFilterState, targetFilter: UiFilter? = null) {
        userPreferences.saveAdvancedFilters(filters)
        _state.update { current ->
            val nextUiFilters = when {
                targetFilter == UiFilter.Raids && filters.raidActiveCount > 0 -> setOf(UiFilter.Raids)
                targetFilter in setOf(UiFilter.DMax, UiFilter.GMax) && filters.maxActiveCount > 0 -> setOf(targetFilter!!)
                targetFilter == null && filters.raidActiveCount > 0 -> setOf(UiFilter.Raids)
                targetFilter == null && filters.maxActiveCount > 0 -> setOf(UiFilter.DMax)
                else -> current.filters
            }
            if (nextUiFilters != current.filters) {
                userPreferences.saveUiFilters(nextUiFilters)
            }
            val effectiveFilters = effectiveFiltersFor(nextUiFilters, lastViewport?.zoom)
            current.copy(
                filters = nextUiFilters,
                advancedFilters = filters,
                markers = MapFilter.markerItems(
                    current.visibleObjects,
                    effectiveFilters,
                    filters.withDerivedRaidPokemonTiers(current.visibleObjects),
                    current.uniqueRaidBossHints,
                ),
            )
        }
        retryLastViewport()
    }

    fun resetFilters() {
        userPreferences.saveUiFilters(emptySet())
        userPreferences.saveAdvancedFilters(AdvancedFilterState())
        _state.update { current ->
            val effectiveFilters = effectiveFiltersFor(emptySet(), lastViewport?.zoom)
            current.copy(
                filters = emptySet(),
                advancedFilters = AdvancedFilterState(),
                markers = MapFilter.markerItems(
                    current.visibleObjects,
                    effectiveFilters,
                    AdvancedFilterState(),
                    current.uniqueRaidBossHints,
                ),
            )
        }
        retryLastViewport()
    }

    fun selectMarker(marker: MarkerItem?) {
        _state.update { it.copy(selectedMarker = marker) }
    }

    fun toggleRaidDone(marker: MarkerItem) {
        if (!marker.isRaidMarker()) return
        _state.update { current ->
            val nextDoneRaidIds = if (marker.objectId in current.doneRaidIds) {
                current.doneRaidIds - marker.objectId
            } else {
                current.doneRaidIds + marker.objectId
            }
            userPreferences.saveDoneRaidIds(nextDoneRaidIds)
            current.copy(doneRaidIds = nextDoneRaidIds)
        }
    }

    fun onCameraIdle(bounds: ViewportBounds, centerLatitude: Double, centerLongitude: Double, zoom: Float) {
        userPreferences.saveCameraPosition(SavedCameraPosition(centerLatitude, centerLongitude, zoom))
        lastViewport = ViewportRequest(bounds, centerLatitude, centerLongitude, zoom)
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            delay(600)
            fetchViewport(bounds, zoom)
        }
    }

    fun onForegrounded() {
        retryLastViewport(forceRefresh = true)
    }

    private fun retryLastViewport(forceRefresh: Boolean = false) {
        val request = lastViewport ?: return
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            fetchViewport(request.bounds, request.zoom, forceRefresh)
        }
    }

    private fun validateMetadataThenLoad() {
        val token = _state.value.token.trim()
        if (token.isBlank()) return
        viewModelScope.launch {
            try {
                repository.fetchMetadata(token)
                _state.update { it.copy(showSettings = false, statusMessage = "Token saved") }
                retryLastViewport()
            } catch (e: CampfireHttpException) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    _state.update { it.copy(showSettings = true, statusMessage = "Token expired or invalid") }
                } else {
                    _state.update { it.copy(statusMessage = e.userMessage("Metadata request failed")) }
                }
            } catch (_: IOException) {
                _state.update { it.copy(statusMessage = "Metadata request failed; check network.") }
            }
        }
    }

    private fun fetchViewport(bounds: ViewportBounds, zoom: Float, forceRefresh: Boolean = false) {
        val token = _state.value.token.trim()
        if (token.isBlank()) {
            _state.update { it.copy(showSettings = true, statusMessage = "Paste a bearer token to load map objects") }
            return
        }
        val covering = coverer.cover(bounds, zoom)
        if (covering.blockedByMaxCells) {
            _state.update {
                it.copy(
                    isLoading = false,
                    s2CellLevel = covering.level,
                    s2CellCount = 0,
                    statusMessage = "Zoom in to load this area",
                )
            }
            return
        }
        val selectedFilters = _state.value.filters
        val effectiveFilters = effectiveFiltersFor(selectedFilters, zoom)
        val dropTypes = MapFilter.dropTypesFor(effectiveFilters)
        if (dropTypes.isEmpty()) {
            _state.update { it.copy(markers = emptyList(), isLoading = false) }
            return
        }

        repository.cancelInFlight()
        val job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val objects = repository.fetchVisibleObjects(
                    token = token,
                    s2Level = covering.level,
                    s2CellIds = covering.cellIds,
                    dropTypes = dropTypes,
                    forceRefresh = forceRefresh,
                )
                val current = _state.value
                val latestEffectiveFilters = effectiveFiltersFor(current.filters, zoom)
                val uniqueRaidBossHints = current.uniqueRaidBossHints.updatedWith(objects)
                if (uniqueRaidBossHints != current.uniqueRaidBossHints) {
                    userPreferences.saveUniqueRaidBossHints(uniqueRaidBossHints)
                }
                val markers = MapFilter.markerItems(
                    objects,
                    latestEffectiveFilters,
                    current.advancedFilters.withDerivedRaidPokemonTiers(objects),
                    uniqueRaidBossHints,
                )
                _state.update {
                    it.copy(
                        visibleObjects = objects,
                        markers = markers,
                        uniqueRaidBossHints = uniqueRaidBossHints,
                        s2CellLevel = covering.level,
                        s2CellCount = covering.cellIds.size,
                        isLoading = false,
                    )
                }
                logLoadedDataDebug(
                    objects = objects,
                    markers = markers,
                    filters = latestEffectiveFilters,
                    dropTypes = dropTypes,
                    s2Level = covering.level,
                    cellIds = covering.cellIds,
                    uniqueRaidBossHints = uniqueRaidBossHints,
                )
            } catch (e: CampfireHttpException) {
                when (e.statusCode) {
                    401, 403 -> _state.update {
                        it.copy(isLoading = false, showSettings = true, statusMessage = "Token expired or invalid")
                    }
                    429 -> _state.update { it.copy(isLoading = false, statusMessage = "Rate limited; wait before retrying.") }
                    else -> _state.update { it.copy(isLoading = false, statusMessage = e.userMessage("Map request failed")) }
                }
            } catch (_: IOException) {
                _state.update { it.copy(isLoading = false, statusMessage = "Network failure; old markers kept.") }
            }
        }
        repository.rememberInFlight(job)
    }
}

private fun logLoadedDataDebug(
    objects: List<PogoMapObject>,
    markers: List<MarkerItem>,
    filters: Set<UiFilter>,
    dropTypes: Set<BackendDropType>,
    s2Level: Int,
    cellIds: List<String>,
    uniqueRaidBossHints: UniqueRaidBossHints,
) {
    if (!BuildConfig.DEBUG) return

    val objectTypeCounts = objects
        .groupingBy { it.mapObjectType ?: "UNKNOWN" }
        .eachCount()
        .toSortedMap()
    val markerCounts = markers
        .groupingBy { it.category }
        .eachCount()
        .toList()
        .sortedBy { it.first.ordinal }
        .joinToString { "${it.first}=${it.second}" }
        .ifBlank { "none" }

    Log.i(
        DATA_DEBUG_TAG,
        "LoadedData s2Level=$s2Level cells=${cellIds.size} dropTypes=${dropTypes.map { it.wireName }.sorted()} " +
            "filters=${filters.ifEmpty { MapFilter.allUiFilters }.map { it.name }.sorted()} " +
            "objects=${objects.size} markers=${markers.size}",
    )
    Log.i(DATA_DEBUG_TAG, "ObjectTypes $objectTypeCounts")
    Log.i(DATA_DEBUG_TAG, "MarkerCategories $markerCounts")
    Log.i(DATA_DEBUG_TAG, "UniqueRaidBossHints $uniqueRaidBossHints")
    objects.forEachIndexed { index, obj ->
        Log.i(DATA_DEBUG_TAG, "Object[$index] ${obj.debugSummary(markers.filter { it.objectId == obj.id })}")
    }
}

private fun UniqueRaidBossHints.updatedWith(objects: Collection<PogoMapObject>): UniqueRaidBossHints {
    var next = this
    objects.forEach { obj ->
        val raid = obj.pgoGym?.raid ?: return@forEach
        val learnedBoss = raid.learnableUniqueBossName() ?: return@forEach
        next = when (raid.rating) {
            5 -> next.copy(tier5Boss = learnedBoss)
            15 -> next.copy(shadowTier5Boss = learnedBoss)
            6 -> next.copy(megaBoss = learnedBoss)
            else -> next
        }
    }
    return next
}

private fun RaidInfo.learnableUniqueBossName(): String? {
    if (isEggRaid()) return null
    if (rating !in setOf(5, 6, 15)) return null
    return bossName?.trim()?.takeUnless {
        it.isBlank() || it.equals("Egg", ignoreCase = true) || it.equals("Raid Egg", ignoreCase = true)
    }
}

private fun PogoMapObject.debugSummary(markers: List<MarkerItem>): String {
    val locationText = location?.let { "${it.latitude},${it.longitude}" } ?: "missing"
    val markerText = markers.map { it.category.name }.ifEmpty { listOf("none") }.joinToString("|")
    val details = buildList {
        pgoGym?.let { gym ->
            add("team=${gym.team ?: "unknown"}")
            gym.raid?.let { raid ->
                add("raidBoss=${raid.bossName ?: "unknown"}")
                add("raidRating=${raid.rating ?: "unknown"}")
                add("raidStart=${raid.startsAt ?: "unknown"}")
                add("raidHatch=${raid.hatchAt ?: "unknown"}")
                add("raidEnd=${raid.endsAt ?: "unknown"}")
                add("bossImage=${raid.bossImageUrl ?: "none"}")
                add("eggImage=${raid.eggImageUrl ?: "none"}")
            }
        }
        pgoPowerspot?.let { spot ->
            val battle = spot.overrideMaxBattle ?: spot.maxBattle
            add("maxBoss=${battle?.bossName ?: "none"}")
            add("maxRating=${battle?.rating ?: "unknown"}")
            add("maxStart=${battle?.startsAt ?: "unknown"}")
            add("maxEnd=${battle?.endsAt ?: "unknown"}")
        }
        pgoRoute?.let { route ->
            add("routeMeters=${route.distanceMeters ?: "unknown"}")
            add("routeEnd=${route.endPoi?.location?.let { "${it.latitude},${it.longitude}" } ?: "missing"}")
        }
        event?.let { event ->
            add("eventTitle=${event.title ?: "untitled"}")
            add("eventStart=${event.startsAt ?: "unknown"}")
            add("eventEnd=${event.endsAt ?: "unknown"}")
        }
    }
    return "id=$id type=${mapObjectType ?: "UNKNOWN"} markers=$markerText location=$locationText ${details.joinToString(" ")}"
}

private fun normalizeUiFilters(filters: Set<UiFilter>): Set<UiFilter> {
    return filters.take(1).toSet()
}

private fun AdvancedFilterState.withDerivedRaidPokemonTiers(objects: Collection<PogoMapObject>): AdvancedFilterState {
    return copy(raidPokemonTiers = MapFilter.raidPokemonTiers(objects, raidPokemon))
}

private fun effectiveFiltersFor(selectedFilters: Set<UiFilter>, zoom: Float?): Set<UiFilter> {
    return selectedFilters.ifEmpty {
        zoom?.let(MapFilter::filtersForZoom) ?: setOf(UiFilter.Raids)
    }
}

private fun MarkerItem.isRaidMarker(): Boolean {
    return (category == UiFilter.Raids || category == UiFilter.EventRaids) && source.pgoGym?.raid != null
}

private data class ViewportRequest(
    val bounds: ViewportBounds,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val zoom: Float,
)

private const val DATA_DEBUG_TAG = "PoGoMapData"

private fun CampfireHttpException.userMessage(prefix: String): String {
    return buildString {
        append(prefix)
        append(": HTTP ")
        append(statusCode)
        if (responseBody.isNotBlank()) {
            append(" - ")
            append(responseBody)
        }
    }
}
