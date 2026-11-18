// Лесной рубеж — сервер для игры по сети (без Claude).
// Запуск:  node server.js        (порт 8080)   или   PORT=3000 node server.js
// Нужен только Node.js 16+, никаких npm-пакетов. Положите рядом index.html и play.html.
// Игроки в той же Wi-Fi сети открывают http://<IP этого компьютера>:8080
// Для игры через интернет разверните этот файл на любом хостинге Node.js (Render, Railway, VPS).
'use strict';
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto'), os = require('os');
const PORT = +process.env.PORT || 8080, ROOT = __dirname;
const MAX_MSG = 64 * 1024, MAX_PEERS = 64;
const TYPES = {'.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css', '.png':'image/png', '.json':'application/json', '.apk':'application/vnd.android.package-archive', '.zip':'application/zip'};

const server = http.createServer((req, res) => {
  let p = decodeURIComponent((req.url || '/').split('?')[0]);
  if (p === '/' || p === '') p = '/index.html';
  if (p === '/status'){ res.writeHead(200, {'Content-Type':'application/json', 'Access-Control-Allow-Origin':'*'}); return res.end(JSON.stringify({ok:true, players:clients.size})); }
  const file = path.normalize(path.join(ROOT, p));
  if (!file.startsWith(ROOT) || path.basename(file) === 'server.js'){ res.writeHead(403); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err){ res.writeHead(404); return res.end('not found'); }
    res.writeHead(200, {'Content-Type':TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control':'no-cache'}); res.end(data);
  });
});

// ---- minimal WebSocket (RFC 6455): text frames, ping/pong, close ----
const clients = new Map(); let nextId = 1;
function frame(str){ const b = Buffer.from(str), n = b.length; let h;
  if (n < 126) h = Buffer.from([0x81, n]);
  else if (n < 65536){ h = Buffer.alloc(4); h[0] = 0x81; h[1] = 126; h.writeUInt16BE(n, 2); }
  else { h = Buffer.alloc(10); h[0] = 0x81; h[1] = 127; h.writeBigUInt64BE(BigInt(n), 2); }
  return Buffer.concat([h, b]); }
function send(c, obj){ if (!c.sock.destroyed) try { c.sock.write(frame(JSON.stringify(obj))); } catch(e){} }
function broadcast(from, obj){ const f = frame(JSON.stringify(obj)); for (const c of clients.values()) if (c !== from && !c.sock.destroyed) try { c.sock.write(f); } catch(e){} }
function drop(c){ if (!clients.has(c.id)) return; clients.delete(c.id); broadcast(null, {t:'x', id:c.id}); try { c.sock.destroy(); } catch(e){} }

server.on('upgrade', (req, sock) => {
  if ((req.url || '').split('?')[0] !== '/ws' || !req.headers['sec-websocket-key'] || clients.size >= MAX_PEERS){ sock.destroy(); return; }
  const accept = crypto.createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  sock.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  sock.setNoDelay(true);
  const c = {id:'p' + (nextId++) + '_' + crypto.randomBytes(3).toString('hex'), sock, buf:Buffer.alloc(0), pres:null, last:Date.now(), n:0, win:Date.now()};
  clients.set(c.id, c);
  const peers = {}; for (const o of clients.values()) if (o !== c && o.pres) peers[o.id] = o.pres;
  send(c, {t:'hi', id:c.id, peers});
  sock.on('data', d => {
    c.buf = Buffer.concat([c.buf, d]); if (c.buf.length > MAX_MSG * 2) return drop(c);
    while (c.buf.length >= 2){
      const b0 = c.buf[0], b1 = c.buf[1], op = b0 & 15, masked = b1 & 128; let len = b1 & 127, off = 2;
      if (len === 126){ if (c.buf.length < 4) return; len = c.buf.readUInt16BE(2); off = 4; }
      else if (len === 127){ if (c.buf.length < 10) return; len = Number(c.buf.readBigUInt64BE(2)); off = 10; }
      if (len > MAX_MSG || !masked) return drop(c);
      if (c.buf.length < off + 4 + len) return;
      const mask = c.buf.slice(off, off + 4), data = Buffer.from(c.buf.slice(off + 4, off + 4 + len));
      for (let i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
      c.buf = c.buf.slice(off + 4 + len); c.last = Date.now();
      if (op === 8) return drop(c);
      if (op === 9){ try { sock.write(Buffer.concat([Buffer.from([0x8a, data.length]), data])); } catch(e){} continue; }
      if (op !== 1) continue;
      const t = Date.now(); if (t - c.win > 1000){ c.win = t; c.n = 0; } if (++c.n > 40) continue;   // flood guard: 40 msgs/s
      let m; try { m = JSON.parse(data.toString('utf8')); } catch(e){ continue; }
      if (m && m.t === 'p' && m.d && typeof m.d === 'object'){ c.pres = m.d; broadcast(c, {t:'p', id:c.id, d:m.d}); }
    }
  });
  sock.on('close', () => drop(c)); sock.on('error', () => drop(c));
});
setInterval(() => { const t = Date.now(); for (const c of clients.values()) if (t - c.last > 20000) drop(c); }, 5000);

server.listen(PORT, () => {
  console.log('Лесной рубеж: сервер запущен на порту ' + PORT);
  for (const list of Object.values(os.networkInterfaces())) for (const a of list || []) if (a.family === 'IPv4' && !a.internal) console.log('  Игроки в вашей сети: http://' + a.address + ':' + PORT + '   (адрес сервера в игре: ' + a.address + ':' + PORT + ')');
  console.log('  На этом компьютере: http://localhost:' + PORT);
});
