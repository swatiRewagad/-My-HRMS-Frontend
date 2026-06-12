import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

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
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  description: string;
  timeline: { action: string; fromStatus: string; toStatus: string; timestamp: string; remarks: string }[];
}

@Component({
  selector: 'app-ops-head',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  // Confirmation dialog
  showConfirmDialog = signal(false);
  confirmAction = signal<'approve' | 'reject'>('approve');
  confirmComments = '';
  processing = signal(false);
  actionResult = signal('');
  actionSuccess = signal(false);

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

  filteredComplaints = computed(() => {
    let result = this.complaints();
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

  private loadTransferComplaints() {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/crpc/transfers`).subscribe({
      next: (res) => {
        this.complaints.set(res?.data || []);
        this.loading.set(false);
      },
      error: () => {
        this.complaints.set(this.getMockData());
        this.loading.set(false);
      }
    });
  }

  private getMockData(): TransferComplaint[] {
    const statuses = ['Sent to Other', 'Sent to DO', 'Pending Approval', 'Sent Back'];
    const offices = ['CRPC Mumbai', 'CRPC Delhi', 'RBIO Chennai', 'RBIO Kolkata', 'CRPC Bangalore', 'RBI Dept - Banking Supervision'];
    const entities = ['HDFC Bank', 'ICICI Bank', 'SBI', 'Axis Bank', 'PNB', 'Bank of Baroda', 'Kotak Mahindra', 'IndusInd Bank'];
    const data: TransferComplaint[] = [];
    for (let i = 1; i <= 25; i++) {
      data.push({
        complaintId: `CMP-${6500 + i}`,
        complaintNumber: `CRPC/2024-25/${String(i).padStart(5, '0')}`,
        from: `user${i}@email.com`,
        pending: Math.floor(Math.random() * 15) + 1,
        fromOffice: offices[Math.floor(Math.random() * 3)],
        targetOffice: offices[Math.floor(Math.random() * offices.length)],
        status: statuses[Math.floor(Math.random() * statuses.length)],
        entityName: entities[Math.floor(Math.random() * entities.length)],
        proposedCategory: ['Banking', 'Credit Card', 'Insurance', 'ATM', 'Digital Payment'][Math.floor(Math.random() * 5)],
        creationDate: new Date(Date.now() - Math.random() * 30 * 86400000).toISOString().split('T')[0],
        language: ['English', 'Hindi', 'Marathi', 'Tamil'][Math.floor(Math.random() * 4)],
        territory: ['Mumbai', 'Delhi', 'Chennai', 'Kolkata', 'Bangalore'][Math.floor(Math.random() * 5)],
        subject: `Complaint regarding ${entities[Math.floor(Math.random() * entities.length)]} services`,
        complainantName: `Complainant ${i}`,
        complainantEmail: `user${i}@email.com`,
        complainantPhone: `98765${String(43210 + i).padStart(5, '0')}`,
        description: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Customer is facing issues with banking services.',
        timeline: [
          { action: 'Created', fromStatus: '', toStatus: 'New', timestamp: new Date(Date.now() - 20 * 86400000).toISOString(), remarks: 'Complaint registered' },
          { action: 'Assigned', fromStatus: 'New', toStatus: 'Assigned', timestamp: new Date(Date.now() - 18 * 86400000).toISOString(), remarks: 'Auto-assigned via round robin' },
          { action: 'Transfer Requested', fromStatus: 'Assigned', toStatus: 'Sent to Other', timestamp: new Date(Date.now() - 5 * 86400000).toISOString(), remarks: 'Transferred to target office' },
        ]
      });
    }
    return data;
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

  submitConfirm() {
    const complaint = this.selectedComplaint();
    if (!complaint) return;

    this.processing.set(true);
    const action = this.confirmAction();

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/crpc/transfer-action/${complaint.complaintId}`,
      {
        action: action === 'approve' ? 'APPROVE_TRANSFER' : 'REJECT_TRANSFER',
        remarks: this.confirmComments,
        actor: this.auth.currentUser()?.username || '',
        language: this.forwardLanguage,
        territory: this.forwardTerritory,
        reassignTerritory: this.reassignTerritory
      }
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        this.actionResult.set(`Transfer ${action === 'approve' ? 'approved' : 'rejected'} successfully.`);
        this.processing.set(false);
        this.showConfirmDialog.set(false);
        this.loadTransferComplaints();
        setTimeout(() => this.selectedComplaint.set(null), 1500);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
        this.processing.set(false);
        this.showConfirmDialog.set(false);
      }
    });
  }

  getExpectedStatusChange(): string {
    const current = this.selectedComplaint()?.status || '';
    if (this.confirmAction() === 'approve') {
      return `${current} → Sent to DO`;
    }
    return `${current} → Sent Back to Deputy Ombudsman`;
  }

  navigateTo(item: string) {
    this.sidebarItem.set(item);
    if (item === 'home') this.router.navigate(['/crpc/home']);
    if (item === 'reviewer') this.router.navigate(['/crpc/reviewer']);
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }
}
