const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const source=require('node:fs').readFileSync(require.resolve('../index.html'),'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush=()=>new Promise(setImmediate);
const globalData={version:7,systems:[{system:'BACKEND',mode:'OFF'},{system:'MOBILE',mode:'OFF'}]};
const userData={userId:'test-user',version:4,overrides:[{system:'BACKEND',decision:'INHERIT'},{system:'MOBILE',decision:'INHERIT'}],accountId:null,backendEnabled:false,mobileEnabled:false,accountOperationsEnabled:null};
function setup(){
 let session; const requests=[],timers=new Map();
 const context=vm.createContext({DCLogic:class{props={};setState(p,cb){Object.assign(this.state,p);cb?.();}},window:{crypto:require('node:crypto'),saqzAdmin:{me:{},onSession(fn){session=fn;},fetchAdmin(path,options){return new Promise((resolve,reject)=>requests.push({path,options,resolve,reject}));}}},setTimeout(fn){let id=Symbol();timers.set(id,fn);return id;},clearTimeout(id){timers.delete(id);}});
 const App=vm.runInContext(source+'\nComponent',context);const app=new App();app.componentDidMount();app.recApply('global',globalData);
 return {app,requests,timers,logout(){session(null);}};
}
const ok=data=>({ok:true,status:200,json:async()=>data});
test('mandatory reason prevents request; write includes original version and both systems',async()=>{
 const {app,requests}=setup();await app.recSave('global');assert.equal(requests.length,0);
 app.recEdit('global','reason','  teste controlado  ');const save=app.recSave('global');const body=JSON.parse(requests[0].options.body);
 assert.equal(body.reason,'teste controlado');assert.equal(body.expectedVersion,7);assert.equal(body.systems.length,2);assert.match(body.requestId,/^[a-f0-9-]{36}$/);
 requests[0].resolve(ok({...globalData,version:8}));await save;assert.equal(app.state.recGlobal.version,8);
});
test('timeout freezes editing and retry uses byte-identical body; old response cannot win',async()=>{
 const {app,requests,timers}=setup();app.recEdit('global','reason','teste timeout');const save=app.recSave('global');
 for(const fn of timers.values())fn();await save;assert.equal(app.state.recGlobalPending,true);
 app.recEdit('global','BACKEND','ALL_USERS');assert.equal(app.state.recGlobalDraft.BACKEND,'OFF');app.recLoad('global');assert.equal(requests.length,1);
 const retry=app.recSave('global');assert.equal(requests[1].options.body,requests[0].options.body);
 requests[1].resolve(ok({...globalData,version:8}));await retry;requests[0].resolve(ok({...globalData,version:999}));await flush();assert.equal(app.state.recGlobal.version,8);
});
test('409 prevents further writes until explicit reload; new request gets new version',async()=>{
 const {app,requests}=setup();app.recEdit('global','reason','teste conflito');const save=app.recSave('global');requests[0].resolve({ok:false,status:409});await save;
 await app.recSave('global');assert.equal(requests.length,1);assert.equal(app.state.recGlobalConflict,true);
 app.recLoad('global');requests[1].resolve(ok({...globalData,version:9}));await flush();assert.equal(app.state.recGlobalConflict,false);assert.equal(app.state.recGlobal.version,9);
});
test('user flag uses null for keep and no-account control is disabled',async()=>{
 const {app,requests}=setup();app.state.recUserId='test-user';app.recApply('user',userData);assert.equal(app.recebimentosVals().recAccountDisabled,true);
 app.recEdit('user','reason','teste conta');const save=app.recSave('user');assert.equal(JSON.parse(requests[0].options.body).accountOperationsEnabled,null);requests[0].resolve(ok(userData));await save;
});
test('history discards out-of-order pages and relocates shrinking total',async()=>{
 const {app,requests}=setup();app.recHistory(1);app.recHistory(2);requests[1].resolve(ok({total:30,items:[{reason:'new'}]}));await flush();requests[0].resolve(ok({total:1,items:[{reason:'old'}]}));await flush();assert.equal(app.state.recHistoryData.items[0].reason,'new');
 app.recHistory(3);requests[2].resolve(ok({total:1,items:[]}));await flush();assert.match(requests[3].path,/page=1/);
});
test('logout clears drafts, target, pending body and late parsed response',async()=>{
 const {app,requests,logout}=setup();app.recEdit('global','reason','private reason');const save=app.recSave('global');logout();requests[0].resolve(ok({...globalData,version:20}));await flush();
 assert.equal(app.state.recGlobal,null);assert.equal(app.state.recGlobalDraft.reason,'');assert.equal(app.state.recUserId,null);assert.equal(app.state.recGlobalPending,false);assert.equal(Object.keys(app._recebimentosPending).length,0);
});
test('account boolean and override decisions follow contract',async()=>{
 const {app,requests}=setup();app.state.recUserId='test-user';app.recApply('user',{...userData,accountId:'test-account'});
 app.recEdit('user','BACKEND','ALLOW');app.recEdit('user','MOBILE','DENY');app.recEdit('user','account','false');app.recEdit('user','reason','Restringir conta');
 const save=app.recSave('user');const body=JSON.parse(requests[0].options.body);assert.equal(body.accountOperationsEnabled,false);assert.equal(body.expectedVersion,4);assert.deepEqual(body.overrides,[{system:'BACKEND',decision:'ALLOW'},{system:'MOBILE',decision:'DENY'}]);
 requests[0].resolve(ok(userData));await save;
});
test('outdated selected-user response is discarded after a later target load',async()=>{
 const {app,requests}=setup();app.state.recUserId='first';app.recLoad('user');
 app._recuser++;app.state.recUserBusy=false;app.state.recUserId='second';app.recLoad('user');
 requests[1].resolve(ok({...userData,userId:'second'}));await flush();requests[0].resolve(ok({...userData,userId:'first'}));await flush();assert.equal(app.state.recUser.userId,'second');
});
