package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.application.finance.expense.*
import br.com.saqz.groups.domain.finance.expense.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.*
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.*
import java.util.UUID

data class ExpenseRequest @JsonCreator constructor(@JsonProperty("requestId") val requestId:UUID?=null,@JsonProperty("description") val description:String?,@JsonProperty("amountCents") val amountCents:Long?,@JsonProperty("expenseDate") val expenseDate:LocalDate?,@JsonProperty("category") val category:String?,@JsonProperty("customCategory") val customCategory:String?=null,@JsonProperty("notes") val notes:String?=null)
data class ExpenseEventResponse(val actorId:UUID,val action:String,val occurredAt:Instant)
data class ExpenseResponse(val id:UUID,val groupId:UUID,val description:String,val amountCents:Long,val expenseDate:LocalDate,val category:String,val customCategory:String?,val notes:String?,val status:String,val version:Long,val events:List<ExpenseEventResponse>)
data class ExpenseListResponse(val expenses:List<ExpenseResponse>,val activeExpenseTotalCents:Long)
data class FinanceTotalsResponse(val pendingChargeCents:Long,val paidChargeCents:Long,val waivedChargeCents:Long,val cancelledChargeCents:Long,val activeExpenseCents:Long)

@RestController class ExpenseController(private val actors:VerifiedGroupActorResolver,private val expenses:ExpenseService,private val charges:ChargeManagement){
    @GetMapping("/api/groups/{groupId}/expenses") fun list(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String)=listed(expenses.list(actors.resolve(identity),uuid(groupId)))
    @PostMapping("/api/groups/{groupId}/expenses") fun create(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@RequestBody request:ExpenseRequest):ResponseEntity<ExpenseResponse>{val requestId=request.requestId?:invalid("requestId");return saved(expenses.create(actors.resolve(identity),uuid(groupId),request.draft(),requestId),HttpStatus.CREATED)}
    @PutMapping("/api/groups/{groupId}/expenses/{expenseId}") fun edit(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@PathVariable expenseId:String,@RequestHeader("If-Match",required=false) ifMatch:String?,@RequestBody request:ExpenseRequest)=saved(expenses.edit(actors.resolve(identity),uuid(groupId),uuid(expenseId),version(ifMatch),request.draft()))
    @PostMapping("/api/groups/{groupId}/expenses/{expenseId}/void") fun void(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@PathVariable expenseId:String,@RequestHeader("If-Match",required=false) ifMatch:String?)=saved(expenses.void(actors.resolve(identity),uuid(groupId),uuid(expenseId),version(ifMatch)))
    @GetMapping("/api/groups/{groupId}/finance/totals") fun totals(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String):FinanceTotalsResponse{val actor=actors.resolve(identity);val group=uuid(groupId);val expense=listed(expenses.list(actor,group));val charge=when(val result=charges.listOrganizer(actor,group)){is ChargeListResult.Success->requireNotNull(result.totals);ChargeListResult.Hidden->throw GameNotFoundException();ChargeListResult.Forbidden->throw AccessForbiddenException()};return FinanceTotalsResponse(charge.pendingCents,charge.paidCents,charge.waivedCents,charge.cancelledCents,expense.activeExpenseTotalCents)}
    private fun listed(result:ExpenseResult)=when(result){is ExpenseResult.Listed->ExpenseListResponse(result.value.expenses.map{it.response()},result.value.activeTotalCents);ExpenseResult.Hidden->throw GameNotFoundException();ExpenseResult.Forbidden->throw AccessForbiddenException();else->error("unexpected expense list result")}
    private fun saved(
        result: ExpenseResult,
        status: HttpStatus = HttpStatus.OK,
    ): ResponseEntity<ExpenseResponse> = when (result) {
        is ExpenseResult.Saved -> ResponseEntity.status(status)
            .eTag(result.value.expense.version.toString())
            .body(result.value.response())
        is ExpenseResult.Invalid -> throw InvalidGroupRequestException(
            result.fields.associateWith { listOf("is invalid") },
        )
        ExpenseResult.Hidden -> throw GameNotFoundException()
        ExpenseResult.Forbidden -> throw AccessForbiddenException()
        ExpenseResult.Conflict -> throw VersionConflictException()
        ExpenseResult.InvalidLifecycle -> throw InvalidGameTransitionException()
        is ExpenseResult.Listed -> error("unexpected expense mutation result")
    }
    private fun ExpenseRequest.draft()=ExpenseDraft(description,amountCents,expenseDate,category?.let{runCatching{ExpenseCategory.valueOf(it)}.getOrNull()},customCategory,notes);private fun uuid(v:String)=runCatching{UUID.fromString(v)}.getOrElse{throw GameNotFoundException()};private fun version(v:String?):Long{if(v==null)throw PreconditionRequiredException();return Regex("\"([1-9][0-9]*)\"").matchEntire(v)?.groupValues?.get(1)?.toLong()?:invalid("ifMatch")};private fun invalid(field:String):Nothing=throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}
private fun ExpenseWithEvents.response():ExpenseResponse{val e=expense;val s=e.snapshot;return ExpenseResponse(e.id,e.groupId,s.description,s.amountCents,s.expenseDate,s.category.name,s.customCategory,s.notes,e.status.name,e.version,events.map{ExpenseEventResponse(it.actorId,it.action.name,it.occurredAt)})}
