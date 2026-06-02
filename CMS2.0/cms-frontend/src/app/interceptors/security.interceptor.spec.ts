// @ts-nocheck
import { securityInterceptor } from './security.interceptor';
import { of, throwError } from 'rxjs';

function createRequest(method: string, url = 'http://localhost/api/v1/test') {
  const headers: Record<string, string> = {};
  return {
    method,
    url,
    clone: (opts: any) => {
      if (opts.setHeaders) {
        Object.assign(headers, opts.setHeaders);
      }
      return { method, url, headers, clone: (o2: any) => { Object.assign(headers, o2?.setHeaders || {}); return { method, url, headers }; } };
    },
    headers,
  } as any;
}

describe('securityInterceptor', () => {
  it('should pass through GET requests unchanged', (done) => {
    const req = createRequest('GET');
    const next = (r: any) => of({ status: 200 });

    securityInterceptor(req, next).subscribe({
      next: (res: any) => {
        expect(res.status).toBe(200);
        done();
      },
    });
  });

  it('should pass through HEAD requests unchanged', (done) => {
    const req = createRequest('HEAD');
    const next = (r: any) => of({ status: 200 });

    securityInterceptor(req, next).subscribe({
      next: (res: any) => {
        expect(res.status).toBe(200);
        done();
      },
    });
  });

  it('should add security headers to POST requests', (done) => {
    let capturedReq: any;
    const req = createRequest('POST');
    const next = (r: any) => { capturedReq = r; return of({ status: 200 }); };

    securityInterceptor(req, next).subscribe({
      next: () => {
        expect(capturedReq.headers['X-Requested-With']).toBe('XMLHttpRequest');
        expect(capturedReq.headers['X-Content-Type-Options']).toBe('nosniff');
        done();
      },
    });
  });

  it('should add CSRF token from cookie if available', (done) => {
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf-token-123',
    });

    let capturedReq: any;
    const req = createRequest('POST');
    const next = (r: any) => { capturedReq = r; return of({ status: 200 }); };

    securityInterceptor(req, next).subscribe({
      next: () => {
        expect(capturedReq.headers['X-XSRF-TOKEN']).toBe('test-csrf-token-123');
        done();
      },
    });

    // Clean up
    Object.defineProperty(document, 'cookie', { writable: true, value: '' });
  });

  it('should propagate errors', (done) => {
    const req = createRequest('POST');
    const error = { status: 500, error: { message: 'Server error' } };
    const next = (r: any) => throwError(() => error);

    securityInterceptor(req, next).subscribe({
      error: (err: any) => {
        expect(err.status).toBe(500);
        done();
      },
    });
  });
});
