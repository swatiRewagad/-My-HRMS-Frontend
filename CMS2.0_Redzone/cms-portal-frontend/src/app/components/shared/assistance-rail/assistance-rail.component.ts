import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../services/translation.service';
import {
  AssistanceRailService,
  AssistanceSignal
} from '../../../services/assistance-rail.service';

/**
 * What the panel is currently showing.
 *
 * <p>There is deliberately no "empty is an error" state. The rail endpoint ALWAYS answers HTTP 200 —
 * even for an unknown complaint, because the rail must never block an officer — so `empty`
 * (`glow: false`, no signals) is a NORMAL answer and the common one: most complaints legitimately have
 * nothing to say. `failed` can therefore only mean the request did not complete at all. Collapsing the
 * two is the defect the sibling similar-cases panel was built to undo.
 *
 * <p>`dismissed` is a THIRD non-result state, and separate for the same reason the first two are: the
 * rail answering with nothing and the officer having silenced everything it answered with are different
 * statements, and printing "nothing to flag" over signals the officer themselves hid would tell them
 * the feature found nothing when it found something they chose not to see.
 */
type RailState = 'idle' | 'loading' | 'signals' | 'empty' | 'dismissed' | 'failed';

/**
 * PrimeIcons class per stable `kind`. Explicit, not derived, so an unknown kind cannot pick an icon
 * that implies a meaning nobody chose.
 */
const KIND_ICONS: Record<string, string> = {
  'unsaved-draft': 'pi-pencil',
  'last-section': 'pi-bookmark',
  'last-viewed': 'pi-clock',
  'complainant-history': 'pi-user',
  'entity-clause-precedent': 'pi-book',
  'category-closure-time': 'pi-hourglass',
  // A BAR CHART, not an arrow or a `pi-forward`. The glyph is the only thing on the row that could
  // imply "do this": a forward/arrow icon reads as a control even with no handler behind it, and this
  // signal reports what HISTORICALLY followed, which the workflow may now refuse. A chart says
  // "frequency with a denominator", which is exactly what the sentence behind it says.
  'next-action': 'pi-chart-bar'
};

/**
 * i18n key per stable `kind`, in the `assistance.signal.*` namespace the DTO's javadoc names as the
 * contract.
 *
 * <p>NOT built as `'assistance.signal.' + kind`. The mechanical form would mint a plausible key for a
 * kind nobody has translated, and {@link TranslationService.translate} renders a missing key as the key
 * itself — so a seventh kind added server-side would ship `assistance.signal.new-thing` into an
 * officer's rail as if it were prose. An unlisted kind gets no key here, which routes it to the server's
 * own English `title` instead.
 *
 * <p>THE SUFFIX IS THE `kind` VALUE VERBATIM, HYPHENS AND ALL — `assistance.signal.last-section`, NOT
 * `last_section`. Verified against `AssistanceRailTranslationSeeder`, which is the code that actually
 * populates the bundle: it seeds the hyphenated spelling in all 13 locales and its own javadoc names
 * this map as the authority for the spelling. Normalising `-` to `_` on either side would miss every
 * one of the six, and a miss here is SILENT: `translate` echoes the key back, this component reads that
 * as "not localised" and prints the server's English, so the whole panel would fall back to English
 * while looking exactly like a working, merely-untranslated panel.
 */
const KIND_LABEL_KEYS: Record<string, string> = {
  'unsaved-draft': 'assistance.signal.unsaved-draft',
  'last-section': 'assistance.signal.last-section',
  'last-viewed': 'assistance.signal.last-viewed',
  'complainant-history': 'assistance.signal.complainant-history',
  'entity-clause-precedent': 'assistance.signal.entity-clause-precedent',
  'category-closure-time': 'assistance.signal.category-closure-time',
  'next-action': 'assistance.signal.next-action'
};

/** The one kind whose `detail` is the officer's own text rather than server prose. */
const DRAFT_KIND = 'unsaved-draft';

/** The one kind whose `detail` is a machine-readable `LocalDateTime.toString()` rather than prose. */
const LAST_VIEWED_KIND = 'last-viewed';

/**
 * The one kind the panel renders with extra visual weight — and the one it must render as PROSE AND
 * NOTHING ELSE.
 *
 * <p>`next-action` reports the action that most often FOLLOWED in comparable past cases. That rollup is
 * mined from what historically happened, not from what the workflow currently permits, so it can and
 * will sometimes name an action `workflow-action-bar` would now refuse. Brief 21 is "suggest, highlight,
 * do not auto-select": the highlight is {@link isHighlighted}, which reaches CSS only, and there is
 * deliberately no control, no `link` and no output for it — a click that pre-selected a transition from
 * a historical frequency would be the rail committing workflow state on an officer's behalf.
 */
