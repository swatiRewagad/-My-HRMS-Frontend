import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CommentTemplateService, CommentTemplate } from '../../../services/comment-template.service';

@Component({
  selector: 'app-comment-templates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <header class="page-header">
        <h1>Comment Templates</h1>
        <button class="btn-primary" (click)="openForm()">+ Add Template</button>
      </header>

      <div class="filters">
        <select [(ngModel)]="filterCategory" (ngModelChange)="loadTemplates()">
          <option value="">All Categories</option>
          <option value="GENERAL">General</option>
          <option value="CLOSURE">Closure</option>
          <option value="REJECTION">Rejection</option>
          <option value="FOLLOWUP">Follow-up</option>
        </select>
        <select [(ngModel)]="filterMode" (ngModelChange)="loadTemplates()">
          <option value="">All Modes</option>
          <option value="EMAIL">Email</option>
          <option value="PHYSICAL_LETTER">Physical Letter</option>
          <option value="PORTAL">Portal</option>
        </select>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Category</th>
              <th>Mode of Receipt</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (t of templates(); track t.id) {
              <tr>
                <td>
                  <strong>{{ t.title }}</strong>
                  @if (t.description) {
                    <br><small>{{ t.description }}</small>
                  }
                </td>
                <td><span class="tag">{{ t.category }}</span></td>
                <td>{{ t.modeOfReceipt || 'ALL' }}</td>
                <td><span class="status" [class.active]="t.active">{{ t.active ? 'Active' : 'Inactive' }}</span></td>
                <td class="actions">
                  <button class="btn-sm" (click)="editTemplate(t)">Edit</button>
                  <button class="btn-sm" [class.btn-danger]="t.active" (click)="toggleActive(t)">
                    {{ t.active ? 'Deactivate' : 'Activate' }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="empty">No templates found</td></tr>
            }
          </tbody>
        </table>
      </div>

      @if (showForm()) {
        <div class="modal-overlay" (click)="closeForm()">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>{{ editing() ? 'Edit' : 'Add' }} Comment Template</h2>
            <form (ngSubmit)="saveTemplate()">
              <div class="form-group">
                <label>Title *</label>
                <input [(ngModel)]="form.title" name="title" required maxlength="200">
              </div>
              <div class="form-group">
                <label>Description</label>
                <input [(ngModel)]="form.description" name="description" maxlength="500">
              </div>
              <div class="form-group">
                <label>Content *</label>
                <textarea [(ngModel)]="form.content" name="content" required rows="6"></textarea>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label>Category</label>
                  <select [(ngModel)]="form.category" name="category">
                    <option value="GENERAL">General</option>
                    <option value="CLOSURE">Closure</option>
                    <option value="REJECTION">Rejection</option>
                    <option value="FOLLOWUP">Follow-up</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Mode of Receipt</label>
                  <select [(ngModel)]="form.modeOfReceipt" name="modeOfReceipt">
                    <option value="ALL">All</option>
                    <option value="EMAIL">Email</option>
                    <option value="PHYSICAL_LETTER">Physical Letter</option>
                    <option value="PORTAL">Portal</option>
                  </select>
                </div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn-secondary" (click)="closeForm()">Cancel</button>
                <button type="submit" class="btn-primary">{{ editing() ? 'Update' : 'Create' }}</button>
              </div>
            </form>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 1100px; margin: 0 auto; }
    .page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
    .page-header h1 { font-size: 22px; font-weight: 700; margin: 0; }
    .filters { display: flex; gap: 12px; margin-bottom: 16px; }
    .filters select { padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 13px; }
    .table-wrap { overflow-x: auto; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f8fafc; padding: 12px 16px; text-align: left; font-size: 13px; font-weight: 600; color: #475569; }
    td { padding: 12px 16px; font-size: 14px; border-top: 1px solid #f1f5f9; }
    td small { color: #64748b; }
    .tag { background: #e2e8f0; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
    .status { padding: 2px 8px; border-radius: 4px; font-size: 12px; background: #fee2e2; color: #991b1b; }
    .status.active { background: #dcfce7; color: #166534; }
    .actions { display: flex; gap: 8px; }
    .btn-primary { background: #3b82f6; color: #fff; border: none; padding: 8px 16px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .btn-secondary { background: #e2e8f0; color: #334155; border: none; padding: 8px 16px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .btn-sm { padding: 4px 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; font-size: 12px; cursor: pointer; }
    .btn-danger { border-color: #fca5a5; color: #dc2626; }
    .empty { text-align: center; color: #94a3b8; padding: 24px; }
    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 1000; display: flex; align-items: center; justify-content: center; }
    .modal { background: #fff; border-radius: 16px; padding: 24px; width: 560px; max-height: 90vh; overflow-y: auto; }
    .modal h2 { margin: 0 0 20px; font-size: 18px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 4px; }
    .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; }
    .form-group textarea { resize: vertical; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
  `]
})
export class CommentTemplatesComponent implements OnInit {
  private service = inject(CommentTemplateService);

  templates = signal<CommentTemplate[]>([]);
  showForm = signal(false);
  editing = signal(false);
  editId: number | null = null;

  filterCategory = '';
  filterMode = '';

  form = { title: '', description: '', content: '', category: 'GENERAL', modeOfReceipt: 'ALL' };

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    const category = this.filterCategory || undefined;
    const mode = this.filterMode || undefined;
    this.service.getFiltered(category, mode).subscribe(list => this.templates.set(list));
  }

  openForm(): void {
    this.form = { title: '', description: '', content: '', category: 'GENERAL', modeOfReceipt: 'ALL' };
    this.editing.set(false);
    this.editId = null;
    this.showForm.set(true);
  }

  editTemplate(t: CommentTemplate): void {
    this.form = { title: t.title, description: t.description, content: t.content, category: t.category, modeOfReceipt: t.modeOfReceipt };
    this.editing.set(true);
    this.editId = t.id;
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
  }

  saveTemplate(): void {
    if (this.editing() && this.editId) {
      this.service.update(this.editId, this.form).subscribe(() => {
        this.closeForm();
        this.loadTemplates();
      });
    } else {
      this.service.create(this.form).subscribe(() => {
        this.closeForm();
        this.loadTemplates();
      });
    }
  }

  toggleActive(t: CommentTemplate): void {
    const action = t.active ? this.service.deactivate(t.id) : this.service.activate(t.id);
    action.subscribe(() => this.loadTemplates());
  }
}
