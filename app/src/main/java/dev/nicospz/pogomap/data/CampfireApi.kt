package dev.nicospz.pogomap.data

import android.util.Log
import dev.nicospz.pogomap.domain.BackendDropType
import dev.nicospz.pogomap.domain.EventInfo
import dev.nicospz.pogomap.domain.GymInfo
import dev.nicospz.pogomap.domain.LatLngPoint
import dev.nicospz.pogomap.domain.MaxBattleInfo
import dev.nicospz.pogomap.domain.POGO_REALITY_CHANNEL_ID
import dev.nicospz.pogomap.domain.PogoMapObject
import dev.nicospz.pogomap.domain.PokestopInfo
import dev.nicospz.pogomap.domain.PoiInfo
import dev.nicospz.pogomap.domain.PowerspotInfo
import dev.nicospz.pogomap.domain.RaidInfo
import dev.nicospz.pogomap.domain.RouteInfo
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CampfireApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: String = "https://niantic-social-api.nianticlabs.com/graphql",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun fetchMetadata(token: String): MetadataResult = withContext(Dispatchers.IO) {
        val payload = GraphQlRequest(
            operationName = "PoGoMapMetadata",
            query = METADATA_QUERY,
            variables = buildJsonObject("id" to JsonPrimitive(POGO_REALITY_CHANNEL_ID)),
        )
        val body = execute(token, payload)
        MetadataResult(
            googleMapsPlatformMapId = body.findString("googleMapsPlatformMapId"),
            mapFilters = body.findStringArray("mapFilters"),
            sources = body.findStringArray("sources"),
        )
    }

    suspend fun fetchMapObjects(
        token: String,
        s2CellLevel: Int,
        s2CellIds: List<String>,
        dropTypes: Set<BackendDropType>,
    ): FetchResult = withContext(Dispatchers.IO) {
        val sourcesByS2Cells = s2CellIds.map { cellId ->
            SourcesByS2CellInput(
                s2CellId = cellId,
                sources = listOf(
                    SourceInput(
                        name = "PGO",
                        dropTypes = dropTypes.map { it.wireName }.sorted(),
                    ),
                ),
            )
        }
        val input = buildJsonObject(
            "realityChannelId" to JsonPrimitive(POGO_REALITY_CHANNEL_ID),
            "s2CellLevel" to JsonPrimitive(s2CellLevel),
            "sourcesByS2Cells" to json.encodeToJsonElement(sourcesByS2Cells),
        )
        val variables = buildJsonObject(
            "realityChannelMapObjectsByS2CellsInput" to input,
        )
        val payload = GraphQlRequest(
            operationName = "PgoGameMapObjectsByS2CellsProvider_mapObjectsByS2Cells_Query",
            query = MAP_OBJECTS_QUERY,
            variables = variables,
        )
        val body = execute(token, payload)
        FetchResult(objects = GraphQlMapObjectParser.parseObjects(body))
    }

    private fun execute(token: String, payload: GraphQlRequest): JsonObject {
        val requestJson = json.encodeToString(payload)
        logDebug(
            TAG,
            "GraphQL request operation=${payload.operationName} variables=${payload.variables.toString().safeLogExcerpt()} tokenLength=${token.removePrefix("Bearer ").trim().length}",
        )
        val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Authorization", "Bearer ${token.removePrefix("Bearer ").trim()}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://pokemongo.com")
            .header("Referer", "https://pokemongo.com/")
            .header(
                "x-client-info",
                """{"Game":"CAMPFIRE","Language":"en","Platform":"web_standalone","CampfireVersion":"2026.21.1"}""",
            )
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            logDebug(
                TAG,
                "GraphQL response operation=${payload.operationName} status=${response.code} body=${responseBody.safeErrorExcerpt()}",
            )
            if (!response.isSuccessful) {
                logWarn(
                    TAG,
                    "GraphQL HTTP failure operation=${payload.operationName} status=${response.code} message=${response.message} body=${responseBody.safeErrorExcerpt()}",
                )
                throw CampfireHttpException(
                    statusCode = response.code,
                    statusMessage = response.message,
                    responseBody = responseBody.safeErrorExcerpt(),
                )
            }
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val errors = parsed["errors"]
            if (errors != null && errors != JsonNull) {
                val excerpt = errors.toString().safeErrorExcerpt()
                logWarn(TAG, "GraphQL logical errors operation=${payload.operationName} errors=$excerpt")
                throw IOException("GraphQL errors: $excerpt")
            }
            logDebug(TAG, "GraphQL success operation=${payload.operationName}")
            return parsed
        }
    }

    companion object {
        const val TAG = "PoGoMapApi"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private const val METADATA_QUERY = """
            query PoGoMapMetadata(${'$'}id: ID!) {
              realityChannelById(id: ${'$'}id) {
                mapFilters
                metadata { googleMapsPlatformMapId }
                sources { name dropTypes }
              }
            }
        """

        private const val MAP_OBJECTS_QUERY = """
            query PgoGameMapObjectsByS2CellsProvider_mapObjectsByS2Cells_Query(
              ${'$'}realityChannelMapObjectsByS2CellsInput: RealityChannelMapObjectsByS2CellsInput!
            ) {
              realityChannelMapObjectsByS2Cells(input: ${'$'}realityChannelMapObjectsByS2CellsInput) {
                mapObjectsByS2CellsAndTypes {
                  s2CellId
                  mapObjectsByType {
                    type
                    mapObjects {
                      id
                      mapObjectType
                      score
                      event {
                        mapObjectLocation { latitude longitude }
                        id
                        location
                        eventTime
                        eventEndTime
                        rsvpStatus
                        campfireLiveEvent { eventType id }
                      }
                      pgoGym {
                        location { latitude longitude }
                        isMegaEnhancedEligible
                        raid {
                          bossName
                          rating
                          bossImageUrl
                          eggImageUrl
                          startTime
                          hatchTime
                          endTime
                        }
                        team
                      }
                      pgoPowerspot {
                        location { latitude longitude }
                        maxBattle {
                          bossName
                          rating
                          bossImageUrl
                        }
                        overrideMaxBattle {
                          bossName
                          rating
                          openTime
                          bossImageUrl
                        }
                        overrideBattleStartMinutes
                        overrideBattleEndMinutes
                      }
                      pgoRoute {
                        startPoi {
                          fortId
                          location { latitude longitude }
                        }
                        endPoi {
                          location { latitude longitude }
                        }
                        id
                        distanceMeters
                        reversible
                        tagList
                      }
                      pgoPokestop {
                        location { latitude longitude }
                      }
                    }
                  }
                }
              }
            }
        """
    }
}

