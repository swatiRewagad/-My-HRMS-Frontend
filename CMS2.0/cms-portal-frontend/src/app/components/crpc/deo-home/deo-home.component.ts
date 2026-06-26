import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { EmailDraft } from '../../../models/email-syndication.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

interface DraftComplaint {
  draftId: string;
  displayId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmailId: string;
  subject: string;
  modeOfReceipt: 'EMAIL' | 'PHYSICAL_LETTER' | 'PORTAL' | 'CPGRAMS';
  status: string;
  category: string;
  entityName: string;
  state: string;
  district: string;
  systemSuggestion: string;
  emailType: 'TO' | 'CC_BCC' | null;
  vernacular: boolean;
  assignedAt: string;
  createdAt: string;
  priority: string;
  slaRemaining: number;
  ageing: number;
  proposedCategory: string;
  hasAttachments: boolean;
}

@Component({
  selector: 'app-deo-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './deo-home.component.html',
  styleUrl: './deo-home.component.scss'
})
export class DeoHomeComponent implements OnInit {

  router = inject(Router);

  drafts = signal<DraftComplaint[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  // Filters
  filterStatus = signal('');
  filterMode = signal<'ALL' | 'DIRECT' | 'ABR'>('ALL');
  searchText = signal('');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
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
    complainantName: '', mobileNumber: '', email: '',
    fromEmailId: '', modeOfReceipt: '', entityName: '',
    subject: '', ndiContactPerson: '', category: ''
  };

  // Column configuration
  allColumns = signal([
    { key: 'displayId', label: 'Complaint Id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'fromEmailId', label: 'From', visible: true },
    { key: 'ageing', label: 'Pending', visible: true },
    { key: 'modeOfReceipt', label: 'Mode', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'proposedCategory', label: 'Proposed Com...', visible: true },
    { key: 'createdAt', label: 'Creation Date', visible: true },
    { key: 'subject', label: 'Subject', visible: false },
    { key: 'category', label: 'Category', visible: false },
    { key: 'priority', label: 'Priority', visible: false },
    { key: 'slaRemaining', label: 'SLA (hrs)', visible: false },
    { key: 'state', label: 'State', visible: false },
    { key: 'district', label: 'District', visible: false },
    { key: 'systemSuggestion', label: 'System Suggestion', visible: false },
    { key: 'emailType', label: 'Email Type', visible: false },
    { key: 'vernacular', label: 'Vernacular', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  filteredColumns = computed(() => {
    if (!this.columnSearchText) return this.allColumns();
    const q = this.columnSearchText.toLowerCase();
    return this.allColumns().filter(c => c.label.toLowerCase().includes(q));
  });

  filteredDrafts = computed(() => {
    let result = this.drafts();
    const status = this.filterStatus();
    const mode = this.filterMode();
    const search = this.searchText();
    if (status) result = result.filter(d => d.status === status);
    if (mode === 'DIRECT') result = result.filter(d => d.modeOfReceipt === 'PHYSICAL_LETTER' || d.modeOfReceipt === 'PORTAL');
    if (mode === 'ABR') result = result.filter(d => d.modeOfReceipt === 'EMAIL' || d.modeOfReceipt === 'CPGRAMS');
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(d =>
        d.draftId.toLowerCase().includes(q) ||
        d.complainantName.toLowerCase().includes(q) ||
        d.entityName.toLowerCase().includes(q) ||
        d.subject.toLowerCase().includes(q)
      );
    }
    // Column-level filters
    for (const [key, val] of Object.entries(this.columnFilters)) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(d => String((d as any)[key] || '').toLowerCase().includes(q));
      }
    }
    // Toggle filters
    if (this.filterUnread()) {
      result = result.filter(d => !this.visitedIds().has(d.draftId));
    }
    if (this.filterWithoutAttachments()) {
      result = result.filter(d => !d.hasAttachments);
    }
    // Sorting
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

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredDrafts().length / this.pageSize)));

