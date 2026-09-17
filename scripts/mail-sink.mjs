// Mail sink — a tiny local SMTP server that accepts every message and saves it, so notification email
// can be checked without sending anything to real people.
//
//   node scripts/mail-sink.mjs [port] [folder]      defaults: 2525, ./mail-sink
//
// Point the backend at it (no TLS, no login):
//   APP_NOTIFICATIONS_MAIL_ENABLED=true APP_MAIL_HOST=localhost APP_MAIL_PORT=2525 APP_MAIL_STARTTLS=false
//
// Each message is written as <n>.eml (open it in any mail client) and summarised on the console.

import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';

const port = Number(process.argv[2] || 2525);
const folder = path.resolve(process.argv[3] || 'mail-sink');
fs.mkdirSync(folder, { recursive: true });
let count = fs.readdirSync(folder).filter((f) => f.endsWith('.eml')).length;

/** Decodes an RFC 2047 header such as =?UTF-8?B?...?= so Arabic subjects print readably. */
function decodeHeader(value) {
  return value.replace(/=\?([^?]+)\?([BbQq])\?([^?]*)\?=/g, (_, charset, enc, text) => {
    const bytes = enc.toUpperCase() === 'B'
      ? Buffer.from(text, 'base64')
      : Buffer.from(text.replace(/_/g, ' ').replace(/=([0-9A-F]{2})/gi, (m, h) => String.fromCharCode(parseInt(h, 16))), 'binary');
    return bytes.toString(charset.toLowerCase().includes('utf') ? 'utf8' : 'latin1');
  }).replace(/\?=\s+=\?/g, '');
}

net.createServer((socket) => {
  let buffer = '';
  let inData = false;
  let envelope = { from: '', to: [] };
  const reply = (line) => socket.write(line + '\r\n');
  reply('220 journey-mail-sink ready');

  socket.on('data', (chunk) => {
    buffer += chunk.toString('binary');
    while (true) {
      if (inData) {
        const end = buffer.indexOf('\r\n.\r\n');
        if (end < 0) return;
        const raw = buffer.slice(0, end).replace(/\r\n\.\./g, '\r\n.');
        buffer = buffer.slice(end + 5);
        inData = false;
        const file = path.join(folder, `${++count}.eml`);
        fs.writeFileSync(file, Buffer.from(raw, 'binary'));
        const subject = (raw.match(/^Subject: (.*(?:\r\n[ \t].*)*)/m)?.[1] ?? '').replace(/\r\n[ \t]/g, ' ');
        console.log(`#${count} to ${envelope.to.join(', ')} — ${decodeHeader(subject)}  (${path.basename(file)})`);
        envelope = { from: '', to: [] };
        reply('250 OK: saved');
        continue;
      }
      const eol = buffer.indexOf('\r\n');
      if (eol < 0) return;
      const line = buffer.slice(0, eol);
      buffer = buffer.slice(eol + 2);
      const verb = line.slice(0, 4).toUpperCase();
      if (verb === 'EHLO' || verb === 'HELO') { reply('250-journey-mail-sink'); reply('250 8BITMIME'); }
      else if (verb === 'MAIL') { envelope.from = line.slice(10).trim(); reply('250 OK'); }
      else if (verb === 'RCPT') { envelope.to.push(line.slice(8).trim().replace(/[<>]/g, '')); reply('250 OK'); }
      else if (verb === 'DATA') { inData = true; reply('354 End data with <CR><LF>.<CR><LF>'); }
      else if (verb === 'QUIT') { reply('221 Bye'); socket.end(); return; }
      else if (verb === 'RSET' || verb === 'NOOP') reply('250 OK');
      else reply('502 Not implemented');
    }
  });
  socket.on('error', () => undefined);
}).listen(port, () => console.log(`mail sink on localhost:${port}, saving to ${folder}`));
