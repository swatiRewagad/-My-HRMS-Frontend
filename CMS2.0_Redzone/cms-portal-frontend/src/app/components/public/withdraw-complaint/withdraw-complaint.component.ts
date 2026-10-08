import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ComplaintService } from '../../../services/complaint.service';
import { PublicAuthService } from '../../../services/public-auth.service';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { UploadLimitsService } from '../../../services/upload-limits.service';

@Component({
  selector: 'app-withdraw-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe],
  templateUrl: './withdraw-complaint.component.html',
  styleUrl: './withdraw-complaint.component.scss'
})
export class WithdrawComplaintComponent implements OnInit {

  /**
   * UST107 / FR-G-036 scenario 5 (Document/CMS_Portal_UTS.txt) — verbatim. The server answers the
   * same string so a citizen sees one wording whichever side refuses.
   */
  static readonly REQUIRED_REASON_MESSAGE = 'Reason for withdrawal is required.';

  /**
   * UST107 / FR-G-036 scenario 3 — verbatim, and deliberately ONE message for both conditions:
   * "When the file exceeds the size limit OR is in an unsupported format". The component used to
   * emit two different strings, neither of them the specified one.
   */
  static readonly INVALID_FILE_MESSAGE =
    'Invalid file type or size, please upload a valid document.';

  /** UST105 / FR-G-035 scenario 3 — verbatim, for a status the Scheme excludes. */
  static readonly NOT_WITHDRAWABLE_MESSAGE = 'Complaints cannot be withdrawn.';

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private complaintService = inject(ComplaintService);
  private authService = inject(PublicAuthService);
  // Public: the templates render the configured limit in their upload hints.
  uploadLimits = inject(UploadLimitsService);

  // FR-G-027: Withdraw complaint
  phase = signal<'search' | 'confirm' | 'success'>('search');
  complaintId = '';
  reason = '';
  additionalRemarks = '';
  loading = signal(false);
  error = '';
  withdrawnRef = '';

  // FR-G-028: Withdrawal reasons
  reasons = [
    'Issue resolved by the Regulated Entity',
    'Complaint filed by mistake',
    'Duplicate complaint filed',
    'Want to approach a different forum',
    'Personal reasons',
    'Other',
  ];

  // FR-G-036: Supporting documents (optional)
  withdrawalDocs: File[] = [];
  isDragOver = false;
  fileUploadError = '';

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.complaintId = id;
    }
  }

  searchComplaint() {
    if (!this.complaintId.trim()) {
      this.error = 'Please enter your complaint reference number.';
      return;
    }
    this.error = '';
    this.loading.set(true);

    this.complaintService.trackComplaint(this.complaintId.trim()).subscribe({
      next: (complaint) => {
        this.loading.set(false);

        // UST105 scenario 3. The server refuses a withdrawal for a closed complaint, one sent to
        // another department, or one sent to another regulatory body — but the form used to open for
        // any complaint the tracker could find, so the citizen chose a reason, attached a document
        // and was refused only on submit. `withdrawable` is the server's own answer to the same
        // question the withdraw call will ask, so the two cannot disagree.
        //
        // `=== false`, not `!`: an older backend omits the field entirely, and treating "unknown" as
        // "forbidden" would deny a live right on a deployment skew. Unknown falls through to the
        // server, which is the enforcement point regardless.
        if (complaint?.withdrawable === false) {
          this.error = WithdrawComplaintComponent.NOT_WITHDRAWABLE_MESSAGE;
          return;
        }
        this.phase.set('confirm');
      },
      error: () => {
        this.loading.set(false);
        this.error = 'No complaint found with this reference number, or it is not eligible for withdrawal.';
      }
    });
  }

  // FR-G-029: Confirm withdrawal
  confirmWithdraw() {
    if (!this.reason) {
      this.error = WithdrawComplaintComponent.REQUIRED_REASON_MESSAGE;
      return;
    }
    this.error = '';
    this.loading.set(true);

    // `phone` is authorisation, not metadata: the withdraw endpoint rejects the request unless it
    // matches the complaint's registered mobile number. This page's tracker call returns a
    // ComplaintStatus, which carries no complainant phone, so the authenticated citizen's own
    // session identifier is the only one available here — the same source every other public
    // component behind publicAuthGuard uses (see complaint-history, public-layout, file-complaint).
    const phone = this.authService.userIdentifier();

    // The documents go WITH the withdrawal (UST107 scenario 4). They used to be collected here and
    // never sent anywhere.
    this.complaintService.withdrawComplaint(
      this.complaintId, this.reason, this.additionalRemarks, phone, this.withdrawalDocs,
    ).subscribe({
      next: () => {
        this.withdrawnRef = this.complaintId;
        this.loading.set(false);
        this.phase.set('success');
      },
      error: (err) => {
        this.loading.set(false);
        this.error = err.error?.message || 'Failed to withdraw complaint. Please try again.';
      }
    });
  }

  /**
   * The formats a withdrawal document may be in.
   *
   * Narrowed to the set the citizen complaint form already accepts (`file-validator.ts:36` —
   * .pdf .doc .jpg .jpeg .png). The previous list also admitted DOCX/XLS/XLSX, which contradicted
   * the wizard on the very same portal: a citizen could attach a spreadsheet when withdrawing but
   * not when filing. UST107 scenario 3 names no formats, so the portal's own established set is the
   * authority, and keeping one set means one answer to "what can I attach?".
   *
   * Matched on EXTENSION, not on `file.type`: the browser reports an empty or generic MIME type for
   * plenty of legitimate files (notably .doc from some Windows configurations), and an empty string
   * passed the old MIME check silently because the guard ran `includes('')` on a list that of course
   * did not contain it — so a type-less file was refused with a message naming types it might well
   * have been. The server sniffs the real magic bytes anyway (FileUploadValidator), so the extension
   * is the right client-side gate: it is what the citizen can see and correct.
   */
  private static readonly ALLOWED_DOC_EXTENSIONS = ['pdf', 'doc', 'jpg', 'jpeg', 'png'];

  /**
   * UST107 scenario 3: ONE message for "exceeds the size limit OR is in an unsupported format".
   * Size is checked against the live configured limit, never a compiled-in figure.
   */
  private acceptWithdrawalFile(file: File): boolean {
    const extension = (file.name.split('.').pop() || '').toLowerCase();
    const formatOk = WithdrawComplaintComponent.ALLOWED_DOC_EXTENSIONS.includes(extension);
    const sizeOk = file.size <= this.uploadLimits.maxFileSizeBytes();

    if (!formatOk || !sizeOk) {
      this.fileUploadError = WithdrawComplaintComponent.INVALID_FILE_MESSAGE;
      return false;
    }
    this.withdrawalDocs.push(file);
    return true;
  }

  onWithdrawalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.fileUploadError = '';
    for (let i = 0; i < input.files.length; i++) {
      this.acceptWithdrawalFile(input.files[i]);
    }
    input.value = '';
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    this.fileUploadError = '';
    for (let i = 0; i < event.dataTransfer.files.length; i++) {
      this.acceptWithdrawalFile(event.dataTransfer.files[i]);
    }
  }

  removeWithdrawalDoc(index: number) {
    this.withdrawalDocs.splice(index, 1);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  trackComplaint() {
    this.router.navigate(['/public/track', this.withdrawnRef]);
  }
}
