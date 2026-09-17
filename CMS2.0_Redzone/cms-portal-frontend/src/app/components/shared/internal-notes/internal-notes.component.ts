import { Component, inject, input, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ComplaintQueryService, InternalNote } from '../../../services/complaint-query.service';

/**
 * Entity-side internal notes (UST860).
 *
 * Only rendered in the RE portal, but that is not the control — the server refuses these endpoints
 * for RBI callers outright, so hiding the panel is presentation, not security.
 *
 * The edit affordance follows the server's `editable` flag rather than a locally computed clock,
 * so a client with a skewed clock cannot present an edit box for a note the server has locked.
 */
@Component({
  selector: 'app-internal-notes',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './internal-notes.component.html',
  styleUrl: './internal-notes.component.scss'
})
export class InternalNotesComponent implements OnInit {
  private queryService = inject(ComplaintQueryService);

  complaintNumber = input.required<string>();
  entityCode = input<string | null>(null);

  loading = signal(true);
  notes = signal<InternalNote[]>([]);
  editWindowMinutes = signal(5);
  error = signal('');

  newNote = signal('');
  posting = signal(false);

  editingId = signal<number | null>(null);
  editBody = signal('');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.queryService.listInternalNotes(this.complaintNumber(), this.entityCode()).subscribe({
      next: res => {
        this.notes.set(res.notes ?? []);
        this.editWindowMinutes.set(res.editWindowMinutes ?? 5);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('query.error.load_failed');
        this.loading.set(false);
      }
    });
  }

  addNote(): void {
    if (!this.newNote().trim()) {
      this.error.set('query.error.body_required');
      return;
    }
    this.posting.set(true);
    this.queryService.addInternalNote(this.complaintNumber(), this.newNote(), this.entityCode())
      .subscribe({
        next: () => {
          this.newNote.set('');
          this.posting.set(false);
          this.error.set('');
          this.load();
        },
        error: err => {
          this.posting.set(false);
          this.error.set(err.error?.message || 'query.error.send_failed');
        }
      });
  }

  startEdit(note: InternalNote): void {
    this.editingId.set(note.id);
    this.editBody.set(note.body);
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editBody.set('');
  }

  saveEdit(noteId: number): void {
    if (!this.editBody().trim()) {
      this.error.set('query.error.body_required');
      return;
    }
    this.posting.set(true);
    this.queryService.editInternalNote(noteId, this.editBody(), this.entityCode()).subscribe({
      next: () => {
        this.posting.set(false);
        this.cancelEdit();
        this.load();
      },
      error: err => {
        this.posting.set(false);
        // A 409 here means the window closed between render and save.
        this.error.set(err.error?.message || 'query.notes.locked');
        this.cancelEdit();
        this.load();
      }
    });
  }
}
