import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

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
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  drafts = signal<ReviewDraft[]>([]);
  loading = signal(false);

  filterStatus = signal('');
  filterDeoDecision = signal('');
  searchText = signal('');

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
    const status = this.filterStatus();
    const deoDecision = this.filterDeoDecision();
    const search = this.searchText();
    if (status) result = result.filter(d => d.status === status);
    if (deoDecision) result = result.filter(d => d.deoDecision === deoDecision);
    if (search) {
      const q = search.toLowerCase();
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

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/queue`, {
      params: { assignedTo: username }
    }).subscribe({
        next: (res) => {
          const queueDrafts = (res?.data || [])
            .filter((d: any) => ['SENT_TO_REVIEWER', 'APPROVED_ROUTED', 'SENT_BACK_TO_DEO', 'CLOSED_NOT_A_COMPLAINT'].includes(d.status))
            .map((d: any) => this.mapQueueToDraft(d));
          this.drafts.set(queueDrafts);
          this.loading.set(false);
        },
        error: () => {
          this.drafts.set([]);
          this.loading.set(false);
        }
      });
  }

  private mapQueueToDraft(d: any): ReviewDraft {
    const hours = (Date.now() - new Date(d.receivedAt || d.createdAt).getTime()) / 3600000;
    return {
      draftId: d.draftId || '',
      complainantName: d.complainantName || '',
      fromEmailId: d.senderEmail || '',
      subject: d.subject || '',
      modeOfReceipt: d.modeOfReceipt || 'EMAIL',
      status: this.mapReviewerStatus(d.status),
      category: d.category || 'GENERAL',
      entityName: d.entityName || '',
      deoDecision: 'MAINTAINABLE',
      deoName: d.processedBy || d.assignedTo || '',
      assignedAt: d.createdAt || new Date().toISOString(),
      ageing: Math.max(0, Math.floor(hours / 24)),
      priority: hours > 48 ? 'HIGH' : hours > 24 ? 'MEDIUM' : 'LOW',
      vernacular: d.isVernacular || false,
    };
  }

  private mapReviewerStatus(status: string): 'PENDING_REVIEW' | 'APPROVED' | 'SENT_BACK' | 'CLOSED_NM' {
    switch (status) {
      case 'APPROVED_ROUTED': return 'APPROVED';
      case 'SENT_BACK_TO_DEO': return 'SENT_BACK';
      case 'CLOSED_NOT_A_COMPLAINT': return 'CLOSED_NM';
      default: return 'PENDING_REVIEW';
    }
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/reviewer/draft', draftId]);
  }
}
