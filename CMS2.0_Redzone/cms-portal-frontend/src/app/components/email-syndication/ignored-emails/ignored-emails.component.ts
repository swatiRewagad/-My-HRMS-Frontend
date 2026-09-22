import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService, IgnoredEmailFilters } from '../../../services/email-syndication.service';
import { IgnoredEmailEntry, IgnoreListEntry } from '../../../models/email-syndication.model';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-ignored-emails',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './ignored-emails.component.html',
  styleUrl: './ignored-emails.component.scss'
})
export class IgnoredEmailsComponent implements OnInit {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  entries = signal<IgnoredEmailEntry[]>([]);
  total = signal(0);
  rules = signal<IgnoreListEntry[]>([]);
  loading = signal(false);
  exporting = signal(false);
  error = signal('');

  senderEmail = signal('');
  ruleId = signal('');
  fromDate = signal('');
  toDate = signal('');

  ngOnInit() {
    this.loadRules();
    this.search();
  }

  private loadRules() {
    this.emailService.getIgnoreList().subscribe({
      next: (data) => this.rules.set(data ?? []),
      error: () => this.rules.set([])
    });
  }

  private currentFilters(): IgnoredEmailFilters {
    return {
      senderEmail: this.senderEmail().trim() || undefined,
      ruleId: this.ruleId() || undefined,
      from: this.fromDate() || undefined,
      to: this.toDate() || undefined
    };
  }

  search() {
    this.loading.set(true);
    this.error.set('');
    this.emailService.getIgnoredEmails(this.currentFilters()).subscribe({
      next: (report) => {
        this.entries.set(report?.entries ?? []);
        this.total.set(report?.total ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.entries.set([]);
        this.total.set(0);
        this.error.set('intake.ignored_emails_load_failed');
        this.loading.set(false);
      }
    });
  }

  clearFilters() {
    this.senderEmail.set('');
    this.ruleId.set('');
    this.fromDate.set('');
    this.toDate.set('');
    this.search();
  }

  exportCsv() {
    this.exporting.set(true);
    this.error.set('');
    this.emailService.downloadIgnoredEmailsCsv(this.currentFilters()).subscribe({
      next: () => this.exporting.set(false),
      error: () => {
        this.exporting.set(false);
        this.error.set('intake.ignored_emails_export_failed');
      }
    });
  }

  hasFilters(): boolean {
    return !!(this.senderEmail().trim() || this.ruleId() || this.fromDate() || this.toDate());
  }

  ruleLabel(rule: IgnoreListEntry): string {
    return `#${rule.id} · ${rule.emailPattern} (${rule.matchField ?? 'FROM'})`;
  }

  goToMaster() {
    this.router.navigate(['/email-syndication/ignore-list']);
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }
}
