import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ComplaintService } from '../../../services/complaint.service';
import { validateFile, validateFileSet, MAX_FILE_COUNT } from '../../../utils/file-validator';
import { announceToScreenReader, setPageTitle } from '../../../utils/accessibility';

interface EligibilityQuestion {
  key: string;
  question: string;
  type: 'select' | 'radio';
  options: { label: string; value: string }[];
  blockOn: string | null;
  blockMessage: string;
  nonMaintainable?: boolean;
}

@Component({
  selector: 'app-public-file-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './file-complaint.component.html',
  styleUrl: './file-complaint.component.scss'
})
export class PublicFileComplaintComponent implements OnInit, OnDestroy {

  private complaintService = inject(ComplaintService);
  private router = inject(Router);
  private sessionTimer: any = null;
  private autoSaveTimer: any = null;

  // FR-G-007: Flow phases in correct order
  phase = signal<'login' | 'eligibility' | 'form' | 'success' | 'non-maintainable'>('login');

  // FR-G-002/003: Login
  loginMode = signal<'mobile' | 'email'>('mobile');
  loginMobile = '';
  loginEmail = '';
  loginPassword = '';
  captchaText = '';
  captchaInput = '';
  otpSent = signal(false);
  otpDigits = ['', '', '', '', '', ''];
  otpVerified = signal(false);
  loginError = '';
  otpError = '';
  resendTimer = 0;
  private resendInterval: any = null;

  // FR-G-004: Session (15 min)
  sessionActive = signal(false);
  sessionTimeLeft = 900;

  // Eligibility (FR-G-007 step 2)
  eligibilityStep = signal(1);
  eligibilityAnswers: Record<string, string> = {};
  eligibilityBlocked = signal(false);
  eligibilityBlockMessage = signal('');
  nonMaintainableCaseId = '';

  banks = [
    { id: 1, name: 'State Bank of India' },
    { id: 2, name: 'HDFC Bank' },
    { id: 3, name: 'ICICI Bank' },
    { id: 4, name: 'Punjab National Bank' },
    { id: 5, name: 'Bank of Baroda' },
    { id: 6, name: 'Axis Bank' },
    { id: 7, name: 'Kotak Mahindra Bank' },
    { id: 8, name: 'ABC Bank' },
    { id: 9, name: 'Yes Bank' },
    { id: 10, name: 'IndusInd Bank' },
  ];

  // FR-G-007 step 1: RE selection done in eligibility
  eligibilityQuestions: EligibilityQuestion[] = [
    {
      key: 'regulatedEntity',
      question: 'Select Bank or Financial Institution against which the complaint is being filed',
      type: 'select',
      options: [],
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
      nonMaintainable: true,
    },
    {
      key: 'isSubJudice',
      question: 'Is your complaint sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority?',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause 10(2)(b)(ii) of the Reserve Bank - Integrated Ombudsman Scheme, 2021. To download your complaint closure letter, please click Create Complaint Closure.',
      nonMaintainable: true,
    },
    {
      key: 'alreadyWithOmbudsman',
      question: 'Has your complaint already been dealt with or is under process on the same ground with the Ombudsman?',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes' }, { label: 'No', value: 'no' }],
      blockOn: 'yes',
      blockMessage: 'Your complaint has already been dealt with or is under process on the same ground with the Ombudsman. Duplicate complaints cannot be filed.',
      nonMaintainable: true,
    },
  ];

  // FR-G-007: Multi-step form (steps 3-7)
  // Step 1: Complainant Details, Step 2: Regulated Entity Details, Step 3: Complaint Details,
  // Step 4: Authorised Representative, Step 5: Declaration & Review, Step 6: Preview/Submit
  currentStep = signal(1);
  totalSteps = 6;
  stepTitles = [
    'Complainant Details',
    'Regulated Entity Details',
    'Complaint Details',
    'Authorised Representative',
    'Declaration',
    'Review and Submit'
  ];

  declarationChecked = false;
  submitting = signal(false);
  referenceNumber = '';

  // FR-G-008: Draft
  draftSaved = signal(false);

