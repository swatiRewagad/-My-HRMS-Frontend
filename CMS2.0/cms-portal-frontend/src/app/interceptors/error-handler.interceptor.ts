import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, retry, throwError } from 'rxjs';

/**
 * NFR-002/NFR-014: Data integrity and reliability.
 * Retries failed requests (network errors) up to 2 times before failing.
 * Ensures no silent data loss on transient failures.
 */
export const errorHandlerInterceptor: HttpInterceptorFn = (req, next) => {
  const isIdempotent = req.method === 'GET' || req.method === 'HEAD';

  return next(req).pipe(
    retry({ count: isIdempotent ? 2 : 0, delay: 1000 }),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 0) {
        console.error('[NFR-014] Network error - check connectivity:', req.url);
      } else if (error.status === 401) {
        console.warn('[NFR-005] Unauthorized - session may have expired');
      } else if (error.status >= 500) {
        console.error('[NFR-014] Server error:', error.status, req.url);
      }
      return throwError(() => error);
    })
  );
};
