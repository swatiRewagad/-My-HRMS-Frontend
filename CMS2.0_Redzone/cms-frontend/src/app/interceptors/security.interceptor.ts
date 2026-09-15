import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_COOKIE = 'XSRF-TOKEN';

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[2]) : null;
}

export const securityInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  let securedReq = req;

  if (!req.method || req.method === 'GET' || req.method === 'HEAD') {
    return next(securedReq);
  }

  const csrfToken = getCookie(CSRF_COOKIE);
  if (csrfToken) {
    securedReq = req.clone({
      setHeaders: { [CSRF_HEADER]: csrfToken },
    });
  }

  securedReq = securedReq.clone({
    setHeaders: {
      'X-Requested-With': 'XMLHttpRequest',
      'X-Content-Type-Options': 'nosniff',
    },
  });

  return next(securedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 403 && error.error?.message?.includes('CSRF')) {
        console.error('CSRF token validation failed. Please refresh the page.');
      }
      return throwError(() => error);
    })
  );
};
