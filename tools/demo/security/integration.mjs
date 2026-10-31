import assert from 'node:assert/strict';
import { readFile, unlink } from 'node:fs/promises';
import { initializeApp, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { opaque, digest, totp } from './crypto.mjs';

process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
const app=initializeApp({projectId:'demo-stefanykhub'},'security-integration');
const auth=getAuth(app),db=getFirestore(app),root='http://127.0.0.1:8787';
let checks=0,testUid;
const email=`security-${Date.now()}@pnu.edu.ua`,password='StrongTest42!';


const bucketNames=['ip:127.0.0.1','register:127.0.0.1','captcha:127.0.0.1','mail-ip:127.0.0.1','action:127.0.0.1','mfa-ip:127.0.0.1'];
const originalBuckets=await Promise.all(bucketNames.map(async name=>{const ref=db.doc(`security_limits/${digest(name)}`);return {ref,data:(await ref.get()).data()};}));
for(const entry of originalBuckets)await entry.ref.delete();
async function api(path,body={},token=null,expected=200) {
  const response=await fetch(root+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(body)});
  const result=await response.json();assert.equal(response.status,expected,`${path}: ${JSON.stringify(result)}`);checks++;return result;
}
async function exchange(customToken) {
  const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo-api-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:customToken,returnSecureToken:true})});
  assert.equal(response.status,200);return (await response.json()).idToken;
}
async function login(mail,pw=password) {return api('/api/login',{email:mail,password:pw});}
async function captcha(expires=Date.now()+180000) {
  const id=opaque();await db.doc(`security_tickets/${digest(id)}`).set({kind:'captcha',uid:'',expires,answerHash:digest('234567'),attempts:0});return id;
}
async function outbox(kind) {return JSON.parse(await readFile(new URL(`../.security-local/${kind}-${digest(email)}.json`,import.meta.url),'utf8'));}
function tokenFromMail(message) {return message.link.split('#')[1].split(':')[1];}
async function database(token,status) {
  const response=await fetch('http://127.0.0.1:8080/v1/projects/demo-stefanykhub/databases/(default)/documents/materials/1001',{headers:{Authorization:`Bearer ${token}`}});
  assert.equal(response.status,status,await response.text());checks++;
}
try {
  const publicCaptcha=await api('/api/captcha');
  assert.equal(Buffer.from(publicCaptcha.image,'base64').subarray(1,4).toString(),'PNG');
  assert.equal(publicCaptcha.answer,undefined);
  await api('/api/admin/accounts',{},null,401);
  const profile={email,password,fullName:'Security Test',role:'STUDENT',course:'2',specialty:'Software Engineering',group:'TEST',captchaAnswer:'234567'};
  await api('/api/register',{...profile,captchaId:await captcha(),captchaAnswer:'999999'},null,400);
  await api('/api/register',{...profile,captchaId:await captcha(Date.now()-1)},null,400);
  await api('/api/register',{...profile,captchaId:await captcha(),password:'weak'},null,400);
  const challenge=await captcha();await api('/api/register',{...profile,captchaId:challenge});
  testUid=(await auth.getUserByEmail(email)).uid;
  await api('/api/register',{...profile,captchaId:challenge},null,400);
  await api('/api/login',{email,password},null,403);
  const verify=tokenFromMail(await outbox('verify'));
  await api('/api/action',{kind:'verify',token:verify});
  await api('/api/action',{kind:'verify',token:verify},null,400);
  let result=await login(email);let token=await exchange(result.customToken);
  await database(token,200);
  await api('/api/admin/audit',{},token,403);
  assert.equal((await api('/api/security/status',{},token)).emailVerified,true);

  const raw=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-api-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
  await database((await raw.json()).idToken,403);
  const setup=await api('/api/mfa/setup',{},token);
  const step=Math.floor(Date.now()/30000);
  const validCodes=[step-1,step,step+1].map(s=>totp(setup.secret,s));
  const wrongCode=['000000','111111','222222','333333'].find(code=>!validCodes.includes(code));
  await api('/api/mfa/enable',{setup:setup.setup,code:wrongCode},token,401);
  await api('/api/mfa/enable',{setup:setup.setup,code:totp(setup.secret,step-1)},token);
  await api('/api/security/status',{},token,401);
  await database(token,403);
  result=await login(email);assert.equal(result.mfaRequired,true);assert.equal(result.customToken,undefined);
  await api('/api/mfa/verify',{challenge:result.challenge,code:wrongCode},null,401);
  const completed=await api('/api/mfa/verify',{challenge:result.challenge,code:totp(setup.secret,step)});
  token=await exchange(completed.customToken);
  await api('/api/mfa/verify',{challenge:result.challenge,code:totp(setup.secret,step)},null,401);
  assert.equal((await api('/api/security/status',{},token)).mfaEnabled,true);
  const encrypted=(await db.doc(`security_accounts/${testUid}`).get()).data().secret;
  assert.ok(!encrypted.includes(setup.secret));
  await api('/api/mfa/disable',{code:totp(setup.secret,step)},token,401);
  await api('/api/mfa/disable',{code:totp(setup.secret,step+1)},token);
  result=await login(email);token=await exchange(result.customToken);
  assert.equal((await api('/api/security/status',{},token)).mfaEnabled,false);

  for(let i=0;i<5;i++)await api('/api/login',{email,password:'Incorrect42!'},null,401);
  await api('/api/login',{email,password},null,429);
  const admin=await exchange((await login('admin.demo@pnu.edu.ua','DemoPass123!')).customToken);
  const accounts=await api('/api/admin/accounts',{},admin);
  assert.equal(accounts.accounts.find(x=>x.uid===testUid).failedAttempts,5);
  await api('/api/admin/block',{uid:testUid,blocked:false},admin);
  result=await login(email);token=await exchange(result.customToken);
  await api('/api/admin/block',{uid:testUid,blocked:true},admin);
  await api('/api/security/status',{},token,401);await database(token,403);
  await api('/api/admin/block',{uid:testUid,blocked:false},admin);
  result=await login(email);token=await exchange(result.customToken);
  await api('/api/reset',{email});
  const reset=tokenFromMail(await outbox('reset'));
  await api('/api/action',{kind:'reset',token:reset,password:'weak'},null,400);
  await api('/api/action',{kind:'reset',token:reset,password:'NewSecure42!'});
  await api('/api/action',{kind:'reset',token:reset,password:'NewSecure42!'},null,400);
  await api('/api/security/status',{},token,401);
  await api('/api/login',{email,password},null,401);
  await login(email,'NewSecure42!');
  const expired=opaque();await db.doc(`security_tickets/${digest(expired)}`).set({kind:'reset',uid:testUid,expires:Date.now()-1});
  await api('/api/action',{kind:'reset',token:expired,password:'OtherSecure42!'},null,400);
  const audit=await api('/api/admin/audit',{},admin);
  assert.ok(audit.events.some(e=>e.email===email && e.event==='LOGIN_LOCKED'));
  assert.ok(audit.events.some(e=>e.email===email && e.event==='MFA_ENABLED'));
  console.log(`PASS: ${checks} security integration checks (CAPTCHA, policy, activation, replay, 2FA, rules, lockout, admin, reset).`);
} finally {
  if(testUid) {
    await db.recursiveDelete(db.doc(`users/${testUid}`));await db.doc(`pending_profiles/${testUid}`).delete();
    await db.doc(`security_accounts/${testUid}`).delete();await auth.deleteUser(testUid);
    for(const collection of ['security_tickets','security_audit']) {
      const records=await db.collection(collection).where(collection==='security_audit'?'email':'uid','==',collection==='security_audit'?email:testUid).get();
      for(const record of records.docs)await record.ref.delete();
    }
    for(const prefix of ['mfa-setup','mfa-change'])await db.doc(`security_limits/${digest(`${prefix}:${testUid}`)}`).delete();
  }
  await db.doc(`security_limits/${digest(`login:${email}`)}`).delete();
  await db.doc(`security_limits/${digest(`mail:${email}`)}`).delete();
  for(const entry of originalBuckets) {if(entry.data)await entry.ref.set(entry.data);else await entry.ref.delete();}
  for(const kind of ['verify','reset'])await unlink(new URL(`../.security-local/${kind}-${digest(email)}.json`,import.meta.url)).catch(error=>{if(error.code!=='ENOENT')throw error;});
  await deleteApp(app);
}
