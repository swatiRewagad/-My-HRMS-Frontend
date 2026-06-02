import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { throwError } from 'rxjs';

interface RateLimitEntry {
  count: number;
  resetTime: number;
}

const rateLimitMap = new Map<string, RateLimitEntry>();

const RATE_LIMITS: Record<string, { max: number; windowMs: number }> = {
  'POST:/complaints': { max: 5, windowMs: 60_000 },
  'POST:/files/upload': { max: 20, windowMs: 60_000 },
  'POST:/files/upload/chunk': { max: 100, windowMs: 60_000 },
  'POST:/email-simulation': { max: 10, windowMs: 60_000 },
  'DEFAULT': { max: 60, windowMs: 60_000 },
};

function getEndpointKey(req: HttpRequest<unknown>): string {
  const url = new URL(req.url, window.location.origin);
  const path = url.pathname.replace(/\/api\/v\d+/, '').replace(/\/\d+/g, '/:id');

  for (const key of Object.keys(RATE_LIMITS)) {
    if (key === 'DEFAULT') continue;
    const [method, pattern] = key.split(':');
    if (req.method === method && path.includes(pattern)) {
      return key;
    }
  }
  return `${req.method}:${path}`;
}

export const rateLimitInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  if (req.method === 'GET' || req.method === 'HEAD') {
    return next(req);
  }

  const key = getEndpointKey(req);
  const limits = Object.keys(RATE_LIMITS).find(k => key.includes(k.split(':')[1] || ''))
    ? RATE_LIMITS[key] || RATE_LIMITS['DEFAULT']
    : RATE_LIMITS['DEFAULT'];

  const now = Date.now();
  const entry = rateLimitMap.get(key);

  if (entry) {
    if (now > entry.resetTime) {
      rateLimitMap.set(key, { count: 1, resetTime: now + limits.windowMs });
    } else if (entry.count >= limits.max) {
      const retryAfter = Math.ceil((entry.resetTime - now) / 1000);
      return throwError(() => ({
        status: 429,
        error: { message: `Too many requests. Please wait ${retryAfter} seconds before trying again.` },
      }));
    } else {
      entry.count++;
    }
  } else {
    rateLimitMap.set(key, { count: 1, resetTime: now + limits.windowMs });
  }

  return next(req);
};
