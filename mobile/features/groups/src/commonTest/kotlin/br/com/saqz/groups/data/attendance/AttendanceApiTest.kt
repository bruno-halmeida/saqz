package br.com.saqz.groups.data.attendance

import br.com.saqz.network.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AttendanceApiTest {
    @Test fun `read round trips no response and aggregate counts`()=runTest{val result=api{detailResponse(DETAIL_NO_RESPONSE)}.read(GROUP,GAME);val detail=assertIs<NetworkResult.Success<AttendanceDetailDto>>(result).value;assertNull(detail.ownAttendance);assertEquals(2,detail.confirmedCount);assertEquals(1,detail.availableSpots);assertEquals(1,detail.waitlistCount)}
    @Test fun `read round trips confirmed own response`()=runTest{assertEquals(AttendanceStatusDto.CONFIRMED,readStatus("CONFIRMED"))}
    @Test fun `read round trips declined own response`()=runTest{assertEquals(AttendanceStatusDto.DECLINED,readStatus("DECLINED"))}
    @Test fun `read round trips waitlisted position`()=runTest{val result=api{detailResponse(detail("WAITLISTED",4))}.read(GROUP,GAME);assertEquals(4,assertIs<NetworkResult.Success<AttendanceDetailDto>>(result).value.ownAttendance!!.waitlistPosition)}
    @Test fun `athlete detail contains only own response and aggregate counts`()=runTest{api{request->assertEquals("/api/groups/$GROUP/games/$GAME/attendance",request.url.encodedPath);val keys=Json.parseToJsonElement(DETAIL_NO_RESPONSE).jsonObject.keys;assertEquals(setOf("ownAttendance","confirmedCount","availableSpots","waitlistCount","capacity"),keys);detailResponse(DETAIL_NO_RESPONSE)}.read(GROUP,GAME)}
    @Test fun `self command sends stable key and intent only`()=runTest{api{request->val body=request.json();assertEquals(HttpMethod.Put,request.method);assertEquals(setOf("requestId","intent"),body.keys);assertEquals(KEY,body.getValue("requestId").jsonPrimitive.content);assertFalse(body.keys.any{it in setOf("waitlistPosition","charge","capacity","actorId","occurredAt")});mutationResponse()}.respond(GROUP,GAME,SelfAttendanceCommand(KEY,AttendanceIntentDto.CONFIRM))}
    @Test fun `organizer override sends explicit member target and reason`()=runTest{api{request->val body=request.json();assertEquals(HttpMethod.Post,request.method);assertEquals("/api/groups/$GROUP/games/$GAME/attendance/override",request.url.encodedPath);assertEquals(setOf("requestId","memberId","intent","reason"),body.keys);assertEquals("Correção manual",body.getValue("reason").jsonPrimitive.content);mutationResponse()}.override(GROUP,GAME,OverrideAttendanceCommand(KEY,MEMBER,AttendanceIntentDto.DECLINE,"Correção manual"))}
    @Test fun `capacity command preserves quoted game ETag and exact body`()=runTest{api{request->assertEquals(HttpMethod.Put,request.method);assertEquals("\"7\"",request.headers[HttpHeaders.IfMatch]);assertEquals(setOf("requestId","capacity"),request.json().keys);capacityResponse()}.capacity(GROUP,GAME,"\"7\"",CapacityCommand(KEY,24))}
    @Test fun `mutation preserves attendance ETag`()=runTest{val result=api{mutationResponse(etag="\"9\"")}.respond(GROUP,GAME,SelfAttendanceCommand(KEY,AttendanceIntentDto.CONFIRM));assertEquals("\"9\"",assertIs<NetworkResult.Success<VersionedAttendanceMutationDto>>(result).value.etag)}
    @Test fun `capacity preserves returned game ETag`()=runTest{val result=api{capacityResponse(etag="\"8\"")}.capacity(GROUP,GAME,"\"7\"",CapacityCommand(KEY,24));assertEquals("\"8\"",assertIs<NetworkResult.Success<VersionedCapacityDto>>(result).value.etag)}
    @Test fun `full outcome is distinct authoritative waitlisted success`()=runTest{val result=api{mutationResponse(MUTATION_JSON.replace("CONFIRMED","WAITLISTED").replace("\"waitlistPosition\":null","\"waitlistPosition\":3"))}.respond(GROUP,GAME,SelfAttendanceCommand(KEY,AttendanceIntentDto.CONFIRM));val value=assertIs<NetworkResult.Success<VersionedAttendanceMutationDto>>(result).value.value;assertEquals(AttendanceStatusDto.WAITLISTED,value.attendance.status);assertEquals(3,value.attendance.waitlistPosition)}
    @Test fun `auth refresh retries identical self command key and body`()=runTest{var calls=0;val bodies=mutableListOf<String>();val gateway=api(RefreshingTokens()){request->calls++;bodies+=(request.body as TextContent).text;if(calls==1)unauthorized() else mutationResponse()};gateway.respond(GROUP,GAME,SelfAttendanceCommand(KEY,AttendanceIntentDto.CONFIRM));assertEquals(2,calls);assertEquals(bodies.first(),bodies.last());assertEquals(listOf(KEY,KEY),bodies.map{Json.parseToJsonElement(it).jsonObject.getValue("requestId").jsonPrimitive.content})}
    @Test fun `auth refresh preserves capacity key body and ETag`()=runTest{var calls=0;val bodies=mutableListOf<String>();val etags=mutableListOf<String?>();val gateway=api(RefreshingTokens()){request->calls++;bodies+=(request.body as TextContent).text;etags+=request.headers[HttpHeaders.IfMatch];if(calls==1)unauthorized() else capacityResponse()};gateway.capacity(GROUP,GAME,"\"7\"",CapacityCommand(KEY,25));assertEquals(listOf<String?>("\"7\"","\"7\""),etags);assertEquals(bodies.first(),bodies.last())}
    @Test fun `deadline frozen stale and hidden problems remain distinct`() {assertEquals(AttendanceGatewayFailure.DeadlinePassed,problem(409,"ATTENDANCE_DEADLINE_PASSED").toAttendanceGatewayFailure());assertEquals(AttendanceGatewayFailure.Frozen,problem(409,"ATTENDANCE_FROZEN").toAttendanceGatewayFailure());assertEquals(AttendanceGatewayFailure.Conflict,problem(409,"VERSION_CONFLICT").toAttendanceGatewayFailure());assertEquals(AttendanceGatewayFailure.HiddenResource,problem(404,"GAME_NOT_FOUND").toAttendanceGatewayFailure())}
    @Test fun `validation authentication and temporary failures remain distinct`() {assertIs<AttendanceGatewayFailure.Validation>(problem(400,"VALIDATION_FAILED",mapOf("reason" to listOf("invalid"))).toAttendanceGatewayFailure());assertEquals(AttendanceGatewayFailure.Authentication,problem(401,"AUTHENTICATION_REQUIRED").toAttendanceGatewayFailure());assertEquals(AttendanceGatewayFailure.Temporary,NetworkError.Timeout.toAttendanceGatewayFailure())}
    @Test fun `versioned responses without ETag are invalid`()=runTest{val mutation=api{respond(MUTATION_JSON,headers=jsonHeaders())}.respond(GROUP,GAME,SelfAttendanceCommand(KEY,AttendanceIntentDto.CONFIRM));val capacity=api{respond(CAPACITY_JSON,headers=jsonHeaders())}.capacity(GROUP,GAME,"\"7\"",CapacityCommand(KEY,24));assertEquals(NetworkError.InvalidResponse,assertIs<NetworkResult.Failure>(mutation).error);assertEquals(NetworkError.InvalidResponse,assertIs<NetworkResult.Failure>(capacity).error)}

    private suspend fun readStatus(status:String):AttendanceStatusDto{val result=api{detailResponse(detail(status,null))}.read(GROUP,GAME);return assertIs<NetworkResult.Success<AttendanceDetailDto>>(result).value.ownAttendance!!.status}
    private fun detail(status:String,position:Long?)="""{"ownAttendance":{"memberId":"$MEMBER","status":"$status","waitlistPosition":${position?:"null"},"version":2},"confirmedCount":2,"availableSpots":1,"waitlistCount":1,"capacity":3}"""
    private fun api(tokens:IdTokenProvider=Tokens(),handler:suspend MockRequestHandleScope.(HttpRequestData)->HttpResponseData):AttendanceApi{val network=NetworkClient(MockEngine{request->handler(request)},NetworkConfig(NetworkEnvironment.Test,"https://api.example.test/"));return AttendanceApi(AuthenticatedNetworkClient(network,tokens,NoopInvalidator()))}
    private fun MockRequestHandleScope.detailResponse(body:String)=respond(body,headers=jsonHeaders())
    private fun MockRequestHandleScope.mutationResponse(body:String=MUTATION_JSON,etag:String="\"2\"")=respond(body,headers=versionedHeaders(etag))
    private fun MockRequestHandleScope.capacityResponse(etag:String="\"2\"")=respond(CAPACITY_JSON,headers=versionedHeaders(etag))
    private fun MockRequestHandleScope.unauthorized()=respond("""{"status":401,"code":"AUTHENTICATION_REQUIRED","correlationId":"c"}""",HttpStatusCode.Unauthorized,jsonHeaders())
    private fun HttpRequestData.json()=Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders()=headersOf(HttpHeaders.ContentType,"application/json")
    private fun versionedHeaders(etag:String)=headersOf(HttpHeaders.ContentType to listOf("application/json"),HttpHeaders.ETag to listOf(etag))
    private fun problem(status:Int,code:String,fields:Map<String,List<String>>?=null)=NetworkError.ApiProblemError(ApiProblem(status,code,"corr",fields))
    private class Tokens:IdTokenProvider{override fun token(forceRefresh:Boolean,completion:(TokenResult)->Unit)=completion(TokenResult.Available("token"))}
    private class RefreshingTokens:IdTokenProvider{override fun token(forceRefresh:Boolean,completion:(TokenResult)->Unit)=completion(TokenResult.Available(if(forceRefresh)"fresh" else "old"))}
    private class NoopInvalidator:SessionInvalidator{override fun invalidate()=Unit}
    private companion object {
        const val GROUP="group-1";const val GAME="game-1";const val MEMBER="member-1";const val KEY="attendance-key"
        const val DETAIL_NO_RESPONSE="""{"ownAttendance":null,"confirmedCount":2,"availableSpots":1,"waitlistCount":1,"capacity":3}"""
        const val MUTATION_JSON="""{"attendance":{"memberId":"member-1","status":"CONFIRMED","waitlistPosition":null,"version":2},"audit":{"actorId":"member-1","source":"SELF","oldStatus":null,"newStatus":"CONFIRMED","occurredAt":"2026-08-01T10:00:00Z"},"promotedCount":0,"detail":{"ownAttendance":{"memberId":"member-1","status":"CONFIRMED","version":2},"confirmedCount":1,"availableSpots":2,"waitlistCount":0,"capacity":3}}"""
        const val CAPACITY_JSON="""{"capacity":24,"version":2,"promotedCount":1,"detail":{"ownAttendance":null,"confirmedCount":3,"availableSpots":21,"waitlistCount":0,"capacity":24}}"""
    }
}
