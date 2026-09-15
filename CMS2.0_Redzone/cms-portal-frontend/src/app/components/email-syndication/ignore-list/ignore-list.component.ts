import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { IgnoreListEntry, IgnoreListRequest } from '../../../models/email-syndication.model';

@Component({
  selector: 'app-ignore-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ignore-list.component.html',
  styleUrl: './ignore-list.component.scss'
})
export class IgnoreListComponent implements OnInit {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  entries = signal<IgnoreListEntry[]>([]);
  loading = signal(false);
  showAddForm = signal(false);
  error = signal('');
  success = signal('');

  newEntry = signal<IgnoreListRequest>({ emailPattern: '', reason: '' });

  ngOnInit() {
    this.loadEntries();
  }

  loadEntries() {
    this.loading.set(true);
    this.emailService.getIgnoreList().subscribe({
      next: (data) => {
        this.entries.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  addEntry() {
    const entry = this.newEntry();
    if (!entry.emailPattern) return;

    this.emailService.addToIgnoreList(entry).subscribe({
      next: () => {
        this.success.set('Entry added successfully');
        this.showAddForm.set(false);
        this.newEntry.set({ emailPattern: '', reason: '' });
        this.loadEntries();
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to add entry')
    });
  }

  removeEntry(id: number) {
    this.emailService.removeFromIgnoreList(id).subscribe({
      next: () => {
        this.success.set('Entry removed');
        this.loadEntries();
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => this.error.set('Failed to remove entry')
    });
  }

  updateNewEntry(field: string, value: string) {
    this.newEntry.update(prev => ({ ...prev, [field]: value }));
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }
}