const NEXT_ACTION_KIND = 'next-action';

/**
 * English of record for the keys THIS COMPONENT introduced and which are not yet in the bundle.
 *
 * <p>The signal headings have the server's English `title` as their fallback. These do not — they are
 * the panel's own chrome, so there is no server prose behind them — and the alternative to checking the
 * English in here is rendering `assistance.dismiss` to an officer. Same rule as everywhere else in this
 * file: a complete English sentence beats a bare key or a raw `{{count}}`, and the real fix is seeding,
 * not this map.
 *
 * <p>`AssistanceRailTranslationSeeder` is the code that populates the bundle and is owned elsewhere, so
 * these four keys are REPORTED for seeding rather than added here. Once seeded in a locale this map
 * stops being reached in that locale, with no change to this file.
 */
const UI_TEXT_FALLBACK: Record<string, string> = {
  'assistance.dismiss': 'Dismiss this kind of suggestion',
  'assistance.announce_signals': 'Assistance has {{count}} suggestions for this complaint.',
  'assistance.all_dismissed': 'You have dismissed every suggestion on this screen.',
  'assistance.all_dismissed_hint': 'They will not be offered again until you reload this screen.'
};

/**
 * How long after the panel opens the live-region sentence is written.
 *
 * <p>Not cosmetic and not a race workaround. An `aria-live` region announces MUTATIONS, not content
 * that was already there when the element entered the DOM — and the whole point of {@link
 * AssistanceRailComponent.probe} is that the answer usually arrives BEFORE the panel does, so writing
 * the sentence in the same change-detection pass that mounts the region would reliably announce
 * nothing. Writing it a tick later makes it a mutation of a region already present.
 *
 * <p>Longer than one frame on purpose: the component also moves focus to the heading on open, which
 * assistive technology reads immediately. Landing both in the same frame makes them compete and the
 * count tends to lose. The existing focus move is itself a `setTimeout`, so this follows the shape
 * already in the file rather than introducing a second scheduling convention.
 */
const ANNOUNCE_DELAY_MS = 150;

/**
 * The assistance rail (Brief 21): the first and only frontend consumer of `/api/v1/assistance/rail`.
 *
 * <h2>Localised text first, server English as the safety net</h2>
 * The API returns `title`/`detail` as already-resolved ENGLISH prose ("This complainant has 4 earlier
 * complaints"). This product ships multiple locales, so printing that unconditionally would hard-code
 * English into a localised screen while looking finished. Every line here is instead built from `kind`
 * — a stable machine key — plus the server's `params` map, through {@link TranslationService}. But a
 * resolution that comes back as the bare key (not seeded) or still carrying a `{{placeholder}}` (seeded
 * with a parameter the server did not send) is DISCARDED in favour of `title`: server English beats
 * `assistance.signal.last-section` or a raw `{{section}}` on an officer's screen.
 * {@link usesServerText} is the single site that decides this. Against the DEPLOYED backend it answers
 * true for most signals, because that build sends no `params` and so cannot fill the placeholders the
 * seeded values carry; `unsaved-draft`, whose value has no placeholder, is the one that localises
 * today. That is the designed degradation, not a state to code around.
 *
 * <h2>Why the decision is a method and the rendering is the pipe</h2>
 * Choosing between locales needs to INSPECT the resolved string, which a pipe in a template cannot do;
 * {@link usesServerText} therefore calls `TranslationService.translate` — the same function the pipe
 * wraps — purely to judge the outcome, and the template then renders either the server's `title` or the
 * key through the pipe with {@link labelParams}. Translating twice is cheap and keeps ONE text-producing
 * path on screen. Both arms re-evaluate every change detection, because `TranslatePipe` is `pure: false`
 * for exactly this reason, so a late-arriving locale bundle flips the decision and the text together.
 *
 * <h2>Tier 0 is rendered first, and that is not cosmetic</h2>
 * Tier 0 is per-user continuity; tier 1 is an aggregate prior about OTHER complaints. The server orders
 * `unsaved-draft` first deliberately because it is the only signal describing work the officer could
 * still LOSE. The grouping keeps the distinction visible: an officer who cannot tell the two apart
 * cannot tell what the rail is asserting.
 *
 * <h2>Nothing is fetched until a host asks — by opening the panel OR by probing for the bulb</h2>
 * Two triggers, ONE effect and ONE request. `open` renders the panel and reads; {@link probe} reads
 * WITHOUT rendering, so a host can light its bulb from {@link glow} before the officer has opened
 * anything — which is the entire interaction the brief describes, and which a read gated on `open`
 * alone makes impossible: a bulb that only lights once you look behind it signals nothing.
 *
 * <p>The effect is deliberately one effect over `open() || probe()` rather than one per trigger. Two
 * effects would both fire for the common host that sets both, and the `loaded` guard is not a lock —
 * it is only set in the response handler, so two synchronous passes would both see it false and issue
 * two identical GETs. {@link AssistanceRailService.railCached} would de-duplicate the HTTP, but not the
 * two subscriptions each writing the same signals, so the saving is real but the correctness argument
 * is the single trigger expression.
 *
 * <p>Laziness survives: a host that sets neither input still pays no DOM and no request. The read goes
 * through `railCached`, so a host whose bulb probed and then opened the panel pays ONE request total.
 *
 * <h2>Dismissal is per-kind, per-session, and in memory</h2>
 * There is no backend for it and none was invented. {@link dismiss} hides a `kind` for the life of this
 * component instance, and — load-bearing — {@link glow} falls to false once everything is dismissed.
 * A bulb glowing over a rail whose every row the officer has already dismissed is precisely the
 * "trains officers to ignore it" failure the brief names, so the server's `glow` is consumed as the
 * CEILING on glowing rather than as the answer: the server decides whether there is anything worth
 * saying, the officer decides whether they still want to hear it, and the bulb needs both.
 */
