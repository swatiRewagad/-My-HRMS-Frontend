import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/**
 * The single status badge for every portal (UST847).
 *
 * Before this existed each portal rendered status its own way and none of them agreed:
 *  - RE used `[class]="'status-' + status"` with lowercase snake_case. Worse, `[class]` REPLACES the
 *    whole class attribute, so binding it stripped `.status-badge` itself and the padding and radius
 *    went with it. Its five SCSS classes also matched nothing, because the server writes
 *    "re_responded" while the stylesheet expected "responded".
 *  - RBIO used `[attr.data-status]` with UPPERCASE and styled only three of its statuses.
 *  - The public tracker used both mechanisms at once, upper-casing then kebab-casing.
 *  - CEPC/AA duplicated a `getStatusLabel` Record map per component.
 *
 * This standardises on `[attr.data-status]` with an UPPERCASE, underscore-preserving value, because
 * an attribute selector cannot clobber the base class the way `[class]` did, and uppercase already
 * matches what the newer tables store. Values arriving in any casing are normalised here, so a
 * caller passing "re_responded" and one passing "RE_RESPONDED" render identically — which is
 * precisely what UST847 asks for between the RE and RBI sides.
 *
 * Labels resolve through the translation pipe. Portals that previously hardcoded English get
 * localisation for free, and no portal can drift to its own wording again.
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss'
})
export class StatusBadgeComponent {
  /** Raw status from the API, in any casing. */
  status = input.required<string | null | undefined>();

  /**
   * Translation key for the label. When omitted, one is derived from the status. Callers that get a
   * key from the server (the RE activity ladder does) should pass it, so the server stays the single
   * source of truth for which key describes which value.
   */
  labelKey = input<string | null>(null);

  /** Optional prefix for derived keys, letting the same component serve different vocabularies. */
  keyPrefix = input<string>('status');

  /** Falls back to the humanised raw value when a key has no translation seeded yet. */
  fallbackLabel = input<string | null>(null);

  normalised = computed(() => (this.status() ?? '').trim().toUpperCase());

  resolvedKey = computed(() => {
    const explicit = this.labelKey();
    if (explicit) {
      return explicit;
    }
    const value = this.normalised();
    return value ? `${this.keyPrefix()}.${value.toLowerCase()}` : `${this.keyPrefix()}.unknown`;
  });

  /**
   * Shown when the key is missing from the translation bundle. Turns RESPONSE_BEING_PREPARED into
   * "Response Being Prepared" rather than leaving a raw enum on screen — the old `| titlecase`
   * rendered "Re_responded", which is what a user actually saw before this.
   */
  humanised = computed(() => {
    const explicit = this.fallbackLabel();
    if (explicit) {
      return explicit;
    }
    const value = this.normalised();
    if (!value) {
      return '';
    }
    return value
      .split('_')
      .filter(part => part.length > 0)
      .map(part => part.charAt(0) + part.slice(1).toLowerCase())
      .join(' ');
  });
}
