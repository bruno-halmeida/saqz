package br.com.saqz.groups.data.finance

import br.com.saqz.network.ApiProblem
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class ChargeKindDto { GAME, MONTHLY }
@Serializable enum class ChargeStatusDto { PENDING, PAID, WAIVED, CANCELLED }
@Serializable enum class ExpenseCategoryDto { VENUE, EQUIPMENT, REFEREE, OTHER }
@Serializable enum class ExpenseStatusDto { ACTIVE, VOIDED }
@Serializable enum class ExpenseActionDto { CREATED, EDITED, VOIDED }

@Serializable data class ChargeEventDto(val actorId:String,val oldStatus:ChargeStatusDto,val newStatus:ChargeStatusDto,val note:String?=null,val occurredAt:String)
@Serializable data class ChargeDto(val id:String,val groupId:String,val memberId:String,val kind:ChargeKindDto,val gameId:String?=null,val month:String?=null,val amountCents:Long,val dueDate:String,val status:ChargeStatusDto,val reviewRequired:Boolean=false,val version:Long,val events:List<ChargeEventDto>)
@Serializable data class ChargeListDto(val charges:List<ChargeDto>,val pendingTotalCents:Long?=null,val paidTotalCents:Long?=null,val waivedTotalCents:Long?=null,val cancelledTotalCents:Long?=null)
@Serializable data class MonthlyChargeCommandDto(val requestId:String,val month:String,val amountCents:Long,val dueDate:String,val memberIds:Set<String>)
@Serializable data class ChargeStatusCommandDto(val status:ChargeStatusDto,val note:String?=null)

@Serializable data class ExpenseEventDto(val actorId:String,val action:ExpenseActionDto,val occurredAt:String)
@Serializable data class ExpenseDto(val id:String,val groupId:String,val description:String,val amountCents:Long,val expenseDate:String,val category:ExpenseCategoryDto,val customCategory:String?=null,val notes:String?=null,val status:ExpenseStatusDto,val version:Long,val events:List<ExpenseEventDto>)
@Serializable data class ExpenseListDto(val expenses:List<ExpenseDto>,val activeExpenseTotalCents:Long)
@Serializable data class ExpenseWriteCommandDto(val requestId:String?=null,val description:String,val amountCents:Long,val expenseDate:String,val category:ExpenseCategoryDto,val customCategory:String?=null,val notes:String?=null)
@Serializable data class FinanceTotalsDto(val pendingChargeCents:Long,val paidChargeCents:Long,val waivedChargeCents:Long,val cancelledChargeCents:Long,val activeExpenseCents:Long)

data class VersionedChargeDto(val charge:ChargeDto,val etag:String)
data class VersionedExpenseDto(val expense:ExpenseDto,val etag:String)

sealed interface FinanceGatewayFailure {
    data class Validation(val fields:Map<String,List<String>>):FinanceGatewayFailure
    data object HiddenResource:FinanceGatewayFailure
    data object Forbidden:FinanceGatewayFailure
    data object Conflict:FinanceGatewayFailure
    data object PreconditionRequired:FinanceGatewayFailure
    data object InvalidLifecycle:FinanceGatewayFailure
    data object Authentication:FinanceGatewayFailure
    data object Temporary:FinanceGatewayFailure
    data object InvalidResponse:FinanceGatewayFailure
}

fun NetworkError.toFinanceGatewayFailure():FinanceGatewayFailure=when(this){
    is NetworkError.ApiProblemError->problem.toFinanceFailure()
    NetworkError.InvalidResponse->FinanceGatewayFailure.InvalidResponse
    is NetworkError.HttpStatus,NetworkError.Timeout,NetworkError.Unavailable,NetworkError.PayloadTooLarge->FinanceGatewayFailure.Temporary
}

private fun ApiProblem.toFinanceFailure():FinanceGatewayFailure=when(code){
    "VALIDATION_FAILED"->FinanceGatewayFailure.Validation(fieldErrors.orEmpty())
    "GROUP_NOT_FOUND","GAME_NOT_FOUND"->FinanceGatewayFailure.HiddenResource
    "ACCESS_FORBIDDEN"->FinanceGatewayFailure.Forbidden
    "VERSION_CONFLICT"->FinanceGatewayFailure.Conflict
    "PRECONDITION_REQUIRED"->FinanceGatewayFailure.PreconditionRequired
    "INVALID_GAME_TRANSITION"->FinanceGatewayFailure.InvalidLifecycle
    "AUTHENTICATION_REQUIRED"->FinanceGatewayFailure.Authentication
    else->if(status>=500)FinanceGatewayFailure.Temporary else FinanceGatewayFailure.InvalidResponse
}

