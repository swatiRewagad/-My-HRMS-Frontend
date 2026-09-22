import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, NonNullableFormBuilder, FormControl, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Observable, of, Subject, ReplaySubject, interval, merge, forkJoin } from 'rxjs';
import { catchError, tap, switchMap, takeUntil, distinctUntilChanged, map, startWith, filter, take } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ComplaintService } from '../../../services/complaint.service';
import { FileUploadService } from '../../../services/file-upload.service';
import { PublicAuthService } from '../../../services/public-auth.service';
import { CustomValidators } from '../../../utils/custom-validators';
import { lookupPincode } from '../../../utils/pincode-data';
import {
  BankEntity, AccountType, SelectOption, AttachmentPreview,
  EligibilityQuestion, ComplaintPayload,
} from '../models';
import {
  INITIAL_FORM_DATA, DEFAULT_CATEGORIES, ACCOUNT_TYPE_FIELD_MAP,
  ELIGIBILITY_QUESTIONS,
} from '../configs';
import { validateFile, validateStepQuota } from '../../../utils/file-validator';

export interface FileMeta {
  fileId: string;
  fileName: string;
  fileSize: number;
  viewUrl: string;
}

function fileMetaRequired(control: AbstractControl): ValidationErrors | null {
  const val = control.value;
  return Array.isArray(val) && val.length > 0 ? null : { required: true };
}

const MAX_FILE_SIZE = 2 * 1024 * 1024;

@Injectable()
export class ComplaintFacadeService {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private nnfb = inject(NonNullableFormBuilder);
  private complaintService = inject(ComplaintService);
  private fileUploadService = inject(FileUploadService);
  private publicAuth = inject(PublicAuthService);

  // ── Submit result observable ──
  private submitResultSubject = new Subject<{ success: boolean; message?: string }>();
  submitResult$ = this.submitResultSubject.asObservable();

  // ── Signals ──
  phase = signal<'eligibility' | 'form' | 'success' | 'non-maintainable'>('eligibility');
  eligibilityStep = signal(1);
  eligibilityBlocked = signal(false);
  eligibilityBlockMessage = signal('');
  eligibilityBlockMessageKey = signal('');
  closureLetterPara2 = signal('');
  showSimplified = signal(false);
  currentStep = signal(1);
  highestStepReached = signal(1);
  submitting = signal(false);
  draftSaved = signal(false);
  lastSavedAt = signal('');
  uploading = signal<Record<string, boolean>>({});
  private uploadErrorSubject = new Subject<string>();
  uploadError$ = this.uploadErrorSubject.asObservable();
  showDuplicatePopup = signal(false);
  isRecording = signal(false);
  showReplyDateAutoClosure = signal(false);

  // ── State ──
  formData: Record<string, any> = { ...INITIAL_FORM_DATA };
  eligibilityAnswers: Record<string, string> = {};
  eligibilityQuestions: EligibilityQuestion[] = JSON.parse(JSON.stringify(ELIGIBILITY_QUESTIONS));
  banks: BankEntity[] = [];
  categories: SelectOption[] = [];
  subCategories: Record<string, SelectOption[]> = {};
  accountTypes: AccountType[] = [];
  states: SelectOption[] = [];
  complainantStatesList: SelectOption[] = [];

  attachments: File[] = [];
  attachmentPreviews: AttachmentPreview[] = [];

  // Location state
  complainantStates: string[] = [];
  complainantDistricts: string[] = [];
  districts: string[] = [];
  branches: string[] = [];
  entityBranchOptions: string[] = ['Main Branch', 'North Zone Branch', 'Downtown Branch', 'South Extension Branch'];
  repStates: string[] = [];
  repDistricts: string[] = [];
  repCities: string[] = [];
  pincodeLoading = false;
  repPincodeLoading = false;

  // Eligibility file state
  complaintFileWithRE: File | null = null;
  complaintFileWithREName = '';
  complaintFileMeta: FileMeta | null = null;
  reminderFile: File | null = null;
  reminderFileName = '';
  reminderFileMeta: FileMeta | null = null;
  replyFile: File | null = null;
  replyFileName = '';
  replyFileMeta: FileMeta | null = null;
  repFiles: File[] = [];

  get eligibilityBytesUsed(): number {
    return (this.complaintFileWithRE?.size ?? 0) + (this.reminderFile?.size ?? 0) + (this.replyFile?.size ?? 0);
  }

  get complaintDetailsBytesUsed(): number {
    return this.attachments.reduce((sum, f) => sum + f.size, 0);
  }

  get repAuthBytesUsed(): number {
    return this.repFiles.reduce((sum, f) => sum + f.size, 0);
  }

  // Error state
  validationErrors: Record<string, string> = {};
  eligibilityFieldError = '';
  eligibilityFileError = '';
  eligibilityRefError = '';
  replyDateError = '';
  replyFileError = '';
  reminderDateError = '';
  reminderFileError = '';
  fileUploadError = '';

  // Misc
  nonMaintainableCaseId = '';
  duplicateMessage = '';
  duplicateCheckDone = false;
  declarationChecked = false;
  declaration2Checked = false;
  referenceNumber = '';
  submittedStatus = '';
  submittedAt = '';
  slaDueDate = '';
  dateDisplay: Record<string, string> = { bankComplaintDate: '', reminderDate: '', replyDate: '' };

  entitySearchText = '';
  entityDropdownOpen = false;
  filteredEntityOptions: SelectOption[] = [];
  entitySelectOptions: SelectOption[] = [];
  nonCoveredEntityOptions: SelectOption[] = [];

  private destroy$ = new Subject<void>();
  private entitiesLoaded$ = new ReplaySubject<void>(1);
  private manualSave$ = new Subject<void>();
  draftId = signal('');
  private recognition: any = null;

