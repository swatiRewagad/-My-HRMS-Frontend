import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';

interface CepcComplaint {
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  entityName: string;
  entityType: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  department: string;
  assignedRole: string;
  assignedOfficer: string;
  workflowStage: string;
}

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

@Component({
  selector: 'app-cepc-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, CepcSlaIndicatorComponent],
  templateUrl: './cepc-dashboard.component.html',
  styleUrl: './cepc-dashboard.component.scss'
})
export class CepcDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaints = signal<CepcComplaint[]>([]);
  loading = signal(true);
  userRole = signal<CepcRole>('CEPC_DO');
  filterStatus = '';
  searchText = '';
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  currentPage = signal(1);
  pageSize = 15;
  Math = Math;

  roleLabels: Record<CepcRole, string> = {
    'CEPC_DO': 'Dealing Officer',
    'CEPC_REVIEWER': 'Reviewer',
    'CEPC_INCHARGE': 'In Charge',
    'CEPC_CLOSING_AUTHORITY': 'Closing Authority',
    'CEPC_ADMIN': 'Admin',
    'CEPC_CONTACT_PERSON': 'Contact Person'
  };

  // ─── Create Complaint Dialog (DO only) ───
  showCreateDialog = signal(false);
  newComplaint = {
    complainantName: '', complainantEmail: '', complainantPhone: '',
    complainantAddress: '', subject: '', description: '',
    entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
  };
  creating = signal(false);
  createSuccess = signal('');
  createError = signal('');

  stats = computed(() => {
    const all = this.complaints();
    return {
      total: all.length,
      pending: all.filter(c => ['assigned', 'pending', 'new'].includes(c.status)).length,
      inProgress: all.filter(c => c.status === 'in_progress').length,
      underReview: all.filter(c => ['under_review', 'reviewer_review', 'incharge_review'].includes(c.status)).length,
      awaitingClosure: all.filter(c => c.status === 'awaiting_closure').length,
      escalated: all.filter(c => c.status === 'escalated').length,
      closed: all.filter(c => ['closed', 'resolved'].includes(c.status)).length,
    };
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();
    if (this.filterStatus) {
      if (this.filterStatus === 'pending') {
        result = result.filter(c => ['assigned', 'pending', 'new'].includes(c.status));
      } else if (this.filterStatus === 'under_review') {
        result = result.filter(c => ['under_review', 'reviewer_review', 'incharge_review'].includes(c.status));
      } else {
        result = result.filter(c => c.status === this.filterStatus);
      }
    }
    if (this.searchText) {
      const q = this.searchText.toLowerCase();
      result = result.filter(c =>
        c.complaintNumber.toLowerCase().includes(q) ||
        c.complainantName.toLowerCase().includes(q) ||
        c.entityName.toLowerCase().includes(q) ||
        c.subject.toLowerCase().includes(q)
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

  paginatedComplaints = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredComplaints().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredComplaints().length / this.pageSize)));

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('CEPC_ADMIN')) this.userRole.set('CEPC_ADMIN');
    else if (roles.includes('CEPC_CLOSING_AUTHORITY')) this.userRole.set('CEPC_CLOSING_AUTHORITY');
    else if (roles.includes('CEPC_INCHARGE')) this.userRole.set('CEPC_INCHARGE');
    else if (roles.includes('CEPC_REVIEWER')) this.userRole.set('CEPC_REVIEWER');
    else if (roles.includes('CEPC_CONTACT_PERSON')) this.userRole.set('CEPC_CONTACT_PERSON');
    else this.userRole.set('CEPC_DO');

    this.loadComplaints();
  }

  loadComplaints() {
    this.loading.set(true);
    const officer = this.auth.currentUser()?.username || '';
    const role = this.userRole();

    let url: string;
    if (role === 'CEPC_ADMIN') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks`;
    } else if (role === 'CEPC_CONTACT_PERSON') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/contact-person/tasks?officer=${officer}`;
    } else {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks?role=${role}`;
    }

    this.http.get<any>(url).subscribe({
      next: (res) => {
        const roleTasks = res?.data || [];
        // Also fetch complaints this officer acted on (history)
        this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/my-actions?officer=${officer}`).subscribe({
          next: (actionsRes) => {
            const actionTasks: CepcComplaint[] = actionsRes?.data || [];
            // Merge: add action-history complaints not already in role tasks
            const existingIds = new Set(roleTasks.map((t: any) => t.complaintNumber));
            const merged = [...roleTasks, ...actionTasks.filter((t: any) => !existingIds.has(t.complaintNumber))];
            this.complaints.set(merged);
            this.loading.set(false);
          },
          error: () => {
            this.complaints.set(roleTasks);
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  // ─── Create Complaint (CEPC DO) ───
  openCreateDialog() {
    this.showCreateDialog.set(true);
    this.createSuccess.set('');
    this.createError.set('');
    this.newComplaint = {
      complainantName: '', complainantEmail: '', complainantPhone: '',
      complainantAddress: '', subject: '', description: '',
      entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
    };
  }

  closeCreateDialog() {
    this.showCreateDialog.set(false);
  }

  submitNewComplaint() {
    if (!this.newComplaint.complainantName || !this.newComplaint.subject) {
      this.createError.set('Complainant Name and Subject are required.');
      return;
    }
    this.creating.set(true);
    this.createError.set('');

    const payload = {
      ...this.newComplaint,
      createdBy: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/create-complaint`, payload)
      .subscribe({
        next: (res) => {
          this.creating.set(false);
          this.createSuccess.set(`Complaint ${res?.data?.complaintNumber} created successfully.`);
          setTimeout(() => {
            this.showCreateDialog.set(false);
            this.loadComplaints();
          }, 1500);
        },
        error: (err) => {
          this.creating.set(false);
          this.createError.set(err.error?.message || 'Failed to create complaint.');
        }
      });
  }

  openComplaint(complaint: CepcComplaint) {
    this.router.navigate(['/cepc/complaint', complaint.complaintNumber]);
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  isOverdue(complaint: CepcComplaint): boolean {
    if (!complaint.slaDueDate) return false;
    return new Date(complaint.slaDueDate) < new Date();
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'assigned': 'Assigned',
      'pending': 'Pending',
      'new': 'New',
      'in_progress': 'Under Examination',
      'under_review': 'Under Review',
      'reviewer_review': 'Reviewer Review',
      'incharge_review': 'In Charge Review',
      'awaiting_closure': 'Awaiting Closure',
      'escalated': 'Escalated',
      'sent_back': 'Sent Back',
      'info_requested': 'Info Requested',
      'forwarded': 'Forwarded to Dept',
      'forwarded_to_contact': 'With Contact Person',
      'closed': 'Closed',
      'resolved': 'Resolved',
    };
    return labels[status] || status;
  }

  async logout() {
    await this.auth.logout();
  }

  goBack() {
    this.router.navigate(['/staff/dashboard']);
  }
}
