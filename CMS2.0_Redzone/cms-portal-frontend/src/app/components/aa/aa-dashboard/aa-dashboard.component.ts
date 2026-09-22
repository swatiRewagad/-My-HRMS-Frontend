import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface AppealSummary {
  appealNumber: string;
  originalComplaintNumber: string;
  classification: 'APPEAL' | 'REPRESENTATION';
  classificationOverridden?: boolean;
  appellantName: string;
  status: string;
  hearingDate: string | null;
  assignedTo: string;
  filedDate: string;
}

interface AppealStats {
  total: number;
  pendingReview: number;
  hearingsScheduled: number;
  ordersPassed: number;
  closed: number;
}

type AaRole = 'AA_DO' | 'AA_REVIEWER' | 'AA_SECRETARIAT' | 'AA_ADMIN';

@Component({
  selector: 'app-aa-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent, TranslatePipe],
  templateUrl: './aa-dashboard.component.html',
  styleUrl: './aa-dashboard.component.scss'
})
export class AaDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  appeals = signal<AppealSummary[]>([]);
  loading = signal(true);
  loadError = signal(false);
  userRole = signal<AaRole>('AA_DO');

  /**
   * The AA home views. Server-side filters, because "open" and "assigned to me" need the status
   * vocabulary and the caller's identity, neither of which the browser should decide.
   */
  viewFilter = signal<'all' | 'assigned-to-me' | 'created-by-me'>('all');
  classificationFilter = signal<'' | 'APPEAL' | 'REPRESENTATION'>('');
  openOnly = signal(false);

  stats = signal<AppealStats>({ total: 0, pendingReview: 0, hearingsScheduled: 0, ordersPassed: 0, closed: 0 });

  /**
   * SIGNALS, not plain fields.
   *
   * {@link filteredAppeals} is a `computed()`, and a computed only re-evaluates when a SIGNAL it read
   * changes. Reading plain properties registered no dependency at all, so the grid NEVER re-filtered:
   * typing in the search box did nothing, the status and classification dropdowns did nothing, and
   * clicking a column header never reordered the rows. The same defect class was found in the RBIO task
   * list and the CEPC dashboard.
   */
  filterStatus = signal('');
  filterClassification = signal('');
  searchText = signal('');
  sortColumn = signal('');
  sortDirection = signal<'asc' | 'desc'>('asc');

  currentPage = signal(1);
  pageSize = 15;
  Math = Math;

  // Translation keys, not English literals: these are user-facing role names.
  roleLabels: Record<AaRole, string> = {
    'AA_DO': 'aa.role_do',
    'AA_REVIEWER': 'aa.role_reviewer',
    'AA_SECRETARIAT': 'aa.role_secretariat',
    'AA_ADMIN': 'aa.role_admin'
  };

  /** Switches the active home view and refetches; filtering happens server-side. */
  setView(view: 'all' | 'assigned-to-me' | 'created-by-me') {
    this.viewFilter.set(view);
    this.currentPage.set(1);
    this.loadAppeals();
  }

  setClassificationFilter(value: '' | 'APPEAL' | 'REPRESENTATION') {
    this.classificationFilter.set(value);
    this.currentPage.set(1);
    this.loadAppeals();
  }

  toggleOpenOnly() {
    this.openOnly.set(!this.openOnly());
    this.currentPage.set(1);
    this.loadAppeals();
  }

  filteredAppeals = computed(() => {
    let result = this.appeals();

    const status = this.filterStatus();
    if (status) {
      result = result.filter(a => a.status === status);
    }

    const classification = this.filterClassification();
    if (classification) {
      result = result.filter(a => a.classification === classification);
    }

    const search = this.searchText();
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(a =>
        a.appealNumber.toLowerCase().includes(q) ||
        a.originalComplaintNumber.toLowerCase().includes(q) ||
        (a.appellantName ?? '').toLowerCase().includes(q)
      );
    }

    const column = this.sortColumn();
    if (column) {
      const direction = this.sortDirection();
      result = [...result].sort((a, b) => {
        const av = (a as any)[column] || '';
        const bv = (b as any)[column] || '';
        const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true });
        return direction === 'asc' ? cmp : -cmp;
      });
    }
    return result;
  });

  paginatedAppeals = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredAppeals().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredAppeals().length / this.pageSize)));

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('AA_ADMIN')) this.userRole.set('AA_ADMIN');
    else if (roles.includes('AA_SECRETARIAT')) this.userRole.set('AA_SECRETARIAT');
    else if (roles.includes('AA_REVIEWER')) this.userRole.set('AA_REVIEWER');
    else this.userRole.set('AA_DO');

    this.loadStats();
    this.loadAppeals();
  }

  loadStats() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/appeals/stats`).subscribe({
      next: (res) => {
        this.stats.set(res?.data || { total: 0, pendingReview: 0, hearingsScheduled: 0, ordersPassed: 0, closed: 0 });
      },
      error: () => {}
    });
  }

  loadAppeals() {
    this.loading.set(true);
    this.loadError.set(false);

    // The previous call passed role= and officer= to GET /api/v1/appeals, which had no handler at all;
    // the silent error branch below then left the grid empty and indistinguishable from "no appeals".
    // Assignment scoping is now expressed as assignedOfficer=me and resolved server-side.
    const params = new URLSearchParams();
    if (this.viewFilter() === 'assigned-to-me') params.set('assignedOfficer', 'me');
    if (this.viewFilter() === 'created-by-me') params.set('createdBy', 'me');
    if (this.classificationFilter()) params.set('classification', this.classificationFilter());
    if (this.openOnly()) params.set('openOnly', 'true');

    const url = `${environment.apiBaseUrl}/api/v1/appeals?${params.toString()}`;

    this.http.get<any>(url).subscribe({
      next: (res) => {
        this.appeals.set(res?.data || []);
        this.loading.set(false);
      },
      error: () => {
        // Surfaced, not swallowed: an empty grid must not be able to hide a broken endpoint.
        this.appeals.set([]);
        this.loadError.set(true);
        this.loading.set(false);
      }
    });
  }

  openAppeal(appeal: AppealSummary) {
    this.router.navigate(['/aa/appeal', appeal.appealNumber]);
  }

  sortBy(column: string) {
    if (this.sortColumn() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
  }

  /** Used by the stat cards, which double as status filters. */
  setStatusFilter(status: string) {
    this.filterStatus.set(status);
    this.currentPage.set(1);
  }

  async logout() {
    await this.auth.logout();
  }

  goBack() {
    this.router.navigate(['/staff/dashboard']);
  }
}