  // ── Forms ──
  complaintStepperForm = this.nnfb.group({
    complainantDetails: this.nnfb.group({
      complaintCategory: ['', [Validators.required]],
      firstName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50), CustomValidators.alphaNumeric()]],
      middleName: ['', [Validators.maxLength(50), CustomValidators.alphaNumeric()]],
      lastName: ['', [Validators.required, Validators.maxLength(50), CustomValidators.alphaNumeric()]],
      age: ['', [Validators.min(18), Validators.max(120), CustomValidators.numericOnly()]],
      gender: [''],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
      phone: [{value: '', disabled: true}],
      pincode: ['', [Validators.required]],
      state: [{value: '', disabled: true}, [Validators.required]],
      city: [{value: '', disabled: true}, [Validators.required]],
      addressDetails: ['', [Validators.required, Validators.maxLength(500)]],
    }),
    regulatedEntity: this.nnfb.group({
      isCreditCardComplaint: ['', [Validators.required]],
      entityState: [''],
      entityDistrict: [''],
      entityBranch: [''],
    }),
    complaintDetails: this.nnfb.group({
      complaintCategory: ['', [Validators.required]],
      complaintText: ['', [Validators.required, Validators.maxLength(5000)]],
      hasAccountWithRE: ['', [Validators.required]],
      accountTypeSelection: [''],
      savingsAccountNumber: [''],
      loanAccountNumber: [''],
      atmDebitCardNumber: [''],
      creditCardNumber: [''],
      isWalletComplaint: ['', [Validators.required]],
      walletName: [''],
      transactionRefNumber: [''],
      isBusinessCorrespondent: ['', [Validators.required]],
      disputeAmount: ['', [CustomValidators.numericOnly()]],
      compensationSought: ['', [CustomValidators.numericOnly(), CustomValidators.maxCeiling(3000000, 'Compensation for consequential loss can be awarded only up to ₹30 lakh.')]],
      reliefSought: ['', [CustomValidators.numericOnly(), CustomValidators.maxCeiling(300000, 'Compensation for expenses, harassment, and mental anguish can be awarded only up to ₹3 lakh.')]],
      fileUpload: new FormControl<FileMeta[]>([], { nonNullable: true, validators: [fileMetaRequired] }),
    }),
    repAuthorization: this.nnfb.group({
      hasAuthRep: ['', [Validators.required]],
      repName: [''],
      repEmail: [''],
      repPhone: [''],
      repPincode: [''],
      repState: [{value: '', disabled: true}],
      repDistrict: [{value: '', disabled: true}],
      repCity: [{value: '', disabled: true}],
      repAddress: [''],
      repFileUpload: new FormControl<FileMeta[]>([], { nonNullable: true }),
    }),
    declaration: this.nnfb.group({
      declaration1: [false, [Validators.requiredTrue]],
      declaration2: [false, [Validators.requiredTrue]],
    }),
  });

  eligibilityStageForm = this.fb.group({
    regulatedEntity: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    filedWithRE: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    bankComplaintDate: new FormControl<string>('', { nonNullable: true }),
    bankComplaintRef: new FormControl<string>('', { nonNullable: true }),
    complaintFileWithRE: new FormControl<FileMeta | null>(null),
    receivedReply: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    replyDate: new FormControl<string>('', { nonNullable: true }),
    replyFileMeta: new FormControl<FileMeta | null>(null),
    sentReminder: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    reminderDate: new FormControl<string>('', { nonNullable: true }),
    reminderFileMeta: new FormControl<FileMeta | null>(null),
    isSubJudice: new FormControl<string>('', { nonNullable: true }),
    alreadySettled: new FormControl<string>('', { nonNullable: true }),
    throughAdvocateEligibility: new FormControl<string>('', { nonNullable: true }),
    isComplainantSelf: new FormControl<string>('', { nonNullable: true }),
    pendingBeforeOmbudsman: new FormControl<string>('', { nonNullable: true }),
    settledByOmbudsman: new FormControl<string>('', { nonNullable: true }),
    staffOfRE: new FormControl<string>('', { nonNullable: true }),
    previouslyFiledWithCEPC: new FormControl<string>('', { nonNullable: true }),
    employeeOfRE: new FormControl<string>('', { nonNullable: true }),
    employerRelationship: new FormControl<string>('', { nonNullable: true }),
  });

  // ── Form accessors ──
  get complainantDetailsForm() { return this.complaintStepperForm.controls.complainantDetails; }
  get regulatedEntityForm() { return this.complaintStepperForm.controls.regulatedEntity; }
  get complaintDetailsForm() { return this.complaintStepperForm.controls.complaintDetails; }
  get repAuthorizationForm() { return this.complaintStepperForm.controls.repAuthorization; }
  get declarationForm() { return this.complaintStepperForm.controls.declaration; }

  // ── Initialization ──
  initialize(): void {
    this.setupEligibilityFormWatchers();
    this.setupComplainantFormWatchers();
    this.setupRegulatedEntityFormWatchers();
    this.setupComplaintDetailsFormWatchers();
    this.setupRepAuthorizationFormWatchers();
    this.setupDeclarationFormWatchers();
    this.loadRegulatedEntities();
    this.loadMasterData();
    this.formData['phone'] = this.publicAuth.userIdentifier() || '';
    this.complainantDetailsForm.controls.phone.setValue(this.formData['phone']);
    this.startAutoSave();
  }

  destroy(): void {
    if (this.phase() === 'form' || this.phase() === 'eligibility') {
      this.saveDraftToServer();
    }
    this.destroy$.next();
    this.destroy$.complete();
    this.stopRecording();
  }

  // ── Eligibility form watchers ──
  private setupEligibilityFormWatchers(): void {
    const form = this.eligibilityStageForm;

    this.watchConditionalValidators(form, 'filedWithRE', 'yes',
      [{ ctrl: 'bankComplaintDate', validators: [Validators.required] },
       { ctrl: 'complaintFileWithRE', validators: [Validators.required] }]);

    this.watchConditionalValidators(form, 'receivedReply', 'yes',
      [{ ctrl: 'replyDate', validators: [Validators.required] },
       { ctrl: 'replyFileMeta', validators: [Validators.required] }]);

    this.watchConditionalValidators(form, 'sentReminder', 'yes',
      [{ ctrl: 'reminderDate', validators: [Validators.required] },
       { ctrl: 'reminderFileMeta', validators: [Validators.required] }]);

    form.get('throughAdvocateEligibility')!.valueChanges.subscribe(val => {
      const selfCtrl = form.get('isComplainantSelf')!;
      if (val === 'yes') {
        selfCtrl.reset('');
        this.formData['isComplainantSelf'] = '';
        this.eligibilityAnswers['isComplainantSelf'] = '';
        selfCtrl.setValidators([Validators.required]);
        selfCtrl.markAsTouched();
      } else {
        selfCtrl.clearValidators();
        selfCtrl.reset('');
        this.formData['isComplainantSelf'] = '';
        delete this.eligibilityAnswers['isComplainantSelf'];
      }
      selfCtrl.updateValueAndValidity();
    });

    form.get('employeeOfRE')!.valueChanges.subscribe(val => {
      const relCtrl = form.get('employerRelationship')!;
      if (val === 'yes') {
        relCtrl.setValidators([Validators.required]);
        relCtrl.markAsTouched();
      } else {
        relCtrl.clearValidators();
        relCtrl.reset('');
      }
      relCtrl.updateValueAndValidity();
    });
  }

  private watchConditionalValidators(form: any, trigger: string, activateOn: string, targets: { ctrl: string; validators: any[] }[]): void {
    form.get(trigger)!.valueChanges.subscribe((val: string) => {
      for (const t of targets) {
        const ctrl = form.get(t.ctrl)!;
        if (val === activateOn) {
          ctrl.setValidators(t.validators);
          ctrl.markAsTouched();
        } else {
          ctrl.clearValidators();
        }
        ctrl.updateValueAndValidity();
      }
    });
  }

  // ── Regulated Entity form watchers ──
  private setupRegulatedEntityFormWatchers(): void {
    const re = this.regulatedEntityForm;

    re.controls.isCreditCardComplaint.valueChanges.subscribe(value => {
      this.formData['isCreditCardComplaint'] = value;
      if (value === 'no') {
        re.controls.entityState.setValidators([Validators.required]);
        re.controls.entityDistrict.setValidators([Validators.required]);
        re.controls.entityBranch.setValidators([Validators.required]);
      } else {
        re.controls.entityState.clearValidators();
        re.controls.entityDistrict.clearValidators();
        re.controls.entityBranch.clearValidators();
        re.controls.entityState.setValue('');
        re.controls.entityDistrict.setValue('');
        re.controls.entityBranch.setValue('');
      }
      re.controls.entityState.updateValueAndValidity();
      re.controls.entityDistrict.updateValueAndValidity();
      re.controls.entityBranch.updateValueAndValidity();
    });

    re.controls.entityState.valueChanges.subscribe(v => { this.formData['entityState'] = v; });
    re.controls.entityDistrict.valueChanges.subscribe(v => { this.formData['entityDistrict'] = v; });
    re.controls.entityBranch.valueChanges.subscribe(v => { this.formData['entityBranch'] = v; });
  }

  // ── Complaint Details form watchers ──
  private setupComplaintDetailsFormWatchers(): void {
    const cd = this.complaintDetailsForm;

    const syncFields = ['complaintCategory', 'complaintText', 'isBusinessCorrespondent',
      'disputeAmount', 'compensationSought', 'reliefSought', 'walletName',
      'transactionRefNumber', 'savingsAccountNumber', 'loanAccountNumber',
      'atmDebitCardNumber', 'creditCardNumber'] as const;
    for (const field of syncFields) {
      (cd.controls as any)[field]?.valueChanges.subscribe((v: string) => { this.formData[field] = v; });
    }

    cd.controls.hasAccountWithRE.valueChanges.subscribe(value => {
      this.formData['hasAccountWithRE'] = value;
      if (value === 'yes') {
        cd.controls.accountTypeSelection.setValidators([Validators.required]);
        this.syncAccountTypeSelectionControl();
      } else {
        cd.controls.accountTypeSelection.clearValidators();
        cd.controls.accountTypeSelection.setValue('');
        this.clearAccountNumberFields();
      }
      cd.controls.accountTypeSelection.updateValueAndValidity();
    });

    cd.controls.isWalletComplaint.valueChanges.subscribe(value => {
      this.formData['isWalletComplaint'] = value;
      if (value === 'yes') {
        cd.controls.walletName.setValidators([Validators.required, Validators.maxLength(100)]);
        cd.controls.transactionRefNumber.setValidators([Validators.required, Validators.maxLength(150), CustomValidators.numericOnly()]);
      } else {
        cd.controls.walletName.clearValidators();
        cd.controls.walletName.setValue('');
        cd.controls.transactionRefNumber.clearValidators();
        cd.controls.transactionRefNumber.setValue('');
      }
      cd.controls.walletName.updateValueAndValidity();
      cd.controls.transactionRefNumber.updateValueAndValidity();
    });
  }

  syncAccountTypeSelectionControl(): void {
    const hasSelection = this.accountTypes.some(a => a.checked);
    this.complaintDetailsForm.controls.accountTypeSelection.setValue(hasSelection ? 'selected' : '');
  }

  private clearAccountNumberFields(): void {
    const cd = this.complaintDetailsForm;
    const fields = ['savingsAccountNumber', 'loanAccountNumber', 'atmDebitCardNumber', 'creditCardNumber'] as const;
    for (const f of fields) {
      const ctrl = cd.controls[f];
      ctrl.clearValidators();
      ctrl.setValue('');
      ctrl.updateValueAndValidity();
    }
    this.accountTypes.forEach(a => a.checked = false);
  }

  // ── Rep Authorization form watchers ──
  private setupRepAuthorizationFormWatchers(): void {
    const ra = this.repAuthorizationForm;

    ra.controls.hasAuthRep.valueChanges.subscribe(value => {
      this.formData['hasAuthRep'] = value;
      if (value === 'yes') {
        ra.controls.repName.setValidators([Validators.required, Validators.maxLength(150), CustomValidators.alphaNumeric()]);
        ra.controls.repPhone.setValidators([Validators.required, CustomValidators.numericOnly(), Validators.minLength(10), Validators.maxLength(10)]);
        ra.controls.repEmail.setValidators([Validators.email, Validators.maxLength(100)]);
        ra.controls.repPincode.setValidators([Validators.required, Validators.minLength(6), Validators.maxLength(6), CustomValidators.numericOnly()]);
        ra.controls.repState.setValidators([Validators.required]);
        ra.controls.repDistrict.setValidators([Validators.required]);
        ra.controls.repCity.setValidators([Validators.required]);
        ra.controls.repAddress.setValidators([Validators.required, Validators.maxLength(100)]);
        ra.controls.repFileUpload.setValidators([fileMetaRequired]);
      } else {
        this.clearRepFields();
      }
      const textFields = ['repName', 'repPhone', 'repEmail', 'repPincode', 'repState', 'repDistrict', 'repCity', 'repAddress'] as const;
      for (const f of textFields) { ra.controls[f].updateValueAndValidity(); }
      ra.controls.repFileUpload.updateValueAndValidity();
    });

    const repSyncFields = ['repName', 'repEmail', 'repPhone', 'repPincode', 'repState', 'repDistrict', 'repCity', 'repAddress'] as const;
    for (const f of repSyncFields) {
      ra.controls[f].valueChanges.subscribe((v: string) => { this.formData[f] = v; });
    }
  }

  private clearRepFields(): void {
    const ra = this.repAuthorizationForm;
    const textFields = ['repName', 'repPhone', 'repEmail', 'repPincode', 'repState', 'repDistrict', 'repCity', 'repAddress'] as const;
    for (const f of textFields) {
      ra.controls[f].clearValidators();
      ra.controls[f].setValue('');
      ra.controls[f].updateValueAndValidity();
    }
    ra.controls.repFileUpload.clearValidators();
    ra.controls.repFileUpload.setValue([]);
    ra.controls.repFileUpload.updateValueAndValidity();
    this.repStates = [];
    this.repDistricts = [];
    this.repCities = [];
    this.repFiles = [];
  }

  syncRepFileUploadControl(): void {
    const ctrl = this.repAuthorizationForm.controls.repFileUpload;
    const meta: FileMeta[] = this.repFiles.map(f => ({
      fileId: '',
      fileName: f.name,
      fileSize: f.size,
      viewUrl: '',
    }));
    ctrl.setValue(meta);
    ctrl.markAsTouched();
    ctrl.markAsDirty();
  }

  // ── Declaration form watchers ──
  private setupDeclarationFormWatchers(): void {
    this.declarationForm.controls.declaration1.valueChanges.subscribe(v => { this.declarationChecked = v; });
    this.declarationForm.controls.declaration2.valueChanges.subscribe(v => { this.declaration2Checked = v; });
  }

  // ── Complainant form watchers ──
  private setupComplainantFormWatchers(): void {
    const cd = this.complainantDetailsForm;

    const syncFields = ['complaintCategory', 'firstName', 'middleName', 'lastName',
      'age', 'gender', 'email', 'pincode', 'state', 'city', 'addressDetails'] as const;
    for (const field of syncFields) {
      (cd.controls as any)[field]?.valueChanges.subscribe((v: string) => { this.formData[field] = v; });
    }

    cd.controls.complaintCategory.valueChanges.subscribe(value => {
      this.formData['complainantCategory'] = value;
      this.applyComplainantCategoryValidators(value);
    });

    cd.controls.pincode.valueChanges.subscribe(value => {
      if (!value || value.length < 6) {
        cd.controls.state.setValue('');
        cd.controls.city.setValue('');
        this.complainantStates = [];
        this.complainantDistricts = [];
        return;
      }
      if (!/^\d{6}$/.test(value)) return;

      this.pincodeLoading = true;
      cd.controls.state.setValue('');
      cd.controls.city.setValue('');
      this.complainantStates = [];
      this.complainantDistricts = [];

      this.http.get<any[]>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading = false;
          if (res?.[0]?.Status === 'Success' && res[0].PostOffice?.length) {
            const po = res[0].PostOffice;
            this.complainantStates = [...new Set(po.map((p: any) => p.State).filter(Boolean))] as string[];
            this.complainantDistricts = [...new Set(po.map((p: any) => p.District).filter(Boolean))] as string[];
            cd.controls.state.setValue(this.complainantStates[0] || '');
            cd.controls.city.setValue(this.complainantDistricts[0] || '');
          } else {
            this.applyLocalPincodeReactive(value);
          }
        },
        error: () => { this.pincodeLoading = false; this.applyLocalPincodeReactive(value); }
      });
    });
  }

  private applyLocalPincodeReactive(value: string): void {
    const entry = lookupPincode(value);
    const cd = this.complainantDetailsForm;
    if (entry) {
      this.complainantStates = [entry.state];
      this.complainantDistricts = [entry.district];
      cd.controls.state.setValue(entry.state);
      cd.controls.city.setValue(entry.district);
    } else {
      cd.controls.state.setValue('');
      cd.controls.city.setValue('');
      cd.controls.pincode.setErrors({ invalidPincode: true });
    }
  }

  private applyComplainantCategoryValidators(category: string): void {
    const cd = this.complainantDetailsForm;
    const personalFields = ['firstName', 'middleName', 'lastName', 'age', 'gender', 'email'] as const;
    const isIndividual = category === 'individual' || category === 'senior_citizen' || category === 'pwd';

    if (isIndividual) {
      cd.controls.firstName.setValidators([Validators.required, Validators.minLength(2), Validators.maxLength(50), CustomValidators.alphaNumeric()]);
      cd.controls.middleName.setValidators([Validators.maxLength(50), CustomValidators.alphaNumeric()]);
      cd.controls.lastName.setValidators([Validators.required, Validators.maxLength(50), CustomValidators.alphaNumeric()]);
      cd.controls.age.clearValidators();
      cd.controls.age.setValidators([Validators.min(18), Validators.max(120), CustomValidators.numericOnly()]);
      cd.controls.gender.clearValidators();
      cd.controls.email.setValidators([Validators.required, Validators.email, Validators.maxLength(100)]);
    } else {
      for (const field of personalFields) {
        cd.controls[field].clearValidators();
        cd.controls[field].setValue('');
      }
    }
    for (const field of personalFields) {
      cd.controls[field].updateValueAndValidity();
    }
  }

  // ── Eligibility logic ──
  get selectedEntityType(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const bank = this.banks.find(b => String(b.id) === val);
    return bank?.department?.toUpperCase() || 'RBIO';
  }

  get isCEPCEntity(): boolean {
    return this.selectedEntityType === 'CEPC';
  }

  get visibleEligibilityQuestions(): EligibilityQuestion[] {
    return this.eligibilityQuestions.filter((q) => this.isQuestionVisible(q));
  }

  isQuestionVisible(q: EligibilityQuestion): boolean {
    if (q.key === 'regulatedEntity' || q.key === 'filedWithRE' || q.key === 'receivedReply' || q.key === 'sentReminder') return true;
    if (this.isCEPCEntity && ['isSubJudice', 'alreadySettled', 'pendingBeforeOmbudsman', 'settledByOmbudsman'].includes(q.key)) return false;
    if (q.key === 'previouslyFiledWithCEPC') return this.isCEPCEntity;
    if (q.key === 'employeeOfRE') return this.isCEPCEntity;
    if (q.key === 'employerRelationship') return false;
    if (q.key === 'staffOfRE') return this.selectedEntityType === 'RBIO';
    if (q.key === 'throughAdvocateEligibility') return this.selectedEntityType === 'RBIO';
    return true;
  }

  get currentQuestion(): EligibilityQuestion {
    const visible = this.visibleEligibilityQuestions;
    const idx = Math.min(this.eligibilityStep() - 1, visible.length - 1);
    return visible[idx];
  }

  get totalEligibilitySteps(): number {
    return this.visibleEligibilityQuestions.length;
  }

  get selectedEntityName(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const opt = this.eligibilityQuestions[0].options.find(o => o.value === val);
    return opt?.label ?? 'the Regulated Entity';
  }

  selectEligibilityAnswer(value: string): void {
    const q = this.currentQuestion;
    this.eligibilityAnswers[q.key] = value;
    const ctrl = this.eligibilityStageForm.get(q.key);
    if (ctrl) {
      ctrl.setValue(value as any);
      ctrl.markAsTouched();
    }
    const shouldBlock = q.blockOn && value === q.blockOn;
    const cepcBlock = this.isCEPCEntity && q.cepcBlockMessage && value === (q.blockOn || 'yes');
    if (shouldBlock || cepcBlock) {
      this.eligibilityBlocked.set(true);
      if (this.isCEPCEntity && q.cepcBlockMessage) {
        this.eligibilityBlockMessage.set(q.cepcBlockMessage.replace(/<RE Name>/g, this.selectedEntityName));
        this.closureLetterPara2.set((q.cepcClosureLetter || '').replace(/<RE Name>/g, this.selectedEntityName));
      } else {
        this.eligibilityBlockMessage.set(q.blockMessage.replace(/<RE Name>/g, this.selectedEntityName));
        this.closureLetterPara2.set((q.closureLetterPara2 || '').replace(/<RE Name>/g, this.selectedEntityName));
      }
      this.eligibilityBlockMessageKey.set('');
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
      this.eligibilityBlockMessageKey.set('');
      this.closureLetterPara2.set('');
    }

    if (q.key === 'receivedReply' && value === 'no') {
      this.checkThirtyDayBlock();
    }
  }

  private checkThirtyDayBlock(): void {
    const filedDate = this.formData['bankComplaintDate'];
    if (!filedDate) return;
    const daysSinceFiling = Math.floor((Date.now() - new Date(filedDate).getTime()) / (1000 * 60 * 60 * 24));
    if (daysSinceFiling <= 30) {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(
        'As the Regulated Entity has not yet been given 30 days to respond to your complaint, your complaint cannot be registered at this time. Please wait until 30 days have elapsed from the date of filing your complaint with the Regulated Entity.'
      );
      this.eligibilityBlockMessageKey.set('eligibility.block_less_than_30_days');
      this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
    }
  }

  nextEligibility(): boolean {
    if (this.eligibilityBlocked()) {
      const q = this.currentQuestion;
      if (q.nonMaintainable) {
        this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
        this.phase.set('non-maintainable');
      }
      return false;
    }
    const q = this.currentQuestion;
    if (!this.eligibilityAnswers[q.key]) return false;

    this.eligibilityFieldError = '';
    this.eligibilityFileError = '';
    this.eligibilityRefError = '';
    this.replyFileError = '';
    this.replyDateError = '';
    this.reminderFileError = '';
    this.reminderDateError = '';

    const up = this.uploading();
    if (up['eligibility_complaint'] || up['eligibility_reminder'] || up['eligibility_reply']) {
      return false;
    }

    if (q.key === 'filedWithRE' && this.eligibilityAnswers['filedWithRE'] === 'yes') {
      if (!this.formData['bankComplaintDate']) { this.eligibilityFieldError = 'Complaint date with RE is required'; return false; }
      if (!this.complaintFileMeta) { this.eligibilityFileError = 'Please upload a copy of the complaint sent to the Regulated Entity'; return false; }
    }
    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'yes') {
      if (!this.formData['replyDate']) { this.replyDateError = 'Date on which reply was received is required'; return false; }
      if (!this.replyFileMeta) { this.replyFileError = 'Please upload a copy of the reply received from the Regulated Entity'; return false; }
    }
    if (q.key === 'sentReminder' && this.eligibilityAnswers['sentReminder'] === 'yes') {
      if (!this.formData['reminderDate']) { this.reminderDateError = 'Date on which reminder was sent is required'; return false; }
      if (!this.reminderFileMeta) { this.reminderFileError = 'Please upload a copy of the reminder sent to the Regulated Entity'; return false; }
    }
    if (q.key === 'employeeOfRE' && this.eligibilityAnswers['employeeOfRE'] === 'yes') {
      if (!this.eligibilityAnswers['employerRelationship']) return false;
    }

    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'no') {
      const filedDate = this.formData['bankComplaintDate'];
      if (filedDate) {
        const daysSinceFiling = Math.floor((Date.now() - new Date(filedDate).getTime()) / (1000 * 60 * 60 * 24));
        if (daysSinceFiling <= 30) {
          this.eligibilityBlocked.set(true);
          this.eligibilityBlockMessage.set(
            'As the Regulated Entity has not yet been given 30 days to respond to your complaint, your complaint cannot be registered at this time. Please wait until 30 days have elapsed from the date of filing your complaint with the Regulated Entity.'
          );
          this.eligibilityBlockMessageKey.set('eligibility.block_less_than_30_days');
          this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
          this.phase.set('non-maintainable');
          return false;
        }
      }
    }

    if (this.eligibilityStep() < this.totalEligibilitySteps) {
      this.showSimplified.set(false);
      this.eligibilityStep.update(s => s + 1);
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    } else {
      this.syncFormToAnswers();
      this.phase.set('form');
      this.currentStep.set(1);
    }
    return true;
  }

  prevEligibility(): void {
    if (this.eligibilityStep() > 1) {
      this.showSimplified.set(false);
      this.eligibilityStep.update(s => s - 1);
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  private syncFormToAnswers(): void {
    const form = this.eligibilityStageForm;
    const keys = Object.keys(form.controls) as (keyof typeof form.controls)[];
    for (const key of keys) {
      const val = form.get(key)?.value;
      if (typeof val === 'string' && val) {
        this.eligibilityAnswers[key] = val;
      }
    }
    if (form.get('bankComplaintDate')!.value) this.formData['bankComplaintDate'] = form.get('bankComplaintDate')!.value;
    if (form.get('bankComplaintRef')!.value) this.formData['bankComplaintRef'] = form.get('bankComplaintRef')!.value;
  }

  private rebuildEligibilityAnswers(): void {
    this.eligibilityAnswers = {};
    const form = this.eligibilityStageForm;
    const keys = Object.keys(form.controls) as (keyof typeof form.controls)[];
    for (const key of keys) {
      const val = form.get(key)?.value;
      if (typeof val === 'string' && val) {
        this.eligibilityAnswers[key] = val;
      }
    }
    if (form.get('bankComplaintDate')!.value) this.formData['bankComplaintDate'] = form.get('bankComplaintDate')!.value;
    if (form.get('bankComplaintRef')!.value) this.formData['bankComplaintRef'] = form.get('bankComplaintRef')!.value;
  }

  // ── Entity search ──
  filterEntities(): void {
    const term = this.entitySearchText.toLowerCase().trim();
    const source = this.eligibilityQuestions[0].options;
    if (!term) {
      this.filteredEntityOptions = source.slice(0, 50).map(o => ({ ...o, entityType: this.banks.find(b => String(b.id) === o.value)?.entityType }));
      return;
    }
    this.filteredEntityOptions = source
      .filter(o => {
        const bank = this.banks.find(b => String(b.id) === o.value);
        return o.label.toLowerCase().includes(term) || bank?.entityType?.toLowerCase().includes(term);
      })
      .slice(0, 50)
      .map(o => ({ ...o, entityType: this.banks.find(b => String(b.id) === o.value)?.entityType }));
  }

  selectEntityFromSearch(opt: SelectOption): void {
    this.selectEligibilityAnswer(opt.value);
    this.entitySearchText = '';
    this.entityDropdownOpen = false;
    this.filteredEntityOptions = [];
  }

  clearEntitySelection(): void {
    this.eligibilityAnswers['regulatedEntity'] = '';
    this.entitySearchText = '';
    this.eligibilityBlocked.set(false);
    this.eligibilityStageForm.get('regulatedEntity')!.setValue('');
  }

  getSelectedEntityLabel(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    return this.eligibilityQuestions[0].options.find(o => o.value === val)?.label ?? '';
  }

  getSelectedBankName(): string {
    const bankId = this.eligibilityAnswers['regulatedEntity'];
    return this.banks.find(b => String(b.id) === bankId)?.name || '';
  }

  // ── Location lookups ──
  onEntityStateChange(): void {
    this.regulatedEntityForm.controls.entityDistrict.setValue('');
    this.regulatedEntityForm.controls.entityBranch.setValue('');
    this.districts = [];
    this.branches = [];
    const state = this.regulatedEntityForm.controls.entityState.value;
    if (state) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state } }).subscribe({
        next: (res) => { this.districts = res?.data ?? res ?? []; },
        error: () => {}
      });
    }
  }

  private readonly MOCK_BRANCHES = ['Main Branch', 'City Branch', 'Regional Office', 'Zonal Office', 'Service Branch', 'Extension Counter'];

  onEntityDistrictChange(): void {
    this.regulatedEntityForm.controls.entityBranch.setValue('');
    this.branches = [];
    const district = this.regulatedEntityForm.controls.entityDistrict.value;
    if (district) {
      // TODO: Replace mock data with API call once branch endpoint is ready
      // this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/branches`, { params: { district } }).subscribe({
      //   next: (res) => { this.branches = res?.data ?? res ?? []; },
      //   error: () => {}
      // });
      this.branches = [...this.MOCK_BRANCHES];
    }
  }

  onRepPincodeInput(): void {
    const ra = this.repAuthorizationForm;
    const value = ra.controls.repPincode.value;
    if (value && value.length === 6 && /^\d{6}$/.test(value)) {
      this.repPincodeLoading = true;
      ra.controls.repState.setValue('');
      ra.controls.repDistrict.setValue('');
      ra.controls.repCity.setValue('');
      this.repStates = [];
      this.repDistricts = [];
      this.repCities = [];

      this.http.get<any[]>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.repPincodeLoading = false;
          if (res?.[0]?.Status === 'Success' && res[0].PostOffice?.length) {
            const po = res[0].PostOffice;
            this.repStates = [...new Set(po.map((p: any) => p.State).filter(Boolean))] as string[];
            this.repDistricts = [...new Set(po.map((p: any) => p.District).filter(Boolean))] as string[];
            this.repCities = [...new Set(po.map((p: any) => p.Name).filter(Boolean))] as string[];
            ra.controls.repState.setValue(this.repStates[0] || '');
            ra.controls.repDistrict.setValue(this.repDistricts[0] || '');
            ra.controls.repCity.setValue(this.repCities[0] || '');
          }
        },
        error: () => { this.repPincodeLoading = false; }
      });
    } else {
      ra.controls.repState.setValue('');
      ra.controls.repDistrict.setValue('');
      ra.controls.repCity.setValue('');
      this.repStates = [];
      this.repDistricts = [];
      this.repCities = [];
    }
  }

  // ── Account types ──
  private readonly FIXED_ACCOUNT_TYPES: AccountType[] = [
    { label: 'Savings Account', value: 'savings', checked: false },
    { label: 'Loan Account', value: 'loan', checked: false },
    { label: 'ATM/Debit Card', value: 'atm_debit', checked: false },
    { label: 'Credit Card', value: 'credit_card', checked: false },
  ];

  selectAllAccounts = false;

  initFixedAccountTypes(): void {
    this.accountTypes = this.FIXED_ACCOUNT_TYPES.map(a => ({ ...a }));
  }

  toggleSelectAllAccountTypes(checked: boolean): void {
    this.selectAllAccounts = checked;
    for (const at of this.accountTypes) {
      at.checked = checked;
      this.applyAccountTypeValidators(at);
    }
    this.syncAccountTypeSelectionControl();
  }

  onAccountTypeToggle(accountType: AccountType): void {
    this.applyAccountTypeValidators(accountType);
    this.selectAllAccounts = this.accountTypes.every(a => a.checked);
    this.syncAccountTypeSelectionControl();
  }

  private applyAccountTypeValidators(accountType: AccountType): void {
    const ctrlName = ACCOUNT_TYPE_FIELD_MAP[accountType.value];
    if (!ctrlName) return;
    const ctrl = this.complaintDetailsForm.get(ctrlName);
    if (!ctrl) return;
    if (accountType.checked) {
      ctrl.setValidators([Validators.required, Validators.maxLength(100), CustomValidators.numericOnly()]);
    } else {
      ctrl.clearValidators();
      ctrl.setValue('');
    }
    ctrl.updateValueAndValidity();
  }

  // ── File handling ──
  uploadEligibilityFile(type: 'complaint' | 'reminder' | 'reply', file: File): void {
    const fileResult = validateFile(file);
    if (!fileResult.valid) {
      if (type === 'complaint') this.eligibilityFileError = fileResult.error!;
      else if (type === 'reminder') this.reminderFileError = fileResult.error!;
      else this.replyFileError = fileResult.error!;
      return;
    }

    const currentFileSize = type === 'complaint' ? (this.complaintFileWithRE?.size ?? 0)
      : type === 'reminder' ? (this.reminderFile?.size ?? 0) : (this.replyFile?.size ?? 0);
    const otherFilesSize = this.eligibilityBytesUsed - currentFileSize;
    const quotaResult = validateStepQuota([file], otherFilesSize, 'eligibility');
    if (!quotaResult.valid) {
      if (type === 'complaint') this.eligibilityFileError = quotaResult.error!;
      else if (type === 'reminder') this.reminderFileError = quotaResult.error!;
      else this.replyFileError = quotaResult.error!;
      return;
    }

    const key = `eligibility_${type}`;
    this.uploading.update(s => ({ ...s, [key]: true }));

    this.fileUploadService.uploadSingleFile(file, 'complaints').subscribe({
      next: (res) => {
        const meta: FileMeta = {
          fileId: res.data.storagePath,
          fileName: file.name,
          fileSize: file.size,
          viewUrl: this.fileUploadService.getDownloadUrl(res.data.storagePath),
        };
        switch (type) {
          case 'complaint':
            this.complaintFileWithRE = file;
            this.complaintFileWithREName = file.name;
            this.complaintFileMeta = meta;
            this.eligibilityFileError = '';
            this.eligibilityStageForm.get('complaintFileWithRE')!.setValue(meta);
            break;
          case 'reminder':
            this.reminderFile = file;
            this.reminderFileName = file.name;
            this.reminderFileMeta = meta;
            this.reminderFileError = '';
            this.eligibilityStageForm.get('reminderFileMeta')!.setValue(meta);
            break;
          case 'reply':
            this.replyFile = file;
            this.replyFileName = file.name;
            this.replyFileMeta = meta;
            this.replyFileError = '';
            this.eligibilityStageForm.get('replyFileMeta')!.setValue(meta);
            break;
        }
        this.uploading.update(s => ({ ...s, [key]: false }));
      },
      error: (err) => {
        const msg = err?.error?.message || err?.message || 'File upload failed. Please try again.';
        if (type === 'complaint') {
          this.complaintFileWithRE = null;
          this.complaintFileWithREName = '';
          this.eligibilityFileError = msg;
        } else if (type === 'reminder') {
          this.reminderFile = null;
          this.reminderFileName = '';
          this.reminderFileError = msg;
        } else {
          this.replyFile = null;
          this.replyFileName = '';
          this.replyFileError = msg;
        }
        this.uploadErrorSubject.next(msg);
        this.uploading.update(s => ({ ...s, [key]: false }));
      },
    });
  }

  removeEligibilityFile(type: 'complaint' | 'reminder' | 'reply'): void {
    let storagePath: string | undefined;
    switch (type) {
      case 'complaint':
        storagePath = this.complaintFileMeta?.fileId;
        this.complaintFileWithRE = null;
        this.complaintFileWithREName = '';
        this.complaintFileMeta = null;
        this.eligibilityStageForm.get('complaintFileWithRE')!.setValue(null);
        break;
      case 'reminder':
        storagePath = this.reminderFileMeta?.fileId;
        this.reminderFile = null;
        this.reminderFileName = '';
        this.reminderFileMeta = null;
        this.eligibilityStageForm.get('reminderFileMeta')!.setValue(null);
        break;
      case 'reply':
        storagePath = this.replyFileMeta?.fileId;
        this.replyFile = null;
        this.replyFileName = '';
        this.replyFileMeta = null;
        this.eligibilityStageForm.get('replyFileMeta')!.setValue(null);
        break;
    }
    if (storagePath) this.fileUploadService.deleteFile(storagePath).subscribe();
    this.saveDraftToServer();
  }

  syncFileUploadControl(): void {
    const ctrl = this.complaintDetailsForm.controls.fileUpload;
    const meta: FileMeta[] = this.attachments.map((f, i) => ({
      fileId: this.attachmentPreviews[i]?.fileId || '',
      fileName: f.name,
      fileSize: f.size,
      viewUrl: this.attachmentPreviews[i]?.viewUrl || '',
    }));
    ctrl.setValue(meta);
    ctrl.markAsTouched();
  }

  uploadAndSyncFiles(files: File[], controlPath: 'complaintDetails' | 'repAuth'): void {
    if (!files.length) return;
    const key = controlPath === 'complaintDetails' ? 'fileUpload' : 'repFileUpload';
    this.uploading.update(s => ({ ...s, [key]: true }));

    forkJoin(files.map(f => this.fileUploadService.uploadSingleFile(f, 'complaints'))).subscribe({
      next: (results) => {
        const newMetas: FileMeta[] = files.map((f, i) => ({
          fileId: results[i].data.storagePath,
          fileName: f.name,
          fileSize: f.size,
          viewUrl: this.fileUploadService.getDownloadUrl(results[i].data.storagePath),
        }));

        if (controlPath === 'complaintDetails') {
          this.attachments.push(...files);
          for (const m of newMetas) {
            this.attachmentPreviews.push({ name: m.fileName, url: m.viewUrl, type: '', size: m.fileSize, fileId: m.fileId, viewUrl: m.viewUrl });
          }
          const ctrl = this.complaintDetailsForm.controls.fileUpload;
          ctrl.setValue([...(ctrl.value || []), ...newMetas]);
          ctrl.markAsTouched();
          ctrl.markAsDirty();
        } else {
          this.repFiles.push(...files);
          const ctrl = this.repAuthorizationForm.controls.repFileUpload;
          ctrl.setValue([...(ctrl.value || []), ...newMetas]);
          ctrl.markAsTouched();
          ctrl.markAsDirty();
        }
        this.uploading.update(s => ({ ...s, [key]: false }));
      },
      error: (err) => {
        const msg = err?.error?.message || err?.message || 'File upload failed. Please try again.';
        this.uploadErrorSubject.next(msg);
        this.uploading.update(s => ({ ...s, [key]: false }));
      },
    });
  }

  removeUploadedFile(controlPath: 'complaintDetails' | 'repAuth', index: number): void {
    if (controlPath === 'complaintDetails') {
      const ctrl = this.complaintDetailsForm.controls.fileUpload;
      const meta = [...(ctrl.value || [])];
      const removed = meta.splice(index, 1)[0];
      ctrl.setValue(meta);
      ctrl.markAsDirty();
      this.attachments.splice(index, 1);
      if (this.attachmentPreviews[index]?.url) URL.revokeObjectURL(this.attachmentPreviews[index].url);
      this.attachmentPreviews.splice(index, 1);
      if (removed?.fileId) this.fileUploadService.deleteFile(removed.fileId).subscribe();
    } else {
      const ctrl = this.repAuthorizationForm.controls.repFileUpload;
      const meta = [...(ctrl.value || [])];
      const removed = meta.splice(index, 1)[0];
      ctrl.setValue(meta);
      ctrl.markAsDirty();
      this.repFiles.splice(index, 1);
      if (removed?.fileId) this.fileUploadService.deleteFile(removed.fileId).subscribe();
    }
    this.saveDraftToServer();
  }

  // ── Date handling ──
  onDateInput(field: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/[^0-9]/g, '');
    if (val.length > 8) val = val.substring(0, 8);
    let formatted = '';
    if (val.length > 4) formatted = val.substring(0, 2) + '/' + val.substring(2, 4) + '/' + val.substring(4);
    else if (val.length > 2) formatted = val.substring(0, 2) + '/' + val.substring(2);
    else formatted = val;
    this.dateDisplay[field] = formatted;
    input.value = formatted;

    if (val.length === 8) {
      const day = parseInt(val.substring(0, 2), 10);
      const month = parseInt(val.substring(2, 4), 10);
      const year = parseInt(val.substring(4, 8), 10);
      if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1900 && year <= 2100) {
        const iso = `${year}-${val.substring(2, 4)}-${val.substring(0, 2)}`;
        this.formData[field] = iso;
        this.eligibilityStageForm.get(field)?.setValue(iso);
        this.triggerDateValidation(field);
      } else {
        this.formData[field] = '';
        this.eligibilityStageForm.get(field)?.setValue('');
      }
    } else {
      this.formData[field] = '';
      this.eligibilityStageForm.get(field)?.setValue('');
    }
  }

  onDatePickerChange(field: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const iso = input.value;
    if (iso) {
      this.formData[field] = iso;
      this.dateDisplay[field] = this.isoToDisplay(iso);
      this.eligibilityStageForm.get(field)?.setValue(iso);
      this.triggerDateValidation(field);
    }
  }

  isoToDisplay(iso: string): string {
    if (!iso) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  initDateDisplays(): void {
    for (const field of ['bankComplaintDate', 'reminderDate', 'replyDate']) {
      if (this.formData[field]) this.dateDisplay[field] = this.isoToDisplay(this.formData[field]);
    }
  }

  private triggerDateValidation(field: string): void {
    if (field === 'bankComplaintDate') this.onBankComplaintDateChange();
    else if (field === 'reminderDate') this.onReminderDateChange();
    else if (field === 'replyDate') this.onReplyDateChange();
  }

  onBankComplaintDateChange(): void {
    this.eligibilityFieldError = '';
    const bankDate = this.formData['bankComplaintDate'];
    if (bankDate) {
      const selected = new Date(bankDate);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (selected > today) this.eligibilityFieldError = 'Date cannot be a future date';
    }
  }

  onReplyDateChange(): void {
    this.replyDateError = '';
    const replyDate = this.formData['replyDate'];
    const complaintDate = this.formData['bankComplaintDate'];
    if (replyDate && complaintDate && new Date(replyDate) < new Date(complaintDate)) {
      this.replyDateError = 'Reply date cannot be earlier than the complaint filing date';
    }
  }

  onReminderDateChange(): void {
    this.reminderDateError = '';
    const reminderDate = this.formData['reminderDate'];
    const complaintDate = this.formData['bankComplaintDate'];
    if (reminderDate && complaintDate && new Date(reminderDate) < new Date(complaintDate)) {
      this.reminderDateError = 'Reminder date cannot be earlier than the complaint filing date';
    }
  }

  // ── Sub-answers ──
  onEmployerRelationshipAnswer(value: string): void {
    this.eligibilityAnswers['employerRelationship'] = value;
    this.eligibilityStageForm.get('employerRelationship')!.setValue(value);
    const q = this.eligibilityQuestions.find(eq => eq.key === 'employerRelationship');
    if (value === 'yes' && q) {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(q.blockMessage.replace(/<RE Name>/g, this.selectedEntityName));
      this.eligibilityBlockMessageKey.set(q.blockMessageKey || '');
      this.closureLetterPara2.set((q.closureLetterPara2 || '').replace(/<RE Name>/g, this.selectedEntityName));
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
      this.closureLetterPara2.set('');
    }
  }

  onAdvocateSubAnswer(value: string): void {
    this.formData['isComplainantSelf'] = value;
    this.eligibilityAnswers['isComplainantSelf'] = value;
    const ctrl = this.eligibilityStageForm.get('isComplainantSelf')!;
    ctrl.setValue(value);
    ctrl.markAsTouched();
    if (value === 'no') {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(
        'As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.'
      );
      this.eligibilityBlockMessageKey.set('eligibility.block_advocate_not_complainant');
      this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  // ── Multi-step navigation ──
  nextStep(): boolean {
    if (!this.validateCurrentStep()) return false;
    if (this.currentStep() < 6) {
      this.currentStep.update(s => s + 1);
      if (this.currentStep() > this.highestStepReached()) {
        this.highestStepReached.set(this.currentStep());
      }
      this.manualSave$.next();
    }
    return true;
  }

  prevStep(): void {
    if (this.currentStep() > 1) {
      this.currentStep.update(s => s - 1);
      this.touchCurrentStepForm();
    }
  }

  goToStep(step: number): void {
    if (step <= this.highestStepReached()) {
      this.currentStep.set(step);
      this.touchCurrentStepForm();
    }
  }

  private touchCurrentStepForm(): void {
    const formMap: Record<number, any> = {
      1: this.complainantDetailsForm,
      2: this.regulatedEntityForm,
      3: this.complaintDetailsForm,
      4: this.repAuthorizationForm,
      5: this.declarationForm,
    };
    const form = formMap[this.currentStep()];
    if (form) form.markAllAsTouched();
  }

  goToEligibility(): void {
    this.phase.set('eligibility');
  }

  isNextDisabled(): boolean {
    const step = this.currentStep();
    if (step === 1) return this.complainantDetailsForm.invalid;
    if (step === 2) return this.regulatedEntityForm.invalid;
    if (step === 3) return this.complaintDetailsForm.invalid;
    if (step === 4) return this.repAuthorizationForm.invalid;
    if (step === 5) return this.declarationForm.invalid;
    return false;
  }

  validateCurrentStep(): boolean {
    this.validationErrors = {};
    const step = this.currentStep();
    const formMap: Record<number, any> = {
      1: this.complainantDetailsForm,
      2: this.regulatedEntityForm,
      3: this.complaintDetailsForm,
      4: this.repAuthorizationForm,
      5: this.declarationForm,
    };
    const form = formMap[step];
    if (form) {
      form.markAllAsTouched();
      Object.values(form.controls).forEach((c: any) => c.markAsDirty());
      if (form.invalid) return false;
    }
    return true;
  }

  // ── Eligibility step validity ──
  get isCurrentEligibilityStepValid(): boolean {
    if (this.eligibilityBlocked()) return false;
    const q = this.currentQuestion;
    if (!q) return false;
    if (q.type === 'select') return !!this.eligibilityStageForm.get('regulatedEntity')!.value;
    if (!this.eligibilityAnswers[q.key]) return false;

    if (q.key === 'filedWithRE' && this.eligibilityAnswers['filedWithRE'] === 'yes') {
      if (!this.formData['bankComplaintDate'] || !this.complaintFileMeta) return false;
    }
    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'yes') {
      if (!this.formData['replyDate'] || !this.replyFileMeta) return false;
    }
    if (q.key === 'sentReminder' && this.eligibilityAnswers['sentReminder'] === 'yes') {
      if (!this.formData['reminderDate'] || !this.reminderFileMeta) return false;
    }
    if (q.key === 'throughAdvocateEligibility' && this.eligibilityAnswers['throughAdvocateEligibility'] === 'yes') {
      if (!this.eligibilityStageForm.get('isComplainantSelf')!.value) return false;
    }
    if (q.key === 'employeeOfRE' && this.eligibilityAnswers['employeeOfRE'] === 'yes') {
      if (!this.eligibilityAnswers['employerRelationship']) return false;
    }
    return true;
  }

  // ── Submission ──
  submit(): void {
    if (this.declarationForm.invalid) return;
    if (!this.duplicateCheckDone) {
      this.checkDuplicate();
      return;
    }
    this.duplicateCheckDone = false;
    this.performSubmit();
  }

  private checkDuplicate(): void {
    const phone = this.formData['phone'];
    const email = this.formData['email'];
    const entityName = this.getSelectedBankName();
    const category = this.formData['complaintCategory'];
    const disputeDate = this.formData['disputeDate'];

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/check-duplicate`, {
      phone, email, entityName, category, disputeDate
    }).subscribe({
      next: (res) => {
        if (res?.duplicate) {
          this.duplicateMessage = res.matchedOn === 'email'
            ? 'Duplicate complaint detected based on email.'
            : 'Duplicate complaint detected based on mobile number.';
          this.showDuplicatePopup.set(true);
        } else {
          this.duplicateCheckDone = true;
          this.submit();
        }
      },
      error: () => { this.duplicateCheckDone = true; this.submit(); }
    });
  }

  dismissDuplicatePopup(): void {
    this.showDuplicatePopup.set(false);
    this.duplicateMessage = '';
  }

  proceedDespiteDuplicate(): void {
    this.showDuplicatePopup.set(false);
    this.duplicateCheckDone = true;
    this.submit();
  }

  private performSubmit(): void {
    this.submitting.set(true);
    const selectedEntityId = this.eligibilityAnswers['regulatedEntity'];
    const selectedBank = this.banks.find(b => String(b.id) === selectedEntityId);

    const payload: ComplaintPayload = {
      filingType: 'ONLINE',
      category: this.formData['complaintCategory'] || 'GENERAL',
      complainantName: [this.formData['firstName'], this.formData['middleName'], this.formData['lastName']].filter(Boolean).join(' '),
      complainantEmail: this.formData['email'],
      complainantPhone: this.formData['phone'],
      complainantAddress: this.formData['address'],
      complainantState: this.formData['state'] || undefined,
      complainantDistrict: this.formData['district'] || undefined,
      entityName: this.getSelectedBankName(),
      entityType: selectedBank?.entityType || 'BANK',
      regulatedEntityId: selectedEntityId ? parseInt(selectedEntityId, 10) : undefined,
      subject: this.formData['subCategory1'] || this.formData['complaintCategory'] || 'General Complaint',
      description: this.formData['complaintText'],
      amountInvolved: this.formData['disputeAmount'] ? parseFloat(this.formData['disputeAmount'].replace(/,/g, '')) : undefined,
      transactionDate: this.formData['disputeDate'] || undefined,
      priorReComplaint: this.eligibilityAnswers['filedWithRE'] === 'yes',
      reComplaintDate: this.formData['bankComplaintDate'] || undefined,
      reComplaintReference: this.formData['bankComplaintRef'] || undefined,
      reRepliedAndDissatisfied: this.eligibilityAnswers['receivedReply'] === 'yes',
    };

    this.complaintService.registerComplaint(payload).subscribe({
      next: (ack) => {
        this.referenceNumber = ack.complaintId;
        this.submittedStatus = ack.status || 'REGISTERED';
        this.submittedAt = ack.registeredAt || '';
        this.slaDueDate = ack.slaDueDate || '';
        this.submitting.set(false);
        this.phase.set('success');
        this.clearDraft();
        this.submitResultSubject.next({ success: true });
      },
      error: (err) => {
        this.submitting.set(false);
        const message = err?.error?.message || 'Failed to submit complaint. Please try again.';
        this.submitResultSubject.next({ success: false, message });
      }
    });
  }

  // ── Draft management ──
  private buildDraftPayload(): any {
    this.syncFormToAnswers();
    const phone = this.publicAuth.userIdentifier() || '';
    return {
      phone,
      entityName: this.getSelectedBankName(),
      formData: this.complaintStepperForm.getRawValue(),
      eligibilityFormData: this.eligibilityStageForm.getRawValue(),
      eligibilityAnswers: { ...this.eligibilityAnswers },
      currentStep: this.currentStep(),
      highestStepReached: this.highestStepReached(),
      eligibilityStep: this.eligibilityStep(),
      phase: this.phase(),
      checkedAccountTypes: this.accountTypes.filter(a => a.checked).map(a => a.value),
      dateDisplay: { ...this.dateDisplay },
    };
  }

  private draftPayloadSnapshot(): string {
    return JSON.stringify(this.buildDraftPayload());
  }

  saveDraft(): void {
    this.manualSave$.next();
  }

  saveDraftToServer(): void {
    const phone = this.publicAuth.userIdentifier();
    if (!phone) return;
    const payload = this.buildDraftPayload();
    this.complaintService.saveDraft(payload).subscribe({
      next: (res) => {
        if (res?.draftId) this.draftId.set(res.draftId);
        this.draftSaved.set(true);
        this.lastSavedAt.set(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }));
        setTimeout(() => this.draftSaved.set(false), 2000);
      },
      error: () => {}
    });
  }

  private startAutoSave(): void {
    const autoTick$ = interval(120_000).pipe(
      filter(() => this.phase() === 'form' || this.phase() === 'eligibility'),
      map(() => this.draftPayloadSnapshot()),
      distinctUntilChanged(),
    );

    merge(
      autoTick$,
      this.manualSave$.pipe(map(() => this.draftPayloadSnapshot())),
    ).pipe(
      switchMap(() => {
        const phone = this.publicAuth.userIdentifier();
        if (!phone) return of(null);
        return this.complaintService.saveDraft(this.buildDraftPayload()).pipe(catchError(() => of(null)));
      }),
      takeUntil(this.destroy$),
    ).subscribe(res => {
      if (res?.draftId) this.draftId.set(res.draftId);
      this.draftSaved.set(true);
      this.lastSavedAt.set(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }));
      setTimeout(() => this.draftSaved.set(false), 2000);
    });
  }

  loadDraftFromServer(draftId: string): void {
    forkJoin([
      this.complaintService.getDraft(draftId),
      this.entitiesLoaded$.pipe(take(1)),
    ]).subscribe({
      next: ([draft]) => { this.hydrateFromDraft(draft); },
      error: () => {}
    });
  }

  private hydrateFromDraft(draft: any): void {
    // 1. Restore signals / navigation state
    if (draft.phase === 'form' || draft.phase === 'eligibility') this.phase.set(draft.phase);
    if (draft.currentStep) this.currentStep.set(draft.currentStep);
    this.highestStepReached.set(draft.highestStepReached ?? draft.currentStep ?? 1);
    if (draft.draftId) this.draftId.set(draft.draftId);

    // 2. Patch the stepper form from the getRawValue() snapshot
    if (draft.formData) {
      this.complaintStepperForm.patchValue(draft.formData, { emitEvent: false });
    }

    // 3. Patch eligibility form from eligibilityFormData (single source of truth)
    if (draft.eligibilityFormData) {
      this.eligibilityStageForm.patchValue(draft.eligibilityFormData, { emitEvent: false });
    } else if (draft.eligibilityAnswers) {
      this.eligibilityAnswers = { ...draft.eligibilityAnswers };
      this.patchEligibilityFromFormData();
    }

    // 4. Rebuild eligibilityAnswers from the patched form (derive, not store)
    this.rebuildEligibilityAnswers();

    // 5. Restore eligibility step from saved state or derive from answers
    if (draft.eligibilityStep) {
      this.eligibilityStep.set(draft.eligibilityStep);
    } else if (this.eligibilityAnswers['regulatedEntity']) {
      this.eligibilityStep.set(Object.keys(this.eligibilityAnswers).length + 1);
    }

    // 6. Sync the flat formData dictionary from the patched reactive forms
    this.syncFormDataFromControls();

    // 7. Restore declaration from formData (single source of truth)
    const decl = draft.formData?.declaration;
    if (decl) {
      this.declarationChecked = !!decl.declaration1;
      this.declaration2Checked = !!decl.declaration2;
      this.declarationForm.controls.declaration1.setValue(this.declarationChecked, { emitEvent: false });
      this.declarationForm.controls.declaration2.setValue(this.declaration2Checked, { emitEvent: false });
    }
    // Backwards-compat: support old drafts that still have top-level declarations
    if (draft.declarationChecked !== undefined && !decl?.declaration1) {
      this.declarationChecked = draft.declarationChecked;
      this.declarationForm.controls.declaration1.setValue(this.declarationChecked, { emitEvent: false });
    }
    if (draft.declaration2Checked !== undefined && !decl?.declaration2) {
      this.declaration2Checked = draft.declaration2Checked;
      this.declarationForm.controls.declaration2.setValue(this.declaration2Checked, { emitEvent: false });
    }

    // 8. Restore file metadata from formData controls
    this.hydrateFileControls(draft);

    if (draft.dateDisplay) this.dateDisplay = { ...this.dateDisplay, ...draft.dateDisplay };
    this.initDateDisplays();

    // 9. Restore account types and location dropdown options
    this.restoreAccountTypes(draft.checkedAccountTypes);
    this.restoreLocationOptions();

    // 10. Re-fire conditional validators that were suppressed by emitEvent:false
    this.reapplyConditionalValidators();
  }

  private syncFormDataFromControls(): void {
    const raw = this.complaintStepperForm.getRawValue();
    const cd = raw.complainantDetails;
    const re = raw.regulatedEntity;
    const comp = raw.complaintDetails;
    const rep = raw.repAuthorization;

    Object.assign(this.formData, {
      complainantCategory: cd.complaintCategory, firstName: cd.firstName, middleName: cd.middleName,
      lastName: cd.lastName, age: cd.age, gender: cd.gender, email: cd.email, phone: cd.phone,
      pincode: cd.pincode, state: cd.state, city: cd.city, addressDetails: cd.addressDetails,
      isCreditCardComplaint: re.isCreditCardComplaint, entityState: re.entityState,
      entityDistrict: re.entityDistrict, entityBranch: re.entityBranch,
      complaintCategory: comp.complaintCategory, complaintText: comp.complaintText,
      hasAccountWithRE: comp.hasAccountWithRE, isWalletComplaint: comp.isWalletComplaint,
      walletName: comp.walletName, transactionRefNumber: comp.transactionRefNumber,
      isBusinessCorrespondent: comp.isBusinessCorrespondent,
      disputeAmount: comp.disputeAmount, compensationSought: comp.compensationSought,
      reliefSought: comp.reliefSought,
      savingsAccountNumber: comp.savingsAccountNumber, loanAccountNumber: comp.loanAccountNumber,
      atmDebitCardNumber: comp.atmDebitCardNumber, creditCardNumber: comp.creditCardNumber,
      hasAuthRep: rep.hasAuthRep, repName: rep.repName, repEmail: rep.repEmail,
      repPhone: rep.repPhone, repPincode: rep.repPincode, repState: rep.repState,
      repDistrict: rep.repDistrict, repCity: rep.repCity, repAddress: rep.repAddress,
    });

    const ef = this.eligibilityStageForm;
    const dateFields = ['bankComplaintDate', 'bankComplaintRef', 'reminderDate', 'replyDate'] as const;
    for (const f of dateFields) {
      const val = ef.get(f)?.value || this.eligibilityAnswers[f];
      if (val) this.formData[f] = val;
    }
  }

  private hydrateFileControls(draft: any): void {
    const fd = draft.formData;

    // Restore complaintDetails.fileUpload (FileMeta[])
    const fileUploadData = fd?.complaintDetails?.fileUpload;
    if (Array.isArray(fileUploadData) && fileUploadData.length) {
      const normalized = fileUploadData.map((m: FileMeta) => ({ ...m, viewUrl: this.normalizeViewUrl(m) }));
      this.complaintDetailsForm.controls.fileUpload.setValue(normalized);
      this.attachmentPreviews = normalized.map((m: FileMeta) => ({
        name: m.fileName, url: m.viewUrl, type: '', size: m.fileSize,
        fileId: m.fileId, viewUrl: m.viewUrl,
      }));
    } else if (draft.attachmentMeta?.length) {
      const migrated: FileMeta[] = draft.attachmentMeta.map((m: any) => ({
        fileId: '', fileName: m.name, fileSize: m.size, viewUrl: '',
      }));
      this.complaintDetailsForm.controls.fileUpload.setValue(migrated);
      this.attachmentPreviews = migrated.map(m => ({
        name: m.fileName, url: '', type: '', size: m.fileSize, fileId: '', viewUrl: '',
      }));
    }

    // Restore repAuthorization.repFileUpload (FileMeta[])
    const repFileData = fd?.repAuthorization?.repFileUpload;
    if (Array.isArray(repFileData) && repFileData.length) {
      const normalized = repFileData.map((m: FileMeta) => ({ ...m, viewUrl: this.normalizeViewUrl(m) }));
      this.repAuthorizationForm.controls.repFileUpload.setValue(normalized);
    }

    // Restore eligibility file metadata
    const ef = this.eligibilityStageForm;
    const complaintMeta = ef.get('complaintFileWithRE')?.value as FileMeta | null;
    if (complaintMeta?.fileId) {
      complaintMeta.viewUrl = this.normalizeViewUrl(complaintMeta);
      this.complaintFileMeta = complaintMeta;
      this.complaintFileWithREName = complaintMeta.fileName;
    }
    const replyMeta = ef.get('replyFileMeta')?.value as FileMeta | null;
    if (replyMeta?.fileId) {
      replyMeta.viewUrl = this.normalizeViewUrl(replyMeta);
      this.replyFileMeta = replyMeta;
      this.replyFileName = replyMeta.fileName;
    }
    const reminderMeta = ef.get('reminderFileMeta')?.value as FileMeta | null;
    if (reminderMeta?.fileId) {
      reminderMeta.viewUrl = this.normalizeViewUrl(reminderMeta);
      this.reminderFileMeta = reminderMeta;
      this.reminderFileName = reminderMeta.fileName;
    }
  }

  private normalizeViewUrl(meta: FileMeta): string {
    if (!meta.fileId) return '';
    if (meta.viewUrl?.includes('/view?path=')) return meta.viewUrl;
    return this.fileUploadService.getDownloadUrl(meta.fileId);
  }

  private restoreLocationOptions(): void {
    const raw = this.complaintStepperForm.getRawValue();
    const cd = raw.complainantDetails;
    const re = raw.regulatedEntity;
    const rep = raw.repAuthorization;

    if (cd.state) this.complainantStates = [cd.state];
    if (cd.city) this.complainantDistricts = [cd.city];
    if (re.entityState) this.states = [{ label: re.entityState, value: re.entityState }];
    if (re.entityDistrict) this.districts = [re.entityDistrict];
    if (re.entityBranch) this.branches = [re.entityBranch];
    if (rep.repState) this.repStates = [rep.repState];
    if (rep.repDistrict) this.repDistricts = [rep.repDistrict];
    if (rep.repCity) this.repCities = [rep.repCity];
  }

  private reapplyConditionalValidators(): void {
    const cd = this.complaintDetailsForm;
    const ra = this.repAuthorizationForm;
    const re = this.regulatedEntityForm;

    // hasAccountWithRE → accountTypeSelection + account number validators
    if (cd.controls.hasAccountWithRE.value === 'yes') {
      cd.controls.accountTypeSelection.setValidators([Validators.required]);
      this.syncAccountTypeSelectionControl();
    } else {
      cd.controls.accountTypeSelection.clearValidators();
    }
    cd.controls.accountTypeSelection.updateValueAndValidity();

    // isWalletComplaint → walletName + transactionRefNumber validators
    if (cd.controls.isWalletComplaint.value === 'yes') {
      cd.controls.walletName.setValidators([Validators.required, Validators.maxLength(100)]);
      cd.controls.transactionRefNumber.setValidators([Validators.required, Validators.maxLength(150), CustomValidators.numericOnly()]);
    } else {
      cd.controls.walletName.clearValidators();
      cd.controls.transactionRefNumber.clearValidators();
    }
    cd.controls.walletName.updateValueAndValidity();
    cd.controls.transactionRefNumber.updateValueAndValidity();

    // isCreditCardComplaint → entity state/district/branch validators
    if (re.controls.isCreditCardComplaint.value === 'no') {
      re.controls.entityState.setValidators([Validators.required]);
      re.controls.entityDistrict.setValidators([Validators.required]);
      re.controls.entityBranch.setValidators([Validators.required]);
    } else {
      re.controls.entityState.clearValidators();
      re.controls.entityDistrict.clearValidators();
      re.controls.entityBranch.clearValidators();
    }
    re.controls.entityState.updateValueAndValidity();
    re.controls.entityDistrict.updateValueAndValidity();
    re.controls.entityBranch.updateValueAndValidity();

    // hasAuthRep → rep field validators
    if (ra.controls.hasAuthRep.value === 'yes') {
      ra.controls.repName.setValidators([Validators.required, Validators.maxLength(150), CustomValidators.alphaNumeric()]);
      ra.controls.repPhone.setValidators([Validators.required, CustomValidators.numericOnly(), Validators.minLength(10), Validators.maxLength(10)]);
      ra.controls.repEmail.setValidators([Validators.email, Validators.maxLength(100)]);
      ra.controls.repPincode.setValidators([Validators.required, Validators.minLength(6), Validators.maxLength(6), CustomValidators.numericOnly()]);
      ra.controls.repState.setValidators([Validators.required]);
      ra.controls.repDistrict.setValidators([Validators.required]);
      ra.controls.repCity.setValidators([Validators.required]);
      ra.controls.repAddress.setValidators([Validators.required, Validators.maxLength(100)]);
      ra.controls.repFileUpload.setValidators([fileMetaRequired]);
      const repTextFields = ['repName', 'repPhone', 'repEmail', 'repPincode', 'repState', 'repDistrict', 'repCity', 'repAddress'] as const;
      for (const f of repTextFields) { ra.controls[f].updateValueAndValidity(); }
      ra.controls.repFileUpload.updateValueAndValidity();
    }

    // complainant category → personal field validators
    const complainantCategory = this.complainantDetailsForm.controls.complaintCategory.value;
    if (complainantCategory) {
      this.applyComplainantCategoryValidators(complainantCategory);
    }

    // Eligibility: filedWithRE → bankComplaintDate + complaintFileWithRE
    const ef = this.eligibilityStageForm;
    if (ef.get('filedWithRE')!.value === 'yes') {
      ef.get('bankComplaintDate')!.setValidators([Validators.required]);
      ef.get('complaintFileWithRE')!.setValidators([Validators.required]);
    } else {
      ef.get('bankComplaintDate')!.clearValidators();
      ef.get('complaintFileWithRE')!.clearValidators();
    }
    ef.get('bankComplaintDate')!.updateValueAndValidity();
    ef.get('complaintFileWithRE')!.updateValueAndValidity();

    // Eligibility: receivedReply → replyDate + replyFileMeta
    if (ef.get('receivedReply')!.value === 'yes') {
      ef.get('replyDate')!.setValidators([Validators.required]);
      ef.get('replyFileMeta')!.setValidators([Validators.required]);
    } else {
      ef.get('replyDate')!.clearValidators();
      ef.get('replyFileMeta')!.clearValidators();
    }
    ef.get('replyDate')!.updateValueAndValidity();
    ef.get('replyFileMeta')!.updateValueAndValidity();

    // Eligibility: sentReminder → reminderDate + reminderFileMeta
    if (ef.get('sentReminder')!.value === 'yes') {
      ef.get('reminderDate')!.setValidators([Validators.required]);
      ef.get('reminderFileMeta')!.setValidators([Validators.required]);
    } else {
      ef.get('reminderDate')!.clearValidators();
      ef.get('reminderFileMeta')!.clearValidators();
    }
    ef.get('reminderDate')!.updateValueAndValidity();
    ef.get('reminderFileMeta')!.updateValueAndValidity();

    // Eligibility: throughAdvocateEligibility → isComplainantSelf
    if (ef.get('throughAdvocateEligibility')!.value === 'yes') {
      ef.get('isComplainantSelf')!.setValidators([Validators.required]);
    } else {
      ef.get('isComplainantSelf')!.clearValidators();
    }
    ef.get('isComplainantSelf')!.updateValueAndValidity();

    // Eligibility: employeeOfRE → employerRelationship
    if (ef.get('employeeOfRE')!.value === 'yes') {
      ef.get('employerRelationship')!.setValidators([Validators.required]);
    } else {
      ef.get('employerRelationship')!.clearValidators();
    }
    ef.get('employerRelationship')!.updateValueAndValidity();
  }

  patchComplaintDetailsFromFormData(): void {
    const cd = this.complaintDetailsForm;
    cd.controls.complaintCategory.setValue(this.formData['complaintCategory'] || '', { emitEvent: true });
    cd.controls.complaintText.setValue(this.formData['complaintText'] || '', { emitEvent: false });
    cd.controls.hasAccountWithRE.setValue(this.formData['hasAccountWithRE'] || '', { emitEvent: true });
    cd.controls.isWalletComplaint.setValue(this.formData['isWalletComplaint'] || '', { emitEvent: true });
    cd.controls.isBusinessCorrespondent.setValue(this.formData['isBusinessCorrespondent'] || '', { emitEvent: true });
    cd.controls.disputeAmount.setValue((this.formData['disputeAmount'] || '').replace(/,/g, ''), { emitEvent: false });
    cd.controls.compensationSought.setValue((this.formData['compensationSought'] || '').replace(/,/g, ''), { emitEvent: false });
    cd.controls.reliefSought.setValue((this.formData['reliefSought'] || '').replace(/,/g, ''), { emitEvent: false });
    if (this.formData['walletName']) cd.controls.walletName.setValue(this.formData['walletName'], { emitEvent: false });
    if (this.formData['transactionRefNumber']) cd.controls.transactionRefNumber.setValue(this.formData['transactionRefNumber'], { emitEvent: false });
    if (this.formData['savingsAccountNumber']) cd.controls.savingsAccountNumber.setValue(this.formData['savingsAccountNumber'], { emitEvent: false });
    if (this.formData['loanAccountNumber']) cd.controls.loanAccountNumber.setValue(this.formData['loanAccountNumber'], { emitEvent: false });
    if (this.formData['atmDebitCardNumber']) cd.controls.atmDebitCardNumber.setValue(this.formData['atmDebitCardNumber'], { emitEvent: false });
    if (this.formData['creditCardNumber']) cd.controls.creditCardNumber.setValue(this.formData['creditCardNumber'], { emitEvent: false });
  }

  patchRepAuthorizationFromFormData(): void {
    const ra = this.repAuthorizationForm;
    ra.controls.hasAuthRep.setValue(this.formData['hasAuthRep'] || '', { emitEvent: true });
    if (this.formData['hasAuthRep'] === 'yes') {
      if (this.formData['repName']) ra.controls.repName.setValue(this.formData['repName'], { emitEvent: false });
      if (this.formData['repEmail']) ra.controls.repEmail.setValue(this.formData['repEmail'], { emitEvent: false });
      if (this.formData['repPhone']) ra.controls.repPhone.setValue(this.formData['repPhone'], { emitEvent: false });
      if (this.formData['repPincode']) ra.controls.repPincode.setValue(this.formData['repPincode'], { emitEvent: false });
      if (this.formData['repState']) {
        this.repStates = [this.formData['repState']];
        ra.controls.repState.setValue(this.formData['repState'], { emitEvent: false });
      }
      if (this.formData['repDistrict']) {
        this.repDistricts = [this.formData['repDistrict']];
        ra.controls.repDistrict.setValue(this.formData['repDistrict'], { emitEvent: false });
      }
      if (this.formData['repCity']) {
        this.repCities = [this.formData['repCity']];
        ra.controls.repCity.setValue(this.formData['repCity'], { emitEvent: false });
      }
      if (this.formData['repAddress']) ra.controls.repAddress.setValue(this.formData['repAddress'], { emitEvent: false });
    }
  }

  patchComplainantDetailsFromFormData(): void {
    const cd = this.complainantDetailsForm;
    if (this.formData['complainantCategory']) {
      cd.controls.complaintCategory.setValue(this.formData['complainantCategory'], { emitEvent: false });
      this.applyComplainantCategoryValidators(this.formData['complainantCategory']);
    }
    if (this.formData['firstName']) cd.controls.firstName.setValue(this.formData['firstName'], { emitEvent: false });
    if (this.formData['middleName']) cd.controls.middleName.setValue(this.formData['middleName'], { emitEvent: false });
    if (this.formData['lastName']) cd.controls.lastName.setValue(this.formData['lastName'], { emitEvent: false });
    if (this.formData['age']) cd.controls.age.setValue(this.formData['age'], { emitEvent: false });
    if (this.formData['gender']) cd.controls.gender.setValue(this.formData['gender'], { emitEvent: false });
    if (this.formData['email']) cd.controls.email.setValue(this.formData['email'], { emitEvent: false });
    if (this.formData['phone']) cd.controls.phone.setValue(this.formData['phone'], { emitEvent: false });
    if (this.formData['addressDetails']) cd.controls.addressDetails.setValue(this.formData['addressDetails'], { emitEvent: false });
    if (this.formData['pincode']) {
      cd.controls.pincode.setValue(this.formData['pincode'], { emitEvent: false });
    }
    if (this.formData['state']) {
      this.complainantStates = [this.formData['state']];
      cd.controls.state.setValue(this.formData['state'], { emitEvent: false });
    }
    if (this.formData['city']) {
      this.complainantDistricts = [this.formData['city']];
      cd.controls.city.setValue(this.formData['city'], { emitEvent: false });
    }
  }

  patchRegulatedEntityFromFormData(): void {
    const re = this.regulatedEntityForm;
    if (this.formData['isCreditCardComplaint']) {
      re.controls.isCreditCardComplaint.setValue(this.formData['isCreditCardComplaint'], { emitEvent: true });
    }
    if (this.formData['entityState']) {
      this.states = [{ label: this.formData['entityState'], value: this.formData['entityState'] }];
      re.controls.entityState.setValue(this.formData['entityState'], { emitEvent: false });
    }
    if (this.formData['entityDistrict']) {
      this.districts = [this.formData['entityDistrict']];
      re.controls.entityDistrict.setValue(this.formData['entityDistrict'], { emitEvent: false });
    }
    if (this.formData['entityBranch']) {
      this.branches = [this.formData['entityBranch']];
      re.controls.entityBranch.setValue(this.formData['entityBranch'], { emitEvent: false });
    }
  }

  patchEligibilityFromFormData(): void {
    const ef = this.eligibilityStageForm;
    for (const key of Object.keys(this.eligibilityAnswers)) {
      const ctrl = ef.get(key);
      if (ctrl && this.eligibilityAnswers[key]) {
        ctrl.setValue(this.eligibilityAnswers[key] as any, { emitEvent: false });
        ctrl.markAsTouched();
      }
    }
  }

  private reapplyEligibilityConditionalValidators(): void {
    const ef = this.eligibilityStageForm;

    if (ef.get('filedWithRE')!.value === 'yes') {
      ef.get('bankComplaintDate')!.setValidators([Validators.required]);
      ef.get('complaintFileWithRE')!.setValidators([Validators.required]);
    } else {
      ef.get('bankComplaintDate')!.clearValidators();
      ef.get('complaintFileWithRE')!.clearValidators();
    }
    ef.get('bankComplaintDate')!.updateValueAndValidity({ emitEvent: false });
    ef.get('complaintFileWithRE')!.updateValueAndValidity({ emitEvent: false });

    if (ef.get('receivedReply')!.value === 'yes') {
      ef.get('replyDate')!.setValidators([Validators.required]);
      ef.get('replyFileUploaded')!.setValidators([Validators.requiredTrue]);
    } else {
      ef.get('replyDate')!.clearValidators();
      ef.get('replyFileUploaded')!.clearValidators();
    }
    ef.get('replyDate')!.updateValueAndValidity({ emitEvent: false });
    ef.get('replyFileUploaded')!.updateValueAndValidity({ emitEvent: false });

    if (ef.get('sentReminder')!.value === 'yes') {
      ef.get('reminderDate')!.setValidators([Validators.required]);
      ef.get('reminderFileUploaded')!.setValidators([Validators.requiredTrue]);
    } else {
      ef.get('reminderDate')!.clearValidators();
      ef.get('reminderFileUploaded')!.clearValidators();
    }
    ef.get('reminderDate')!.updateValueAndValidity({ emitEvent: false });
    ef.get('reminderFileUploaded')!.updateValueAndValidity({ emitEvent: false });

    if (ef.get('throughAdvocateEligibility')!.value === 'yes') {
      ef.get('isComplainantSelf')!.setValidators([Validators.required]);
    } else {
      ef.get('isComplainantSelf')!.clearValidators();
    }
    ef.get('isComplainantSelf')!.updateValueAndValidity({ emitEvent: false });

    if (ef.get('employeeOfRE')!.value === 'yes') {
      ef.get('employerRelationship')!.setValidators([Validators.required]);
    } else {
      ef.get('employerRelationship')!.clearValidators();
    }
    ef.get('employerRelationship')!.updateValueAndValidity({ emitEvent: false });
  }

  private restoreAccountTypes(checkedValues?: string[]): void {
    if (!checkedValues?.length) return;
    for (const at of this.accountTypes) {
      at.checked = checkedValues.includes(at.value);
      if (at.checked) this.applyAccountTypeValidators(at);
    }
    this.selectAllAccounts = this.accountTypes.every(a => a.checked);
    this.syncAccountTypeSelectionControl();
  }

  clearDraft(): void {
    const id = this.draftId();
    if (id) {
      this.complaintService.deleteDraft(id).subscribe({ error: () => {} });
      this.draftId.set('');
    }
  }

  // ── Speech recognition ──
  get speechSupported(): boolean {
    return !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
  }

  toggleRecording(): void {
    if (this.isRecording()) this.stopRecording();
    else this.startRecording();
  }

  private startRecording(): void {
    const SRConstructor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SRConstructor) return;
    this.recognition = new SRConstructor();
    this.recognition.lang = 'en-IN';
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.onresult = (event: any) => {
      let transcript = '';
      for (let i = event.resultIndex; i < event.results.length; i++) { transcript += event.results[i][0].transcript; }
      const newText = ((this.complaintDetailsForm.controls.complaintText.value || '') + ' ' + transcript).trim();
      this.complaintDetailsForm.controls.complaintText.setValue(newText);
    };
    this.recognition.onerror = () => this.isRecording.set(false);
    this.recognition.onend = () => this.isRecording.set(false);
    this.recognition.start();
    this.isRecording.set(true);
  }

  stopRecording(): void {
    if (this.recognition) { this.recognition.stop(); this.recognition = null; }
    this.isRecording.set(false);
  }

  // ── Master data loading ──
  private loadMasterData(): void {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/categories`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        const categoryMap: Record<string, SelectOption[]> = {};
        const categorySet = new Map<string, string>();
        data.forEach((item: any) => {
          const catValue = item.categoryName || item.value;
          const catLabel = item.categoryLabel || item.label || catValue;
          if (!categorySet.has(catValue)) categorySet.set(catValue, catLabel);
          if (item.subCategory) {
            if (!categoryMap[catValue]) categoryMap[catValue] = [];
            categoryMap[catValue].push({ label: item.subCategory, value: item.subCategoryValue || item.subCategory });
          }
        });
        this.categories = Array.from(categorySet.entries()).map(([value, label]) => ({ label, value }));
        if (this.categories.length === 0) this.categories = [...DEFAULT_CATEGORIES];
        this.subCategories = categoryMap;
      },
      error: () => { this.categories = [...DEFAULT_CATEGORIES]; }
    });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        this.states = data.map((s: any) => typeof s === 'string' ? { label: s, value: s } : { label: s.name || s.label, value: s.value || s.code || s.name });
        this.complainantStatesList = this.states;
      },
      error: () => {}
    });

    this.initFixedAccountTypes();
  }

  private loadRegulatedEntities(): void {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`).subscribe({
      next: (res) => {
        const entities = res?.data ?? res ?? [];
        this.banks = entities.map((e: any) => ({ id: e.id, name: e.name, department: e.department || 'RBIO', entityType: e.entityType }));
        this.banks.sort((a, b) => a.name.localeCompare(b.name));
        this.eligibilityQuestions[0].options = this.banks.map(b => ({ label: b.name, value: String(b.id) }));
        const covered = this.banks.filter(b => b.department !== 'CEPC');
        const notCovered = this.banks.filter(b => b.department === 'CEPC');
        this.entitySelectOptions = covered.map(b => ({ label: b.name, value: String(b.id) }));
        this.nonCoveredEntityOptions = notCovered.map(b => ({ label: b.name, value: String(b.id) }));
        this.entitiesLoaded$.next();
      },
      error: () => {
        this.eligibilityQuestions[0].options = [];
        this.entitiesLoaded$.next();
      }
    });
  }

  // ── Utility ──
  formatIndianNumber(value: string): string {
    const num = value.replace(/^0+(?=\d)/, '');
    if (num.length <= 3) return num;
    let result = num.slice(-3);
    let remaining = num.slice(0, -3);
    while (remaining.length > 0) {
      result = remaining.slice(-2) + ',' + result;
      remaining = remaining.slice(0, -2);
    }
    return result;
  }

  amountInWords(value: string): string {
    const num = parseInt((value || '').replace(/,/g, ''), 10);
    if (!num || isNaN(num)) return '';
    return this.convertToWords(num) + ' rupees';
  }

  private convertToWords(n: number): string {
    if (n === 0) return 'zero';
    const ones = ['', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine',
      'ten', 'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen'];
    const tens = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety'];
    const convert = (num: number): string => {
      if (num === 0) return '';
      if (num < 20) return ones[num];
      if (num < 100) return tens[Math.floor(num / 10)] + (num % 10 ? '-' + ones[num % 10] : '');
      if (num < 1000) return ones[Math.floor(num / 100)] + ' hundred' + (num % 100 ? ' ' + convert(num % 100) : '');
      if (num < 100000) return convert(Math.floor(num / 1000)) + ' thousand' + (num % 1000 ? ' ' + convert(num % 1000) : '');
      if (num < 10000000) return convert(Math.floor(num / 100000)) + ' lakh' + (num % 100000 ? ' ' + convert(num % 100000) : '');
      return convert(Math.floor(num / 10000000)) + ' crore' + (num % 10000000 ? ' ' + convert(num % 10000000) : '');
    };
    const words = convert(n);
    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + 'b';
    return (bytes / 1024).toFixed(1) + 'kb';
  }

  formatDate(isoDate: string): string {
    if (!isoDate) return '—';
    const parts = isoDate.split('-');
    if (parts.length !== 3) return isoDate;
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
}