  // Form data
  formData: Record<string, any> = {
    // Complainant
    name: '',
    email: '',
    complainantCategory: 'individual',
    phone: '',
    state: '',
    district: '',
    pincode: '',
    address: '',
    // RE Details
    bankComplaintDate: '',
    bankComplaintRef: '',
    disputeDate: '',
    receivedReplyFromEntity: '',
    replyDate: '',
    isWalletComplaint: '',
    walletName: '',
    transactionRefNumber: '',
    isBusinessCorrespondent: '',
    cardNumber: '',
    loanAccountNumber: '',
    // Complaint Details
    complaintCategory: '',
    subCategory1: '',
    subCategory2: '',
    complaintText: '',
    disputeAmount: '',
    compensationSought: '',
    reliefSought: '',
    // Auth Rep
    throughAdvocate: '',
    authorizeRepresentative: '',
    repName: '',
    repPhone: '',
    repEmail: '',
    repAddress: '',
  };

  attachments: File[] = [];
  attachmentPreviews: { name: string; url: string; type: string }[] = [];

  categories = [
    { label: 'ATM / Debit Cards', value: 'ATM' },
    { label: 'UPI / Mobile Banking', value: 'UPI' },
    { label: 'NEFT / RTGS', value: 'NEFT_RTGS' },
    { label: 'Loans and Advances', value: 'LOAN' },
    { label: 'Credit Card', value: 'CREDIT_CARD' },
    { label: 'Deposit', value: 'DEPOSIT' },
    { label: 'Insurance', value: 'INSURANCE' },
    { label: 'General', value: 'GENERAL' },
  ];

  subCategories: Record<string, { label: string; value: string }[]> = {
    ATM: [
      { label: 'Card not dispensing cash but account debited', value: 'ATM_NO_CASH' },
      { label: 'Card cloning / skimming', value: 'ATM_CLONING' },
      { label: 'Unauthorized transaction', value: 'ATM_UNAUTHORIZED' },
      { label: 'ATM swallowed card', value: 'ATM_SWALLOWED' },
      { label: 'Wrong amount dispensed', value: 'ATM_WRONG_AMOUNT' },
      { label: 'Other ATM/Debit Card issue', value: 'ATM_OTHER' },
    ],
    UPI: [
      { label: 'Transaction failed but amount debited', value: 'UPI_FAILED_DEBITED' },
      { label: 'Unauthorized UPI transaction', value: 'UPI_UNAUTHORIZED' },
      { label: 'Refund not received', value: 'UPI_REFUND' },
      { label: 'UPI ID / VPA related issue', value: 'UPI_VPA' },
      { label: 'Mobile banking app not working', value: 'UPI_APP_ISSUE' },
      { label: 'Other UPI/Mobile Banking issue', value: 'UPI_OTHER' },
    ],
    NEFT_RTGS: [
      { label: 'Amount debited but not credited to beneficiary', value: 'NEFT_NOT_CREDITED' },
      { label: 'Delay in transfer', value: 'NEFT_DELAY' },
      { label: 'Wrong account credited', value: 'NEFT_WRONG_ACCOUNT' },
      { label: 'Refund not received for failed transfer', value: 'NEFT_REFUND' },
      { label: 'Other NEFT/RTGS issue', value: 'NEFT_OTHER' },
    ],
    LOAN: [
      { label: 'Excessive interest / hidden charges', value: 'LOAN_INTEREST' },
      { label: 'Harassment by recovery agents', value: 'LOAN_HARASSMENT' },
      { label: 'Non-release of original documents after repayment', value: 'LOAN_DOCUMENTS' },
      { label: 'Loan sanctioned without consent', value: 'LOAN_NO_CONSENT' },
      { label: 'Foreclosure / prepayment issues', value: 'LOAN_FORECLOSURE' },
      { label: 'Other Loan issue', value: 'LOAN_OTHER' },
    ],
    CREDIT_CARD: [
      { label: 'Unauthorized transaction', value: 'CC_UNAUTHORIZED' },
      { label: 'Excess charges / hidden fees', value: 'CC_CHARGES' },
      { label: 'Card issued without consent', value: 'CC_NO_CONSENT' },
      { label: 'Non-settlement of insurance claim', value: 'CC_INSURANCE' },
      { label: 'Billing dispute', value: 'CC_BILLING' },
      { label: 'Other Credit Card issue', value: 'CC_OTHER' },
    ],
    DEPOSIT: [
      { label: 'Non-payment of deposit / maturity amount', value: 'DEP_NON_PAYMENT' },
      { label: 'Premature closure issue', value: 'DEP_PREMATURE' },
      { label: 'Interest rate discrepancy', value: 'DEP_INTEREST' },
      { label: 'TDS related issue', value: 'DEP_TDS' },
      { label: 'Other Deposit issue', value: 'DEP_OTHER' },
    ],
    INSURANCE: [
      { label: 'Claim rejected / delayed', value: 'INS_CLAIM_REJECTED' },
      { label: 'Policy misselling', value: 'INS_MISSELLING' },
      { label: 'Premium refund not received', value: 'INS_REFUND' },
      { label: 'Other Insurance issue', value: 'INS_OTHER' },
    ],
    GENERAL: [
      { label: 'Account opening / closing issue', value: 'GEN_ACCOUNT' },
      { label: 'KYC related issue', value: 'GEN_KYC' },
      { label: 'Pension related issue', value: 'GEN_PENSION' },
      { label: 'Non-adherence to fair practices code', value: 'GEN_FAIR_PRACTICE' },
      { label: 'Remittance issue', value: 'GEN_REMITTANCE' },
      { label: 'Other', value: 'GEN_OTHER' },
    ],
  };

