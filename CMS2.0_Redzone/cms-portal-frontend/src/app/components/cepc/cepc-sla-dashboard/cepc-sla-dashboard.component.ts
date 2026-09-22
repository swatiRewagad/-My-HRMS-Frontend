import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';

interface SlaComplaint {
  complaintNumber: string;
  subject: string;
  assignedOfficer: string;
  slaDueDate: string;
  status: string;
  priority: string;
}

@Component({
  selector: 'app-cepc-sla-dashboard',
  standalone: true,
  imports: [CommonModule, CepcSlaIndicatorComponent],
  templateUrl: './cepc-sla-dashboard.component.html',
  styleUrl: './cepc-sla-dashboard.component.scss'
})
export class CepcSlaDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaints = signal<SlaComplaint[]>([]);
  loading = signal(true);

  private terminalStates = ['closed', 'resolved', 'rejected', 'withdrawn'];

  activeComplaints = computed(() => {
    return this.complaints().filter(c => !this.terminalStates.includes((c.status || '').toLowerCase()));
  });

  stats = computed(() => {
    const active = this.activeComplaints();
    const now = new Date();
    let onTrack = 0;
    let atRisk = 0;
    let breached = 0;

    for (const c of active) {
      if (!c.slaDueDate) { onTrack++; continue; }
      const days = Math.ceil((new Date(c.slaDueDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
      if (days < 0) breached++;
      else if (days < 2) atRisk++;
      else onTrack++;
    }

    return { total: active.length, onTrack, atRisk, breached };
  });

  compliancePercent = computed(() => {
    const s = this.stats();
    if (s.total === 0) return 100;
    return Math.round(((s.total - s.breached) / s.total) * 100);
  });

  breachedComplaints = computed(() => {
    const now = new Date();
    return this.activeComplaints()
      .filter(c => {
        if (!c.slaDueDate) return false;
        return new Date(c.slaDueDate).getTime() < now.getTime();
      })
      .map(c => ({
        ...c,
        daysOverdue: Math.abs(Math.ceil((new Date(c.slaDueDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24)))
      }))
      .sort((a, b) => b.daysOverdue - a.daysOverdue);
  });

  atRiskComplaints = computed(() => {
    const now = new Date();
    return this.activeComplaints()
      .filter(c => {
        if (!c.slaDueDate) return false;
        const days = Math.ceil((new Date(c.slaDueDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
        return days >= 0 && days < 2;
      })
      .map(c => ({
        ...c,
        daysRemaining: Math.ceil((new Date(c.slaDueDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
      }));
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadTasks();
  }

  loadTasks() {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks`).subscribe({
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

  openComplaint(complaintNumber: string) {
    this.router.navigate(['/cepc/complaint', complaintNumber]);
  }

  goBack() {
    this.router.navigate(['/cepc/dashboard']);
  }
}
