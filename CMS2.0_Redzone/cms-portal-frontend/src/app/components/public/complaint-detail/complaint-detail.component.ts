import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface Comment {
  author: string;
  authorType: 'CMS' | 'USER';
  message: string;
  link?: string;
  date: string;
}

interface StatusHistoryItem {
  status: string;
  date: string;
  remarks?: string;
}

interface DetailRow {
  label: string;
  value: string;
}

@Component({
  selector: 'app-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SpeechButtonComponent, TranslatePipe],
  templateUrl: './complaint-detail.component.html',
  styleUrl: './complaint-detail.component.scss'
})
export class ComplaintDetailComponent implements OnInit {

  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(PublicAuthService);
  private complaintService = inject(ComplaintService);

  complaint = signal<any>(null);
  loading = signal(true);
  activeTab = signal<'details' | 'history'>('details');
  comments = signal<Comment[]>([]);
  statusHistory = signal<StatusHistoryItem[]>([]);

  mode: 'view' | 'withdraw' = 'view';

  // Withdrawal state
  showWithdrawModal = signal(false);
  reason = '';
  additionalRemarks = '';
  withdrawalDocs: File[] = [];
  isDragOver = false;
  fileUploadError = signal('');
  withdrawError = signal('');
  withdrawnRef = '';
  withdrawSuccess = signal(false);

