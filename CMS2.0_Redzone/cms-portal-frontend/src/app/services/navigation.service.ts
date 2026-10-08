import { Injectable, inject, signal, computed } from '@angular/core';
import {
  Router,
  NavigationEnd,
  NavigationStart,
  NavigationCancel,
  NavigationCancellationCode,
  NavigationError,
  NavigationExtras,
  CanDeactivateFn
} from '@angular/router';
import { filter, map } from 'rxjs/operators';
import { Observable } from 'rxjs';

/**
 * A single entry in the internal navigation history stack.
 */
export interface NavigationHistoryEntry {
  /** The full URL path including query string and fragment. */
  url: string;
  /** Epoch timestamp of when navigation occurred. */
  timestamp: number;
  /** Optional state object passed via NavigationExtras.state. */
  state?: Record<string, unknown>;
}

/**
 * A route segment: either a string path or a numeric ID parameter.
 *
 * @example
 * // String segments
 * const route: RouteSegment[] = ['/rbio', 'complaint', '123'];
 *
 * // Numeric ID parameter
 * const route: RouteSegment[] = ['/rbio/complaint', 456];
 */
export type RouteSegment = string | number;

const MAX_HISTORY_SIZE = 25;

/**
 * Centralized navigation service that wraps Angular's Router with
 * history-aware back navigation, state passing, dirty-state guards,
 * and reactive signals for current/previous URL tracking.
 *
 * ## Why this exists
 * Components across the app hardcode `this.router.navigate(['/some-path'])`
 * in their `goBack()` methods. This breaks when a component is reachable
 * from multiple parent routes — the user should return to where they
 * actually came from, not a hardcoded destination.
 *
 * ## How it works
 * The service subscribes to `Router.events` and maintains an internal
 * history stack (bounded to 25 entries). `goBack()` pops from this stack
 * and navigates to the actual previous URL, falling back to a provided
 * default route when no history exists (deep link, bookmark, first visit).
 *
 * ## Usage
 * ```typescript
 * private navService = inject(NavigationService);
 *
 * goBack() {
 *   this.navService.goBack(['/rbio']);
 * }
 * ```
 */
@Injectable({ providedIn: 'root' })
export class NavigationService {

  private readonly router = inject(Router);

  // ── Internal mutable state ────────────────────────────────────────

  private readonly _currentUrl = signal<string>('/');
  private readonly _previousUrl = signal<string | null>(null);
  private readonly _historyStack = signal<NavigationHistoryEntry[]>([]);
  private readonly _isNavigating = signal<boolean>(false);
  private readonly _dirtyComponents = signal<Set<string>>(new Set());
  private _isGoingBack = false;

  // ── Public read-only signals ──────────────────────────────────────

  /**
   * The current URL after the latest successful navigation.
   * Updates on every `NavigationEnd` event.
   *
   * @example
   * ```html
   * <span>Current: {{ navService.currentUrl() }}</span>
   * ```
   */
  readonly currentUrl = this._currentUrl.asReadonly();

  /**
   * The URL the user was on before the current page, or `null` when
   * there is no prior navigation history (first page load, deep link).
   *
   * @example
   * ```typescript
   * if (this.navService.previousUrl()) {
   *   console.log('Came from:', this.navService.previousUrl());
   * }
   * ```
   */
  readonly previousUrl = this._previousUrl.asReadonly();

  /**
   * The full bounded navigation history stack, newest entry first.
   * Maximum 25 entries are retained to prevent memory leaks.
   *
   * @example
   * ```typescript
   * const history = this.navService.navigationHistory();
   * console.log('Last 5 pages:', history.slice(0, 5).map(e => e.url));
   * ```
   */
  readonly navigationHistory = computed<readonly NavigationHistoryEntry[]>(
    () => [...this._historyStack()]
  );

  /**
   * The current URL decomposed into path segments, useful for
   * building breadcrumb navigation.
   * Query parameters and fragments are stripped.
   *
   * @example
   * ```typescript
   * // URL: /rbio/complaint/123?tab=summary#details
   * // Returns: ['rbio', 'complaint', '123']
   * const segments = this.navService.currentRoutePath();
   * ```
   */
  readonly currentRoutePath = computed<string[]>(() => {
    const url = this._currentUrl();
    return url.split('?')[0].split('#')[0].split('/').filter(Boolean);
  });

