import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface ComplaintTask {
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  entityName: string;
  department: string;
  assignedRole: string;
  assignedOfficer: string;
  hasAttachments?: boolean;
  triageSignal?: string;
  viewOnly?: boolean;
}

@Component({
  selector: 'app-rbio-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-tasks.component.html',
  styleUrl: './rbio-tasks.component.scss'
})
export class RbioTasksComponent implements OnInit {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private http = inject(HttpClient);

  tasks = signal<ComplaintTask[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set(JSON.parse(localStorage.getItem('rbio_visited_ids') || '[]')));
  // 'mine' = only complaints assigned to the logged-in officer (default); 'all' = every RBIO
  // complaint regardless of assignee, matching the All/Assigned-to-me split on /crpc/home.
  queueScope = signal<'mine' | 'all'>('mine');

  // Filters
  filterStatus = signal('');
  filterSlaBreached = signal(false);
  searchText = signal('');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
  filterSatisfiesRules = signal(false);
  columnFilters = signal<Record<string, string>>({});
  columnSearchText = signal('');

  setColumnFilter(key: string, value: string) {
    this.columnFilters.update(f => ({ ...f, [key]: value }));
  }

  // Sorting
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // Pagination
  currentPage = signal(1);
  pageSize = 10;

  // Dialogs
  showColumnConfig = signal(false);
  showAdvancedSearch = signal(false);

