import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * One thing the rail has to say. These are EXACTLY the fields `AssistanceRailResponse.Signal`
 * serialises — the Java side is a `record` rather than the hand-built `LinkedHashMap` its neighbours
 * use, precisely so this shape stays a contract. `detail`, `count` and `link` are emitted as `null`
 * rather than omitted, so they are nullable here and not optional: `count === null` is the server
 * saying "this signal is not a count", which is a different statement from "the field is missing".
 */
export interface AssistanceSignal {
  /**
   * 0 for per-user continuity, 1 for a precomputed prior. There is no tier 2 and no scaffolding for
   * one — a signal that needed inference to decide whether it was worth saying could not be answered
   * in the milliseconds a screen load can spare. Do not add a third group to a renderer.
   */
  tier: number;

  /**
   * STABLE MACHINE KEY, one of seven: `unsaved-draft`, `last-section`, `last-viewed` (tier 0);
   * `complainant-history`, `entity-clause-precedent`, `category-closure-time`, `next-action` (tier 1).
   * Declared as constants in `AssistanceRailService` on the server for this reason. The client's icon
   * AND its i18n lookup both hang off this value, so it is the only field a renderer may branch on.
   *
   * <p>`next-action` is the one kind whose rendering is CONSTRAINED rather than merely configured. It
   * names the action that most often followed in comparable past cases — a historical frequency, not a
   * check of what the workflow now permits — so it renders as read-only prose with no control and a
   * `link` that is always null. See `AssistanceRailComponent.isHighlighted`.
   */
  kind: string;

  /**
   * Short display text, ALREADY RESOLVED TO ENGLISH on the server.
   *
   * <p>This is the FALLBACK OF RECORD, not a field the renderer may skip. The rail UI prefers a
   * localised string built from `kind` + {@link params} through the translate pipe, because the product
   * ships 13 locales and this field is English whatever the officer's locale. But when the
   * `assistance.*` key is missing from the locale bundle, or {@link params} lacks a value the key's
   * `{{placeholder}}` needs, the UI renders THIS instead: server English beats a raw `{{count}}` or a
   * bare key on an officer's screen. Verified relevant rather than theoretical — the `assistance.*`
   * namespace is not yet present in `GET /api/v1/i18n/translations/en`, so today this field is what
   * actually renders.
   */
  title: string;

  /**
   * Longer text, or null. NOT uniformly prose: for `unsaved-draft` it is a flattened 120-character
   * preview of the officer's OWN text (safe to render verbatim — it is not server English), and for
   * `last-viewed` it is a bare `LocalDateTime.toString()`, i.e. a machine-readable ISO-8601 local
   * timestamp. The tier-1 kinds put English sample-size prose here, which is why the renderer does not
   * print this field unconditionally.
   */
  detail: string | null;

  /** The number the signal is about, or null when it is not a count. Always set for tier 1. */
  count: number | null;

  /**
   * A RELATIVE app route, or null. Never absolute — an absolute URL from the server would be an
   * open-redirect surface.
   *
   * <p>Only `complainant-history` populates it, as `/search?complainantEmail=...`, and that link is
   * HALF-DEAD: the `search` route exists in `app.routes.ts`, but `search.component.ts` reads no query
   * parameters at all (no `ActivatedRoute`, no `queryParams`, no `snapshot`), so the filter is dropped
   * and the officer lands on an unfiltered search. The rail therefore does not render this as a
   * navigable link. Fixing it belongs to the search screen, not here.
   */
  link: string | null;

  /**
   * Named values for the `{{placeholder}}` slots in the i18n key chosen from {@link kind} — e.g.
   * `{section}` for `last-section`, `{days, sample}` for `category-closure-time`.
   *
   * <p>OPTIONAL, and that is load-bearing rather than defensive typing: the Java record
   * `AssistanceRailResponse.Signal` does NOT declare this field today (verified against the live
   * payload on 8082, which emits exactly tier/kind/title/detail/count/link), and it is being added by a
   * concurrent change. Every consumer must therefore work when it is absent, `null`, or present but
   * short of a value its key needs — which is the whole reason {@link title} is kept as a fallback
   * instead of localising unconditionally.
   */
  params?: Record<string, string> | null;
}

export interface AssistanceRail {
  complaintNumber: string;

  /**
   * True iff `signals` is non-empty. DERIVED SERVER-SIDE on purpose and must be consumed, never
   * recomputed from `signals.length`: the DTO documents that keeping the two together is what stops
   * "glowing" and "has content" from drifting apart, and a rail that glows with nothing behind it
   * trains officers to ignore it — the one failure mode that makes the feature worthless rather than
   * merely imperfect.
   */
  glow: boolean;

