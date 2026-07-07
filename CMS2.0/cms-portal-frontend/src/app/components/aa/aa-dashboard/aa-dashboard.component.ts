import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface AppealSummary {
  appealNumber: string;
  originalComplaintNumber: string;
  classification: 'APPEAL' | 'REPRESENTATION';
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

type AaRole = 'AA_REGISTRAR' | 'AA_BENCH_OFFICER' | 'AA_AUTHORITY' | 'AA_ADMIN';

@Component({
  selector: 'app-aa-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './aa-dashboard.component.html',
  styleUrl: './aa-dashboard.component.scss'
})
export class AaDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  appeals = signal<AppealSummary[]>([]);
  loading = signal(true);
  userRole = signal<AaRole>('AA_REGISTRAR');

  stats = signal<AppealStats>({ total: 0, pendingReview: 0, hearingsScheduled: 0, ordersPassed: 0, closed: 0 });

  filterStatus = '';
  filterClassification = '';
  searchText = '';
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  currentPage = signal(1);
  pageSize = 15;
  Math = Math;

  roleLabels: Record<AaRole, string> = {
    'AA_REGISTRAR': 'Registrar',
    'AA_BENCH_OFFICER': 'Bench Officer',
    'AA_AUTHORITY': 'Appellate Authority',
    'AA_ADMIN': 'AA Admin'
  };

  filteredAppeals = computed(() => {
    let result = this.appeals();
    if (this.filterStatus) {
      result = result.filter(a => a.status === this.filterStatus);
    }
    if (this.filterClassification) {
      result = result.filter(a => a.classification === this.filterClassification);
    }
    if (this.searchText) {
      const q = this.searchText.toLowerCase();
      result = result.filter(a =>
        a.appealNumber.toLowerCase().includes(q) ||
        a.originalComplaintNumber.toLowerCase().includes(q) ||
        a.appellantName.toLowerCase().includes(q)
      );
    }
    if (this.sortColumn) {
      result = [...result].sort((a, b) => {
        const av = (a as any)[this.sortColumn] || '';
        const bv = (b as any)[this.sortColumn] || '';
        const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true });
        return this.sortDirection === 'asc' ? cmp : -cmp;
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
    else if (roles.includes('AA_AUTHORITY')) this.userRole.set('AA_AUTHORITY');
    else if (roles.includes('AA_BENCH_OFFICER')) this.userRole.set('AA_BENCH_OFFICER');
    else this.userRole.set('AA_REGISTRAR');

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
    const role = this.userRole();
    const officer = this.auth.currentUser()?.username || '';

    let url = `${environment.apiBaseUrl}/api/v1/appeals?role=${role}&officer=${officer}`;

    this.http.get<any>(url).subscribe({
      next: (res) => {
        this.appeals.set(res?.data || []);
        this.loading.set(false);
      },
      error: () => {
        this.appeals.set([]);
        this.loading.set(false);
      }
    });
  }

  openAppeal(appeal: AppealSummary) {
    this.router.navigate(['/aa/appeal', appeal.appealNumber]);
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'filed': 'Filed',
      'under_review': 'Under Review',
      'accepted': 'Accepted',
      'rejected': 'Rejected',
      'hearing_scheduled': 'Hearing Scheduled',
      'hearing_completed': 'Hearing Completed',
      'order_reserved': 'Order Reserved',
      'order_passed': 'Order Passed',
      'remanded': 'Remanded',
      'dismissed': 'Dismissed',
      'closed': 'Closed',
      'assigned': 'Assigned',
      'documents_requested': 'Documents Requested',
    };
    return labels[status] || status;
  }

  async logout() {
    await this.auth.logout();
  }

  goBack() {
    this.router.navigate(['/staff/dashboard']);
  }
}
