import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * One thing the officer's QUEUE has to say. Exactly the fields `AssistanceQueueResponse.Signal`
 * serialises.
 *
 * <p>Note what is NOT here and cannot be: there is no `complaintNumber` and no `tier`. The absence is the
 * entire reason this type exists beside {@link import('./assistance-rail.service').AssistanceSignal}
 * rather than reusing it — a queue fact keyed against one complaint would be read as a statement about
 * that complaint, which is the misreading the separate endpoint was built to prevent.
 */
export interface AssistanceQueueSignal {
  /**
   * STABLE MACHINE KEY. One value today, `queue-deadline-triage`, and the hyphens are load-bearing: the
   * i18n key is spelled from this verbatim by `AssistanceQueueTranslationSeeder`, and a mismatch is
   * SILENT — `translate` echoes the key back, this client reads that as "not localised" and prints the
   * server's English, which looks exactly like a merely-untranslated panel.
   *
   * <p>Deliberately NOT the same string as the per-complaint rail's `deadline-triage`. The two are
   * different kinds in different namespaces answering different questions, and collapsing them would
   * make one renderer's icon map pick a glyph for the other's sentence.
   */
  kind: string;

  /**
   * Short display text, ALREADY RESOLVED TO ENGLISH on the server — "3 of your 14 cases breach within
   * 48h".
   *
   * <p>The FALLBACK OF RECORD, not a field a renderer may skip. Preferred output is the localised string
   * built from `kind` + {@link params}; this is what renders when the key is unseeded in the officer's
   * locale or when a `{{placeholder}}` survives interpolation. Server English is a worse locale but a
   * complete sentence; a bare key or a raw `{{count}}` is neither.
   */
  title: string;

  /**
   * The overdue qualifier as English prose, or null when nothing is overdue.
   *
   * <p>Null and not "0 already overdue": the server suppresses the clause at zero for the same reason it
   * suppresses the whole signal at zero breaching. The NUMBER is still in `params['overdue']`.
   */
  detail: string | null;

  /**
   * The BREACHING count — the numerator, the "3". Never the queue size.
   *
   * <p>Equal to `params['count']` by construction on the server, which sends both rather than relying on
   * a client fold. See {@link AssistanceQueueService.labelParams} for why that redundancy is deliberate.
   */
  count: number | null;

  /** A RELATIVE app route. Never absolute — an absolute URL from the server is an open-redirect surface. */
  link: string | null;

  /**
   * Values for the `{{placeholder}}` slots of the key chosen from {@link kind}: `count`, `total`,
   * `hours` and `overdue`, all four always present together.
   *
   * <p>Optional on the wire anyway. The record normalises it to `{}`, but a client that indexed it
   * unguarded would break against any build that predates the field — the rail's own client carries that
   * scar, so this one is written the same way from the start.
   */
  params?: Record<string, string> | null;
}

/** The queue endpoint's whole payload. */
export interface AssistanceQueue {
  /**
   * True iff `signals` is non-empty. DERIVED SERVER-SIDE and must be CONSUMED, never recomputed from
   * `signals.length`: keeping the two together server-side is what stops "glowing" and "has content"
   * from drifting apart, and a banner that announces itself with nothing behind it trains officers to
   * ignore it — the one failure that makes an ambient affordance worthless rather than imperfect.
   */
  glow: boolean;

  signals: AssistanceQueueSignal[];
  count: number;
}

/**
 * The queue answer after this officer's dismissals. Mirrors the rail's verdict shape for the same
 * reason: two components read one answer, and a rule written twice drifts.
 */
export interface AssistanceQueueVerdict {
  /** The server's `glow` ANDed with "something survived dismissal". Can only ever turn glowing OFF. */
  glow: boolean;

  /** The server's own `count` less what this officer dismissed, floored at zero. */
  count: number;

  /** The rows that survived dismissal — the only ones a renderer may show. */
  signals: AssistanceQueueSignal[];

  /** How many the officer silenced. Distinguishes "dismissed" from "the queue had nothing to say". */
  dismissed: number;
}

/** One frozen empty set, so a `computed` reading it does not recompute on a new reference each pass. */
const EMPTY_KINDS: ReadonlySet<string> = new Set<string>();

/**
 * i18n key per stable `kind`, in the `assistance.queue.*` namespace.
 *
 * <p>HARDCODED, not `'assistance.queue.signal.' + kind`. The mechanical form would mint a
 * plausible-looking key for a kind nobody has translated, and `translate` renders a missing key as the
 * key itself — so a second server-side kind would ship `assistance.queue.signal.new-thing` into an
 * officer's screen as if it were prose. An unlisted kind gets no key, which routes it to the server's own
 * English instead. The seeder's javadoc names this map as the authority on spelling.
 */
const QUEUE_LABEL_KEYS: Record<string, string> = {
  'queue-deadline-triage': 'assistance.queue.signal.queue-deadline-triage'
};

