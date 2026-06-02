import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CmsService } from '../../services/cms.service';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { SecureValidators } from '../../validators/form-validators';
import { saveAs } from 'file-saver';

interface EmailThread {
  threadId: string;
  complaintNumber: string;
  fromEmail: string;
  subject: string;
  sentAt: string;
  emailCount: number;
  status: string;
}

interface EmailMessage {
  id: number;
  messageId: string;
  threadId: string;
  fromEmail: string;
  toEmail: string;
  subject: string;
  body: string;
  direction: string;
  status: string;
  complaintNumber: string;
  attachmentUrl: string;
  sentAt: string;
}

@Component({
  selector: 'app-email-simulation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './email-simulation.component.html',
  styleUrl: './email-simulation.component.scss',
})
export class EmailSimulationComponent implements OnInit {
  threads: EmailThread[] = [];
  selectedThread: { threadId: string; complaintNumber: string; status: string; emails: EmailMessage[] } | null = null;
  stats = { totalThreads: 0, awaitingForm: 0, completed: 0 };

  view: 'list' | 'compose' | 'thread' = 'list';
  filterStatus: 'all' | 'AWAITING_FORM' | 'COMPLETED' = 'all';

  // Compose form
  compose = { fromEmail: '', fromName: '', subject: '', body: '' };
  sending = false;

  // Reply with attachment
  showReplyForm = false;
  uploadedFile: File | null = null;
  replying = false;
  parsedFormData: Record<string, string> | null = null;

  banks: any[] = [];
  categories: any[] = [];

  emailError = '';

  constructor(private cms: CmsService, private sanitizer: InputSanitizerService) {}

  ngOnInit() {
    this.loadThreads();
    this.loadStats();
    this.cms.getBanks().subscribe(b => this.banks = b);
    this.cms.getCategories().subscribe(c => this.categories = c);
  }

  loadThreads() {
    this.cms.getEmailThreads().subscribe(threads => this.threads = threads);
  }

  loadStats() {
    this.cms.getEmailStats().subscribe(s => this.stats = s);
  }

  get filteredThreads(): EmailThread[] {
    if (this.filterStatus === 'all') return this.threads;
    return this.threads.filter(t => t.status === this.filterStatus);
  }

  openCompose() {
    this.view = 'compose';
    this.compose = { fromEmail: '', fromName: '', subject: '', body: '' };
  }

  sendEmail() {
    if (!this.compose.fromEmail || !this.compose.subject) return;
    this.emailError = '';

    if (!SecureValidators.EMAIL_RE.test(this.compose.fromEmail)) {
      this.emailError = 'Invalid email format';
      return;
    }
    if (this.compose.fromEmail.length > 254) {
      this.emailError = 'Email too long';
      return;
    }
    if (this.sanitizer.hasXssPayload(this.compose.subject) || this.sanitizer.hasXssPayload(this.compose.body)) {
      this.emailError = 'Input contains unsafe content';
      return;
    }

    const sanitized = {
      fromEmail: this.sanitizer.sanitizeText(this.compose.fromEmail),
      fromName: this.sanitizer.sanitizeText(this.compose.fromName),
      subject: this.sanitizer.truncate(this.sanitizer.sanitizeText(this.compose.subject), 200),
      body: this.sanitizer.truncate(this.sanitizer.sanitizeText(this.compose.body), 5000),
    };

    this.sending = true;
    this.cms.simulateIncomingEmail(sanitized).subscribe({
      next: (result) => {
        this.sending = false;
        this.selectedThread = result;
        this.view = 'thread';
        this.loadThreads();
        this.loadStats();
      },
      error: () => this.sending = false,
    });
  }

  openThread(thread: EmailThread) {
    this.cms.getEmailThread(thread.threadId).subscribe(result => {
      this.selectedThread = result;
      this.view = 'thread';
      this.showReplyForm = false;
      this.uploadedFile = null;
      this.parsedFormData = null;
    });
  }

