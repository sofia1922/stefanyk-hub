import { createHmac, randomBytes, timingSafeEqual, createCipheriv, createDecipheriv, createHash, randomInt } from 'node:crypto';
import { deflateSync } from 'node:zlib';

export const digest = value => createHash('sha256').update(String(value)).digest('hex');
export const opaque = () => randomBytes(32).toString('base64url');
export function passwordError(password) {
  if (typeof password !== 'string' || password.length < 8 || password.length > 128) return 'Пароль має містити від 8 до 128 символів.';
  if (!/\p{Lu}/u.test(password) || !/\p{Ll}/u.test(password) || !/[0-9]/.test(password) || !/[^\p{L}\p{N}\s]/u.test(password)) return 'Додайте велику й малу літери, цифру та спеціальний символ.';
  if (['password1!', 'password123!', 'qwerty123!', 'admin123!', '12345678aa!'].includes(password.toLowerCase())) return 'Цей пароль надто поширений. Виберіть інший.';
  return null;
}
const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
export function base32(bytes) {
  let bits = 0, value = 0, result = '';
  for (const byte of bytes) { value = (value << 8) | byte; bits += 8; while (bits >= 5) { result += alphabet[(value >>> (bits - 5)) & 31]; bits -= 5; } }
  if (bits) result += alphabet[(value << (5 - bits)) & 31];
  return result;
}
export function unbase32(text) {
  let bits = 0, value = 0; const result = [];
  for (const char of text) { const n = alphabet.indexOf(char); if (n < 0) throw Error('Invalid secret'); value = (value << 5) | n; bits += 5; if (bits >= 8) { result.push((value >>> (bits - 8)) & 255); bits -= 8; } }
  return Buffer.from(result);
}
export function totp(secret, step = Math.floor(Date.now() / 30000), digits = 6) {
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(step));
  const mac = createHmac('sha1', unbase32(secret)).update(counter).digest();
  const offset = mac[mac.length - 1] & 15;
  return String((mac.readUInt32BE(offset) & 0x7fffffff) % (10 ** digits)).padStart(digits, '0');
}
export function matchTotp(secret, code, lastStep = -1, now = Date.now()) {
  if (!/^\d{6}$/.test(String(code))) return null;
  const current = Math.floor(now / 30000);
  for (const step of [current, current - 1, current + 1]) {
    if (step > lastStep && timingSafeEqual(Buffer.from(totp(secret, step)), Buffer.from(String(code)))) return step;
  }
  return null;
}
export function encrypt(secret, key, uid) {
  const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(uid));
  const data = Buffer.concat([cipher.update(secret, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), data].map(x => x.toString('base64url')).join('.');
}
export function decrypt(text, key, uid) {
  const [iv, tag, data] = text.split('.').map(x => Buffer.from(x, 'base64url'));
  const cipher = createDecipheriv('aes-256-gcm', key, iv); cipher.setAAD(Buffer.from(uid)); cipher.setAuthTag(tag);
  return Buffer.concat([cipher.update(data), cipher.final()]).toString('utf8');
}

// Server-generated PNG: the answer never travels to the client as text.
const glyphs = {
  '2':['11110','00001','00001','01110','10000','10000','11111'],
  '3':['11110','00001','00001','01110','00001','00001','11110'],
  '4':['10010','10010','10010','11111','00010','00010','00010'],
  '5':['11111','10000','10000','11110','00001','00001','11110'],
  '6':['01111','10000','10000','11110','10001','10001','01110'],
  '7':['11111','00001','00010','00100','01000','01000','01000'],
  '8':['01110','10001','10001','01110','10001','10001','01110'],
  '9':['01110','10001','10001','01111','00001','00001','11110']
};
function crc32(buffer) { let crc = -1; for (const byte of buffer) { crc ^= byte; for (let n = 0; n < 8; n++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); } return (crc ^ -1) >>> 0; }
function chunk(type, data) { const name = Buffer.from(type), length = Buffer.alloc(4), crc = Buffer.alloc(4); length.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([name, data]))); return Buffer.concat([length, name, data, crc]); }
export function captchaPng(answer) {
  const w = 250, h = 70, pixels = Buffer.alloc(w*h*3, 246);
  function dot(x,y,color) { x=Math.round(x);y=Math.round(y); if(x>=0&&x<w&&y>=0&&y<h) { const p=(y*w+x)*3; pixels[p]=color[0];pixels[p+1]=color[1];pixels[p+2]=color[2]; } }
  [...answer].forEach((char,i) => { const dy=randomInt(-5,6), skew=(randomInt(-8,9))/10; glyphs[char].forEach((row,y)=> [...row].forEach((bit,x)=> { if(bit==='1') for(let a=0;a<5;a++)for(let b=0;b<5;b++)dot(15+i*38+x*5+a+skew*y,17+y*5+b+dy,[35,50,100]); })); });
  for(let i=0;i<900;i++)dot(randomInt(w),randomInt(h),[130,150,180]);
  for(let i=0;i<w;i++)dot(i,35+10*Math.sin(i/18),[100,130,180]);
  const rows=Buffer.alloc((w*3+1)*h);for(let y=0;y<h;y++)pixels.copy(rows,y*(w*3+1)+1,y*w*3,(y+1)*w*3);
  const header=Buffer.alloc(13);header.writeUInt32BE(w);header.writeUInt32BE(h,4);header[8]=8;header[9]=2;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(rows)),chunk('IEND',Buffer.alloc(0))]);
}
export function newCaptcha() { return Array.from({length:6},()=>String(randomInt(2,10))).join(''); }
