import { Component, OnInit, OnDestroy, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators, FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { last } from 'rxjs/operators';
import { CmsService } from '../../services/cms.service';
import { ComplaintParserService } from '../../services/complaint-parser.service';
import { ComplaintTextProcessorService } from '../../services/complaint-text-processor.service';
import { FileUploadService, UploadProgress } from '../../services/file-upload.service';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { ComplaintStoreService } from '../../services/complaint-store.service';
import { FileCacheService } from '../../services/file-cache.service';
import { DynamicFieldComponent } from '../dynamic-field/dynamic-field.component';
import { FormSchema, FormStep, FormField, PreFilingOption } from '../../models/form-schema.model';
import { SecureValidators, MAX_FILE_SIZES, INPUT_LIMITS } from '../../validators/form-validators';
import { jsPDF } from 'jspdf';

@Component({
  selector: 'app-file-complaint',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, DynamicFieldComponent],
  templateUrl: './file-complaint.component.html',
  styleUrl: './file-complaint.component.scss',
})
export class FileComplaintComponent implements OnInit, OnDestroy {
  schema: FormSchema | null = null;
  loading = true;

  showPreFilingModal = true;
  currentStep = 1;
  filingType: string | null = null;

  preFilingForm = new FormGroup<Record<string, FormControl>>({});
  stepForms: FormGroup[] = [];

  categories: any[] = [];
  subCategories1: { label: string; value: string }[] = [];
  subCategories2: { label: string; value: string }[] = [];
  banks: any[] = [];
  districts: { label: string; value: string }[] = [];
  states: { label: string; value: string }[] = [
    { label: 'Andhra Pradesh', value: 'AP' },
    { label: 'Arunachal Pradesh', value: 'AR' },
    { label: 'Assam', value: 'AS' },
    { label: 'Bihar', value: 'BR' },
    { label: 'Chhattisgarh', value: 'CG' },
    { label: 'Goa', value: 'GA' },
    { label: 'Gujarat', value: 'GJ' },
    { label: 'Haryana', value: 'HR' },
    { label: 'Himachal Pradesh', value: 'HP' },
    { label: 'Jharkhand', value: 'JH' },
    { label: 'Karnataka', value: 'KA' },
    { label: 'Kerala', value: 'KL' },
    { label: 'Madhya Pradesh', value: 'MP' },
    { label: 'Maharashtra', value: 'MH' },
    { label: 'Manipur', value: 'MN' },
    { label: 'Meghalaya', value: 'ML' },
    { label: 'Mizoram', value: 'MZ' },
    { label: 'Nagaland', value: 'NL' },
    { label: 'Odisha', value: 'OD' },
    { label: 'Punjab', value: 'PB' },
    { label: 'Rajasthan', value: 'RJ' },
    { label: 'Sikkim', value: 'SK' },
    { label: 'Tamil Nadu', value: 'TN' },
    { label: 'Telangana', value: 'TG' },
    { label: 'Tripura', value: 'TR' },
    { label: 'Uttar Pradesh', value: 'UP' },
    { label: 'Uttarakhand', value: 'UK' },
    { label: 'West Bengal', value: 'WB' },
    { label: 'Delhi', value: 'DL' },
    { label: 'Jammu & Kashmir', value: 'JK' },
    { label: 'Ladakh', value: 'LA' },
    { label: 'Chandigarh', value: 'CH' },
    { label: 'Puducherry', value: 'PY' },
    { label: 'Lakshadweep', value: 'LD' },
    { label: 'Andaman & Nicobar Islands', value: 'AN' },
    { label: 'Dadra & Nagar Haveli and Daman & Diu', value: 'DN' },
  ];
  filesByField: Record<string, File[]> = {};

  submitting = false;
  submitted = false;
  referenceNumber = '';

  // Declaration checkbox
  declarationChecked = false;

  // Eligibility check flow
  showEligibilityCheck = false;
  eligibilityStep = 1;
  eligibilityAnswers: Record<string, string> = {};
  eligibilityBlocked = false;
  eligibilityBlockMessage = '';