/**
 * The one client for `GET /api/v1/assistance/queue` (Brief 21 §5.3 item 4).
 *
 * <h2>Why a second service rather than a method on `AssistanceRailService`</h2>
 * Not tidiness. Every read on that service is keyed by complaint number — the cache, the dismissals, the
 * probe map, the verdict — because every answer it carries is about ONE complaint. This endpoint takes no
 * complaint and its answer is about the officer's whole queue, so every one of those keys would have to
 * be given a sentinel value, and a sentinel in a cache keyed by complaint number is a bug waiting for the
 * day a complaint is named that. The dismissal SCOPE differs for the same reason: dismissing a queue
 * alert must silence it across every screen the officer visits, whereas dismissing a rail signal silences
 * it for one complaint.
 *
 * <h2>The caller is resolved, never declared</h2>
 * This method takes NO parameters and never may. The server derives the officer from
 * `RequestIdentityResolver`; a `userId` argument here would be an identity-spoofing surface, and the
 * defect is on record in this codebase — `EmailSyndicationApiController:451` returned every row in the
 * system when its client-supplied owner was omitted.
 *
 * <h2>ONE request per app, not per screen</h2>
 * `shareReplay(1)` with `refCount: false`, cached on the service rather than per component. The queue
 * answer is the same on every screen the officer opens, and this is a service with app lifetime, so four
 * hosts cost one GET. That is also what keeps the 60/min assistance bucket out of the picture: without
 * it, an officer moving between complaints would re-ask a question whose answer cannot have changed.
 *
 * <p>{@link refresh} exists for the case that genuinely invalidates it — the officer acting on a case,
 * which changes their own queue — and is deliberately a separate call rather than a TTL: a timer would
 * re-announce the same sentence to a screen-reader user at intervals nobody chose.
 *
 * <h2>Failures are SWALLOWED here, unlike the rail's</h2>
 * A difference worth justifying, because it contradicts a sibling. The rail's service deliberately does
 * NOT catch, so a transport failure stays an error its panel can show — the officer opened that panel and
 * is owed an answer. Nobody opens this: it is ambient, nobody asked for it, and §5.1 puts it off the
 * critical path. So a failed read leaves the signal at null, which renders as nothing at all, and there
 * is no error state to design because there is no one to show it to.
 */
