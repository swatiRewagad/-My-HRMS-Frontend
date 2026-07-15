import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Lara from '@primeng/themes/lara';
import { routes } from './app.routes';
import { KeycloakService } from './services/keycloak.service';
import { EcmService } from './services/ecm.service';
import { authInterceptor } from './services/auth.interceptor';

function initializeApp(keycloak: KeycloakService, ecmService: EcmService) {
  return async () => {
    if (!keycloak.hasRealmSelected || !keycloak.isAuthenticated) {
      if (!window.location.pathname.includes('realm-select')) {
        window.location.href = '/realm-select';
      }
      return;
    }
    await keycloak.init();
    await ecmService.resolveCurrentUser();
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({ theme: { preset: Lara } }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [KeycloakService, EcmService],
      multi: true,
    },
  ],
};
