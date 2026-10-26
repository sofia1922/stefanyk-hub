// Administrative inventory: never import this script into the Android application.
import { initializeApp, applicationDefault, deleteApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';

const [projectId, bucketName, budgetText] = process.argv.slice(2);
if (!projectId || !bucketName || bucketName.includes('/') || !/^[a-z0-9.-]+$/.test(bucketName)) {
  throw new Error('Usage: node tools/demo/storage-report.mjs PROJECT_ID BUCKET_NAME [BUDGET_BYTES]');
}
const budgetBytes = budgetText === undefined ? null : Number(budgetText);
if (budgetBytes !== null && (!Number.isSafeInteger(budgetBytes) || budgetBytes <= 0)) throw new Error('BUDGET_BYTES must be a positive safe integer');
const demo = projectId === 'demo-stefanykhub';
if (demo) {
  process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
  process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
  process.env.STORAGE_EMULATOR_HOST = 'http://127.0.0.1:9199';
} else if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_STORAGE_EMULATOR_HOST || process.env.STORAGE_EMULATOR_HOST) {
  throw new Error('Remove emulator environment variables before creating a production report');
}
const app = initializeApp({ projectId, storageBucket: bucketName, ...(demo ? {} : { credential: applicationDefault() }) });
try {
  const bucket = getStorage(app).bucket();
  let bytes = 0;
  let files = 0;
  let pageToken;
  do {
    const [page, next] = await bucket.getFiles({ autoPaginate: false, maxResults: 500, ...(pageToken ? { pageToken } : {}) });
    for (const file of page) {
      const size = Number(file.metadata.size);
      if (!Number.isSafeInteger(size) || size < 0 || !Number.isSafeInteger(bytes + size)) throw new Error(`Invalid object size: ${file.name}`);
      bytes += size;
      files++;
    }
    pageToken = next?.pageToken;
  } while (pageToken);
  await getFirestore(app).collection('system_metrics').doc('storage').set({
    bytes, files, budgetBytes, bucket: bucketName, updatedAt: FieldValue.serverTimestamp()
  });
  console.log(`Storage report published: ${files} objects, ${bytes} bytes, project ${projectId}`);
} finally { await deleteApp(app); }
