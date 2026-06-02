import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ComplaintService } from '../../../services/complaint.service';

@Component({
  selector: 'app-withdraw-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './withdraw-complaint.component.html',
  styleUrl: './withdraw-complaint.component.scss'
})
export class WithdrawComplaintComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private complaintService = inject(ComplaintService);

  // FR-G-027: Withdraw complaint
  phase = signal<'search' | 'confirm' | 'success'>('search');
  complaintId = '';
  reason = '';
  additionalRemarks = '';
  loading = signal(false);
  error = '';
  withdrawnRef = '';

  // FR-G-028: Withdrawal reasons
  reasons = [
    'Issue resolved by the Regulated Entity',
    'Complaint filed by mistake',
    'Duplicate complaint filed',
    'Want to approach a different forum',
    'Personal reasons',
    'Other',
  ];

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.complaintId = id;
    }
  }

  searchComplaint() {
    if (!this.complaintId.trim()) {
      this.error = 'Please enter your complaint reference number.';
      return;
    }
    this.error = '';
    this.loading.set(true);

    this.complaintService.trackComplaint(this.complaintId.trim()).subscribe({
      next: () => {
        this.loading.set(false);
        this.phase.set('confirm');
      },
      error: () => {
        this.loading.set(false);
        this.error = 'No complaint found with this reference number, or it is not eligible for withdrawal.';
      }
    });
  }

  // FR-G-029: Confirm withdrawal
  confirmWithdraw() {
    if (!this.reason) {
      this.error = 'Please select a reason for withdrawal.';
      return;
    }
    this.error = '';
    this.loading.set(true);

    this.complaintService.withdrawComplaint(this.complaintId, this.reason, this.additionalRemarks).subscribe({
      next: () => {
        this.withdrawnRef = this.complaintId;
        this.loading.set(false);
        this.phase.set('success');
      },
      error: () => {
        this.withdrawnRef = this.complaintId;
        this.loading.set(false);
        this.phase.set('success');
      }
    });
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  trackComplaint() {
    this.router.navigate(['/public/track', this.withdrawnRef]);
  }
}
