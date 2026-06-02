import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule, UpperCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ComplaintService } from '../../services/complaint.service';
import { ComplaintStatus } from '../../models/complaint.model';

@Component({
  selector: 'app-complaint-tracker',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './complaint-tracker.component.html',
  styleUrl: './complaint-tracker.component.scss'
})
export class ComplaintTrackerComponent implements OnInit {

  private complaintService = inject(ComplaintService);
  private route = inject(ActivatedRoute);

  searchId = signal('');
  status = signal<ComplaintStatus | null>(null);
  loading = signal(false);
  error = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.searchId.set(id);
      this.track();
    }
  }

  track() {
    const id = this.searchId().trim();
    if (!id) return;

    this.loading.set(true);
    this.error.set('');
    this.status.set(null);

    this.complaintService.trackComplaint(id).subscribe({
      next: (data) => {
        this.status.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        if (err.status === 404) {
          this.error.set('No complaint found with this reference number.');
        } else {
          this.error.set('Unable to fetch complaint status. Please try again.');
        }
        this.loading.set(false);
      }
    });
  }
}
