const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const source=require('node:fs').readFileSync(require.resolve('../index.html'),'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush=()=>new Promise(setImmediate);
const ok=(data,status=200)=>({ok:true,status,json:async()=>data});
function setup(){
 let session;const requests=[];
 const ctx=vm.createContext({DCLogic:class{props={};setState(p){Object.assign(this.state,p);}},window:{saqzAdmin:{me:{},onSession(fn){session=fn;},fetchAdmin(path,options){return new Promise((resolve,reject)=>requests.push({path,options,resolve,reject}));}}},setTimeout,clearTimeout});
 const App=vm.runInContext(source+'\nComponent',ctx);const app=new App();app.componentDidMount();
 return {app,requests,logout(){session(null);}};
}
test('loads persisted mode and coupons and retries a failed load',async()=>{
 const {app,requests}=setup();app.trialLoad();assert.equal(app.state.trialBusy,true);
 requests[0].resolve(ok({mode:'COUPON_ONLY'}));requests[1].resolve(ok([{code:'ARENA',trialDays:45,uses:2}]));await flush();
 assert.equal(app.state.trialData.mode,'COUPON_ONLY');assert.equal(app.state.trialCoupons[0].trialDays,45);
 app.trialLoad();requests[2].reject(Error('offline'));requests[3].resolve(ok([]));await flush();
 assert.match(app.state.trialError,/carregar/i);assert.equal(app.state.trialBusy,false);
 app.trialLoad();requests[4].resolve(ok({mode:'OFF'}));requests[5].resolve(ok([]));await flush();assert.equal(app.state.trialData.mode,'OFF');
});
test('all three mode writes require confirmation from server and reject duplicate clicks',async()=>{
 for(const mode of ['ON','OFF','COUPON_ONLY']){
  const {app,requests}=setup();app.state.trialData={mode:'ON'};app.state.trialModeDraft=mode;
  app.trialSaveMode();app.trialSaveMode();assert.equal(requests.length,1);assert.deepEqual(JSON.parse(requests[0].options.body),{mode});
  assert.equal(app.state.trialData.mode,'ON');requests[0].resolve({ok:false,status:500});await flush();
  assert.equal(app.state.trialData.mode,'ON');assert.match(app.state.trialError,/confirmad/i);assert.equal(app.state.trialBusy,false);
 }
});
test('creates trial coupon with custom days and optional campaign expiry and cap',async()=>{
 const {app,requests}=setup();app.state.trialForm={code:' arena ',campaign:'Quadra A',days:'45',max:'10',until:'2099-10-20'};
 app.trialCreate();assert.equal(requests[0].path,'/admin/trial-coupons');
 assert.deepEqual(JSON.parse(requests[0].options.body),{code:'ARENA',campaign:'Quadra A',trialDays:45,maxUses:10,validUntil:'2099-10-20T23:59:59.999Z'});
 requests[0].resolve(ok({},201));await flush();assert.equal(app.state.trialForm.code,'');assert.equal(app.state.trialForm.days,'14');
 assert.equal(requests[1].path,'/admin/trial-offer');assert.equal(requests[2].path,'/admin/trial-coupons');
 requests[1].resolve(ok({mode:'ON'}));requests[2].resolve(ok([]));await flush();
});
test('invalid days cap code and date never send requests; duplicate preserves form',async()=>{
 const {app,requests}=setup();
 for(const patch of [{days:'0'},{days:'366'},{days:'1.5'},{max:'-1'},{code:'A B'},{until:'2099-02-31'}]){
  app.state.trialForm={code:'ARENA',campaign:'',days:'14',max:'',until:'',...patch};app.trialCreate();assert.equal(requests.length,0);
 }
 app.state.trialForm={code:'ARENA',campaign:'',days:'14',max:'',until:''};app.trialCreate();requests[0].resolve({ok:false,status:409});await flush();
 assert.equal(app.state.trialForm.code,'ARENA');assert.match(app.state.trialError,/existe/i);
});
test('deactivation preserves displayed history until server confirms and reloads',async()=>{
 const {app,requests}=setup();app.state.trialCoupons=[{id:'id',code:'A',uses:3,active:true}];app.trialDeactivate('id');
 assert.equal(requests[0].path,'/admin/trial-coupons/id/deactivate');assert.equal(app.state.trialCoupons[0].active,true);
 requests[0].resolve({ok:true,status:204});await flush();requests[1].resolve(ok({mode:'OFF'}));requests[2].resolve(ok([{id:'id',code:'A',uses:3,active:false}]));await flush();
 assert.equal(app.state.trialCoupons[0].uses,3);assert.equal(app.state.trialCoupons[0].active,false);
});
test('logout clears coupon drafts and discards a late mutation',async()=>{
 const {app,requests,logout}=setup();app.state.trialForm={code:'PRIVATE',campaign:'Partner',days:'30',max:'',until:''};app.trialCreate();logout();
 requests[0].resolve(ok({},201));await flush();assert.equal(app.state.trialForm.code,'');assert.equal(app.state.trialData,null);assert.equal(app.state.trialBusy,false);assert.equal(requests.length,1);
});
