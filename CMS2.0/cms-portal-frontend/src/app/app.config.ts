import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withViewTransitions, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { sessionTimeoutInterceptor } from './interceptors/session-timeout.interceptor';
import { errorHandlerInterceptor } from './interceptors/error-handler.interceptor';
import { securityHeadersInterceptor } from './interceptors/security-headers.interceptor';
import { keycloakTokenInterceptor } from './interceptors/keycloak-token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withViewTransitions(),
      withRouterConfig({ onSameUrlNavigation: 'reload' })
    ),
    provideHttpClient(
      withInterceptors([
        keycloakTokenInterceptor,
        securityHeadersInterceptor,
        sessionTimeoutInterceptor,
        errorHandlerInterceptor,
      ])
    ),
  ]
};
