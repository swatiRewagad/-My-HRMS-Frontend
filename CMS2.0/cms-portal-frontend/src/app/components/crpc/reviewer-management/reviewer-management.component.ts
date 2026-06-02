import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CrpcService } from '../../../services/crpc.service';
import { ReviewerUser } from '../../../models/crpc.model';

@Component({
  selector: 'app-reviewer-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reviewer-management.component.html',
  styleUrl: './reviewer-management.component.scss'
})
export class ReviewerManagementComponent implements OnInit {

  private crpcService = inject(CrpcService);
  private router = inject(Router);

  reviewers = signal<ReviewerUser[]>([]);
  loading = signal(false);
  success = signal('');
  error = signal('');
  showAddForm = signal(false);

  newReviewer = signal({
    id: '',
    displayName: '',
    email: '',
    maxLoad: 25,
    region: 'NORTH'
  });

  regions = ['NORTH', 'SOUTH', 'EAST', 'WEST', 'CENTRAL'];

  activeCount = computed(() => this.reviewers().filter(r => r.isActive && !r.isOnLeave).length);
  totalLoad = computed(() => this.reviewers().reduce((sum, r) => sum + r.currentLoad, 0));
  totalCapacity = computed(() => this.reviewers().filter(r => r.isActive).reduce((sum, r) => sum + r.maxLoad, 0));

  ngOnInit() {
    this.loadReviewers();
  }

  loadReviewers() {
    this.loading.set(true);
    this.crpcService.getReviewers().subscribe({
      next: (data) => {
        this.reviewers.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  updateThreshold(id: string, maxLoad: number) {
    this.crpcService.updateReviewerThreshold(id, maxLoad).subscribe({
      next: () => {
        this.showSuccess(`Max load updated for ${id}`);
        this.loadReviewers();
      },
      error: () => this.error.set('Failed to update threshold')
    });
  }

  toggleActive(id: string) {
    this.crpcService.toggleReviewerActive(id).subscribe({
      next: () => {
        this.showSuccess(`Status toggled for ${id}`);
        this.loadReviewers();
      },
      error: () => this.error.set('Failed to update status')
    });
  }

  toggleLeave(id: string) {
    this.crpcService.toggleReviewerLeave(id).subscribe({
      next: () => {
        this.showSuccess(`Leave status updated for ${id}`);
        this.loadReviewers();
      },
      error: () => this.error.set('Failed to update leave status')
    });
  }

  addReviewer() {
    const rev = this.newReviewer();
    if (!rev.id || !rev.displayName) return;

    this.crpcService.addReviewer(rev).subscribe({
      next: () => {
        this.showSuccess(`Reviewer ${rev.displayName} added`);
        this.showAddForm.set(false);
        this.newReviewer.set({ id: '', displayName: '', email: '', maxLoad: 25, region: 'NORTH' });
        this.loadReviewers();
      },
      error: () => this.error.set('Failed to add reviewer')
    });
  }

  removeReviewer(id: string) {
    this.crpcService.removeReviewer(id).subscribe({
      next: () => {
        this.showSuccess(`Reviewer ${id} removed`);
        this.loadReviewers();
      },
      error: () => this.error.set('Failed to remove reviewer')
    });
  }

  resetRoundRobin() {
    this.crpcService.resetRoundRobin().subscribe({
      next: () => this.showSuccess('Round-robin pointer reset'),
      error: () => this.error.set('Failed to reset pointer')
    });
  }

  updateNewReviewer(field: string, value: any) {
    this.newReviewer.update(prev => ({ ...prev, [field]: value }));
  }

  getUtilization(rev: ReviewerUser): number {
    return rev.maxLoad > 0 ? (rev.currentLoad / rev.maxLoad) * 100 : 0;
  }

  private showSuccess(msg: string) {
    this.success.set(msg);
    setTimeout(() => this.success.set(''), 3000);
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }
}
