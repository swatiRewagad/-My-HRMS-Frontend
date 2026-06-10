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

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/queue`, {
      params: { status: 'SENT_TO_REVIEWER' }
    }).subscribe({
        next: (res) => {
          console.log('[ReviewerHome] API response:', res);
          const queueDrafts = (res?.data || []).map((d: any) => this.mapQueueToDraft(d));
          console.log('[ReviewerHome] Mapped drafts:', queueDrafts.length);
          this.drafts.set(queueDrafts);
          this.loading.set(false);
        },
        error: (err) => {
          console.error('[ReviewerHome] API error:', err);
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
      status: 'PENDING_REVIEW',
      category: d.category || 'GENERAL',
      entityName: d.entityName || '',
      deoDecision: 'MAINTAINABLE',
      deoName: d.assignedTo || '',
      assignedAt: d.createdAt || new Date().toISOString(),
      ageing: Math.max(0, Math.floor(hours / 24)),
      priority: hours > 48 ? 'HIGH' : hours > 24 ? 'MEDIUM' : 'LOW',
      vernacular: d.isVernacular || false,
    };
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/reviewer/draft', draftId]);
  }
}
