package br.com.saqz.androidapp.groups.draft

import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.model.*
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.expenses.*
import br.com.saqz.groups.presentation.games.editor.*
import org.junit.Assert.*
import org.junit.Test

class AndroidGroupDraftStoreTest {
    @Test fun setupDraftRoundTripsExactVersionGroupEtagKeyAndForm(){val f=fixture();val draft=setup();assertTrue(f.store.writeSetup(draft));assertEquals(draft,success(f.store.readSetup(GroupDraftKey(GroupDraftResource.UPDATE_GROUP,GROUP))))}
    @Test fun gameDraftRoundTripsExactResourceEtagKeyAndAllowedValues(){val f=fixture();val draft=game();assertTrue(f.store.writeGame(draft));assertEquals(draft,success(f.store.readGame(GROUP,GAME)))}
    @Test fun monthlyDraftRoundTripsSelectionReviewAndStableKey(){val f=fixture();val draft=monthly();assertTrue(f.store.writeMonthly(draft));assertEquals(draft,success(f.store.readMonthly(GROUP)))}
    @Test fun expenseDraftRoundTripsConditionalFormVersionAndKey(){val f=fixture();val draft=expense();assertTrue(f.store.writeExpense(draft));assertEquals(draft,success(f.store.readExpense(GROUP,EXPENSE)))}
    @Test fun clearExpenseOnlyRemovesMatchingResourceIdPreservingSiblings(){val f=fixture();val first=expense().copy(expenseId=EXPENSE);val second=expense().copy(expenseId=EXPENSE2,form=expense().form.copy(description="Outra"));assertTrue(f.store.writeExpense(first));assertTrue(f.store.writeExpense(second));assertTrue(f.store.clearExpense(GROUP,EXPENSE,KEY));assertSame(AndroidDraftReadResult.Missing,f.store.readExpense(GROUP,EXPENSE));assertEquals(second,success(f.store.readExpense(GROUP,EXPENSE2)))}
    @Test fun recreatedRepositoryReadsPreviouslyCommittedDrafts(){val preferences=FakePreferences();AndroidGroupDraftStore(preferences).writeGame(game());val recreated=AndroidGroupDraftStore(preferences);assertEquals(game(),success(recreated.readGame(GROUP,GAME)))}
    @Test fun failedAtomicCommitKeepsPreviouslyCommittedValue(){val f=fixture();assertTrue(f.store.writeMonthly(monthly()));f.preferences.failWrites=true;assertFalse(f.store.writeMonthly(monthly().copy(amountBrl="80,00")));assertEquals("70,00",success(f.store.readMonthly(GROUP)).amountBrl)}
    @Test fun corruptEnvelopeReturnsTypedCorruptWithoutDraft(){val f=fixture();f.store.writeExpense(expense());f.preferences.forceOnly("not-json");assertSame(AndroidDraftReadResult.Corrupt,f.store.readExpense(GROUP,EXPENSE))}
    @Test fun oldPayloadSchemaReturnsTypedUnsupportedWithoutDraft(){val f=fixture();f.store.writeMonthly(monthly().copy(schemaVersion=99));assertSame(AndroidDraftReadResult.UnsupportedSchema,f.store.readMonthly(GROUP))}
    @Test fun serializedStructureExcludesSecretMediaPaymentAndRawErrorFields(){val f=fixture();f.store.writeSetup(setup());f.store.writeGame(game());f.store.writeMonthly(monthly());f.store.writeExpense(expense());val raw=f.preferences.entries().values.joinToString();listOf("bearerToken","inviteCode","photoBytes","photoHandle","paymentCredential","rawServerError").forEach{assertFalse(raw.contains("\"$it\""))}}
    @Test fun mismatchedSuccessKeyPreservesDraftAndMatchingKeyClearsOnlyTarget(){val f=fixture();f.store.writeMonthly(monthly());f.store.writeExpense(expense());assertTrue(f.store.clearMonthly(GROUP,"other-key"));assertNotNull(success(f.store.readMonthly(GROUP)));assertTrue(f.store.clearMonthly(GROUP,KEY));assertSame(AndroidDraftReadResult.Missing,f.store.readMonthly(GROUP));assertEquals(expense(),success(f.store.readExpense(GROUP,EXPENSE)))}
    @Test fun groupLossClearsOnlyMatchingGroupDrafts(){val preferences=FakePreferences();val store=AndroidGroupDraftStore(preferences);store.writeMonthly(monthly());store.writeExpense(expense().copy(groupId=OTHER));assertTrue(store.clearGroup(GROUP));assertSame(AndroidDraftReadResult.Missing,store.readMonthly(GROUP));assertEquals(OTHER,success(store.readExpense(OTHER,EXPENSE)).groupId)}
    @Test fun logoutClearsEveryGroupDraftWithoutTouchingUnrelatedPreference(){val f=fixture();f.store.writeGame(game());f.store.writeMonthly(monthly());f.preferences.values["unrelated"]="keep";assertTrue(f.store.clearAll());assertSame(AndroidDraftReadResult.Missing,f.store.readGame(GROUP,GAME));assertSame(AndroidDraftReadResult.Missing,f.store.readMonthly(GROUP));assertEquals("keep",f.preferences.values["unrelated"])}

