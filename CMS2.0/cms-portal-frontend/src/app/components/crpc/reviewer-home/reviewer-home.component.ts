import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

interface ReviewDraft {
  draftId: string;
  complainantName: string;
  fromEmailId: string;
  subject: string;
  modeOfReceipt: string;
  status: 'PENDING_REVIEW' | 'APPROVED' | 'SENT_BACK' | 'CLOSED_NM';
  category: string;
  entityName: string;
  deoDecision: 'MAINTAINABLE' | 'NON_MAINTAINABLE';
  deoName: string;
  assignedAt: string;
  ageing: number;
  priority: string;
  vernacular: boolean;
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

  drafts = signal<ReviewDraft[]>([]);
  loading = signal(false);

  filterStatus = '';
  filterDeoDecision = '';
  searchText = '';

  stats = computed(() => {
    const all = this.drafts();
    return {
      total: all.length,
      pending: all.filter(d => d.status === 'PENDING_REVIEW').length,
      approved: all.filter(d => d.status === 'APPROVED').length,
      sentBack: all.filter(d => d.status === 'SENT_BACK').length,
      closedNm: all.filter(d => d.status === 'CLOSED_NM').length,
    };
  });

  filteredDrafts = computed(() => {
    let result = this.drafts();
    if (this.filterStatus) result = result.filter(d => d.status === this.filterStatus);
    if (this.filterDeoDecision) result = result.filter(d => d.deoDecision === this.filterDeoDecision);
    if (this.searchText) {
      const q = this.searchText.toLowerCase();
      result = result.filter(d =>
        d.draftId.toLowerCase().includes(q) ||
        d.complainantName.toLowerCase().includes(q) ||
        d.subject.toLowerCase().includes(q)
      );
    }
    return result;
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
    setTimeout(() => {
      this.drafts.set([
        { draftId: 'DRF-001', complainantName: 'Rajesh Kumar', fromEmailId: 'rajesh@example.com', subject: 'ATM cash not dispensed', modeOfReceipt: 'EMAIL', status: 'PENDING_REVIEW', category: 'ATM', entityName: 'SBI', deoDecision: 'MAINTAINABLE', deoName: 'Ramesh Patil', assignedAt: '2026-06-01T14:31:00Z', ageing: 1, priority: 'MEDIUM', vernacular: false },
        { draftId: 'DRF-003', complainantName: 'अमित पटेल', fromEmailId: 'amit@gmail.com', subject: 'UPI transaction failed', modeOfReceipt: 'EMAIL', status: 'PENDING_REVIEW', category: 'UPI', entityName: 'ICICI Bank', deoDecision: 'NON_MAINTAINABLE', deoName: 'Kavitha Nair', assignedAt: '2026-05-31T16:00:00Z', ageing: 2, priority: 'LOW', vernacular: true },
        { draftId: 'DRF-006', complainantName: 'Anita Roy', fromEmailId: 'anita.r@outlook.com', subject: 'Loan processing delay', modeOfReceipt: 'CPGRAMS', status: 'PENDING_REVIEW', category: 'LOAN', entityName: 'BOB', deoDecision: 'MAINTAINABLE', deoName: 'Arjun Singh', assignedAt: '2026-06-02T09:00:00Z', ageing: 0, priority: 'HIGH', vernacular: false },
        { draftId: 'DRF-004', complainantName: 'Sunita Devi', fromEmailId: '', subject: 'Credit card charges', modeOfReceipt: 'CPGRAMS', status: 'APPROVED', category: 'CREDIT_CARD', entityName: 'Axis Bank', deoDecision: 'MAINTAINABLE', deoName: 'Ramesh Patil', assignedAt: '2026-05-30T10:00:00Z', ageing: 3, priority: 'HIGH', vernacular: false },
      ]);
      this.loading.set(false);
    }, 500);
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/reviewer/draft', draftId]);
  }
}
