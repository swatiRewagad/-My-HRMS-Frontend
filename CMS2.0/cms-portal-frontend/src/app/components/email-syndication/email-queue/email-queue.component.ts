import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { EmailDraft, EmailDraftStatus, EmailQueueStats } from '../../../models/email-syndication.model';

@Component({
  selector: 'app-email-queue',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './email-queue.component.html',
  styleUrl: './email-queue.component.scss'
})
export class EmailQueueComponent implements OnInit {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  drafts = signal<EmailDraft[]>([]);
  stats = signal<EmailQueueStats | null>(null);
  loading = signal(false);
  selectedStatus = signal<string>('');
  statusOptions: EmailDraftStatus[] = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'CONVERTED', 'DUPLICATE', 'IGNORED'];

  ngOnInit() {
    this.loadQueue();
    this.loadStats();
  }

  loadQueue() {
    this.loading.set(true);
    this.emailService.getQueue(this.selectedStatus() || undefined).subscribe({
      next: (data) => {
        this.drafts.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  loadStats() {
    this.emailService.getStats().subscribe({
      next: (data) => this.stats.set(data)
    });
  }

  filterByStatus(status: string) {
    this.selectedStatus.set(status);
    this.loadQueue();
  }

  openDraft(draftId: string) {
    this.router.navigate(['/email-syndication/draft', draftId]);
  }

  navigateToIgnoreList() {
    this.router.navigate(['/email-syndication/ignore-list']);
  }

  navigateToDeoManagement() {
    this.router.navigate(['/email-syndication/deo-management']);
  }

  navigateToSimulator() {
    this.router.navigate(['/email-syndication/simulator']);
  }

  navigateToReviewerManagement() {
    this.router.navigate(['/crpc/reviewer-management']);
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'PENDING': return 'status-pending';
      case 'ASSIGNED': return 'status-assigned';
      case 'IN_PROGRESS': return 'status-progress';
      case 'CONVERTED': return 'status-converted';
      case 'DUPLICATE': return 'status-duplicate';
      case 'IGNORED': return 'status-ignored';
      default: return '';
    }
  }

  getTimeSince(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const hours = Math.floor(diff / 3600000);
    if (hours < 1) return 'Just now';
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }
}