  get filteredSubCategories(): { label: string; value: string }[] {
    return this.subCategories[this.formData['complaintCategory']] || [];
  }

  onCategoryChange() {
    this.formData['subCategory1'] = '';
    this.formData['subCategory2'] = '';
  }

  states = [
    { label: 'Andhra Pradesh', value: 'AP' }, { label: 'Arunachal Pradesh', value: 'AR' },
    { label: 'Assam', value: 'AS' }, { label: 'Bihar', value: 'BR' },
    { label: 'Chhattisgarh', value: 'CG' }, { label: 'Delhi', value: 'DL' },
    { label: 'Goa', value: 'GA' }, { label: 'Gujarat', value: 'GJ' },
    { label: 'Haryana', value: 'HR' }, { label: 'Himachal Pradesh', value: 'HP' },
    { label: 'Jharkhand', value: 'JH' }, { label: 'Karnataka', value: 'KA' },
    { label: 'Kerala', value: 'KL' }, { label: 'Madhya Pradesh', value: 'MP' },
    { label: 'Maharashtra', value: 'MH' }, { label: 'Manipur', value: 'MN' },
    { label: 'Meghalaya', value: 'ML' }, { label: 'Mizoram', value: 'MZ' },
    { label: 'Nagaland', value: 'NL' }, { label: 'Odisha', value: 'OD' },
    { label: 'Punjab', value: 'PB' }, { label: 'Rajasthan', value: 'RJ' },
    { label: 'Sikkim', value: 'SK' }, { label: 'Tamil Nadu', value: 'TN' },
    { label: 'Telangana', value: 'TG' }, { label: 'Tripura', value: 'TR' },
    { label: 'Uttar Pradesh', value: 'UP' }, { label: 'Uttarakhand', value: 'UK' },
    { label: 'West Bengal', value: 'WB' }, { label: 'Jammu & Kashmir', value: 'JK' },
  ];

  // FR-G-013: Speech to text
  isRecording = signal(false);
  speechSupported = false;
  private recognition: any = null;

  ngOnInit() {
    setPageTitle('File a Complaint');
    this.eligibilityQuestions[0].options = this.banks.map(b => ({ label: b.name, value: String(b.id) }));
    this.generateCaptcha();
    this.speechSupported = !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
    this.loadDraft();
  }

  ngOnDestroy() {
    this.clearResendTimer();
    if (this.sessionTimer) clearInterval(this.sessionTimer);
    if (this.autoSaveTimer) clearInterval(this.autoSaveTimer);
    this.stopRecording();
  }

  // ══════ FR-G-002/003: LOGIN ══════
  switchLoginMode(mode: 'mobile' | 'email') {
    this.loginMode.set(mode);
    this.loginError = '';
  }

  generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    this.captchaText = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  }

  sendOtp() {
    this.loginError = '';
    if (this.loginMode() === 'mobile') {
      if (!/^[6-9]\d{9}$/.test(this.loginMobile)) {
        this.loginError = 'Enter a valid 10-digit Indian mobile number starting with 6-9.';
        return;
      }
    } else {
      if (!this.loginEmail || !this.loginEmail.includes('@')) {
        this.loginError = 'Enter a valid email address.';
        return;
      }
    }
    if (this.captchaInput !== this.captchaText) {
      this.loginError = 'Invalid CAPTCHA. Please try again.';
      this.generateCaptcha();
      this.captchaInput = '';
      return;
    }
    this.otpSent.set(true);
    this.otpDigits = ['', '', '', '', '', ''];
    this.startResendTimer();
  }

  loginWithPassword() {
    this.loginError = '';
    if (!this.loginEmail || !this.loginPassword) {
      this.loginError = 'Please enter email and password.';
      return;
    }
    if (this.captchaInput !== this.captchaText) {
      this.loginError = 'Invalid CAPTCHA. Please try again.';
      this.generateCaptcha();
      this.captchaInput = '';
      return;
    }
    this.startSession();
    this.phase.set('eligibility');
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

  verifyOtp() {
    const code = this.otpDigits.join('');
    if (code.length < 6) { this.otpError = 'Enter all 6 digits'; return; }
    this.otpError = '';
    this.otpVerified.set(true);
    this.clearResendTimer();
    this.formData['phone'] = this.loginMobile;
    this.startSession();
    setTimeout(() => this.phase.set('eligibility'), 600);
  }

  resendOtp() {
    if (this.resendTimer > 0) return;
    this.otpDigits = ['', '', '', '', '', ''];
    this.startResendTimer();
  }

  cancelVerification() {
    this.otpSent.set(false);
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.clearResendTimer();
  }

  private startResendTimer() {
    this.clearResendTimer();
    this.resendTimer = 30;
    this.resendInterval = setInterval(() => {
      this.resendTimer--;
      if (this.resendTimer <= 0) this.clearResendTimer();
    }, 1000);
  }

  private clearResendTimer() {
    if (this.resendInterval) { clearInterval(this.resendInterval); this.resendInterval = null; }
    this.resendTimer = 0;
  }

  // FR-G-004: Session Management
  private startSession() {
    this.sessionActive.set(true);
    this.sessionTimeLeft = 900;
    this.sessionTimer = setInterval(() => {
      this.sessionTimeLeft--;
      if (this.sessionTimeLeft <= 0) {
        this.sessionActive.set(false);
        clearInterval(this.sessionTimer);
        this.phase.set('login');
      }
    }, 1000);
    // Auto-save every 60 seconds
    this.autoSaveTimer = setInterval(() => this.saveDraft(), 60000);
  }

  get sessionMinutes(): string {
    const m = Math.floor(this.sessionTimeLeft / 60);
    const s = this.sessionTimeLeft % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  // ══════ ELIGIBILITY (FR-G-007 step 2) ══════
  get currentQuestion(): EligibilityQuestion {
    return this.eligibilityQuestions[this.eligibilityStep() - 1];
  }

  get totalEligibilitySteps(): number {
    return this.eligibilityQuestions.length;
  }

  selectEligibilityAnswer(value: string) {
    const q = this.currentQuestion;
    this.eligibilityAnswers[q.key] = value;
    if (q.blockOn && value === q.blockOn) {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(q.blockMessage);
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  nextEligibility() {
    if (this.eligibilityBlocked()) {
      // FR-G-010: Non-maintainable → generate case ID, show closure
      const q = this.currentQuestion;
      if (q.nonMaintainable) {
        this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
        this.phase.set('non-maintainable');
      }
      return;
    }
    const q = this.currentQuestion;
    if (!this.eligibilityAnswers[q.key]) return;

    if (this.eligibilityStep() < this.totalEligibilitySteps) {
      this.eligibilityStep.update(s => s + 1);
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    } else {
      this.phase.set('form');
      this.currentStep.set(1);
    }
  }

  prevEligibility() {
    if (this.eligibilityStep() > 1) {
      this.eligibilityStep.update(s => s - 1);
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  // FR-G-010: Download closure letter
  downloadClosureLetter() {
    const content = `
RESERVE BANK OF INDIA
Integrated Ombudsman Scheme, 2021

CLOSURE LETTER

Case ID: ${this.nonMaintainableCaseId}
Date: ${new Date().toLocaleDateString('en-IN')}

Dear Complainant,

Your complaint has been closed as Non-Maintainable under the provisions of the Reserve Bank - Integrated Ombudsman Scheme, 2021.

Reason: ${this.eligibilityBlockMessage()}

This is a system-generated letter.

Reserve Bank of India
Department of Consumer Education and Protection
    `.trim();

    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Closure_Letter_${this.nonMaintainableCaseId}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // FR-G-017: Form Validation
  validationErrors: Record<string, string> = {};

  validateCurrentStep(): boolean {
    this.validationErrors = {};
    const step = this.currentStep();

    if (step === 1) {
      if (!this.formData['name']?.trim()) this.validationErrors['name'] = 'Full name is required';
      if (!this.formData['state']) this.validationErrors['state'] = 'State is required';
      if (!this.formData['pincode'] || !/^\d{6}$/.test(this.formData['pincode'])) this.validationErrors['pincode'] = 'Valid 6-digit pincode is required';
      if (this.formData['email'] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData['email'])) this.validationErrors['email'] = 'Invalid email format';
    } else if (step === 3) {
      if (!this.formData['complaintCategory']) this.validationErrors['complaintCategory'] = 'Category is required';
      if (!this.formData['subCategory1']) this.validationErrors['subCategory1'] = 'Sub-category is required';
      if (!this.formData['complaintText']?.trim()) this.validationErrors['complaintText'] = 'Complaint description is required';
    } else if (step === 4) {
      if (this.formData['authorizeRepresentative'] === 'yes') {
        if (!this.formData['repName']?.trim()) this.validationErrors['repName'] = 'Representative name is required';
      }
    } else if (step === 5) {
      if (!this.declarationChecked) this.validationErrors['declaration'] = 'You must accept the declaration to proceed';
    }

    return Object.keys(this.validationErrors).length === 0;
  }

  // FR-G-009: Tooltips
  tooltips: Record<string, string> = {
    name: 'Enter your full legal name as it appears on official documents',
    email: 'Optional. Used for sending updates about your complaint',
    complainantCategory: 'Select Individual for personal complaints, Business for company-related issues',
    state: 'Select the state where you reside',
    pincode: 'Enter 6-digit postal code of your area',
    bankComplaintRef: 'Reference/acknowledgement number provided by the bank when you filed the complaint',
    disputeDate: 'Date when the disputed transaction or issue occurred',
    complaintCategory: 'Select the broad category that best describes your complaint',
    subCategory1: 'Select specific nature of your complaint within the chosen category',
    complaintText: 'Describe your complaint in detail including all relevant facts, dates, and amounts',
    disputeAmount: 'Total monetary amount involved in the dispute (in Indian Rupees)',
    compensationSought: 'Amount of compensation you are seeking for the loss/inconvenience',
    reliefSought: 'Describe what action or remedy you expect from the Ombudsman',
    repName: 'Full name of the person authorized to represent you',
  };

  // FR-G-016: Tab/keyboard navigation
  onStepKeydown(event: KeyboardEvent) {
    if (event.key === 'Tab' && !event.shiftKey) {
      const focusable = document.querySelectorAll('.step-content input:not([disabled]), .step-content select:not([disabled]), .step-content textarea:not([disabled])');
      const last = focusable[focusable.length - 1] as HTMLElement;
      if (document.activeElement === last) {
        event.preventDefault();
        if (this.currentStep() < this.totalSteps) this.nextStep();
      }
    }
  }

  // FR-G-019: Download acknowledgement letter
  downloadAcknowledgement() {
    import('jspdf').then(({ jsPDF }) => {
      const doc = new jsPDF();
      const pageWidth = doc.internal.pageSize.getWidth();
      let y = 20;

      // Header
      doc.setFontSize(16);
      doc.setFont('helvetica', 'bold');
      doc.text('RESERVE BANK OF INDIA', pageWidth / 2, y, { align: 'center' });
      y += 8;
      doc.setFontSize(11);
      doc.setFont('helvetica', 'normal');
      doc.text('Integrated Ombudsman Scheme, 2021', pageWidth / 2, y, { align: 'center' });
      y += 12;

      // Title
      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('ACKNOWLEDGEMENT OF COMPLAINT', pageWidth / 2, y, { align: 'center' });
      y += 4;
      doc.setLineWidth(0.5);
      doc.line(40, y, pageWidth - 40, y);
      y += 12;

      // Reference details
      doc.setFontSize(10);
      doc.setFont('helvetica', 'bold');
      doc.text('Complaint Reference Number:', 20, y);
      doc.setFont('helvetica', 'normal');
      doc.text(this.referenceNumber, 80, y);
      y += 7;
      doc.setFont('helvetica', 'bold');
      doc.text('Date of Filing:', 20, y);
      doc.setFont('helvetica', 'normal');
      doc.text(new Date().toLocaleDateString('en-IN'), 80, y);
      y += 14;

      // Body
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      const name = this.formData['name'] || 'Complainant';
      doc.text(`Dear ${name},`, 20, y);
      y += 8;

      const bodyText = `This is to acknowledge receipt of your complaint filed against ${this.getSelectedBankName()} under the Reserve Bank - Integrated Ombudsman Scheme, 2021.`;
      const bodyLines = doc.splitTextToSize(bodyText, pageWidth - 40);
      doc.text(bodyLines, 20, y);
      y += bodyLines.length * 5 + 10;

      // Complaint Details
      doc.setFont('helvetica', 'bold');
      doc.text('Complaint Details:', 20, y);
      y += 7;
      doc.setFont('helvetica', 'normal');
      doc.text(`Category: ${this.formData['complaintCategory'] || 'N/A'}`, 25, y);
      y += 6;
      doc.text(`Dispute Amount: Rs. ${this.formData['disputeAmount'] || 'N/A'}`, 25, y);
      y += 12;

      const trackText = `Your complaint has been registered and will be processed as per the provisions of the Scheme. You may track the status using your reference number: ${this.referenceNumber}`;
      const trackLines = doc.splitTextToSize(trackText, pageWidth - 40);
      doc.text(trackLines, 20, y);
      y += trackLines.length * 5 + 8;

      doc.setFont('helvetica', 'bold');
      doc.text('Expected Resolution Timeline:', 20, y);
      doc.setFont('helvetica', 'normal');
      doc.text(' Within 30 days from the date of receipt.', 72, y);
      y += 14;

      // Contact
      doc.setFont('helvetica', 'bold');
      doc.text('For any queries, please contact:', 20, y);
      y += 7;
      doc.setFont('helvetica', 'normal');
      doc.text('Toll-free: 14448', 25, y);
      y += 6;
      doc.text('Website: https://cms.rbi.org.in', 25, y);
      y += 14;

      // Footer
      doc.setFontSize(9);
      doc.setTextColor(100);
      doc.text('This is a system-generated acknowledgement.', 20, y);
      y += 10;
      doc.setTextColor(0);
      doc.setFontSize(10);
      doc.setFont('helvetica', 'bold');
      doc.text('Reserve Bank of India', 20, y);
      y += 5;
      doc.setFont('helvetica', 'normal');
      doc.text('Department of Consumer Education and Protection', 20, y);

      doc.save(`Acknowledgement_${this.referenceNumber}.pdf`);
    });
  }

  // ══════ MULTI-STEP FORM ══════
  nextStep() {
    if (!this.validateCurrentStep()) return;
    if (this.currentStep() < this.totalSteps) {
      this.currentStep.update(s => s + 1);
      this.saveDraft();
    }
  }

  prevStep() {
    if (this.currentStep() > 1) {
      this.currentStep.update(s => s - 1);
    }
  }

  goToStep(step: number) {
    if (step <= this.currentStep()) {
      this.currentStep.set(step);
    }
  }

  // FR-G-008: Save Draft
  saveDraft() {
    const draft = { formData: this.formData, eligibilityAnswers: this.eligibilityAnswers, currentStep: this.currentStep() };
    localStorage.setItem('cms_complaint_draft', JSON.stringify(draft));
    this.draftSaved.set(true);
    setTimeout(() => this.draftSaved.set(false), 2000);
  }

  loadDraft() {
    const saved = localStorage.getItem('cms_complaint_draft');
    if (saved) {
      try {
        const draft = JSON.parse(saved);
        if (draft.formData) Object.assign(this.formData, draft.formData);
        if (draft.eligibilityAnswers) this.eligibilityAnswers = draft.eligibilityAnswers;
      } catch (e) {}
    }
  }

  clearDraft() {
    localStorage.removeItem('cms_complaint_draft');
  }

  // FR-G-012 + NFR-006: File handling with validation and preview
  fileUploadError = '';

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.fileUploadError = '';

    const newFiles = Array.from(input.files);
    const setResult = validateFileSet(newFiles, this.attachments.length);
    if (!setResult.valid) {
      this.fileUploadError = setResult.error!;
      announceToScreenReader(setResult.error!, 'assertive');
      input.value = '';
      return;
    }

    for (const file of newFiles) {
      const result = validateFile(file);
      if (!result.valid) {
        this.fileUploadError = result.error!;
        announceToScreenReader(result.error!, 'assertive');
        continue;
      }
      this.attachments.push(file);
      const url = URL.createObjectURL(file);
      this.attachmentPreviews.push({ name: file.name, url, type: file.type });
    }
    input.value = '';
  }

  removeAttachment(index: number) {
    URL.revokeObjectURL(this.attachmentPreviews[index].url);
    this.attachments.splice(index, 1);
    this.attachmentPreviews.splice(index, 1);
  }

  previewAttachment(index: number) {
    window.open(this.attachmentPreviews[index].url, '_blank');
  }

  // FR-G-013: Speech to text
  toggleRecording() {
    if (this.isRecording()) {
      this.stopRecording();
    } else {
      this.startRecording();
    }
  }

  private startRecording() {
    const SRConstructor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SRConstructor) return;

    this.recognition = new SRConstructor();
    this.recognition.lang = 'en-IN';
    this.recognition.continuous = true;
    this.recognition.interimResults = true;

    this.recognition.onresult = (event: any) => {
      let transcript = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        transcript += event.results[i][0].transcript;
      }
      this.formData['complaintText'] = (this.formData['complaintText'] || '') + ' ' + transcript;
    };

    this.recognition.onerror = () => this.isRecording.set(false);
    this.recognition.onend = () => this.isRecording.set(false);

    this.recognition.start();
    this.isRecording.set(true);
  }

  private stopRecording() {
    if (this.recognition) {
      this.recognition.stop();
      this.recognition = null;
    }
    this.isRecording.set(false);
  }

  // ══════ SUBMIT ══════
  submit() {
    if (!this.declarationChecked) return;
    this.submitting.set(true);

    const payload = {
      channel: 'WEB_PORTAL',
      category: this.formData['complaintCategory'] || 'GENERAL',
      complainantName: this.formData['name'],
      complainantEmail: this.formData['email'],
      complainantPhone: this.formData['phone'],
      entityName: this.getSelectedBankName(),
      entityType: 'BANK',
      subject: this.formData['subCategory1'] || this.formData['complaintCategory'] || 'General Complaint',
      description: this.formData['complaintText'],
      amountInvolved: this.formData['disputeAmount'] ? parseFloat(this.formData['disputeAmount']) : undefined,
      transactionDate: this.formData['disputeDate'] || undefined,
    };

    this.complaintService.registerComplaint(payload).subscribe({
      next: (ack) => {
        this.referenceNumber = ack.complaintId;
        this.submitting.set(false);
        this.phase.set('success');
        this.clearDraft();
      },
      error: () => {
        const now = new Date();
        const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
        this.referenceNumber = `CMP-${dateStr}-${String(Math.floor(Math.random() * 999999)).padStart(6, '0')}`;
        this.submitting.set(false);
        this.phase.set('success');
        this.clearDraft();
      }
    });
  }

  get today(): string {
    return new Date().toLocaleDateString('en-IN');
  }

  getSelectedBankName(): string {
    const bankId = this.eligibilityAnswers['regulatedEntity'];
    const bank = this.banks.find(b => String(b.id) === bankId);
    return bank?.name || '';
  }

  trackComplaint() {
    this.router.navigate(['/public/track', this.referenceNumber]);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  withdrawComplaint() {
    this.router.navigate(['/public/withdraw', this.referenceNumber]);
  }
}
