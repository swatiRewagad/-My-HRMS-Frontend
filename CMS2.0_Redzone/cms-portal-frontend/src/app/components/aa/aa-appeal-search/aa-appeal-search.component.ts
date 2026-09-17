import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/** One row of the parent-complaint result grid. PII arrives already masked from the server. */
interface ParentComplaintRow {
  complaintNumber: string;
  complainantName: string | null;
  complainantEmail: string | null;
  complainantPhone: string | null;
  accountNumber: string | null;
  subject: string | null;
  status: string | null;
  workflowStage: string | null;
  closureClause: string | null;
  rbioOfficeCode: string | null;
  categoryId: number | null;
  groundOfComplaintId: number | null;
  entityCode: string | null;
  closedAt: string | null;
  reopenedAt: string | null;
  createdAt: string | null;
  appealEligible: boolean;
  piiMasked: boolean;
}

interface GroundOption {
  id: number;
  groundCode: string;
  label: string;
  labelKey: string | null;
}

interface ClauseOption {
  clauseCode: string;
  label: string;
  labelKey: string | null;
  appealableByComplainant: boolean;
  appealableByEntity: boolean;
}

interface OfficeOption {
  officeCode: string;
  officeName: string;
}

/** GET /api/categories returns a BARE array (not a {data} envelope) of complaint_categories. */
interface CategoryOption {
  id: number;
  name: string;
}

/**
 * AA parent-complaint search (stories 1-6).
 *
 * Serves AA_DO and AA_REVIEWER identically (story 4) and RE Principal Nodal Officers (story 5) — the
 * scoping difference is entirely server-side, resolved from the caller's token. This screen therefore
 * does NOT branch on role: a client-side narrowing would be both duplicated and bypassable, and the
 * backend already refuses cross-entity parents and restricts a PNO to appeal-eligible parents.
 *
 * Every filter is optional and they combine (story 3). The server rejects a filterless search rather
 * than returning the whole national complaint table.
 */
@Component({
  selector: 'app-aa-appeal-search',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './aa-appeal-search.component.html',
  styleUrl: './aa-appeal-search.component.scss'
})
export class AaAppealSearchComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaintNumber = signal('');
  appellantName = signal('');
  appellantMobile = signal('');
  appellantEmail = signal('');
  rbioOfficeCode = signal('');
  closureClause = signal('');
  categoryId = signal<number | null>(null);
  groundOfComplaintId = signal<number | null>(null);

  results = signal<ParentComplaintRow[]>([]);
  grounds = signal<GroundOption[]>([]);
  clauses = signal<ClauseOption[]>([]);
  offices = signal<OfficeOption[]>([]);
  categories = signal<CategoryOption[]>([]);

  loading = signal(false);
  /** True once a search has actually run, so "no results" is distinguishable from "not searched yet". */
  searched = signal(false);
  loadError = signal(false);
  errorMessageKey = signal<string | null>(null);
  totalElements = signal(0);

  hasAnyFilter = computed(() =>
    this.complaintNumber().trim() !== '' ||
    this.appellantName().trim() !== '' ||
    this.appellantMobile().trim() !== '' ||
    this.appellantEmail().trim() !== '' ||
    this.rbioOfficeCode() !== '' ||
    this.closureClause() !== '' ||
    this.categoryId() !== null ||
    this.groundOfComplaintId() !== null
  );

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadMasters();
  }

  /**
   * Dropdown options come from master tables, never from a literal list in this file — a hardcoded
   * clause or ground list drifts from what complaints actually carry and from the Scheme in force.
   */
  private loadMasters() {
    const base = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/masters`;

    this.http.get<{ data: GroundOption[] }>(`${base}/grounds`).subscribe({
      next: (res) => this.grounds.set(res?.data ?? []),
      error: () => this.grounds.set([])
    });

    this.http.get<{ data: ClauseOption[] }>(`${base}/closure-clauses`).subscribe({
      next: (res) => this.clauses.set(res?.data ?? []),
      error: () => this.clauses.set([])
    });

    this.http.get<{ data: OfficeOption[] }>(`${base}/offices`).subscribe({
      next: (res) => this.offices.set(res?.data ?? []),
      error: () => this.offices.set([])
    });

    // Bare array, and deliberately NOT /api/v1/masters/categories: CATEGORY_MASTER is empty in this
    // product, so that endpoint returns [] and the dropdown would silently render blank.
    this.http.get<CategoryOption[]>(`${environment.apiBaseUrl}/api/categories`).subscribe({
      next: (res) => this.categories.set(res ?? []),
      error: () => this.categories.set([])
    });
  }

  search() {
    if (!this.hasAnyFilter()) {
      this.errorMessageKey.set('aa.search.error_no_filter');
      this.loadError.set(true);
      return;
    }

    this.loading.set(true);
    this.loadError.set(false);
    this.errorMessageKey.set(null);

    const params = new URLSearchParams();
    const put = (key: string, value: string) => {
      if (value.trim() !== '') {
        params.set(key, value.trim());
      }
    };
    put('complaintNumber', this.complaintNumber());
    put('appellantName', this.appellantName());
    put('appellantMobile', this.appellantMobile());
    put('appellantEmail', this.appellantEmail());
    put('rbioOfficeCode', this.rbioOfficeCode());
    put('closureClause', this.closureClause());
    if (this.categoryId() !== null) {
      params.set('categoryId', String(this.categoryId()));
    }
    if (this.groundOfComplaintId() !== null) {
      params.set('groundOfComplaintId', String(this.groundOfComplaintId()));
    }

    const url = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/search?${params.toString()}`;

    this.http.get<{ data: { results: ParentComplaintRow[]; totalElements: number } }>(url).subscribe({
      next: (res) => {
        this.results.set(res?.data?.results ?? []);
        this.totalElements.set(res?.data?.totalElements ?? 0);
        this.searched.set(true);
        this.loading.set(false);
      },
      error: (err) => {
        // Surfaced, never swallowed: an empty grid must not be able to hide a broken endpoint.
        this.results.set([]);
        this.totalElements.set(0);
        this.searched.set(true);
        this.loadError.set(true);
        this.errorMessageKey.set(err?.error?.messageKey ?? 'aa.search.error_failed');
        this.loading.set(false);
      }
    });
  }

  reset() {
    this.complaintNumber.set('');
    this.appellantName.set('');
    this.appellantMobile.set('');
    this.appellantEmail.set('');
    this.rbioOfficeCode.set('');
    this.closureClause.set('');
    this.categoryId.set(null);
    this.groundOfComplaintId.set(null);
    this.results.set([]);
    this.totalElements.set(0);
    this.searched.set(false);
    this.loadError.set(false);
    this.errorMessageKey.set(null);
  }

  /** Story 6: the register affordance is offered only for an appeal-eligible parent. */
  openRegister(row: ParentComplaintRow) {
    if (!row.appealEligible) {
      return;
    }
    this.router.navigate(['/aa/register', row.complaintNumber]);
  }

  officeLabel(code: string | null): string {
    if (!code) {
      return '';
    }
    return this.offices().find((o) => o.officeCode === code)?.officeName ?? code;
  }

  setCategoryId(raw: string) {
    this.categoryId.set(raw === '' ? null : Number(raw));
  }

  setGroundId(raw: string) {
    this.groundOfComplaintId.set(raw === '' ? null : Number(raw));
  }
}