  signals: AssistanceSignal[];
  count: number;
}

/**
 * What the bulb and the panel each need to know, after the officer's dismissals have been applied.
 *
 * <p>Exists so the two cannot disagree. The bulb lives on the host and the panel is destroyed every time
 * the drawer closes, so they are necessarily separate components reading the same answer — and a glow
 * rule written out twice is a rule that drifts. {@link AssistanceRailService.verdict} is the only place
 * it is computed.
 */
export interface AssistanceVerdict {
  /**
   * The SERVER's `glow` ANDed with "something survived dismissal". The server half is consumed and
   * never recomputed from a length — it is derived server-side precisely so "glowing" and "has content"
   * cannot drift apart — so this can only ever turn glowing OFF, never on.
   */
  glow: boolean;

  /** The server's own `count` less what this officer dismissed, floored at zero. */
  count: number;

  /** The rows that survived dismissal — the only ones a renderer may show. */
  signals: AssistanceSignal[];

  /** How many of the server's signals this officer silenced. Distinguishes "dismissed" from "empty". */
  dismissed: number;
}

/**
 * Returned for a complaint with no dismissals. One frozen instance rather than a fresh `new Set()` per
 * call, so a `computed` reading it does not see a new reference — and therefore recompute — on every
 * change detection.
 */
const EMPTY_KINDS: ReadonlySet<string> = new Set<string>();

/** The memory write's answer. `success` is the SERVER's verdict and the client may not invent one. */
export interface AssistanceMemoryResult {
  success: boolean;
}

/**
 * The one client for the assistance rail (Brief 21).
 *
 * <h2>The caller is resolved, never declared</h2>
 * Neither method takes a user id, and neither ever may. Both endpoints derive the officer from
 * `RequestIdentityResolver` on the server (token-authoritative; `X-User-*` headers honoured only under
 * dev-local). Tier 0 is one officer's unsaved work keyed on that resolved principal — an owner the
 * caller could name would be no owner at all, and the repository deliberately has no finder that omits
 * it. A `userId` parameter added here would be an identity-spoofing surface, not a convenience.
 *
 * <h2>Both CONTROLLERS always answer HTTP 200, so this service still catches nothing</h2>
 * Modelled on {@link import('./similar-cases.service').SimilarCasesService}: no `catchError`, so a
 * TRANSPORT failure stays an error the caller can show. That is not redundant with the server's own
 * guards. The server degrades a rail it cannot compute to `glow: false` with an empty list — because
 * `GlobalExceptionHandler` maps a bare `RuntimeException` to 400 and a leaked failure would present as
 * a client error on a valid request — which means an empty rail is a NORMAL state arriving as a 200.
 * Those two must not be collapsed: the sibling similar-cases panel exists because an error branch that
 * set an empty list made a non-existent endpoint read as "nothing found" for months.
 *
 * <p>"Always 200" is true of the controllers and NOT of the endpoints, which is a distinction worth
 * keeping straight: `RateLimitFilter` gives `/api/v1/assistance/**` its own smaller bucket per Brief 21
 * §6.2, and an exhausted bucket answers **429 before the controller is reached**. So the error branch
 * now has two reachable causes — the request not completing, and the rail being throttled — and the
 * component treats both the same way, which is correct. A 429 here means the officer is reloading far
 * faster than a person works, the next read succeeds, and the component's `retry()` already re-requests
 * rather than replaying the failure. It is deliberately NOT mapped to an empty list: that is the exact
 * collapse named above.
 */
