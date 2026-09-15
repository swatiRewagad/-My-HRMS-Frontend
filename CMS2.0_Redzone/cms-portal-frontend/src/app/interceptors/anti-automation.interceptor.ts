import { HttpInterceptorFn } from '@angular/common/http';

let formStartTime = Date.now();

export function resetFormStartTime() {
  formStartTime = Date.now();
}

export const antiAutomationInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/v1/citizen/') && !req.url.includes('/api/v1/complaints')) {
    return next(req);
  }

  const modifiedReq = req.clone({
    setHeaders: {
      'X-Form-Start': String(formStartTime),
    }
  });

  return next(modifiedReq);
};
