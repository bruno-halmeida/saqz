package br.com.saqz.androidapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.saqz.androidapp.groups.draft.AndroidGroupDraftAdapters
import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import br.com.saqz.groups.presentation.finance.charges.*
import br.com.saqz.groups.presentation.finance.expenses.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidGroupDraftStoreInstrumentedTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun clearBefore() { AndroidGroupDraftAdapters.create(context).store.clearAll() }
    @After fun clearAfter() { AndroidGroupDraftAdapters.create(context).store.clearAll() }

    @Test fun appPrivateDraftsRoundTripAcrossAdapterRecreation(){val first=AndroidGroupDraftAdapters.create(context);val monthly=MonthlyChargeDraft(groupId=GROUP,commandKey=KEY,month="2026-08",amountBrl="70,00",dueDate="2026-08-10",selectedMemberIds=setOf("member"),reviewed=true);var write:MonthlyDraftWriteResult?=null;first.monthly.write(monthly){write=it};assertSame(MonthlyDraftWriteResult.Success,write);val recreated=AndroidGroupDraftAdapters.create(context);var restored:MonthlyDraftReadResult?=null;recreated.monthly.read(GROUP){restored=it};assertEquals(monthly,(restored as MonthlyDraftReadResult.Success).draft)}
    @Test fun matchingSuccessAndGroupLossCleanupAreExact(){val adapters=AndroidGroupDraftAdapters.create(context);val one=ExpenseDraft(groupId=GROUP,commandKey=KEY,form=ExpenseForm("Água","12,34","2026-08-12",ExpenseCategoryDto.VENUE));val two=ExpenseDraft(groupId=OTHER,commandKey="other-key",form=ExpenseForm("Bola","20,00","2026-08-13",ExpenseCategoryDto.EQUIPMENT));adapters.expense.write(one){};adapters.expense.write(two){};adapters.expense.clear(GROUP,null,"wrong"){};assertNotNull((adapters.store.readExpense(GROUP) as br.com.saqz.androidapp.groups.draft.AndroidDraftReadResult.Success).value);assertTrue(adapters.store.clearGroup(GROUP));assertSame(br.com.saqz.androidapp.groups.draft.AndroidDraftReadResult.Missing,adapters.store.readExpense(GROUP));assertEquals(OTHER,(adapters.store.readExpense(OTHER) as br.com.saqz.androidapp.groups.draft.AndroidDraftReadResult.Success).value.groupId)}
    private companion object{const val GROUP="instrumented-group-1";const val OTHER="instrumented-group-2";const val KEY="instrumented-key"}
}
