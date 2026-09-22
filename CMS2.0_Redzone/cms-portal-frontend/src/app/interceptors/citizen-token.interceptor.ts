import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PublicAuthService } from '../services/public-auth.service';

const CITIZEN_ENDPOINTS = ['/api/v1/complaints', '/api/v1/appeals', '/api/v1/feedback'];

export const citizenTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (!CITIZEN_ENDPOINTS.some(path => req.url.includes(path))) {
    return next(req);
  }

  const auth = inject(PublicAuthService);
  const token = auth.getToken();
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { 'X-Citizen-Token': token } }));
};
