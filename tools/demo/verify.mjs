import assert from 'node:assert/strict';

const project = 'demo-stefanykhub';
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
const firestore = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
let checks = 0;
async function login(email) {
  const gateway = await fetch('http://127.0.0.1:8787/api/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'DemoPass123!' })
  });
  assert.equal(gateway.status, 200, await gateway.clone().text());
  const session = await gateway.json();
  const response = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo-api-key', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: session.customToken, returnSecureToken: true })
  });
  assert.equal(response.status, 200, `Login failed for ${email}: ${await response.clone().text()}`);
  const result = await response.json();
  const claims = JSON.parse(Buffer.from(result.idToken.split('.')[1], 'base64url').toString());
  assert.equal(claims.email_verified, true);
  checks++;
  return result.idToken;
}
function document(fields) {
  return { fields: Object.fromEntries(Object.entries(fields).map(([key, value]) => [key,
    typeof value === 'number' ? { integerValue: String(value) } : { stringValue: value }
  ])) };
}
async function request(token, method, path, fields, expected = 200) {
  const response = await fetch(`${firestore}/${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(fields ? { body: JSON.stringify(document(fields)) } : {})
  });
  const body = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${body}`);
  checks++;
  return body ? JSON.parse(body) : null;
}

const student = await login('student.demo@pnu.edu.ua');
const teacher = await login('teacher.demo@pnu.edu.ua');
const otherTeacher = await login('teacher2.demo@pnu.edu.ua');
const admin = await login('admin.demo@pnu.edu.ua');
const scratch = `materials/verify-${Date.now()}`;
try {
  await request(student, 'GET', 'users/demo-student');
  await request(teacher, 'GET', 'users/demo-teacher');
  await request(student, 'GET', 'materials/1001');
  await request(null, 'GET', 'materials/1001', null, 403);
  await request(student, 'PATCH', scratch, { teacherId: 'demo-student', title: 'Forbidden' }, 403);
  await request(teacher, 'PATCH', scratch, { teacherId: 'demo-teacher', title: 'Rule check' });
  await request(otherTeacher, 'PATCH', scratch, { teacherId: 'demo-other-teacher', title: 'Forbidden' }, 403);
  await request(student, 'PATCH', 'users/demo-student', {
    id: 'demo-student', email: 'student.demo@pnu.edu.ua', role: 'ADMIN'
  }, 403);
  await request(student, 'PATCH', `${scratch}/reactions/demo-student`, { reaction: 'QUESTION' });
  const ownVote = await request(teacher, 'GET', `${scratch}/reactions/demo-student`);
  assert.equal(ownVote.fields.reaction.stringValue, 'QUESTION');
  await request(student, 'PATCH', `${scratch}/reactions/demo-student-2`, { reaction: 'FIRE' }, 403);
  await request(student, 'PATCH', `${scratch}/reactions/demo-student`, { reaction: 'INVALID' }, 403);
  await request(student, 'PATCH', `${scratch}/reactions/demo-student`, { reaction: 'FIRE', count: 100 }, 403);
  await request(teacher, 'PATCH', `${scratch}/reactions/demo-teacher`, { reaction: 'FIRE' }, 403);
  await request(admin, 'PATCH', `${scratch}/reactions/demo-admin`, { reaction: 'FIRE' }, 403);
  await request(student, 'DELETE', `${scratch}/reactions/demo-student`);
  const votes = await request(teacher, 'GET', 'materials/1001/reactions');
  assert.equal(votes.documents.length, 1);
  const concerns = votes.documents.filter(doc => ['DIFFICULT', 'QUESTION'].includes(doc.fields.reaction.stringValue));
  assert.equal(concerns.length, 1);
  checks++;
  const bucket = 'demo-stefanykhub.firebasestorage.app';
  const storageRoot = `http://127.0.0.1:9199/v0/b/${bucket}/o`;
  const samplePath = encodeURIComponent('materials/demo-teacher/lesson-v2-1001.pdf');
  const pdf = await fetch(`${storageRoot}/${samplePath}?alt=media`, { headers: { Authorization: `Firebase ${student}` } });
  assert.equal(pdf.status, 200, await pdf.clone().text());
  assert.ok((await pdf.text()).startsWith('%PDF-'));
  checks++;
  const materials = await request(student, 'GET', 'materials');
  const lessons = materials.documents.filter(material => !material.name.endsWith(`/${scratch.split('/')[1]}`));
  assert.equal(lessons.length, 2);
  for (const material of lessons) {
    const url = material.fields.linkUrl.stringValue.replace('10.0.2.2', '127.0.0.1');
    const response = await fetch(url);
    assert.equal(response.status, 200);
    assert.match(response.headers.get('content-type'), /application\/pdf/);
    const contents = await response.text();
    assert.ok(contents.startsWith('%PDF-'));
    assert.ok(contents.length > 30000, 'Expected a PDF with embedded fonts and lesson content');
    checks++;
  }
  const uploadPath = encodeURIComponent('materials/demo-teacher/verify.pdf');
  async function upload(token, type, status) {
    const response = await fetch(`${storageRoot}?name=${uploadPath}`, {
      method: 'POST', headers: { Authorization: `Firebase ${token}`, 'Content-Type': type },
      body: Buffer.from('%PDF-1.4\nlocal rule check\n%%EOF')
    });
    assert.equal(response.status, status, await response.text());
    checks++;
  }
  await upload(student, 'application/pdf', 403);
  await upload(otherTeacher, 'application/pdf', 403);
  await upload(teacher, 'text/plain', 403);
  await upload(teacher, 'application/pdf', 200);
  const deleted = await fetch(`${storageRoot}/${uploadPath}`, {
    method: 'DELETE', headers: { Authorization: `Firebase ${teacher}` }
  });
  assert.ok([200, 204].includes(deleted.status), await deleted.text());
  checks++;
  console.log(`PASS: ${checks} emulator authentication, authorization, storage and feedback checks.`);
} finally {
  await request(teacher, 'DELETE', scratch);
}
