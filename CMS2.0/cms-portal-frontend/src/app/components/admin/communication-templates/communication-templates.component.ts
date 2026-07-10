import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CommunicationTemplateService, CommunicationTemplate } from '../../../services/communication-template.service';

@Component({
  selector: 'app-communication-templates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <header class="page-header">
        <h1>Communication Templates</h1>
        <button class="btn-primary" (click)="openForm()">+ Add Template</button>
      </header>

      <div class="filters">
        <select [(ngModel)]="filterMode" (ngModelChange)="loadTemplates()">
          <option value="">All Modes</option>
          <option value="EMAIL">Email</option>
          <option value="SMS">SMS</option>
          <option value="LETTER">Letter</option>
        </select>
        <select [(ngModel)]="filterCategory" (ngModelChange)="loadTemplates()">
          <option value="">All Categories</option>
          <option value="ACKNOWLEDGEMENT">Acknowledgement</option>
          <option value="CLOSURE">Closure</option>
          <option value="NOTIFICATION">Notification</option>
          <option value="REJECTION">Rejection</option>
        </select>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Template Name</th>
              <th>Mode</th>
              <th>Trigger</th>
              <th>Scheme</th>
              <th>Category</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (t of filteredTemplates(); track t.id) {
              <tr>
                <td>
                  <strong>{{ t.templateName }}</strong>
                  @if (t.description) { <br><small>{{ t.description }}</small> }
                </td>
                <td><span class="tag mode-{{t.mode | lowercase}}">{{ t.mode }}</span></td>
                <td>{{ t.triggerCondition }}</td>
                <td>{{ t.schemeVersion }}</td>
                <td><span class="tag">{{ t.category }}</span></td>
                <td><span class="status" [class.active]="t.active">{{ t.active ? 'Active' : 'Inactive' }}</span></td>
                <td class="actions">
                  <button class="btn-sm" (click)="editTemplate(t)">Edit</button>
                  <button class="btn-sm" (click)="previewTemplate(t)">Preview</button>
                  <button class="btn-sm" [class.btn-danger]="t.active" (click)="toggleActive(t)">
                    {{ t.active ? 'Deactivate' : 'Activate' }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="empty">No templates found</td></tr>
            }
          </tbody>
        </table>
      </div>

      @if (showForm()) {
        <div class="modal-overlay" (click)="closeForm()">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>{{ editing() ? 'Edit' : 'Add' }} Communication Template</h2>
            <form (ngSubmit)="saveTemplate()">
              <div class="form-group">
                <label>Template Name *</label>
                <input [(ngModel)]="form.templateName" name="templateName" required maxlength="200">
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label>Mode *</label>
                  <select [(ngModel)]="form.mode" name="mode" required>
                    <option value="EMAIL">Email</option>
                    <option value="SMS">SMS</option>
                    <option value="LETTER">Letter</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Trigger Condition</label>
                  <select [(ngModel)]="form.triggerCondition" name="triggerCondition">
                    <option value="">None</option>
                    <option value="CRPC_REJECTION">CRPC Rejection</option>
                    <option value="NEW_COMPLAINT_ACK">New Complaint Ack</option>
                    <option value="CLOSURE">Closure</option>
                    <option value="SENT_TO_OTHER_REG">Sent to Other Regulator</option>
                    <option value="INSUFFICIENT_DETAILS">Insufficient Details</option>
                  </select>
                </div>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label>Scheme Version</label>
                  <select [(ngModel)]="form.schemeVersion" name="schemeVersion">
                    <option value="BOTH">Both</option>
                    <option value="RBIOS_2021">RBIOS 2021</option>
                    <option value="RBIOS_2026">RBIOS 2026</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Category</label>
                  <select [(ngModel)]="form.category" name="category">
                    <option value="ACKNOWLEDGEMENT">Acknowledgement</option>
                    <option value="CLOSURE">Closure</option>
                    <option value="NOTIFICATION">Notification</option>
                    <option value="REJECTION">Rejection</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label>Description</label>
                <input [(ngModel)]="form.description" name="description" maxlength="500">
              </div>
              <div class="form-group">
                <label>Subject Template *</label>
                <input [(ngModel)]="form.subjectTemplate" name="subjectTemplate" required [attr.placeholder]="'Use &#123;&#123;variableName&#125;&#125; for placeholders'">
              </div>
              <div class="form-group">
                <label>Body Template *</label>
                <textarea [(ngModel)]="form.bodyTemplate" name="bodyTemplate" required rows="8"
                          [attr.placeholder]="'Use &#123;&#123;complainantName&#125;&#125;, &#123;&#123;complaintNumber&#125;&#125;, &#123;&#123;bankName&#125;&#125;, etc.'"></textarea>
              </div>
              <div class="variable-hint">
                Available variables: complainantName, complaintNumber, bankName, subject, currentDate, closureDate, status
              </div>
              <div class="form-actions">
                <button type="button" class="btn-secondary" (click)="closeForm()">Cancel</button>
                <button type="submit" class="btn-primary">{{ editing() ? 'Update' : 'Create' }}</button>
              </div>
            </form>
          </div>
        </div>
      }

      @if (showPreview()) {
        <div class="modal-overlay" (click)="showPreview.set(false)">
          <div class="modal preview-modal" (click)="$event.stopPropagation()">
            <h2>Template Preview</h2>
            <div class="preview-subject"><strong>Subject:</strong> {{ previewSubject() }}</div>
            <div class="preview-body" [innerHTML]="previewBody()"></div>
            <div class="form-actions">
              <button class="btn-secondary" (click)="showPreview.set(false)">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 1200px; margin: 0 auto; }
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
    .tag.mode-email { background: #dbeafe; color: #1d4ed8; }
    .tag.mode-sms { background: #dcfce7; color: #166534; }
    .tag.mode-letter { background: #fef3c7; color: #92400e; }
    .status { padding: 2px 8px; border-radius: 4px; font-size: 12px; background: #fee2e2; color: #991b1b; }
    .status.active { background: #dcfce7; color: #166534; }
    .actions { display: flex; gap: 8px; }
    .btn-primary { background: #3b82f6; color: #fff; border: none; padding: 8px 16px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .btn-secondary { background: #e2e8f0; color: #334155; border: none; padding: 8px 16px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .btn-sm { padding: 4px 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; font-size: 12px; cursor: pointer; }
    .btn-danger { border-color: #fca5a5; color: #dc2626; }
    .empty { text-align: center; color: #94a3b8; padding: 24px; }
    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 1000; display: flex; align-items: center; justify-content: center; }
    .modal { background: #fff; border-radius: 16px; padding: 24px; width: 640px; max-height: 90vh; overflow-y: auto; }
    .preview-modal { width: 700px; }
    .modal h2 { margin: 0 0 20px; font-size: 18px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 4px; }
    .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; }
    .form-group textarea { resize: vertical; font-family: monospace; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
    .variable-hint { font-size: 12px; color: #64748b; background: #f8fafc; padding: 8px 12px; border-radius: 6px; margin-bottom: 16px; }
    .preview-subject { padding: 12px; background: #f8fafc; border-radius: 8px; margin-bottom: 16px; }
    .preview-body { padding: 16px; border: 1px solid #e2e8f0; border-radius: 8px; white-space: pre-wrap; line-height: 1.6; }
  `]
})
export class CommunicationTemplatesComponent implements OnInit {
  private service = inject(CommunicationTemplateService);

  templates = signal<CommunicationTemplate[]>([]);
  filteredTemplates = signal<CommunicationTemplate[]>([]);
  showForm = signal(false);
  showPreview = signal(false);
  editing = signal(false);
  editId: number | null = null;

  previewSubject = signal('');
  previewBody = signal('');

  filterMode = '';
  filterCategory = '';

  form: any = this.getEmptyForm();

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.service.getAll().subscribe(list => {
      this.templates.set(list);
      this.applyFilters();
    });
  }

  applyFilters(): void {
    let result = this.templates();
    if (this.filterMode) result = result.filter(t => t.mode === this.filterMode);
    if (this.filterCategory) result = result.filter(t => t.category === this.filterCategory);
    this.filteredTemplates.set(result);
  }

  openForm(): void {
    this.form = this.getEmptyForm();
    this.editing.set(false);
    this.editId = null;
    this.showForm.set(true);
  }

  editTemplate(t: CommunicationTemplate): void {
    this.form = { ...t };
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

  toggleActive(t: CommunicationTemplate): void {
    const action = t.active ? this.service.deactivate(t.id) : this.service.activate(t.id);
    action.subscribe(() => this.loadTemplates());
  }

  previewTemplate(t: CommunicationTemplate): void {
    const sampleVars: Record<string, string> = {
      complainantName: 'John Doe',
      complaintNumber: 'CMS-2026-001234',
      bankName: 'State Bank of India',
      subject: 'Unauthorized transaction',
      currentDate: '09-07-2026',
      closureDate: '09-07-2026',
      status: 'Closed',
    };
    this.service.render(t.id, sampleVars).subscribe(res => {
      this.previewSubject.set(res.subject);
      this.previewBody.set(res.body);
      this.showPreview.set(true);
    });
  }

  private getEmptyForm() {
    return {
      templateName: '', mode: 'EMAIL', triggerCondition: '', schemeVersion: 'BOTH',
      subjectTemplate: '', bodyTemplate: '', description: '', category: 'NOTIFICATION'
    };
  }
}