@Component({
  selector: 'app-assistance-rail',
  standalone: true,
  // DatePipe is here for `last-viewed` only: its `detail` is an ISO timestamp, so the officer's own
  // locale formats it rather than the server's English age phrase being printed.
  imports: [TranslatePipe, DatePipe],
  templateUrl: './assistance-rail.component.html',
  styleUrl: './assistance-rail.component.scss'
})
export class AssistanceRailComponent {

  private service = inject(AssistanceRailService);
  private i18n = inject(TranslationService);

  /** Host-driven. Opening it for the first time triggers the read; closing it renders nothing. */
  readonly open = input(false);

  /**
   * Read the rail WITHOUT rendering the panel, so the host's bulb can glow before anything is opened.
   *
   * <p>Default false, and that is the lazy contract: a host that sets neither this nor {@link open}
   * issues no request at all. A host that wants the bulb binds `[probe]="true"` and reads
   * {@link glow} — the panel still renders only when `open()` is true, which the template enforces, so
   * probing costs a request and no DOM.
   *
   * <p>Separate from `open` rather than folded into it because they are different questions. `open`
   * means "the officer is looking at this"; `probe` means "tell me whether there is anything to look
   * at". Re-using `open` for both would make the panel appear the moment the host wanted a bulb.
   */
  readonly probe = input(false);

  /**
   * The complaint on screen, as its NUMBER (`CMS-20260601-A1B2C3`). Changing it invalidates the cache,
   * so the rail cannot go on describing the complaint the officer just left.
   */
  readonly complaintNumber = input('');

  /** Shows the panel's own close affordance. Off for a host that supplies its own chrome. */
  readonly showClose = input(true);

  /**
   * Named `closePanel`, not `close`: `close` is a native DOM event name, so an output called that trips
   * `@angular-eslint/no-output-native`, and a host's `(close)` binding would become ambiguous between
   * this output and the native event the moment the panel is wrapped in a `<dialog>`.
   */
  readonly closePanel = output<void>();

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  /**
   * Everything the server said, before dismissal. Kept whole rather than filtered in place so a
   * dismissal is reversible in principle and, more importantly, so {@link serverCount} stays a record
   * of what the server actually answered — overwriting this with the surviving rows would destroy the
   * only evidence of how many signals the officer silenced.
   */
  private readonly allSignals = signal<AssistanceSignal[]>([]);
  readonly loading = signal(false);

  /** Set only when the request did not complete. The server's own degradation arrives as a 200. */
  readonly failed = signal(false);

  /**
   * The server's `glow`, consumed and NEVER recomputed from a length — see {@link glow}, which is where
   * it is combined with dismissal.
   */
  private readonly serverGlow = signal(false);

  /** The server's own `count`, carried explicitly by the contract rather than counted here. */
  private readonly serverCount = signal(0);

