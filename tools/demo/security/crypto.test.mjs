import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { base32, totp, matchTotp, passwordError, encrypt, decrypt, captchaPng } from './crypto.mjs';

test('RFC 6238 SHA1 reference vectors',()=>{
  const secret=base32(Buffer.from('12345678901234567890'));
  for(const [seconds,expected] of [[59,'94287082'],[1111111109,'07081804'],[1111111111,'14050471'],[1234567890,'89005924'],[2000000000,'69279037'],[20000000000,'65353130']]) {
    assert.equal(totp(secret,Math.floor(seconds/30),8),expected);
  }
});
test('TOTP rejects replay, invalid format and expired time windows',()=>{
  const secret=base32(randomBytes(20)),now=1234567890000,step=Math.floor(now/30000);
  const code=totp(secret,step);
  assert.equal(matchTotp(secret,code,-1,now),step);
  assert.equal(matchTotp(secret,code,step,now),null);
  assert.equal(matchTotp(secret,code,-1,now+120000),null);
  assert.equal(matchTotp(secret,'12345',-1,now),null);
});
test('password policy checks every required category and common passwords',()=>{
  for(const password of ['Ab1!abc','abcdefgh1!','ABCDEFGH1!','Abcdefgh!','Abcdefgh1','Abcdefg1 ','Password123!','A'.repeat(129)])assert.ok(passwordError(password));
  assert.equal(passwordError('Abcdef1!'),null);
  assert.equal(passwordError('СкладнийПароль42!'),null);
});
test('TOTP secrets are authenticated, encrypted and bound to the user',()=>{
  const key=randomBytes(32),value=encrypt('SECRET',key,'alice');
  assert.equal(decrypt(value,key,'alice'),'SECRET');
  assert.throws(()=>decrypt(value,key,'bob'));
  assert.throws(()=>decrypt(value,randomBytes(32),'alice'));
  assert.ok(!value.includes('SECRET'));
});
test('CAPTCHA is an actual PNG, without a plaintext answer',()=>{
  const image=captchaPng('234567');
  assert.equal(image.subarray(1,4).toString(),'PNG');
  assert.equal(image.readUInt32BE(16),250);
  assert.ok(!image.includes(Buffer.from('234567')));
});
