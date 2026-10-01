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
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { SimilarCase, SimilarCasesService } from '../../../services/similar-cases.service';

/** What the panel is currently showing. Four states, because three of them used to look identical. */
type PanelState = 'idle' | 'loading' | 'results' | 'empty' | 'unavailable' | 'failed';

/**
 * The one "find similar cases" panel.
 *
 * <h2>Three half-implementations, three endpoints, one working feature between them</h2>
 * `staff/task-action` called `GET /api/v1/complaints/{id}/similar`, which **no controller implements**.
 * Its error branch did `similarCases.set([])`, so a route that answered 404 on every single request
 * rendered as "No similar cases found" and the feature looked merely unhelpful rather than broken.
 * `crpc/draft-assessment` called `POST /api/v1/past-complaints/similar`, which does exist but is a Groq
 * LLM prompt with a keyword fallback — out of scope for Brief 21. Both now call
 * `POST /api/v1/similar-cases/search` through {@link SimilarCasesService}.
 *
 * <h2>An error must LOOK like an error</h2>
 * This is the point of the component. `empty` (the search ran and matched nothing), `unavailable`
 * (no search provider is configured or reachable — the server says `provider: "none"`) and `failed`
 * (the request itself did not complete) are three visually distinct states with three different
 * messages. Collapsing them into one empty list is the exact defect that hid a dead endpoint for
 * months, so it is not reintroduced for tidiness.
 *
 * <h2>Nothing is fetched until the panel opens</h2>
 * `open` is an input the host drives from its own toggle. The first time it goes true a search fires;
 * afterwards the result is cached until `text` changes, so re-opening the panel is free. The component
 * renders nothing at all while closed, so a host that never opens it pays no DOM.
 *
 * <h2>Identifiers only, deliberately</h2>
 * The API narrows `_source` to complaintNumber/subject/status/categoryId/createdAt so that one
 * complaint's free text cannot leak onto another's screen. This template therefore shows no body,
 * no complainant and no amount. The raw `_score` is NOT rendered as a percentage: the three old copies
 * all printed `score * 100 + '%'`, inventing a confidence number Elasticsearch never claimed.
 */
@Component({
  selector: 'app-similar-cases',
  standalone: true,
  imports: [DatePipe, TranslatePipe, StatusBadgeComponent],
  templateUrl: './similar-cases.component.html',
  styleUrl: './similar-cases.component.scss'
})
export class SimilarCasesComponent {

  private service = inject(SimilarCasesService);

  /** Host-driven. Opening it for the first time triggers the search; closing it renders nothing. */
  readonly open = input(false);

  /**
   * Subject plus description of the complaint on screen. Changing it invalidates the cache, so a CRPC
   * officer who rewrites the subject and re-opens the panel gets a fresh search rather than the
   * previous draft's precedents.
   */
  readonly text = input('');

  /**
   * A category ID. Optional. The server filters on the indexed `categoryId` term, so a category NAME
   * matches nothing and would present as a genuine no-match — the service drops any non-numeric value
   * rather than sending it.
   */
  readonly categoryId = input<string | number | null>(null);

  readonly maxResults = input(5);

  /** Renders each result as a button. Off by default: a button that does nothing traps keyboard users. */
  readonly selectable = input(false);

  /** Shows the panel's own close affordance. Off for a host that supplies its own chrome. */
  readonly showClose = input(true);

  readonly close = output<void>();
  readonly selectCase = output<SimilarCase>();

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  results = signal<SimilarCase[]>([]);
  loading = signal(false);
  /** Set only on a transport failure. Distinct from an empty result on purpose. */
  failed = signal(false);
  /** The server's own answer: "elasticsearch", or "none" when no provider could serve the query. */
  provider = signal<string | null>(null);

  /** The text the cached results belong to, so a changed complaint re-searches rather than lying. */
  private searchedText = '';
  private searched = false;

  readonly state = computed<PanelState>(() => {
    if (this.loading()) return 'loading';
    if (this.failed()) return 'failed';
    if (!this.searched) return 'idle';
    if (this.results().length > 0) return 'results';
    return this.provider() === 'none' ? 'unavailable' : 'empty';
  });

  constructor() {
    effect(() => {
      // Reading both signals keeps this reactive to a mid-session subject edit as well as to the open
      // toggle; the guard is what makes it lazy rather than a fetch on screen load.
      const isOpen = this.open();
      const text = this.text();
      if (!isOpen) return;
      if (this.searched && this.searchedText === text) return;
      if (!text.trim()) {
        // Nothing to search on. Reported as an empty result rather than an error: the officer has
        // simply not written a subject yet, and the server would return nothing anyway.
        this.searched = true;
        this.searchedText = text;
        this.results.set([]);
        this.provider.set(null);
        this.failed.set(false);
        return;
      }
      this.runSearch(text);
    });

    // Focus the heading when the panel opens so a keyboard user lands inside it rather than having to
    // tab back through the whole page. The heading carries tabindex="-1" for exactly this.
    effect(() => {
      if (!this.open()) return;
      const el = this.heading()?.nativeElement;
      if (el) setTimeout(() => el.focus());
    });
  }

  /** Re-runs the current search. Offered from the failed, empty and unavailable states alike. */
  retry(): void {
    const text = this.text();
    if (!text.trim()) return;
    this.runSearch(text);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.close.emit();
    }
  }

  private runSearch(text: string): void {
    this.loading.set(true);
    this.failed.set(false);
    this.service.search(text, this.categoryId(), this.maxResults()).subscribe({
      next: res => {
        this.results.set(res?.results ?? []);
        this.provider.set(res?.provider ?? null);
        this.searched = true;
        this.searchedText = text;
        this.loading.set(false);
      },
      error: () => {
        // NOT set([]). An empty list here is what made a non-existent endpoint look like a working one.
        this.results.set([]);
        this.provider.set(null);
        this.failed.set(true);
        this.searched = true;
        this.searchedText = text;
        this.loading.set(false);
      }
    });
  }
}
