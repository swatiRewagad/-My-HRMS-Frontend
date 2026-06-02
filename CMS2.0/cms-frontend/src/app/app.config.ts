import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Lara from '@primeng/themes/lara';
import { routes } from './app.routes';
import { securityInterceptor } from './interceptors/security.interceptor';
import { rateLimitInterceptor } from './interceptors/rate-limit.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([securityInterceptor, rateLimitInterceptor])
    ),
    provideAnimationsAsync(),
    providePrimeNG({ theme: { preset: Lara } }),
  ],
};