class CampfireHttpException(
    val statusCode: Int,
    val statusMessage: String,
    val responseBody: String = "",
) : IOException(
    buildString {
        append("HTTP ")
        append(statusCode)
        if (statusMessage.isNotBlank()) {
            append(' ')
            append(statusMessage)
        }
        if (responseBody.isNotBlank()) {
            append(": ")
            append(responseBody)
        }
    },
)

data class MetadataResult(
    val googleMapsPlatformMapId: String?,
    val mapFilters: List<String>,
    val sources: List<String>,
)

data class FetchResult(
    val objects: List<PogoMapObject>,
)

@Serializable
private data class GraphQlRequest(
    val operationName: String,
    val query: String,
    val variables: JsonElement,
)

@Serializable
private data class SourcesByS2CellInput(
    val s2CellId: String,
    val sources: List<SourceInput>,
)

@Serializable
private data class SourceInput(
    val name: String,
    val dropTypes: List<String>,
)

private fun buildJsonObject(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(pairs.toMap())

private object GraphQlMapObjectParser {
    fun parseObjects(root: JsonObject): List<PogoMapObject> {
        val candidates = mutableListOf<JsonObject>()
        collectMapObjectCandidates(root, candidates)
        return candidates.mapNotNull { parseObject(it) }.distinctBy { it.id }
    }

    private fun collectMapObjectCandidates(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                val hasMapShape = element["id"] is JsonPrimitive &&
                    (element["mapObjectType"] is JsonPrimitive ||
                        element["pgoGym"] is JsonObject ||
                        element["pgoPowerspot"] is JsonObject ||
                        element["pgoPokestop"] is JsonObject ||
                        element["pgoRoute"] is JsonObject ||
                        element["event"] is JsonObject)
                if (hasMapShape) out += element
                element.values.forEach { collectMapObjectCandidates(it, out) }
            }
            is JsonArray -> element.forEach { collectMapObjectCandidates(it, out) }
            else -> Unit
        }
    }

    private fun parseObject(obj: JsonObject): PogoMapObject? {
        val id = obj.string("id") ?: return null
        return PogoMapObject(
            id = id,
            mapObjectType = obj.string("mapObjectType"),
            pgoGym = obj.obj("pgoGym")?.let(::parseGym),
            pgoPowerspot = obj.obj("pgoPowerspot")?.let(::parsePowerspot),
            pgoPokestop = obj.obj("pgoPokestop")?.let { PokestopInfo(it.obj("location")?.location()) },
            pgoRoute = obj.obj("pgoRoute")?.let(::parseRoute),
            event = obj.obj("event")?.let(::parseEvent),
        )
    }

    private fun parseGym(obj: JsonObject): GymInfo = GymInfo(
        location = obj.obj("location")?.location(),
        team = obj.string("team") ?: obj.string("teamName"),
        raid = obj.obj("raid")?.let(::parseRaid),
    )

    private fun parseRaid(obj: JsonObject): RaidInfo = RaidInfo(
        bossName = obj.string("bossName") ?: obj.string("pokemonName"),
        rating = obj.int("rating") ?: obj.int("tier"),
        startsAt = obj.instant("startTime") ?: obj.instant("startsAt"),
        hatchAt = obj.instant("hatchTime") ?: obj.instant("hatchesAt"),
        endsAt = obj.instant("endTime") ?: obj.instant("endsAt"),
        bossImageUrl = obj.string("bossImageUrl"),
        eggImageUrl = obj.string("eggImageUrl"),
    )

    private fun parsePowerspot(obj: JsonObject): PowerspotInfo = PowerspotInfo(
        location = obj.obj("location")?.location(),
        maxBattle = obj.obj("maxBattle")?.let(::parseMaxBattle),
        overrideMaxBattle = obj.obj("overrideMaxBattle")?.let(::parseMaxBattle),
    )

    private fun parseMaxBattle(obj: JsonObject): MaxBattleInfo = MaxBattleInfo(
        bossName = obj.string("bossName") ?: obj.string("pokemonName"),
        rating = obj.int("rating") ?: obj.int("tier"),
        startsAt = obj.instant("startTime") ?: obj.instant("startsAt"),
        endsAt = obj.instant("endTime") ?: obj.instant("endsAt"),
    )

    private fun parseRoute(obj: JsonObject): RouteInfo = RouteInfo(
        startPoi = obj.obj("startPoi")?.let { PoiInfo(it.obj("location")?.location()) },
        endPoi = obj.obj("endPoi")?.let { PoiInfo(it.obj("location")?.location()) },
        distanceMeters = obj.double("distanceMeters") ?: obj.double("distance"),
    )

    private fun parseEvent(obj: JsonObject): EventInfo = EventInfo(
        title = obj.string("title") ?: obj.string("name"),
        mapObjectLocation = obj.obj("mapObjectLocation")?.location() ?: obj.obj("location")?.location(),
        startsAt = obj.instant("startTime") ?: obj.instant("startsAt"),
        endsAt = obj.instant("endTime") ?: obj.instant("endsAt"),
    )
}

