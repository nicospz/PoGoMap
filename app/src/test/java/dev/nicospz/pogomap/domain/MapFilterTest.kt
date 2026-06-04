package dev.nicospz.pogomap.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFilterTest {
    @Test
    fun `gym with raid appears under raids`() {
        val marker = MapFilter.markerItems(listOf(gym("gym-1", raid = RaidInfo("Mewtwo", 5))), setOf(UiFilter.Raids))

        assertEquals(1, marker.size)
        assertEquals(UiFilter.Raids, marker.single().category)
        assertEquals("Mewtwo", marker.single().title)
    }

    @Test
    fun `empty filters show all objects`() {
        val markers = MapFilter.markerItems(listOf(gym("gym-1", raid = RaidInfo("Mewtwo", 5))), emptySet())

        assertEquals(1, markers.size)
        assertEquals(UiFilter.Raids, markers.single().category)
        assertEquals(MapFilter.dropTypesFor(MapFilter.allUiFilters), MapFilter.dropTypesFor(emptySet()))
    }

    @Test
    fun `zoom filters follow slider order with pokestops last`() {
        assertEquals(setOf(UiFilter.Raids), MapFilter.filtersForZoom(7.5f))
        assertEquals(
            listOf(
                UiFilter.Raids,
                UiFilter.EventRaids,
                UiFilter.Events,
                UiFilter.GMax,
                UiFilter.DMax,
                UiFilter.Gyms,
            ),
            MapFilter.filtersForZoom(14f).toList(),
        )
        assertEquals(
            setOf(
                UiFilter.Raids,
                UiFilter.EventRaids,
                UiFilter.Events,
                UiFilter.GMax,
                UiFilter.DMax,
                UiFilter.Gyms,
                UiFilter.Pokestops,
            ),
            MapFilter.filtersForZoom(14.5f),
        )
        assertTrue(UiFilter.Routes !in MapFilter.filtersForZoom(16f))
    }

    @Test
    fun `zoom filters do not fetch pokestops until close zoom`() {
        assertTrue(BackendDropType.PgoPokestop !in MapFilter.dropTypesFor(MapFilter.filtersForZoom(14f)))
        assertTrue(BackendDropType.PgoPokestop in MapFilter.dropTypesFor(MapFilter.filtersForZoom(14.5f)))
    }

    @Test
    fun `gym without raid appears under gyms only`() {
        val gym = gym("gym-1", raid = null)

        assertTrue(MapFilter.markerItems(listOf(gym), setOf(UiFilter.Raids)).isEmpty())
        assertEquals(UiFilter.Gyms, MapFilter.markerItems(listOf(gym), setOf(UiFilter.Gyms)).single().category)
    }

    @Test
    fun `empty boss name displays as egg`() {
        assertEquals("Egg", MapFilter.raidDisplayName(RaidInfo("", 1)))
    }

    @Test
    fun `egg image marks placeholder raid as egg`() {
        val objects = listOf(
            gym(
                "gym-1",
                raid = RaidInfo(
                    bossName = "Raid Egg",
                    rating = 1,
                    hatchAt = Instant.now().plusSeconds(600),
                    eggImageUrl = "https://example.com/tier1.png",
                ),
            ),
            gym("gym-2", raid = RaidInfo("Pikachu", 1)),
        )

        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(raidTypes = setOf(RaidTypeFilter.Eggs)),
        )

        assertEquals(1, markers.size)
        assertEquals("gym-1", markers.single().objectId)
        assertEquals("Egg", markers.single().title)
    }

    @Test
    fun `hatched raid with egg image and boss name is active boss`() {
        val now = Instant.now()
        val raid = RaidInfo(
            bossName = "Cresselia",
            rating = 15,
            startsAt = now.minusSeconds(7200),
            hatchAt = now.minusSeconds(2700),
            endsAt = now.plusSeconds(1800),
            bossImageUrl = "https://example.com/cresselia.png",
            eggImageUrl = "https://example.com/tier15.png",
        )

        val marker = MapFilter.markerItems(listOf(gym("gym-1", raid = raid)), setOf(UiFilter.Raids)).single()

        assertEquals("Cresselia", marker.title)
        assertEquals(1, MapFilter.markerItems(
            objects = listOf(gym("gym-1", raid = raid)),
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(raidTypes = setOf(RaidTypeFilter.ActiveRaids)),
        ).size)
        assertTrue(MapFilter.markerItems(
            objects = listOf(gym("gym-1", raid = raid)),
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(raidTypes = setOf(RaidTypeFilter.Eggs)),
        ).isEmpty())
    }

    @Test
    fun `powerspot with max battle appears under dmax gmax`() {
        val powerSpot = powerspot("spot-1", "Charmander")

        val marker = MapFilter.markerItems(listOf(powerSpot), setOf(UiFilter.DMax))

        assertEquals(1, marker.size)
        assertEquals(UiFilter.DMax, marker.single().category)
    }

    @Test
    fun `max pokemon filter keeps matching powerspots`() {
        val objects = listOf(
            powerspot("spot-1", "Charmander"),
            powerspot("spot-2", "Bulbasaur"),
        )

        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.DMax),
            advancedFilters = AdvancedFilterState(maxPokemon = setOf("Bulbasaur")),
        )

        assertEquals(1, markers.size)
        assertEquals("spot-2", markers.single().objectId)
        assertEquals(listOf("Bulbasaur", "Charmander"), MapFilter.maxPokemon(objects))
    }

    @Test
    fun `duplicate object ids collapse to one marker`() {
        val duplicate = gym("gym-1", raid = RaidInfo("Mewtwo", 5))

        val markers = MapFilter.markerItems(listOf(duplicate, duplicate.copy()), setOf(UiFilter.Raids))

        assertEquals(1, markers.size)
    }

    @Test
    fun `advanced filters match active raid tier and pokemon`() {
        val objects = listOf(
            gym("gym-1", raid = RaidInfo("Mewtwo", 5)),
            gym("gym-2", raid = RaidInfo("Pikachu", 1)),
            gym("gym-3", raid = RaidInfo("", 5)),
        )

        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(
                raidTypes = setOf(RaidTypeFilter.ActiveRaids),
                raidTiers = setOf(5),
                raidPokemon = setOf("Mewtwo"),
            ),
        )

        assertEquals(1, markers.size)
        assertEquals("Mewtwo", markers.single().title)
    }

    @Test
    fun `advanced egg filter keeps egg raids`() {
        val objects = listOf(
            gym("gym-1", raid = RaidInfo("Mewtwo", 5)),
            gym("gym-2", raid = RaidInfo("", 5)),
        )

        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(raidTypes = setOf(RaidTypeFilter.Eggs)),
        )

        assertEquals(1, markers.size)
        assertEquals("Egg", markers.single().title)
    }

    @Test
    fun `unique raid boss hints label matching tier eggs`() {
        val objects = listOf(
            gym("tier-5-egg", raid = RaidInfo("", 5)),
            gym("shadow-tier-5-egg", raid = RaidInfo("", 15)),
            gym("mega-egg", raid = RaidInfo("", 6)),
            gym("tier-3-egg", raid = RaidInfo("", 3)),
        )

        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            uniqueRaidBossHints = UniqueRaidBossHints(
                tier5Boss = "Tapu Bulu",
                shadowTier5Boss = "Cresselia",
                megaBoss = "Mega Altaria",
            ),
        )

        val titlesById = markers.associate { it.objectId to it.title }
        assertEquals("Tapu Bulu", titlesById["tier-5-egg"])
        assertEquals("Cresselia", titlesById["shadow-tier-5-egg"])
        assertEquals("Mega Altaria", titlesById["mega-egg"])
        assertEquals("Egg", titlesById["tier-3-egg"])
    }

    @Test
    fun `pokemon and egg filters keep eggs with matching pokemon tier`() {
        val objects = listOf(
            gym("active-tapu", raid = RaidInfo("Tapu Bulu", 5)),
            gym("tier-5-egg", raid = RaidInfo("", 5)),
            gym("tier-15-egg", raid = RaidInfo("", 15)),
        )

        val selectedPokemon = setOf("Tapu Bulu")
        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(
                raidTypes = setOf(RaidTypeFilter.Eggs),
                raidPokemon = selectedPokemon,
                raidPokemonTiers = MapFilter.raidPokemonTiers(objects, selectedPokemon),
            ),
        )

        assertEquals(1, markers.size)
        assertEquals("tier-5-egg", markers.single().objectId)
    }

    @Test
    fun `pokemon filter without raid type keeps boss and matching tier eggs`() {
        val objects = listOf(
            gym("active-tapu", raid = RaidInfo("Tapu Bulu", 5)),
            gym("tier-5-egg", raid = RaidInfo("", 5)),
            gym("active-cresselia", raid = RaidInfo("Cresselia", 15)),
            gym("tier-15-egg", raid = RaidInfo("", 15)),
        )

        val selectedPokemon = setOf("Tapu Bulu")
        val markers = MapFilter.markerItems(
            objects = objects,
            filters = setOf(UiFilter.Raids),
            advancedFilters = AdvancedFilterState(
                raidPokemon = selectedPokemon,
                raidPokemonTiers = MapFilter.raidPokemonTiers(objects, selectedPokemon),
            ),
        )

        assertEquals(listOf("active-tapu", "tier-5-egg"), markers.map { it.objectId })
    }

    private fun gym(id: String, raid: RaidInfo?): PogoMapObject = PogoMapObject(
        id = id,
        mapObjectType = "PGO_GYM",
        pgoGym = GymInfo(
            location = LatLngPoint(35.0, 139.0),
            team = "MYSTIC",
            raid = raid,
        ),
    )

    private fun powerspot(id: String, bossName: String): PogoMapObject = PogoMapObject(
        id = id,
        mapObjectType = "PGO_POWERSPOT",
        pgoPowerspot = PowerspotInfo(
            location = LatLngPoint(35.0, 139.0),
            maxBattle = MaxBattleInfo(bossName, 3),
            overrideMaxBattle = null,
        ),
    )
}
