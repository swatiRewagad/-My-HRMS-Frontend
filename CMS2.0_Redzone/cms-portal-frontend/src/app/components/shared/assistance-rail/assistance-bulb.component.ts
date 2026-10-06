import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AssistanceRail,
  AssistanceRailService
} from '../../../services/assistance-rail.service';
import { TranslationService } from '../../../services/translation.service';

/**
 * The bulb (Brief 21 §5.1): the affordance that tells an officer there is something in the assistance
 * rail WITHOUT opening anything.
 *
 * <h2>Why this is a component and not three lines in each host</h2>
 * Five staff screens need it. Each already has its own icon strip with its own class names, so the
 * TEMPTING shape was a `[glow]` class binding per host driven by whatever each one could reach. That
 * fails on the thing the brief cares about most: the bulb has to be right while the panel is CLOSED, and
 * the panel is destroyed when closed on every one of these screens. A host computing glow for itself
 * would have to re-implement the dismissal rule, and the copy that drifted would be the bulb — a bulb
 * that is wrong looks exactly like a bulb that is right.
 *
 * <h2>It reads; it does not render the rail</h2>
 * This probes `/assistance/rail` through the service's shared cache and renders ONE button. The panel is
 * still the host's to show. Because both go through {@link AssistanceRailService.railCached}, the probe
 * and the officer's first open are one HTTP request, not two.
 *
 * <h2>The kill switch hides the affordance rather than disabling it</h2>
 * §6.2 requires every feature to have a config kill switch "honoured by the frontend as 'hide the
 * affordance'". So when `/assistance/status` says unavailable — or cannot be reached, which the service
 * degrades to the same answer — this renders NOTHING. Not a greyed-out bulb: a disabled control is a
 * promise of a feature, and an officer who clicks it has been told the system is broken rather than that
 * the feature is off.
 *
 * <h2>Glow is never the only signal</h2>
 * Three channels, because colour and animation alone fail WCAG and fail `prefers-reduced-motion`:
 * <ul>
 *   <li>the glow class, which the stylesheet reduces to a static ring under reduced-motion;
 *   <li>a visible numeric badge;
 *   <li>`data-signal-count` plus the count in the accessible name, so assistive technology hears it.
 * </ul>
 */
@Component({
  selector: 'app-assistance-bulb',
  standalone: true,
  template: `
    <!--
      Rendered only when the feature is ON. @if and not [hidden]: §6.2's kill switch means "hide the
      affordance", and a hidden-but-present button is still in the accessibility tree on some stacks.
    -->
    @if (enabled()) {
      <button type="button"
              class="assistance-bulb"
              [class]="buttonClass()"
              [class.assistance-bulb--glow]="glow()"
              [class.active]="open()"
              data-testid="assistance-bulb"
              [attr.data-signal-count]="count()"
              [attr.aria-pressed]="open()"
              [attr.aria-label]="label()"
              [attr.title]="label()"
              (click)="togglePanel.emit()">
        <i class="pi pi-lightbulb" aria-hidden="true"></i>
        <!--
          The SIGHTED non-colour channel. Only when there is a count to show, because a badge reading
          "0" over a dark bulb says the rail has nothing in a way that invites a click to confirm it.
        -->
        @if (glow()) {
          <span class="assistance-bulb-badge" aria-hidden="true">{{ count() }}</span>
        }
      </button>
    }
  `,
  styleUrl: './assistance-bulb.component.scss'
})
export class AssistanceBulbComponent {

  private service = inject(AssistanceRailService);
  private i18n = inject(TranslationService);

  /**
   * The complaint on screen, as its NUMBER (`CMS-20260601-A1B2C3`) and never a numeric route id — the
   * rail keys tier-0 memory on the number, so an id yields a rail that is correctly empty, which reads
   * exactly like a feature that does not work.
   *
   * <p>Empty is a legitimate value and means "nothing to ask about yet": hosts bind it from a computed
   * over a record that is still loading. No request is issued until it is non-blank.
   */
  readonly complaintNumber = input('');

  /** Whether the host's panel is currently open, for `aria-pressed` and the active class. */
  readonly open = input(false);

  /**
   * The host's own icon-strip class, applied ALONGSIDE `assistance-bulb`.
   *
   * <p>These five screens have three different icon strips (`icon-sidebar-btn`, `iib-btn`, `rail-icon`)
   * whose geometry belongs to the host, not here. The alternative — this component imposing its own box
   * — would put one misaligned icon in each strip, so the host passes its class and this contributes
   * only the glow and the badge.
   */
  readonly hostClass = input('');

  /**
   * Named `togglePanel`, not `toggle`: `toggle` is a native DOM event (`<details>` fires it), so an
   * output called that trips `@angular-eslint/no-output-native` and a host's `(toggle)` binding would
   * become ambiguous the moment this button sat inside a disclosure element. Same reason the panel's
   * output is `closePanel` rather than `close`.
   */
  readonly togglePanel = output<void>();

  /** Null until the probe resolves. A failed probe leaves it null, which is dark and silent. */
  private readonly rail = signal<AssistanceRail | null>(null);

  /**
   * The §6.2 kill switch. Starts FALSE, so the bulb is absent until the server says the feature is on
   * rather than appearing and then vanishing on a slow network.
   */
  private readonly available = signal(false);

  readonly enabled = computed(() => this.available());

  /** Driven by the service's single rule, so the bulb and the panel cannot disagree. */
  private readonly verdict = computed(() =>
    this.service.verdict(this.complaintNumber(), this.rail())
  );

  readonly glow = computed(() => this.verdict().glow);
  readonly count = computed(() => this.verdict().count);

  /**
   * The accessible name, carrying the COUNT when there is one — the third non-colour channel.
   *
   * <p>Falls back to English prose rather than printing `assistance.bulb_label` into an accessible name,
   * on the same rule the panel uses: a complete English sentence beats a bare key on screen, and the
   * real fix is seeding.
   */
  readonly label = computed(() => {
    const count = this.count();
    if (count > 0) {
      const text = this.i18n.translate('assistance.bulb_label_count', { count: String(count) });
      if (text && text !== 'assistance.bulb_label_count' && !text.includes('{{')) return text;
      return `Assistance — ${count} suggestion${count === 1 ? '' : 's'}`;
    }
    const text = this.i18n.translate('assistance.panel_title');
    return text && text !== 'assistance.panel_title' ? text : 'Assistance';
  });

  readonly buttonClass = computed(() => this.hostClass());

  constructor() {
    // The kill switch, read once per app through the service's shared cache.
    effect(() => {
      this.service.available().subscribe(on => this.available.set(on));
    });

    /*
     * The probe. Gated on the switch being ON, so a disabled feature costs no rail request at all —
     * otherwise every screen load would query a rail it is forbidden to show.
     *
     * Re-reads when the complaint changes, which matters on the task screens: the officer moves to the
     * next complaint without a reload, and a bulb still glowing from the previous one would be pointing
     * at signals belonging to a complaint that is no longer on screen.
     */
    effect(() => {
      if (!this.available()) return;
      const complaint = this.complaintNumber().trim();
      if (!complaint) return;
      this.service.railCached(complaint).subscribe({
        next: rail => this.rail.set(rail),
        // SILENT, per §5.1: assistance is off the critical path, so a failure here means no bulb and no
        // message. The deliberate opposite of the user-initiated search, which must say when it fails —
        // nobody asked for this, so nobody is owed an error about it.
        error: () => this.rail.set(null)
      });
    });
  }
}
