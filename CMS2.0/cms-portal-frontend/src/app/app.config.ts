import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER, inject } from '@angular/core';
import { provideRouter, withViewTransitions, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { sessionTimeoutInterceptor } from './interceptors/session-timeout.interceptor';
import { errorHandlerInterceptor } from './interceptors/error-handler.interceptor';
import { securityHeadersInterceptor } from './interceptors/security-headers.interceptor';
import { keycloakTokenInterceptor } from './interceptors/keycloak-token.interceptor';
import { antiAutomationInterceptor } from './interceptors/anti-automation.interceptor';
import { RuntimeConfigService } from './services/runtime-config.service';

function initializeApp(configService: RuntimeConfigService) {
  return () => configService.load();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [RuntimeConfigService],
      multi: true
    },
    provideRouter(
      routes,
      withViewTransitions(),
      withRouterConfig({ onSameUrlNavigation: 'reload' })
    ),
    provideHttpClient(
      withInterceptors([
        keycloakTokenInterceptor,
        securityHeadersInterceptor,
        antiAutomationInterceptor,
        sessionTimeoutInterceptor,
        errorHandlerInterceptor,
      ])
    ),
  ]
};
