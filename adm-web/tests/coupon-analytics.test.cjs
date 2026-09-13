const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const source=require('node:fs').readFileSync(require.resolve('../index.html'),'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush=()=>new Promise(setImmediate);
const ok=data=>({ok:true,json:async()=>data});
const metrics={users:3,payingUsers:2,conversionPercent:66.67,payments:3,revenueCents:2000,incompleteUsers:1};
const data=()=>({asOf:'2026-09-13T12:00:00Z',summary:metrics,coupons:[
 {id:'t',type:'TRIAL',code:'ARENA',campaign:'Quadra A',trialDays:45,status:'ACTIVE',metrics,ongoingTrials:1,endedTrials:2,endedConversionPercent:50},
 {id:'d',type:'DISCOUNT',code:'ARENA',discountPercent:20,status:'EXPIRED',metrics,ongoingTrials:null,endedTrials:null,endedConversionPercent:null},
 {id:'z',type:'TRIAL',code:'ZERO',campaign:null,trialDays:14,status:'INACTIVE',metrics:{users:0,payingUsers:0,conversionPercent:null,payments:0,revenueCents:0,incompleteUsers:0},ongoingTrials:0,endedTrials:0,endedConversionPercent:null}
]});
function setup(){
 let session;const requests=[];
 const ctx=vm.createContext({DCLogic:class{props={};setState(p){Object.assign(this.state,p);}},window:{saqzAdmin:{me:{},onSession(fn){session=fn;},fetchAdmin(path,options){return new Promise((resolve,reject)=>requests.push({path,options,resolve,reject}));}}},setTimeout,clearTimeout});
 const App=vm.runInContext(source+'\nComponent',ctx);const app=new App();app.componentDidMount();
 return {app,requests,session};
}
test('analytics loads actual report once while pending and formats metrics without summing coupon rows',async()=>{
 const {app,requests}=setup();app.analyticsLoad();app.analyticsLoad();
 assert.equal(requests.length,1);assert.equal(requests[0].path,'/admin/coupon-analytics');assert.equal(app.analyticsVals().analytics.busy,true);
 assert.equal(app.analyticsVals().analytics.ready,false);
 requests[0].resolve(ok(data()));await flush();const v=app.analyticsVals();
 assert.equal(v.analytics.ready,true);assert.equal(v.analytics.users,3);assert.equal(v.analytics.payers,2);assert.equal(v.analytics.rate,'66,67%');assert.match(v.analytics.revenue,/20,00/);
 assert.equal(v.analyticsRows.length,3);assert.equal(v.analyticsRows[0].benefit,'Trial · 45 dias');assert.equal(v.analyticsRows[1].benefit,'Desconto · 20%');
 assert.equal(v.analyticsRows[0].maturity,'1 em andamento · 2 encerrados');assert.equal(v.analyticsRows[0].matureRate,'Conversão dos encerrados: 50%');assert.equal(v.analyticsRows[1].maturity,'Não se aplica');
 assert.equal(v.analyticsRows[2].rate,'—');assert.equal(v.analyticsRows[2].status,'Desativado');assert.match(v.analyticsRows[0].incomplete,/1 organizador/);
});
test('type and search filters preserve all statuses and distinguish identical codes',()=>{
 const {app}=setup();app.state.analyticsData=data();
 app.analyticsVals().analytics.onType({target:{value:'DISCOUNT'}});assert.equal(app.analyticsVals().analyticsRows.length,1);assert.equal(app.analyticsVals().analyticsRows[0].key,'DISCOUNT:d');
 app.analyticsVals().analytics.onType({target:{value:'ALL'}});app.analyticsVals().analytics.onSearch({target:{value:'  quadra a '}});
 assert.equal(app.analyticsVals().analyticsRows.length,1);assert.equal(app.analyticsVals().analyticsRows[0].key,'TRIAL:t');
 app.analyticsVals().analytics.onSearch({target:{value:'zero'}});assert.equal(app.analyticsVals().analyticsRows[0].status,'Desativado');
 assert.equal(app.analyticsVals().analytics.users,3);
 app.analyticsVals().analytics.onSearch({target:{value:'not-found'}});assert.equal(app.analyticsVals().analytics.noResults,true);assert.equal(app.analyticsVals().analytics.empty,false);
});
test('failure clears visible totals and retry recovers without showing zeros as known results',async()=>{
 const {app,requests}=setup();app.state.analyticsData=data();app.analyticsLoad();requests[0].reject(Error('offline'));await flush();
 let v=app.analyticsVals();assert.equal(v.analytics.ready,false);assert.equal(v.analyticsRows.length,0);assert.match(v.analytics.status,/Não foi possível/);assert.equal(v.analytics.reloadLabel,'Tentar novamente');
 v.analytics.onReload();requests[1].resolve(ok(data()));await flush();assert.equal(app.analyticsVals().analytics.ready,true);assert.equal(app.state.analyticsError,'');
});
test('empty catalog is distinct from search without matches',async()=>{
 const {app,requests}=setup();app.analyticsLoad();requests[0].resolve(ok({...data(),coupons:[],summary:{...metrics,users:0,payingUsers:0,conversionPercent:null,revenueCents:0,incompleteUsers:0}}));await flush();
 const v=app.analyticsVals();assert.equal(v.analytics.empty,true);assert.equal(v.analytics.noResults,false);assert.equal(v.analytics.rate,'—');
});
test('logout clears private report and filters and ignores response already parsing',async()=>{
 const {app,requests,session}=setup();app.state.analyticsSearch='PRIVATE';app.state.analyticsType='DISCOUNT';app.analyticsLoad();
 let finish;requests[0].resolve({ok:true,json:()=>new Promise(resolve=>finish=resolve)});await flush();session(null);finish(data());await flush();
 assert.equal(app.state.analyticsData,null);assert.equal(app.state.analyticsBusy,false);assert.equal(app.state.analyticsSearch,'');assert.equal(app.state.analyticsType,'ALL');assert.equal(app.analyticsVals().analyticsRows.length,0);
});
test('direct admin session replacement clears old data and starts a fresh page load',async()=>{
 const {app,requests,session}=setup();app.state.page='cupons';app.analyticsLoad();session({id:'new-admin'});
 const analytics=requests.filter(r=>r.path==='/admin/coupon-analytics');assert.equal(analytics.length,2);
 analytics[0].resolve(ok(data()));analytics[1].resolve(ok({...data(),coupons:[]}));await flush();
 assert.equal(app.state.analyticsBusy,false);assert.equal(app.state.analyticsData.coupons.length,0);
});