  /**
   * Kinds the officer has dismissed, read from the SERVICE rather than held here.
   *
   * <p>Client-side and in memory by design: there is no endpoint for dismissal and none was invented.
   * But the scope is the SESSION ON THIS SCREEN, which is longer than this component lives — both hosts
   * render the panel inside `@if (showAssistancePanel())`, so closing the drawer destroys it. A set held
   * in a field here would be discarded on close and the host's bulb, which survives, would light again
   * over rows the officer had just silenced. That is the "never re-glow for the same payload" rule and
   * the "trains officers to ignore it" failure in one, so the set lives where both the panel and the
   * bulb can see it. See {@link AssistanceRailService.dismissals}.
   *
   * <p>Keyed on `kind` and not on the signal object, because `kind` is the stable machine key and
   * because the brief dismisses a suggestion TYPE, not an instance: an officer who does not want to be
   * told about closure times does not want to be told again when the number changes.
   */
  private readonly dismissedKinds = computed(() =>
    this.service.dismissedKinds(this.complaintNumber())
  );

  /** Which complaint the cached answer belongs to, so a changed input re-reads rather than lying. */
  private loadedFor = '';
  private loaded = false;

  /**
   * The server's answer with this officer's dismissals applied, from the SERVICE's single rule.
   *
   * <p>Everything the panel shows is read off this, and the host's bulb reads the same function — see
   * {@link AssistanceRailService.verdict} for why the rule may not be written down twice.
   */
  private readonly verdict = computed(() =>
    this.service.verdict(this.complaintNumber(), {
      complaintNumber: this.complaintNumber(),
      glow: this.serverGlow(),
      signals: this.allSignals(),
      count: this.serverCount()
    })
  );

  /**
   * What survives dismissal — the only list the panel renders and the only one that counts.
   *
   * <p>Derived rather than destructive so {@link allSignals} stays the server's answer verbatim.
   */
  readonly railSignals = computed(() => this.verdict().signals);

  /** Whether the bulb should be lit. The rule itself is the service's; see {@link verdict}. */
  readonly glow = computed(() => this.verdict().glow);

  /** The count the panel's pill shows: the server's own, less what this officer dismissed. */
  readonly count = computed(() => this.verdict().count);

  /** How many of the server's signals this officer has hidden. Drives the "all dismissed" state. */
  readonly dismissedCount = computed(() => this.verdict().dismissed);

  readonly state = computed<RailState>(() => {
    if (this.loading()) return 'loading';
    if (this.failed()) return 'failed';
    if (!this.loaded) return 'idle';
    if (this.railSignals().length > 0) return 'signals';
    // Nothing left to show. WHICH nothing matters: the server having had nothing to say is a normal
    // result, whereas the officer having dismissed everything it did say is their own doing and must not
    // be reported back to them as "nothing to flag".
    return this.dismissedCount() > 0 ? 'dismissed' : 'empty';
  });

  /** Per-user continuity. Rendered first: it is the half the officer stands to lose. */
  readonly tier0 = computed(() => this.railSignals().filter(s => s.tier === 0));

  /** Precomputed aggregate priors. Anything the server tiers beyond 0 lands here rather than vanishing. */
  readonly tier1 = computed(() => this.railSignals().filter(s => s.tier !== 0));

  /**
   * The sentence read out when the rail resolves, or `''` for "say nothing".
   *
   * <p>A plain signal written once per resolution rather than a `computed`, because an `aria-live`
   * region announces every mutation of its content: a computed would be re-read on each change
   * detection and any flicker in its inputs would re-announce the same sentence to a screen-reader
   * user. {@link announce} is the only writer and it writes on resolution only.
   *
   * <p>Why a live region at all: the brief is explicit that glow cannot be the only signal, since
   * colour and animation alone fail WCAG and fail the reduced-motion preference the citizen app already
   * honours. The visible count pill is the second channel for a sighted user; this is the second channel
   * for everyone else.
   */
  readonly announcement = signal('');

  /** Guards {@link announcement} against re-announcing one resolution. */
  private announcedFor = '';

