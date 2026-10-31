
const projectId = 'demo-stefanykhub';
const bucketName = `${projectId}.firebasestorage.app`;
process.env.GCLOUD_PROJECT = projectId;
process.env.GOOGLE_CLOUD_PROJECT = projectId;
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
process.env.STORAGE_EMULATOR_HOST = 'http://127.0.0.1:9199';

const { initializeApp, deleteApp } = await import('firebase-admin/app');
const { getAuth } = await import('firebase-admin/auth');
const { getFirestore } = await import('firebase-admin/firestore');
const { getStorage } = await import('firebase-admin/storage');


for (const port of [9099, 8080, 9199]) {
  try {
    await fetch(`http://127.0.0.1:${port}/`, { signal: AbortSignal.timeout(5000) });
  } catch {
    throw new Error(`Local emulator on port ${port} is unavailable. Start auth,firestore,storage emulators first.`);
  }
}

const app = initializeApp({ projectId, storageBucket: bucketName }, 'seed-local-demo');
const auth = getAuth(app);
const db = getFirestore(app);
const bucket = getStorage(app).bucket();
const password = 'DemoPass123!';

const users = [
  { id: 'demo-student', email: 'student.demo@pnu.edu.ua', fullName: 'Олена Тестова', role: 'STUDENT' },
  { id: 'demo-teacher', email: 'teacher.demo@pnu.edu.ua', fullName: 'Олександр Тестовий', role: 'TEACHER' },
  { id: 'demo-other-teacher', email: 'teacher2.demo@pnu.edu.ua', fullName: 'Інша Викладачка', role: 'TEACHER' },
  { id: 'demo-admin', email: 'admin.demo@pnu.edu.ua', fullName: 'Адміністратор Тестовий', role: 'ADMIN' }
];

const { readFile } = await import('node:fs/promises');

try {

  for (const n of [2, 3, 4, 5]) {
    const uid = `demo-student-${n}`;
    await db.recursiveDelete(db.doc(`users/${uid}`));
    await db.recursiveDelete(db.doc(`pending_profiles/${uid}`));
    try { await auth.deleteUser(uid); }
    catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
  }
  for (const material of (await db.collection('materials').get()).docs) {
    if (/^100[1-5]$/.test(material.id)) await db.recursiveDelete(material.ref);
  }
  for (const id of [1003, 1004, 1005]) {
    const teacher = id === 1005 ? 'demo-other-teacher' : 'demo-teacher';
    await bucket.file(`materials/${teacher}/demo-${id}.pdf`).delete({ ignoreNotFound: true });
    await db.doc(`users/demo-student/completed/${id}`).delete();
    await db.doc(`users/demo-student/favorites/${id}`).delete();
  }
  for (const user of users) {
    const properties = { email: user.email, displayName: user.fullName, password, emailVerified: true, disabled: false };
    try { await auth.updateUser(user.id, properties); }
    catch (error) {
      if (error.code !== 'auth/user-not-found') throw error;
      await auth.createUser({ uid: user.id, ...properties });
    }
    await db.doc(`users/${user.id}`).set({
      ...user, course: user.role === 'STUDENT' ? '2 курс' : '',
      specialty: user.role === 'STUDENT' ? '121 Інженерія програмного забезпечення' : '',
      group: user.role === 'STUDENT' ? 'ІПЗ-21' : '',
      department: user.role === 'STUDENT' ? '' : 'Кафедра програмної інженерії'
    });
    const security = db.doc(`security_accounts/${user.id}`);
    if (!(await security.get()).exists) await security.create({ blocked: false, version: 1, mfaEnabled: false, lastStep: -1 });
  }
  for (const user of users.filter(user => user.role === 'TEACHER')) {
    await db.doc(`allowed_teachers/${user.email}`).set({ email: user.email });
  }

  await db.doc('allowed_teachers/invited.demo@pnu.edu.ua').set({ email: 'invited.demo@pnu.edu.ua' });

  const titles = ['Бази даних: таблиці та SQL', 'Англійська для ІТ: спілкування в команді'];
  for (let i = 0; i < titles.length; i++) {
    const id = 1001 + i;
    const teacherId = 'demo-teacher';
    const path = `materials/${teacherId}/lesson-v2-${id}.pdf`;
    const token = `local-lesson-v2-${id}`;
    const pdf = await readFile(new URL(`./materials/${i === 0 ? 'databases' : 'english'}.pdf`, import.meta.url));
    await bucket.file(path).save(pdf, {
      metadata: { contentType: 'application/pdf', metadata: { firebaseStorageDownloadTokens: token } }
    });
    await db.doc(`materials/${id}`).set({
      id, title: titles[i], type: i % 2 === 0 ? 'Лабораторна' : 'Лекція',
      subject: i === 0 ? 'Бази даних' : 'Англійська мова',
      teacher: i === 4 ? 'Інша Викладачка' : 'Олександр Тестовий', teacherId,
      date: '2026-10-26', fileType: 'PDF', fileSize: `${Math.ceil(pdf.length / 1024)} KB`, semester: 3,
      course: '2 курс', tags: '#навчання #практика', description: i === 0 ? 'Таблиці, ключі, запити SQL, JOIN і транзакції. Приклади та завдання для практики.' : 'Англійська для роботи в ІТ: словник, щоденні звіти, діалог і практичне завдання.',
      linkUrl: `http://10.0.2.2:9199/v0/b/${bucketName}/o/${encodeURIComponent(path)}?alt=media&token=${token}`,
      downloadCount: 0
    });
  }
  const studentIds = users.filter(user => user.role === 'STUDENT').map(user => user.id);
  const reactions = {
    1001: ['DIFFICULT'],
    1002: ['CLEAR']
  };
  for (const [id, votes] of Object.entries(reactions)) {
    for (let i = 0; i < studentIds.length; i++) {
      await db.doc(`materials/${id}/reactions/${studentIds[i]}`).set({ reaction: votes[i] });
    }
  }
  await db.doc('users/demo-student/completed/1001').set({ materialId: 1001 });
  await db.doc('users/demo-student/completed/1002').set({ materialId: 1002 });
  await db.doc('users/demo-student/favorites/1001').set({ materialId: 1001 });
  console.log('Local demo is ready. Password for all accounts: DemoPass123!');
  console.table(users.map(({ email, role }) => ({ email, role })));
} finally {
  await deleteApp(app);
}
