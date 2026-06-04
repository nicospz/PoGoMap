package dev.nicospz.pogomap.domain

import java.time.Instant

object MapFilter {
    val allUiFilters: Set<UiFilter> = UiFilter.entries.toSet()
    val zoomOrderedUiFilters: List<UiFilter> = listOf(
        UiFilter.Raids,
        UiFilter.EventRaids,
        UiFilter.Events,
        UiFilter.GMax,
        UiFilter.DMax,
        UiFilter.Gyms,
        UiFilter.Pokestops,
    )

    fun filtersForZoom(zoom: Float): Set<UiFilter> {
        val visibleCount = when {
            zoom < 8f -> 1
            zoom < 9f -> 2
            zoom < 10f -> 3
            zoom < 11f -> 4
            zoom < 12f -> 5
            zoom < 13f -> 6
            zoom < 14.5f -> 6
            else -> 7
        }
        return zoomOrderedUiFilters.take(visibleCount).toSet()
    }

    fun dropTypesFor(filters: Set<UiFilter>): Set<BackendDropType> {
        val activeFilters = filters.ifEmpty { allUiFilters }
        val drops = mutableSetOf<BackendDropType>()
        if (activeFilters.any { it == UiFilter.Raids || it == UiFilter.EventRaids || it == UiFilter.Gyms }) {
            drops += BackendDropType.PgoGym
        }
        if (activeFilters.any { it == UiFilter.Events }) drops += BackendDropType.CampfireEvent
        if (activeFilters.any { it == UiFilter.DMax || it == UiFilter.GMax }) drops += BackendDropType.PgoPowerspot
        if (activeFilters.any { it == UiFilter.Routes }) drops += BackendDropType.PgoRoute
        if (activeFilters.any { it == UiFilter.Pokestops }) drops += BackendDropType.PgoPokestop
        return drops
    }

    fun markerItems(
        objects: Collection<PogoMapObject>,
        filters: Set<UiFilter>,
        advancedFilters: AdvancedFilterState = AdvancedFilterState(),
        uniqueRaidBossHints: UniqueRaidBossHints = UniqueRaidBossHints(),
    ): List<MarkerItem> {
        val activeFilters = filters.ifEmpty { allUiFilters }
        return objects
            .distinctBy { it.id }
            .flatMap { obj -> markersForObject(obj, activeFilters, advancedFilters, uniqueRaidBossHints) }
            .sortedWith(compareBy<MarkerItem> { it.category.ordinal }.thenBy { it.objectId })
    }

    fun raidTiers(objects: Collection<PogoMapObject>): List<Int> {
        return objects.mapNotNull { it.pgoGym?.raid?.rating }.distinct().sorted()
    }

    fun raidPokemon(objects: Collection<PogoMapObject>): List<String> {
        return objects.mapNotNull { obj ->
            obj.pgoGym?.raid?.bossName?.trim()?.takeUnless { it.isBlank() }
        }.distinct().sorted()
    }

    fun raidPokemonTiers(objects: Collection<PogoMapObject>, pokemon: Set<String>): Set<Int> {
        if (pokemon.isEmpty()) return emptySet()
        return objects.mapNotNull { obj ->
            val raid = obj.pgoGym?.raid ?: return@mapNotNull null
            val bossName = raid.bossName?.trim()?.takeUnless { it.isBlank() } ?: return@mapNotNull null
            raid.rating?.takeIf { bossName in pokemon }
        }.toSet()
    }

    fun maxPokemon(objects: Collection<PogoMapObject>): List<String> {
        return objects.mapNotNull { obj ->
            val battle = obj.pgoPowerspot?.overrideMaxBattle ?: obj.pgoPowerspot?.maxBattle
            battle?.bossName?.trim()?.takeUnless { it.isBlank() }
        }.distinct().sorted()
    }

    fun raidDisplayName(raid: RaidInfo?): String {
        if (raid.isEggRaid()) return "Egg"
        val bossName = raid?.bossName?.trim().orEmpty()
        return bossName.ifEmpty { "Raid" }
    }

    fun raidDisplayName(raid: RaidInfo?, uniqueRaidBossHints: UniqueRaidBossHints): String {
        if (raid.isEggRaid()) {
            return uniqueRaidBossHints.bossForRating(raid?.rating) ?: "Egg"
        }
        return raidDisplayName(raid)
    }

