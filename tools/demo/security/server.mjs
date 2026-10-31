import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { initializeApp, applicationDefault, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import nodemailer from 'nodemailer';
import { digest, opaque, passwordError, base32, totp, matchTotp, encrypt, decrypt, captchaPng, newCaptcha } from './crypto.mjs';

const demo = process.env.SECURITY_MODE === 'demo';
const port = Number(process.env.SECURITY_PORT || 8787);
const projectId = demo ? 'demo-stefanykhub' : process.env.GCLOUD_PROJECT;
if (!projectId) throw Error('Set GCLOUD_PROJECT and SECURITY_MODE=production.');
if (demo) {
  process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
  process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
} else if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIRESTORE_EMULATOR_HOST) throw Error('Emulator variables are forbidden in production.');
const app = initializeApp({ projectId, ...(!demo ? {credential: applicationDefault()} : {}) }, 'security-gateway');
const auth = getAuth(app), db = getFirestore(app);
const apiKey = demo ? 'demo-api-key' : process.env.FIREBASE_WEB_API_KEY;
const publicUrl = demo ? `http://127.0.0.1:${port}` : process.env.SECURITY_PUBLIC_URL;
if (!apiKey || !publicUrl || (!demo && !publicUrl.startsWith('https://'))) throw Error('Configure API key and HTTPS SECURITY_PUBLIC_URL.');
const localDir = new URL('../.security-local/', import.meta.url);
let key;
if (demo) {
  await mkdir(localDir,{recursive:true});
  try { key=await readFile(new URL('encryption.key',localDir)); }
  catch(error) { if(error.code!=='ENOENT')throw error; key=randomBytes(32);await writeFile(new URL('encryption.key',localDir),key,{flag:'wx'}); }
} else key=Buffer.from(process.env.SECURITY_ENCRYPTION_KEY || '', 'base64');
if (key.length!==32)throw Error('SECURITY_ENCRYPTION_KEY must decode to 32 random bytes.');
const mailer = demo ? null : nodemailer.createTransport(process.env.SMTP_URL || '');
if (!demo && (!process.env.SMTP_URL || !process.env.MAIL_FROM))throw Error('Configure SMTP_URL and MAIL_FROM.');
const fail = (message,status=400) => Object.assign(new Error(message),{status});
const normalized = email => String(email||'').trim().toLowerCase();
function university(email) { return /^[^\s@]+@(pnu\.edu\.ua|stefanyk\.edu\.ua)$/.test(email) && email.length<=254; }
const accountRef = uid => db.doc(`security_accounts/${uid}`);
const limiterRef = email => db.doc(`security_limits/${digest(email)}`);
async function identity(action, body) {
  const root=demo?'http://127.0.0.1:9099/identitytoolkit.googleapis.com':'https://identitytoolkit.googleapis.com';
  const response=await fetch(`${root}/v1/accounts:${action}?key=${encodeURIComponent(apiKey)}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const result=await response.json();if(!response.ok)throw fail('Не вдалося підтвердити облікові дані.',401);return result;
}
async function state(uid) {
  const ref=accountRef(uid);
  return db.runTransaction(async tx=>{const snap=await tx.get(ref);if(snap.exists)return snap.data();const data={blocked:false,version:1,mfaEnabled:false,lastStep:-1};tx.create(ref,data);return data;});
}
async function log(email,event,success,uid=null,ip='') {
  await db.collection('security_audit').add({email,event,success,uid,ipHash:digest(ip),time:Date.now()});
}
async function rate(keyName,max,windowMs) {
  const ref=limiterRef(keyName),now=Date.now();
  await db.runTransaction(async tx=>{const snap=await tx.get(ref);const value=snap.data()||{};
    const count=value.until>now?(value.count||0)+1:1;
    if(value.until>now && count>max)throw fail('Забагато спроб. Спробуйте пізніше.',429);
    tx.set(ref,{count,until:value.until>now?value.until:now+windowMs});
  });
}
async function attempt(email,ip) {
  await rate(`ip:${ip}`,50,15*60_000);
  try { await rate(`login:${email}`,5,15*60_000); }
  catch(error) {await log(email,'LOGIN_LOCKED',false,null,ip);throw error;}
}
async function ticket(kind,uid,data={},ttl=5*60_000) {
  const token=opaque();await db.doc(`security_tickets/${digest(token)}`).set({kind,uid,expires:Date.now()+ttl,attempts:0,...data});return token;
}
async function consume(token,kind) {
  if(typeof token!=='string'||token.length>150)throw fail('Недійсний або прострочений код.');
  const ref=db.doc(`security_tickets/${digest(token)}`);
  const result=await db.runTransaction(async tx=>{const snap=await tx.get(ref);const v=snap.data();
    if(!v||v.kind!==kind||v.expires<Date.now())return null;tx.delete(ref);return v;});
  if(!result)throw fail('Недійсний або прострочений код.');return result;
}
async function sendAction(uid,kind) {
  const user=await auth.getUser(uid);
  const token=await ticket(kind,uid,{version:(await state(uid)).version},kind==='verify'?60*60_000:30*60_000);

  const link=`${publicUrl}/action#${kind}:${token}`;
  const subject=kind==='verify'?'Підтвердження пошти Stefanyk.Hub':'Відновлення пароля Stefanyk.Hub';
  if(demo) {
    await writeFile(new URL(`${kind}-${digest(user.email)}.json`,localDir),JSON.stringify({to:user.email,subject,link,expiresInMinutes:kind==='verify'?60:30},null,2));
  } else await mailer.sendMail({from:process.env.MAIL_FROM,to:user.email,subject,text:`${subject}\n${link}\nПосилання одноразове. Строк дії: ${kind==='verify'?60:30} хвилин. Якщо це були не ви, ігноруйте лист.`});
}
async function session(uid,expectedVersion,mfaPassed=false) {
  const user=await auth.getUser(uid),s=await state(uid);
  if(s.blocked||user.disabled)throw fail('Обліковий запис заблоковано.',403);
  if(s.version!==expectedVersion || (s.mfaEnabled && !mfaPassed))throw fail('Налаштування безпеки змінилися. Увійдіть знову.',401);
  if(!user.emailVerified)throw fail('Підтвердіть електронну пошту за посиланням у листі.',403);
  let profile=await db.doc(`users/${uid}`).get();
  if(!profile.exists) {
    const pending=await db.doc(`pending_profiles/${uid}`).get();
    if(!pending.exists)throw fail('Профіль не знайдено.',403);
    const p=pending.data();
    if(p.role==='TEACHER' && !(await db.doc(`allowed_teachers/${user.email}`).get()).exists)throw fail('Запрошення викладача відкликано.',403);
    await db.doc(`users/${uid}`).set(p);await pending.ref.delete();profile=await db.doc(`users/${uid}`).get();
  }
  await limiterRef(`login:${user.email}`).delete();
  const customToken=await auth.createCustomToken(uid,{secure_session:true,security_version:s.version});
  return {customToken,profile:profile.data()};
}
async function beginLogin(user,ip) {
  const s=await state(user.uid);
  if(user.disabled||s.blocked)throw fail('Обліковий запис заблоковано.',403);
  if(!user.emailVerified)throw fail('Підтвердіть електронну пошту. За потреби надішліть лист повторно.',403);
  if(s.mfaEnabled)return {mfaRequired:true,challenge:await ticket('login',user.uid,{version:s.version})};
  const result=await session(user.uid,s.version);await log(user.email,'LOGIN',true,user.uid,ip);return result;
}
async function authorized(req,admin=false) {
  const raw=req.headers.authorization?.match(/^Bearer (.+)$/)?.[1];if(!raw)throw fail('Увійдіть у систему.',401);
  let claims;try {claims=await auth.verifyIdToken(raw,!demo);}catch {throw fail('Сесію завершено. Увійдіть знову.',401);}
  const s=await state(claims.uid),user=await auth.getUser(claims.uid);
  if(!claims.secure_session||claims.security_version!==s.version||s.blocked||user.disabled||!user.emailVerified)throw fail('Сесію завершено. Увійдіть знову.',401);
  if(admin && (await db.doc(`users/${claims.uid}`).get()).data()?.role!=='ADMIN')throw fail('Недостатньо прав.',403);
  return {user,s,claims};
}
async function verifyCode(uid,code,expectedVersion) {
  const ref=accountRef(uid);
  return db.runTransaction(async tx=>{const snap=await tx.get(ref);const s=snap.data();
    if(!s||s.blocked||!s.mfaEnabled||s.version!==expectedVersion)throw fail('Сесію завершено.',401);
    const secret=decrypt(s.secret,key,uid);
    const step=matchTotp(secret,code,s.lastStep??-1);
    if(step===null)throw fail('Неправильний або вже використаний код 2FA.',401);
    tx.update(ref,{lastStep:step});return s;});
}
const actionHtml=`<!doctype html><html lang="uk"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Stefanyk.Hub</title><style>body{font:18px system-ui;background:#eef2fa;padding:24px}main{max-width:460px;margin:8vh auto;background:white;padding:28px;border-radius:18px}input,button{box-sizing:border-box;width:100%;font:inherit;padding:12px;margin:8px 0}button{background:#355cdb;color:white;border:0;border-radius:8px}p{line-height:1.5}</style><main><h1>Stefanyk.Hub</h1><p id="intro"></p><form id="form"><input id="password" type="password" autocomplete="new-password" placeholder="Новий пароль" minlength="8" maxlength="128"><button>Підтвердити</button></form><p id="message" role="status"></p></main><script>const [kind,token]=location.hash.slice(1).split(':');history.replaceState(null,'','/action');document.getElementById('intro').textContent=kind==='verify'?'Підтвердіть свою електронну пошту.':'Новий пароль: 8–128 символів, великі й малі літери, цифра та спеціальний символ.';document.getElementById('password').hidden=kind==='verify';document.getElementById('form').onsubmit=async e=>{e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try{const response=await fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({kind,token,password:document.getElementById('password').value})});const body=await response.json();document.getElementById('message').textContent=body.message||body.error;if(response.ok)e.target.hidden=true;}catch{document.getElementById('message').textContent='Не вдалося з’єднатися із сервером.'}finally{button.disabled=false}};</script></html>`;

async function route(path,b,req) {
  const ip=req.socket.remoteAddress||'';
  if(path==='/api/captcha') {
    await rate(`captcha:${ip}`,30,60_000);
    const answer=newCaptcha();const id=await ticket('captcha','',{answerHash:digest(answer)},3*60_000);
    return {id,image:captchaPng(answer).toString('base64'),expiresInSeconds:180};
  }
  if(path==='/api/register') {
    await rate(`register:${ip}`,10,60*60_000);
    const challenge=await consume(b.captchaId,'captcha');
    if(challenge.answerHash!==digest(String(b.captchaAnswer||'').trim()))throw fail('Неправильна CAPTCHA. Оновіть зображення.');
    const email=normalized(b.email),error=passwordError(b.password);
    if(error)throw fail(error);
    if(!university(email)||typeof b.fullName!=='string'||!b.fullName.trim()||b.fullName.length>120||!['STUDENT','TEACHER'].includes(b.role))throw fail('Перевірте ім’я, пошту та роль.');
    const required=b.role==='STUDENT'?['course','specialty','group']:['department'];
    for(const field of required)if(typeof b[field]!=='string'||!b[field].trim()||b[field].length>200)throw fail('Заповніть навчальні дані.');
    if(b.role==='TEACHER' && !(await db.doc(`allowed_teachers/${email}`).get()).exists)throw fail('Адресу викладача має додати адміністратор.');
    let user;
    try {user=await auth.createUser({email,password:b.password,displayName:b.fullName.trim(),emailVerified:false});}
    catch(error) {if(error.code==='auth/email-already-exists')throw fail('Не вдалося створити акаунт. Спробуйте ввійти або відновити пароль.');throw error;}
    const profile={id:user.uid,email,fullName:b.fullName.trim(),role:b.role,course:b.course||'',specialty:b.specialty||'',group:b.group||'',department:b.department||''};
    await db.doc(`pending_profiles/${user.uid}`).set(profile);await state(user.uid);
    await sendAction(user.uid,'verify');await log(email,'REGISTER',true,user.uid,ip);
    return {message:'Акаунт створено. Підтвердіть пошту за посиланням у листі.'};
  }
  if(path==='/api/login') {
    const email=normalized(b.email);if(!university(email)||typeof b.password!=='string'||b.password.length>128)throw fail('Перевірте пошту та пароль.');
    await attempt(email,ip);
    try {const result=await identity('signInWithPassword',{email,password:b.password,returnSecureToken:true});if(passwordError(b.password))throw fail('Пароль не відповідає вимогам. Скористайтеся відновленням пароля.',403);return await beginLogin(await auth.getUser(result.localId),ip);}
    catch(error) {await log(email,'LOGIN',false,null,ip);throw error;}
  }
  if(path==='/api/google') {
    await rate(`google:${ip}`,30,15*60_000);
    const result=await identity('signInWithIdp',{postBody:`id_token=${encodeURIComponent(b.idToken||'')}&providerId=google.com`,requestUri:publicUrl,returnSecureToken:true});
    const user=await auth.getUser(result.localId);
    if(!university(user.email)||!user.emailVerified)throw fail('Потрібна підтверджена університетська пошта.',403);
    await attempt(user.email,ip);
    const profile=await db.doc(`users/${user.uid}`).get(),pending=await db.doc(`pending_profiles/${user.uid}`).get();
    if(!profile.exists&&!pending.exists){const teacher=(await db.doc(`allowed_teachers/${user.email}`).get()).exists;await db.doc(`users/${user.uid}`).set({id:user.uid,email:user.email,fullName:user.displayName||user.email,role:teacher?'TEACHER':'STUDENT',course:'',specialty:'',group:'',department:''});}
    return beginLogin(user,ip);
  }
  if(path==='/api/mfa/verify') {
    await rate(`mfa-ip:${ip}`,30,15*60_000);
    const ref=db.doc(`security_tickets/${digest(b.challenge||'')}`);
    const challenge=await db.runTransaction(async tx=>{const snap=await tx.get(ref),v=snap.data();if(!v||v.kind!=='login'||v.expires<Date.now()||v.attempts>=5)throw fail('Спроби вичерпано. Увійдіть знову.',401);tx.update(ref,{attempts:v.attempts+1});return v;});
    const s=await state(challenge.uid);if(!s.mfaEnabled||s.version!==challenge.version)throw fail('Сесію завершено.',401);
    try {await verifyCode(challenge.uid,b.code,challenge.version);await consume(b.challenge,'login');const result=await session(challenge.uid,challenge.version,true);await log((await auth.getUser(challenge.uid)).email,'LOGIN_2FA',true,challenge.uid,ip);return result;}
    catch(error){await log((await auth.getUser(challenge.uid)).email,'LOGIN_2FA',false,challenge.uid,ip);throw error;}
  }
  if(path==='/api/resend'||path==='/api/reset') {
    const email=normalized(b.email);if(!university(email))throw fail('Вкажіть університетську пошту.');
    await rate(`mail:${email}`,3,15*60_000);await rate(`mail-ip:${ip}`,15,15*60_000);
    let user;try{user=await auth.getUserByEmail(email);}catch(error){if(error.code!=='auth/user-not-found')throw error;}
    if(user && (path==='/api/reset'||!user.emailVerified))await sendAction(user.uid,path==='/api/reset'?'reset':'verify');
    return {message:'Якщо відповідний акаунт існує, лист надіслано.'};
  }
  if(path==='/api/action') {
    await rate(`action:${ip}`,30,15*60_000);
    if(!['verify','reset'].includes(b.kind))throw fail('Невідомий тип посилання.');
    if(b.kind==='reset'&&passwordError(b.password))throw fail(passwordError(b.password));
    const v=await consume(b.token,b.kind),user=await auth.getUser(v.uid);
    if(user.disabled||(await state(v.uid)).blocked)throw fail('Акаунт заблоковано.',403);
    if(v.version!==(await state(v.uid)).version)throw fail('Посилання більше не дійсне. Запросіть новий лист.');
    if(b.kind==='verify')await auth.updateUser(v.uid,{emailVerified:true});
    else {await auth.updateUser(v.uid,{password:b.password});await db.runTransaction(async tx=>{const ref=accountRef(v.uid),snap=await tx.get(ref);tx.update(ref,{version:snap.data().version+1});});await auth.revokeRefreshTokens(v.uid);await limiterRef(`login:${user.email}`).delete();}
    await log(user.email,b.kind==='verify'?'EMAIL_VERIFIED':'PASSWORD_RESET',true,v.uid,ip);
    return {message:b.kind==='verify'?'Пошту підтверджено. Поверніться до застосунку та ввійдіть.':'Пароль змінено. Увійдіть із новим паролем.'};
  }
  if(path==='/api/security/status') {
    const {user,s}=await authorized(req);return {emailVerified:user.emailVerified,mfaEnabled:s.mfaEnabled};
  }
  if(path==='/api/mfa/setup') {
    const {user,s,claims}=await authorized(req);if(s.mfaEnabled)throw fail('2FA вже ввімкнено.');
    if(Date.now()/1000-claims.auth_time>600)throw fail('Для налаштування 2FA ввійдіть знову.',401);
    await rate(`mfa-setup:${user.uid}`,5,15*60_000);
    const secret=base32(randomBytes(20));
    const setup=await ticket('setup',user.uid,{secret:encrypt(secret,key,user.uid),version:s.version},10*60_000);
    return {setup,secret,uri:`otpauth://totp/Stefanyk.Hub:${encodeURIComponent(user.email)}?secret=${secret}&issuer=Stefanyk.Hub&algorithm=SHA1&digits=6&period=30`};
  }
  if(path==='/api/mfa/enable') {
    const {user,s}=await authorized(req);await rate(`mfa-change:${user.uid}`,5,15*60_000);
    await db.runTransaction(async tx=>{
      const ticketRef=db.doc(`security_tickets/${digest(b.setup||'')}`),ref=accountRef(user.uid);
      const [ticketSnap,accountSnap]=await Promise.all([tx.get(ticketRef),tx.get(ref)]),t=ticketSnap.data(),a=accountSnap.data();
      if(!t||t.kind!=='setup'||t.expires<Date.now()||t.uid!==user.uid||t.version!==a.version||a.version!==s.version||a.mfaEnabled||a.blocked)throw fail('Недійсне налаштування 2FA.');
      const step=matchTotp(decrypt(t.secret,key,user.uid),b.code,a.lastStep??-1);
      if(step===null)throw fail('Неправильний код автентифікатора.',401);
      tx.update(ref,{secret:t.secret,mfaEnabled:true,version:a.version+1,lastStep:step});tx.delete(ticketRef);
    });
    await auth.revokeRefreshTokens(user.uid);await log(user.email,'MFA_ENABLED',true,user.uid,ip);
    return {message:'2FA увімкнено. Увійдіть знову з кодом автентифікатора.'};
  }
  if(path==='/api/mfa/disable') {
    const {user,s}=await authorized(req);await rate(`mfa-change:${user.uid}`,5,15*60_000);
    if(!s.mfaEnabled)throw fail('2FA вже вимкнено.');
    await db.runTransaction(async tx=>{const ref=accountRef(user.uid),snap=await tx.get(ref),a=snap.data();
      if(a.blocked||a.version!==s.version||!a.mfaEnabled)throw fail('Сесію завершено.',401);
      const step=matchTotp(decrypt(a.secret,key,user.uid),b.code,a.lastStep??-1);
      if(step===null)throw fail('Неправильний або вже використаний код 2FA.',401);
      tx.set(ref,{blocked:false,version:a.version+1,mfaEnabled:false,lastStep:-1});
    });
    await auth.revokeRefreshTokens(user.uid);await log(user.email,'MFA_DISABLED',true,user.uid,ip);
    return {message:'2FA вимкнено. Увійдіть знову.'};
  }
  if(path==='/api/admin/accounts') {
    await authorized(req,true);const records=await auth.listUsers(100);
    const accounts=await Promise.all(records.users.map(async user=>{const s=await state(user.uid),limit=(await limiterRef(`login:${user.email}`).get()).data();return {uid:user.uid,email:user.email,blocked:s.blocked||user.disabled,mfaEnabled:s.mfaEnabled,failedAttempts:limit?.until>Date.now()?limit.count:0,lockedUntil:limit?.until>Date.now()&&limit.count>=5?limit.until:0};}));return {accounts};
  }
  if(path==='/api/admin/audit') {
    await authorized(req,true);const records=await db.collection('security_audit').orderBy('time','desc').limit(100).get();return {events:records.docs.map(d=>({id:d.id,...d.data()}))};
  }
  if(path==='/api/admin/block') {
    const actor=await authorized(req,true);if(typeof b.uid!=='string'||typeof b.blocked!=='boolean')throw fail('Некоректний запит.');
    if(b.uid===actor.user.uid)throw fail('Не можна заблокувати власний акаунт.');
    const user=await auth.getUser(b.uid);await state(user.uid);
    await db.runTransaction(async tx=>{const ref=accountRef(user.uid),snap=await tx.get(ref);tx.update(ref,{blocked:b.blocked,version:snap.data().version+1});});
    await auth.updateUser(user.uid,{disabled:b.blocked});await auth.revokeRefreshTokens(user.uid);
    if(!b.blocked)await limiterRef(`login:${user.email}`).delete();
    await log(user.email,b.blocked?'ADMIN_BLOCK':'ADMIN_UNBLOCK',true,actor.user.uid,ip);
    return {message:b.blocked?'Акаунт заблоковано.':'Акаунт розблоковано, лічильник спроб скинуто.'};
  }
  throw fail('Маршрут не знайдено.',404);
}
export const server=createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Frame-Options','DENY');
  const path=new URL(req.url,'http://localhost').pathname;
  if(req.method==='GET'&&path==='/action'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(actionHtml);return;}
  if(req.method==='GET'&&path==='/health'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ok:true,mode:demo?'demo':'production'}));return;}
  res.setHeader('Content-Type','application/json; charset=utf-8');
  try {
    if(req.method!=='POST')throw fail('Метод не підтримується.',405);
    let body='';for await(const chunk of req){body+=chunk;if(Buffer.byteLength(body)>16_384)throw fail('Запит завеликий.',413);}
    let parsed;try{parsed=JSON.parse(body||'{}');}catch{throw fail('Некоректний JSON.');}
    if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw fail('Некоректний запит.');
    const result=await route(path,parsed,req);res.end(JSON.stringify(result));
  } catch(error){res.statusCode=error.status||500;res.end(JSON.stringify({error:error.status?error.message:'Помилка сервера. Спробуйте пізніше.'}));if(!error.status)console.error('Security operation failed:',error.code||error.name);}
});
server.requestTimeout=20_000;server.headersTimeout=10_000;
setInterval(async()=>{
  try {for(const [collection,field] of [['security_tickets','expires'],['security_limits','until']]) {
    const stale=await db.collection(collection).where(field,'<',Date.now()).limit(200).get();
    if(!stale.empty){const batch=db.batch();stale.docs.forEach(doc=>batch.delete(doc.ref));await batch.commit();}
  }}catch(error){console.error('Security cleanup failed:',error.code||error.name);}
},5*60_000).unref();
server.listen(port,demo?'127.0.0.1':'0.0.0.0',()=>console.log(`Security API ready (${demo?'demo':'production'}) on port ${port}.`));
export async function stopSecurityServer() {
  await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
  await deleteApp(app);
}
