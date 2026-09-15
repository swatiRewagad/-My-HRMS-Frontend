import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ComplaintService } from '../../services/complaint.service';
import { PublicAuthService } from '../../services/public-auth.service';
import { CitizenAuthApiService, CaptchaResponse } from '../../services/citizen-auth-api.service';
import { ComplaintStatus } from '../../models/complaint.model';
import { environment } from '../../../environments/environment';
import { TableModule } from 'primeng/table';
import { ComplaintTimelineComponent } from '../../shared/complaint-timeline/complaint-timeline.component';

@Component({
  selector: 'app-complaint-tracker',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TableModule, ComplaintTimelineComponent],
  templateUrl: './complaint-tracker.component.html',
  styleUrl: './complaint-tracker.component.scss'
})
export class ComplaintTrackerComponent implements OnInit {

  private complaintService = inject(ComplaintService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private authService = inject(PublicAuthService);
  private authApi = inject(CitizenAuthApiService);
  private destroyRef = inject(DestroyRef);

  searchId = signal('');
  status = signal<ComplaintStatus | null>(null);
  loading = signal(false);
  error = signal('');
  pdfLoading = signal(false);
  statusChangeNotice = signal('');

  // UST98: Track via Mobile+OTP
  trackMode = signal<'id' | 'mobile'>('id');
  mobileNumber = '';
  otpSent = signal(false);
  otpCode = '';
  mobileComplaints = signal<any[]>([]);
  mobileVerified = signal(false);
  sessionId = '';

  captchaInput = '';
  captchaData = signal<CaptchaResponse | null>(null);
  captchaLoading = signal(false);
  captchaType = signal<'VISUAL' | 'MATH'>('VISUAL');

  // Pagination / sort / filter state for complaints table
  totalRecords = signal(0);
  currentPage = signal(0);
  pageSize = signal(20);
  sortField = signal('createdAt');
  sortOrder = signal(-1); // -1 = desc, 1 = asc
  statusFilter = signal('');

  statusOptions = [
    { label: 'All Statuses', value: '' },
    { label: 'Pending', value: 'PENDING' },
    { label: 'New', value: 'NEW' },
    { label: 'Assigned', value: 'ASSIGNED' },
    { label: 'In Progress', value: 'IN_PROGRESS' },
    { label: 'Under Review', value: 'UNDER_REVIEW' },
    { label: 'Escalated', value: 'ESCALATED' },
    { label: 'Resolved', value: 'RESOLVED' },
    { label: 'Closed', value: 'CLOSED' },
    { label: 'Withdrawn', value: 'WITHDRAWN' }
  ];

  // Polling subscription
  private pollingSub: Subscription | null = null;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.searchId.set(id);
      this.track();
    } else if (this.authService.isSessionValid()) {
      this.mobileNumber = this.authService.userIdentifier();
      if (this.mobileNumber) {
        this.trackMode.set('mobile');
        this.mobileVerified.set(true);
        this.loadComplaintsByMobile();
      }
    }
  }

  switchMode(mode: 'id' | 'mobile') {
    this.trackMode.set(mode);
    this.error.set('');
    this.status.set(null);
    this.stopPolling();
    if (mode === 'mobile' && !this.mobileVerified() && !this.captchaData()) {
      this.loadCaptcha();
    }
  }

  loadCaptcha(onLoaded?: () => void) {
    this.captchaLoading.set(true);
    this.captchaInput = '';
    this.authApi.getCaptcha(this.captchaType()).subscribe({
      next: (data) => {
        this.captchaData.set(data);
        this.captchaLoading.set(false);
        onLoaded?.();
      },
      error: () => {
        this.error.set('Failed to load CAPTCHA. Please try again.');
        this.captchaLoading.set(false);
      }
    });
  }

  switchCaptchaType() {
    this.captchaType.set(this.captchaType() === 'VISUAL' ? 'MATH' : 'VISUAL');
    this.loadCaptcha();
  }

  playCaptchaAudio() {
    // A VISUAL challenge has no speakable form — its answer is deliberately not sent to the
    // client. Swap to the MATH challenge, which is both accessible and safe to read aloud.
    if (this.captchaType() !== 'MATH') {
      this.captchaType.set('MATH');
      this.loadCaptcha(() => this.speakCaptcha());
      return;
    }
    this.speakCaptcha();
  }

  private speakCaptcha() {
    const question = this.captchaData()?.audioQuestion;
    if (!question) return;
    const utterance = new SpeechSynthesisUtterance(question);
    utterance.rate = 0.7;
    utterance.lang = 'en-IN';
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }

  track() {
    const id = this.searchId().trim();
    if (!id) return;

    this.loading.set(true);
    this.error.set('');
    this.status.set(null);
    this.stopPolling();

    this.complaintService.trackComplaint(id).subscribe({
      next: (data) => {
        this.status.set(data);
        this.loading.set(false);
        this.startPolling(id);
      },
      error: (err) => {
        if (err.status === 404) {
          this.error.set('Complaint number not found, please check and try again.');
        } else {
          this.error.set('Unable to fetch complaint status. Please try again.');
        }
        this.loading.set(false);
      }
    });
  }

  // UST98: Send OTP for mobile-based tracking
  sendTrackingOtp() {
    if (!/^[6-9]\d{9}$/.test(this.mobileNumber)) {
      this.error.set('Enter a valid 10-digit mobile number.');
      return;
    }
    const captcha = this.captchaData();
    if (!captcha) {
      this.error.set('CAPTCHA not loaded. Please refresh.');
      this.loadCaptcha();
      return;
    }
    if (!this.captchaInput.trim()) {
      this.error.set('Please enter the CAPTCHA.');
      return;
    }

    this.loading.set(true);
    this.error.set('');
    // Tracking only reads back a complaint the citizen already filed, so no new DPDP consent is
    // collected here — the consent recorded at filing time still governs.
    this.authApi.sendOtp(this.mobileNumber, captcha.token, this.captchaInput.trim(), false, 'en').subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpCode = '';
        this.otpSent.set(true);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'INVALID_CAPTCHA') {
          this.error.set('Invalid CAPTCHA. Please try again.');
          this.loadCaptcha();
        } else if (body?.error === 'COOLOFF_ACTIVE' || body?.error === 'RATE_LIMITED') {
          this.error.set(body.message || 'Too many attempts. Please try again later.');
        } else {
          this.error.set(body?.message || 'Failed to send OTP. Please try again.');
        }
      }
    });
  }

  verifyTrackingOtp() {
    if (this.otpCode.length < 6) {
      this.error.set('Enter the 6-digit OTP.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.authApi.verifyOtp(this.mobileNumber, this.otpCode, this.sessionId).subscribe({
      next: (res) => {
        this.authService.login(this.mobileNumber, res.token);
        this.mobileVerified.set(true);
        this.loading.set(false);
        this.loadComplaintsByMobile();
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'OTP_EXPIRED') {
          this.error.set('OTP has expired. Please request a new one.');
          this.otpSent.set(false);
          this.loadCaptcha();
        } else if (body?.error === 'MAX_ATTEMPTS_EXCEEDED') {
          this.error.set(body.message || 'Too many incorrect attempts. Please request a new OTP.');
          this.otpSent.set(false);
          this.loadCaptcha();
        } else {
          this.error.set('Invalid OTP, please try again.');
        }
      }
    });
  }

  loadComplaintsByMobile() {
    this.loading.set(true);
    const sortDir = this.sortOrder() === 1 ? 'asc' : 'desc';
    const statusParam = this.statusFilter() ? `&status=${this.statusFilter()}` : '';
    const url = `${environment.apiBaseUrl}/api/v1/complaints?phone=${this.mobileNumber}` +
      `&page=${this.currentPage()}&size=${this.pageSize()}` +
      `&sortBy=${this.sortField()}&sortDir=${sortDir}${statusParam}`;

    this.http.get<any>(url).subscribe({
      next: (res) => {
        const data = res?.data || res || {};
        // Handle both old (array) and new (paginated) response formats
        if (data.content) {
          this.mobileComplaints.set(Array.isArray(data.content) ? data.content : []);
          this.totalRecords.set(data.totalElements || 0);
        } else if (Array.isArray(data)) {
          this.mobileComplaints.set(data);
          this.totalRecords.set(data.length);
        } else {
          this.mobileComplaints.set([]);
          this.totalRecords.set(0);
        }
        this.loading.set(false);
        if (this.mobileComplaints().length === 0) {
          this.error.set('No complaints found for this mobile number.');
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.mobileComplaints.set([]);
        this.totalRecords.set(0);
        if (err.status === 401 || err.status === 403) {
          this.mobileVerified.set(false);
          this.otpSent.set(false);
          this.authService.logout();
          this.loadCaptcha();
          this.error.set('Your session has expired. Please verify your mobile number again.');
        } else {
          this.error.set('Unable to fetch complaints. Please try again.');
        }
      }
    });
  }

  onTablePage(event: any) {
    this.currentPage.set(Math.floor((event.first || 0) / (event.rows || 20)));
    this.pageSize.set(event.rows || 20);
    this.loadComplaintsByMobile();
  }

  onTableSort(event: any) {
    const fieldMap: Record<string, string> = {
      'createdAt': 'createdAt',
      'createdAtFormatted': 'createdAt',
      'status': 'status',
      'complaintNumber': 'complaintNumber',
      'closedAt': 'closedAt',
      'closedAtFormatted': 'closedAt'
    };
    this.sortField.set(fieldMap[event.field] || 'createdAt');
    this.sortOrder.set(event.order || -1);
    this.loadComplaintsByMobile();
  }

  onStatusFilterChange(value: string) {
    this.statusFilter.set(value);
    this.currentPage.set(0);
    this.loadComplaintsByMobile();
  }

  selectMobileComplaint(complaint: any) {
    const id = complaint.complaintNumber || complaint.complaintId || complaint.id;
    this.searchId.set(id);
    this.trackMode.set('id');
    this.track();
  }

  /** UST111: Check if complaint status allows filing an appeal (case-insensitive). */
  isAppealEligibleStatus(status: string): boolean {
    const appealStatuses = ['CLOSED', 'RESOLVED', 'REJECTED'];
    return appealStatuses.includes((status || '').toUpperCase());
  }

  /** UST111: Navigate to file-appeal page with the complaint number pre-filled. */
  fileAppeal() {
    const s = this.status();
    if (!s) return;
    this.router.navigate(['/public/appeal'], {
      queryParams: { complaint: s.complaintId }
    });
  }

  /** UST105: Only show Withdraw button for statuses that allow withdrawal. */
  canWithdraw(status: string): boolean {
    const nonWithdrawable = ['CLOSED', 'RESOLVED', 'REJECTED', 'WITHDRAWN', 'SENT_BACK', 'APPROVED'];
    return !nonWithdrawable.includes((status || '').toUpperCase());
  }

  // Polling for real-time updates (Task 8)
  private startPolling(complaintId: string) {
    this.stopPolling();
    this.pollingSub = interval(30000).pipe(
      takeUntilDestroyed(this.destroyRef),
      switchMap(() => this.complaintService.trackComplaint(complaintId))
    ).subscribe({
      next: (data) => {
        const current = this.status();
        if (current && data.status !== current.status) {
          this.statusChangeNotice.set(`Status updated: ${current.status} -> ${data.status}`);
          setTimeout(() => this.statusChangeNotice.set(''), 8000);
        }
        this.status.set(data);
      },
      error: () => {
        // Silently ignore polling errors
      }
    });
  }

  private stopPolling() {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
      this.pollingSub = null;
    }
  }

  refreshStatus() {
    const s = this.status();
    if (!s) return;
    const id = s.complaintId || this.searchId();
    if (!id) return;
    this.loading.set(true);
    this.complaintService.trackComplaint(id).subscribe({
      next: (data) => {
        const current = this.status();
        if (current && data.status !== current.status) {
          this.statusChangeNotice.set(`Status updated: ${current.status} -> ${data.status}`);
          setTimeout(() => this.statusChangeNotice.set(''), 8000);
        }
        this.status.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }

  getStatusClass(status: string): string {
    if (!status) return '';
    const s = status.toUpperCase();
    switch (s) {
      case 'NEW':
      case 'PENDING': return 'status-new';
      case 'ASSIGNED': return 'status-assigned';
      case 'IN_PROGRESS': return 'status-in-progress';
      case 'UNDER_REVIEW': return 'status-under-review';
      case 'ESCALATED': return 'status-escalated';
      case 'RESOLVED': return 'status-resolved';
      case 'CLOSED': return 'status-closed';
      case 'WITHDRAWN': return 'status-withdrawn';
      default: return '';
    }
  }

  downloadPdf() {
    const s = this.status();
    if (!s) return;

    this.pdfLoading.set(true);

    import('jspdf').then(({ jsPDF }) => {
      try {
        const doc = new jsPDF();
        const pw = doc.internal.pageSize.getWidth();
        let y = 20;

        doc.setFontSize(14);
        doc.setFont('helvetica', 'bold');
        doc.text('COMPLAINT STATUS REPORT', pw / 2, y, { align: 'center' });
        y += 10;
        doc.setFontSize(10);
        doc.setFont('helvetica', 'normal');
        doc.text(`Generated: ${new Date().toLocaleString('en-IN')}`, pw / 2, y, { align: 'center' });
        y += 12;

        const addRow = (label: string, value: string) => {
          if (y > 270) { doc.addPage(); y = 20; }
          doc.setFont('helvetica', 'bold');
          doc.text(`${label}:`, 20, y);
          doc.setFont('helvetica', 'normal');
          doc.text(value || 'N/A', 70, y);
          y += 7;
        };

        addRow('Complaint ID', s.complaintId);
        addRow('Status', s.status);
        addRow('Category', s.category);
        addRow('Regulated Entity', (s as any).entityName || 'N/A');
        addRow('Registered', s.registeredAt ? new Date(s.registeredAt).toLocaleDateString('en-IN') : '');
        addRow('SLA Due', s.slaDueDate ? new Date(s.slaDueDate).toLocaleDateString('en-IN') : '');
        addRow('Closure Clause', (s as any).closureClause || 'N/A');
        addRow('Closure Date', (s as any).closedAt ? new Date((s as any).closedAt).toLocaleDateString('en-IN') : 'N/A');
        if (s.assignedTeam) addRow('Assigned Team', s.assignedTeam);
        if (s.resolutionSummary) addRow('Resolution', s.resolutionSummary);

        // 4-stage representation
        if (s.stages && s.stages.length > 0) {
          y += 5;
          doc.setFont('helvetica', 'bold');
          doc.text('Complaint Progress:', 20, y);
          y += 7;
          doc.setFont('helvetica', 'normal');
          for (const stage of s.stages) {
            if (y > 270) { doc.addPage(); y = 20; }
            const statusIcon = stage.status === 'completed' ? '[Done]' :
                               stage.status === 'current' ? '[Current]' : '[Pending]';
            const dateStr = stage.date ? ` (${new Date(stage.date).toLocaleDateString('en-IN')})` : '';
            doc.text(`  Stage ${stage.stage}: ${stage.label} ${statusIcon}${dateStr}`, 25, y);
            y += 6;
          }
        }

        // Detailed timeline
        if (s.timeline && s.timeline.length > 0) {
          y += 5;
          doc.setFont('helvetica', 'bold');
          doc.text('Detailed Timeline:', 20, y);
          y += 7;
          doc.setFont('helvetica', 'normal');
          for (const entry of s.timeline) {
            if (y > 270) { doc.addPage(); y = 20; }
            doc.text(`${new Date(entry.timestamp).toLocaleDateString('en-IN')} - ${entry.action} (${entry.fromStatus} -> ${entry.toStatus})`, 25, y);
            y += 6;
          }
        }

        // This report carries no cryptographic signature — there is no CMS certificate authority.
        // Claiming one ("DIGITALLY SIGNED | RBI CMS Digital Certificate Authority") invited the
        // citizen to treat an unsigned PDF as tamper-proof, so state the true position instead and
        // point them at the portal, which is the actual authoritative source.
        y += 10;
        if (y > 262) { doc.addPage(); y = 20; }
        doc.setDrawColor(150);
        doc.setFillColor(245, 245, 245);
        doc.roundedRect(20, y, pw - 40, 22, 2, 2, 'FD');
        doc.setTextColor(80);
        doc.setFontSize(8);
        doc.setFont('helvetica', 'bold');
        doc.text('SYSTEM-GENERATED REPORT — NOT A DIGITALLY SIGNED DOCUMENT', 25, y + 8);
        doc.setFont('helvetica', 'normal');
        doc.text(`Verify the current status online by tracking complaint ${s.complaintId} on the RBI CMS portal.`, 25, y + 16);
        doc.setTextColor(0);

        doc.save(`Complaint_${s.complaintId}.pdf`);
        this.pdfLoading.set(false);
      } catch (err) {
        console.error('PDF generation failed:', err);
        this.error.set('Failed to generate PDF. Please try again.');
        this.pdfLoading.set(false);
      }
    }).catch(() => {
      this.error.set('Failed to load PDF library. Please try again.');
      this.pdfLoading.set(false);
    });
  }
}
