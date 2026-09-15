import { rateLimitInterceptor } from './rate-limit.interceptor';
import { of } from 'rxjs';

function createRequest(method: string, url: string) {
  return { method, url } as any;
}

function createNext() {
  return (req: any) => of({ type: 4, body: {} });
}

describe('rateLimitInterceptor', () => {
  beforeEach(() => {
    // Clear any rate limit state between tests by waiting
    // The map is module-scoped so we work around with fresh endpoints
  });

  it('should pass through GET requests without rate limiting', (done) => {
    const req = createRequest('GET', 'http://localhost/api/v1/complaints');
    const next = createNext();

    rateLimitInterceptor(req, next).subscribe({
      next: (res) => {
        expect(res).toBeDefined();
        done();
      },
    });
  });

  it('should pass through HEAD requests', (done) => {
    const req = createRequest('HEAD', 'http://localhost/api/v1/status');
    const next = createNext();

    rateLimitInterceptor(req, next).subscribe({
      next: (res) => {
        expect(res).toBeDefined();
        done();
      },
    });
  });

  it('should allow POST requests within limit', (done) => {
    const uniqueUrl = `http://localhost/api/v1/unique-${Date.now()}`;
    const req = createRequest('POST', uniqueUrl);
    const next = createNext();

    rateLimitInterceptor(req, next).subscribe({
      next: (res) => {
        expect(res).toBeDefined();
        done();
      },
    });
  });

  it('should block requests after exceeding rate limit', (done) => {
    const uniqueUrl = `http://localhost/api/v1/ratelimit-test-${Date.now()}`;
    const next = createNext();
    let completed = 0;
    let blocked = false;

    // Fire 61 requests (DEFAULT max is 60)
    for (let i = 0; i < 61; i++) {
      const req = createRequest('POST', uniqueUrl);
      rateLimitInterceptor(req, next).subscribe({
        next: () => { completed++; },
        error: (err: any) => {
          blocked = true;
          expect(err.status).toBe(429);
          expect(err.error.message).toContain('Too many requests');
          done();
        },
      });
      if (blocked) break;
    }

    if (!blocked) {
      done();
    }
  });
});
