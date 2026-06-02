import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

interface DraftComplaint {
  draftId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmailId: string;
  subject: string;
  modeOfReceipt: 'EMAIL' | 'PHYSICAL_LETTER' | 'PORTAL' | 'CPGRAMS';
  status: 'DRAFT' | 'SENT_TO_REVIEWER' | 'REJECTED_BY_REVIEWER' | 'APPROVED';
  category: string;
  entityName: string;
  state: string;
  district: string;
  systemSuggestion: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | 'PENDING';
  emailType: 'TO' | 'CC_BCC' | null;
  vernacular: boolean;
  assignedAt: string;
  createdAt: string;
  priority: string;
  slaRemaining: number;
  ageing: number;
}

@Component({
  selector: 'app-deo-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './deo-home.component.html',
  styleUrl: './deo-home.component.scss'
})
export class DeoHomeComponent implements OnInit {

  private router = inject(Router);

  drafts = signal<DraftComplaint[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());

  // Filters
  filterStatus = '';
  filterMode = '';
  filterEmailType = '';
  filterSuggestion = '';
  searchText = '';

  // Column configuration (F.11: predefined columns per BRD)
  allColumns = [
    { key: 'draftId', label: 'Draft Complaint ID', visible: true },
    { key: 'category', label: 'Category', visible: true },
    { key: 'subject', label: 'Subject', visible: true },
    { key: 'fromEmailId', label: 'From Email ID', visible: true },
    { key: 'ageing', label: 'Ageing (days)', visible: true },
    { key: 'assignedAt', label: 'Date & Time', visible: true },
    { key: 'status', label: 'Status Code', visible: true },
    { key: 'modeOfReceipt', label: 'Mode of Receipt', visible: true },
    { key: 'systemSuggestion', label: 'System Suggestion', visible: true },
    { key: 'emailType', label: 'Email Type', visible: true },
    { key: 'complainantName', label: 'Complainant', visible: false },
    { key: 'entityName', label: 'Entity Name', visible: false },
    { key: 'state', label: 'State', visible: false },
    { key: 'district', label: 'District', visible: false },
    { key: 'vernacular', label: 'Vernacular', visible: false },
    { key: 'priority', label: 'Priority', visible: true },
    { key: 'slaRemaining', label: 'SLA (hrs)', visible: true },
  ];

  showColumnConfig = signal(false);

  visibleColumns = computed(() => this.allColumns.filter(c => c.visible));

  filteredDrafts = computed(() => {
    let result = this.drafts();
    if (this.filterStatus) result = result.filter(d => d.status === this.filterStatus);
    if (this.filterMode) result = result.filter(d => d.modeOfReceipt === this.filterMode);
    if (this.filterEmailType) result = result.filter(d => d.emailType === this.filterEmailType);
    if (this.filterSuggestion) result = result.filter(d => d.systemSuggestion === this.filterSuggestion);
    if (this.searchText) {
      const q = this.searchText.toLowerCase();
      result = result.filter(d =>
        d.draftId.toLowerCase().includes(q) ||
        d.complainantName.toLowerCase().includes(q) ||
        d.entityName.toLowerCase().includes(q)
      );
    }
    return result;
  });

  // Stats
  stats = computed(() => {
    const all = this.drafts();
    return {
      total: all.length,
      draft: all.filter(d => d.status === 'DRAFT').length,
      sentToReviewer: all.filter(d => d.status === 'SENT_TO_REVIEWER').length,
      rejected: all.filter(d => d.status === 'REJECTED_BY_REVIEWER').length,
      approved: all.filter(d => d.status === 'APPROVED').length,
    };
  });

  loggedInUser: { id: string; name: string; role: string } | null = null;

