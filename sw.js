// Лесной рубеж — offline support for the installed (home screen) version.
// The game page and version file are always fetched fresh when online; everything else is cached.
const CACHE = 'lr-v10';
const CORE = ['app.html', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'icon-180.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return;
  const fresh = url.origin === location.origin && (/\.html$|\/$|version\.json$/.test(url.pathname));
  if (fresh){
    e.respondWith(fetch(req).then(r => { if (r.ok){ const cp = r.clone(); caches.open(CACHE).then(c => c.put(req, cp)); } return r; })
      .catch(() => caches.match(req, {ignoreSearch:true}).then(r => r || caches.match('app.html'))));
    return; }
  if (/cdnjs\.cloudflare\.com|fonts\.(googleapis|gstatic)\.com/.test(url.host) || url.origin === location.origin){
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(r => { if (r.ok || r.type === 'opaque'){ const cp = r.clone(); caches.open(CACHE).then(c => c.put(req, cp)); } return r; })));
  }
});
