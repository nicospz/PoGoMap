package dev.nicospz.pogomap.data

import dev.nicospz.pogomap.domain.BackendDropType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CampfireApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `valid token returns markers`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "data": {
                        "realityChannelById": {
                          "mapObjects": [
                            {
                              "id": "gym-1",
                              "mapObjectType": "PGO_GYM",
                              "pgoGym": {
                                "team": "MYSTIC",
                                "location": { "latitude": 35.681236, "longitude": 139.767125 },
                                "raid": { "bossName": "", "rating": "1", "eggImageUrl": "https://example.com/tier1.png" }
                              }
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val api = CampfireApi(endpoint = server.url("/graphql").toString())

        val result = api.fetchMapObjects("abc123", 13, listOf("123"), setOf(BackendDropType.PgoGym))

        assertEquals(1, result.objects.size)
        assertEquals("gym-1", result.objects.single().id)
        assertEquals(1, result.objects.single().pgoGym?.raid?.rating)
        assertEquals("https://example.com/tier1.png", result.objects.single().pgoGym?.raid?.eggImageUrl)
        val request = server.takeRequest()
        assertEquals("Bearer abc123", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("realityChannelMapObjectsByS2CellsInput"))
    }

    @Test
    fun `401 throws http exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad token"}"""))
        val api = CampfireApi(endpoint = server.url("/graphql").toString())

        val thrown = runCatching {
            api.fetchMapObjects("bad", 13, listOf("123"), setOf(BackendDropType.PgoGym))
        }.exceptionOrNull()

        assertTrue(thrown is CampfireHttpException)
        assertEquals(401, (thrown as CampfireHttpException).statusCode)
        assertEquals("""{"error":"bad token"}""", thrown.responseBody)
    }
}