@Injectable({ providedIn: 'root' })
export class AssistanceQueueService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/assistance`;

  private queueCache?: Observable<AssistanceQueue>;

  /** The kill switch is one process-wide answer, so it is read once and not per host. */
  private statusCache?: Observable<boolean>;

  /** Null until the read resolves. A FAILED read leaves it null, which is silent. */
  private readonly answer = signal<AssistanceQueue | null>(null);

  /** Set once the read has been started, so {@link probe} cannot issue a second GET. */
  private probing = false;

  /**
   * ══ DISMISSAL LIVES HERE, NOT IN THE BANNER, AND THAT IS NOT A PREFERENCE ══
   *
   * <p>The brief requires a dismissed suggestion type to stop being offered for the session and the
   * affordance never to re-glow for the same payload. The banner is rendered inside an `@if`, so it is
   * DESTROYED whenever it stops being shown — and a dismissal held in a component field dies with it, so
   * the alert would reappear on the officer's next screen. Invisible in a screenshot: the banner looks
   * correct both before and after.
   *
   * <p>NOT keyed by complaint, which is the one real difference from the rail's dismissal set. This fact
   * is about the officer's whole queue, so silencing it on the complaint screen must also silence it on
   * the dashboard — a per-complaint key would make the same alert reappear on the next screen and read as
   * a dismissal that did not work.
   *
   * <p>In memory for the tab only. Persisting it would need a policy on when a dismissal expires, and
   * since the underlying fact is a DEADLINE, a dismissal that outlived the session would hide a breach
   * the officer silenced yesterday. Nobody has made that call, so the conservative reading of "for the
   * session" stands.
   */
  private readonly dismissals = signal<ReadonlySet<string>>(EMPTY_KINDS);

  /**
   * Whether assistance is switched on server-side (§6.2).
   *
   * <p>Degrades to `false` on any failure, which is the safe direction for an ambient affordance: the
   * brief requires the frontend to honour the switch by HIDING the affordance, so an unreachable server
   * must read as "no banner" and never as a banner that errors when touched.
   */
  available(): Observable<boolean> {
    if (!this.statusCache) {
      this.statusCache = this.http
        .get<{ available: boolean }>(`${this.baseUrl}/status`)
        .pipe(
          // `=== true` and not a truthiness check: a 200 carrying an empty body, which is what a
          // misconfigured gateway returns, would otherwise read as `undefined` and then as "on" under
          // any looser test. The switch must fail CLOSED.
          map(res => res?.available === true),
          catchError(() => of(false)),
          shareReplay({ bufferSize: 1, refCount: false })
        );
    }
    return this.statusCache;
  }

  /**
   * The raw read, shared across every caller for the life of the app.
   *
   * <p>`refCount: false` is deliberate: with it true the replay buffer would be dropped the moment the
   * last subscriber unsubscribed, which on these hosts is every navigation — so the next screen would
   * re-ask. The answer is a queue-wide count that does not change between two screens of the same
   * session, so replaying it is correct rather than merely cheap.
   */
  queue(): Observable<AssistanceQueue> {
    if (!this.queueCache) {
      this.queueCache = this.http
        .get<AssistanceQueue>(`${this.baseUrl}/queue`)
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.queueCache;
  }

  /**
   * Starts the read if it has not been started. Fire-and-forget: the answer lands in a signal.
   *
   * <p>Idempotent because callers are `effect`s that re-run for unrelated reasons. Gated by the caller on
   * the kill switch, so a disabled feature costs no request at all.
   *
   * <p>A failure leaves {@link answer} null rather than storing an empty queue. Absent reads as "nothing
   * known", which is what a failed read means; an empty object would read as "the queue is quiet", which
   * is a claim this client has no evidence for.
   */
  probe(): void {
    if (this.probing) return;
    this.probing = true;
    this.queue().subscribe({
      next: res => this.answer.set(res),
      error: () => {
        this.answer.set(null);
        // Reset so a later host can retry. The shareReplay's own resetOnError default means that retry
        // reaches the server rather than replaying the failure.
        this.probing = false;
      }
    });
  }

  /**
   * Forces the next read to reach the server.
   *
   * <p>Exists because the queue describes state the officer themselves changes: closing a case or
   * extending a deadline makes the cached count stale, and an officer who acted and saw the same number
   * would conclude the feature does not work. Dismissals are deliberately NOT cleared — the officer
   * silenced the KIND, not the instance, and re-offering it because the number moved is precisely the
   * "never re-glow for the same payload" rule being broken.
   */
  refresh(): void {
    this.queueCache = undefined;
    this.probing = false;
    this.probe();
  }

  /** The kinds this officer has silenced this session. Never null. */
  dismissedKinds(): ReadonlySet<string> {
    return this.dismissals();
  }

  /**
   * Silences one kind for the rest of the session, on every screen.
   *
   * <p>The set is REPLACED rather than mutated: a `Set.add` on the same reference leaves every `computed`
   * reading this signal believing nothing changed, so the banner would stay on screen.
   */
  dismiss(kind: string): void {
    if (!kind) return;
    const current = this.dismissals();
    if (current.has(kind)) return;
    const next = new Set(current);
    next.add(kind);
    this.dismissals.set(next);
  }

  /**
   * ══ THE ONE PLACE THE GLOW RULE LIVES ══
   *
   * <p>Applies dismissals to the server's answer. Every renderer reads this, which is the point: the
   * brief's rule — show it only when there is something genuinely there, and never again for a payload
   * the officer dismissed — must be ONE rule, and the copy that drifted would be the one the officer
   * sees, because a wrong banner looks exactly like a right one.
   *
   * <p>`null` in means not yet read: silent and empty, not an error.
   */
  verdict(): AssistanceQueueVerdict {
    const queue = this.answer();
    const hidden = this.dismissals();
    const all = queue?.signals ?? [];
    const signals = all.filter(s => !hidden.has(s.kind));
    const dismissed = all.length - signals.length;
    return {
      // The server's verdict as the CEILING: `glow: false` with surviving rows stays dark, so the
      // server's own floors (queue size ≥ 3, breaching ≥ 1) remain authoritative and this can only
      // ever subtract.
      glow: (queue?.glow ?? false) && signals.length > 0,
      // The server's `count`, not a row tally — it is the contract's authority on how much it had to
      // say. Floored so an arithmetic surprise cannot print a negative badge.
      count: Math.max(0, (queue?.count ?? 0) - dismissed),
      signals,
      dismissed
    };
  }

  /**
   * The i18n key for a signal, looked up and never concatenated. See {@link QUEUE_LABEL_KEYS}.
   *
   * <p>`''` for an unlisted kind, which can never be a hit in the bundle, so an unknown kind lands on
   * the server-English path rather than printing an invented key.
   */
  labelKeyFor(kind: string): string {
    return QUEUE_LABEL_KEYS[kind] ?? '';
  }

  /**
   * The interpolation values for {@link labelKeyFor}'s key.
   *
   * <p>`count` is folded in from the FIELD only when `params` did not already carry it. The server sends
   * both and sends them equal, so this fold is normally a no-op — and that redundancy is the deliberate
   * guard: the `{{count}}` placeholder in all thirteen locales means the NUMERATOR, so a payload whose
   * `count` field held the denominator would render "14 of 14 cases breach" in every locale while the
   * English `title` stayed correct. A bug invisible to anyone testing in English.
   *
   * <p>`params` is spread with a `?? {}` guard rather than indexed: absent, null and populated must all
   * work, because a client that assumed the field would break against any build predating it.
   */
  labelParams(s: AssistanceQueueSignal): Record<string, string> {
    const params: Record<string, string> = { ...(s.params ?? {}) };
    if (params['count'] === undefined && s.count !== null && s.count !== undefined) {
      params['count'] = String(s.count);
    }
    return params;
  }
}
