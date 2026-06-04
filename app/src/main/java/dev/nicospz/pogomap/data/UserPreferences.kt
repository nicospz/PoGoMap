package dev.nicospz.pogomap.data

import android.content.Context
import dev.nicospz.pogomap.domain.AdvancedFilterState
import dev.nicospz.pogomap.domain.RaidTypeFilter
import dev.nicospz.pogomap.domain.UniqueRaidBossHints
import dev.nicospz.pogomap.domain.UiFilter

data class SavedCameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
)

enum class MarkerTimeMode {
    Remaining,
    RealTime,
}

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pogo_user_preferences", Context.MODE_PRIVATE)

    fun loadUiFilters(): Set<UiFilter> {
        val names = prefs.getStringSet(KEY_UI_FILTERS, null) ?: return emptySet()
        val filters = names.mapNotNull { name -> UiFilter.entries.firstOrNull { it.name == name } }.toSet()
        return if (filters.size == UiFilter.entries.size) emptySet() else filters.take(1).toSet()
    }

    fun saveUiFilters(filters: Set<UiFilter>) {
        val storedFilters = if (filters.size == UiFilter.entries.size) emptySet() else filters.take(1).toSet()
        prefs.edit().putStringSet(KEY_UI_FILTERS, storedFilters.map { it.name }.toSet()).apply()
    }

    fun loadAdvancedFilters(): AdvancedFilterState {
        return AdvancedFilterState(
            raidTypes = prefs.getStringSet(KEY_RAID_TYPES, emptySet()).orEmpty()
                .mapNotNull { name -> RaidTypeFilter.entries.firstOrNull { it.name == name } }
                .toSet(),
            raidTiers = prefs.getStringSet(KEY_RAID_TIERS, emptySet()).orEmpty()
                .mapNotNull { it.toIntOrNull() }
                .toSet(),
            raidPokemon = prefs.getStringSet(KEY_RAID_POKEMON, emptySet()).orEmpty(),
            maxPokemon = prefs.getStringSet(KEY_MAX_POKEMON, emptySet()).orEmpty(),
        )
    }

    fun saveAdvancedFilters(filters: AdvancedFilterState) {
        prefs.edit()
            .putStringSet(KEY_RAID_TYPES, filters.raidTypes.map { it.name }.toSet())
            .putStringSet(KEY_RAID_TIERS, filters.raidTiers.map { it.toString() }.toSet())
            .putStringSet(KEY_RAID_POKEMON, filters.raidPokemon)
            .putStringSet(KEY_MAX_POKEMON, filters.maxPokemon)
            .apply()
    }

    fun loadCameraPosition(): SavedCameraPosition? {
        if (!prefs.contains(KEY_CAMERA_LAT) || !prefs.contains(KEY_CAMERA_LNG)) return null
        return SavedCameraPosition(
            latitude = Double.fromBits(prefs.getLong(KEY_CAMERA_LAT, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_CAMERA_LNG, 0L)),
            zoom = prefs.getFloat(KEY_CAMERA_ZOOM, 13f),
        )
    }

    fun saveCameraPosition(position: SavedCameraPosition) {
        prefs.edit()
            .putLong(KEY_CAMERA_LAT, position.latitude.toBits())
            .putLong(KEY_CAMERA_LNG, position.longitude.toBits())
            .putFloat(KEY_CAMERA_ZOOM, position.zoom)
            .apply()
    }

    fun loadUniqueRaidBossHints(): UniqueRaidBossHints {
        return UniqueRaidBossHints(
            tier5Boss = prefs.getString(KEY_TIER_5_BOSS, null),
            shadowTier5Boss = prefs.getString(KEY_SHADOW_TIER_5_BOSS, null),
            megaBoss = prefs.getString(KEY_MEGA_BOSS, null),
        )
    }

    fun saveUniqueRaidBossHints(hints: UniqueRaidBossHints) {
        prefs.edit()
            .putNullableString(KEY_TIER_5_BOSS, hints.tier5Boss)
            .putNullableString(KEY_SHADOW_TIER_5_BOSS, hints.shadowTier5Boss)
            .putNullableString(KEY_MEGA_BOSS, hints.megaBoss)
            .apply()
    }

    fun loadDoneRaidIds(): Set<String> {
        return prefs.getStringSet(KEY_DONE_RAID_IDS, emptySet()).orEmpty()
    }

    fun saveDoneRaidIds(ids: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_DONE_RAID_IDS, ids)
            .apply()
    }

    fun loadMarkerTimeMode(): MarkerTimeMode {
        val name = prefs.getString(KEY_MARKER_TIME_MODE, null) ?: return MarkerTimeMode.Remaining
        return MarkerTimeMode.entries.firstOrNull { it.name == name } ?: MarkerTimeMode.Remaining
    }

    fun saveMarkerTimeMode(mode: MarkerTimeMode) {
        prefs.edit()
            .putString(KEY_MARKER_TIME_MODE, mode.name)
            .apply()
    }

    companion object {
        private const val KEY_UI_FILTERS = "ui_filters"
        private const val KEY_RAID_TYPES = "raid_types"
        private const val KEY_RAID_TIERS = "raid_tiers"
        private const val KEY_RAID_POKEMON = "raid_pokemon"
        private const val KEY_MAX_POKEMON = "max_pokemon"
        private const val KEY_CAMERA_LAT = "camera_lat"
        private const val KEY_CAMERA_LNG = "camera_lng"
        private const val KEY_CAMERA_ZOOM = "camera_zoom"
        private const val KEY_TIER_5_BOSS = "tier_5_boss"
        private const val KEY_SHADOW_TIER_5_BOSS = "shadow_tier_5_boss"
        private const val KEY_MEGA_BOSS = "mega_boss"
        private const val KEY_DONE_RAID_IDS = "done_raid_ids"
        private const val KEY_MARKER_TIME_MODE = "marker_time_mode"
    }
}

private fun android.content.SharedPreferences.Editor.putNullableString(
    key: String,
    value: String?,
): android.content.SharedPreferences.Editor {
    val cleaned = value?.trim()?.takeUnless { it.isBlank() }
    return if (cleaned == null) remove(key) else putString(key, cleaned)
}