  paginatedDrafts = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredDrafts().slice(start, start + this.pageSize);
  });

  paginationStart = computed(() => this.filteredDrafts().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize + 1);
  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredDrafts().length));

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
    const all = this.drafts();
    return {
      total: all.length,
      draft: all.filter(d => d.status === 'DRAFT').length,
      inProgress: all.filter(d => d.status === 'IN_PROGRESS').length,
      rejected: all.filter(d => d.status === 'REJECTED_BY_REVIEWER').length,
      approved: all.filter(d => d.status === 'APPROVED').length,
      direct: all.filter(d => d.modeOfReceipt === 'PHYSICAL_LETTER' || d.modeOfReceipt === 'PORTAL').length,
      viaAbr: all.filter(d => d.modeOfReceipt === 'EMAIL' || d.modeOfReceipt === 'CPGRAMS').length,
    };
  });

  private emailService = inject(EmailSyndicationService);
  private auth = inject(KeycloakAuthService);
  loggedInUser: { id: string; name: string; role: string } | null = null;

  ngOnInit() {
    try {
      const visited = localStorage.getItem('visitedComplaintIds');
      if (visited) this.visitedIds.set(new Set(JSON.parse(visited)));
    } catch {}

    const stored = sessionStorage.getItem('crpc_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r => ['DEO', 'REVIEWER', 'CRPC_HEAD'].includes(r)) || 'DEO';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
        sessionStorage.setItem('crpc_user', JSON.stringify(this.loggedInUser));
      }
    }

    if (this.loggedInUser?.role === 'REVIEWER') {
      this.router.navigate(['/crpc/reviewer']);
      return;
    }

    if (this.loggedInUser?.role === 'CRPC_HEAD') {
      this.router.navigate(['/crpc/ops-head']);
      return;
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
    this.emailService.getQueue(undefined, username).subscribe({
      next: (queueDrafts) => {
        const myDrafts = queueDrafts.map((d, i) => this.mapToDraftComplaint(d, i + 1));
        this.drafts.set(myDrafts);
        this.loading.set(false);
      },
      error: () => {
        this.drafts.set([]);
        this.loading.set(false);
      }
    });
  }

  private mapToDraftComplaint(d: EmailDraft, index: number): DraftComplaint {
    const hours = (Date.now() - new Date(d.receivedAt).getTime()) / 3600000;
    return {
      draftId: d.draftId,
      displayId: d.displayId || ('C' + String(index).padStart(3, '0')),
      complaintNumber: d.parentComplaintId || '',
      complainantName: d.complainantName || '',
      fromEmailId: d.senderEmail || '',
      subject: d.subject || '',
      modeOfReceipt: (d.modeOfReceipt as any) || 'EMAIL',
      status: this.mapStatus(d.status),
      category: d.category || 'GENERAL',
      entityName: '',
      state: '',
      district: '',
      systemSuggestion: 'PENDING',
      emailType: 'TO',
      vernacular: false,
      assignedAt: d.createdAt || new Date().toISOString(),
      createdAt: d.receivedAt || new Date().toISOString(),
      priority: 'MEDIUM',
      slaRemaining: Math.max(0, 72 - Math.floor(hours)),
      ageing: Math.max(0, Math.floor(hours / 24)),
      proposedCategory: d.category || '',
      hasAttachments: (d.attachments && d.attachments.length > 0) || false,
    };
  }

  private mapStatus(status: string): string {
    switch (status) {
      case 'SENT_TO_REVIEWER':
      case 'ASSIGNED': return 'IN_PROGRESS';
      case 'APPROVED_ROUTED':
      case 'CONVERTED': return 'APPROVED';
      case 'SENT_BACK_TO_DEO':
      case 'CLOSED_NOT_A_COMPLAINT': return 'REJECTED_BY_REVIEWER';
      case 'DRAFT': return 'DRAFT';
      default: return 'DRAFT';
    }
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  openDraft(draftId: string) {
    this.visitedIds.update(ids => { const s = new Set(ids); s.add(draftId); localStorage.setItem('visitedComplaintIds', JSON.stringify([...s])); return s; });
    this.router.navigate(['/crpc/draft', draftId]);
  }

  createPhysicalLetter() {
    this.router.navigate(['/crpc/physical-letter']);
  }

  toggleSelect(draftId: string) {
    const ids = new Set(this.selectedIds());
    if (ids.has(draftId)) ids.delete(draftId);
    else ids.add(draftId);
    this.selectedIds.set(ids);
  }

  toggleSelectAll() {
    const filtered = this.filteredDrafts();
    if (this.selectedIds().size === filtered.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(filtered.map(d => d.draftId)));
    }
  }

  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  dragIndex: number | null = null;
  dragOverIndex: number | null = null;

  onColumnDragStart(index: number) {
    this.dragIndex = index;
  }

  onColumnDragOver(event: DragEvent, index: number) {
    event.preventDefault();
    this.dragOverIndex = index;
  }

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

  onColumnDragEnd() {
    this.dragIndex = null;
    this.dragOverIndex = null;
  }

  applyAdvancedSearch() {
    const q = this.advSearch;
    let result = this.drafts();
    if (q.complaintNumber) result = result.filter(d => d.complaintNumber.includes(q.complaintNumber));
    if (q.complaintId) result = result.filter(d => d.draftId.includes(q.complaintId));
    if (q.statusCode) result = result.filter(d => d.status === q.statusCode);
    if (q.complainantName) result = result.filter(d => d.complainantName.toLowerCase().includes(q.complainantName.toLowerCase()));
    if (q.mobileNumber) result = result.filter(d => d.fromEmailId.includes(q.mobileNumber));
    if (q.email) result = result.filter(d => d.fromEmailId.toLowerCase().includes(q.email.toLowerCase()));
    if (q.fromEmailId) result = result.filter(d => d.fromEmailId.toLowerCase().includes(q.fromEmailId.toLowerCase()));
    if (q.modeOfReceipt) result = result.filter(d => d.modeOfReceipt === q.modeOfReceipt);
    if (q.entityName) result = result.filter(d => d.entityName.toLowerCase().includes(q.entityName.toLowerCase()));
    if (q.subject) result = result.filter(d => d.subject.toLowerCase().includes(q.subject.toLowerCase()));
    if (q.category) result = result.filter(d => d.category === q.category);
    this.searchText.set(JSON.stringify(q));
    this.showAdvancedSearch.set(false);
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'DRAFT': return 'Draft';
      case 'IN_PROGRESS': return 'In Progress';
      case 'APPROVED': return 'Approved';
      case 'REJECTED_BY_REVIEWER': return 'Sent Back';
      default: return status;
    }
  }

  getCellValue(draft: DraftComplaint, key: string): string {
    const val = (draft as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'vernacular') return val ? 'Yes' : 'No';
    if (key === 'assignedAt' || key === 'createdAt') return new Date(val).toLocaleDateString('en-IN');
    if (key === 'ageing') return val + ' day' + (val !== 1 ? 's' : '');
    return String(val);
  }

  exportToCSV() {
    const columns = this.visibleColumns();
    const data = this.filteredDrafts();
    const header = columns.map(c => c.label).join(',');
    const rows = data.map(d => columns.map(c => {
      const val = this.getCellValue(d, c.key);
      return `"${val.replace(/"/g, '""')}"`;
    }).join(','));
    const csv = [header, ...rows].join('\n');
    this.downloadFile(csv, 'crpc-complaints.csv', 'text/csv');
  }

  exportToExcel() {
    const columns = this.visibleColumns();
    const data = this.filteredDrafts();
    let html = '<table><thead><tr>';
    columns.forEach(c => html += `<th>${c.label}</th>`);
    html += '</tr></thead><tbody>';
    data.forEach(d => {
      html += '<tr>';
      columns.forEach(c => html += `<td>${this.getCellValue(d, c.key)}</td>`);
      html += '</tr>';
    });
    html += '</tbody></table>';
    const blob = new Blob([`<html><head><meta charset="UTF-8"></head><body>${html}</body></html>`], { type: 'application/vnd.ms-excel' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'crpc-complaints.xls';
    a.click();
    URL.revokeObjectURL(url);
  }

  private downloadFile(content: string, filename: string, type: string) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
