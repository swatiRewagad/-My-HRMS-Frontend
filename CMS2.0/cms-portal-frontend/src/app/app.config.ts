import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER, inject } from '@angular/core';
import { provideRouter, withViewTransitions, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';
import { definePreset } from '@primeng/themes';
import { routes } from './app.routes';

const BluePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}'
    }
  }
});
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
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: BluePreset,
        options: {
          darkModeSelector: '.my-app-dark'
        }
      }
    }),
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