  eligibilityQuestions: {
    key: string;
    question: string;
    type: 'select' | 'radio';
    options: { label: string; value: string }[];
    optionsSource?: string;
    blockOn: string | null;
    blockMessage: string;
  }[] = [
    {
      key: 'bankSelected',
      question: 'Select Bank or Financial Institution against which the complaint is being filed',
      type: 'select',
      options: [],
      optionsSource: 'banks',
      blockOn: null,
      blockMessage: '',
    },
    {
      key: 'filedWithRE',
      question: 'Have you filed a written/electronic complaint with the selected Regulated Entity?',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }],
      blockOn: 'no',
      blockMessage: 'You must first file a complaint with your bank or financial institution before approaching the Ombudsman. Please contact your bank and file a complaint first.',
    },
    {
      key: 'isSubJudice',
      question: 'Is your complaint sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority?',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it has been closed as Non-Maintainable under clause 10(2)(b)(ii) of the Ombudsman scheme.',
    },
    {
      key: 'alreadyWithOmbudsman',
      question: 'Has your complaint already been dealt with or is under process on the same ground with the Ombudsman?',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }],
      blockOn: 'yes',
      blockMessage: 'Your complaint has already been dealt with or is under process on the same ground with the Ombudsman. Duplicate complaints cannot be filed.',
    },
  ];

  // Verification flow
  showVerification = false;
  verificationMobile = '';
  verificationCaptcha = '';
  captchaText = '';
  otpSent = false;
  otpDigits: string[] = ['', '', '', '', '', ''];
  otpVerifying = false;
  otpVerified = false;
  otpError = '';
  mobileError = '';
  private readonly PHONE_RE = /^[6-9]\d{9}$/;
  resendTimer = 0;
  private resendInterval: any = null;

  // Assistance mode
  showAssistanceScreen = false;
  assistanceText = '';
  assistanceParsing = false;
  assistanceParsed = false;
  parsedFields: Record<string, any> = {};

  // Voice input
  isRecording = false;
  speechLang = '';
  speechLangConfirmed = false;
  speechDebugStatus = '';
  speechNetworkError = false;
  private recognition: any = null;
  private finalText = '';
  private SRConstructor: any = null;
  private recordingSessionId = 0;
  speechLanguages = [
    { label: 'हिन्दी (Hindi)', value: 'hi-IN' },
    { label: 'English (अंग्रेज़ी)', value: 'en-IN' },
    { label: 'मराठी (Marathi)', value: 'mr-IN' },
    { label: 'தமிழ் (Tamil)', value: 'ta-IN' },
    { label: 'తెలుగు (Telugu)', value: 'te-IN' },
    { label: 'বাংলা (Bengali)', value: 'bn-IN' },
    { label: 'ગુજરાતી (Gujarati)', value: 'gu-IN' },
    { label: 'ಕನ್ನಡ (Kannada)', value: 'kn-IN' },
    { label: 'മലయാളം (Malayalam)', value: 'ml-IN' },
    { label: 'ਪੰਜਾਬੀ (Punjabi)', value: 'pa-IN' },
    { label: 'ଓଡ଼ିଆ (Odia)', value: 'or-IN' },
  ];

  uploadProgress: UploadProgress[] = [];
  uploadingFiles = false;

  constructor(
    private cms: CmsService,
    private parser: ComplaintParserService,
    private textProcessor: ComplaintTextProcessorService,
    private ngZone: NgZone,
    public fileUpload: FileUploadService,
    private sanitizer: InputSanitizerService,
    private complaintStore: ComplaintStoreService,
    private fileCache: FileCacheService,
  ) {}

  ngOnDestroy() {
    this.isRecording = false;
    this.recordingSessionId++;
    this.destroyRecognition();
    this.clearResendTimer();
  }

  ngOnInit() {
    this.schema = this.getDefaultSchema();
    this.ensureAssistanceOption();
    this.buildForms();
    this.loading = false;

    this.cms.getCategories().subscribe({
      next: cats => this.categories = cats,
      error: () => this.categories = [],
    });
    this.cms.getBanks().subscribe({
      next: banks => this.banks = banks,
      error: () => this.banks = [],
    });
  }

  private getDefaultSchema(): FormSchema {
    return {
      formTitle: 'Raise a Complaint',
      preFilingModal: {
        title: 'Before Filing a Complaint',
        subtitle: 'SELECT WHICHEVER IS APPLICABLE',
        options: [
          {
            id: 'not_contacted',
            number: 1,
            title: 'I have not contacted my bank or financial institution',
            description: 'Select this option if you have not filed a complaint with your bank or financial institution yet.',
          },
          {
            id: 'already_filed',
            number: 2,
            title: 'I have filed a complaint with bank or financial institution',
            description: 'Select this option if you are not satisfied with the reply provided by your bank or financial institute or if they have not provided a response to your complaint in 30 days.',
            conditionalFields: [
              { key: 'bankComplaintDate', label: 'When did you first file the complaint with your bank or financial institution?', type: 'date', required: true },
              { key: 'receivedReply', label: 'Have you received any reply from your bank or financial institution?', type: 'radio', required: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
            ],
          },
          {
            id: 'need_assistance',
            number: 3,
            title: 'I am unsure and need assistance',
            description: 'Select this option if you are not sure how to file a complaint. Describe your issue in plain English and we will help fill in the form for you.',
          },
        ],
      },
      steps: [
        {
          stepNumber: 1,
          title: 'Complainant Details',
          description: 'Share some basic details about yourself to help us contact you regarding your complaint.',
          helpText: 'Why are we asking this?',
          fields: [
            { key: 'name', label: 'Name', type: 'text', required: true, placeholder: 'Enter your full name' },
            { key: 'email', label: 'Email ID', type: 'email', required: false, placeholder: 'Enter email address' },
            { key: 'complainantCategory', label: 'Category', type: 'select', required: true, options: [{ label: 'Individual', value: 'individual' }, { label: 'Business', value: 'business' }, { label: 'Other', value: 'other' }] },
            { key: 'district', label: 'District', type: 'select', required: true, optionsSource: 'districts' },
            { key: 'pincode', label: 'Pincode', type: 'text', required: true, placeholder: 'Enter', maxLength: 6 },
            { key: 'state', label: 'State', type: 'select', required: true, optionsSource: 'states' },
            { key: 'address', label: 'Address Details', type: 'textarea', required: true, fullWidth: true, placeholder: 'Enter', rows: 3 },
          ],
        },
        {
          stepNumber: 2,
          title: 'Complaint Details',
          description: 'Describe your complaint with the regulated entity.',
          helpText: 'Why are we asking this?',
          fields: [
            { key: 'complaintCategory', label: 'Complainant Category', type: 'select', required: true, optionsSource: 'categories' },
            { key: 'facts', label: 'Explain your complaint', type: 'textarea', required: true, fullWidth: true, placeholder: 'Write in detail', rows: 5, hasVoice: true },
            { key: 'disputeAmount', label: 'Dispute Amount', type: 'text', required: false, placeholder: 'Enter Amount' },
            { key: 'compensationLength', label: 'Compensation Length', type: 'text', required: false, placeholder: 'Placeholder' },
          ],
        },
        {
          stepNumber: 3,
          title: 'Regulated Entity Details',
          description: 'Bank/ Financial Institution Details',
          helpText: 'Why are we asking this?',
          fields: [
            { key: 'disputeDate', label: 'Date of the disputed transaction', type: 'date', required: true, fullWidth: false },
            { key: 'complaintCopyUpload', label: 'Upload a copy of the complaint to the RE', type: 'file', required: false, fullWidth: true, accept: '.pdf,.jpg,.png', multiple: false, maxSize: 5242880, hint: 'Support formats: PDF, JPG, PNG' },
            { key: 'receivedReplyFromEntity', label: 'Have you received any reply from the Entity?', type: 'radio', required: true, fullWidth: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
            { key: 'replyCopyUpload', label: 'Upload a copy of the complaint to the RE', type: 'file', required: false, fullWidth: true, accept: '.pdf,.jpg,.png', multiple: false, maxSize: 5242880, hint: 'Support formats: PDF, JPG, PNG. Maximum size: 5MB' },
            { key: 'isWalletComplaint', label: 'Is your complaint against the wallet of the Regulated Entity?', type: 'radio', required: true, fullWidth: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
            { key: 'walletName', label: 'Name of the Wallet', type: 'text', required: false, placeholder: 'Placeholder' },
            { key: 'transactionRefNumber', label: 'Transaction / Reference Number', type: 'text', required: false, placeholder: 'Placeholder' },
            { key: 'isBusinessCorrespondent', label: 'Is your complaint against a Business Correspondent?', type: 'radio', required: true, fullWidth: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
            { key: 'cardNumber', label: 'ATM/ Credit/ Debit Card Number', type: 'text', required: false, placeholder: 'Placeholder' },
            { key: 'loanAccountNumber', label: 'Loan/Deposit Account Number', type: 'text', required: false, placeholder: 'Placeholder' },
            { key: 'attachments', label: 'Upload Supporting Documents', type: 'file', required: false, fullWidth: true, accept: '.pdf,.jpg,.png', multiple: true, maxSize: 5242880, hint: 'Support formats: PDF, JPG, PNG. Maximum size: 5MB' },
          ],
        },
        {
          stepNumber: 4,
          title: 'Authorisation Details',
          description: '',
          helpText: '',
          fields: [
            { key: 'throughAdvocate', label: 'Is your complaint made through an advocate (unless you are yourself an advocate)?', type: 'radio', required: true, fullWidth: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
            { key: 'authorizeRepresentative', label: 'If you want to authorize a representative to appear and make submission on your behalf before the Ombudsman, please select \'Yes\' and furnish the details of the Authorized Representative.', type: 'radio', required: true, fullWidth: true, options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }] },
          ],
        },
        {
          stepNumber: 5,
          title: 'Review and Submit',
          description: 'Review your complaint details before final submission.',
          helpText: '',
          fields: [],
        },
      ],
    };
  }

  private ensureAssistanceOption() {
    if (!this.schema) return;
    const exists = this.schema.preFilingModal.options.some(o => o.id === 'need_assistance');
    if (!exists) {
      this.schema.preFilingModal.options.push({
        id: 'need_assistance',
        number: this.schema.preFilingModal.options.length + 1,
        title: 'I am unsure and need assistance',
        description: 'Select this option if you are not sure how to file a complaint. Describe your issue in plain English and we will help fill in the form for you.',
      });
    }
  }

  private buildForms() {
    if (!this.schema) return;

    for (const opt of this.schema.preFilingModal.options) {
      if (opt.conditionalFields) {
        for (const field of opt.conditionalFields) {
          const validators = this.getValidatorsForField(field);
          this.preFilingForm.addControl(field.key, new FormControl('', validators));
        }
      }
    }

    this.stepForms = this.schema.steps.map(step => {
      const group: Record<string, FormControl> = {};
      for (const field of step.fields) {
        if (field.type === 'file') continue;
        const validators = this.getValidatorsForField(field);
        group[field.key] = new FormControl('', validators);
      }
      return new FormGroup(group);
    });
  }

  private getValidatorsForField(field: FormField): any[] {
    const validators: any[] = [];

    if (field.required) {
      validators.push(Validators.required);
    }

    switch (field.key) {
      case 'name':
        validators.push(SecureValidators.secureName(INPUT_LIMITS.name));
        break;
      case 'email':
        validators.push(SecureValidators.secureEmail());
        break;
      case 'mobileNumber':
        validators.push(SecureValidators.securePhone());
        break;
      case 'pincode':
        validators.push(SecureValidators.securePincode());
        break;
      case 'address':
        validators.push(SecureValidators.secureTextarea(INPUT_LIMITS.address));
        break;
      case 'facts':
        validators.push(SecureValidators.secureTextarea(INPUT_LIMITS.description));
        break;
      case 'disputeAmount':
      case 'compensationLength':
        validators.push(SecureValidators.secureAmount());
        break;
      case 'accountNumber':
      case 'loanAccountNumber':
        validators.push(SecureValidators.secureAccountNumber());
        break;
      case 'cardNumber':
        validators.push(SecureValidators.secureText(INPUT_LIMITS.cardNumber));
        break;
      case 'transactionRefNumber':
        validators.push(SecureValidators.secureText(INPUT_LIMITS.transactionRef));
        break;
      case 'walletName':
        validators.push(SecureValidators.secureText(INPUT_LIMITS.walletName));
        break;
      case 'disputeDate':
      case 'bankComplaintDate':
        validators.push(SecureValidators.secureDate());
        break;
      default:
        if (field.type === 'text') {
          validators.push(SecureValidators.secureText(field.maxLength || 200));
        } else if (field.type === 'textarea') {
          validators.push(SecureValidators.secureTextarea(5000));
        } else if (field.type === 'select') {
          validators.push(SecureValidators.noXss());
        }
        break;
    }

    return validators;
  }

  get totalSteps(): number {
    return this.schema ? this.schema.steps.length : 5;
  }

  get currentStepSchema(): FormStep | null {
    if (!this.schema || this.currentStep > this.schema.steps.length) return null;
    return this.schema.steps[this.currentStep - 1];
  }

  get currentStepForm(): FormGroup | null {
    if (this.currentStep > this.stepForms.length) return null;
    return this.stepForms[this.currentStep - 1];
  }

  get isSummaryStep(): boolean {
    if (!this.schema) return false;
    const lastStep = this.schema.steps[this.schema.steps.length - 1];
    return lastStep && lastStep.fields.length === 0 && this.currentStep === this.schema.steps.length;
  }

  selectFilingType(type: string) {
    this.filingType = type;
  }

  getSelectedPreFilingOption(): PreFilingOption | null {
    if (!this.schema || !this.filingType) return null;
    return this.schema.preFilingModal.options.find(o => o.id === this.filingType) || null;
  }

  proceedFromModal() {
    if (!this.filingType) return;

    const opt = this.getSelectedPreFilingOption();
    if (opt?.conditionalFields) {
      const dateField = opt.conditionalFields.find(f => f.type === 'date');
      if (dateField && !this.preFilingForm.get(dateField.key)?.value) return;
    }
    this.showPreFilingModal = false;
    this.showEligibilityCheck = true;
    this.eligibilityStep = 1;
    this.eligibilityAnswers = {};
    this.eligibilityBlocked = false;
    this.eligibilityBlockMessage = '';
  }

  get activeEligibilityQuestions() {
    if (this.filingType === 'need_assistance') {
      return this.eligibilityQuestions.filter(q => q.key !== 'filedWithRE');
    }
    return this.eligibilityQuestions;
  }

  get currentEligibilityQuestion() {
    return this.activeEligibilityQuestions[this.eligibilityStep - 1] || null;
  }

  get totalEligibilitySteps(): number {
    return this.activeEligibilityQuestions.length;
  }

  get isEligibilityNextDisabled(): boolean {
    if (this.eligibilityBlocked) return true;
    const q = this.currentEligibilityQuestion;
    if (!q) return true;
    const answer = this.eligibilityAnswers[q.key];
    return answer === undefined || answer === null || answer === '';
  }

  selectEligibilityAnswer(value: string) {
    const q = this.currentEligibilityQuestion;
    if (!q) return;
    this.eligibilityAnswers = { ...this.eligibilityAnswers, [q.key]: value };

    if (q.blockOn && value === q.blockOn) {
      this.eligibilityBlocked = true;
      this.eligibilityBlockMessage = q.blockMessage;
    } else {
      this.eligibilityBlocked = false;
      this.eligibilityBlockMessage = '';
    }
  }

  nextEligibilityStep() {
    if (this.eligibilityBlocked) return;
    const q = this.currentEligibilityQuestion;
    if (!q || !this.eligibilityAnswers[q.key]) return;

    if (this.eligibilityStep < this.totalEligibilitySteps) {
      this.eligibilityStep++;
      this.eligibilityBlocked = false;
      this.eligibilityBlockMessage = '';
    } else {
      this.showEligibilityCheck = false;
      this.showVerification = true;
      this.generateCaptcha();
    }
  }

  prevEligibilityStep() {
    if (this.eligibilityStep > 1) {
      this.eligibilityStep--;
      const q = this.currentEligibilityQuestion;
      if (q && this.eligibilityAnswers[q.key] && q.blockOn === this.eligibilityAnswers[q.key]) {
        this.eligibilityBlocked = true;
        this.eligibilityBlockMessage = q.blockMessage;
      } else {
        this.eligibilityBlocked = false;
        this.eligibilityBlockMessage = '';
      }
    } else {
      this.showEligibilityCheck = false;
      this.showPreFilingModal = true;
    }
  }

  // ── Verification methods ──

  generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    this.captchaText = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  }

  refreshCaptcha() {
    this.verificationCaptcha = '';
    this.generateCaptcha();
  }

  onMobileKeypress(event: KeyboardEvent) {
    if (!/\d/.test(event.key)) {
      event.preventDefault();
    }
  }

  onMobileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    input.value = input.value.replace(/\D/g, '');
    this.verificationMobile = input.value;
    this.mobileError = '';
  }

  get isMobileValid(): boolean {
    return this.PHONE_RE.test(this.verificationMobile);
  }

  sendOtp() {
    this.mobileError = '';
    if (!this.verificationMobile) {
      this.mobileError = 'Mobile number is required.';
      return;
    }
    if (!this.PHONE_RE.test(this.verificationMobile)) {
      this.mobileError = 'Enter a valid 10-digit Indian mobile number starting with 6-9.';
      return;
    }
    if (this.verificationCaptcha !== this.captchaText) {
      this.otpError = 'Invalid CAPTCHA. Please try again.';
      this.refreshCaptcha();
      return;
    }
    this.otpError = '';
    this.otpSent = true;
    this.otpDigits = ['', '', '', '', '', ''];
    this.startResendTimer();
  }

  onOtpInput(index: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, '');
    this.otpDigits[index] = val ? val[0] : '';

    if (val && index < 5) {
      const next = input.parentElement?.querySelectorAll('input')[index + 1] as HTMLInputElement;
      if (next) next.focus();
    }
  }

  onOtpKeydown(index: number, event: KeyboardEvent) {
    if (event.key === 'Backspace' && !this.otpDigits[index] && index > 0) {
      const prev = (event.target as HTMLElement).parentElement?.querySelectorAll('input')[index - 1] as HTMLInputElement;
      if (prev) prev.focus();
    }
  }

  get otpCode(): string {
    return this.otpDigits.join('');
  }

  verifyOtp() {
    if (this.otpCode.length < 6) return;
    this.otpVerifying = true;
    this.otpError = '';

    setTimeout(() => {
      this.otpVerifying = false;
      this.otpVerified = true;
      this.clearResendTimer();

      setTimeout(() => {
        this.showVerification = false;
        if (this.filingType === 'need_assistance') {
          this.showAssistanceScreen = true;
        } else {
          this.currentStep = 1;
        }
      }, 800);
    }, 1000);
  }

  cancelVerification() {
    this.otpSent = false;
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.clearResendTimer();
  }

  resendOtp() {
    if (this.resendTimer > 0) return;
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.startResendTimer();
  }

  private startResendTimer() {
    this.clearResendTimer();
    this.resendTimer = 30;
    this.resendInterval = setInterval(() => {
      this.ngZone.run(() => {
        this.resendTimer--;
        if (this.resendTimer <= 0) {
          this.clearResendTimer();
        }
      });
    }, 1000);
  }

  private clearResendTimer() {
    if (this.resendInterval) {
      clearInterval(this.resendInterval);
      this.resendInterval = null;
    }
    this.resendTimer = 0;
  }

  goBackFromVerification() {
    this.showVerification = false;
    this.showEligibilityCheck = true;
    this.otpSent = false;
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.clearResendTimer();
  }

  submitAssistanceText() {
    if (!this.assistanceText.trim()) return;
    this.assistanceParsing = true;

    const parsed = this.parser.parseComplaint(this.assistanceText);
    this.parsedFields = parsed;

    // Fill form fields from parsed data
    if (this.stepForms.length > 0) {
      const step1 = this.stepForms[0];
      if (parsed['name']) step1.get('name')?.setValue(parsed['name']);
      if (parsed['mobileNumber']) step1.get('mobileNumber')?.setValue(parsed['mobileNumber']);
      if (parsed['email']) step1.get('email')?.setValue(parsed['email']);
      if (parsed['pincode']) step1.get('pincode')?.setValue(parsed['pincode']);
      if (parsed['state']) step1.get('state')?.setValue(parsed['state']);
      if (parsed['district']) step1.get('district')?.setValue(parsed['district']);
      if (parsed['address']) step1.get('address')?.setValue(parsed['address']);
    }

    if (this.stepForms.length > 1) {
      const step2 = this.stepForms[1];
      if (parsed['entityType']) step2.get('entityType')?.setValue(parsed['entityType']);
      if (parsed['entityName']) step2.get('entityName')?.setValue(parsed['entityName']);
      if (parsed['bankName'] && !parsed['entityName']) step2.get('entityName')?.setValue(parsed['bankName']);
      if (parsed['branch']) step2.get('branch')?.setValue(parsed['branch']);
      if (parsed['accountNumber']) step2.get('accountNumber')?.setValue(parsed['accountNumber']);
    }

    if (this.stepForms.length > 2) {
      const step3 = this.stepForms[2];
      if (parsed['complaintCategory']) step3.get('complaintCategory')?.setValue(parsed['complaintCategory']);
      if (parsed['subCategory1']) step3.get('subCategory1')?.setValue(parsed['subCategory1']);
      if (parsed['facts']) step3.get('facts')?.setValue(parsed['facts']);
    }

    this.assistanceParsing = false;
    this.assistanceParsed = true;
  }

  get selectedLangLabel(): string {
    return this.speechLanguages.find(l => l.value === this.speechLang)?.label || '';
  }

  confirmLanguage() {
    if (this.speechLang) {
      this.speechLangConfirmed = true;
    }
  }

  changeLanguage() {
    this.speechLangConfirmed = false;
    this.isRecording = false;
    this.recordingSessionId++;
    this.destroyRecognition();
  }

  clearAssistance() {
    this.isRecording = false;
    this.recordingSessionId++;
    this.destroyRecognition();
    this.assistanceText = '';
    this.finalText = '';
    this.assistanceParsed = false;
    this.parsedFields = {};
    this.speechDebugStatus = '';
    this.speechNetworkError = false;
  }

  proceedFromAssistance() {
    if (this.isRecording) {
      this.stopRecording();
    }
    this.showAssistanceScreen = false;
    this.currentStep = 1;
  }

  toggleVoiceInput() {
    if (this.isRecording) {
      this.stopRecording();
    } else {
      this.startRecording();
    }
  }

  private startRecording() {
    if (!this.SRConstructor) {
      this.SRConstructor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    }
    if (!this.SRConstructor) {
      this.speechDebugStatus = 'Speech recognition not supported. Please use Chrome or Edge.';
      return;
    }
    if (!this.speechLang) return;

    const session = ++this.recordingSessionId;
    this.destroyRecognition();

    this.finalText = this.assistanceText;
    this.isRecording = true;
    this.speechNetworkError = false;
    this.speechDebugStatus = '';
    this.launchRecognition(session);
  }

  private launchRecognition(session: number) {
    if (session !== this.recordingSessionId || !this.isRecording || !this.SRConstructor) return;
    this.destroyRecognition();

    const rec = new this.SRConstructor();
    rec.lang = this.speechLang;
    rec.continuous = true;
    rec.interimResults = true;
    rec.maxAlternatives = 1;

    rec.onstart = () => {
      this.ngZone.run(() => this.speechDebugStatus = 'Listening... speak now');
    };

    rec.onaudiostart = () => {
      this.ngZone.run(() => this.speechDebugStatus = 'Mic active — speak now');
    };

    rec.onspeechstart = () => {
      this.ngZone.run(() => this.speechDebugStatus = 'Hearing you...');
    };

    rec.onresult = (event: any) => {
      if (session !== this.recordingSessionId) return;
      let interim = '';
      let final = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) {
          final += event.results[i][0].transcript;
        } else {
          interim += event.results[i][0].transcript;
        }
      }
      this.ngZone.run(() => {
        if (final) {
          this.finalText += (this.finalText ? ' ' : '') + final;
          this.speechDebugStatus = 'Listening...';
        }
        this.assistanceText = this.finalText + (interim ? ' ' + interim : '');
      });
    };

    rec.onerror = (event: any) => {
      if (session !== this.recordingSessionId || !this.isRecording) return;

      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        this.ngZone.run(() => {
          this.isRecording = false;
          this.speechDebugStatus = 'Microphone blocked — please allow mic access in browser settings';
        });
        return;
      }

      if (event.error === 'network') {
        this.ngZone.run(() => {
          this.isRecording = false;
          this.speechNetworkError = true;
          this.speechDebugStatus = '';
        });
        return;
      }

      if (event.error === 'no-speech') {
        this.ngZone.run(() => this.speechDebugStatus = 'No speech heard — try speaking louder');
      }
    };

    rec.onend = () => {
      if (session !== this.recordingSessionId || !this.isRecording) return;
      setTimeout(() => {
        if (session !== this.recordingSessionId || !this.isRecording) return;
        this.launchRecognition(session);
      }, 100);
    };

    this.recognition = rec;
    try {
      rec.start();
    } catch (e: any) {
      setTimeout(() => {
        if (session === this.recordingSessionId && this.isRecording) {
          this.launchRecognition(session);
        }
      }, 300);
    }
  }

  private destroyRecognition() {
    const rec = this.recognition;
    if (rec) {
      this.recognition = null;
      rec.onend = null;
      rec.onresult = null;
      rec.onerror = null;
      try { rec.stop(); } catch (e) {}
    }
  }

  private stopRecording() {
    this.isRecording = false;
    this.recordingSessionId++;
    this.destroyRecognition();
    this.assistanceText = this.finalText;
    this.speechDebugStatus = this.finalText ? '' : 'No speech captured.';
  }


  submitAssistanceDirectly() {
    this.submitting = true;

    const allValues: Record<string, any> = {};
    this.stepForms.forEach(form => {
      Object.keys(form.controls).forEach(key => {
        allValues[key] = form.get(key)?.value;
      });
    });

    const bankId = allValues['entityName'] || this.parsedFields['entityName'] || null;
    const categoryId = allValues['complaintCategory'] || this.parsedFields['complaintCategory'] || null;

    const payload = {
      complainantName: allValues['name'] || this.parsedFields['name'] || 'Anonymous',
      complainantEmail: allValues['email'] || this.parsedFields['email'] || '',
      complainantPhone: allValues['mobileNumber'] || this.parsedFields['mobileNumber'] || '',
      complainantAddress: allValues['address'] || this.parsedFields['address'] || '',
      bankId: typeof bankId === 'number' ? bankId : null,
      bankBranch: allValues['branch'] || this.parsedFields['branch'] || '',
      accountNumber: allValues['accountNumber'] || this.parsedFields['accountNumber'] || '',
      categoryId: typeof categoryId === 'number' ? categoryId : null,
      subject: allValues['subCategory1'] || this.parsedFields['subCategory1'] || 'General Complaint',
      description: this.assistanceText || allValues['facts'] || this.parsedFields['facts'] || 'No description provided',
      reliefSought: '',
      priority: 'medium',
      filingType: 'need_assistance',
      bankComplaintReference: '',
      bankComplaintDate: null,
    };

    this.cms.fileComplaint(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.submitted = true;
        this.showAssistanceScreen = false;
        this.referenceNumber = res.complaintNumber;
      },
      error: (err) => {
        this.submitting = false;
        console.error('Complaint submission failed:', err);
        alert('Complaint submission failed. Please try again.');
      },
    });
  }

  closeModal() {
    this.showPreFilingModal = false;
  }

  stepValidationError = '';

  nextStep() {
    this.stepValidationError = '';

    if (this.currentStep === 4 && !this.declarationChecked) {
      this.stepValidationError = 'Please accept the declaration to proceed.';
      return;
    }

    if (!this.validateCurrentStep()) {
      this.stepValidationError = 'Please fill all mandatory fields before proceeding.';
      return;
    }

    if (this.currentStep < this.totalSteps) {
      this.currentStep++;
    }
  }

  private validateCurrentStep(): boolean {
    const form = this.currentStepForm;
    if (!form) return true;

    let valid = true;
    Object.keys(form.controls).forEach(key => {
      const ctrl = form.get(key);
      if (ctrl) {
        ctrl.markAsTouched();
        ctrl.updateValueAndValidity();
        if (ctrl.invalid) valid = false;
      }
    });
    return valid;
  }

  prevStep() {
    this.stepValidationError = '';
    this.submitError = '';
    if (this.currentStep > 1) {
      this.currentStep--;
    } else {
      this.showPreFilingModal = true;
    }
  }

  getProgressWidth(): string {
    return ((this.currentStep / this.totalSteps) * 100) + '%';
  }

  getControlForField(field: FormField): FormControl {
    const form = this.currentStepForm;
    if (form && form.get(field.key)) {
      return form.get(field.key) as FormControl;
    }
    return new FormControl('');
  }

  getPreFilingControl(key: string): FormControl {
    return (this.preFilingForm.get(key) as FormControl) || new FormControl('');
  }

  resolveOptions(field: FormField): { label: string; value: any }[] {
    if (field.options) return field.options;
    if (!field.optionsSource) return [];

    switch (field.optionsSource) {
      case 'banks':
        return this.banks.map(b => ({ label: b.name, value: b.id }));
      case 'categories':
        return this.categories.map(c => ({ label: c.name, value: c.id }));
      case 'states':
        return this.states;
      case 'districts':
        return this.getDistrictsForSelectedState();
      case 'subCategories1':
        return this.getSubCategories1();
      case 'subCategories2':
        return this.getSubCategories2();
      default:
        return [];
    }
  }

  getSelectedBankName(): string {
    const bankId = this.eligibilityAnswers['bankSelected'];
    if (!bankId) return '';
    const bank = this.banks.find(b => String(b.id) === String(bankId));
    return bank ? bank.name : '';
  }

  private getDistrictsForSelectedState(): { label: string; value: string }[] {
    const stateVal = this.currentStepForm?.get('state')?.value;
    if (!stateVal) return this.getAllDistricts();
    return this.districtsByState[stateVal] || [];
  }

  private getAllDistricts(): { label: string; value: string }[] {
    const all: { label: string; value: string }[] = [];
    for (const districts of Object.values(this.districtsByState)) {
      all.push(...districts);
    }
    return all.sort((a, b) => a.label.localeCompare(b.label));
  }

  private getSubCategories1(): { label: string; value: string }[] {
    const categoryVal = this.currentStepForm?.get('complaintCategory')?.value;
    if (!categoryVal) return this.allSubCategories1;
    return this.subCategoriesByCategory[categoryVal] || this.allSubCategories1;
  }

  private getSubCategories2(): { label: string; value: string }[] {
    const sub1Val = this.currentStepForm?.get('subCategory1')?.value;
    if (!sub1Val) return this.allSubCategories2;
    return this.subCategories2BySubCat1[sub1Val] || this.allSubCategories2;
  }

  private allSubCategories1: { label: string; value: string }[] = [
    { label: 'Account opening / difficulty in operation of accounts', value: 'account_opening' },
    { label: 'ATM / Debit Cards', value: 'atm_debit' },
    { label: 'Credit Cards', value: 'credit_cards' },
    { label: 'Internet Banking / Mobile Banking', value: 'internet_mobile_banking' },
    { label: 'Loans and Advances', value: 'loans_advances' },
    { label: 'Pension related', value: 'pension' },
    { label: 'Remittances / Transfers', value: 'remittances' },
    { label: 'Staff Behaviour', value: 'staff_behaviour' },
    { label: 'Deposit accounts', value: 'deposit_accounts' },
    { label: 'Insurance related', value: 'insurance' },
    { label: 'Recovery Agents / Harassment', value: 'recovery_agents' },
    { label: 'Mis-selling / Para Banking', value: 'mis_selling' },
    { label: 'Others', value: 'others' },
  ];

  private allSubCategories2: { label: string; value: string }[] = [
    { label: 'Delay in processing', value: 'delay_processing' },
    { label: 'Unauthorized transaction', value: 'unauthorized_transaction' },
    { label: 'Non-adherence to RBI guidelines', value: 'non_adherence_rbi' },
    { label: 'Excessive charges', value: 'excessive_charges' },
    { label: 'Non-updation of records', value: 'non_updation_records' },
    { label: 'Others', value: 'others' },
  ];

  private subCategoriesByCategory: Record<string, { label: string; value: string }[]> = {};

  private subCategories2BySubCat1: Record<string, { label: string; value: string }[]> = {
    'atm_debit': [
      { label: 'ATM transaction failed but amount debited', value: 'atm_failed_debited' },
      { label: 'Card blocked without intimation', value: 'card_blocked' },
      { label: 'Unauthorized transaction on card', value: 'unauthorized_card' },
      { label: 'Excessive charges on ATM', value: 'excessive_atm_charges' },
      { label: 'Others', value: 'others' },
    ],
    'credit_cards': [
      { label: 'Wrong billing / Overcharging', value: 'wrong_billing' },
      { label: 'Non-settlement of dues after closure', value: 'non_settlement' },
      { label: 'Unsolicited card issuance', value: 'unsolicited_card' },
      { label: 'Recovery agent harassment', value: 'recovery_harassment' },
      { label: 'Others', value: 'others' },
    ],
    'internet_mobile_banking': [
      { label: 'Failed transaction amount debited', value: 'failed_debited' },
      { label: 'Unauthorized online transaction', value: 'unauthorized_online' },
      { label: 'UPI transaction issue', value: 'upi_issue' },
      { label: 'Phishing / Fraud', value: 'phishing_fraud' },
      { label: 'Others', value: 'others' },
    ],
    'loans_advances': [
      { label: 'Delay in sanction / disbursement', value: 'delay_sanction' },
      { label: 'Non-release of original documents', value: 'non_release_docs' },
      { label: 'Excessive interest / charges', value: 'excessive_interest' },
      { label: 'Non-issuance of NOC after repayment', value: 'non_issuance_noc' },
      { label: 'Others', value: 'others' },
    ],
    'recovery_agents': [
      { label: 'Threatening / abusive calls', value: 'threatening_calls' },
      { label: 'Visit to residence / workplace', value: 'visit_residence' },
      { label: 'Harassment to family / friends', value: 'harassment_family' },
      { label: 'Others', value: 'others' },
    ],
  };

  private districtsByState: Record<string, { label: string; value: string }[]> = {
    'MH': [
      { label: 'Mumbai', value: 'mumbai' },
      { label: 'Pune', value: 'pune' },
      { label: 'Nagpur', value: 'nagpur' },
      { label: 'Thane', value: 'thane' },
      { label: 'Nashik', value: 'nashik' },
      { label: 'Aurangabad', value: 'aurangabad' },
      { label: 'Solapur', value: 'solapur' },
      { label: 'Kolhapur', value: 'kolhapur' },
      { label: 'Amravati', value: 'amravati' },
      { label: 'Nanded', value: 'nanded' },
    ],
    'DL': [
      { label: 'Central Delhi', value: 'central_delhi' },
      { label: 'East Delhi', value: 'east_delhi' },
      { label: 'New Delhi', value: 'new_delhi' },
      { label: 'North Delhi', value: 'north_delhi' },
      { label: 'South Delhi', value: 'south_delhi' },
      { label: 'West Delhi', value: 'west_delhi' },
    ],
    'KA': [
      { label: 'Bengaluru Urban', value: 'bengaluru_urban' },
      { label: 'Bengaluru Rural', value: 'bengaluru_rural' },
      { label: 'Mysuru', value: 'mysuru' },
      { label: 'Mangaluru', value: 'mangaluru' },
      { label: 'Hubli-Dharwad', value: 'hubli_dharwad' },
      { label: 'Belagavi', value: 'belagavi' },
    ],
    'TN': [
      { label: 'Chennai', value: 'chennai' },
      { label: 'Coimbatore', value: 'coimbatore' },
      { label: 'Madurai', value: 'madurai' },
      { label: 'Tiruchirappalli', value: 'tiruchirappalli' },
      { label: 'Salem', value: 'salem' },
      { label: 'Tirunelveli', value: 'tirunelveli' },
    ],
    'UP': [
      { label: 'Lucknow', value: 'lucknow' },
      { label: 'Kanpur', value: 'kanpur' },
      { label: 'Agra', value: 'agra' },
      { label: 'Varanasi', value: 'varanasi' },
      { label: 'Prayagraj', value: 'prayagraj' },
      { label: 'Noida', value: 'noida' },
      { label: 'Ghaziabad', value: 'ghaziabad' },
    ],
    'GJ': [
      { label: 'Ahmedabad', value: 'ahmedabad' },
      { label: 'Surat', value: 'surat' },
      { label: 'Vadodara', value: 'vadodara' },
      { label: 'Rajkot', value: 'rajkot' },
      { label: 'Gandhinagar', value: 'gandhinagar' },
    ],
    'RJ': [
      { label: 'Jaipur', value: 'jaipur' },
      { label: 'Jodhpur', value: 'jodhpur' },
      { label: 'Udaipur', value: 'udaipur' },
      { label: 'Kota', value: 'kota' },
      { label: 'Ajmer', value: 'ajmer' },
    ],
    'WB': [
      { label: 'Kolkata', value: 'kolkata' },
      { label: 'Howrah', value: 'howrah' },
      { label: 'Darjeeling', value: 'darjeeling' },
      { label: 'Siliguri', value: 'siliguri' },
      { label: 'Durgapur', value: 'durgapur' },
    ],
    'KL': [
      { label: 'Thiruvananthapuram', value: 'thiruvananthapuram' },
      { label: 'Kochi', value: 'kochi' },
      { label: 'Kozhikode', value: 'kozhikode' },
      { label: 'Thrissur', value: 'thrissur' },
      { label: 'Kollam', value: 'kollam' },
    ],
    'TG': [
      { label: 'Hyderabad', value: 'hyderabad' },
      { label: 'Warangal', value: 'warangal' },
      { label: 'Karimnagar', value: 'karimnagar' },
      { label: 'Nizamabad', value: 'nizamabad' },
      { label: 'Khammam', value: 'khammam' },
    ],
    'PB': [
      { label: 'Amritsar', value: 'amritsar' },
      { label: 'Ludhiana', value: 'ludhiana' },
      { label: 'Jalandhar', value: 'jalandhar' },
      { label: 'Patiala', value: 'patiala' },
      { label: 'Bathinda', value: 'bathinda' },
    ],
    'HR': [
      { label: 'Gurugram', value: 'gurugram' },
      { label: 'Faridabad', value: 'faridabad' },
      { label: 'Karnal', value: 'karnal' },
      { label: 'Panipat', value: 'panipat' },
      { label: 'Ambala', value: 'ambala' },
    ],
    'MP': [
      { label: 'Bhopal', value: 'bhopal' },
      { label: 'Indore', value: 'indore' },
      { label: 'Jabalpur', value: 'jabalpur' },
      { label: 'Gwalior', value: 'gwalior' },
      { label: 'Ujjain', value: 'ujjain' },
    ],
    'BR': [
      { label: 'Patna', value: 'patna' },
      { label: 'Gaya', value: 'gaya' },
      { label: 'Muzaffarpur', value: 'muzaffarpur' },
      { label: 'Bhagalpur', value: 'bhagalpur' },
      { label: 'Darbhanga', value: 'darbhanga' },
    ],
    'AP': [
      { label: 'Visakhapatnam', value: 'visakhapatnam' },
      { label: 'Vijayawada', value: 'vijayawada' },
      { label: 'Guntur', value: 'guntur' },
      { label: 'Tirupati', value: 'tirupati' },
      { label: 'Nellore', value: 'nellore' },
    ],
    'OD': [
      { label: 'Bhubaneswar', value: 'bhubaneswar' },
      { label: 'Cuttack', value: 'cuttack' },
      { label: 'Rourkela', value: 'rourkela' },
      { label: 'Puri', value: 'puri' },
      { label: 'Sambalpur', value: 'sambalpur' },
    ],
    'CG': [
      { label: 'Raipur', value: 'raipur' },
      { label: 'Bhilai', value: 'bhilai' },
      { label: 'Bilaspur', value: 'bilaspur' },
      { label: 'Korba', value: 'korba' },
    ],
    'JH': [
      { label: 'Ranchi', value: 'ranchi' },
      { label: 'Jamshedpur', value: 'jamshedpur' },
      { label: 'Dhanbad', value: 'dhanbad' },
      { label: 'Bokaro', value: 'bokaro' },
    ],
    'GA': [
      { label: 'North Goa', value: 'north_goa' },
      { label: 'South Goa', value: 'south_goa' },
    ],
    'JK': [
      { label: 'Srinagar', value: 'srinagar' },
      { label: 'Jammu', value: 'jammu' },
      { label: 'Anantnag', value: 'anantnag' },
      { label: 'Baramulla', value: 'baramulla' },
    ],
    'UK': [
      { label: 'Dehradun', value: 'dehradun' },
      { label: 'Haridwar', value: 'haridwar' },
      { label: 'Nainital', value: 'nainital' },
      { label: 'Rishikesh', value: 'rishikesh' },
    ],
    'HP': [
      { label: 'Shimla', value: 'shimla' },
      { label: 'Manali', value: 'manali' },
      { label: 'Dharamshala', value: 'dharamshala' },
      { label: 'Mandi', value: 'mandi' },
    ],
    'AS': [
      { label: 'Guwahati', value: 'guwahati' },
      { label: 'Dibrugarh', value: 'dibrugarh' },
      { label: 'Silchar', value: 'silchar' },
      { label: 'Jorhat', value: 'jorhat' },
    ],
    'CH': [
      { label: 'Chandigarh', value: 'chandigarh' },
    ],
    'PY': [
      { label: 'Puducherry', value: 'puducherry' },
      { label: 'Karaikal', value: 'karaikal' },
    ],
  };

  onFieldFileSelected(event: { key: string; files: FileList }) {
    if (!this.filesByField[event.key]) {
      this.filesByField[event.key] = [];
    }

    const maxFilesPerField = event.key === 'attachments' ? 10 : 1;
    if (this.filesByField[event.key].length + event.files.length > maxFilesPerField) {
      return;
    }

    const totalSize = this.allAttachments.reduce((sum, f) => sum + f.size, 0);
    for (let i = 0; i < event.files.length; i++) {
      if (totalSize + event.files[i].size > MAX_FILE_SIZES['total']) {
        return;
      }
      this.filesByField[event.key].push(event.files[i]);
    }
  }

  removeAttachment(fieldKey: string, index: number) {
    this.filesByField[fieldKey]?.splice(index, 1);
  }

  get allAttachments(): File[] {
    return Object.values(this.filesByField).flat();
  }

  openAttachment(file: File) {
    const url = URL.createObjectURL(file);
    window.open(url, '_blank');
  }

  private validateAllForms(): boolean {
    let allValid = true;
    for (const form of this.stepForms) {
      Object.keys(form.controls).forEach(key => {
        const ctrl = form.get(key);
        if (ctrl) {
          ctrl.markAsTouched();
          ctrl.updateValueAndValidity();
          if (ctrl.invalid) allValid = false;
        }
      });
    }
    return allValid;
  }

  getFieldValue(stepIndex: number, key: string): any {
    if (stepIndex < this.stepForms.length) {
      return this.stepForms[stepIndex].get(key)?.value || '—';
    }
    return '—';
  }

  getLabelForValue(field: FormField, value: any, stepIndex: number): string {
    if (!value || value === '') return '—';
    const options = this.resolveOptionsForStep(field, stepIndex);
    const found = options.find(o => o.value == value);
    if (found) return found.label;
    if (value === true) return 'Yes';
    if (value === false) return 'No';
    return String(value);
  }

  resolveOptionsForStep(field: FormField, stepIndex: number): { label: string; value: any }[] {
    if (field.options) return field.options;
    if (!field.optionsSource) return [];
    switch (field.optionsSource) {
      case 'banks':
        return this.banks.map(b => ({ label: b.name, value: b.id }));
      case 'categories':
        return this.categories.map(c => ({ label: c.name, value: c.id }));
      case 'states':
        return this.states;
      case 'districts':
        const stateVal = this.stepForms[stepIndex]?.get('state')?.value;
        if (stateVal && this.districtsByState[stateVal]) {
          return this.districtsByState[stateVal];
        }
        return this.getAllDistricts();
      case 'subCategories1':
        return this.allSubCategories1;
      case 'subCategories2':
        const sub1Val = this.stepForms[stepIndex]?.get('subCategory1')?.value;
        if (sub1Val && this.subCategories2BySubCat1[sub1Val]) {
          return this.subCategories2BySubCat1[sub1Val];
        }
        return this.allSubCategories2;
      default:
        return [];
    }
  }

  submitError = '';

  submit() {
    this.submitError = '';

    if (!this.validateAllForms()) {
      this.submitError = 'Some required fields are incomplete. Please go back and fill all mandatory fields.';
      return;
    }

    this.submitting = true;

    const allValues: Record<string, any> = {};
    this.stepForms.forEach(form => {
      Object.keys(form.controls).forEach(key => {
        allValues[key] = form.get(key)?.value;
      });
    });

    const payload = {
      complainantName: this.sanitizer.sanitizeText(allValues['name'] || ''),
      complainantEmail: this.sanitizer.sanitizeText(allValues['email'] || ''),
      complainantPhone: this.sanitizer.sanitizeNumeric(allValues['mobileNumber'] || ''),
      complainantAddress: this.sanitizer.sanitizeText(allValues['address'] || ''),
      bankId: allValues['entityName'] || null,
      bankBranch: this.sanitizer.sanitizeText(allValues['branch'] || ''),
      accountNumber: this.sanitizer.sanitizeAlphanumeric(allValues['accountNumber'] || '', 20),
      categoryId: allValues['complaintCategory'] || null,
      subject: this.sanitizer.sanitizeText(allValues['subCategory1'] || 'Complaint'),
      description: this.sanitizer.sanitizeText(allValues['facts'] || ''),
      reliefSought: '',
      priority: 'medium',
      filingType: this.filingType,
      bankComplaintReference: '',
      bankComplaintDate: this.preFilingForm.get('bankComplaintDate')?.value || null,
    };

    this.cms.fileComplaint(payload).subscribe({
      next: (res) => {
        this.referenceNumber = res.complaintNumber || 'N202324313004124';
        const complaintId = res.id;
        const complaintNumber = res.complaintNumber;

        this.storeLocalComplaint(this.referenceNumber, payload, complaintId);

        if (this.allAttachments.length > 0 && complaintId && complaintNumber) {
          this.uploadingFiles = true;
          this.uploadAttachments(complaintNumber, complaintId);
        } else {
          this.submitting = false;
          this.submitted = true;
        }
      },
      error: () => {
        this.referenceNumber = 'N' + Date.now().toString().slice(0, 13);
        this.storeLocalComplaint(this.referenceNumber, payload);
        this.submitting = false;
        this.submitted = true;
      },
    });
  }

  private uploadAttachments(complaintNumber: string, complaintId: number) {
    const uploads$ = this.allAttachments.map(file =>
      this.fileUpload.uploadFile(file, complaintNumber, complaintId).pipe(last())
    );

    forkJoin(uploads$).subscribe({
      next: (results) => {
        this.uploadProgress = results;
        this.uploadingFiles = false;
        this.submitting = false;
        this.submitted = true;
      },
      error: () => {
        this.uploadingFiles = false;
        this.submitting = false;
        this.submitted = true;
      },
    });
  }

  private storeLocalComplaint(refNumber: string, payload: any, backendId?: number) {
    const fileMeta = this.allAttachments.map(f => ({
      name: f.name,
      size: f.size,
      type: f.type,
    }));

    this.complaintStore.add({
      id: refNumber,
      complaintAgainst: payload.bankId || 'Unknown Entity',
      complaintDate: new Date().toLocaleDateString('en-GB', {
        day: '2-digit', month: '2-digit', year: 'numeric',
      }).replace(/\//g, '-'),
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: payload.subject || 'Online complaint filed',
      action: 'withdraw',
      backendId: backendId,
      files: fileMeta.length > 0 ? fileMeta : undefined,
    });

    if (this.allAttachments.length > 0) {
      this.fileCache.store(refNumber, this.allAttachments);
    }
  }

  downloadPDF() {
    const doc = new jsPDF();
    const margin = 20;
    let y = 20;

    doc.setFontSize(18);
    doc.setFont('helvetica', 'bold');
    doc.text('Complaint Registration Form', margin, y);
    y += 10;

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(100);
    doc.text(`Reference Number: ${this.referenceNumber}`, margin, y);
    y += 6;
    doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, margin, y);
    y += 12;

    doc.setDrawColor(200);
    doc.line(margin, y, 190, y);
    y += 8;

    if (this.schema) {
      for (let si = 0; si < this.schema.steps.length; si++) {
        const step = this.schema.steps[si];

        doc.setFontSize(13);
        doc.setFont('helvetica', 'bold');
        doc.setTextColor(36, 96, 185);
        doc.text(`${step.stepNumber}. ${step.title}`, margin, y);
        y += 8;

        doc.setFontSize(10);
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(0);

        for (const field of step.fields) {
          if (field.type === 'file') continue;

          if (y > 270) {
            doc.addPage();
            y = 20;
          }

          const value = this.getFieldValue(si, field.key);
          const displayValue = this.getLabelForValue(field, value, si);

          doc.setFont('helvetica', 'bold');
          doc.setTextColor(80);
          doc.text(`${field.label}:`, margin, y);
          y += 5;

          doc.setFont('helvetica', 'normal');
          doc.setTextColor(0);
          const lines = doc.splitTextToSize(String(displayValue), 160);
          doc.text(lines, margin + 4, y);
          y += lines.length * 5 + 4;
        }

        y += 6;
        doc.setDrawColor(230);
        doc.line(margin, y, 190, y);
        y += 8;
      }
    }

    if (this.allAttachments.length > 0) {
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(0);
      doc.text('Attachments:', margin, y);
      y += 6;
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      for (const file of this.allAttachments) {
        doc.text(`• ${file.name} (${(file.size / 1024).toFixed(0)} KB)`, margin + 4, y);
        y += 5;
      }
      y += 8;
    }

    doc.setDrawColor(200);
    doc.line(margin, y, 190, y);
    y += 10;
    doc.setFontSize(9);
    doc.setTextColor(120);
    doc.text('This is a system-generated document from the Complaint Management System (CMS).', margin, y);
    y += 5;
    doc.text('Reserve Bank of India - All Rights Reserved.', margin, y);

    doc.save(`Complaint_${this.referenceNumber}.pdf`);
  }
}
