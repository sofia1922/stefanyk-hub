
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir, unlink } from 'node:fs/promises';
import { initializeApp, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { opaque, digest } from './security/crypto.mjs';
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
const app=initializeApp({projectId:'demo-stefanykhub'},'lab11-integration');
const auth=getAuth(app),db=getFirestore(app),out=new URL('../../docs/lab11/results/',import.meta.url);
await mkdir(out,{recursive:true});
const email=`lab11-${Date.now()}@pnu.edu.ua`,password='LabStudent42!';
const keepEvidence = process.env.LAB11_KEEP_EVIDENCE === '1';
let uid,studentToken,adminToken;
const cases=[],trace=[];
async function api(path,body={},token=null,expected=200){
  const response=await fetch('http://127.0.0.1:8787'+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(body)});
  const result=await response.json();trace.push({path,status:response.status});
  assert.equal(response.status,expected,JSON.stringify(result));return result;
}
async function exchange(customToken){
 const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo-api-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:customToken,returnSecureToken:true})});assert.equal(response.status,200);return (await response.json()).idToken;
}
async function run(id,title,fn){const start=Date.now();try{const details=await fn();cases.push({id,title,status:'PASS',durationMs:Date.now()-start,details});console.log(`${id} PASS: ${title}`);}catch(error){cases.push({id,title,status:'FAIL',message:error.message});throw error;}}
try {
 await run('IT-001','Реєстрація студента: API → Authentication та Firestore',async()=>{
  const captcha=opaque();await db.doc(`security_tickets/${digest(captcha)}`).set({kind:'captcha',uid:'',expires:Date.now()+180000,answerHash:digest('234567'),attempts:0});
  await api('/api/register',{fullName:'Іван Петренко (ЛР11)',email,password,role:'STUDENT',course:'2 курс',specialty:'121 Інженерія програмного забезпечення',group:'ІПЗ-21',captchaId:captcha,captchaAnswer:'234567'});
  const user=await auth.getUserByEmail(email);uid=user.uid;
  const pending=await db.doc(`pending_profiles/${uid}`).get();assert.equal(pending.data().fullName,'Іван Петренко (ЛР11)');assert.equal(pending.data().group,'ІПЗ-21');assert.equal(user.emailVerified,false);
  return {uid,email,emailVerified:user.emailVerified,document:`pending_profiles/${uid}`,storedProfile:pending.data()};
 });
 await run('IT-002','Активація та вхід: перенесення профілю в users',async()=>{
  const mail=JSON.parse(await readFile(new URL(`./.security-local/verify-${digest(email)}.json`,import.meta.url),'utf8'));
  const token=mail.link.split('#verify:')[1];await api('/api/action',{kind:'verify',token});
  const session=await api('/api/login',{email,password});studentToken=await exchange(session.customToken);
  const profile=await db.doc(`users/${uid}`).get();assert.equal(profile.data().email,email);
  assert.equal((await db.doc(`pending_profiles/${uid}`).get()).exists,false);
  const response=await fetch(`http://127.0.0.1:8080/v1/projects/demo-stefanykhub/databases/(default)/documents/users/${uid}`,{headers:{Authorization:`Bearer ${studentToken}`}});assert.equal(response.status,200);
  return {uid,email,emailVerified:(await auth.getUser(uid)).emailVerified,document:`users/${uid}`,pendingProfileExists:false,profileReadStatus:response.status,storedProfile:profile.data()};
 });
 await run('IT-003','Блокування: API → стан акаунта → заборона доступу до БД',async()=>{
  adminToken=await exchange((await api('/api/login',{email:'admin.demo@pnu.edu.ua',password:'DemoPass123!'})).customToken);
  const before=(await db.doc(`security_accounts/${uid}`).get()).data();
  await api('/api/admin/block',{uid,blocked:true},adminToken);
  const after=(await db.doc(`security_accounts/${uid}`).get()).data();
  assert.equal(after.blocked,true);assert.ok(after.version>before.version);assert.equal((await auth.getUser(uid)).disabled,true);
  const response=await fetch(`http://127.0.0.1:8080/v1/projects/demo-stefanykhub/databases/(default)/documents/users/${uid}`,{headers:{Authorization:`Bearer ${studentToken}`}});assert.equal(response.status,403);
  await api('/api/admin/block',{uid,blocked:false},adminToken);assert.equal((await auth.getUser(uid)).disabled,false);
  return {uid,blocked:true,authDisabled:true,oldSessionReadStatus:response.status,versionBefore:before.version,versionAfter:after.version,unblockedAfterTest:true};
 });
} finally {
 await writeFile(new URL('lab11-integration.json',out),JSON.stringify({executedAt:new Date().toISOString(),environment:'Firebase Emulator Suite / demo-stefanykhub',cases,trace},null,2));
 if(uid&&!keepEvidence){for(const name of ['users','pending_profiles','security_accounts'])await db.recursiveDelete(db.doc(`${name}/${uid}`));await auth.deleteUser(uid);const tickets=await db.collection('security_tickets').where('uid','==',uid).get();for(const doc of tickets.docs)await doc.ref.delete();}
 await db.doc(`security_limits/${digest(`login:${email}`)}`).delete();
 await unlink(new URL(`./.security-local/verify-${digest(email)}.json`,import.meta.url)).catch(error=>{if(error.code!=='ENOENT')throw error;});
 await deleteApp(app);
}