  constructor() {
    effect(() => {
      // ONE effect for BOTH triggers — see the class doc. Two effects would both pass the `loaded`
      // guard synchronously for the common host that opens a panel it was already probing.
      const wanted = this.open() || this.probe();
      const complaint = this.complaintNumber();
      if (!wanted) return;
      if (this.loaded && this.loadedFor === complaint) return;
      if (!complaint.trim()) {
        // No complaint to ask about. Reported as an empty rail rather than an error, which is also what
        // the server does: it does not arbitrate whether a complaint exists.
        this.applyEmpty(complaint);
        return;
      }
      this.load(complaint);
    });

    // Focus the heading on open so a keyboard user lands inside the panel instead of tabbing back
    // through the page. The heading carries tabindex="-1" for exactly this.
    effect(() => {
      if (!this.open()) return;
      const el = this.heading()?.nativeElement;
      if (el) setTimeout(() => el.focus());
    });

    // The live-region write. Gated on `open()` because an announcement is only owed to someone looking
    // at the panel — a host merely probing for its bulb has not asked the rail to say anything, and
    // announcing a count for a panel that is not on screen would be an interruption with nothing behind
    // it.
    effect(() => {
      if (!this.open()) {
        // Cleared on close, and the token cleared WITH it. Both halves matter: an `aria-live` region
        // announces mutations, so leaving the old sentence in place would make the next open a no-op
        // assignment that announces nothing — and keeping the token would then suppress the re-write
        // that would have fixed it, leaving a reopened panel silent for a screen-reader user.
        this.announcement.set('');
        this.announcedFor = '';
        return;
      }
      const state = this.state();
      // Nothing to say about a request still in flight. `polite` already means the region waits its
      // turn; announcing "loading" as well would be two interruptions for one answer.
      if (state === 'loading' || state === 'idle') return;
      // The guard that stops this firing on every change detection: the sentence is a function of
      // complaint + outcome + count, so re-running the effect for an unrelated signal read — or for the
      // `pure: false` translate pipe's churn — recomputes the same token and returns.
      const token = `${this.complaintNumber()}|${state}|${this.count()}`;
      if (this.announcedFor === token) return;
      this.announcedFor = token;
      this.announce(state);
    });
  }

  /** Re-reads the rail. Offered from the failed and empty states alike. */
  retry(): void {
    const complaint = this.complaintNumber();
    if (!complaint.trim()) return;
    this.load(complaint);
  }