private fun JsonObject.location(): LatLngPoint? {
    val lat = double("latitude") ?: double("lat")
    val lng = double("longitude") ?: double("lng") ?: double("lon")
    return if (lat != null && lng != null) LatLngPoint(lat, lng) else null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.double(name: String): Double? = this[name]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
private fun JsonObject.instant(name: String): Instant? = string(name)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun JsonObject.findString(name: String): String? {
    string(name)?.let { return it }
    values.forEach { child ->
        when (child) {
            is JsonObject -> child.findString(name)?.let { return it }
            is JsonArray -> child.forEach { if (it is JsonObject) it.findString(name)?.let { value -> return value } }
            else -> Unit
        }
    }
    return null
}

private fun JsonObject.findStringArray(name: String): List<String> {
    val direct = this[name]
    if (direct is JsonArray) {
        return direct.flatMap { item ->
            when (item) {
                is JsonPrimitive -> listOfNotNull(item.contentOrNull)
                is JsonObject -> item.values.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
                else -> emptyList()
            }
        }
    }
    return values.flatMap {
        when (it) {
            is JsonObject -> it.findStringArray(name)
            is JsonArray -> it.filterIsInstance<JsonObject>().flatMap { child -> child.findStringArray(name) }
            else -> emptyList()
        }
    }
}

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun String.safeErrorExcerpt(): String {
    return replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*"), "$1<redacted>")
        .replace(Regex("[A-Za-z0-9._~+/-]{80,}={0,2}"), "<redacted>")
        .take(420)
}

private fun String.safeLogExcerpt(): String = safeErrorExcerpt().replace(Regex("\\s+"), " ")

private fun logDebug(tag: String, message: String) {
    runCatching { Log.d(tag, message) }
}

private fun logWarn(tag: String, message: String) {
    runCatching { Log.w(tag, message) }
}
