import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { OfficerService, ComplaintDetail } from '../../../services/officer.service';

@Component({
  selector: 'app-complaint-action',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './complaint-action.component.html',
  styleUrl: './complaint-action.component.scss'
})
export class ComplaintActionComponent implements OnInit {

  private officerService = inject(OfficerService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  complaint = signal<ComplaintDetail | null>(null);
  loading = signal(true);
  error = signal('');
  actionLoading = signal(false);
  successMessage = signal('');

  // Action form fields
  showResolveForm = signal(false);
  showEscalateForm = signal(false);
  resolutionText = signal('');
  escalationReason = signal('');
  remarks = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadComplaint(id);
  }

  loadComplaint(id: string) {
    this.loading.set(true);
    this.officerService.getComplaintDetail(id).subscribe({
      next: (data) => {
        this.complaint.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load complaint details.');
        this.loading.set(false);
      }
    });
  }

  acceptComplaint() {
    const c = this.complaint();
    console.log('[ACCEPT] Complaint:', c);
    if (!c) return;
    this.actionLoading.set(true);
    this.error.set('');
    this.successMessage.set('');
    console.log('[ACCEPT] Calling API for:', c.complaintId);
    this.officerService.acceptComplaint(c.complaintId, 'Officer').subscribe({
      next: () => {
        console.log('[ACCEPT] Success!');
        this.successMessage.set('Complaint accepted. Status moved to IN_PROGRESS.');
        this.actionLoading.set(false);
        this.loadComplaint(c.complaintId);
      },
      error: (err) => {
        console.error('[ACCEPT] Error:', err);
        this.error.set('Failed to accept complaint: ' + (err.message || err.status));
        this.actionLoading.set(false);
      }
    });
  }

  submitResolution() {
    const c = this.complaint();
    if (!c || !this.resolutionText()) return;
    this.actionLoading.set(true);
    this.officerService.resolveComplaint(c.complaintId, this.resolutionText()).subscribe({
      next: () => {
        this.successMessage.set('Resolution submitted. Complaint moved to RESOLVED.');
        this.showResolveForm.set(false);
        this.resolutionText.set('');
        this.actionLoading.set(false);
        this.loadComplaint(c.complaintId);
      },
      error: () => {
        this.error.set('Failed to submit resolution.');
        this.actionLoading.set(false);
      }
    });
  }

  submitEscalation() {
    const c = this.complaint();
    if (!c || !this.escalationReason()) return;
    this.actionLoading.set(true);
    this.officerService.escalateComplaint(c.complaintId, this.escalationReason()).subscribe({
      next: () => {
        this.successMessage.set('Complaint escalated to senior officer.');
        this.showEscalateForm.set(false);
        this.escalationReason.set('');
        this.actionLoading.set(false);
        this.loadComplaint(c.complaintId);
      },
      error: () => {
        this.error.set('Failed to escalate complaint.');
        this.actionLoading.set(false);
      }
    });
  }

  moveToUnderReview() {
    const c = this.complaint();
    if (!c) return;
    this.actionLoading.set(true);
    this.officerService.transitionComplaint(c.complaintId, {
      targetStatus: 'UNDER_REVIEW',
      remarks: this.remarks() || 'Investigation complete, drafting resolution'
    }).subscribe({
      next: () => {
        this.successMessage.set('Complaint moved to UNDER_REVIEW.');
        this.actionLoading.set(false);
        this.loadComplaint(c.complaintId);
      },
      error: () => {
        this.error.set('Failed to transition complaint.');
        this.actionLoading.set(false);
      }
    });
  }

  goBack() {
    this.router.navigate(['/officer']);
  }

  getStatusStep(status: string): number {
    const steps: Record<string, number> = {
      'NEW': 0, 'ASSIGNED': 1, 'IN_PROGRESS': 2,
      'UNDER_REVIEW': 3, 'ESCALATED': 3, 'RESOLVED': 4, 'CLOSED': 5
    };
    return steps[status] ?? 0;
  }
}
