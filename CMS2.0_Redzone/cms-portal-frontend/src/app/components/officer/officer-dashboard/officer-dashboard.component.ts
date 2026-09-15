import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OfficerService, OfficerComplaint } from '../../../services/officer.service';

@Component({
  selector: 'app-officer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './officer-dashboard.component.html',
  styleUrl: './officer-dashboard.component.scss'
})
export class OfficerDashboardComponent implements OnInit {

  private officerService = inject(OfficerService);
  private router = inject(Router);

  teams = ['ATM_TEAM', 'DIGITAL_TEAM', 'PAYMENT_SYSTEMS_TEAM', 'LENDING_TEAM', 'CARDS_TEAM', 'GENERAL_TEAM'];
  statuses = ['ALL', 'ASSIGNED', 'IN_PROGRESS', 'UNDER_REVIEW', 'ESCALATED'];

  selectedTeam = signal('ATM_TEAM');
  selectedStatus = signal('ALL');
  officerName = signal('Officer');
  complaints = signal<OfficerComplaint[]>([]);
  loading = signal(false);
  error = signal('');

  stats = signal({ total: 0, assigned: 0, inProgress: 0, escalated: 0, slaBreach: 0 });

  ngOnInit() {
    this.loadComplaints();
  }

  loadComplaints() {
    this.loading.set(true);
    this.error.set('');

    const status = this.selectedStatus() === 'ALL' ? undefined : this.selectedStatus();
    this.officerService.getAssignedComplaints(this.selectedTeam(), status).subscribe({
      next: (data) => {
        this.complaints.set(data);
        this.updateStats(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load complaints. Please check if services are running.');
        this.loading.set(false);
      }
    });
  }

  updateStats(data: OfficerComplaint[]) {
    this.stats.set({
      total: data.length,
      assigned: data.filter(c => c.status === 'ASSIGNED').length,
      inProgress: data.filter(c => c.status === 'IN_PROGRESS').length,
      escalated: data.filter(c => c.status === 'ESCALATED').length,
      slaBreach: data.filter(c => c.slaPercentage >= 80).length
    });
  }

  viewComplaint(complaintId: string) {
    this.router.navigate(['/officer/complaint', complaintId]);
  }

  onTeamChange() {
    this.loadComplaints();
  }

  onStatusChange() {
    this.loadComplaints();
  }

  getPriorityClass(priority: string): string {
    return priority?.toLowerCase() || 'medium';
  }

  getSlaClass(percentage: number): string {
    if (percentage >= 100) return 'sla-breach';
    if (percentage >= 80) return 'sla-warning';
    return 'sla-ok';
  }

  getDaysRemaining(slaDueDate: string): string {
    const due = new Date(slaDueDate);
    const now = new Date();
    const diff = Math.ceil((due.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    if (diff < 0) return `${Math.abs(diff)}d overdue`;
    if (diff === 0) return 'Due today';
    return `${diff}d remaining`;
  }
}
