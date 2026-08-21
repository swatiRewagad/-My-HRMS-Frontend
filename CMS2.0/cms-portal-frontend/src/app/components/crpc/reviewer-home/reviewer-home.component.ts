import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface ReviewDraft {
  draftId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmailId: string;
  subject: string;
  modeOfReceipt: string;
  status: string;
  category: string;
  entityName: string;
  proposedComplaint: string;
  deoDecision: string;
  deoName: string;
  assignedAt: string;
  creationDate: string;
  ageing: number;
  priority: string;
  vernacular: boolean;
}

interface ColumnDef {
  key: string;
  label: string;
  visible: boolean;
}

@Component({
  selector: 'app-reviewer-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reviewer-home.component.html',
  styleUrl: './reviewer-home.component.scss'
})
export class ReviewerHomeComponent implements OnInit {

  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  drafts = signal<ReviewDraft[]>([]);
  loading = signal(false);

  filterStatus = signal('');
  filterDeoDecision = signal('');
  searchText = signal('');
  sidebarCollapsed = signal(false);
  assignmentFilter = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');

  // Pagination
  currentPage = signal(1);
  pageSize = 10;

  // Sort
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // Column filters
  columnFilters: Record<string, string> = {};

  // Columns
  columns: ColumnDef[] = [
    { key: 'draftId', label: 'Complaint Id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'fromEmailId', label: 'From', visible: true },
    { key: 'ageing', label: 'Pending...', visible: true },
    { key: 'modeOfReceipt', label: 'Mode', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'proposedComplaint', label: 'Proposed Com...', visible: true },
    { key: 'creationDate', label: 'Creation Date', visible: true },
  ];

  visibleColumns = computed(() => this.columns.filter(c => c.visible));

  stats = computed(() => {
    const all = this.drafts();
    return {
      total: all.length,
      pending: all.filter(d => ['SENT_TO_REVIEWER', 'SENT_TO_OTHER_DEPT_FOR_APPROVAL', 'VERNACULAR_FOR_APPROVAL'].includes(d.status)).length,
      approved: all.filter(d => ['APPROVED', 'APPROVED_ROUTED', 'APPROVED_SENT_TO_OTHER_DEPT', 'APPROVED_VERNACULAR'].includes(d.status)).length,
      sentBack: all.filter(d => d.status === 'SENT_BACK_TO_DEO' || d.status === 'SENT_BACK').length,
      closedNm: all.filter(d => d.status === 'CLOSED_NM' || d.status === 'CLOSED_NOT_A_COMPLAINT').length,
    };
  });

  filteredDrafts = computed(() => {
    let result = this.drafts();
    const status = this.filterStatus();
    const search = this.searchText();

    if (status) {
      if (status === 'SENT_TO_REVIEWER') result = result.filter(d => d.status === 'SENT_TO_REVIEWER');
      else if (status === 'APPROVED') result = result.filter(d => d.status === 'APPROVED' || d.status === 'APPROVED_ROUTED');
      else if (status === 'SENT_BACK') result = result.filter(d => d.status === 'SENT_BACK_TO_DEO' || d.status === 'SENT_BACK');
      else if (status === 'CLOSED_NM') result = result.filter(d => d.status === 'CLOSED_NM' || d.status === 'CLOSED_NOT_A_COMPLAINT');
    }

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(d =>
        d.draftId.toLowerCase().includes(q) ||
        d.complainantName.toLowerCase().includes(q) ||
        d.subject.toLowerCase().includes(q) ||
        d.fromEmailId.toLowerCase().includes(q)
      );
    }

    // Column filters
    for (const key of Object.keys(this.columnFilters)) {
      const val = this.columnFilters[key]?.toLowerCase();
      if (val) {
        result = result.filter(d => {
          const cellVal = String((d as any)[key] || '').toLowerCase();
          return cellVal.includes(val);
        });
      }
    }

    return result;
  });

