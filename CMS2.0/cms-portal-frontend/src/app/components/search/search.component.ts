import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SearchService, SearchResult, SearchFilters } from '../../services/search.service';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss'
})
export class SearchComponent {

  private searchService = inject(SearchService);
  private router = inject(Router);

  query = signal('');
  categoryFilter = signal('');
  statusFilter = signal('');
  priorityFilter = signal('');
  teamFilter = signal('');

  results = signal<SearchResult[]>([]);
  loading = signal(false);
  searched = signal(false);
  error = signal('');
  reindexing = signal(false);
  reindexMessage = signal('');

  categories = ['ATM', 'UPI', 'NEFT_RTGS', 'LOAN', 'CREDIT_CARD', 'DEPOSIT', 'INSURANCE', 'GENERAL'];
  statuses = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'UNDER_REVIEW', 'ESCALATED', 'RESOLVED', 'CLOSED'];
  priorities = ['HIGH', 'MEDIUM', 'LOW'];
  teams = ['ATM_TEAM', 'DIGITAL_TEAM', 'PAYMENT_SYSTEMS_TEAM', 'LENDING_TEAM', 'CARDS_TEAM', 'GENERAL_TEAM'];

  search() {
    const q = this.query().trim();
    if (!q && !this.categoryFilter() && !this.statusFilter() && !this.priorityFilter()) return;

    this.loading.set(true);
    this.error.set('');
    this.searched.set(true);

    const filters: SearchFilters = {
      q: q || '*',
      category: this.categoryFilter() || undefined,
      status: this.statusFilter() || undefined,
      priority: this.priorityFilter() || undefined,
      team: this.teamFilter() || undefined
    };

    this.searchService.searchComplaints(filters).subscribe({
      next: (data) => {
        this.results.set(data || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Search failed. Please try again.');
        this.loading.set(false);
      }
    });
  }

  clearFilters() {
    this.query.set('');
    this.categoryFilter.set('');
    this.statusFilter.set('');
    this.priorityFilter.set('');
    this.teamFilter.set('');
    this.results.set([]);
    this.searched.set(false);
  }

  reindex() {
    this.reindexing.set(true);
    this.reindexMessage.set('');
    this.searchService.reindex().subscribe({
      next: (data) => {
        this.reindexMessage.set(`Reindex complete: ${data.indexed} indexed, ${data.failed} failed`);
        this.reindexing.set(false);
      },
      error: () => {
        this.reindexMessage.set('Reindex failed.');
        this.reindexing.set(false);
      }
    });
  }

  viewComplaint(id: string) {
    this.router.navigate(['/officer/complaint', id]);
  }

  goBack() {
    this.router.navigate(['/']);
  }
}
