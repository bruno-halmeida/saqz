package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.GroupScheduleController
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.settings.*
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.*
import br.com.saqz.access.application.session.BootstrapSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.*
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupSettingsEndpointIntegrationTest.SettingsTestConfiguration::class, GroupScheduleEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupScheduleEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var groups: GroupSettingsEndpointIntegrationTest.RecordingSettingsReadRepository
    @Autowired private lateinit var schedules: MemorySchedules
    private val id = UUID.randomUUID()
    @BeforeEach fun reset() {
        groups.snapshot = GroupReadSnapshot(id, AccessName.from("Group"), IanaTimeZone.from("UTC"), GroupRole.OWNER, 7)
        schedules.value = VersionedGroupSchedule(GroupSchedule(false, emptyList(), 120, 360, false), 7)
    }
    @Test fun `PUT decodes slots persists and GET returns ETag`() {
        val response = request("PUT")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("\"8\"", response.headers().firstValue("ETag").orElse(""))
        val loaded = request("GET")
        assertEquals(response.body(), loaded.body())
        assertTrue(loaded.body().contains("19:30"))
        assertEquals(90, schedules.value.schedule.durationMinutes)
    }
    @Test fun `If Match is mandatory and stale version conflicts`() {
        assertEquals(428, request("PUT", null).statusCode())
        assertEquals(409, request("PUT", "\"6\"").statusCode())
    }
    @Test fun `athlete cannot write schedule`() {
        groups.snapshot = groups.snapshot!!.copy(role = GroupRole.ATHLETE)
        assertEquals(403, request("PUT").statusCode())
        assertEquals(7, schedules.value.version)
    }
    private fun request(method: String, etag: String? = "\"7\""): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$id/schedule"))
            .header("Authorization", "Bearer settings-token").header("Content-Type", "application/json")
        if (method == "PUT") {
            builder.PUT(HttpRequest.BodyPublishers.ofString("""{"recurring":true,"slots":[{"weekday":"TUESDAY","startTime":"19:30"}],"durationMinutes":90,"confirmationLeadMinutes":720,"paused":true}"""))
            if (etag != null) builder.header("If-Match", etag)
        } else builder.GET()
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean fun schedules() = MemorySchedules()
        @Bean fun scheduleService(transaction: TransactionRunner,
            groups: GroupSettingsEndpointIntegrationTest.RecordingSettingsReadRepository,
            schedules: MemorySchedules) = GroupScheduleService(transaction, groups, schedules)
        @Bean fun scheduleController(bootstrap: BootstrapSession, service: GroupScheduleService) =
            GroupScheduleController(verifiedGroupActorResolver(bootstrap), service)
    }
    class MemorySchedules : GroupScheduleRepository {
        lateinit var value: VersionedGroupSchedule
        override fun read(groupId: UUID) = value
        override fun update(groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): Boolean {
            if (expectedVersion != value.version) return false
            value = VersionedGroupSchedule(schedule, expectedVersion + 1)
            return true
        }
    }
}
