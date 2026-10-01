import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * One precedent hit. These are EXACTLY the fields `/api/v1/similar-cases/search` returns and nothing
 * more: the server narrows `_source` to identifiers on purpose, so one complaint's free text never
 * reaches another complaint's screen. Do not widen this interface speculatively — a field that is not
 * in the server's `_source` list arrives `undefined` and renders as a blank row.
 */
export interface SimilarCase {
  complaintNumber: string;
  subject: string | null;
  status: string | null;
  /** The category id, not its name. The index stores the id so a rename needs no reindex. */
  categoryId: number | null;
  createdAt: string | null;
  /**
   * The raw Elasticsearch `_score`. UNBOUNDED — it is not a 0..1 similarity and must never be
   * rendered as a percentage. The three screens this replaced all did `score * 100 + '%'`, which
   * invented a confidence figure the search engine never claimed.
   */
  score: number | null;
}

export interface SimilarCasesResult {
  /** `elasticsearch` when a provider served the query, `none` when none was available. */
  provider: string;
  count: number;
  results: SimilarCase[];
}

export interface SimilarCasesStatus {
  provider: string;
  available: boolean;
}

/**
 * The one client for similar-case lookup.
 *
 * <h2>Why this exists at all</h2>
 * Three screens hand-rolled this against three different endpoints:
 * `GET /api/v1/complaints/{id}/similar` (staff/task-action) was never implemented at all — the route
 * does not exist in any controller, and the caller's error branch set an empty list, so a dead endpoint
 * presented itself as "no similar cases found" for months. `POST /api/v1/past-complaints/similar`
 * (crpc/draft-assessment) exists but is a Groq LLM call, which Brief 21 puts out of scope. Both now
 * come here.
 *
 * <h2>Degradation is visible, not silent</h2>
 * This service does not catch anything. A transport failure stays an error so the caller can SHOW an
 * error, and `provider === 'none'` is reported verbatim so "search is down" can be told apart from
 * "nothing matched". That distinction is the whole defect that hid the dead endpoint.
 */
@Injectable({ providedIn: 'root' })
export class SimilarCasesService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/similar-cases`;

  /**
   * @param text      complaint subject plus description. Blank text yields no results server-side.
   * @param categoryId a CATEGORY ID. The server filters on the indexed `categoryId` term, so a
   *                   category NAME would match nothing and look like a genuine no-match. Anything
   *                   non-numeric is therefore dropped rather than sent — see the callers, where
   *                   `/api/v1/complaints/{n}` exposes only the resolved category name and so passes
   *                   nothing at all.
   */
  search(text: string, categoryId?: string | number | null, maxResults = 5): Observable<SimilarCasesResult> {
    // The endpoint binds @RequestBody Map<String, String>, so every value goes on the wire as a string.
    const body: Record<string, string> = { text, maxResults: String(maxResults) };
    const category = categoryId === null || categoryId === undefined ? '' : String(categoryId).trim();
    if (/^\d+$/.test(category)) {
      body['category'] = category;
    }
    return this.http.post<SimilarCasesResult>(`${this.baseUrl}/search`, body);
  }

  /** Whether a search provider is reachable. Both routes are staff-only; a CITIZEN identity gets 403. */
  status(): Observable<SimilarCasesStatus> {
    return this.http.get<SimilarCasesStatus>(`${this.baseUrl}/status`);
  }
}
