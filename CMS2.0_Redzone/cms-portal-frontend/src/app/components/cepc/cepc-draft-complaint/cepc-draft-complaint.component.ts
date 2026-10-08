import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { NavigationService } from '../../../services/navigation.service';
import { CepcContextService } from '../../../services/cepc-context.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcHeaderComponent } from '../cepc-header/cepc-header.component';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';
import { lookupPincode } from '../../../utils/pincode-data';

/**
 * The screen opened from a DRAFT row on the CEPC dashboard. Carved out of
 * cepc-complaint-details-view (which only ever rendered the generic assessment/tabs
 * view for these rows) because a draft still being intake-captured — scanned letter,
 * basic details, not yet triaged into eligibility/entity/complainant — needs its own
 * light-weight editor rather than the full workflow screen.
 */
@Component({
  selector: 'app-cepc-draft-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, ToastModule, SpeechButtonComponent, CepcHeaderComponent, TranslateOrPipe],
  templateUrl: './cepc-draft-complaint.component.html',
  styleUrl: './cepc-draft-complaint.component.scss'
})
export class CepcDraftComplaintComponent implements OnInit {

  protected Math = Math;

  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private navService = inject(NavigationService);
  private messageService = inject(MessageService);
  readonly dept = inject(CepcContextService);

  complaintId = '';
  complaintNumber = '';
  statusLabel = 'Draft';
  assignedOfficer = '';

  loadState = signal<'loading' | 'ready' | 'failed'>('loading');
  loadError = signal('');

  // Basic Details
  subject = '';
  description = '';
  comments = '';
  modeOfReceipt = 'PHYSICAL_LETTER';
  receivedDate = '';
  isCpgram = false;
  cpgramsNumber = '';

  sectionExpanded = { basic: true, eligibility: false, entity: false, complainant: false };
  formSubmitAttempted = false;
  fieldErrors: Record<string, string> = {};

  // Eligibility
  proposedComplaintType = 'NEW_COMPLAINT';
  eligibilityEntityName = '';
  eligibilityEntitySearch = '';
  eligibilityEntityResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  showEligibilityEntityDropdown = signal(false);
  private eligibilityEntityTimeout: any = null;
  markAllEligible = false;
  eligibilityQuestions: { key: string; label: string; answer: boolean | null }[] = [
    { key: 'entityRegulatedByRbi', label: 'Is Entity regulated by RBI?', answer: null },
    { key: 'notDirectlyAddressed', label: 'The Complaint not directly addressed to Ombudsman', answer: null },
    { key: 'notRegisteredWithEntity', label: 'Is the Complaint not registered with Entity (FRC)?', answer: null },
    { key: 'frivolousVexatious', label: 'Is the complainant frivolous, vexatious, and threatening?', answer: null },
    { key: 'subJudice', label: 'Is the Complaint Sub-Judice or under arbitration?', answer: null },
    { key: 'isAdvocate', label: 'Is the complainant an advocate?', answer: null },
    { key: 'alreadyDealt', label: 'Has already been dealt with or is under process on the same ground with the ombudsman?', answer: null },
    { key: 'generalAgainstManagement', label: 'Does the complaint involve general complaints against management or executives of a RE?', answer: null },
    { key: 'disputesBetweenREs', label: 'Does it involve disputes between REs?', answer: null },
    { key: 'staffEmployerRelationship', label: 'Is from staff of an RE and involves employer-employee relationship?', answer: null },
    { key: 'incompleteInformation', label: 'Complete information not available for registering the complaint', answer: null },
  ];

  // Entity Details
  entityName = '';
  moduleName = '';
  entityCategory = '';
  entityTypeDisplay = '';
  bsrCode = '';
  entityPincode = '';
  entityCountry = 'India';
  entityState = '';
  entityDistrict = '';
  entityCity = '';
  entityBranchName = '';
  entityBranchCategory = '';
  entityAddress = '';
  branchCenterName = '';
  cosmosCode = '';
  assetSizeInCrores: number | null = null;
  entityQuestions: { key: string; label: string; answer: boolean | null }[] = [
    { key: 'depositTaking', label: 'Whether Deposit Taking/Non-Deposit Taking Entity', answer: null },
    { key: 'assetGreater100Cr', label: 'Whether Asset Size is greater than 100 Crores', answer: null },
    { key: 'liquidatedPresent', label: 'Whether Liquidated present', answer: null },
  ];

