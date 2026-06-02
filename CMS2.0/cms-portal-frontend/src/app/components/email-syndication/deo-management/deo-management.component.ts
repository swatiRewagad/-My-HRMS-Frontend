import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { DeoUser } from '../../../models/email-syndication.model';

@Component({
  selector: 'app-deo-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './deo-management.component.html',
  styleUrl: './deo-management.component.scss'
})
export class DeoManagementComponent implements OnInit {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  deos = signal<DeoUser[]>([]);
  loading = signal(false);
  success = signal('');
  error = signal('');
  showAddForm = signal(false);

  newDeo = signal({
    userId: '',
    displayName: '',
    email: '',
    maxThreshold: 10
  });

  ngOnInit() {
    this.loadDeos();
  }

  loadDeos() {
    this.loading.set(true);
    this.emailService.getDeos().subscribe({
      next: (data) => {
        this.deos.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  updateThreshold(userId: string, threshold: number) {
    this.emailService.updateDeoThreshold(userId, threshold).subscribe({
      next: () => {
        this.success.set(`Threshold updated for ${userId}`);
        this.loadDeos();
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to update threshold')
    });
  }

  toggleActive(userId: string, currentActive: boolean) {
    this.emailService.updateDeoStatus(userId, !currentActive).subscribe({
      next: () => {
        this.loadDeos();
        this.success.set(`DEO ${userId} ${!currentActive ? 'activated' : 'deactivated'}`);
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to update status')
    });
  }

  toggleLeave(userId: string, currentLeave: boolean) {
    this.emailService.updateDeoStatus(userId, undefined, !currentLeave).subscribe({
      next: () => {
        this.loadDeos();
        this.success.set(`Leave status updated for ${userId}`);
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to update leave status')
    });
  }

  addDeo() {
    const deo = this.newDeo();
    if (!deo.userId || !deo.displayName) return;

    this.emailService.addDeo(deo).subscribe({
      next: () => {
        this.success.set(`DEO ${deo.displayName} added successfully`);
        this.showAddForm.set(false);
        this.newDeo.set({ userId: '', displayName: '', email: '', maxThreshold: 10 });
        this.loadDeos();
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to add DEO')
    });
  }

  removeDeo(userId: string, displayName: string) {
    this.emailService.removeDeo(userId).subscribe({
      next: () => {
        this.success.set(`DEO ${displayName} removed successfully`);
        this.loadDeos();
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to remove DEO')
    });
  }

  updateNewDeo(field: string, value: any) {
    this.newDeo.update(prev => ({ ...prev, [field]: value }));
  }

  resetPointer() {
    this.emailService.resetRoundRobinPointer().subscribe({
      next: () => {
        this.success.set('Round-robin pointer reset successfully');
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to reset pointer')
    });
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }

  getUtilization(deo: DeoUser): number {
    return deo.maxThreshold > 0 ? (deo.currentAssignedCount / deo.maxThreshold) * 100 : 0;
  }
}
