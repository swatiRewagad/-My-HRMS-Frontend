import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslationService } from '../services/translation.service';

/**
 * `{{ 'ui.cepc.grid.unread_only' | translateOr: 'Unread Only' }}`
 *
 * The `translate` pipe with the screen's existing English as the fallback, for labels whose English must not
 * change when the screen is wired up to i18n. See `TranslationService.translateOr`.
 */
@Pipe({
  name: 'translateOr',
  standalone: true,
  pure: false
})
export class TranslateOrPipe implements PipeTransform {

  private translationService = inject(TranslationService);

  transform(key: string, fallback: string, params?: Record<string, string>): string {
    return this.translationService.translateOr(key, fallback, params);
  }
}
