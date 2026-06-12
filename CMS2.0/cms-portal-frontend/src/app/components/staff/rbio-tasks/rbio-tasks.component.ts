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

  // Filters
  filterStatus = signal('');
  searchText = signal('');
  columnFilters: Record<string, string> = {};
  columnSearchText = '';

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
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'subject', label: 'Subject', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'priority', label: 'Priority', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'assignedOfficer', label: 'Assigned To', visible: true },
    { key: 'slaDueDate', label: 'SLA Due Date', visible: true },
    { key: 'assignedAt', label: 'Assigned At', visible: false },
    { key: 'department', label: 'Department', visible: false },
    { key: 'assignedRole', label: 'Role', visible: false },
  ];

  visibleColumns = computed(() => this.allColumns.filter(c => c.visible));

  filteredColumns = computed(() => {
    if (!this.columnSearchText) return this.allColumns;
    const q = this.columnSearchText.toLowerCase();
    return this.allColumns.filter(c => c.label.toLowerCase().includes(q));
  });

  filteredTasks = computed(() => {
    let result = this.tasks();
    const status = this.filterStatus();
    const search = this.searchText();
    if (status) result = result.filter(t => t.status?.toLowerCase() === status.toLowerCase());
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(t =>
        t.complaintNumber?.toLowerCase().includes(q) ||
        t.complainantName?.toLowerCase().includes(q) ||
        t.entityName?.toLowerCase().includes(q) ||
        t.subject?.toLowerCase().includes(q)
      );
    }
    for (const [key, val] of Object.entries(this.columnFilters)) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(t => String((t as any)[key] || '').toLowerCase().includes(q));
      }
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
    return {
      total: all.length,
      assigned: all.filter(t => t.status?.toLowerCase() === 'assigned').length,
      inProgress: all.filter(t => t.status?.toLowerCase() === 'in_progress').length,
      escalated: all.filter(t => t.status?.toLowerCase() === 'escalated').length,
      resolved: all.filter(t => t.status?.toLowerCase() === 'resolved').length,
      rejected: all.filter(t => t.status?.toLowerCase() === 'rejected').length,
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
    const officer = this.auth.currentUser()?.username || '';

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

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  openTask(task: ComplaintTask) {
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
    let result = this.tasks();
    if (q.complaintNumber) result = result.filter(t => t.complaintNumber.includes(q.complaintNumber));
    if (q.complaintId) result = result.filter(t => t.complaintId.includes(q.complaintId));
    if (q.statusCode) result = result.filter(t => t.status?.toLowerCase() === q.statusCode.toLowerCase());
    if (q.complainantName) result = result.filter(t => t.complainantName?.toLowerCase().includes(q.complainantName.toLowerCase()));
    if (q.entityName) result = result.filter(t => t.entityName?.toLowerCase().includes(q.entityName.toLowerCase()));
    if (q.subject) result = result.filter(t => t.subject?.toLowerCase().includes(q.subject.toLowerCase()));
    if (q.priority) result = result.filter(t => t.priority?.toLowerCase() === q.priority.toLowerCase());
    if (q.assignedOfficer) result = result.filter(t => t.assignedOfficer?.toLowerCase().includes(q.assignedOfficer.toLowerCase()));
    this.searchText.set(JSON.stringify(q));
    this.showAdvancedSearch.set(false);
  }

  getCellValue(task: ComplaintTask, key: string): string {
    const val = (task as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'assignedAt' || key === 'slaDueDate') {
      try { return new Date(val).toLocaleDateString('en-IN'); } catch { return val; }
    }
    return String(val);
  }

  async logout() {
    await this.auth.logout();
  }
}
