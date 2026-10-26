await import('./seed.mjs');
process.env.SECURITY_MODE = 'demo';
const { stopSecurityServer } = await import('./security/server.mjs');
try {
  await import('./verify.mjs');
  await import('./security/integration.mjs');
} finally { await stopSecurityServer(); }