  downloadComplaintForm() {
    if (!this.selectedThread) return;
    const complaintNumber = this.selectedThread.complaintNumber;
    const firstEmail = this.selectedThread.emails[0];
    const senderName = firstEmail?.fromEmail?.split('@')[0]?.replace(/[._]/g, ' ') || '';
    const senderEmail = firstEmail?.fromEmail || '';
    const facts = (firstEmail?.body || '').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    const stateOptions = [
      'Andhra Pradesh','Arunachal Pradesh','Assam','Bihar','Chhattisgarh','Goa','Gujarat',
      'Haryana','Himachal Pradesh','Jharkhand','Karnataka','Kerala','Madhya Pradesh',
      'Maharashtra','Manipur','Meghalaya','Mizoram','Nagaland','Odisha','Punjab',
      'Rajasthan','Sikkim','Tamil Nadu','Telangana','Tripura','Uttar Pradesh',
      'Uttarakhand','West Bengal','Delhi','Jammu & Kashmir','Ladakh','Chandigarh',
      'Puducherry','Lakshadweep','Andaman & Nicobar Islands','Dadra & Nagar Haveli and Daman & Diu'
    ].map(s => `<option value="${s}">${s}</option>`).join('');

    const bankOptions = this.banks.map(b => `<option value="${b.name}">${b.name}</option>`).join('');

    const categoryOptions = [
      'Account opening / difficulty in operation of accounts',
      'ATM / Debit Cards',
      'Credit Cards',
      'Internet Banking / Mobile Banking',
      'Loans and Advances',
      'Pension related',
      'Remittances / Transfers',
      'Staff Behaviour',
      'Deposit accounts',
      'Insurance related',
      'Recovery Agents / Harassment',
      'Mis-selling / Para Banking',
      'Others'
    ].map(c => `<option value="${c}">${c}</option>`).join('');

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Complaint Form - ${complaintNumber}</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f6fa; padding: 30px; }
  .form-container { max-width: 750px; margin: 0 auto; background: #fff; border-radius: 12px; box-shadow: 0 2px 20px rgba(0,0,0,0.08); overflow: hidden; }
  .form-header { background: linear-gradient(135deg, #2B3990, #4a5ac7); color: #fff; padding: 28px 32px; text-align: center; }
  .form-header h1 { font-size: 18px; margin-bottom: 4px; }
  .form-header h2 { font-size: 14px; font-weight: 400; opacity: 0.9; }
  .form-meta { display: flex; justify-content: space-between; padding: 14px 32px; background: #f8f9ff; border-bottom: 1px solid #e5e7eb; font-size: 12px; color: #555; }
  .form-meta strong { color: #2B3990; }
  .instructions { padding: 16px 32px; background: #fffbeb; border-bottom: 1px solid #fde68a; font-size: 12px; color: #92400e; }
  .instructions b { display: block; margin-bottom: 4px; }
  .form-body { padding: 24px 32px; }
  .field { margin-bottom: 18px; }
  .field label { display: block; font-size: 12px; font-weight: 600; color: #333; margin-bottom: 5px; pointer-events: none; user-select: none; }
  .field label .req { color: #dc2626; }
  .field input, .field textarea, .field select { width: 100%; padding: 9px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 13px; font-family: inherit; color: #333; outline: none; background: #fff; }
  .field input:focus, .field textarea:focus, .field select:focus { border-color: #2B3990; box-shadow: 0 0 0 2px rgba(43,57,144,0.1); }
  .field textarea { resize: vertical; min-height: 70px; }
  .field input[readonly] { background: #f3f4f6; color: #666; }
  .field select { appearance: auto; cursor: pointer; }
  .row { display: flex; gap: 16px; }
  .row .field { flex: 1; }
  .form-footer { padding: 16px 32px; border-top: 1px solid #e5e7eb; text-align: center; }
  .save-btn { padding: 10px 28px; background: linear-gradient(135deg, #f97316, #ea580c); color: #fff; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; }
  .save-btn:hover { opacity: 0.9; }
  .note { font-size: 11px; color: #888; margin-top: 10px; font-style: italic; text-align: center; }
  @media print { .save-btn, .instructions, .note { display: none; } body { padding: 0; background: #fff; } .form-container { box-shadow: none; } }
</style>
</head>
<body>
<div class="form-container">
  <div class="form-header">
    <h1>RBI - Complaint Management System</h1>
    <h2>COMPLAINT REGISTRATION FORM</h2>
  </div>
  <div class="form-meta">
    <span>Reference: <strong>${complaintNumber}</strong></span>
    <span>Date: <strong>${new Date().toLocaleDateString('en-IN')}</strong></span>
  </div>
  <div class="instructions">
    <b>Instructions:</b>
    Fill all fields below (fields marked * are mandatory). After filling, click "Save Filled Form" and attach this file in your reply email. DO NOT change the email subject line.
  </div>
  <div class="form-body">
    <div class="row">
      <div class="field">
        <label>Full Name <span class="req">*</span></label>
        <input type="text" id="complainantName" value="${senderName}" />
      </div>
      <div class="field">
        <label>Mobile Number <span class="req">*</span></label>
        <input type="tel" id="complainantPhone" placeholder="+91 XXXXX XXXXX" />
      </div>
    </div>
    <div class="field">
      <label>Email</label>
      <input type="email" id="complainantEmail" value="${senderEmail}" readonly />
    </div>
    <div class="field">
      <label>Complete Address <span class="req">*</span></label>
      <textarea id="complainantAddress" rows="2" placeholder="Enter your full address"></textarea>
    </div>
    <div class="row">
      <div class="field">
        <label>State <span class="req">*</span></label>
        <select id="state">
          <option value="">-- Select State --</option>
          ${stateOptions}
        </select>
      </div>
      <div class="field">
        <label>District <span class="req">*</span></label>
        <input type="text" id="district" placeholder="Enter district" />
      </div>
      <div class="field">
        <label>Pincode <span class="req">*</span></label>
        <input type="text" id="pincode" placeholder="Enter pincode" maxlength="6" />
      </div>
    </div>
    <div class="row">
      <div class="field">
        <label>Bank / Financial Institution <span class="req">*</span></label>
        <select id="bankName">
          <option value="">-- Select Bank --</option>
          ${bankOptions}
          <option value="__other__">Other (type below)</option>
        </select>
      </div>
      <div class="field">
        <label>Branch (if applicable)</label>
        <input type="text" id="bankBranch" placeholder="Enter branch name" />
      </div>
    </div>
    <div class="field" id="bankOtherField" style="display:none;">
      <label>Other Bank / Institution Name</label>
      <input type="text" id="bankNameOther" placeholder="Type institution name" />
    </div>
    <div class="field">
      <label>Account Number (if applicable)</label>
      <input type="text" id="accountNumber" placeholder="Enter account number" />
    </div>
    <div class="field">
      <label>Complaint Category <span class="req">*</span></label>
      <select id="categoryName">
        <option value="">-- Select Category --</option>
        ${categoryOptions}
      </select>
    </div>
    <div class="field">
      <label>Facts of the Complaint <span class="req">*</span></label>
      <textarea id="description" rows="4">${facts}</textarea>
    </div>
    <div class="field">
      <label>Relief Sought</label>
      <textarea id="reliefSought" rows="2" placeholder="What resolution do you expect?"></textarea>
    </div>
  </div>
  <div class="form-footer">
    <button class="save-btn" onclick="saveForm()">Save Filled Form</button>
    <p class="note">After saving, attach this file in your reply email to complaints@cms.rbi.org.in</p>
  </div>
</div>
<script>
document.getElementById('bankName').addEventListener('change', function() {
  document.getElementById('bankOtherField').style.display = this.value === '__other__' ? 'block' : 'none';
});

function saveForm() {
  var fields = ['complainantName','complainantPhone','complainantEmail','complainantAddress','pincode','bankBranch','bankNameOther','accountNumber','description','reliefSought'];
  fields.forEach(function(id) {
    var el = document.getElementById(id);
    if (!el) return;
    if (el.tagName === 'TEXTAREA') { el.textContent = el.value; }
    else { el.setAttribute('value', el.value); }
  });
  // Handle selects
  var selects = ['state','bankName','categoryName','district'];
  selects.forEach(function(id) {
    var el = document.getElementById(id);
    if (!el) return;
    if (el.tagName === 'SELECT') {
      var opts = el.options;
      for (var i = 0; i < opts.length; i++) {
        if (opts[i].value === el.value) { opts[i].setAttribute('selected','selected'); }
        else { opts[i].removeAttribute('selected'); }
      }
    } else {
      el.setAttribute('value', el.value);
    }
  });
  // If bank is "other", store the typed name
  var bankSel = document.getElementById('bankName');
  if (bankSel.value === '__other__') {
    var otherVal = document.getElementById('bankNameOther').value;
    if (otherVal) { bankSel.setAttribute('data-other', otherVal); }
  }
  var html = '<!DOCTYPE html>' + document.documentElement.outerHTML;
  var blob = new Blob([html], {type: 'text/html'});
  var a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'Complaint_Form_${complaintNumber}_filled.html';
  a.click();
}
</script>
</body>
</html>`;

    const blob = new Blob([html], { type: 'text/html' });
    saveAs(blob, `Complaint_Form_${complaintNumber}.html`);
  }

  openReplySection() {
    this.showReplyForm = true;
    this.uploadedFile = null;
    this.parsedFormData = null;
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      this.uploadedFile = input.files[0];
      this.parseHtmlForm(this.uploadedFile);
    }
  }

  private async parseHtmlForm(file: File) {
    const text = await file.text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(text, 'text/html');

    const fieldIds = [
      'complainantName', 'complainantPhone', 'complainantEmail', 'complainantAddress',
      'state', 'district', 'pincode', 'bankName', 'bankBranch', 'bankNameOther',
      'accountNumber', 'categoryName', 'description', 'reliefSought',
    ];

    const parsed: Record<string, string> = {};
    for (const id of fieldIds) {
      const el = doc.getElementById(id);
      if (!el) continue;

      if (el.tagName === 'TEXTAREA') {
        parsed[id] = el.textContent?.trim() || '';
      } else if (el.tagName === 'SELECT') {
        const selected = el.querySelector('option[selected]') as HTMLOptionElement;
        const val = selected?.getAttribute('value') || '';
        if (val && val !== '__other__') {
          parsed[id] = val;
        } else if (val === '__other__') {
          const otherVal = el.getAttribute('data-other') || '';
          parsed[id] = otherVal || parsed['bankNameOther'] || '';
        }
      } else {
        parsed[id] = (el as HTMLInputElement).getAttribute('value')?.trim() || '';
      }
    }

    // If bankName was "other", use the typed name
    if (parsed['bankName'] === '' && parsed['bankNameOther']) {
      parsed['bankName'] = parsed['bankNameOther'];
    }
    delete parsed['bankNameOther'];

    this.parsedFormData = Object.keys(parsed).filter(k => parsed[k]).length > 0 ? parsed : null;
  }

  submitReply() {
    if (!this.selectedThread || !this.parsedFormData) return;
    this.replying = true;

    const payload = {
      threadId: this.selectedThread.threadId,
      fromEmail: this.selectedThread.emails[0].fromEmail,
      subject: this.selectedThread.emails[0].subject,
      complainantName: this.parsedFormData['complainantName'] || '',
      complainantPhone: this.parsedFormData['complainantPhone'] || '',
      complainantAddress: this.parsedFormData['complainantAddress'] || '',
      bankId: null as number | null,
      bankBranch: this.parsedFormData['bankBranch'] || '',
      accountNumber: this.parsedFormData['accountNumber'] || '',
      categoryId: null as number | null,
      description: this.parsedFormData['description'] || '',
      reliefSought: this.parsedFormData['reliefSought'] || '',
    };

    // Try to match bank by name
    const bankName = this.parsedFormData['bankName']?.toLowerCase();
    if (bankName) {
      const matchedBank = this.banks.find(b => b.name.toLowerCase().includes(bankName) || bankName.includes(b.name.toLowerCase()));
      if (matchedBank) payload.bankId = matchedBank.id;
    }

    // Try to match category by name
    const catName = this.parsedFormData['categoryName']?.toLowerCase();
    if (catName) {
      const matchedCat = this.categories.find(c => c.name.toLowerCase().includes(catName) || catName.includes(c.name.toLowerCase()));
      if (matchedCat) payload.categoryId = matchedCat.id;
    }

    this.cms.replyWithForm(payload).subscribe({
      next: (result) => {
        this.replying = false;
        this.selectedThread = result;
        this.showReplyForm = false;
        this.uploadedFile = null;
        this.parsedFormData = null;
        this.loadThreads();
        this.loadStats();
      },
      error: () => this.replying = false,
    });
  }

  backToList() {
    this.view = 'list';
    this.selectedThread = null;
    this.showReplyForm = false;
    this.uploadedFile = null;
    this.parsedFormData = null;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  }
}