interface AthleteFinanceGateway { suspend fun ownCharges(groupId:String):NetworkResult<ChargeListDto> }

interface OrganizerFinanceGateway {
    suspend fun charges(groupId:String):NetworkResult<ChargeListDto>
    suspend fun generateMonthly(groupId:String,command:MonthlyChargeCommandDto):NetworkResult<ChargeListDto>
    suspend fun updateChargeStatus(groupId:String,chargeId:String,etag:String,command:ChargeStatusCommandDto):NetworkResult<VersionedChargeDto>
    suspend fun expenses(groupId:String):NetworkResult<ExpenseListDto>
    suspend fun createExpense(groupId:String,command:ExpenseWriteCommandDto):NetworkResult<VersionedExpenseDto>
    suspend fun editExpense(groupId:String,expenseId:String,etag:String,command:ExpenseWriteCommandDto):NetworkResult<VersionedExpenseDto>
    suspend fun voidExpense(groupId:String,expenseId:String,etag:String):NetworkResult<VersionedExpenseDto>
    suspend fun totals(groupId:String):NetworkResult<FinanceTotalsDto>
}

class AthleteFinanceApi(private val network:AuthenticatedNetworkClient):AthleteFinanceGateway {
    override suspend fun ownCharges(groupId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/charges/me",ChargeListDto.serializer())
}

class OrganizerFinanceApi(private val network:AuthenticatedNetworkClient):OrganizerFinanceGateway {
    private val json=Json{explicitNulls=false}
    override suspend fun charges(groupId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/charges",ChargeListDto.serializer())
    override suspend fun generateMonthly(groupId:String,command:MonthlyChargeCommandDto)=network.execute(HttpMethod.Post,"api/groups/$groupId/charges/monthly",ChargeListDto.serializer(),NetworkRequest(json.encodeToString(command)))
    override suspend fun updateChargeStatus(groupId:String,chargeId:String,etag:String,command:ChargeStatusCommandDto)=network.execute(HttpMethod.Post,"api/groups/$groupId/charges/$chargeId/status",ChargeDto.serializer(),NetworkRequest(json.encodeToString(command),mapOf(HttpHeaders.IfMatch to etag))).versionedCharge()
    override suspend fun expenses(groupId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/expenses",ExpenseListDto.serializer())
    override suspend fun createExpense(groupId:String,command:ExpenseWriteCommandDto)=network.execute(HttpMethod.Post,"api/groups/$groupId/expenses",ExpenseDto.serializer(),NetworkRequest(json.encodeToString(command))).versionedExpense()
    override suspend fun editExpense(groupId:String,expenseId:String,etag:String,command:ExpenseWriteCommandDto)=network.execute(HttpMethod.Put,"api/groups/$groupId/expenses/$expenseId",ExpenseDto.serializer(),NetworkRequest(json.encodeToString(command),mapOf(HttpHeaders.IfMatch to etag))).versionedExpense()
    override suspend fun voidExpense(groupId:String,expenseId:String,etag:String)=network.execute(HttpMethod.Post,"api/groups/$groupId/expenses/$expenseId/void",ExpenseDto.serializer(),NetworkRequest(headers=mapOf(HttpHeaders.IfMatch to etag))).versionedExpense()
    override suspend fun totals(groupId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/finance/totals",FinanceTotalsDto.serializer())
    private fun NetworkResult<ChargeDto>.versionedCharge(): NetworkResult<VersionedChargeDto> = when(this){is NetworkResult.Failure->this;is NetworkResult.Success->metadata.header(HttpHeaders.ETag)?.let{NetworkResult.Success(VersionedChargeDto(value,it),metadata)}?:NetworkResult.Failure(NetworkError.InvalidResponse)}
    private fun NetworkResult<ExpenseDto>.versionedExpense(): NetworkResult<VersionedExpenseDto> = when(this){is NetworkResult.Failure->this;is NetworkResult.Success->metadata.header(HttpHeaders.ETag)?.let{NetworkResult.Success(VersionedExpenseDto(value,it),metadata)}?:NetworkResult.Failure(NetworkError.InvalidResponse)}
}
