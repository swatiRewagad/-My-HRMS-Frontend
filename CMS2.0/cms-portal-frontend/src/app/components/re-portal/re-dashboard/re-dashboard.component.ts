import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface DashboardStats {
  totalForwarded: number;
  pendingResponse: number;
  responded: number;
  breached: number;
}

interface ReComplaint {
  complaintNumber: string;
  subject: string;
  forwardedDate: string;
  responseDeadline: string;
  status: string;
  category: string;
}

@Component({
  selector: 'app-re-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './re-dashboard.component.html',
  styleUrl: './re-dashboard.component.scss'
})
export class ReDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  loading = signal(true);
  stats = signal<DashboardStats>({ totalForwarded: 0, pendingResponse: 0, responded: 0, breached: 0 });
  complaints = signal<ReComplaint[]>([]);
  filterStatus = signal('');

  entityName = signal('');
  nodalOfficer = signal('');

  filteredComplaints = computed(() => {
    const status = this.filterStatus();
    if (!status) return this.complaints();
    return this.complaints().filter(c => c.status === status);
  });

  ngOnInit() {
    const user = this.auth.currentUser();
    this.entityName.set(user?.firstName ? `${user.firstName} ${user.lastName}` : user?.username || '');
    this.loadDashboard();
  }

  loadDashboard() {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/dashboard`).subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.stats.set({
          totalForwarded: data.totalForwarded || 0,
          pendingResponse: data.pendingResponse || 0,
          responded: data.responded || 0,
          breached: data.breached || 0
        });
        this.complaints.set(data.complaints || []);
        this.nodalOfficer.set(data.nodalOfficerName || '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  getUrgencyClass(complaint: ReComplaint): string {
    if (!complaint.responseDeadline) return '';
    const deadline = new Date(complaint.responseDeadline);
    const now = new Date();
    const diffMs = deadline.getTime() - now.getTime();
    const diffDays = diffMs / (1000 * 60 * 60 * 24);

    if (diffDays < 0) return 'urgency-breached';
    if (diffDays < 2) return 'urgency-red';
    if (diffDays <= 5) return 'urgency-yellow';
    return 'urgency-green';
  }

  getDaysRemaining(deadline: string): string {
    if (!deadline) return '-';
    const diff = new Date(deadline).getTime() - new Date().getTime();
    const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
    if (days < 0) return `${Math.abs(days)}d overdue`;
    if (days === 0) return 'Due today';
    return `${days}d remaining`;
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'pending': 'Pending Response',
      'responded': 'Responded',
      'breached': 'Breached',
      'extension_requested': 'Extension Requested',
      'clarification_requested': 'Clarification Requested'
    };
    return labels[status] || status;
  }

  openComplaint(complaint: ReComplaint) {
    this.router.navigate(['/re-portal/complaints', complaint.complaintNumber]);
  }

  onFilterChange(event: Event) {
    const select = event.target as HTMLSelectElement;
    this.filterStatus.set(select.value);
  }
}