  /**
   * `true` while a navigation is in progress (between `NavigationStart`
   * and `NavigationEnd`/`NavigationCancel`/`NavigationError`).
   * Useful for showing loading indicators.
   *
   * @example
   * ```html
   * <p-progressBar *ngIf="navService.isNavigating()" mode="indeterminate" />
   * ```
   */
  readonly isNavigating = this._isNavigating.asReadonly();

  /**
   * `true` when one or more components have registered unsaved changes
   * via `registerDirtyState()`. Navigation methods will prompt the user
   * for confirmation before proceeding.
   *
   * @example
   * ```typescript
   * if (this.navService.hasDirtyState()) {
   *   console.warn('There are unsaved changes');
   * }
   * ```
   */
  readonly hasDirtyState = computed(() => this._dirtyComponents().size > 0);

  /**
   * Observable that emits the URL string on every successful navigation.
   * Prefer the `currentUrl` signal for template bindings.
   * Use with `takeUntilDestroyed()` for component subscriptions.
   *
   * @example
   * ```typescript
   * this.navService.navigationEnd$
   *   .pipe(takeUntilDestroyed(this.destroyRef))
   *   .subscribe(url => console.log('Navigated to:', url));
   * ```
   */
  readonly navigationEnd$: Observable<string> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map(e => e.urlAfterRedirects)
  );

  constructor() {
    this.trackRouterEvents();
  }

  // ── Navigation Methods ────────────────────────────────────────────

  /**
   * Navigates to the actual previous page from the internal history stack.
   * If no history exists (user bookmarked or deep-linked to the current page),
   * navigates to the provided fallback route instead.
   *
   * Before navigating, checks for unsaved changes and prompts the user
   * via `window.confirm()` if dirty state is registered.
   *
   * @param fallbackRoute - Route segments to use when history is empty.
   * @param fallbackExtras - Optional NavigationExtras for the fallback navigation.
   * @returns Promise resolving to `true` if navigation succeeded, `false` if
   *          cancelled by the user or navigation failed.
   *
   * @example
   * ```typescript
   * // Basic usage — falls back to /rbio when no history
   * goBack() {
   *   this.navService.goBack(['/rbio']);
   * }
   *
   * // With query params on fallback
   * goBack() {
   *   this.navService.goBack(['/rbio'], { queryParams: { tab: 'pending' } });
   * }
   * ```
   */
  goBack(fallbackRoute: RouteSegment[], fallbackExtras?: NavigationExtras): Promise<boolean> {
    if (!this.confirmDirtyNavigation()) {
      return Promise.resolve(false);
    }

    const stack = this._historyStack();

    // Stack has at least 2 entries: current page + previous page
    if (stack.length >= 2) {
      this._isGoingBack = true;
      const previousEntry = stack[stack.length - 2];

      // Pop current page off the stack
      this._historyStack.set(stack.slice(0, -1));

      return this.router.navigateByUrl(previousEntry.url);
    }

    // No meaningful history — use fallback route
    return this.router.navigate(
      fallbackRoute.map(s => s),
      fallbackExtras
    );
  }

  /**
   * Navigates to the specified route segments. Thin wrapper around
   * `Router.navigate()` that additionally checks for unsaved changes.
   *
   * The navigation is automatically recorded in the history stack via
   * the `Router.events` subscription — no manual tracking needed.
   *
   * @param commands - Array of route segments, identical to `Router.navigate()`'s first argument.
   * @param extras - Optional `NavigationExtras` (queryParams, fragment, queryParamsHandling, etc.).
   * @returns Promise resolving to `true` if navigation succeeded.
   *
   * @example
   * ```typescript
   * // Simple navigation
   * this.navService.navigate(['/rbio/complaint', complaintId]);
   *
   * // With query params
   * this.navService.navigate(['/rbio'], {
   *   queryParams: { status: 'pending', page: 1 }
   * });
   *
   * // With fragment
   * this.navService.navigate(['/rbio/complaint', id], { fragment: 'timeline' });
   * ```
   */
  navigate(commands: RouteSegment[], extras?: NavigationExtras): Promise<boolean> {
    if (!this.confirmDirtyNavigation()) {
      return Promise.resolve(false);
    }
    return this.router.navigate(commands.map(s => s), extras);
  }

  /**
   * Navigates to an absolute URL string. Wrapper around `Router.navigateByUrl()`
   * with dirty-state confirmation.
   *
   * Use this when you have a full URL string (e.g., from a notification's
   * `actionUrl` property) rather than route segments.
   *
   * @param url - Absolute URL string (e.g., '/rbio/complaint/123?tab=summary').
   * @param extras - Optional `NavigationExtras`.
   * @returns Promise resolving to `true` if navigation succeeded.
   *
   * @example
   * ```typescript
   * // Navigate to a URL from notification
   * this.navService.navigateByUrl(notification.actionUrl);
   *
   * // Navigate with extras
   * this.navService.navigateByUrl('/rbio/complaint/123', { replaceUrl: true });
   * ```
   */
  navigateByUrl(url: string, extras?: NavigationExtras): Promise<boolean> {
    if (!this.confirmDirtyNavigation()) {
      return Promise.resolve(false);
    }
    return this.router.navigateByUrl(url, extras);
  }

  /**
   * Navigates to the specified route while passing arbitrary data via
   * `NavigationExtras.state` (stored in `history.state`).
   *
   * The state is retrievable on the target component via `getNavigationState()`.
   * State persists only for the immediate next navigation — subsequent
   * navigations overwrite it.
   *
   * ## When to use
   * - Passing transient data that shouldn't appear in the URL
   *   (e.g., a pre-loaded entity to avoid a redundant API call)
   * - Passing UI hints (e.g., which tab to activate, scroll position)
   *
   * ## When NOT to use
   * - Data that should survive page refresh → use query params instead
   * - Data that should be bookmarkable → use route params or query params
   *
   * @param commands - Array of route segments.
   * @param state - Arbitrary data object to pass to the target route.
   * @param extras - Additional `NavigationExtras` (merged; explicit state takes precedence).
   * @returns Promise resolving to `true` if navigation succeeded.
   *
   * @example
   * ```typescript
   * // Pass complaint data to avoid re-fetching
   * this.navService.navigateWithState(
   *   ['/rbio/complaint', complaint.id],
   *   { complaint, fromDashboard: true }
   * );
   *
   * // On the target component:
   * const state = this.navService.getNavigationState<{ complaint: Complaint }>();
   * if (state?.complaint) {
   *   this.complaint.set(state.complaint);
   * }
   * ```
   */
  navigateWithState(
    commands: RouteSegment[],
    state: Record<string, unknown>,
    extras?: NavigationExtras
  ): Promise<boolean> {
    if (!this.confirmDirtyNavigation()) {
      return Promise.resolve(false);
    }
    return this.router.navigate(commands.map(s => s), {
      ...extras,
      state
    });
  }

  /**
   * Retrieves the state object passed by the previous navigation via
   * `navigateWithState()`, if any.
   *
   * Must be called early in the target component's lifecycle (constructor,
   * `ngOnInit`, or `afterNextRender`) — the state is lost after the next navigation.
   *
   * Uses `Router.getCurrentNavigation()?.extras.state` first (available during
   * constructor/ngOnInit), then falls back to `window.history.state` (available
   * after navigation completes).
   *
   * @typeParam T - The expected shape of the state object.
   * @returns The state object cast to `T`, or `null` if no state was passed.
   *
   * @example
   * ```typescript
   * interface DetailState { complaint: Complaint; fromDashboard: boolean; }
   *
   * // In constructor or ngOnInit:
   * const state = this.navService.getNavigationState<DetailState>();
   * if (state?.complaint) {
   *   this.complaint.set(state.complaint);
   * }
   * ```
   */
  getNavigationState<T = Record<string, unknown>>(): T | null {
    const nav = this.router.getCurrentNavigation();
    if (nav?.extras?.state) {
      return nav.extras.state as T;
    }

    if (typeof window !== 'undefined' && window.history.state) {
      const { navigationId, ...rest } = window.history.state;
      return Object.keys(rest).length > 0 ? (rest as T) : null;
    }

    return null;
  }

  /**
   * Navigates while replacing the current URL in the browser's history stack.
   * The replaced entry will NOT appear when the user clicks the browser back button.
   *
   * Also removes the current entry from the internal history stack so that
   * `goBack()` skips over it correctly.
   *
   * ## When to use
   * - Post-login redirects (login page shouldn't appear on back-press)
   * - Wizard step corrections (replacing an invalid step with the corrected one)
   * - Normalizing URLs (e.g., redirecting `/rbio/` to `/rbio`)
   *
   * @param commands - Array of route segments.
   * @param extras - Additional `NavigationExtras` (`replaceUrl` is forced to `true`).
   * @returns Promise resolving to `true` if navigation succeeded.
   *
   * @example
   * ```typescript
   * // After successful login, replace /login with /rbio
   * this.navService.navigateAndReplace(['/rbio']);
   *
   * // Replace current wizard step
   * this.navService.navigateAndReplace(['/wizard/step-2'], {
   *   queryParams: { corrected: true }
   * });
   * ```
   */
  navigateAndReplace(commands: RouteSegment[], extras?: NavigationExtras): Promise<boolean> {
    if (!this.confirmDirtyNavigation()) {
      return Promise.resolve(false);
    }

    const stack = this._historyStack();
    if (stack.length > 0) {
      this._historyStack.set(stack.slice(0, -1));
    }

    return this.router.navigate(commands.map(s => s), {
      ...extras,
      replaceUrl: true
    });
  }

  /**
   * Forces a full reload of the current route, re-triggering guards,
   * resolvers, and component lifecycle hooks (`OnInit`, `afterNextRender`).
   *
   * Internally navigates to `/` with `skipLocationChange: true`, then
   * immediately navigates back to the current URL. This is a clean
   * abstraction over the manual pattern used elsewhere in the codebase.
   *
   * ## When to use
   * - After a bulk action that changes the data underlying the current view
   * - When you need to re-run route guards (e.g., after role changes)
   * - When `onSameUrlNavigation: 'reload'` alone isn't sufficient
   *
   * @returns Promise resolving to `true` if the reload navigation succeeded.
   *
   * @example
   * ```typescript
   * async onBulkActionComplete() {
   *   await this.navService.reloadCurrentRoute();
   * }
   * ```
   */
  reloadCurrentRoute(): Promise<boolean> {
    const url = this._currentUrl();
    return this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      return this.router.navigateByUrl(url);
    });
  }

  /**
   * Builds a URL string from route segments and optional query params
   * without performing any navigation.
   *
   * Uses Angular's `Router.createUrlTree()` and `Router.serializeUrl()`
   * internally, so the output is always consistent with the app's
   * route configuration.
   *
   * ## When to use
   * - Generating `href` values for anchor tags
   * - Building URLs for clipboard copy operations
   * - Constructing notification `actionUrl` values
   * - Analytics tracking / logging
   *
   * @param commands - Array of route segments.
   * @param queryParams - Optional key-value pairs for query string parameters.
   * @param fragment - Optional URL fragment (hash).
   * @returns The serialized URL string.
   *
   * @example
   * ```typescript
   * // Simple URL
   * const url = this.navService.buildUrl(['/rbio/complaint', '123']);
   * // Returns: '/rbio/complaint/123'
   *
   * // With query params and fragment
   * const url = this.navService.buildUrl(
   *   ['/rbio/complaint', '123'],
   *   { tab: 'timeline', highlight: 'true' },
   *   'section-2'
   * );
   * // Returns: '/rbio/complaint/123?tab=timeline&highlight=true#section-2'
   * ```
   */
  buildUrl(
    commands: RouteSegment[],
    queryParams?: Record<string, string>,
    fragment?: string
  ): string {
    const tree = this.router.createUrlTree(commands.map(s => s), {
      queryParams,
      fragment
    });
    return this.router.serializeUrl(tree);
  }

  /**
   * Registers the calling component as having unsaved ("dirty") changes.
   *
   * While any component has dirty state registered, navigation methods
   * (`goBack()`, `navigate()`, `navigateByUrl()`, `navigateAndReplace()`,
   * `navigateWithState()`) will prompt the user with a confirmation dialog
   * before proceeding.
   *
   * Returns a cleanup function that should be called when the component
   * is destroyed or the form is saved/reset. Best used with Angular's
   * `DestroyRef.onDestroy()`.
   *
   * @param componentId - A unique identifier for the calling component.
   *   Recommended: use the component's class name or a descriptive slug.
   * @returns A cleanup function that deregisters the dirty state.
   *
   * @example
   * ```typescript
   * private destroyRef = inject(DestroyRef);
   * private navService = inject(NavigationService);
   *
   * ngOnInit() {
   *   // Register dirty state when form becomes dirty
   *   const cleanup = this.navService.registerDirtyState('rbio-create-complaint');
   *
   *   // Auto-cleanup on component destroy
   *   this.destroyRef.onDestroy(cleanup);
   * }
   *
   * onSave() {
   *   // Manually clear when form is saved
   *   this.navService.clearDirtyState('rbio-create-complaint');
   * }
   * ```
   */
  registerDirtyState(componentId: string): () => void {
    this._dirtyComponents.update(set => {
      const next = new Set(set);
      next.add(componentId);
      return next;
    });

    return () => this.clearDirtyState(componentId);
  }

  /**
   * Deregisters the dirty state for the given component.
   * Once all components have cleared their dirty state, navigations
   * will proceed without confirmation prompts.
   *
   * @param componentId - The same identifier passed to `registerDirtyState()`.
   *
   * @example
   * ```typescript
   * onFormSaved() {
   *   this.navService.clearDirtyState('rbio-create-complaint');
   * }
   *
   * onFormReset() {
   *   this.navService.clearDirtyState('rbio-create-complaint');
   * }
   * ```
   */
  clearDirtyState(componentId: string): void {
    this._dirtyComponents.update(set => {
      const next = new Set(set);
      next.delete(componentId);
      return next;
    });
  }

  // ── Private Methods ───────────────────────────────────────────────

  private trackRouterEvents(): void {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        this._isNavigating.set(true);
        return;
      }

      // A cancelled or errored navigation leaves the address bar untouched, so with these events
      // discarded the only symptom is a click that appears to do nothing. The cancellation code
      // tells apart a guard returning false from a redirect, a supersede, or a lazy-chunk failure.
      if (event instanceof NavigationCancel) {
        this._isNavigating.set(false);
        console.warn(
          `[NavigationService] Navigation to "${event.url}" was cancelled`,
          {
            code: event.code === undefined ? 'unknown' : NavigationCancellationCode[event.code],
            reason: event.reason
          }
        );
        return;
      }

      if (event instanceof NavigationError) {
        this._isNavigating.set(false);
        console.error(`[NavigationService] Navigation to "${event.url}" failed`, event.error);
        return;
      }

      if (event instanceof NavigationEnd) {
        this._isNavigating.set(false);
        const url = event.urlAfterRedirects;

        if (this._isGoingBack) {
          // goBack() already popped the stack; just update current/previous signals
          this._isGoingBack = false;
          this._currentUrl.set(url);
          const stack = this._historyStack();
          this._previousUrl.set(stack.length >= 2 ? stack[stack.length - 2].url : null);
          return;
        }

        const stack = this._historyStack();
        const lastEntry = stack[stack.length - 1];

        // Deduplicate consecutive identical URLs (onSameUrlNavigation: 'reload')
        if (lastEntry && lastEntry.url === url) {
          return;
        }

        const entry: NavigationHistoryEntry = { url, timestamp: Date.now() };
        const nav = this.router.getCurrentNavigation();
        if (nav?.extras?.state) {
          entry.state = nav.extras.state as Record<string, unknown>;
        }

        let newStack = [...stack, entry];
        if (newStack.length > MAX_HISTORY_SIZE) {
          newStack = newStack.slice(newStack.length - MAX_HISTORY_SIZE);
        }

        this._historyStack.set(newStack);
        this._previousUrl.set(lastEntry?.url ?? null);
        this._currentUrl.set(url);
      }
    });
  }

  private confirmDirtyNavigation(): boolean {
    if (this.hasDirtyState()) {
      return window.confirm(
        'You have unsaved changes. Are you sure you want to leave this page?'
      );
    }
    return true;
  }
}

/**
 * Standalone functional route guard that prevents navigation when any component
 * has registered unsaved changes via `NavigationService.registerDirtyState()`.
 *
 * Add this to route configurations that need deactivation protection:
 *
 * @example
 * ```typescript
 * // In app.routes.ts
 * {
 *   path: 'rbio/create-complaint',
 *   loadComponent: () => import('./...').then(m => m.RbioCreateComplaintComponent),
 *   canDeactivate: [unsavedChangesGuard]
 * }
 * ```
 */
export const unsavedChangesGuard: CanDeactivateFn<unknown> = () => {
  const navService = inject(NavigationService);
  if (navService.hasDirtyState()) {
    return window.confirm(
      'You have unsaved changes. Are you sure you want to leave this page?'
    );
  }
  return true;
};