@Injectable({ providedIn: 'root' })
export class AssistanceRailService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/assistance`;

  /** Bound on {@link railCached}, so a long session working a queue cannot grow this without limit. */
  private static readonly MAX_CACHED = 20;

  private railCache = new Map<string, Observable<AssistanceRail>>();

  /** The kill switch is one process-wide answer, so it is cached once and not per complaint. */
  private statusCache?: Observable<boolean>;

  /**
   * What the rail has to say about one complaint, to the calling officer.
   *
   * @param complaintNumber the complaint on screen. Goes on the wire as `complaintId`, which is the
   *                        parameter name the controller binds even though the value is a complaint
   *                        NUMBER (`CMS-20260601-A1B2C3`) and not a numeric id.
   */
  rail(complaintNumber: string): Observable<AssistanceRail> {
    return this.http.get<AssistanceRail>(`${this.baseUrl}/rail`, {
      params: { complaintId: complaintNumber }
    });
  }

  /**
   * The same read as {@link rail}, but shared: the host's bulb and the panel it opens are asking one
   * question, so they must not be two requests.
   *
   * <p>The bulb has to know whether to glow BEFORE the officer opens anything, which is the whole
   * interaction — a bulb that only lights once you look behind it signals nothing. But the panel also
   * reads on first open, so a naive implementation issues two identical GETs per complaint, and the
   * second one lands on a screen already showing the first one's answer.
   *
   * <p>`shareReplay(1)` with `refCount: false` is what makes the second caller free: the response is
   * replayed from cache for the life of the entry, so opening the panel after the bulb has already
   * answered costs no request at all. `refCount: false` is deliberate — with it true the cache would be
   * dropped the moment the bulb's subscription ended, which is exactly when the panel is about to ask.
   *
   * <p>Keyed per complaint number and {@link MAX_CACHED} entries at most. Unbounded would be a leak on
   * a long-lived SPA: an officer working a queue visits dozens of complaints without a reload.
   */
  railCached(complaintNumber: string): Observable<AssistanceRail> {
    const key = complaintNumber.trim();
    const hit = this.railCache.get(key);
    if (hit) return hit;

    const shared = this.rail(key).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    if (this.railCache.size >= AssistanceRailService.MAX_CACHED) {
      // Oldest first: Map preserves insertion order, so the first key is the least recently added.
      const oldest = this.railCache.keys().next();
      if (!oldest.done) this.railCache.delete(oldest.value);
    }
    this.railCache.set(key, shared);
    return shared;
  }

  /**
   * ══ DISMISSAL LIVES HERE, NOT IN THE PANEL, AND THAT IS NOT A PREFERENCE ══
   *
   * <p>The brief requires that a dismissed suggestion type stop being offered "on that screen for the
   * session" and that the bulb NEVER re-glow for the same payload. Both hosts render the panel inside
   * `@if (showAssistancePanel())`, so closing it DESTROYS the component — any dismissal held in a
   * component field dies with it, and the bulb (which outlives the panel) would light again over rows
   * the officer had just silenced. That is precisely the "trains officers to ignore it" failure, and it
   * would have been invisible in a screenshot: the panel looks correct both before and after.
   *
   * <p>Keeping the panel alive with `[hidden]` instead was the alternative and is the wrong trade here:
   * the rail holds nothing the officer can type into, so there is no unsaved state to protect (the
   * opposite of the assessment fields, which is why THOSE are hidden rather than destroyed), and the
   * drawer slot is shared with the similar-cases panel.
   *
   * <p>Keyed by complaint number so moving to the next complaint starts clean — a dismissal is about one
   * officer's view of one complaint, not a standing preference. Not persisted beyond the tab: a
   * dismissal that survived a reload would need a server policy on when it expires, and none exists.
   */
  private readonly dismissals = signal<ReadonlyMap<string, ReadonlySet<string>>>(new Map());

  /** The kinds this officer has silenced for `complaintNumber` this session. Never null. */
  dismissedKinds(complaintNumber: string): ReadonlySet<string> {
    return this.dismissals().get(complaintNumber.trim()) ?? EMPTY_KINDS;
  }

  /**
   * Silences one signal kind for one complaint, for the rest of this session.
   *
   * <p>The map and the inner set are both REPLACED rather than mutated: a `Set.add` on the same
   * reference leaves every `computed` reading this signal believing nothing changed, so the row would
   * stay on screen and the bulb would stay lit.
   */
  dismiss(complaintNumber: string, kind: string): void {
    const key = complaintNumber.trim();
    if (!key || !kind) return;
    const current = this.dismissals();
    const existing = current.get(key);
    if (existing?.has(kind)) return;
    const nextKinds = new Set(existing ?? []);
    nextKinds.add(kind);
    const next = new Map(current);
    next.set(key, nextKinds);
    this.dismissals.set(next);
  }

  /**
   * Server answers already probed, by complaint number — the signal-backed half of the read.
   *
   * <p>Exists for hosts that cannot embed {@link AssistanceBulbComponent}. The RBIO complaint screen is
   * the case: its icon strip belongs to the shared `app-context-rail`, whose DOM is encapsulated, so the
   * glow has to be passed INTO that component as data rather than rendered by a component of ours. Such
   * a host needs the answer as a signal it can read from a `computed`, not as an Observable it would
   * have to subscribe to and mirror into a field — and every host that mirrored it separately would be
   * another copy of the rule.
   *
   * <p>The HTTP is still {@link railCached}, so a host using this and a bulb on the same screen share
   * one request.
   */
  private readonly probed = signal<ReadonlyMap<string, AssistanceRail>>(new Map());

  /** Complaints whose probe is in flight or done, so {@link probeRail} cannot issue a second GET. */
  private probing = new Set<string>();

  /**
   * Starts a probe for one complaint, if one has not already been started. Fire-and-forget: the answer
   * lands in {@link probed} and is read through {@link probedRail}.
   *
   * <p>Idempotent because a caller will be an `effect` that re-runs for unrelated reasons.
   *
   * <p>Failures are SWALLOWED, per §5.1: assistance is off the critical path and must fail silently, so
   * a dead rail means no glow and no message. Nobody asked for this read, so nobody is owed an error
   * about it — the deliberate opposite of the user-initiated search path.
   */
  probeRail(complaintNumber: string): void {
    const key = complaintNumber.trim();
    if (!key || this.probing.has(key)) return;
    this.probing.add(key);
    this.railCached(key).subscribe({
      next: rail => this.probed.update(current => new Map(current).set(key, rail)),
      error: () => {
        // Left absent rather than stored as an empty rail: absent reads as "nothing known", which is
        // what a failed probe actually means, and keeps the door open for a retry.
        this.probing.delete(key);
      }
    });
  }

  /** The probed answer for one complaint, or null if it has not arrived (or did not). */
  probedRail(complaintNumber: string): AssistanceRail | null {
    return this.probed().get(complaintNumber.trim()) ?? null;
  }

  /**
   * ══ THE ONE PLACE THE GLOW RULE LIVES ══
   *
   * <p>Applies this officer's dismissals to a server answer. Both the host's bulb and the panel call it,
   * which is the point: they are separate components by necessity (the panel is destroyed on close, the
   * bulb is not), and the brief's rule — glow only when there is something genuinely there, and never
   * again for a payload the officer has dismissed — must be ONE rule. Written out in both places it
   * would drift, and the half that drifted would be the bulb, because a bulb that is wrong looks
   * identical to a bulb that is right.
   *
   * <p>`null` in means not yet read, which is dark and empty rather than an error.
   */
  verdict(complaintNumber: string, rail: AssistanceRail | null): AssistanceVerdict {
    const hidden = this.dismissedKinds(complaintNumber);
    const all = rail?.signals ?? [];
    const signals = all.filter(s => !hidden.has(s.kind));
    const dismissed = all.length - signals.length;
    return {
      // The server's verdict as the CEILING. `serverGlow` false with surviving rows stays dark, so the
      // server's confidence floor remains authoritative.
      glow: (rail?.glow ?? false) && signals.length > 0,
      // The server's `count`, not a row tally: it is the contract's authority on how many things it had
      // to say. Floored so an arithmetic surprise cannot print a negative badge.
      count: Math.max(0, (rail?.count ?? 0) - dismissed),
      signals,
      dismissed
    };
  }

  /**
   * Drops the cached answer for one complaint, so the next read goes to the server.
   *
   * <p>Needed because the rail describes state the officer themselves changes: a tier-0 memory write
   * makes the cached rail stale, and an officer who types, navigates away and comes back would
   * otherwise be shown the pre-write answer and conclude the feature does not work.
   */
  invalidate(complaintNumber: string): void {
    this.railCache.delete(complaintNumber.trim());
  }

  /**
   * Whether the assistance feature is switched on server-side (the §6.2 kill switch).
   *
   * <p>Degrades to `false` — unavailable — on any transport or server failure, which is the safe
   * direction for assistance specifically: the brief requires the frontend to honour the switch by
   * HIDING the affordance rather than showing a broken one, so an unreachable server must read as
   * "no bulb", never as a bulb that errors when clicked. This is the deliberate opposite of the
   * user-initiated search path, which must say so when it fails.
   */
  available(): Observable<boolean> {
    if (!this.statusCache) {
      this.statusCache = this.http
        .get<{ available: boolean }>(`${this.baseUrl}/status`)
        .pipe(
          map(res => res?.available === true),
          catchError(() => of(false)),
          shareReplay({ bufferSize: 1, refCount: false })
        );
    }
    return this.statusCache;
  }

  /**
   * Records where the officer was and what they left behind, as they leave a screen (the tier-0 write).
   *
   * <p>`section` and `draftText` are sent even when null, and the server overwrites with null on
   * purpose: "the draft box is now empty" is a fact worth recording, and treating null as "leave the
   * previous value alone" would keep offering to restore text the officer has already saved or
   * deliberately cleared.
   *
   * <p>A caller must report `success` as the server returned it and must not assume a write happened —
   * a false here is usually an unresolved identity. The inverse bug is on record: a component that set
   * `draftSaved(true)` inside its error handler showed a confirmation for a save that never occurred.
   */
  rememberVisit(
    complaintNumber: string,
    section?: string | null,
    draftText?: string | null
  ): Observable<AssistanceMemoryResult> {
    const body = {
      complaintNumber,
      section: section ?? null,
      draftText: draftText ?? null
    };
    return this.http.put<AssistanceMemoryResult>(`${this.baseUrl}/rail/memory`, body);
  }
}