  // Advanced Search
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', entityName: '', subject: '',
    priority: '', assignedOfficer: ''
  };

  // Column configuration
  allColumns = [
    { key: 'complaintId', label: 'Complaint Id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'assignedOfficer', label: 'Assigned To', visible: true },
    { key: 'slaDueDate', label: 'SLA Breach In', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'subject', label: 'Subject', visible: true },
    { key: 'priority', label: 'Priority', visible: false },
    { key: 'assignedAt', label: 'Assigned At', visible: false },
    { key: 'department', label: 'Department', visible: false },
    { key: 'assignedRole', label: 'Role', visible: false },
  ];

  visibleColumns = computed(() => this.allColumns.filter(c => c.visible));

  filteredColumns = computed(() => {
    const text = this.columnSearchText();
    if (!text) return this.allColumns;
    const q = text.toLowerCase();
    return this.allColumns.filter(c => c.label.toLowerCase().includes(q));
  });

  filteredTasks = computed(() => {
    let result = this.tasks();
    const status = this.filterStatus();
    const search = this.searchText();
    if (status) result = result.filter(t => t.status?.toLowerCase() === status.toLowerCase());
    if (this.filterSlaBreached()) result = result.filter(t => this.slaDaysRemaining(t.slaDueDate) < 0);
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(t =>
        t.complaintNumber?.toLowerCase().includes(q) ||
        t.complainantName?.toLowerCase().includes(q) ||
        t.entityName?.toLowerCase().includes(q) ||
        t.subject?.toLowerCase().includes(q)
      );
    }
    for (const [key, val] of Object.entries(this.columnFilters())) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(t => String((t as any)[key] || '').toLowerCase().includes(q));
      }
    }
    if (this.filterUnread()) {
      result = result.filter(t => !this.visitedIds().has(String(t.complaintId)));
    }
    if (this.filterWithoutAttachments()) {
      result = result.filter(t => !t.hasAttachments);
    }
    if (this.filterSatisfiesRules()) {
      result = result.filter(t => t.triageSignal === 'OBJECTIVELY_CLEAR');
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

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredTasks().length / this.pageSize)));

  paginatedTasks = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredTasks().slice(start, start + this.pageSize);
  });

  paginationStart = computed(() => this.filteredTasks().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize + 1);
  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredTasks().length));

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
    const all = this.tasks();
    const statusIs = (t: ComplaintTask, s: string) => t.status?.toLowerCase() === s;
    return {
      total: all.length,
      assigned: all.filter(t => statusIs(t, 'assigned')).length,
      inProgress: all.filter(t => statusIs(t, 'in_progress')).length,
      escalated: all.filter(t => statusIs(t, 'escalated')).length,
      resolved: all.filter(t => statusIs(t, 'resolved')).length,
      rejected: all.filter(t => statusIs(t, 'rejected')).length,
      // Same KPI set as the RBIO Home dashboard, same status/SLA conventions.
      totalPending: all.length,
      pendingWithMe: all.filter(t => statusIs(t, 'assigned') || statusIs(t, 'in_progress')).length,
      pendingWithRE: all.filter(t => statusIs(t, 'awaiting_response')).length,
      pendingMeeting: all.filter(t => statusIs(t, 'meeting_scheduled')).length,
      slaBreach: all.filter(t => this.slaDaysRemaining(t.slaDueDate) < 0).length,
      slaBreached0to15: all.filter(t => {
        const d = this.slaDaysRemaining(t.slaDueDate);
        return d < 0 && d >= -15;
      }).length,
      slaBreached16to30: all.filter(t => {
        const d = this.slaDaysRemaining(t.slaDueDate);
        return d < -15 && d >= -30;
      }).length,
      // Same tab set as the RBIO Home dashboard.
      draft: all.filter(t => statusIs(t, 'draft') || statusIs(t, 'new')).length,
      sentBack: all.filter(t => statusIs(t, 'sent_back')).length,
      // No backend status exists yet for these two - honestly 0 until that workflow stage exists.
      responseFromRE: all.filter(t => statusIs(t, 'response_from_re')).length,
      withdrawn: all.filter(t => statusIs(t, 'withdrawn')).length,
    };
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadTasks();
  }

  private loadTasks() {
    this.loading.set(true);
    const officer = this.queueScope() === 'all' ? '' : (this.auth.currentUser()?.username || '');

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/rbio/all-tasks?officer=${officer}`)
      .subscribe({
        next: (res) => {
          this.tasks.set(res?.data || []);
          this.loading.set(false);
        },
        error: () => {
          this.tasks.set([]);
          this.loading.set(false);
        }
      });
  }

  setQueueScope(scope: 'mine' | 'all') {
    if (this.queueScope() === scope) return;
    this.queueScope.set(scope);
    this.loadTasks();
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  openTask(task: ComplaintTask) {
    const visited = new Set(this.visitedIds());
    visited.add(String(task.complaintId));
    this.visitedIds.set(visited);
    localStorage.setItem('rbio_visited_ids', JSON.stringify([...visited]));
    this.router.navigate(['/staff/rbio/task', task.complaintNumber]);
  }

  toggleSelect(id: string) {
    const ids = new Set(this.selectedIds());
    if (ids.has(id)) ids.delete(id);
    else ids.add(id);
    this.selectedIds.set(ids);
  }

  toggleSelectAll() {
    const filtered = this.filteredTasks();
    if (this.selectedIds().size === filtered.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(filtered.map(t => t.complaintId)));
    }
  }

  toggleColumnVisibility(key: string) {
    const col = this.allColumns.find(c => c.key === key);
    if (col) col.visible = !col.visible;
  }

  applyAdvancedSearch() {
    const q = this.advSearch;
    this.filterStatus.set(q.statusCode || '');
    const filters: Record<string, string> = {};
    if (q.complaintNumber) filters['complaintNumber'] = q.complaintNumber;
    if (q.complaintId) filters['complaintId'] = q.complaintId;
    if (q.complainantName) filters['complainantName'] = q.complainantName;
    if (q.entityName) filters['entityName'] = q.entityName;
    if (q.subject) filters['subject'] = q.subject;
    if (q.priority) filters['priority'] = q.priority;
    if (q.assignedOfficer) filters['assignedOfficer'] = q.assignedOfficer;
    this.columnFilters.set(filters);
    this.searchText.set('');
    this.showAdvancedSearch.set(false);
  }

  private slaDaysRemaining(slaDueDate: string): number {
    if (!slaDueDate) return 999;
    return Math.ceil((new Date(slaDueDate).getTime() - new Date().getTime()) / (1000 * 60 * 60 * 24));
  }

  // Same rule as the RBIO Home dashboard: a couple of statuses always get a fixed color
  // regardless of SLA, everything else is colored by SLA severity.
  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'draft': 'Draft', 'new': 'New Complaint', 'assigned': 'New Complaint',
      'in_progress': 'In Progress', 'sent_back': 'Sent Back to DO', 'sent_to_do': 'Sent Back to DO',
      'meeting_scheduled': 'Meeting Scheduled', 'awaiting_response': 'Information Required',
      'pending_office_head_approval': 'Sent to Other Office', 'sent_to_reviewer': 'Sent to Reviewer',
      'escalated': 'Escalated', 'resolved': 'Resolved', 'rejected': 'Rejected',
      'closed': 'Closed', 'withdrawn': 'Complaint Withdrawn',
    };
    return map[status?.toLowerCase()] || status;
  }

  getStatusIcon(status: string): string {
    const map: Record<string, string> = {
      'draft': 'pi-box', 'new': 'pi-send', 'assigned': 'pi-send',
      'sent_back': 'pi-reply', 'sent_to_do': 'pi-reply', 'awaiting_response': 'pi-exclamation-circle',
      'pending_office_head_approval': 'pi-arrow-up-right', 'sent_to_reviewer': 'pi-arrow-up-right',
      'meeting_scheduled': 'pi-calendar', 'resolved': 'pi-verified',
      'withdrawn': 'pi-inbox',
    };
    return map[status?.toLowerCase()] || 'pi-circle-fill';
  }

  // Pure SLA-breach severity for the "SLA Breach In" pill itself.
  getSlaClass(slaDueDate: string): string {
    const days = this.slaDaysRemaining(slaDueDate);
    if (days < 0) return 'sla-red';
    if (days <= 15) return 'sla-yellow';
    return 'sla-green';
  }

  // Row left-border/background driven purely by workflow status, not SLA days:
  // white/none for a fresh complaint, red once communication is sent to RE (awaiting their
  // response), green once RE responds, yellow when sent back to the user, pink if withdrawn.
  getRowClass(slaDueDate: string, status?: string): string {
    const s = status?.toLowerCase();
    if (s === 'withdrawn') return 'sla-pink';
    if (s === 'sent_back' || s === 'sent_to_do') return 'sla-yellow';
    if (s === 'response_from_re') return 'sla-green';
    if (s === 'awaiting_response') return 'sla-red';
    return '';
  }

  getSlaLabel(slaDueDate: string): string {
    if (!slaDueDate) return '—';
    const days = this.slaDaysRemaining(slaDueDate);
    if (Math.abs(days) < 2) return `${Math.round(days * 24)} Hrs`;
    return `${days} Days`;
  }

  getCellValue(task: ComplaintTask, key: string): string {
    const val = (task as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'slaDueDate') return this.getSlaLabel(val);
    if (key === 'assignedAt') {
      try { return new Date(val).toLocaleDateString('en-IN'); } catch { return val; }
    }
    return String(val);
  }

  navigateToCreateComplaint() {
    this.router.navigate(['/rbio/create-complaint']);
  }

  async logout() {
    await this.auth.logout();
  }
}