  /**
   * Silences one signal KIND for the rest of this session on this screen.
   *
   * <p>Per the brief: "if a user dismisses a suggestion type, stop offering it on that screen for the
   * session. Never re-glow for the same payload." Both halves fall out of one set — the row stops
   * rendering because {@link railSignals} filters on it, and the bulb stops glowing because
   * {@link glow} requires a surviving signal. There is no second mechanism and no re-glow timer to get
   * wrong.
   *
   * <p>Deliberately NOT written to the server. No endpoint exists for dismissal; inventing one, or
   * stuffing the set into localStorage so it silently outlived the session, would both be a policy
   * decision — how long a dismissal lasts, whether it follows the officer to another machine — that
   * nobody has made. In-memory for the tab is the conservative reading of "for the session".
   *
   * <p>Delegated to the service, which is what makes it outlive this component — the hosts destroy the
   * panel on close, and a dismissal that died with it would let the bulb re-glow. See
   * {@link dismissedKinds}.
   *
   * <p>Focus returns to the heading because dismissing DESTROYS the button that was clicked, which
   * drops focus to `<body>`. The Escape handler sits on the region and only fires through bubbling,
   * so without this, silencing a signal silently disables Escape and leaves a keyboard user unable to
   * close the panel — verified in a browser, and invisible in a screenshot.
   */
  dismiss(kind: string): void {
    this.service.dismiss(this.complaintNumber(), kind);
    const el = this.heading()?.nativeElement;
    if (el) setTimeout(() => el.focus());
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.closePanel.emit();
    }
  }

  /** PrimeIcons class. An unrecognised kind gets a neutral one rather than borrowing another's meaning. */
  iconFor(kind: string): string {
    return KIND_ICONS[kind] ?? 'pi-info-circle';
  }

  /**
   * The i18n key for a signal's heading, looked up and never concatenated — see {@link KIND_LABEL_KEYS}
   * for why a mechanical key is a hazard rather than a shortcut.
   *
   * <p>Returns `''` for an unlisted kind. The template only reaches this arm when
   * {@link usesServerText} is false, and an empty key can never be a hit in the bundle, so an unknown
   * kind still ends up on the server-English path instead of printing an invented key.
   */
  labelKeyFor(kind: string): string {
    return KIND_LABEL_KEYS[kind] ?? '';
  }

  /**
   * The interpolation variables for {@link labelKeyFor}'s key.
   *
   * <p>`params` is the server's own placeholder map — `{section}`, `{age}`, `{count, clause}`,
   * `{days, sample}` — and is treated as POSSIBLY ABSENT rather than merely empty. The DTO normalises it
   * to `{}`, but the RUNNING backend predates that field and omits it entirely (verified against the
   * live payload), so all three of absent, empty and populated have to work and none may be indexed
   * unguarded. `count` is then folded in only when `params` did not already carry it, so
   * a server that starts sending a formatted count wins over the raw number; it is stringified because
   * it arrives as a JSON number from a Java `Long` and `translate` substitutes strings.
   *
   * <p>Never null — the template hands the result straight to the translate pipe.
   */
  labelParams(s: AssistanceSignal): Record<string, string> {
    const params: Record<string, string> = { ...(s.params ?? {}) };
    if (params['count'] === undefined && s.count !== null && s.count !== undefined) {
      params['count'] = String(s.count);
    }
    return params;
  }

  /**
   * The server's already-resolved English `title` — THE FALLBACK OF RECORD, which the template marks
   * `lang="en"` because it is English whatever the officer's locale.
   *
   * <p>Two cases reach it, and both are live rather than theoretical:
   * <ul>
   *   <li>the `assistance.signal.*` key is missing from the bundle — either not seeded in the officer's
   *       locale, or spelled differently from what `KIND_LABEL_KEYS` asks for;
   *   <li>the key resolved but `params` lacks a value it names, leaving a `{{placeholder}}` in the
   *       sentence. This is the case that fires on the DEPLOYED backend today, which sends no `params`
   *       field at all, so every key carrying a placeholder falls back here.
   * </ul>
   *
   * <p>The real fix for both is seeding the keys, not changing this method: server English is a worse
   * locale but a complete sentence an officer can act on, whereas a bare key or a raw `{{count}}` is
   * not. `''` rather than null when `title` is somehow absent, so no "undefined" reaches the screen.
   */
  serverTextFallback(s: AssistanceSignal): string {
    return s.title ?? '';
  }

  /**
   * The officer's OWN unsaved text, or null.
   *
   * <p>Non-null only for `unsaved-draft`, where the server puts a flattened ≤120-character preview of
   * what the officer typed. That is not server prose, so it renders verbatim in any locale and must
   * NEVER be translated — it is the entire point of the signal.
   *
   * <p>Read from `detail`, and from `detail` ALONE. Verified against the server: this kind is built
   * through `Signal.memory`'s four-argument overload so its `params` is the empty map, there is no
   * `PARAM_PREVIEW` constant beside `PARAM_SECTION`/`PARAM_AGE`/etc., and the DTO states it outright
   * ("`unsaved-draft` — empty; the preview is `detail`"). A `params['preview']` read ahead of this one
   * can therefore never hit on any payload the server can emit — it would read as the live source while
   * `detail` silently did the work, so the day the two disagreed nobody would know which was authority.
   *
   * <p>Keyed on `kind`, never on `detail != null`: `detail` is populated for most kinds with meanings
   * this row would misrepresent — an ISO timestamp for `last-viewed`, English sample-size prose for the
   * tier-1 kinds, which would then render inside the quotation marks the stylesheet puts around this
   * element as if the officer had written it.
   */
  draftPreview(s: AssistanceSignal): string | null {
    if (s.kind !== DRAFT_KIND) return null;
    const preview = s.detail?.trim();
    return preview ? preview : null;
  }

  /**
   * The ISO-8601 timestamp for `last-viewed`, for the `date` pipe to format in the officer's locale.
   *
   * <p>Deliberately `detail` and not the `age` in `params`: `age` is a resolved English phrase ("3 days
   * ago") that exists so a client can rebuild the server's SENTENCE, and printing it here would put
   * English into a localised row that has a machine-readable value available.
   *
   * <p>Null for every other kind, which is the guard that matters: `detail` is a bare
   * `LocalDateTime.toString()` for this kind alone, and returning it unconditionally would leak a raw
   * ISO string into another signal's row where it means something else entirely.
   *
   * <p>Returned as the STRING and not a `Date`, because `DatePipe` parses an ISO-8601 value carrying no
   * offset — which `LocalDateTime.toString()` is, `2026-10-05T14:30:00` — as local time, the same
   * reading the server intended. But it is parse-CHECKED first and null'd if it fails: `DatePipe`
   * throws `Unable to convert "..." into a date` rather than degrading, so an unexpected `detail` on
   * this kind would take the whole rail down over a line the officer could have done without.
   */
  lastViewedAt(s: AssistanceSignal): string | null {
    if (s.kind !== LAST_VIEWED_KIND) return null;
    const at = s.detail?.trim();
    if (!at) return null;
    return Number.isNaN(new Date(at).getTime()) ? null : at;
  }

  /**
   * Whether this row takes the emphasised treatment — a CLASS and nothing more.
   *
   * <p>True for `next-action` only. The brief permits highlighting and forbids auto-selecting, so the
   * emphasis is the whole of what this kind gets: the row stays the same `<li>` of prose as every other
   * tier-1 row, with no control, no `link` and no output a host could wire to a transition. See
   * {@link NEXT_ACTION_KIND} for why that is a correctness requirement and not restraint — the
   * frequency it reports is historical and the workflow may now refuse the action it names.
   */
  isHighlighted(s: AssistanceSignal): boolean {
    return s.kind === NEXT_ACTION_KIND;
  }

  /**
   * ══ THE SINGLE SITE THAT DECIDES LOCALISED-VS-SERVER-ENGLISH ══
   *
   * <p>True when localisation did not actually happen, so the row must print
   * {@link serverTextFallback} instead of the pipe's output. It asks that question directly of
   * {@link resolve} rather than inferring it from component state, because the only reliable evidence
   * that a key resolved is the string it resolved to — and a pipe in a template cannot inspect its own
   * output, which is why this is a method and not a pipe.
   */
  usesServerText(s: AssistanceSignal): boolean {
    return this.resolve(s) === null;
  }

  /**
   * Translates a signal's heading and JUDGES THE RESULT: the localised string, or null meaning
   * "localisation did not happen, use the server's English".
   *
   * <p>This is the one place the localised-vs-server-English rule lives. {@link usesServerText} is a
   * thin question asked of it, and the template renders the winning arm — so the rule cannot drift
   * between the decision and the rendering.
   *
   * <p>Three failure shapes, all live rather than theoretical:
   * <ul>
   *   <li>NO KEY. An unmapped kind — a seventh signal added server-side — never gets an invented key;
   *   <li>KEY ECHOED BACK. {@link TranslationService.translate} returns the key itself when it is
   *       missing from the bundle, which is the case that fires for every signal until the
   *       `assistance.*` namespace is seeded;
   *   <li>A SURVIVING `{{placeholder}}`. The key is seeded but `params` lacked a value it names.
   *       `translate` substitutes only the parameters it is given and leaves the rest in place, so a
   *       half-interpolated sentence is a SUCCESSFUL lookup that must still be rejected. This is the
   *       shape the deployed backend produces today: it sends no `params` field at all, so a key
   *       needing `{{days}}` cannot be filled and the panel correctly falls back to server English.
   * </ul>
   */
  private resolve(s: AssistanceSignal): string | null {
    return this.resolveKey(this.labelKeyFor(s.kind), this.labelParams(s));
  }

  /**
   * The rule itself, lifted off {@link AssistanceSignal} so the three keys this component introduced —
   * which have no server-provided prose behind them — are judged by exactly the same test as the signal
   * headings instead of a second, looser one.
   *
   * @param key    an `assistance.*` key, or `''` for "there is no key", which can never be a hit.
   * @param params the values the key's `{{placeholder}}` slots need.
   * @return the localised string, or null meaning "localisation did not happen".
   */
  private resolveKey(key: string, params?: Record<string, string>): string | null {
    if (!key) return null;
    const text = this.i18n.translate(key, params);
    if (!text || text === key || text.includes('{{')) return null;
    return text;
  }

  /**
   * One of this component's OWN keys, with an English sentence as the fallback of record.
   *
   * <p>The signal headings fall back to the server's English `title`. These three keys have no server
   * counterpart — they describe the panel's own chrome, not a signal — so the English lives here
   * instead, and the same rule applies: a complete English sentence beats `assistance.dismiss` or a raw
   * `{{count}}` on an officer's screen. {@link UI_TEXT_FALLBACK} is checked in so the keys being unseeded
   * degrades the panel's locale and nothing else; the real fix is seeding them, not changing this.
   *
   * <p>This matters more here than for the seeded keys the template still renders through the pipe
   * directly: those are known to exist in the bundle, whereas these are new and the seeder is owned by
   * another change, so the unseeded window is real rather than hypothetical.
   */
  uiText(key: string, params?: Record<string, string>): string {
    return this.resolveKey(key, params) ?? this.interpolate(UI_TEXT_FALLBACK[key] ?? '', params);
  }

  /**
   * `{{name}}` substitution for {@link UI_TEXT_FALLBACK}, matching what
   * {@link TranslationService.translate} does to a bundle value — otherwise the fallback for a key with
   * a placeholder would print the literal `{{count}}` the rule above exists to reject.
   *
   * <p>Global flag and an escaped key, both copied from `TranslationService.translate` rather than
   * reinvented: a non-global replace substitutes only the FIRST occurrence (the recorded defect there
   * was prose naming `{{days}}` twice and shipping the second raw to a citizen), and the key is
   * interpolated into a regex so a metacharacter in it would otherwise be live pattern syntax.
   */
  private interpolate(text: string, params?: Record<string, string>): string {
    if (!params) return text;
    return Object.entries(params).reduce(
      (acc, [k, v]) =>
        acc.replace(new RegExp(`\\{\\{${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\}\\}`, 'g'), v),
      text
    );
  }

  /**
   * Writes the live-region sentence for a resolved rail.
   *
   * <p>Called once per resolution from the constructor's third effect, never from a `computed`: an
   * `aria-live` region re-announces on every content mutation, so a sentence recomputed each change
   * detection would repeat itself at a screen-reader user for as long as the panel stayed open.
   *
   * <p>`loading` and `idle` never reach here — there is nothing to announce about a request still in
   * flight, and the region is `polite` precisely so it waits its turn rather than interrupting.
   */
  private announce(state: RailState): void {
    const text = this.announcementTextFor(state);
    // Deferred so the write is a MUTATION of a region already in the DOM. With `probe` the answer
    // usually arrives before the panel mounts, and a region whose text was present at mount announces
    // nothing at all — see ANNOUNCE_DELAY_MS.
    setTimeout(() => this.announcement.set(text), ANNOUNCE_DELAY_MS);
  }

  /**
   * The sentence per resolved state. A switch rather than a ternary chain: `sonarjs/no-nested-conditional`
   * is an error in this repo, and four outcomes read better as four cases anyway.
   *
   * <p>`failed` and `empty` REUSE the keys the visible panel already prints rather than minting
   * announcement-only variants, so the sentence a screen reader hears is the sentence on screen — and
   * because each of those would otherwise need translating into 13 locales to say the same thing twice.
   * `signals` and `dismissed` get their own keys because the panel says those two visually (a count pill,
   * a muted block) rather than in words there is anything to read out.
   */
  private announcementTextFor(state: RailState): string {
    switch (state) {
      case 'signals':
        return this.uiText('assistance.announce_signals', { count: String(this.count()) });
      case 'dismissed':
        return this.uiText('assistance.all_dismissed');
      case 'failed':
        return this.resolveKey('assistance.error_failed') ?? 'The assistance rail did not load.';
      default:
        return this.resolveKey('assistance.nothing_to_report') ?? 'Nothing to flag on this complaint.';
    }
  }

  /**
   * The one read, through {@link AssistanceRailService.railCached} rather than `rail`.
   *
   * <p>`railCached` is what makes the bulb free: the host's probe and the panel's first open are the
   * same question, and `shareReplay(1)` with `refCount: false` means the second of them costs no
   * request at all. With the uncached `rail` the normal host — probe for the bulb, then open when the
   * officer clicks it — issued two identical GETs per complaint, the second landing on a screen already
   * showing the first one's answer.
   *
   * <p>{@link retry} going through the cache too is correct, though it needed checking: `shareReplay`
   * defaults to `resetOnError: true`, so a failed read tears its own replay buffer down and the next
   * subscriber re-subscribes to the source — the retry button issues a real request rather than
   * replaying the failure forever. A SUCCESSFUL answer does persist, which is the point, and the service
   * offers `invalidate()` for the case that needs a forced re-read.
   */
  private load(complaint: string): void {
    this.loading.set(true);
    this.failed.set(false);
    this.service.railCached(complaint).subscribe({
      next: res => {
        this.allSignals.set(res?.signals ?? []);
        // The server's answer, not `signals.length > 0`. See the `glow` getter for how dismissal is
        // then ANDed onto it, and why that can only ever turn glowing off.
        this.serverGlow.set(res?.glow ?? false);
        this.serverCount.set(res?.count ?? 0);
        this.loaded = true;
        this.loadedFor = complaint;
        this.loading.set(false);
      },
      error: () => {
        // A 200 with an empty list is the server having nothing to say; reaching here means the request
        // itself did not complete, which is a different thing and is shown as one.
        this.allSignals.set([]);
        this.serverGlow.set(false);
        this.serverCount.set(0);
        this.failed.set(true);
        this.loaded = true;
        this.loadedFor = complaint;
        this.loading.set(false);
      }
    });
  }

  private applyEmpty(complaint: string): void {
    this.allSignals.set([]);
    this.serverGlow.set(false);
    this.serverCount.set(0);
    this.failed.set(false);
    this.loaded = true;
    this.loadedFor = complaint;
  }
}