  // Complainant Details
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';
  complainantAddress = '';
  states = signal<string[]>([]);
  districts = signal<string[]>([]);
  pincodeLoading = signal(false);

  private statesFallback = [
    'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat',
    'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh',
    'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
    'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh',
    'Uttarakhand', 'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh',
    'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Jammu and Kashmir', 'Ladakh',
    'Lakshadweep', 'Puducherry'
  ];

  // Left panel - scanned letter upload
  scannedFile: File | null = null;
  scanError = '';
  isDragOver = false;
  ocrInProgress = signal(false);
  ocrComplete = signal(false);
  pdfExpanded = signal(false);
  pdfPage = signal(1);
  pdfTotalPages = signal(1);
  pdfPreviewUrl = signal<SafeResourceUrl | null>(null);

  saving = signal(false);

  ngOnInit(): void {
    this.loadStates();
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.complaintId = id;
        this.loadDraft(id);
      } else {
        this.loadError.set('No complaint id in the address.');
        this.loadState.set('failed');
      }
    });
  }

  private loadDraft(id: string): void {
    this.loadState.set('loading');
    this.http.get<any>(`${environment.apiBaseUrl}${this.dept.cx(`${id}/summary`)}`).subscribe({
      next: (res) => {
        const data = res?.data || res || {};
        const basic = data.basicDetailsDto || {};
        const entity = data.entityDetails || {};
        const eligibility = data.eligibility || {};
        this.complaintNumber = data.navBarDto?.complaintNumber || '';
        this.statusLabel = data.navBarDto?.statusLabel || 'Draft';
        this.assignedOfficer = data.assignedOfficer || '';

        this.subject = basic.subject || '';
        this.description = basic.complainDetails || '';
        this.comments = basic.comments || '';
        this.modeOfReceipt = basic.modeOfReceipt || 'PHYSICAL_LETTER';
        this.receivedDate = basic.receiptDate || '';
        this.isCpgram = !!basic.complaintCpgram;
        this.cpgramsNumber = basic.cpgramNumber || '';
        this.complainantName = basic.complainantName || '';
        this.complainantEmail = basic.emailId || '';
        this.complainantPhone = basic.mobile || '';

        this.proposedComplaintType = eligibility.proposedComplaintType || 'NEW_COMPLAINT';

        this.entityName = entity.entityName || '';
        this.moduleName = entity.moduleName || '';
        this.entityCategory = entity.entityCategory || '';
        this.entityTypeDisplay = entity.entityType || '';
        this.bsrCode = entity.bsrCode || '';
        this.entityPincode = entity.pincode || '';
        this.entityCountry = entity.country || 'India';
        this.entityState = entity.state || '';
        this.entityDistrict = entity.district || '';
        this.entityCity = entity.city || '';
        this.entityBranchName = entity.branchName || '';
        this.entityBranchCategory = entity.branchCategory || '';
        this.entityAddress = entity.entityAddress || '';
        this.branchCenterName = entity.branchCenterName || '';

        this.loadState.set('ready');
      },
      error: (err) => {
        this.loadError.set(err?.status === 403
          ? 'You do not have access to this complaint.'
          : err?.status === 404
            ? 'This complaint could not be found.'
            : 'Could not load this complaint from the server.');
        this.loadState.set('failed');
      }
    });
  }

  private validateForm(): boolean {
    this.fieldErrors = {};
    if (!this.subject.trim()) this.fieldErrors['subject'] = 'Subject is required.';
    if (!this.description.trim()) this.fieldErrors['description'] = 'Complaint Details is required.';
    if (!this.modeOfReceipt) this.fieldErrors['modeOfReceipt'] = 'Mode of Receipt is required.';
    if (this.modeOfReceipt === 'PHYSICAL_LETTER' && !this.receivedDate) {
      this.fieldErrors['receivedDate'] = 'Receipt Date is required for Physical Letters.';
    }
    if (this.complainantEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.complainantEmail)) {
      this.fieldErrors['complainantEmail'] = 'Enter a valid email address.';
    }
    if (this.complainantPhone && !/^\d{10}$/.test(this.complainantPhone)) {
      this.fieldErrors['complainantPhone'] = 'Enter a valid 10-digit mobile number.';
    }
    if (this.complainantPincode && !/^\d{6}$/.test(this.complainantPincode)) {
      this.fieldErrors['complainantPincode'] = 'Enter a valid 6-digit pincode.';
    }
    if (this.entityPincode && !/^\d{6}$/.test(this.entityPincode)) {
      this.fieldErrors['entityPincode'] = 'Enter a valid 6-digit pincode.';
    }
    return Object.keys(this.fieldErrors).length === 0;
  }

  saveDraft(): void {
    this.formSubmitAttempted = true;
    if (!this.validateForm() || !this.complaintId) return;

    this.saving.set(true);
    const payload = {
      basicDetailsDto: {
        subject: this.subject,
        complainDetails: this.description,
        modeOfReceipt: this.modeOfReceipt,
        receiptDate: this.receivedDate || null,
        comments: this.comments,
        complaintCpgram: this.isCpgram,
        cpgramNumber: this.isCpgram ? this.cpgramsNumber : null,
        complainantName: this.complainantName,
        emailId: this.complainantEmail,
        mobile: this.complainantPhone
      },
      entityDetails: {
        entityName: this.entityName,
        moduleName: this.moduleName,
        entityCategory: this.entityCategory,
        bsrCode: this.bsrCode,
        pincode: this.entityPincode,
        country: this.entityCountry,
        state: this.entityState,
        district: this.entityDistrict,
        city: this.entityCity,
        branchName: this.entityBranchName,
        branchCategory: this.entityBranchCategory,
        branchCenterName: this.branchCenterName,
        entityAddress: this.entityAddress
      },
      eligibility: {
        proposedComplaintType: this.proposedComplaintType
      }
    };

    this.http.put<any>(`${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/summary`)}`, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Draft saved successfully.' });
      },
      error: (err) => {
        this.saving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Save Failed',
          detail: err?.error?.message || 'Could not save. Check the fields and try again.'
        });
      }
    });
  }

  // ─── Scanned letter upload ───
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];
    if (!allowed.includes(file.type)) {
      this.scanError = 'Only PDF, JPEG, PNG, or TIFF files are accepted.';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.scanError = 'File size must not exceed 5 MB.';
      return;
    }

    this.scannedFile = file;
    this.scanError = '';

    if (file.type === 'application/pdf') {
      const url = URL.createObjectURL(file);
      this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    }
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    if (!event.dataTransfer?.files?.length) return;
    const fakeEvent = { target: { files: event.dataTransfer.files } } as unknown as Event;
    this.onFileSelected(fakeEvent);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  removeFile(): void {
    this.scannedFile = null;
    this.pdfPreviewUrl.set(null);
    this.ocrComplete.set(false);
  }

  runOcr(): void {
    if (!this.scannedFile) return;
    this.ocrInProgress.set(true);
    this.scanError = '';

    const formData = new FormData();
    formData.append('file', this.scannedFile);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/ocr/extract`, formData).subscribe({
      next: (res) => {
        const data = res?.data || {};
        if (Object.keys(data).length === 0) {
          this.ocrInProgress.set(false);
          this.scanError = 'AI extraction returned no data. Please fill manually or try again later.';
          return;
        }

        if (data.subject) this.subject = data.subject;
        if (data.description) this.description = data.description;

        this.ocrInProgress.set(false);
        this.ocrComplete.set(true);
      },
      error: (err) => {
        this.ocrInProgress.set(false);
        this.scanError = 'AI extraction failed: ' + (err.error?.message || 'Service unavailable. Please fill manually.');
      }
    });
  }

  skipOcr(): void {
    this.ocrComplete.set(true);
  }

  // ─── Eligibility ───
  onEligibilityEntitySearch(value: string): void {
    this.eligibilityEntitySearch = value;
    if (this.eligibilityEntityTimeout) clearTimeout(this.eligibilityEntityTimeout);
    if (!value || value.length < 2) {
      this.showEligibilityEntityDropdown.set(false);
      return;
    }
    this.eligibilityEntityTimeout = setTimeout(() => {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, {
        params: { search: value }
      }).subscribe({
        next: (res) => {
          this.eligibilityEntityResults.set(res?.data || []);
          this.showEligibilityEntityDropdown.set(true);
        },
        error: () => this.eligibilityEntityResults.set([])
      });
    }, 300);
  }

  selectEligibilityEntity(entity: { id: number; name: string; department: string; entityType: string }): void {
    this.eligibilityEntityName = entity.name;
    this.eligibilityEntitySearch = entity.name;
    this.showEligibilityEntityDropdown.set(false);
  }

  onEligibilityEntityBlur(): void {
    setTimeout(() => this.showEligibilityEntityDropdown.set(false), 200);
  }

  onMarkAllEligible(): void {
    if (this.markAllEligible) {
      for (const q of this.eligibilityQuestions) {
        q.answer = q.key === 'entityRegulatedByRbi';
      }
    } else {
      for (const q of this.eligibilityQuestions) {
        q.answer = null;
      }
    }
  }

  // ─── Entity Details ───
  onEntityPincodeInput(value: string): void {
    this.entityPincode = value;
    if (!value) {
      delete this.fieldErrors['entityPincode'];
    } else if (!/^\d*$/.test(value)) {
      this.fieldErrors['entityPincode'] = 'Pincode must contain only digits.';
    } else if (value.length !== 6) {
      this.fieldErrors['entityPincode'] = 'Pincode must be exactly 6 digits.';
    } else {
      delete this.fieldErrors['entityPincode'];
    }
  }

  // ─── Complainant Details ───
  private loadStates(): void {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => {
        const data = res?.data || [];
        this.states.set(data.length > 0 ? data : this.statesFallback);
      },
      error: () => this.states.set(this.statesFallback)
    });
  }

  onStateChange(state: string): void {
    this.complainantState = state;
    this.complainantDistrict = '';
    this.districts.set([]);
    if (state) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state } }).subscribe({
        next: (res) => this.districts.set(res?.data || []),
        error: () => this.districts.set([])
      });
    }
  }

  onPincodeInput(value: string): void {
    this.complainantPincode = value;
    if (!value) {
      delete this.fieldErrors['complainantPincode'];
    } else if (!/^\d*$/.test(value)) {
      this.fieldErrors['complainantPincode'] = 'Pincode must contain only digits.';
    } else if (value.length !== 6) {
      this.fieldErrors['complainantPincode'] = 'Pincode must be exactly 6 digits.';
    } else {
      delete this.fieldErrors['complainantPincode'];
      this.pincodeLoading.set(true);
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading.set(false);
          if (res?.data?.length) {
            const po = res.data[0];
            if (po.state) {
              this.complainantState = po.state;
              this.onStateChange(po.state);
            }
            if (po.district) this.complainantDistrict = po.district;
          } else {
            this.applyLocalPincode(value);
          }
        },
        error: () => {
          this.pincodeLoading.set(false);
          this.applyLocalPincode(value);
        }
      });
    }
  }

  private applyLocalPincode(value: string): void {
    const entry = lookupPincode(value);
    if (entry) {
      this.complainantState = entry.state;
      this.onStateChange(entry.state);
      this.complainantDistrict = entry.district;
      delete this.fieldErrors['complainantPincode'];
    } else {
      this.fieldErrors['complainantPincode'] = 'Invalid pincode. No location found.';
    }
  }

  goBack(): void {
    this.navService.goBack([this.dept.cfg().routePrefix]);
  }
}
