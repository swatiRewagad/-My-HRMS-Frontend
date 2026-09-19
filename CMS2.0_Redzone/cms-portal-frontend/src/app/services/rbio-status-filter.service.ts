import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

/** One selectable filter, exactly as RBIO_STATUS_MASTER defines it. */
export interface RbioStatusFilter {
  statusCode: string;
  label: string;
  translationKey: string;
  /** STATUS (a real complaint status), QUEUE (a routing position), or SCOPE (a predicate on the caller). */
  filterKind: 'STATUS' | 'QUEUE' | 'SCOPE' | string;
  milestone: string | null;
  isDefault: boolean;
  /** The legacy lowercase COMPLAINTS.status value, when this filter maps to one. */
  legacyValue?: string | null;
}

/**
 * Reads the caller's permitted status filters from the server (UST426-433).
 *
 * <h2>Why this service exists rather than a constant</h2>
 * The RBIO home grid and the staff task list between them hardcoded FIVE status option lists that
 * disagreed with each other and with the database. Four of the codes they offered — DRAFT, SENT_BACK,
 * ASSESSMENT_COMPLETE, AWAITING_RESPONSE — do not exist in RBIO_STATUS_MASTER, so choosing them reached
 * the server's unknown-code branch and returned zero rows while looking like a working filter.
 *
 * The backend endpoint that solves this has existed since Wave 0 and had no callers at all.
 *
 * <h2>Fails closed</h2>
 * There is no compiled-in fallback list. If the server cannot say which statuses a role may see, the
 * honest answer is "no status filter is available", not a guess — UST433 requires that statuses outside
 * the role's list are not selectable, and a local copy of a server-owned vocabulary cannot honour that.
 */
@Injectable({ providedIn: 'root' })
export class RbioStatusFilterService {

  private http = inject(HttpClient);

  /**
   * The RBIO rank roles in precedence order.
   *
   * The four ranks are checked BEFORE the two legacy roles. A Dealing Official who also carries the
   * legacy RBIO_OFFICER role must be treated as a Dealing Official, because that is the role whose
   * filter list and queue the stories describe. The component previously searched only the legacy names
   * and could not recognise any of the four ranks at all.
   */
  private static readonly ROLE_PRECEDENCE = [
    'RBIO_ADMIN',
    'RBIO_OMBUDSMAN',
    'RBIO_DEPUTY_OMBUDSMAN',
    'RBIO_REVIEWER',
    'RBIO_DEALING_OFFICIAL',
    'RBIO_CONCILIATOR',
    'RBIO_ADJUDICATOR',
    'RBIO_SUPERVISOR',
    'RBIO_OFFICER'
  ];

  /**
   * The RBIO role to request filters for.
   *
   * Returns an empty string when the token carries no RBIO role, rather than defaulting to
   * RBIO_OFFICER. Guessing a role would show somebody a queue they do not hold; the server then returns
   * no filters, which is the correct outcome for a caller with no RBIO rank.
   */
  primaryRbioRole(roles: string[] | null | undefined): string {
    if (!roles?.length) return '';
    return RbioStatusFilterService.ROLE_PRECEDENCE.find(r => roles.includes(r)) ?? '';
  }

  /**
   * The filters this role may use, in DISPLAY_ORDER.
   *
   * The role is sent explicitly when known; the server resolves it from the token otherwise. It is not a
   * privilege escalation to pass it — the server uses it only to pick a filter list, and every actual
   * data query is scoped by the token regardless of what is asked for here.
   */
  filtersFor(role: string | null | undefined): Observable<RbioStatusFilter[]> {
    let params = new HttpParams();
    if (role) params = params.set('role', role);

    return this.http
      .get<any>(`${environment.apiBaseUrl}/api/v1/rbio/status-filters`, { params })
      .pipe(map(res => {
        const data = res?.data ?? res;
        const filters = data?.filters ?? [];
        return Array.isArray(filters) ? filters as RbioStatusFilter[] : [];
      }));
  }

  /** The sortable column whitelist the server enforces, so the UI does not offer a sort it will ignore. */
  sortableFields(role: string | null | undefined): Observable<string[]> {
    let params = new HttpParams();
    if (role) params = params.set('role', role);

    return this.http
      .get<any>(`${environment.apiBaseUrl}/api/v1/rbio/status-filters`, { params })
      .pipe(map(res => {
        const data = res?.data ?? res;
        const fields = data?.sortableFields ?? [];
        return Array.isArray(fields) ? fields as string[] : [];
      }));
  }
}
