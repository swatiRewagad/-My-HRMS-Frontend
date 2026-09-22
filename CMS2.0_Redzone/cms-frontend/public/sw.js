const CACHE_NAME = 'cms-cache-v2';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
  '/offline.html'
];

const API_CACHE_NAME = 'cms-api-cache-v1';
const API_CACHE_URLS = [
  '/api/categories',
  '/api/categories/root',
  '/api/banks',
  '/api/form-config/raise-complaint',
  '/api/dashboard'
];

// Install — cache static shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

// Activate — clean old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME && key !== API_CACHE_NAME)
          .map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

// Fetch strategy
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // API requests — Network First, fallback to cache
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirstStrategy(request));
    return;
  }

  // Static assets (JS, CSS, images) — Cache First
  if (request.destination === 'script' ||
      request.destination === 'style' ||
      request.destination === 'image' ||
      request.destination === 'font') {
    event.respondWith(cacheFirstStrategy(request));
    return;
  }

  // Navigation — Network First with offline fallback
  if (request.mode === 'navigate') {
    event.respondWith(navigationStrategy(request));
    return;
  }

  // Default — Network First
  event.respondWith(networkFirstStrategy(request));
});

async function networkFirstStrategy(request) {
  try {
    const response = await fetch(request);
    if (response.ok && request.method === 'GET') {
      const cache = await caches.open(API_CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await caches.match(request);
    if (cached) return cached;
    return new Response(JSON.stringify({ error: 'Offline' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}

async function cacheFirstStrategy(request) {
  const cached = await caches.match(request);
  if (cached) return cached;

  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    return new Response('', { status: 503 });
  }
}

async function navigationStrategy(request) {
  try {
    const response = await fetch(request);
    return response;
  } catch (error) {
    const cached = await caches.match('/index.html');
    if (cached) return cached;
    return caches.match('/offline.html');
  }
}

// Background sync for offline complaint filing
self.addEventListener('sync', (event) => {
  if (event.tag === 'sync-complaints') {
    event.waitUntil(syncPendingComplaints());
  }
});

async function syncPendingComplaints() {
  const cache = await caches.open('cms-pending-actions');
  const requests = await cache.keys();

  for (const request of requests) {
    try {
      const cachedResponse = await cache.match(request);
      const body = await cachedResponse.text();
      await fetch(request.url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body
      });
      await cache.delete(request);
    } catch (error) {
      // Will retry on next sync event
    }
  }
}
