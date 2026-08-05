import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';
import { environment } from '../../../../environments/environment';

interface CepcComplaint {
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  entityName: string;
  entityType: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  department: string;
  assignedRole: string;
  assignedOfficer: string;
  workflowStage: string;
  modeOfReceipt: string;
  category: string;
  createdAt: string;
}

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

@Component({
  selector: 'app-cepc-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, NotificationBellComponent, SessionTimeoutComponent, SpeechButtonComponent, CepcSlaIndicatorComponent],
  templateUrl: './cepc-dashboard.component.html',
  styleUrl: './cepc-dashboard.component.scss'
})
export class CepcDashboardComponent implements OnInit {
  router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaints = signal<CepcComplaint[]>([]);
  loading = signal(true);
  userRole = signal<CepcRole>('CEPC_DO');

  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  filterStatus = signal('');
  filterQueue = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
  columnFilters: Record<string, string> = {};
  columnSearchText = '';

  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  currentPage = signal(1);
  pageSize = 10;

  showColumnConfig = signal(false);
  showAdvancedSearch = signal(false);
  advSearchActive = signal(false);
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', email: '', entityName: '',
    subject: '', priority: ''
  };

  dragIndex = -1;
  dragOverIndex = -1;

  loggedInUser: { id: string; name: string; role: string } | null = null;

  roleLabels: Record<CepcRole, string> = {
    'CEPC_DO': 'Dealing Officer',
    'CEPC_REVIEWER': 'Reviewer',
    'CEPC_INCHARGE': 'In Charge',
    'CEPC_CLOSING_AUTHORITY': 'Closing Authority',
    'CEPC_ADMIN': 'Admin',
    'CEPC_CONTACT_PERSON': 'Contact Person'
  };

  // ─── Create Complaint Dialog ───
  showCreateDialog = signal(false);
  newComplaint = {
    complainantName: '', complainantEmail: '', complainantPhone: '',
    complainantAddress: '', subject: '', description: '',
    entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
  };
  creating = signal(false);
  createSuccess = signal('');
  createError = signal('');

  // ─── Column Config ───
  allColumns = signal([
    { key: 'complaintId', label: 'Complaint Id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'complainantName', label: 'Complainant', visible: true },
    { key: 'entityName', label: 'Entity', visible: true },
    { key: 'subject', label: 'Subject', visible: true },
    { key: 'priority', label: 'Priority', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'slaDueDate', label: 'SLA Due', visible: true },
    { key: 'category', label: 'Category', visible: false },
    { key: 'modeOfReceipt', label: 'Mode', visible: false },
    { key: 'assignedOfficer', label: 'Assigned To', visible: false },
    { key: 'createdAt', label: 'Created Date', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  filteredColumns = computed(() => {
    if (!this.columnSearchText) return this.allColumns();
    const q = this.columnSearchText.toLowerCase();
    return this.allColumns().filter(c => c.label.toLowerCase().includes(q));
  });

  stats = computed(() => {
    const all = this.complaints();
    return {
      total: all.length,
      pending: all.filter(c => ['assigned', 'pending', 'new'].includes(c.status)).length,
      inProgress: all.filter(c => c.status === 'in_progress').length,
      underReview: all.filter(c => ['under_review', 'reviewer_review', 'incharge_review'].includes(c.status)).length,
      awaitingClosure: all.filter(c => c.status === 'awaiting_closure').length,
      escalated: all.filter(c => c.status === 'escalated').length,
    };
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();
    const status = this.filterStatus();
    if (status) {
      if (status === 'pending') {
        result = result.filter(c => ['assigned', 'pending', 'new'].includes(c.status));
      } else if (status === 'under_review') {
        result = result.filter(c => ['under_review', 'reviewer_review', 'incharge_review'].includes(c.status));
      } else {
        result = result.filter(c => c.status === status);
      }
    }

    if (this.advSearchActive()) {
      const q = this.advSearch;
      if (q.complaintNumber) result = result.filter(d => d.complaintNumber?.toLowerCase().includes(q.complaintNumber.toLowerCase()));
      if (q.complaintId) result = result.filter(d => d.complaintId.toLowerCase().includes(q.complaintId.toLowerCase()));
      if (q.statusCode) result = result.filter(d => d.status === q.statusCode);
      if (q.complainantName) result = result.filter(d => d.complainantName?.toLowerCase().includes(q.complainantName.toLowerCase()));
      if (q.email) result = result.filter(d => d.complainantEmail?.toLowerCase().includes(q.email.toLowerCase()));
      if (q.entityName) result = result.filter(d => d.entityName?.toLowerCase().includes(q.entityName.toLowerCase()));
      if (q.subject) result = result.filter(d => d.subject?.toLowerCase().includes(q.subject.toLowerCase()));
      if (q.priority) result = result.filter(d => d.priority === q.priority);
    }

    for (const [key, val] of Object.entries(this.columnFilters)) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(d => String((d as any)[key] || '').toLowerCase().includes(q));
      }
    }

    if (this.filterUnread()) {
      result = result.filter(d => !this.visitedIds().has(d.complaintId));
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

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredComplaints().length / this.pageSize)));

  paginatedComplaints = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredComplaints().slice(start, start + this.pageSize);
  });

  paginationStart = computed(() => this.filteredComplaints().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize + 1);
  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredComplaints().length));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];
    const start = Math.max(1, current - 2);
    const end = Math.min(total, current + 2);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  });

  async ngOnInit() {
    try {
      const visited = localStorage.getItem('cepc_visitedComplaintIds');
      if (visited) this.visitedIds.set(new Set(JSON.parse(visited)));
    } catch {}

    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('CEPC_ADMIN')) this.userRole.set('CEPC_ADMIN');
    else if (roles.includes('CEPC_CLOSING_AUTHORITY')) this.userRole.set('CEPC_CLOSING_AUTHORITY');
    else if (roles.includes('CEPC_INCHARGE')) this.userRole.set('CEPC_INCHARGE');
    else if (roles.includes('CEPC_REVIEWER')) this.userRole.set('CEPC_REVIEWER');
    else if (roles.includes('CEPC_CONTACT_PERSON')) this.userRole.set('CEPC_CONTACT_PERSON');
    else this.userRole.set('CEPC_DO');

    const user = this.auth.currentUser();
    if (user) {
      this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role: this.userRole() };
    }

    this.loadComplaints();
  }

  loadComplaints() {
    this.loading.set(true);
    const officer = this.auth.currentUser()?.username || '';
    const role = this.userRole();

    let url: string;
    if (role === 'CEPC_ADMIN') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks`;
    } else if (role === 'CEPC_CONTACT_PERSON') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/contact-person/tasks?officer=${officer}`;
    } else {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks?role=${role}`;
    }

    this.http.get<any>(url).subscribe({
      next: (res) => {
        const roleTasks = (res?.data || []).map((c: any) => this.mapComplaint(c));
        this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/my-actions?officer=${officer}`).subscribe({
          next: (actionsRes) => {
            const actionTasks = (actionsRes?.data || []).map((c: any) => this.mapComplaint(c));
            const existingIds = new Set(roleTasks.map((t: any) => t.complaintNumber));
            const merged = [...roleTasks, ...actionTasks.filter((t: any) => !existingIds.has(t.complaintNumber))];
            this.complaints.set(merged);
            this.loading.set(false);
          },
          error: () => {
            this.complaints.set(roleTasks);
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  private mapComplaint(c: any): CepcComplaint {
    return {
      complaintId: c.complaintId || c.complaintNumber || '',
      complaintNumber: c.complaintNumber || '',
      subject: c.subject || '',
      complainantName: c.complainantName || '',
      complainantEmail: c.complainantEmail || '',
      complainantPhone: c.complainantPhone || '',
      entityName: c.entityName || '',
      entityType: c.entityType || '',
      priority: c.priority || 'MEDIUM',
      status: c.status || 'pending',
      assignedAt: c.assignedAt || '',
      slaDueDate: c.slaDueDate || '',
      department: c.department || 'CEPC',
      assignedRole: c.assignedRole || '',
      assignedOfficer: c.assignedOfficer || '',
      workflowStage: c.workflowStage || '',
      modeOfReceipt: c.modeOfReceipt || c.filingType || '',
      category: c.category || '',
      createdAt: c.createdAt || '',
    };
  }

  // ─── Table Features ───
  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  getCellValue(item: CepcComplaint, key: string): string {
    return (item as any)[key] || '—';
  }

  toggleSelectAll() {
    const all = this.filteredComplaints();
    if (this.selectedIds().size === all.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(all.map(c => c.complaintId)));
    }
  }

  toggleSelect(id: string) {
    this.selectedIds.update(ids => {
      const s = new Set(ids);
      if (s.has(id)) s.delete(id); else s.add(id);
      return s;
    });
  }

  openComplaint(complaint: CepcComplaint) {
    this.visitedIds.update(ids => {
      const s = new Set(ids);
      s.add(complaint.complaintId);
      localStorage.setItem('cepc_visitedComplaintIds', JSON.stringify([...s]));
      return s;
    });
    this.router.navigate(['/cepc/complaint', complaint.complaintNumber]);
  }

  changePageSize(size: number) {
    this.pageSize = size;
    this.currentPage.set(1);
  }

  // ─── Column Config ───
  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  onColumnDragStart(index: number) { this.dragIndex = index; }
  onColumnDragOver(event: DragEvent, index: number) { event.preventDefault(); this.dragOverIndex = index; }
  onColumnDrop(index: number) {
    if (this.dragIndex >= 0 && this.dragIndex !== index) {
      this.allColumns.update(cols => {
        const updated = [...cols];
        const [moved] = updated.splice(this.dragIndex, 1);
        updated.splice(index, 0, moved);
        return updated;
      });
    }
    this.dragIndex = -1;
    this.dragOverIndex = -1;
  }
  onColumnDragEnd() { this.dragIndex = -1; this.dragOverIndex = -1; }

  // ─── Advanced Search ───
  applyAdvancedSearch() {
    this.advSearchActive.set(true);
    this.showAdvancedSearch.set(false);
    this.currentPage.set(1);
  }

  clearAdvancedSearch() {
    this.advSearch = { complaintNumber: '', complaintId: '', statusCode: '', complainantName: '', email: '', entityName: '', subject: '', priority: '' };
    this.advSearchActive.set(false);
    this.currentPage.set(1);
  }

  // ─── Create Complaint ───
  openCreateDialog() {
    this.showCreateDialog.set(true);
    this.createSuccess.set('');
    this.createError.set('');
    this.newComplaint = {
      complainantName: '', complainantEmail: '', complainantPhone: '',
      complainantAddress: '', subject: '', description: '',
      entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
    };
  }

  closeCreateDialog() {
    this.showCreateDialog.set(false);
  }

  submitNewComplaint() {
    if (!this.newComplaint.complainantName || !this.newComplaint.subject) {
      this.createError.set('Complainant Name and Subject are required.');
      return;
    }
    this.creating.set(true);
    this.createError.set('');

    const payload = {
      ...this.newComplaint,
      createdBy: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/create-complaint`, payload)
      .subscribe({
        next: (res) => {
          this.creating.set(false);
          this.createSuccess.set(`Complaint ${res?.data?.complaintNumber} created successfully.`);
          setTimeout(() => {
            this.showCreateDialog.set(false);
            this.loadComplaints();
          }, 1500);
        },
        error: (err) => {
          this.creating.set(false);
          this.createError.set(err.error?.message || 'Failed to create complaint.');
        }
      });
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'assigned': 'Assigned', 'pending': 'Pending', 'new': 'New',
      'in_progress': 'Under Examination', 'under_review': 'Under Review',
      'reviewer_review': 'Reviewer Review', 'incharge_review': 'In Charge Review',
      'awaiting_closure': 'Awaiting Closure', 'escalated': 'Escalated',
      'sent_back': 'Sent Back', 'info_requested': 'Info Requested',
      'forwarded': 'Forwarded to Dept', 'forwarded_to_contact': 'With Contact Person',
      'closed': 'Closed', 'resolved': 'Resolved',
    };
    return labels[status] || status;
  }

  async logout() {
    await this.auth.logout();
  }
}
