import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

interface TransferComplaint {
  complaintId: string;
  complaintNumber: string;
  from: string;
  pending: number;
  fromOffice: string;
  targetOffice: string;
  status: string;
  entityName: string;
  proposedCategory: string;
  creationDate: string;
  language: string;
  territory: string;
  subject: string;
  comment: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  description: string;
  complaintStatus: string;
  assignedOfficer: string;
  timeline: { action: string; fromStatus: string; toStatus: string; timestamp: string; remarks: string }[];
}

interface OfficeThreshold {
  officeId: string;
  officeName: string;
  department: string;
  maxThreshold: number;
  currentCount: number;
  overflowSequenceOrder: number;
  active: boolean;
}

@Component({
  selector: 'app-ops-head',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './ops-head.component.html',
  styleUrl: './ops-head.component.scss'
})
export class OpsHeadComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  loading = signal(true);
  complaints = signal<TransferComplaint[]>([]);
  selectedComplaint = signal<TransferComplaint | null>(null);
  detailSections = { basic: true, eligibility: false, entity: true };
  sidebarItem = signal('transfers');

  // Filters & sorting
  searchText = signal('');
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';
  currentPage = signal(1);
  pageSize = 10;

  // Forward panel
  forwardLanguage = '';
  forwardTerritory = '';
  reassignTerritory = '';
  changeTerritory = false;

  // Status filter tabs
  statusFilter = signal('ALL');

  // Confirmation dialog
  showConfirmDialog = signal(false);
  confirmAction = signal<'approve' | 'reject'>('approve');
  confirmComments = '';
  processing = signal(false);
  actionResult = signal('');
  actionSuccess = signal(false);

  // Full-screen confirmation (matches the RBIO/CRPC success-page pattern; stays until
  // the user dismisses it, instead of a brief inline banner that auto-redirects).
  showSuccessPage = signal(false);
  successAction = signal<'approve' | 'reject'>('approve');
  successComplaintNumber = '';
  successAssignedOfficer = '';

  // Office Thresholds
  officeThresholds = signal<OfficeThreshold[]>([]);
  loadingThresholds = signal(false);
  editingThresholdId = signal<string | null>(null);
  editThresholdValue = 0;

  // Bulk Reassignment
  selectedForBulk = signal<Set<string>>(new Set());
  bulkAssignTo = '';
  bulkReason = '';
  showBulkDialog = signal(false);
  bulkProcessing = signal(false);

  // Transfer History
  transferHistory = signal<any[]>([]);
  loadingHistory = signal(false);

  languages = ['English', 'Hindi', 'Marathi', 'Tamil', 'Telugu', 'Kannada', 'Bengali', 'Gujarati', 'Malayalam', 'Punjabi', 'Odia', 'Urdu'];
  territories = ['Mumbai', 'Delhi', 'Chennai', 'Kolkata', 'Bangalore', 'Hyderabad', 'Ahmedabad', 'Pune', 'Jaipur', 'Lucknow', 'Chandigarh', 'Bhopal', 'Thiruvananthapuram', 'Bhubaneswar', 'Guwahati', 'Patna'];

  stats = computed(() => {
    const all = this.complaints();
    return {
      total: all.length,
      totalTransfers: all.filter(c => c.status.includes('Sent')).length,
      intraRbio: all.filter(c => c.fromOffice.includes('RBIO') && c.targetOffice.includes('RBIO')).length,
      withinCrpc: all.filter(c => c.fromOffice.includes('CRPC') && c.targetOffice.includes('CRPC')).length,
      rbioToCrpc: all.filter(c => c.fromOffice.includes('RBIO') && c.targetOffice.includes('CRPC')).length,
      crpcToRbio: all.filter(c => c.fromOffice.includes('CRPC') && c.targetOffice.includes('RBIO')).length,
      crpcToRbioDept: all.filter(c => c.targetOffice.includes('RBI Dept')).length,
    };
  });

  statusCounts = computed(() => {
    const all = this.complaints();
    return {
      all: all.length,
      draft: all.filter(c => c.status === 'DRAFT').length,
      inProgress: all.filter(c => c.status === 'PENDING').length,
      sentBack: all.filter(c => c.status === 'REJECTED').length,
      assessmentComplete: all.filter(c => c.status === 'APPROVED').length,
    };
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();
    const filter = this.statusFilter();
    if (filter === 'DRAFT') result = result.filter(c => c.status === 'DRAFT');
    else if (filter === 'IN_PROGRESS') result = result.filter(c => c.status === 'PENDING');
    else if (filter === 'SENT_BACK') result = result.filter(c => c.status === 'REJECTED');
    else if (filter === 'ASSESSMENT_COMPLETE') result = result.filter(c => c.status === 'APPROVED');
    const search = this.searchText().toLowerCase();
    if (search) {
      result = result.filter(c =>
        c.complaintId.toLowerCase().includes(search) ||
        c.complaintNumber.toLowerCase().includes(search) ||
        c.entityName.toLowerCase().includes(search) ||
        c.fromOffice.toLowerCase().includes(search) ||
        c.targetOffice.toLowerCase().includes(search)
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
    for (let i = Math.max(1, current - 2); i <= Math.min(total, current + 2); i++) pages.push(i);
    return pages;
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadTransferComplaints();
  }

  loadError = signal(false);

  private loadTransferComplaints() {
    this.loading.set(true);
    this.loadError.set(false);
    // /transfers/all (not /pending) — the dashboard needs to keep showing a complaint
    // after it's been approved/rejected, with its updated status, not drop it entirely.
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/crpc/head/transfers/all`).subscribe({
      next: (res) => {
        const data = Array.isArray(res) ? res : (res?.data || []);
        this.complaints.set(data);
        this.loading.set(false);
      },
      error: () => {
        // No mock fallback here on purpose — a failed request should be visible as an
        // error, not silently replaced with fabricated rows that look like real data.
        this.complaints.set([]);
        this.loading.set(false);
        this.loadError.set(true);
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

  openComplaint(complaint: TransferComplaint) {
    this.selectedComplaint.set(complaint);
    this.forwardLanguage = complaint.language || '';
    this.forwardTerritory = complaint.territory || '';
    this.reassignTerritory = '';
    this.changeTerritory = false;
    this.actionResult.set('');
  }

  closeDetail() {
    this.selectedComplaint.set(null);
  }

  openConfirmDialog(action: 'approve' | 'reject') {
    this.confirmAction.set(action);
    this.confirmComments = '';
    this.showConfirmDialog.set(true);
  }

  cancelConfirm() {
    this.showConfirmDialog.set(false);
    this.confirmComments = '';
  }

  dismissSuccessPage() {
    this.showSuccessPage.set(false);
    this.selectedComplaint.set(null);
  }

  // Reflects the complaint's real current state once resolved, not just the transfer's
  // own PENDING/APPROVED/REJECTED status — e.g. "With DO" once assigned to an officer.
  getStatusLabel(t: TransferComplaint): string {
    if (t.status === 'PENDING') return 'Sent to Other Office';
    if (t.status === 'APPROVED') return t.assignedOfficer ? `With DO (${t.assignedOfficer})` : 'With DO';
    if (t.status === 'REJECTED') return 'Sent Back';
    return t.status;
  }

  submitConfirm() {
    const complaint = this.selectedComplaint();
    if (!complaint) return;

    this.processing.set(true);
    const action = this.confirmAction();
    const username = this.auth.currentUser()?.username || '';

    const endpoint = action === 'approve'
      ? `${environment.apiBaseUrl}/api/v1/crpc/head/transfers/${complaint.complaintId}/approve`
      : `${environment.apiBaseUrl}/api/v1/crpc/head/transfers/${complaint.complaintId}/reject`;

    const body: any = action === 'approve'
      ? { approvedBy: username, comment: this.confirmComments }
      : { approvedBy: username, rejectionComment: this.confirmComments };
    if (action === 'approve' && this.changeTerritory && this.reassignTerritory) {
      body.overrideToOffice = this.reassignTerritory;
    }

    this.http.post<any>(endpoint, body).subscribe({
      next: (res) => {
        this.processing.set(false);
        this.showConfirmDialog.set(false);
        this.loadTransferComplaints();
        this.successAction.set(action);
        this.successComplaintNumber = complaint.complaintNumber;
        this.successAssignedOfficer = res?.assignedOfficer || '';
        this.showSuccessPage.set(true);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
        this.processing.set(false);
        this.showConfirmDialog.set(false);
      }
    });
  }

  officeList = signal<{ officeCode: string; officeName: string; officeType: string }[]>([]);

  loadOfficeList() {
    if (this.officeList().length > 0) return;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/offices`).subscribe({
      next: (res) => this.officeList.set(res?.data || []),
      error: () => this.officeList.set([])
    });
  }

  onChangeTerritoryToggle() {
    if (this.changeTerritory) {
      this.loadOfficeList();
    }
  }

  navigateTo(item: string) {
    this.sidebarItem.set(item);
    if (item === 'home') this.router.navigate(['/crpc/home']);
    if (item === 'reviewer') this.router.navigate(['/crpc/reviewer']);
    if (item === 'thresholds') this.loadOfficeThresholds();
    if (item === 'history') this.loadTransferHistory();
  }

  // Office Thresholds methods
  loadOfficeThresholds() {
    this.loadingThresholds.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/crpc/head/office-thresholds`).subscribe({
      next: (res) => {
        const data = Array.isArray(res) ? res : (res?.data || []);
        this.officeThresholds.set(data);
        this.loadingThresholds.set(false);
      },
      error: () => {
        this.officeThresholds.set([
          { officeId: 'CRPC-CHD', officeName: 'CRPC Chandigarh', department: 'CEPC', maxThreshold: 100, currentCount: 78, overflowSequenceOrder: 1, active: true },
          { officeId: 'CRPC-MUM', officeName: 'CRPC Mumbai', department: 'CEPC', maxThreshold: 120, currentCount: 115, overflowSequenceOrder: 2, active: true },
          { officeId: 'CRPC-DEL', officeName: 'CRPC Delhi', department: 'CEPC', maxThreshold: 100, currentCount: 45, overflowSequenceOrder: 3, active: true },
        ]);
        this.loadingThresholds.set(false);
      }
    });
  }

  startEditThreshold(officeId: string, currentMax: number) {
    this.editingThresholdId.set(officeId);
    this.editThresholdValue = currentMax;
  }

  saveThreshold(officeId: string) {
    this.http.put(`${environment.apiBaseUrl}/api/v1/crpc/head/office-thresholds/${officeId}`, null, {
      params: { threshold: this.editThresholdValue.toString() }
    }).subscribe({
      next: () => {
        this.editingThresholdId.set(null);
        this.loadOfficeThresholds();
      },
      error: () => this.editingThresholdId.set(null)
    });
  }

  cancelEditThreshold() {
    this.editingThresholdId.set(null);
  }

  resetCounters(department: string) {
    this.http.post(`${environment.apiBaseUrl}/api/v1/crpc/head/office-thresholds/reset`, null, {
      params: { department }
    }).subscribe({ next: () => this.loadOfficeThresholds() });
  }

  // Bulk Reassignment methods
  toggleBulkSelect(id: string) {
    const current = this.selectedForBulk();
    const next = new Set(current);
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selectedForBulk.set(next);
  }

  selectAllForBulk() {
    const ids = this.paginatedComplaints().map(c => c.complaintId);
    this.selectedForBulk.set(new Set(ids));
  }

  clearBulkSelection() {
    this.selectedForBulk.set(new Set());
  }

  openBulkReassign() {
    this.bulkAssignTo = '';
    this.bulkReason = '';
    this.showBulkDialog.set(true);
  }

  submitBulkReassign() {
    const ids = Array.from(this.selectedForBulk());
    if (ids.length === 0 || !this.bulkAssignTo) return;
    this.bulkProcessing.set(true);

    this.http.post(`${environment.apiBaseUrl}/api/v1/crpc/head/bulk-reassign`, {
      complaintIds: ids,
      assignTo: this.bulkAssignTo,
      reason: this.bulkReason
    }).subscribe({
      next: () => {
        this.bulkProcessing.set(false);
        this.showBulkDialog.set(false);
        this.selectedForBulk.set(new Set());
        this.loadTransferComplaints();
      },
      error: () => this.bulkProcessing.set(false)
    });
  }

  cancelBulkReassign() {
    this.showBulkDialog.set(false);
  }

  // Transfer History
  loadTransferHistory() {
    this.loadingHistory.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/crpc/head/transfers/history`).subscribe({
      next: (res) => {
        const data = Array.isArray(res) ? res : (res?.data || []);
        this.transferHistory.set(data);
        this.loadingHistory.set(false);
      },
      error: () => this.loadingHistory.set(false)
    });
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }
}
