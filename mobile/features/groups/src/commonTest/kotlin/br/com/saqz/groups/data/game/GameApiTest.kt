package br.com.saqz.groups.data.game

import br.com.saqz.network.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class GameApiTest {
    @Test fun `list decodes every status and server derived count`()=runTest{val result=api{respond("[$GAME_JSON,${GAME_JSON.replace("DRAFT","PUBLISHED")},${GAME_JSON.replace("DRAFT","CANCELLED")},${GAME_JSON.replace("DRAFT","COMPLETED")} ]",headers=jsonHeaders())}.list(GROUP);val games=assertIs<NetworkResult.Success<List<GameDto>>>(result).value;assertEquals(GameStatusDto.entries,games.map{it.status});assertEquals(3,games.first().confirmedCount);assertEquals(21,games.first().availableSpots);assertEquals(2,games.first().waitlistCount)}
    @Test fun `read preserves exact quoted ETag`()=runTest{val result=api{gameResponse(etag="\"23\"")}.read(GROUP,GAME);assertEquals("\"23\"",assertIs<NetworkResult.Success<VersionedGameDto>>(result).value.etag)}
    @Test fun `read without ETag is invalid response`()=runTest{val result=api{respond(GAME_JSON,headers=jsonHeaders())}.read(GROUP,GAME);assertEquals(NetworkError.InvalidResponse,assertIs<NetworkResult.Failure>(result).error)}
    @Test fun `create serializes command key inputs and omits server owned fields`()=runTest{api{request->val body=request.json();assertEquals("POST",request.method.value);assertEquals("/api/groups/$GROUP/games",request.url.encodedPath);assertEquals(KEY,body["requestId"]!!.jsonPrimitive.content);assertFalse(body.containsKey("status"));assertFalse(body.containsKey("confirmedCount"));assertFalse(body.containsKey("availableSpots"));gameResponse(HttpStatusCode.Created)}.create(GROUP,gameCommand())}
    @Test fun `nullable fee and notes are omitted`()=runTest{api{request->val body=request.json();assertFalse(body.containsKey("gameFeeCents"));assertFalse(body.containsKey("notes"));gameResponse(HttpStatusCode.Created)}.create(GROUP,gameCommand().copy(gameFeeCents=null,notes=null))}
    @Test fun `edit retains quoted ETag and exact body`()=runTest{api{request->assertEquals("PUT",request.method.value);assertEquals("\"7\"",request.headers[HttpHeaders.IfMatch]);assertEquals(KEY,request.json()["requestId"]!!.jsonPrimitive.content);gameResponse()}.edit(GROUP,GAME,"\"7\"",gameCommand())}
    @Test fun `publish command has no client authored target state`()=runTest{api{request->assertEquals("/api/groups/$GROUP/games/$GAME/publish",request.url.encodedPath);assertEquals(null,request.body.takeIf{it is TextContent});assertEquals("\"7\"",request.headers[HttpHeaders.IfMatch]);gameResponse()}.lifecycle(GROUP,GAME,"\"7\"","publish")}
    @Test fun `cancel command uses explicit route and ETag`()=runTest{api{request->assertEquals("/api/groups/$GROUP/games/$GAME/cancel",request.url.encodedPath);assertEquals("\"8\"",request.headers[HttpHeaders.IfMatch]);gameResponse()}.lifecycle(GROUP,GAME,"\"8\"","cancel")}
    @Test fun `complete command uses explicit route and ETag`()=runTest{api{request->assertEquals("/api/groups/$GROUP/games/$GAME/complete",request.url.encodedPath);assertEquals("\"9\"",request.headers[HttpHeaders.IfMatch]);gameResponse()}.lifecycle(GROUP,GAME,"\"9\"","complete")}
    @Test fun `series create serializes multi slot local rules without resolved instants`()=runTest{api{request->val body=request.json();assertEquals("/api/groups/$GROUP/game-series",request.url.encodedPath);assertEquals(2,body["slots"]!!.jsonArray.size);assertFalse(body["slots"]!!.jsonArray.first().jsonObject.containsKey("startsAt"));seriesResponse(HttpStatusCode.Created)}.createSeries(GROUP,seriesCommand())}
    @Test fun `series response round trips nested slots nullable fee and resolved occurrence`()=runTest{val result=api{seriesResponse()}.readSeries(GROUP,SERIES);val value=assertIs<NetworkResult.Success<VersionedSeriesDto>>(result).value;assertEquals(WeekdayDto.WEDNESDAY,value.series.slots.first().weekday);assertNull(value.series.slots[1].gameFeeCents);assertEquals("2026-08-12T22:30:00Z",value.series.occurrences.first().startsAt)}
    @Test fun `only this boundary serializes explicit enum and stable key`()=runTest{api{request->val body=request.json();assertEquals("ONLY_THIS",body["scope"]!!.jsonPrimitive.content);assertEquals("CANCEL",body["action"]!!.jsonPrimitive.content);assertEquals(KEY,body["requestId"]!!.jsonPrimitive.content);assertEquals("\"7\"",request.headers[HttpHeaders.IfMatch]);seriesResponse()}.boundary(GROUP,SERIES,"\"7\"",SeriesBoundaryCommand(KEY,SeriesBoundaryScopeDto.ONLY_THIS,SeriesBoundaryActionDto.CANCEL,gameId=GAME))}
    @Test fun `this and future boundary carries local successor not resolved instants`()=runTest{api{request->val body=request.json();assertEquals("THIS_AND_FUTURE",body["scope"]!!.jsonPrimitive.content);val successor=body["successor"]!!.jsonObject;assertEquals("2026-08-19",successor["localStartDate"]!!.jsonPrimitive.content);assertFalse(successor.containsKey("occurrences"));seriesResponse()}.boundary(GROUP,SERIES,"\"7\"",SeriesBoundaryCommand(KEY,SeriesBoundaryScopeDto.THIS_AND_FUTURE,SeriesBoundaryActionDto.EDIT,boundary="2026-08-19",currentRevisionId=REVISION,successor=seriesCommand().copy(localStartDate="2026-08-19")))}
    @Test
    fun `auth refresh retries identical create command key and body`() = runTest {
        var calls = 0
        val bodies = mutableListOf<String>()
        val gateway = api(RefreshingTokens()) { request ->
            calls++
            bodies += (request.body as TextContent).text
            if (calls == 1) {
                respond(
                    """{"status":401,"code":"AUTHENTICATION_REQUIRED","correlationId":"c"}""",
                    HttpStatusCode.Unauthorized,
                    jsonHeaders(),
                )
            } else {
                gameResponse(HttpStatusCode.Created)
            }
        }
        gateway.create(GROUP, gameCommand())
        assertEquals(2, calls)
        assertEquals(listOf(KEY, KEY), bodies.map { Json.parseToJsonElement(it).jsonObject["requestId"]!!.jsonPrimitive.content })
        assertEquals(bodies.first(), bodies.last())
    }
    @Test fun `validation hidden conflict and lifecycle problems remain distinct`() {assertIs<GameGatewayFailure.Validation>(problem(400,"VALIDATION_FAILED",mapOf("title" to listOf("bad"))).toGameGatewayFailure());assertEquals(GameGatewayFailure.HiddenResource,problem(404,"GAME_NOT_FOUND").toGameGatewayFailure());assertEquals(GameGatewayFailure.Conflict,problem(409,"VERSION_CONFLICT").toGameGatewayFailure());assertEquals(GameGatewayFailure.InvalidLifecycle,problem(409,"INVALID_GAME_TRANSITION").toGameGatewayFailure())}
    @Test fun `transport server and malformed response map safely`() {assertEquals(GameGatewayFailure.Temporary,NetworkError.Timeout.toGameGatewayFailure());assertEquals(GameGatewayFailure.Temporary,NetworkError.HttpStatus(503).toGameGatewayFailure());assertEquals(GameGatewayFailure.InvalidResponse,NetworkError.InvalidResponse.toGameGatewayFailure())}

    private fun gameCommand()=GameWriteCommand(KEY,"Treino semanal",GameVenueDto(null,"Arena Central","Rua das Flores 100","Quadra 2"),"2026-08-12","19:30:00","America/Sao_Paulo","2026-08-12T22:30:00Z",90,24,"2026-08-12T19:30:00Z",2500,false,"Levar bola")
    private fun seriesCommand()=WeeklySeriesWriteCommand(SERIES,REVISION,"America/Sao_Paulo","2026-08-12",null,listOf(slot("slot-1",2500),slot("slot-2",null)))
    private fun slot(id:String,fee:Long?)=WeeklySlotDto(id,WeekdayDto.WEDNESDAY,if(id=="slot-1")"19:30:00" else "21:00:00",90,GameVenueDto(null,"Arena Central","Rua das Flores 100"),24,180,fee,"Treino semanal")
    private fun api(tokens:IdTokenProvider=Tokens(),handler:suspend MockRequestHandleScope.(HttpRequestData)->HttpResponseData):GameApi{val network=NetworkClient(MockEngine{request->handler(request)},NetworkConfig(NetworkEnvironment.Test,"https://api.example.test/"));return GameApi(AuthenticatedNetworkClient(network,tokens,NoopInvalidator()))}
    private fun MockRequestHandleScope.gameResponse(status:HttpStatusCode=HttpStatusCode.OK,etag:String="\"7\"")=respond(GAME_JSON,status,headersOf(HttpHeaders.ContentType to listOf("application/json"),HttpHeaders.ETag to listOf(etag)))
    private fun MockRequestHandleScope.seriesResponse(status:HttpStatusCode=HttpStatusCode.OK)=respond(SERIES_JSON,status,headersOf(HttpHeaders.ContentType to listOf("application/json"),HttpHeaders.ETag to listOf("\"7\"")))
    private fun HttpRequestData.json()=Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders()=headersOf(HttpHeaders.ContentType,"application/json")
    private fun problem(status:Int,code:String,fields:Map<String,List<String>>?=null)=NetworkError.ApiProblemError(ApiProblem(status,code,"corr",fields))
    private class Tokens:IdTokenProvider{override fun token(forceRefresh:Boolean,completion:(TokenResult)->Unit)=completion(TokenResult.Available("token"))}
    private class RefreshingTokens:IdTokenProvider{override fun token(forceRefresh:Boolean,completion:(TokenResult)->Unit)=completion(TokenResult.Available(if(forceRefresh)"fresh" else "old"))}
    private class NoopInvalidator:SessionInvalidator{override fun invalidate()=Unit}
    private companion object{
        const val GROUP="group-1";const val GAME="game-1";const val SERIES="series-1";const val REVISION="revision-1";const val KEY="request-key"
        const val GAME_JSON="""{"id":"game-1","groupId":"group-1","title":"Treino semanal","venue":{"name":"Arena Central","address":"Rua das Flores 100"},"localDate":"2026-08-12","localTime":"19:30:00","zoneId":"America/Sao_Paulo","startsAt":"2026-08-12T22:30:00Z","durationMinutes":90,"capacity":24,"confirmationDeadline":"2026-08-12T19:30:00Z","gameFeeCents":2500,"notes":"Levar bola","status":"DRAFT","version":1,"confirmedCount":3,"availableSpots":21,"waitlistCount":2}"""
        const val SERIES_JSON="""{"id":"series-1","revisionId":"revision-1","revisionNumber":1,"zoneId":"America/Sao_Paulo","localStartDate":"2026-08-12","slots":[{"slotKey":"slot-1","weekday":"WEDNESDAY","localTime":"19:30:00","durationMinutes":90,"venue":{"name":"Arena Central","address":"Rua das Flores 100"},"capacity":24,"confirmationLeadMinutes":180,"gameFeeCents":2500,"title":"Treino semanal"},{"slotKey":"slot-2","weekday":"WEDNESDAY","localTime":"21:00:00","durationMinutes":90,"venue":{"name":"Arena Central","address":"Rua das Flores 100"},"capacity":24,"confirmationLeadMinutes":180,"title":"Treino semanal"}],"occurrences":[{"id":"game-1","localDate":"2026-08-12","localTime":"19:30:00","startsAt":"2026-08-12T22:30:00Z","status":"DRAFT","version":1}],"version":1}"""
    }
}
