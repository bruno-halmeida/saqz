package br.com.saqz.groups.domain.finance.expense

import java.time.LocalDate
import kotlin.test.*

class ExpenseTest{
    @Test fun `valid standard expense trims text and clears contradictory custom`(){val v=valid(draft(custom="ignored"));assertEquals("Aluguel",v.description);assertNull(v.customCategory);assertEquals("Quadra",v.notes)}
    @Test fun `description accepts exact bounds`(){assertNotNull(ExpenseValidator.validate(draft(description="ab")).snapshot);assertNotNull(ExpenseValidator.validate(draft(description="x".repeat(160))).snapshot)}
    @Test fun `description rejects outside bounds and controls`(){assertInvalid("description",draft(description="x"));assertInvalid("description",draft(description="x".repeat(161)));assertInvalid("description",draft(description="bad\nname"))}
    @Test fun `amount accepts exact bounds`(){assertEquals(1,valid(draft(amount=1)).amountCents);assertEquals(99_999_999,valid(draft(amount=99_999_999)).amountCents)}
    @Test fun `amount rejects zero negative and overflow`(){listOf(0L,-1L,100_000_000L).forEach{assertInvalid("amountCents",draft(amount=it))}}
    @Test fun `date and category are required`(){assertInvalid("expenseDate",draft(date=null));assertInvalid("category",draft(category=null))}
    @Test fun `confirmed category vocabulary contains referee not service`(){assertEquals(setOf("VENUE","EQUIPMENT","REFEREE","OTHER"),ExpenseCategory.entries.map{it.name}.toSet())}
    @Test fun `other requires trimmed custom category`(){assertEquals("Arbitragem especial",valid(draft(category=ExpenseCategory.OTHER,custom="  Arbitragem especial ")).customCategory);assertInvalid("customCategory",draft(category=ExpenseCategory.OTHER,custom=null))}
    @Test fun `custom category accepts 2 through 40 characters`(){assertNotNull(ExpenseValidator.validate(draft(category=ExpenseCategory.OTHER,custom="ab")).snapshot);assertNotNull(ExpenseValidator.validate(draft(category=ExpenseCategory.OTHER,custom="x".repeat(40))).snapshot);assertInvalid("customCategory",draft(category=ExpenseCategory.OTHER,custom="x"));assertInvalid("customCategory",draft(category=ExpenseCategory.OTHER,custom="x".repeat(41)))}
    @Test fun `blank notes normalize null`(){assertNull(valid(draft(notes="  ")).notes)}
    @Test fun `notes accept two through five hundred`(){assertEquals("ab",valid(draft(notes="ab")).notes);assertEquals(500,valid(draft(notes="x".repeat(500))).notes?.length)}
    @Test fun `notes reject outside bounds and controls`(){assertInvalid("notes",draft(notes="x"));assertInvalid("notes",draft(notes="x".repeat(501)));assertInvalid("notes",draft(notes="bad\nnote"))}
    private fun valid(d:ExpenseDraft)=requireNotNull(ExpenseValidator.validate(d).snapshot);private fun assertInvalid(field:String,d:ExpenseDraft)=assertTrue(field in ExpenseValidator.validate(d).fields)
    private fun draft(description:String?=" Aluguel ",amount:Long?=5000,date:LocalDate?=LocalDate.of(2026,8,1),category:ExpenseCategory?=ExpenseCategory.VENUE,custom:String?=null,notes:String?=" Quadra ")=ExpenseDraft(description,amount,date,category,custom,notes)
}