    private fun fixture()=Fixture(FakePreferences()).let{it.copy(store=AndroidGroupDraftStore(it.preferences))}
    private fun setup()=GroupSetupDraft(resource=GroupDraftResource.UPDATE_GROUP,groupId=GROUP,groupVersion=7,etag="\"7\"",commandKey=KEY,form=GroupSetupForm("Vôlei",GroupModality.COURT_VOLLEYBALL,GroupComposition.MIXED,city="Recife",level=GroupLevel.CUSTOM,customLevel="Intermediário +",playStyle=GroupPlayStyle.FIVE_ONE,defaultCapacity=24,monthlyFeeCents=7000,monthlyDueDay=10))
    private fun game()=GameEditorDraft(groupId=GROUP,gameId=GAME,seriesId=null,commandKey=KEY,version=GameVersionToken("\"4\""),mode=GameEditorMode.ONE_TIME,form=GameEditorForm("Treino",GameVenue(null,"Arena","Rua 1"),"2026-08-12","19:30:00","America/Sao_Paulo","2026-08-12T22:30:00Z","90","24","2026-08-12T19:00:00Z","25,00","Notas"))
    private fun monthly()=MonthlyChargeDraft(groupId=GROUP,commandKey=KEY,month="2026-08",amountBrl="70,00",dueDate="2026-08-10",selectedMemberIds=setOf("member-1","member-2"),reviewed=true)
    private fun expense()=ExpenseDraft(groupId=GROUP,expenseId=EXPENSE,etag="\"3\"",commandKey=KEY,form=ExpenseForm("Água do jogo","123,45","2026-08-12",ExpenseCategoryDto.OTHER,"Água","Compra manual"))
    private fun <T> success(result:AndroidDraftReadResult<T>)=(result as AndroidDraftReadResult.Success<T>).value
    private data class Fixture(val preferences:FakePreferences,val store:AndroidGroupDraftStore=AndroidGroupDraftStore(preferences))
    private class FakePreferences:AndroidDraftPreferences{val values=linkedMapOf<String,String>();var failWrites=false;override fun read(key:String)=values[key];override fun write(key:String,value:String):Boolean{if(failWrites)return false;values[key]=value;return true};override fun remove(key:String)=values.remove(key)!=null;override fun entries()=values.toMap();fun forceOnly(value:String){val key=values.keys.single();values[key]=value}}
    private companion object{const val GROUP="group-1";const val OTHER="group-2";const val GAME="game-1";const val EXPENSE="expense-1";const val EXPENSE2="expense-2";const val KEY="draft-key"}
}
