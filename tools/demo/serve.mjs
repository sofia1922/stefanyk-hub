await import('./seed.mjs');
process.env.SECURITY_MODE = 'demo';
await import('./security/server.mjs');
console.log('\nDemo Firebase is running. Open http://127.0.0.1:4000 for the emulator UI.');
console.log('In the Android emulator, log out and enable Local testing on the sign-in screen.');
console.log('Keep this terminal open. Stop with Ctrl+C. Data is recreated on the next start.');
setInterval(() => {}, 60_000);
