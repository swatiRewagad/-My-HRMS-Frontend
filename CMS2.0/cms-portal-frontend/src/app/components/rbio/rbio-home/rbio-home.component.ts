import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { environment } from '../../../../environments/environment';

interface RbioComplaint {
  complaintId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmail: string;
  subject: string;
  modeOfReceipt: string;
  status: string;
  category: string;
  entityName: string;
  priority: string;
  assignedTo: string;
  createdAt: string;
  slaDueDate: string;
  slaBreachDays: number;
  description: string;
}

@Component({
  selector: 'app-rbio-home',
  standalone: true,
  imports: [CommonModule, FormsModule, NotificationBellComponent, SessionTimeoutComponent],
  templateUrl: './rbio-home.component.html',
  styleUrl: './rbio-home.component.scss'
})
export class RbioHomeComponent implements OnInit {

  router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaints = signal<RbioComplaint[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  filterStatus = signal('');
  filterQueue = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');
  searchText = signal('');
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
  showCreateDropdown = signal(false);

  advSearchActive = signal(false);
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', mobileNumber: '', email: '',
    fromEmailId: '', modeOfReceipt: '', entityName: '',
    subject: '', category: ''
  };

  allColumns = signal([
    { key: 'complaintId', label: 'Complaint Id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'fromEmail', label: 'From', visible: true },
    { key: 'slaBreachDays', label: 'SLA Breach In', visible: true },
    { key: 'modeOfReceipt', label: 'Mode', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'category', label: 'Complaint Category', visible: true },
    { key: 'createdAt', label: 'Creation Date', visible: true },
    { key: 'subject', label: 'Subject', visible: false },
    { key: 'priority', label: 'Priority', visible: false },
    { key: 'assignedTo', label: 'Assigned To', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  filteredColumns = computed(() => {
    if (!this.columnSearchText) return this.allColumns();
    const q = this.columnSearchText.toLowerCase();
    return this.allColumns().filter(c => c.label.toLowerCase().includes(q));
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();
    const status = this.filterStatus();
    if (status) result = result.filter(d => d.status === status);

    if (this.advSearchActive()) {
      const q = this.advSearch;
      if (q.complaintNumber) result = result.filter(d => d.complaintNumber?.toLowerCase().includes(q.complaintNumber.toLowerCase()));
      if (q.complaintId) result = result.filter(d => d.complaintId.toLowerCase().includes(q.complaintId.toLowerCase()));
      if (q.statusCode) result = result.filter(d => d.status === q.statusCode);
      if (q.complainantName) result = result.filter(d => d.complainantName?.toLowerCase().includes(q.complainantName.toLowerCase()));
      if (q.email) result = result.filter(d => d.fromEmail?.toLowerCase().includes(q.email.toLowerCase()));
      if (q.entityName) result = result.filter(d => d.entityName?.toLowerCase().includes(q.entityName.toLowerCase()));
      if (q.subject) result = result.filter(d => d.subject?.toLowerCase().includes(q.subject.toLowerCase()));
      if (q.category) result = result.filter(d => d.category === q.category);
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

  stats = computed(() => {
    const all = this.complaints();
    return {
      totalPending: all.length,
      pendingWithMe: all.filter(d => d.status === 'NEW' || d.status === 'ASSIGNED' || d.status === 'IN_PROGRESS').length,
      pendingContactPerson: all.filter(d => d.status === 'AWAITING_RESPONSE').length,
      pendingMeeting: all.filter(d => d.status === 'MEETING_SCHEDULED').length,
      slaBreach: all.filter(d => d.slaBreachDays > 0).length,
      slaOverdue: all.filter(d => d.slaBreachDays > 30).length,
      draft: all.filter(d => d.status === 'DRAFT' || d.status === 'NEW').length,
      meetingScheduled: all.filter(d => d.status === 'MEETING_SCHEDULED').length,
      sentBack: all.filter(d => d.status === 'SENT_BACK').length,
      assessmentComplete: all.filter(d => d.status === 'ASSESSMENT_COMPLETE').length,
    };
  });

  loggedInUser: { id: string; name: string; role: string } | null = null;

  ngOnInit() {
    try {
      const visited = localStorage.getItem('rbio_visitedComplaintIds');
      if (visited) this.visitedIds.set(new Set(JSON.parse(visited)));
    } catch {}

    const stored = sessionStorage.getItem('rbio_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r =>
          ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_ADJUDICATOR', 'RBIO_CONCILIATOR'].includes(r)
        ) || 'RBIO_OFFICER';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
        sessionStorage.setItem('rbio_user', JSON.stringify(this.loggedInUser));
      }
    }

    this.loadComplaints();
  }

  logout() {
    sessionStorage.removeItem('rbio_user');
    this.auth.logout();
  }

  loadComplaints() {
    this.loading.set(true);
    const username = this.loggedInUser?.id || '';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/rbio/complaints?assignedTo=${username}`).subscribe({
      next: (res) => {
        const items = (res.data || res || []).map((c: any) => this.mapComplaint(c));
        this.complaints.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.complaints.set(this.generateSampleData());
        this.loading.set(false);
      }
    });
  }

  private mapComplaint(c: any): RbioComplaint {
    const slaDue = c.slaDueDate ? new Date(c.slaDueDate) : new Date();
    const now = new Date();
    const diffDays = Math.ceil((now.getTime() - slaDue.getTime()) / (1000 * 60 * 60 * 24));
    return {
      complaintId: c.complaintId || c.complaintNumber || '',
      complaintNumber: c.complaintNumber || '',
      complainantName: c.complainantName || '',
      fromEmail: c.complainantEmail || c.fromEmail || '',
      subject: c.subject || '',
      modeOfReceipt: c.modeOfReceipt || c.filingType || 'Email',
      status: c.status || 'DRAFT',
      category: c.category || 'General',
      entityName: c.entityName || '',
      priority: c.priority || 'MEDIUM',
      assignedTo: c.assignedTo || c.assignedOfficer || '',
      createdAt: c.createdAt || '',
      slaDueDate: c.slaDueDate || '',
      slaBreachDays: Math.max(0, diffDays),
      description: c.description || '',
    };
  }

  private generateSampleData(): RbioComplaint[] {
    const statuses = ['DRAFT', 'IN_PROGRESS', 'SENT_BACK', 'MEETING_SCHEDULED', 'ASSESSMENT_COMPLETE', 'NEW'];
    const entities = ['HDFC Bank Ltd', 'ICICI Bank Ltd', 'SBI', 'Axis Bank Ltd', 'Kotak Bank', 'PNB', 'Yes Bank', 'IDFC Bank', 'IndusInd Bank', 'Bank of Baroda'];
    const categories = ['Account Hold', 'Loan EMI', 'CIBIL Correction', 'Credit Card', 'Transaction Dispute', 'Account Closure', 'Debit Card Issue', 'Interest Rate', 'Loan Processing', 'Statement Error'];
    const names = ['Varshika Gaur', 'Amit Kumar', 'AGR Team', 'Priya Sharma', 'Rajesh Singh', 'Sunita Verma', 'Anil Kapoor', 'Meena Patel', 'Kumar Reddy', 'Sneha Desai'];
    const modes = ['Email', 'Letter', 'Email', 'Email', 'Email', 'Letter', 'Email', 'Email', 'Letter', 'Email'];

    return Array.from({ length: 10 }, (_, i) => ({
      complaintId: `C${String(i + 1).padStart(3, '0')}`,
      complaintNumber: `183940295${i}`,
      complainantName: names[i],
      fromEmail: `${names[i].toLowerCase().replace(' ', '.')}@${i % 3 === 0 ? 'gmail.com' : i % 3 === 1 ? 'yahoo.com' : 'rbi.org.in'}`,
      subject: categories[i],
      modeOfReceipt: modes[i],
      status: statuses[i % statuses.length],
      category: categories[i],
      entityName: entities[i],
      priority: i % 3 === 0 ? 'HIGH' : 'MEDIUM',
      assignedTo: this.loggedInUser?.id || '',
      createdAt: `2026-05-${String(14 - i).padStart(2, '0')}`,
      slaDueDate: `2026-06-${String(14 - i).padStart(2, '0')}`,
      slaBreachDays: [30, 40, 25, 1, -2, -1, 2, 8, 1, 0][i],
      description: '',
    }));
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  openComplaint(complaintId: string) {
    this.visitedIds.update(ids => {
      const s = new Set(ids);
      s.add(complaintId);
      localStorage.setItem('rbio_visitedComplaintIds', JSON.stringify([...s]));
      return s;
    });
    this.router.navigate(['/rbio/complaint', complaintId]);
  }

  toggleSelect(id: string) {
    const ids = new Set(this.selectedIds());
    if (ids.has(id)) ids.delete(id);
    else ids.add(id);
    this.selectedIds.set(ids);
  }

  toggleSelectAll() {
    const filtered = this.filteredComplaints();
    if (this.selectedIds().size === filtered.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(filtered.map(d => d.complaintId)));
    }
  }

  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  applyAdvancedSearch() {
    this.searchText.set('');
    this.advSearchActive.set(true);
    this.currentPage.set(1);
    this.showAdvancedSearch.set(false);
  }

  clearAdvancedSearch() {
    this.advSearch = {
      complaintNumber: '', complaintId: '', statusCode: '',
      complainantName: '', mobileNumber: '', email: '',
      fromEmailId: '', modeOfReceipt: '', entityName: '',
      subject: '', category: ''
    };
    this.advSearchActive.set(false);
  }

  changePageSize(size: number) {
    this.pageSize = size;
    this.currentPage.set(1);
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'DRAFT': 'Draft', 'NEW': 'Draft', 'IN_PROGRESS': 'In Progress',
      'SENT_BACK': 'Sent Back', 'MEETING_SCHEDULED': 'Meeting Scheduled',
      'ASSESSMENT_COMPLETE': 'Assessment Complete', 'AWAITING_RESPONSE': 'Pending'
    };
    return map[status] || status;
  }

  getCellValue(complaint: RbioComplaint, key: string): string {
    const val = (complaint as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'createdAt') return val ? new Date(val).toLocaleDateString('en-IN') : '—';
    return String(val);
  }

  getSlaLabel(days: number): string {
    if (days <= 0) return `${Math.abs(days)} Days`;
    return `${days} Days`;
  }

  getSlaClass(days: number): string {
    if (days > 20) return 'sla-red';
    if (days > 5) return 'sla-orange';
    return 'sla-green';
  }

  dragIndex: number | null = null;
  dragOverIndex: number | null = null;

  onColumnDragStart(index: number) { this.dragIndex = index; }
  onColumnDragOver(event: DragEvent, index: number) { event.preventDefault(); this.dragOverIndex = index; }
  onColumnDrop(index: number) {
    if (this.dragIndex !== null && this.dragIndex !== index) {
      this.allColumns.update(cols => {
        const updated = [...cols];
        const [item] = updated.splice(this.dragIndex!, 1);
        updated.splice(index, 0, item);
        return updated;
      });
    }
    this.dragIndex = null;
    this.dragOverIndex = null;
  }
  onColumnDragEnd() { this.dragIndex = null; this.dragOverIndex = null; }
}
