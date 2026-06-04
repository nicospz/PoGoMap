package dev.nicospz.pogomap.domain

import java.time.Instant

const val POGO_REALITY_CHANNEL_ID = "da83476a-c4da-4312-a610-a4f2fc2c37f0"

enum class BackendDropType(val wireName: String) {
    PgoGym("PGO_GYM"),
    CampfireEvent("CA_EVENT"),
    PgoPowerspot("PGO_POWERSPOT"),
    PgoRoute("PGO_ROUTE"),
    PgoPokestop("PGO_POKESTOP"),
}

enum class UiFilter {
    Raids,
    EventRaids,
    Events,
    GMax,
    DMax,
    Gyms,
    Routes,
    Pokestops,
}

enum class RaidTypeFilter {
    ActiveRaids,
    Eggs,
}

data class AdvancedFilterState(
    val raidTypes: Set<RaidTypeFilter> = emptySet(),
    val raidTiers: Set<Int> = emptySet(),
    val raidPokemon: Set<String> = emptySet(),
    val raidPokemonTiers: Set<Int> = emptySet(),
    val maxPokemon: Set<String> = emptySet(),
) {
    val raidActiveCount: Int
        get() = raidTypes.size + raidTiers.size + raidPokemon.size

    val maxActiveCount: Int
        get() = maxPokemon.size

    val activeCount: Int
        get() = raidActiveCount + maxActiveCount
}

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double,
)

data class RaidInfo(
    val bossName: String?,
    val rating: Int?,
    val startsAt: Instant? = null,
    val hatchAt: Instant? = null,
    val endsAt: Instant? = null,
    val bossImageUrl: String? = null,
    val eggImageUrl: String? = null,
)

data class UniqueRaidBossHints(
    val tier5Boss: String? = null,
    val shadowTier5Boss: String? = null,
    val megaBoss: String? = null,
) {
    fun bossForRating(rating: Int?): String? = when (rating) {
        5 -> tier5Boss
        15 -> shadowTier5Boss
        6 -> megaBoss
        else -> null
    }?.takeUnless { it.isBlank() }
}

data class GymInfo(
    val location: LatLngPoint?,
    val team: String?,
    val raid: RaidInfo?,
)

data class MaxBattleInfo(
    val bossName: String?,
    val rating: Int?,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)

data class PowerspotInfo(
    val location: LatLngPoint?,
    val maxBattle: MaxBattleInfo?,
    val overrideMaxBattle: MaxBattleInfo?,
)

data class PokestopInfo(
    val location: LatLngPoint?,
)

data class PoiInfo(
    val location: LatLngPoint?,
)

data class RouteInfo(
    val startPoi: PoiInfo?,
    val endPoi: PoiInfo?,
    val distanceMeters: Double?,
)

data class EventInfo(
    val title: String?,
    val mapObjectLocation: LatLngPoint?,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)

data class PogoMapObject(
    val id: String,
    val mapObjectType: String?,
    val pgoGym: GymInfo? = null,
    val pgoPowerspot: PowerspotInfo? = null,
    val pgoPokestop: PokestopInfo? = null,
    val pgoRoute: RouteInfo? = null,
    val event: EventInfo? = null,
) {
    val location: LatLngPoint?
        get() = pgoGym?.location
            ?: pgoPowerspot?.location
            ?: pgoPokestop?.location
            ?: pgoRoute?.startPoi?.location
            ?: event?.mapObjectLocation
}

data class MarkerItem(
    val objectId: String,
    val coordinate: LatLngPoint,
    val category: UiFilter,
    val title: String,
    val snippet: String,
    val source: PogoMapObject,
)

data class ViewportBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)
