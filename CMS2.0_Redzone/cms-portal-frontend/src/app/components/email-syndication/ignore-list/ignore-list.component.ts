import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import {
  IgnoreListEntry,
  IgnoreListRequest,
  IgnoreMatchField,
  IgnorePatternType
} from '../../../models/email-syndication.model';
import { TranslatePipe } from '../../../pipes/translate.pipe';

type IgnoreFormModel = {
  emailPattern: string;
  patternType: IgnorePatternType;
  matchField: IgnoreMatchField;
  toPattern: string;
  ccPattern: string;
  bccPattern: string;
  subjectPattern: string;
  exceptionPattern: string;
  reason: string;
  isActive: boolean;
};

@Component({
  selector: 'app-ignore-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './ignore-list.component.html',
  styleUrl: './ignore-list.component.scss'
})
export class IgnoreListComponent implements OnInit {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  entries = signal<IgnoreListEntry[]>([]);
  loading = signal(false);
  saving = signal(false);
  showAddForm = signal(false);
  editingId = signal<number | null>(null);
  error = signal('');
  success = signal('');

  readonly matchFields: IgnoreMatchField[] = ['FROM', 'TO', 'CC', 'BCC', 'SUBJECT'];
  readonly patternTypes: IgnorePatternType[] = ['EXACT', 'DOMAIN', 'WILDCARD', 'CONTAINS'];

  form = signal<IgnoreFormModel>(this.emptyForm());

  ngOnInit() {
    this.loadEntries();
  }

  private emptyForm(): IgnoreFormModel {
    return {
      emailPattern: '',
      patternType: 'EXACT',
      matchField: 'FROM',
      toPattern: '',
      ccPattern: '',
      bccPattern: '',
      subjectPattern: '',
      exceptionPattern: '',
      reason: '',
      isActive: true
    };
  }

  loadEntries() {
    this.loading.set(true);
    this.emailService.getIgnoreList().subscribe({
      next: (data) => {
        this.entries.set(data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('intake.ignore_load_failed');
        this.loading.set(false);
      }
    });
  }

  startCreate() {
    this.editingId.set(null);
    this.form.set(this.emptyForm());
    this.showAddForm.set(true);
  }

  startEdit(entry: IgnoreListEntry) {
    this.editingId.set(entry.id);
    this.showAddForm.set(false);
    this.form.set({
      emailPattern: entry.emailPattern,
      patternType: entry.patternType ?? 'EXACT',
      matchField: entry.matchField ?? 'FROM',
      toPattern: entry.toPattern ?? '',
      ccPattern: entry.ccPattern ?? '',
      bccPattern: entry.bccPattern ?? '',
      subjectPattern: entry.subjectPattern ?? '',
      exceptionPattern: entry.exceptionPattern ?? '',
      reason: entry.reason ?? '',
      isActive: entry.isActive
    });
  }

  cancelForm() {
    this.showAddForm.set(false);
    this.editingId.set(null);
    this.form.set(this.emptyForm());
  }

  updateForm<K extends keyof IgnoreFormModel>(field: K, value: IgnoreFormModel[K]) {
    this.form.update(prev => ({ ...prev, [field]: value }));
  }

  private toRequest(model: IgnoreFormModel): IgnoreListRequest {
    const trim = (v: string) => (v && v.trim() ? v.trim() : undefined);
    return {
      emailPattern: model.emailPattern.trim(),
      patternType: model.patternType,
      matchField: model.matchField,
      toPattern: trim(model.toPattern),
      ccPattern: trim(model.ccPattern),
      bccPattern: trim(model.bccPattern),
      subjectPattern: trim(model.subjectPattern),
      exceptionPattern: trim(model.exceptionPattern),
      reason: trim(model.reason),
      isActive: model.isActive
    };
  }

  saveEntry() {
    const model = this.form();
    if (!model.emailPattern.trim()) return;

    this.error.set('');
    this.saving.set(true);
    const request = this.toRequest(model);
    const editing = this.editingId();

    const done = (messageKey: string) => {
      this.saving.set(false);
      this.flash(messageKey);
      this.cancelForm();
      this.loadEntries();
    };
    const failed = (messageKey: string) => {
      this.saving.set(false);
      this.error.set(messageKey);
    };

    if (editing !== null) {
      this.emailService.updateIgnoreEntry(editing, request).subscribe({
        next: () => done('intake.ignore_rule_updated'),
        error: () => failed('intake.ignore_rule_update_failed')
      });
    } else {
      this.emailService.addToIgnoreList(request).subscribe({
        next: () => done('intake.ignore_rule_created'),
        error: () => failed('intake.ignore_rule_create_failed')
      });
    }
  }

  toggleActive(entry: IgnoreListEntry) {
    this.error.set('');
    const request: IgnoreListRequest = {
      emailPattern: entry.emailPattern,
      patternType: entry.patternType,
      matchField: entry.matchField,
      toPattern: entry.toPattern ?? undefined,
      ccPattern: entry.ccPattern ?? undefined,
      bccPattern: entry.bccPattern ?? undefined,
      subjectPattern: entry.subjectPattern ?? undefined,
      exceptionPattern: entry.exceptionPattern ?? undefined,
      reason: entry.reason ?? undefined,
      isActive: !entry.isActive
    };
    this.emailService.updateIgnoreEntry(entry.id, request).subscribe({
      next: () => {
        this.flash(entry.isActive ? 'intake.ignore_rule_deactivated' : 'intake.ignore_rule_activated');
        this.loadEntries();
      },
      error: () => this.error.set('intake.ignore_rule_update_failed')
    });
  }

  removeEntry(entry: IgnoreListEntry) {
    this.error.set('');
    this.emailService.removeFromIgnoreList(entry.id).subscribe({
      next: () => {
        if (this.editingId() === entry.id) this.cancelForm();
        this.flash('intake.ignore_rule_deleted');
        this.loadEntries();
      },
      error: () => this.error.set('intake.ignore_rule_delete_failed')
    });
  }

  extraCriteriaCount(entry: IgnoreListEntry): number {
    return [entry.toPattern, entry.ccPattern, entry.bccPattern, entry.subjectPattern]
      .filter(v => !!v && `${v}`.trim().length > 0).length;
  }

  goToReport() {
    this.router.navigate(['/email-syndication/ignored-emails']);
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }

  private flash(key: string) {
    this.success.set(key);
    setTimeout(() => this.success.set(''), 3000);
  }
}
