import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface SupervisorComplaint {
  complaintNumber: string;
  complaintId: string;
  subject: string;
  complainantName: string;
  entityName: string;
  status: string;
  priority: string;
  assignedOfficer: string;
  slaDueDate: string;
  assignedAt: string;
  workflowStage: string;
}

@Component({
  selector: 'app-rbio-supervisor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-supervisor-dashboard.component.html',
  styleUrl: './rbio-supervisor-dashboard.component.scss'
})
export class RbioSupervisorDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaints = signal<SupervisorComplaint[]>([]);
  loading = signal(true);
  processing = signal(false);
  actionResult = signal('');
  actionSuccess = signal(false);

  stats = computed(() => {
    const all = this.complaints();
    const now = new Date();
    return {
      total: all.length,
      pendingReview: all.filter(c => (c.status || '').toLowerCase() === 'escalated').length,
      breachedSla: all.filter(c => {
        if (!c.slaDueDate) return false;
        return new Date(c.slaDueDate).getTime() < now.getTime();
      }).length,
      inConciliation: all.filter(c => (c.workflowStage || c.status || '').toLowerCase().includes('conciliation')).length,
      inAdjudication: all.filter(c => (c.workflowStage || c.status || '').toLowerCase().includes('adjudication')).length
    };
  });

  escalatedComplaints = computed(() => {
    return this.complaints().filter(c => (c.status || '').toLowerCase() === 'escalated');
  });

  officerWorkload = computed(() => {
    const workloadMap = new Map<string, number>();
    for (const c of this.complaints()) {
      const officer = c.assignedOfficer || 'Unassigned';
      workloadMap.set(officer, (workloadMap.get(officer) || 0) + 1);
    }
    return Array.from(workloadMap.entries())
      .map(([officer, count]) => ({ officer, count }))
      .sort((a, b) => b.count - a.count);
  });

  maxWorkload = computed(() => {
    const workloads = this.officerWorkload();
    return workloads.length > 0 ? workloads[0].count : 1;
  });

  slaComplianceData = computed(() => {
    const all = this.complaints();
    const now = new Date();
    let onTrack = 0;
    let atRisk = 0;
    let breached = 0;

    for (const c of all) {
      if (!c.slaDueDate) { onTrack++; continue; }
      const days = Math.ceil((new Date(c.slaDueDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
      if (days < 0) breached++;
      else if (days <= 5) atRisk++;
      else onTrack++;
    }

    const total = all.length || 1;
    return {
      onTrack, atRisk, breached,
      onTrackPct: (onTrack / total) * 100,
      atRiskPct: (atRisk / total) * 100,
      breachedPct: (breached / total) * 100
    };
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadComplaints();
  }

  private loadComplaints() {
    this.loading.set(true);
    const officer = this.auth.currentUser()?.username || '';

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/rbio/all-tasks?officer=${officer}&role=SUPERVISOR`)
      .subscribe({
        next: (res) => {
          this.complaints.set(res?.data || []);
          this.loading.set(false);
        },
        error: () => {
          this.complaints.set([]);
          this.loading.set(false);
        }
      });
  }

  quickAction(complaintNumber: string, action: string) {
    this.processing.set(true);
    this.actionResult.set('');
    const actor = this.auth.currentUser()?.username || '';

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      { action, actor, remarks: `Quick action by supervisor: ${action}` }
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        this.actionResult.set(`Action "${action}" on ${complaintNumber} successful.`);
        this.processing.set(false);
        this.loadComplaints();
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(err.error?.message || `Failed: ${action}`);
        this.processing.set(false);
      }
    });
  }

  openComplaint(complaintNumber: string) {
    this.router.navigate(['/staff/rbio/task', complaintNumber]);
  }

  goBack() {
    this.router.navigate(['/staff/rbio/tasks']);
  }
}
