import { HttpInterceptorFn } from '@angular/common/http';

/**
 * NFR-013: Encryption in transit. Adds security headers to all requests.
 * NFR-009: SSDLC compliance - CSRF protection, content type enforcement.
 */
export const securityHeadersInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api/pincode')) {
    return next(req);
  }
  const secureReq = req.clone({
    setHeaders: {
      'X-Content-Type-Options': 'nosniff',
      'X-Requested-With': 'XMLHttpRequest',
      'Cache-Control': 'no-store',
      'Pragma': 'no-cache',
    }
  });
  return next(secureReq);
};