  paginatedDrafts = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredDrafts().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredDrafts().length / this.pageSize)));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];
    const start = Math.max(1, current - 2);
    const end = Math.min(total, start + 4);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  });

  paginationStart = computed(() => {
    if (this.filteredDrafts().length === 0) return 0;
    return (this.currentPage() - 1) * this.pageSize + 1;
  });

  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredDrafts().length));

  loggedInUser: { id: string; name: string; role: string } | null = null;

  ngOnInit() {
    const stored = sessionStorage.getItem('crpc_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r => ['REVIEWER', 'CRPC_HEAD', 'DEO'].includes(r)) || 'REVIEWER';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
        sessionStorage.setItem('crpc_user', JSON.stringify(this.loggedInUser));
      }
    }
    this.loadDrafts();
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }

  loadDrafts() {
    this.loading.set(true);
    const username = this.loggedInUser?.id || '';
    const params: any = {};
    if (this.assignmentFilter() === 'ASSIGNED_TO_ME') {
      params.assignedTo = username;
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/queue`, { params }).subscribe({
      next: (res) => {
        const queueDrafts = (res?.data || []).map((d: any) => this.mapToDraft(d));
        this.drafts.set(queueDrafts);
        this.loading.set(false);
      },
      error: () => {
        this.drafts.set([]);
        this.loading.set(false);
      }
    });
  }

  onAssignmentFilterChange(value: string) {
    this.assignmentFilter.set(value as 'ASSIGNED_TO_ME' | 'ALL');
    this.loadDrafts();
  }

  private mapToDraft(d: any): ReviewDraft {
    const hours = (Date.now() - new Date(d.receivedAt || d.createdAt).getTime()) / 3600000;
    return {
      draftId: d.draftId || '',
      complaintNumber: d.complaintNumber || d.draftId || '',
      complainantName: d.complainantName || '',
      fromEmailId: d.senderEmail || '',
      subject: d.subject || '',
      modeOfReceipt: d.modeOfReceipt || 'EMAIL',
      status: d.status || 'SENT_TO_REVIEWER',
      category: d.category || 'GENERAL',
      entityName: d.entityName || '',
      proposedComplaint: d.category || '',
      deoDecision: d.deoDecision || 'MAINTAINABLE',
      deoName: d.processedBy || d.assignedTo || '',
      assignedAt: d.createdAt || new Date().toISOString(),
      creationDate: d.createdAt ? d.createdAt.split('T')[0] : new Date().toISOString().split('T')[0],
      ageing: Math.max(0, Math.floor(hours / 24)),
      priority: hours > 48 ? 'HIGH' : hours > 24 ? 'MEDIUM' : 'LOW',
      vernacular: d.isVernacular || false,
    };
  }

  sortBy(col: string) {
    if (this.sortColumn === col) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = col;
      this.sortDirection = 'asc';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'SENT_TO_REVIEWER': return 'Sent to Reviewer';
      case 'SENT_TO_OTHER_DEPT_FOR_APPROVAL': return 'Other Dept - Pending Approval';
      case 'VERNACULAR_FOR_APPROVAL': return 'Vernacular - Pending Approval';
      case 'APPROVED': case 'APPROVED_ROUTED': return 'Approved';
      case 'APPROVED_SENT_TO_OTHER_DEPT': return 'Sent to Other Entity';
      case 'APPROVED_VERNACULAR': return 'Sent to Language Office';
      case 'SENT_BACK_TO_DEO': case 'SENT_BACK': return 'Sent Back';
      case 'CLOSED_NM': case 'CLOSED_NOT_A_COMPLAINT': return 'Closed (NM)';
      default: return status;
    }
  }

  getCellValue(draft: ReviewDraft, key: string): string {
    return String((draft as any)[key] || '—');
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/reviewer/draft', draftId]);
  }

  navigateTo(route: string) {
    this.router.navigate([route]);
  }
}
