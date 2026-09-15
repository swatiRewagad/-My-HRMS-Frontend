import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-help-desk',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="helpdesk-container">
      <header class="hd-header">
        <h1>Toll-Free Help Desk</h1>
        <p class="subtitle">Register complaints received via phone call</p>
      </header>

      <div class="form-card">
        <h2>New Phone Complaint</h2>
        <form (ngSubmit)="submitComplaint()">
          <div class="form-row">
            <div class="form-group">
              <label>Caller Name *</label>
              <input [(ngModel)]="form.callerName" name="callerName" required>
            </div>
            <div class="form-group">
              <label>Phone Number *</label>
              <input [(ngModel)]="form.phoneNumber" name="phoneNumber" required pattern="[0-9]{10}">
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Email (optional)</label>
              <input [(ngModel)]="form.email" name="email" type="email">
            </div>
            <div class="form-group">
              <label>Bank/Entity Name *</label>
              <input [(ngModel)]="form.entityName" name="entityName" required>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Category *</label>
              <select [(ngModel)]="form.category" name="category" required>
                <option value="">Select Category</option>
                <option value="ATM">ATM/Debit Card</option>
                <option value="CREDIT_CARD">Credit Card</option>
                <option value="UPI">UPI</option>
                <option value="LOAN">Loan</option>
                <option value="DEPOSIT">Deposit</option>
                <option value="NEFT_RTGS">NEFT/RTGS</option>
                <option value="GENERAL">General Banking</option>
              </select>
            </div>
            <div class="form-group">
              <label>State</label>
              <input [(ngModel)]="form.state" name="state">
            </div>
          </div>
          <div class="form-group">
            <label>Subject *</label>
            <input [(ngModel)]="form.subject" name="subject" required maxlength="200">
          </div>
          <div class="form-group">
            <label>Description *</label>
            <textarea [(ngModel)]="form.description" name="description" required rows="5" maxlength="2000"></textarea>
          </div>
          <div class="form-group">
            <label>Call Reference Number</label>
            <input [(ngModel)]="form.callRefNumber" name="callRefNumber" placeholder="Auto-generated if empty">
          </div>
          <div class="form-actions">
            <button type="button" class="btn-secondary" (click)="resetForm()">Clear</button>
            <button type="submit" class="btn-primary" [disabled]="submitting()">
              {{ submitting() ? 'Registering...' : 'Register Complaint' }}
            </button>
          </div>
        </form>
      </div>

      @if (successMessage()) {
        <div class="success-banner">
          {{ successMessage() }}
        </div>
      }

      <section class="recent-section">
        <h2>Recent Phone Complaints</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Ref #</th>
                <th>Caller</th>
                <th>Phone</th>
                <th>Subject</th>
                <th>Category</th>
                <th>Registered At</th>
              </tr>
            </thead>
            <tbody>
              @for (c of recentComplaints(); track c.complaintNumber) {
                <tr>
                  <td>{{ c.complaintNumber }}</td>
                  <td>{{ c.complainantName }}</td>
                  <td>{{ c.complainantPhone }}</td>
                  <td>{{ c.subject }}</td>
                  <td>{{ c.category }}</td>
                  <td>{{ c.createdAt | date:'short' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="empty">No recent complaints</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .helpdesk-container { padding: 24px; max-width: 900px; margin: 0 auto; }
    .hd-header { margin-bottom: 24px; }
    .hd-header h1 { font-size: 24px; font-weight: 700; margin: 0; }
    .subtitle { color: #64748b; margin: 4px 0 0; }
    .form-card { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); margin-bottom: 24px; }
    .form-card h2 { font-size: 18px; margin: 0 0 20px; font-weight: 600; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 4px; }
    .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; }
    .form-group textarea { resize: vertical; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
    .btn-primary { background: #3b82f6; color: #fff; border: none; padding: 10px 20px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .btn-primary:disabled { opacity: 0.5; }
    .btn-secondary { background: #e2e8f0; color: #334155; border: none; padding: 10px 20px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .success-banner { background: #dcfce7; color: #166534; padding: 12px 16px; border-radius: 8px; margin-bottom: 24px; font-weight: 500; }
    .recent-section { margin-top: 32px; }
    .recent-section h2 { font-size: 18px; font-weight: 600; margin: 0 0 16px; }
    .table-wrap { overflow-x: auto; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f8fafc; padding: 10px 14px; text-align: left; font-size: 13px; font-weight: 600; color: #475569; }
    td { padding: 10px 14px; font-size: 13px; border-top: 1px solid #f1f5f9; }
    .empty { text-align: center; color: #94a3b8; padding: 24px; }
  `]
})
export class HelpDeskComponent implements OnInit {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/complaints`;

  recentComplaints = signal<any[]>([]);
  submitting = signal(false);
  successMessage = signal('');

  form = this.getEmptyForm();

  ngOnInit(): void {
    this.loadRecent();
  }

  submitComplaint(): void {
    this.submitting.set(true);
    this.successMessage.set('');
    const payload = {
      complainantName: this.form.callerName,
      complainantPhone: this.form.phoneNumber,
      complainantEmail: this.form.email,
      subject: this.form.subject,
      description: this.form.description,
      modeOfReceipt: 'PHONE',
      category: this.form.category,
      entityName: this.form.entityName,
      complainantState: this.form.state,
    };
    this.http.post<any>(this.baseUrl, payload).subscribe({
      next: (res) => {
        this.successMessage.set(`Complaint registered successfully: ${res.complaintNumber || 'Ref generated'}`);
        this.resetForm();
        this.loadRecent();
        this.submitting.set(false);
      },
      error: () => {
        this.submitting.set(false);
      }
    });
  }

  resetForm(): void {
    this.form = this.getEmptyForm();
  }

  private loadRecent(): void {
    this.http.get<any>(`${this.baseUrl}?modeOfReceipt=PHONE&size=10`).subscribe(res => {
      this.recentComplaints.set(res.content || res || []);
    });
  }

  private getEmptyForm() {
    return {
      callerName: '', phoneNumber: '', email: '', entityName: '',
      category: '', state: '', subject: '', description: '', callRefNumber: ''
    };
  }
}