  ngOnInit() {
    const stored = sessionStorage.getItem('crpc_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    }
    this.loadDrafts();
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.router.navigate(['/crpc/login']);
  }

  loadDrafts() {
    this.loading.set(true);
    // Mock data - replace with service call
    setTimeout(() => {
      this.drafts.set(this.getMockDrafts());
      this.loading.set(false);
    }, 500);
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/draft', draftId]);
  }

  createPhysicalLetter() {
    this.router.navigate(['/crpc/physical-letter']);
  }


  // Bulk actions
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

  bulkMarkNotComplaint() {
    const ids = Array.from(this.selectedIds());
    if (ids.length === 0) return;
    if (!confirm(`Mark ${ids.length} drafts as "Not a Complaint"? This action will be logged in audit trail.`)) return;
    // API call: mark selected as not-a-complaint
    console.log('Bulk mark not-a-complaint:', ids);
    this.selectedIds.set(new Set());
    this.loadDrafts();
  }

  toggleColumnVisibility(key: string) {
    const col = this.allColumns.find(c => c.key === key);
    if (col) col.visible = !col.visible;
  }

  getCellValue(draft: DraftComplaint, key: string): string {
    const val = (draft as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'vernacular') return val ? 'Yes' : 'No';
    if (key === 'assignedAt' || key === 'createdAt') return new Date(val).toLocaleDateString('en-IN');
    return String(val);
  }

  private getMockDrafts(): DraftComplaint[] {
    return [
      { draftId: 'DRF-001', complaintNumber: '', complainantName: 'Rajesh Kumar', fromEmailId: 'rajesh@example.com', subject: 'ATM cash not dispensed', modeOfReceipt: 'EMAIL', status: 'DRAFT', category: 'ATM', entityName: 'SBI', state: 'MH', district: 'Mumbai', systemSuggestion: 'MAINTAINABLE', emailType: 'TO', vernacular: false, assignedAt: '2026-06-01T10:00:00Z', createdAt: '2026-06-01T09:30:00Z', priority: 'MEDIUM', slaRemaining: 48, ageing: 1 },
      { draftId: 'DRF-002', complaintNumber: '', complainantName: 'Priya Sharma', fromEmailId: '', subject: 'Loan EMI overcharge', modeOfReceipt: 'PHYSICAL_LETTER', status: 'DRAFT', category: 'LOAN', entityName: 'HDFC Bank', state: 'DL', district: 'New Delhi', systemSuggestion: 'PENDING', emailType: null, vernacular: false, assignedAt: '2026-06-01T11:00:00Z', createdAt: '2026-06-01T10:45:00Z', priority: 'HIGH', slaRemaining: 24, ageing: 1 },
      { draftId: 'DRF-003', complaintNumber: '', complainantName: 'अमित पटेल', fromEmailId: 'amit.patel@gmail.com', subject: 'UPI transaction failed', modeOfReceipt: 'EMAIL', status: 'SENT_TO_REVIEWER', category: 'UPI', entityName: 'ICICI Bank', state: 'GJ', district: 'Ahmedabad', systemSuggestion: 'NON_MAINTAINABLE', emailType: 'CC_BCC', vernacular: true, assignedAt: '2026-05-31T14:00:00Z', createdAt: '2026-05-31T13:00:00Z', priority: 'LOW', slaRemaining: 72, ageing: 2 },
      { draftId: 'DRF-004', complaintNumber: '', complainantName: 'Sunita Devi', fromEmailId: '', subject: 'Credit card charges disputed', modeOfReceipt: 'CPGRAMS', status: 'REJECTED_BY_REVIEWER', category: 'CREDIT_CARD', entityName: 'Axis Bank', state: 'UP', district: 'Lucknow', systemSuggestion: 'MAINTAINABLE', emailType: null, vernacular: false, assignedAt: '2026-05-30T09:00:00Z', createdAt: '2026-05-30T08:30:00Z', priority: 'HIGH', slaRemaining: 12, ageing: 3 },
      { draftId: 'DRF-005', complaintNumber: 'CMP-20260530-000012', complainantName: 'Mohammed Iqbal', fromEmailId: 'iqbal.m@yahoo.com', subject: 'Fixed deposit premature closure', modeOfReceipt: 'PORTAL', status: 'APPROVED', category: 'DEPOSIT', entityName: 'PNB', state: 'BR', district: 'Patna', systemSuggestion: 'MAINTAINABLE', emailType: null, vernacular: false, assignedAt: '2026-05-29T16:00:00Z', createdAt: '2026-05-29T15:00:00Z', priority: 'MEDIUM', slaRemaining: 96, ageing: 4 },
    ];
  }
}