    private fun markersForObject(
        obj: PogoMapObject,
        filters: Set<UiFilter>,
        advancedFilters: AdvancedFilterState,
        uniqueRaidBossHints: UniqueRaidBossHints,
    ): List<MarkerItem> {
        val coordinate = obj.location ?: return emptyList()
        val markers = mutableListOf<MarkerItem>()
        val raid = obj.pgoGym?.raid
        val hasRaid = obj.mapObjectType == BackendDropType.PgoGym.wireName && raid != null

        if (UiFilter.Raids in filters && hasRaid && raidMatches(raid, advancedFilters)) {
            markers += MarkerItem(
                objectId = obj.id,
                coordinate = coordinate,
                category = UiFilter.Raids,
                title = raidDisplayName(raid, uniqueRaidBossHints),
                snippet = "Raid ${raid?.rating?.let { "rating $it" } ?: ""}".trim(),
                source = obj,
            )
        }
        if (UiFilter.EventRaids in filters && hasRaid && (raid?.rating ?: 0) >= 10 && raidMatches(raid, advancedFilters)) {
            markers += MarkerItem(
                objectId = obj.id,
                coordinate = coordinate,
                category = UiFilter.EventRaids,
                title = "Event ${raidDisplayName(raid, uniqueRaidBossHints)}",
                snippet = "Rating ${raid?.rating ?: "unknown"}",
                source = obj,
            )
        }
        if (UiFilter.Gyms in filters && obj.mapObjectType == BackendDropType.PgoGym.wireName && raid == null) {
            markers += MarkerItem(
                objectId = obj.id,
                coordinate = coordinate,
                category = UiFilter.Gyms,
                title = "Gym",
                snippet = obj.pgoGym?.team ?: "No team",
                source = obj,
            )
        }
        if ((UiFilter.DMax in filters || UiFilter.GMax in filters) &&
            obj.mapObjectType == BackendDropType.PgoPowerspot.wireName &&
            (obj.pgoPowerspot?.maxBattle != null || obj.pgoPowerspot?.overrideMaxBattle != null)
        ) {
            val battle = obj.pgoPowerspot?.overrideMaxBattle ?: obj.pgoPowerspot?.maxBattle
            if (maxBattleMatches(battle, advancedFilters)) {
                markers += MarkerItem(
                    objectId = obj.id,
                    coordinate = coordinate,
                    category = UiFilter.DMax,
                    title = battle?.bossName?.takeUnless { it.isBlank() } ?: "Max Battle",
                    snippet = battle?.rating?.let { "Rating $it" } ?: "Power Spot",
                    source = obj,
                )
            }
        }
        if (UiFilter.Routes in filters && obj.mapObjectType == BackendDropType.PgoRoute.wireName) {
            markers += MarkerItem(obj.id, coordinate, UiFilter.Routes, "Route", routeSnippet(obj), obj)
        }
        if (UiFilter.Pokestops in filters && obj.mapObjectType == BackendDropType.PgoPokestop.wireName) {
            markers += MarkerItem(obj.id, coordinate, UiFilter.Pokestops, "Pokestop", obj.id, obj)
        }
        if (UiFilter.Events in filters && (obj.event != null || obj.mapObjectType == BackendDropType.CampfireEvent.wireName)) {
            markers += MarkerItem(
                objectId = obj.id,
                coordinate = coordinate,
                category = UiFilter.Events,
                title = obj.event?.title?.takeUnless { it.isBlank() } ?: "Event",
                snippet = obj.id,
                source = obj,
            )
        }
        return markers
    }

    private fun routeSnippet(obj: PogoMapObject): String {
        return obj.pgoRoute?.distanceMeters?.let { "${it.toInt()} m" } ?: obj.id
    }

    private fun raidMatches(raid: RaidInfo?, advancedFilters: AdvancedFilterState): Boolean {
        if (raid == null) return false
        val bossName = raid.bossName?.trim().orEmpty()
        val isEgg = raid.isEggRaid()
        if (advancedFilters.raidTypes.isNotEmpty()) {
            val typeMatches = (RaidTypeFilter.ActiveRaids in advancedFilters.raidTypes && !isEgg) ||
                (RaidTypeFilter.Eggs in advancedFilters.raidTypes && isEgg)
            if (!typeMatches) return false
        }
        if (advancedFilters.raidTiers.isNotEmpty() && raid.rating !in advancedFilters.raidTiers) {
            return false
        }
        if (advancedFilters.raidPokemon.isNotEmpty() && !raidPokemonMatches(raid, bossName, isEgg, advancedFilters)) {
            return false
        }
        return true
    }

    private fun raidPokemonMatches(
        raid: RaidInfo,
        bossName: String,
        isEgg: Boolean,
        advancedFilters: AdvancedFilterState,
    ): Boolean {
        return if (isEgg) {
            raid.rating != null && raid.rating in advancedFilters.raidPokemonTiers
        } else {
            bossName in advancedFilters.raidPokemon
        }
    }

    private fun maxBattleMatches(
        battle: MaxBattleInfo?,
        advancedFilters: AdvancedFilterState,
    ): Boolean {
        if (battle == null) return false
        if (advancedFilters.maxPokemon.isEmpty()) return true
        val bossName = battle.bossName?.trim().orEmpty()
        return bossName in advancedFilters.maxPokemon
    }
}

fun RaidInfo?.isEggRaid(): Boolean {
    if (this == null) return false
    val bossName = bossName?.trim().orEmpty()
    val hasBossName = bossName.isNotBlank() &&
        !bossName.equals("Egg", ignoreCase = true) &&
        !bossName.equals("Raid Egg", ignoreCase = true)
    val hasBossImage = !bossImageUrl.isNullOrBlank()
    val now = Instant.now()

    if (hatchAt != null) {
        if (hatchAt.isAfter(now)) return true
        if (hasBossName || hasBossImage || endsAt?.isAfter(now) == true) return false
    }

    return !hasBossName && (bossName.isBlank() || !eggImageUrl.isNullOrBlank())
}