  reasons: string[] = [
    'Issue resolved by the Regulated Entity',
    'Complaint filed by mistake',
    'Duplicate complaint filed',
    'Want to approach a different forum',
    'Personal reasons',
    'Other',
  ];

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    this.mode = this.route.snapshot.data['mode'] || 'view';
    if (id) {
      this.loadComplaint(id);
    }
  }

  private loadComplaint(id: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const data = res?.data ?? res;
        this.complaint.set(data);
        this.comments.set(data?.comments ?? []);
        const timeline = data?.timeline ?? data?.statusHistory ?? [];
        this.statusHistory.set(timeline.map((t: any) => ({
          status: t.toStatus || t.status || '',
          date: t.timestamp || t.date || '',
          remarks: t.remarks || t.action || ''
        })));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  switchTab(tab: 'details' | 'history') {
    this.activeTab.set(tab);
  }

  private wizard(): Record<string, any> {
    return this.complaint()?.wizardFormData ?? {};
  }

  private wizardGroup(name: string): Record<string, any> {
    return this.wizard()[name] ?? {};
  }

  private yesNo(value: any): string {
    if (value === 'yes' || value === true) return 'Yes';
    if (value === 'no' || value === false) return 'No';
    return '—';
  }

  private rupees(value: any): string {
    const raw = typeof value === 'string' ? value.replace(/,/g, '') : value;
    const num = Number(raw);
    return raw !== '' && raw != null && !isNaN(num) && num !== 0
      ? '₹ ' + num.toLocaleString('en-IN')
      : '';
  }

  private push(rows: DetailRow[], label: string, value: any) {
    const text = value === null || value === undefined ? '' : String(value).trim();
    if (text !== '' && text !== '—') {
      rows.push({ label, value: text });
    }
  }

  get entityLabel(): string {
    return this.complaint()?.entityName || 'the Regulated Entity';
  }

  // The eligibility questions live in the wizard config, not on the complaint, so the answer keys
  // are mapped back to their question text here rather than echoed as raw keys.
  get eligibilityRows(): DetailRow[] {
    const ea = this.complaint()?.eligibilityAnswers ?? {};
    const re = this.entityLabel;
    const rows: DetailRow[] = [];

    const questions: [string, string][] = [
      ['filedWithRE', `Have you filed a written / electronic complaint with the ${re}?`],
      ['receivedReply', 'Have you received any reply from the Entity?'],
      ['sentReminder', `Have you sent any reminder to the ${re}?`],
      ['isSubJudice', 'Is the subject matter of the complaint sub-judice?'],
      ['alreadySettled', 'Has the subject matter already been settled?'],
      ['throughAdvocateEligibility', 'Is the complaint being filed through an advocate?'],
      ['isComplainantSelf', 'Is the complainant filing the complaint himself/herself?'],
      ['pendingBeforeOmbudsman', 'Is the subject matter pending before an Ombudsman?'],
      ['settledByOmbudsman', 'Has the subject matter been settled by an Ombudsman?'],
      ['staffOfRE', 'Are you a staff member of the Regulated Entity?'],
      ['previouslyFiledWithCEPC', 'Have you previously filed this complaint with CEPC?'],
      ['employeeOfRE', 'Are you an employee of the Regulated Entity?'],
      ['employerRelationship', 'Does your complaint involve an employer-employee relationship?'],
    ];

    for (const [key, question] of questions) {
      if (ea[key] !== undefined && ea[key] !== '') {
        rows.push({ label: question, value: this.yesNo(ea[key]) });
      }
    }

    const c = this.complaint();
    this.push(rows, `Date of complaint filed with ${re}`, this.formatDate(c?.reComplaintDate));
    this.push(rows, 'Complaint Reference/Acknowledgement Number', c?.reComplaintReference);
    return rows;
  }

  get complainantRows(): DetailRow[] {
    const g = this.wizardGroup('complainantDetails');
    const c = this.complaint();
    const rows: DetailRow[] = [];
    this.push(rows, 'Complainant Category', g['complaintCategory']);
    this.push(rows, 'Name', c?.complainantName);
    this.push(rows, 'Age', g['age']);
    this.push(rows, 'Gender', g['gender']);
    this.push(rows, 'Mobile Number', c?.complainantPhone);
    this.push(rows, 'Email', c?.complainantEmail);
    this.push(rows, 'Pincode', c?.complainantPincode || g['pincode']);
    this.push(rows, 'State', c?.complainantState);
    this.push(rows, 'District', c?.complainantDistrict || g['city']);
    this.push(rows, 'Address', c?.complainantAddress || g['addressDetails']);
    return rows;
  }

  get entityRows(): DetailRow[] {
    const g = this.wizardGroup('regulatedEntity');
    const c = this.complaint();
    const rows: DetailRow[] = [];
    this.push(rows, 'Regulated Entity Name', c?.entityName);
    this.push(rows, 'Entity Type', c?.entityType);
    if (g['isCreditCardComplaint']) {
      rows.push({ label: 'Is your complaint related to credit card?', value: this.yesNo(g['isCreditCardComplaint']) });
    }
    this.push(rows, 'Entity State', c?.entityState);
    this.push(rows, 'Entity District', c?.entityDistrict);
    this.push(rows, 'Entity Branch', c?.entityBranchName || c?.bankBranch);
    return rows;
  }

  get complaintRows(): DetailRow[] {
    const g = this.wizardGroup('complaintDetails');
    const c = this.complaint();
    const re = this.entityLabel;
    const rows: DetailRow[] = [];
    this.push(rows, 'Complaint Category', c?.category);
    this.push(rows, 'Subject', c?.subject);
    if (g['hasAccountWithRE']) {
      rows.push({ label: `Do you have an account with ${re}?`, value: this.yesNo(g['hasAccountWithRE']) });
    }
    if (g['isWalletComplaint']) {
      rows.push({ label: 'Is your complaint against a Wallet transaction?', value: this.yesNo(g['isWalletComplaint']) });
    }
    this.push(rows, 'Wallet Name', g['walletName']);
    if (g['isBusinessCorrespondent']) {
      rows.push({ label: 'Is your complaint against a Business Correspondent?', value: this.yesNo(g['isBusinessCorrespondent']) });
    }
    this.push(rows, 'Transaction Reference Number', g['transactionRefNumber']);
    this.push(rows, 'Amount Involved in the Dispute', this.rupees(c?.amountInvolved ?? g['disputeAmount']));
    this.push(rows, 'Compensation Sought For Dispute', this.rupees(g['compensationSought']));
    this.push(rows, 'Compensation For Harassment', this.rupees(g['reliefSought']));
    this.push(rows, 'Savings Account Number', g['savingsAccountNumber']);
    this.push(rows, 'Loan Account Number', g['loanAccountNumber']);
    this.push(rows, 'ATM / Debit Card Number', g['atmDebitCardNumber']);
    this.push(rows, 'Credit Card Number', g['creditCardNumber']);
    return rows;
  }

  // Wizard uploads go to the storage service and are never linked into COMPLAINT_ATTACHMENT, so the
  // server's `attachments` list is empty for portal filings; fall back to the wizard's file metadata.
  get uploadedDocuments(): { fileName: string; fileSize?: number; viewUrl?: string }[] {
    const server = this.complaint()?.attachments ?? [];
    if (server.length) {
      return server.map((a: any) => ({ fileName: a.fileName || a.name, fileSize: a.size }));
    }
    const groups = ['complaintDetails', 'repAuthorization'];
    const keys = ['fileUpload', 'repFileUpload'];
    const out: { fileName: string; fileSize?: number; viewUrl?: string }[] = [];
    for (const g of groups) {
      for (const k of keys) {
        const list = this.wizardGroup(g)[k];
        if (Array.isArray(list)) {
          for (const f of list) {
            if (f?.fileName) out.push({ fileName: f.fileName, fileSize: f.fileSize, viewUrl: f.viewUrl });
          }
        }
      }
    }
    return out;
  }

  get representativeRows(): DetailRow[] {
    const c = this.complaint();
    const g = this.wizardGroup('repAuthorization');
    const rows: DetailRow[] = [];
    this.push(rows, 'Representative Name', c?.representativeName);
    this.push(rows, 'Phone', c?.representativePhone);
    this.push(rows, 'Email', c?.representativeEmail);
    this.push(rows, 'Pincode', g['repPincode']);
    this.push(rows, 'State', g['repState']);
    this.push(rows, 'District', g['repDistrict']);
    this.push(rows, 'Address', g['repAddress']);
    return rows;
  }

  /**
   * Statuses that bar withdrawal (UST105/UST107), matching the server's list in
   * ComplaintService.withdrawComplaint.
   *
   * PORTAL_REJECTION, ADJUDICATED and CONCILIATED were absent, so the button was offered on a
   * complaint already settled by award or conciliation and the attempt only failed at the server.
   * SENT_TO_OTHER/FORWARDED_EXTERNAL are the user story's "Sent to other Department" and "Sent to
   * other Regulatory Bodies", which it excludes by name.
   */
  private readonly TERMINAL_STATUSES = [
    'CLOSED', 'RESOLVED', 'REJECTED', 'WITHDRAWN', 'PORTAL_REJECTION',
    'ADJUDICATED', 'CONCILIATED', 'SENT_TO_OTHER', 'FORWARDED_EXTERNAL',
  ];

  isWithdrawable(): boolean {
    const status = this.complaint()?.status?.toUpperCase() || '';
    return !this.TERMINAL_STATUSES.includes(status);
  }

  /**
   * UST105 AC3's message, shown when the page was opened in withdraw mode for a complaint that cannot
   * be withdrawn. Previously the button was simply hidden, so the citizen arrived on a withdraw page
   * with no action and no explanation — a dead end.
   */
  withdrawBlockedMessage(): string {
    return this.isWithdrawable() ? '' : 'Complaints cannot be withdrawn.';
  }

  getStatusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'CLOSED': case 'NON_MAINTAINABLE': case 'REJECTED': return 'status-closed';
      case 'WITHDRAWN': return 'status-closed';
      case 'IN_PROGRESS': case 'INPROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': return 'status-info-required';
      case 'DRAFT': return 'status-draft';
      case 'SENT_BACK': case 'REQUEST_SENT_BACK': return 'status-sent-back';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status?.toUpperCase()) {
      case 'IN_PROGRESS': case 'INPROGRESS': return 'In Progress';
      case 'CLOSED': return 'Closed';
      case 'WITHDRAWN': return 'Withdrawn';
      case 'RESOLVED': return 'Resolved';
      case 'REJECTED': return 'Rejected';
      case 'NON_MAINTAINABLE': return 'Non Maintainable';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'DRAFT': return 'Draft';
      case 'SENT_BACK': case 'REQUEST_SENT_BACK': return 'Request Sent Back';
      case 'PENDING': return 'Pending';
      default: return status?.replace(/_/g, ' ') || '—';
    }
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-');
    } catch { return dateStr; }
  }

  goBack() {
    this.router.navigate(['/public/history']);
  }

  // Withdraw actions
  openWithdrawModal() {
    if (this.mode === 'withdraw') {
      this.showWithdrawModal.set(true);
    } else {
      const c = this.complaint();
      if (c?.complaintId) {
        this.router.navigate(['/public/withdraw', c.complaintId]);
      }
    }
  }

  closeWithdrawModal() {
    this.showWithdrawModal.set(false);
    this.reason = '';
    this.additionalRemarks = '';
    this.withdrawalDocs = [];
    this.fileUploadError.set('');
    this.withdrawError.set('');
  }

  confirmWithdraw() {
    // UST107 AC5 wording, used for both the unselected reason and the empty "Other" free text — in
    // both cases no reason has actually been given.
    if (!this.reason) {
      this.withdrawError.set('Reason for withdrawal is required.');
      return;
    }
    if (this.reason === 'Other' && !this.additionalRemarks.trim()) {
      this.withdrawError.set('Reason for withdrawal is required.');
      return;
    }
    this.withdrawError.set('');
    this.loading.set(true);

    const c = this.complaint();
    const remarks = this.reason === 'Other' ? this.additionalRemarks.trim() : this.reason;

    const phone = c.complainantPhone || this.authService.userIdentifier();

    this.complaintService.withdrawComplaint(c.complaintId, this.reason, remarks, phone, this.withdrawalDocs).subscribe({
      next: () => {
        this.withdrawnRef = c.complaintId;
        this.loading.set(false);
        this.showWithdrawModal.set(false);
        this.withdrawSuccess.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.error?.message || 'Failed to withdraw complaint. Please try again.';
        this.withdrawError.set(msg);
      }
    });
  }

  private readonly WITHDRAW_ALLOWED_TYPES = [
    'application/pdf', 'image/jpeg',
    'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  ];
  private readonly WITHDRAW_ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.doc', '.docx'];
  private readonly WITHDRAW_MAX_TOTAL_BYTES = 5 * 1024 * 1024;

  private validateWithdrawalFile(file: File): string | null {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!this.WITHDRAW_ALLOWED_EXTENSIONS.includes(ext)) {
      return `File type "${ext}" is not allowed. Supported: PDF, JPG, DOC, DOCX.`;
    }
    if (file.size > 2 * 1024 * 1024) {
      return 'File size exceeds the 2MB per file limit.';
    }
    const currentTotal = this.withdrawalDocs.reduce((sum, f) => sum + f.size, 0);
    if (currentTotal + file.size > this.WITHDRAW_MAX_TOTAL_BYTES) {
      return 'File size exceeds the 5 MB limit. Total upload size cannot exceed 5MB.';
    }
    return null;
  }

  onWithdrawalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.fileUploadError.set('');
    for (let i = 0; i < input.files.length; i++) {
      const error = this.validateWithdrawalFile(input.files[i]);
      if (error) {
        this.fileUploadError.set(error);
        continue;
      }
      this.withdrawalDocs.push(input.files[i]);
    }
    input.value = '';
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragOver = false;
    if (!event.dataTransfer?.files?.length) return;
    this.fileUploadError.set('');
    for (let i = 0; i < event.dataTransfer.files.length; i++) {
      const error = this.validateWithdrawalFile(event.dataTransfer.files[i]);
      if (error) {
        this.fileUploadError.set(error);
        continue;
      }
      this.withdrawalDocs.push(event.dataTransfer.files[i]);
    }
  }

  removeWithdrawalDoc(index: number) {
    this.withdrawalDocs.splice(index, 1);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  downloadPdf() {
    const c = this.complaint();
    if (!c?.complaintId) return;
    this.http.get(`${environment.apiBaseUrl}/api/v1/complaints/${c.complaintId}/pdf`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `complaint-${c.complaintId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {}
    });
  }
}
