import { Component, OnInit, OnDestroy, inject, signal, HostListener, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { ComplaintService } from '../../../services/complaint.service';
import { PublicAuthService } from '../../../services/public-auth.service';
import { TranslationService } from '../../../services/translation.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { validateFile, validateFileSet, MAX_FILE_COUNT } from '../../../utils/file-validator';
import { announceToScreenReader, setPageTitle } from '../../../utils/accessibility';
import { lookupPincode } from '../../../utils/pincode-data';
import { environment } from '../../../../environments/environment';

interface EligibilityQuestion {
  key: string;
  question: string;
  translationKey?: string;
  type: 'select' | 'radio';
  options: { label: string; value: string; translationKey?: string }[];
  blockOn: string | null;
  blockMessage: string;
  blockMessageKey?: string;
  nonMaintainable?: boolean;
  simplifiedText?: string;
  simplifiedTextKey?: string;
}

@Component({
  selector: 'app-public-file-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './file-complaint.component.html',
  styleUrl: './file-complaint.component.scss'
})
export class PublicFileComplaintComponent implements OnInit, OnDestroy {

  @ViewChild('formCard') formCard!: ElementRef<HTMLElement>;

  private complaintService = inject(ComplaintService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private publicAuth = inject(PublicAuthService);
  translationService = inject(TranslationService);
  private autoSaveTimer: any = null;
  lastSavedAt = signal('');

  // FR-G-007: Flow phases — login handled by PublicAuthService + guard
  phase = signal<'eligibility' | 'form' | 'success' | 'non-maintainable'>('eligibility');

  // Eligibility (FR-G-007 step 2)
  eligibilityStep = signal(1);
  eligibilityAnswers: Record<string, string> = {};
  eligibilityBlocked = signal(false);
  eligibilityBlockMessage = signal('');
  eligibilityBlockMessageKey = signal('');
  nonMaintainableCaseId = '';
  showSimplified = signal(false);

  banks: { id: number; name: string; department?: string; entityType?: string }[] = [];

  // FR-G-007 step 1: RE selection done in eligibility (9-step as per RBI CMS production)
  eligibilityQuestions: EligibilityQuestion[] = [
    {
      key: 'regulatedEntity',
      question: 'Select Regulated Entity Name',
      translationKey: 'eligibility.q_select_re',
      type: 'select',
      options: [],
      blockOn: null,
      blockMessage: '',
    },
    {
      key: 'filedWithRE',
      question: 'Have you filed a written / electronic complaint with the <RE Name>?',
      translationKey: 'eligibility.q_filed_with_re',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'no',
      blockMessage: 'in terms of clause 10(1)(j) of Reserve Bank – Integrated Ombudsman Scheme, 2026, the complaint cannot be processed under the Scheme.',
      blockMessageKey: 'eligibility.block_not_filed',
      nonMaintainable: true,
    },
    {
      key: 'receivedReply',
      question: 'Have you received any reply from the Entity?',
      translationKey: 'eligibility.q_received_reply',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: null,
      blockMessage: '',
      nonMaintainable: true,
    },
    {
      key: 'sentReminder',
      question: 'Have you sent any reminder to the <RE Name>?',
      translationKey: 'eligibility.q_sent_reminder',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: null,
      blockMessage: '',
      nonMaintainable: true,
    },
    {
      key: 'isSubJudice',
      question: 'Is the complaint relating to the same grievance which is already pending before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?',
      translationKey: 'eligibility.q_sub_judice',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause 10(2)(b)(ii) of the Reserve Bank - Integrated Ombudsman Scheme, 2026.',
      blockMessageKey: 'eligibility.block_sub_judice',
      nonMaintainable: true,
      simplifiedText: 'Have you already taken this exact problem to a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?',
      simplifiedTextKey: 'eligibility.q_sub_judice_simple',
    },
    {
      key: 'alreadySettled',
      question: 'Is the complaint relating to the same grievance which is already settled or dealt before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?',
      translationKey: 'eligibility.q_already_settled',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint has already been settled or dealt with by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2026.',
      blockMessageKey: 'eligibility.block_already_settled',
      nonMaintainable: true,
      simplifiedText: 'Has this exact problem already been resolved by a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?',
      simplifiedTextKey: 'eligibility.q_already_settled_simple',
    },
    {
      key: 'throughAdvocateEligibility',
      question: 'Is your complaint being made through an advocate?',
      translationKey: 'eligibility.q_through_advocate',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: null,
      blockMessage: '',
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'pendingBeforeOmbudsman',
      question: 'Is the complaint relating to the same grievance which is already pending before the Ombudsman?',
      translationKey: 'eligibility.q_pending_ombudsman',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'Your complaint is already pending before the Ombudsman on the same grievance. Duplicate complaints cannot be filed.',
      blockMessageKey: 'eligibility.block_pending_ombudsman',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'settledByOmbudsman',
      question: 'Is the complaint relating to the same grievance which is already settled or dealt with on merits by the Ombudsman?',
      translationKey: 'eligibility.q_settled_ombudsman',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'Your complaint has already been settled or dealt with on merits by the Ombudsman. You cannot file a fresh complaint on the same issue.',
      blockMessageKey: 'eligibility.block_settled_ombudsman',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'staffOfRE',
      question: 'Is the Complainant a staff of the RE and complaint involves employer-employee relationship?',
      translationKey: 'eligibility.q_staff_of_re',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'As the complaint involves the employer-employee relationship with the Regulated Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2026.',
      blockMessageKey: 'eligibility.block_staff_of_re',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'previouslyFiledWithCEPC',
      question: 'Have you previously filed a complaint on the same subject matter with CEPC/RBI Ombudsman?',
      translationKey: 'eligibility.q_previously_filed_cepc',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint on the same subject matter has already been filed with CEPC/RBI, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2026.',
      blockMessageKey: 'eligibility.block_previously_filed_cepc',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'employeeOfRE',
      question: 'Are / were you an employee of the Regulated Entity against whom this complaint is being filed?',
      translationKey: 'eligibility.q_employee_of_re',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: null,
      blockMessage: '',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
    {
      key: 'employerRelationship',
      question: 'If Yes, Is your complaint involves the employee-employer relationship of the Regulated Entity?',
      translationKey: 'eligibility.q_employer_relationship',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'As your complaint involves the employee-employer relationship with the Regulated Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2026.',
      blockMessageKey: 'eligibility.block_employer_relationship',
      nonMaintainable: true,
      simplifiedText: '',
      simplifiedTextKey: '',
    },
  ];

  // FR-G-007: Multi-step form (steps 3-7)
  // Step 1: Complainant Details, Step 2: Regulated Entity Details, Step 3: Complaint Details,
  // Step 4: Authorised Representative, Step 5: Declaration & Review, Step 6: Preview/Submit
  currentStep = signal(1);
  highestStepReached = signal(1);
  totalSteps = 6;
  stepTitles = [
    'Complainant Details',
    'Regulated Entity Details',
    'Complaint Details',
    'Representative Authorisation',
    'Declaration',
    'Review and Submit'
  ];

  declarationChecked = false;
  declaration2Checked = false;
  submitting = signal(false);
  referenceNumber = '';
  watermarkRows = Array.from({ length: 80 }, (_, i) => i + 1);

  // Entity search
  entitySearchText = '';
  entityDropdownOpen = false;
  filteredEntityOptions: { label: string; value: string; entityType?: string }[] = [];
  entitySelectOptions: { label: string; value: string }[] = [];
  nonCoveredEntityOptions: { label: string; value: string }[] = [];

  // FR-G-020: Duplicate detection
  showDuplicatePopup = signal(false);
  duplicateMessage = '';
  duplicateCheckDone = false;

  // FR-G-008: Draft
  draftSaved = signal(false);

  // Form data
  formData: Record<string, any> = {
    // Complainant
    firstName: '',
    middleName: '',
    lastName: '',
    age: '',
    gender: '',
    email: '',
    complainantCategory: '',
    phone: '',
    state: '',
    district: '',
    pincode: '',
    address: '',
    organizationName: '',
    orgLandline: '',
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
    hasAccountWithRE: '',
    accountType: '',
    savingsAccountNumber: '',
    atmDebitCardNumber: '',
    disputeAmount: '',
    compensationSought: '',
    reliefSought: '',
    // RE entity location
    isCreditCardComplaint: '',
    entityState: '',
    entityDistrict: '',
    entityBranch: '',
    creditCardNumber: '',
    reminderDate: '',
    isComplainantSelf: '',
    // Auth Rep
    hasAuthRep: '',
    throughAdvocate: '',
    authorizeRepresentative: '',
    repName: '',
    repPhone: '',
    repEmail: '',
    repPincode: '',
    repState: '',
    repDistrict: '',
    repCity: '',
    repAddress: '',
  };

  attachments: File[] = [];
  attachmentPreviews: { name: string; url: string; type: string; size: number }[] = [];


  categories: { label: string; value: string }[] = [];

  accountTypes: { label: string; value: string; checked: boolean }[] = [];
  accountTypeDropdownOpen = false;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (this.accountTypeDropdownOpen && !target.closest('.multiselect-dropdown')) {
      this.accountTypeDropdownOpen = false;
    }
  }

  getSelectedAccountTypesLabel(): string {
    const selected = this.accountTypes.filter(a => a.checked);
    if (selected.length === 0) return '';
    return selected.map(a => a.label).join(', ');
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

  dateDisplay: Record<string, string> = { bankComplaintDate: '', reminderDate: '', replyDate: '' };

  isoToDisplay(iso: string): string {
    if (!iso) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  onDateInput(field: string, event: Event) {
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
        if (field === 'bankComplaintDate') this.onBankComplaintDateChange();
        else if (field === 'reminderDate') this.onReminderDateChange();
        else if (field === 'replyDate') this.onReplyDateChange();
      } else {
        this.formData[field] = '';
      }
    } else {
      this.formData[field] = '';
    }
  }

  initDateDisplays() {
    for (const field of ['bankComplaintDate', 'reminderDate', 'replyDate']) {
      if (this.formData[field]) {
        this.dateDisplay[field] = this.isoToDisplay(this.formData[field]);
      }
    }
  }

  openDatePicker(event: Event) {
    const btn = event.currentTarget as HTMLElement;
    const hiddenInput = btn.parentElement?.querySelector('.date-hidden-picker') as HTMLInputElement;
    if (hiddenInput) hiddenInput.showPicker();
  }

  onDatePickerChange(field: string, event: Event) {
    const input = event.target as HTMLInputElement;
    const iso = input.value;
    if (iso) {
      this.formData[field] = iso;
      this.dateDisplay[field] = this.isoToDisplay(iso);
      if (field === 'bankComplaintDate') this.onBankComplaintDateChange();
      else if (field === 'reminderDate') this.onReminderDateChange();
      else if (field === 'replyDate') this.onReplyDateChange();
    }
  }

  states: { label: string; value: string }[] = [];
  districts: string[] = [];
  branches: string[] = [];

  get entityStateKeys(): string[] {
    return this.states.map(s => s.value);
  }

  entityStateLabel(key: string): string {
    const state = this.states.find(s => s.value === key);
    return state?.label || key.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  }

  get entityDistricts(): string[] {
    return this.districts;
  }

  get entityBranches(): string[] {
    return this.branches;
  }

  onEntityStateChange() {
    this.formData['entityDistrict'] = '';
    this.formData['entityBranch'] = '';
    this.districts = [];
    this.branches = [];
    const state = this.formData['entityState'];
    if (state) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state } }).subscribe({
        next: (res) => { this.districts = res?.data ?? res ?? []; },
        error: () => {}
      });
    }
  }

  onEntityDistrictChange() {
    this.formData['entityBranch'] = '';
    this.branches = [];
    const district = this.formData['entityDistrict'];
    if (district) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/branches`, { params: { district } }).subscribe({
        next: (res) => { this.branches = res?.data ?? res ?? []; },
        error: () => {}
      });
    }
  }


  subCategories: Record<string, { label: string; value: string }[]> = {};

  get filteredSubCategories(): { label: string; value: string }[] {
    return this.subCategories[this.formData['complaintCategory']] || [];
  }

  onCategoryChange() {
    this.formData['subCategory1'] = '';
    this.formData['subCategory2'] = '';
  }

  private defaultCategories(): { label: string; value: string }[] {
    return [
      { label: 'ATM / Debit Card', value: 'ATM_DEBIT_CARD' },
      { label: 'Credit Card', value: 'CREDIT_CARD' },
      { label: 'Internet / Mobile Banking', value: 'INTERNET_MOBILE_BANKING' },
      { label: 'UPI', value: 'UPI' },
      { label: 'Loans and Advances', value: 'LOANS_ADVANCES' },
      { label: 'Deposit Accounts', value: 'DEPOSIT_ACCOUNTS' },
      { label: 'Remittances (NEFT/RTGS/IMPS)', value: 'REMITTANCES' },
      { label: 'Insurance', value: 'INSURANCE' },
      { label: 'Pension', value: 'PENSION' },
      { label: 'Para Banking', value: 'PARA_BANKING' },
      { label: 'Others', value: 'OTHERS' }
    ];
  }

  onAccountTypeToggle(accountType: { label: string; value: string; checked: boolean }) {
    this.validationErrors['accountType'] = '';
    if (!accountType.checked) {
      const fieldMap: Record<string, string> = {
        savings: 'savingsAccountNumber',
        loan: 'loanAccountNumber',
        atm_debit: 'atmDebitCardNumber',
        credit_card: 'creditCardNumber'
      };
      const field = fieldMap[accountType.value];
      if (field) {
        this.formData[field] = '';
        this.validationErrors[field] = '';
      }
    }
  }

  isAccountTypeSelected(type: string): boolean {
    return this.accountTypes.find(at => at.value === type)?.checked ?? false;
  }

  complainantStatesList: { label: string; value: string }[] = [];

  // Pincode lookup
  pincodeLoading = false;
  complainantStates: string[] = [];
  complainantDistricts: string[] = [];

  onPincodeInput() {
    const value = this.formData['pincode'];
    if (!value) {
      delete this.validationErrors['pincode'];
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];
    } else if (!/^\d*$/.test(value)) {
      this.validationErrors['pincode'] = 'Pincode must contain only digits.';
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];
    } else if (value.length < 6) {
      delete this.validationErrors['pincode'];
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];
    } else if (value.length > 6) {
      this.validationErrors['pincode'] = 'Pincode must be exactly 6 digits.';
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];
    } else {
      delete this.validationErrors['pincode'];
      this.pincodeLoading = true;
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];

      this.http.get<any[]>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading = false;
          if (res && res[0] && res[0].Status === 'Success' && res[0].PostOffice?.length) {
            const postOffices = res[0].PostOffice;
            const states = [...new Set(postOffices.map((po: any) => po.State).filter(Boolean))] as string[];
            const districts = [...new Set(postOffices.map((po: any) => po.District).filter(Boolean))] as string[];
            this.complainantStates = states;
            this.complainantDistricts = districts;
            this.formData['state'] = states[0] || '';
            this.formData['district'] = districts[0] || '';
          } else {
            this.applyLocalPincode(value);
          }
        },
        error: () => {
          this.pincodeLoading = false;
          this.applyLocalPincode(value);
        }
      });
    }
  }

  private applyLocalPincode(value: string) {
    const entry = lookupPincode(value);
    if (entry) {
      this.complainantStates = [entry.state];
      this.complainantDistricts = [entry.district];
      this.formData['state'] = entry.state;
      this.formData['district'] = entry.district;
      delete this.validationErrors['pincode'];
    } else {
      this.validationErrors['pincode'] = 'Invalid pincode. No location found.';
    }
  }

  // Representative pincode lookup
  repPincodeLoading = false;
  repStates: string[] = [];
  repDistricts: string[] = [];
  repCities: string[] = [];

  onRepPincodeInput() {
    const value = this.formData['repPincode'];
    if (value && value.length === 6 && /^\d{6}$/.test(value)) {
      this.repPincodeLoading = true;
      this.formData['repState'] = '';
      this.formData['repDistrict'] = '';
      this.formData['repCity'] = '';
      this.repStates = [];
      this.repDistricts = [];
      this.repCities = [];

      this.http.get<any[]>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.repPincodeLoading = false;
          if (res && res[0] && res[0].Status === 'Success' && res[0].PostOffice?.length) {
            const postOffices = res[0].PostOffice;
            const states = [...new Set(postOffices.map((po: any) => po.State).filter(Boolean))] as string[];
            const districts = [...new Set(postOffices.map((po: any) => po.District).filter(Boolean))] as string[];
            const cities = [...new Set(postOffices.map((po: any) => po.Name).filter(Boolean))] as string[];
            this.repStates = states;
            this.repDistricts = districts;
            this.repCities = cities;
            this.formData['repState'] = states[0] || '';
            this.formData['repDistrict'] = districts[0] || '';
            this.formData['repCity'] = cities[0] || '';
          }
        },
        error: () => {
          this.repPincodeLoading = false;
        }
      });
    } else {
      this.formData['repState'] = '';
      this.formData['repDistrict'] = '';
      this.formData['repCity'] = '';
      this.repStates = [];
      this.repDistricts = [];
      this.repCities = [];
    }
  }

  // Eligibility file uploads (separate per section)
  complaintFileWithRE: File | null = null;
  complaintFileWithREName = '';
  reminderFile: File | null = null;
  reminderFileName = '';
  replyFile: File | null = null;
  replyFileName = '';
  repFile: File | null = null;
  repFileName = '';

  onComplaintFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      if (input.files[0].size > 2 * 1024 * 1024) {
        this.eligibilityFileError = 'File size exceeds 2MB limit';
        input.value = '';
        return;
      }
      this.complaintFileWithRE = input.files[0];
      this.complaintFileWithREName = input.files[0].name;
      this.eligibilityFileError = '';
    }
  }

  onReminderFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      if (input.files[0].size > 2 * 1024 * 1024) {
        this.reminderFileError = 'File size exceeds 2MB limit';
        input.value = '';
        return;
      }
      this.reminderFile = input.files[0];
      this.reminderFileName = input.files[0].name;
      this.reminderFileError = '';
    }
  }

  onReplyFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      if (input.files[0].size > 2 * 1024 * 1024) {
        this.replyFileError = 'File size exceeds 2MB limit';
        input.value = '';
        return;
      }
      this.replyFile = input.files[0];
      this.replyFileName = input.files[0].name;
      this.replyFileError = '';
    }
  }

  previewFile(file: File | null) {
    if (file) {
      const url = URL.createObjectURL(file);
      window.open(url, '_blank');
    }
  }

  removeComplaintFile() {
    this.complaintFileWithRE = null;
    this.complaintFileWithREName = '';
  }

  removeReminderFile() {
    this.reminderFile = null;
    this.reminderFileName = '';
  }

  removeReplyFile() {
    this.replyFile = null;
    this.replyFileName = '';
  }

  onRepFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      if (input.files[0].size > 2 * 1024 * 1024) {
        input.value = '';
        return;
      }
      this.repFile = input.files[0];
      this.repFileName = input.files[0].name;
    }
  }

  onRepFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    const file = event.dataTransfer.files[0];
    if (file.size > 2 * 1024 * 1024) return;
    this.repFile = file;
    this.repFileName = file.name;
  }

  removeRepFile() {
    this.repFile = null;
    this.repFileName = '';
  }

  // FR-G-013: Speech to text
  isRecording = signal(false);
  speechSupported = false;
  private recognition: any = null;

  ngOnInit() {
    setPageTitle('File a Complaint');
    this.loadRegulatedEntities();
    this.loadMasterData();
    this.speechSupported = !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
    this.formData['phone'] = this.publicAuth.userIdentifier() || '';

    const draftId = this.route.snapshot.queryParamMap.get('draftId');
    if (draftId) {
      this.loadDraftFromServer(draftId);
    } else {
      this.loadDraft();
    }

    this.startAutoSave();
  }

  private loadMasterData() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/categories`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        const categoryMap: Record<string, { label: string; value: string }[]> = {};
        const categorySet = new Map<string, string>();
        data.forEach((item: any) => {
          const catValue = item.categoryName || item.value;
          const catLabel = item.categoryLabel || item.label || catValue;
          if (!categorySet.has(catValue)) {
            categorySet.set(catValue, catLabel);
          }
          if (item.subCategory) {
            if (!categoryMap[catValue]) categoryMap[catValue] = [];
            categoryMap[catValue].push({ label: item.subCategory, value: item.subCategoryValue || item.subCategory });
          }
        });
        this.categories = Array.from(categorySet.entries()).map(([value, label]) => ({ label, value }));
        if (this.categories.length === 0) {
          this.categories = this.defaultCategories();
        }
        this.subCategories = categoryMap;
      },
      error: () => {
        this.categories = this.defaultCategories();
      }
    });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        this.states = data.map((s: any) => typeof s === 'string' ? { label: s, value: s } : { label: s.name || s.label, value: s.value || s.code || s.name });
        this.complainantStatesList = this.states;
      },
      error: () => {}
    });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/account-types`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        this.accountTypes = data.map((a: any) => ({ label: a.label || a.name, value: a.value || a.code, checked: false }));
      },
      error: () => {}
    });
  }

  private loadDraftFromServer(draftId: string) {
    this.complaintService.getDraft(draftId).subscribe({
      next: (draft) => {
        if (draft.formData) {
          const validKeys = Object.keys(this.formData);
          for (const key of validKeys) {
            if (draft.formData[key] !== undefined) {
              this.formData[key] = draft.formData[key];
            }
          }
        }
        if (draft.eligibilityAnswers) {
          this.eligibilityAnswers = draft.eligibilityAnswers;
        }
        if (draft.currentStep) {
          this.currentStep.set(draft.currentStep);
          this.highestStepReached.set(draft.currentStep);
        }
        if (draft.phase === 'form') {
          this.phase.set('form');
        }
        if (draft.eligibilityAnswers?.['selectedEntity']) {
          this.eligibilityStep.set(Object.keys(draft.eligibilityAnswers).length + 1);
        }
        localStorage.setItem('cms_draft_id', draftId);
      },
      error: () => {
        this.loadDraft();
      }
    });
  }

  private loadRegulatedEntities() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`).subscribe({
      next: (res) => {
        const entities = res?.data ?? res ?? [];
        this.banks = entities.map((e: any) => ({
          id: e.id,
          name: e.name,
          department: e.department || 'RBIO',
          entityType: e.entityType
        }));
        this.banks.sort((a, b) => a.name.localeCompare(b.name));
        this.eligibilityQuestions[0].options = this.banks.map(b => ({ label: b.name, value: String(b.id) }));
        const covered = this.banks.filter(b => b.department !== 'CEPC');
        const notCovered = this.banks.filter(b => b.department === 'CEPC');
        this.entitySelectOptions = covered.map(b => ({ label: b.name, value: String(b.id) }));
        this.nonCoveredEntityOptions = notCovered.map(b => ({ label: b.name, value: String(b.id) }));
      },
      error: () => {
        this.eligibilityQuestions[0].options = [];
      }
    });
  }

  ngOnDestroy() {
    this.stopAutoSave();
    this.stopRecording();
  }

  get sessionMinutes(): string {
    return this.publicAuth.getFormattedTime();
  }

  // ══════ ELIGIBILITY (FR-G-007 step 2) ══════
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

  get currentQuestionText(): string {
    const q = this.currentQuestion;
    const translated = q.translationKey
      ? this.translationService.translate(q.translationKey)
      : q.question;
    const text = (translated !== q.translationKey) ? translated : q.question;
    return text.replace(/<RE Name>/g, this.selectedEntityName).replace(/\{\{reName\}\}/g, this.selectedEntityName);
  }

  selectEligibilityAnswer(value: string) {
    const q = this.currentQuestion;
    this.eligibilityAnswers[q.key] = value;
    if (q.blockOn && value === q.blockOn) {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(q.blockMessage);
      this.eligibilityBlockMessageKey.set(q.blockMessageKey || '');
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
      this.eligibilityBlockMessageKey.set('');
    }

    // Auto-closure: "No" reply and complaint filed within 30 days
    if (q.key === 'receivedReply' && value === 'no') {
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
        }
      }
    }
  }

  filterEntities() {
    const term = this.entitySearchText.toLowerCase().trim();
    if (!term) {
      this.filteredEntityOptions = this.eligibilityQuestions[0].options.slice(0, 50).map(o => ({
        ...o,
        entityType: this.banks.find(b => String(b.id) === o.value)?.entityType
      }));
      return;
    }
    this.filteredEntityOptions = this.eligibilityQuestions[0].options
      .filter(o => {
        const bank = this.banks.find(b => String(b.id) === o.value);
        return o.label.toLowerCase().includes(term) ||
               (bank?.entityType?.toLowerCase().includes(term));
      })
      .slice(0, 50)
      .map(o => ({
        ...o,
        entityType: this.banks.find(b => String(b.id) === o.value)?.entityType
      }));
  }

  selectEntityFromSearch(opt: { label: string; value: string }) {
    this.selectEligibilityAnswer(opt.value);
    this.entitySearchText = '';
    this.entityDropdownOpen = false;
    this.filteredEntityOptions = [];
  }

  getSelectedEntityLabel(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const opt = this.eligibilityQuestions[0].options.find(o => o.value === val);
    return opt?.label ?? '';
  }

  clearEntitySelection() {
    this.eligibilityAnswers['regulatedEntity'] = '';
    this.entitySearchText = '';
    this.eligibilityBlocked.set(false);
  }

  closeEntityDropdown() {
    setTimeout(() => this.entityDropdownOpen = false, 200);
  }

  selectEntityFromDropdown(value: string) {
    if (value) {
      this.selectEligibilityAnswer(value);
    }
  }

  eligibilityFieldError = '';
  eligibilityFileError = '';
  eligibilityRefError = '';

  nextEligibility() {
    if (this.eligibilityBlocked()) {
      const q = this.currentQuestion;
      if (q.nonMaintainable) {
        this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
        this.phase.set('non-maintainable');
      }
      return;
    }
    const q = this.currentQuestion;
    if (!this.eligibilityAnswers[q.key]) return;

    this.eligibilityFieldError = '';
    this.eligibilityFileError = '';
    this.eligibilityRefError = '';
    this.replyFileError = '';
    this.replyDateError = '';
    this.reminderFileError = '';
    this.reminderDateError = '';
    if (q.key === 'filedWithRE' && this.eligibilityAnswers['filedWithRE'] === 'yes') {
      if (!this.formData['bankComplaintDate']) {
        this.eligibilityFieldError = 'Complaint date with RE is required';
        return;
      }
      if (!this.complaintFileWithRE) {
        this.eligibilityFileError = 'Please upload a copy of the complaint sent to the Regulated Entity';
        return;
      }
    }

    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'yes') {
      if (!this.formData['replyDate']) {
        this.replyDateError = 'Date on which reply was received is required';
        return;
      }
      if (!this.replyFile) {
        this.replyFileError = 'Please upload a copy of the reply received from the Regulated Entity';
        return;
      }
    }

    if (q.key === 'sentReminder' && this.eligibilityAnswers['sentReminder'] === 'yes') {
      if (!this.formData['reminderDate']) {
        this.reminderDateError = 'Date on which reminder was sent is required';
        return;
      }
      if (!this.reminderFile) {
        this.reminderFileError = 'Please upload a copy of the reminder sent to the Regulated Entity';
        return;
      }
    }

    if (q.key === 'employeeOfRE' && this.eligibilityAnswers['employeeOfRE'] === 'yes') {
      if (!this.eligibilityAnswers['employerRelationship']) {
        return;
      }
    }

    // UST11: Block if "No" reply and <=30 days since filing with RE
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
          return;
        }
      }
    }



    if (this.eligibilityStep() < this.totalEligibilitySteps) {
      this.showSimplified.set(false);
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
      this.showSimplified.set(false);
      this.eligibilityStep.update(s => s - 1);
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  // FR-G-010: Download closure letter as PDF
  downloadClosureLetter() {
    import('jspdf').then(({ jsPDF }) => {
      const doc = new jsPDF();
      const pw = doc.internal.pageSize.getWidth();
      let y = 20;

      doc.setFontSize(16);
      doc.setFont('helvetica', 'bold');
      doc.text('RESERVE BANK OF INDIA', pw / 2, y, { align: 'center' });
      y += 8;
      doc.setFontSize(11);
      doc.setFont('helvetica', 'normal');
      doc.text('Integrated Ombudsman Scheme, 2026', pw / 2, y, { align: 'center' });
      y += 12;

      doc.setDrawColor(0);
      doc.line(20, y, pw - 20, y);
      y += 10;

      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('CLOSURE LETTER', pw / 2, y, { align: 'center' });
      y += 12;

      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      doc.text(`Case ID: ${this.nonMaintainableCaseId}`, 20, y);
      y += 7;
      doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, 20, y);
      y += 14;

      doc.text('Dear Complainant,', 20, y);
      y += 10;

      const reason = this.eligibilityBlockMessage();
      const bodyText = `Your complaint has been closed as Non-Maintainable under the provisions of the Reserve Bank - Integrated Ombudsman Scheme, 2026.`;
      const lines = doc.splitTextToSize(bodyText, pw - 40);
      doc.text(lines, 20, y);
      y += lines.length * 6 + 8;

      doc.setFont('helvetica', 'bold');
      doc.text('Reason:', 20, y);
      y += 7;
      doc.setFont('helvetica', 'normal');
      const reasonLines = doc.splitTextToSize(reason, pw - 40);
      doc.text(reasonLines, 20, y);
      y += reasonLines.length * 6 + 14;

      doc.text('This is a system-generated letter and does not require a signature.', 20, y);
      y += 14;

      doc.setFont('helvetica', 'bold');
      doc.text('Reserve Bank of India', 20, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      doc.text('Department of Consumer Education and Protection', 20, y);
      y += 14;

      doc.setDrawColor(0, 100, 0);
      doc.setFillColor(240, 255, 240);
      doc.roundedRect(20, y, pw - 40, 12, 2, 2, 'FD');
      doc.setTextColor(0, 100, 0);
      doc.setFontSize(8);
      doc.setFont('helvetica', 'bold');
      doc.text('DIGITALLY SIGNED | RBI CMS Digital Certificate Authority', 25, y + 8);
      doc.setTextColor(0);

      doc.save(`Closure_Letter_${this.nonMaintainableCaseId}.pdf`);
    });
  }

  // FR-G-017: Form Validation
  validationErrors: Record<string, string> = {};

  onAmountInput(field: string, value: string) {
    const raw = value.replace(/,/g, '');
    if (raw && !/^\d*$/.test(raw)) {
      this.formData[field] = this.formData[field];
      return;
    }
    this.formData[field] = raw ? this.formatIndianNumber(raw) : '';
    if (field === 'compensationSought') this.validateCompensationSought();
    if (field === 'reliefSought') this.validateReliefSought();
  }

  private formatIndianNumber(value: string): string {
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

  validateCompensationSought() {
    const amount = parseFloat((this.formData['compensationSought'] || '0').replace(/,/g, ''));
    if (amount > 3000000) {
      this.validationErrors['compensationSought'] = 'Compensation for consequential loss can be awarded only up to ₹30 lakh. Please enter an amount up to ₹30 lakh.';
    } else {
      this.validationErrors['compensationSought'] = '';
    }
  }

  validateReliefSought() {
    const amount = parseFloat((this.formData['reliefSought'] || '0').replace(/,/g, ''));
    if (amount > 300000) {
      this.validationErrors['reliefSought'] = 'Compensation for expenses, harassment, and mental anguish can be awarded only up to ₹3 lakh. Please enter an amount up to ₹3 lakh.';
    } else {
      this.validationErrors['reliefSought'] = '';
    }
  }

  validateCurrentStep(): boolean {
    this.validationErrors = {};
    const step = this.currentStep();

    if (step === 1) {
      if (!this.formData['firstName']?.trim()) this.validationErrors['name'] = 'First name is required';
      if (!this.formData['pincode'] || !/^\d{6}$/.test(this.formData['pincode'])) this.validationErrors['pincode'] = 'Valid 6-digit pincode is required';
      if (!this.formData['state']) this.validationErrors['state'] = 'Enter valid pincode to auto-fill state';
      if (!this.formData['address']?.trim()) this.validationErrors['address'] = 'Address is required';
      if (this.formData['email'] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData['email'])) this.validationErrors['email'] = 'Invalid email format';
    } else if (step === 2) {
      if (!this.formData['isCreditCardComplaint']) this.validationErrors['isCreditCardComplaint'] = 'Please select Yes or No';
      if (this.formData['isCreditCardComplaint'] === 'no') {
        if (!this.formData['entityState']) this.validationErrors['entityState'] = 'Entity state is required';
        if (!this.formData['entityDistrict']) this.validationErrors['entityDistrict'] = 'Entity district is required';
        if (!this.formData['entityBranch']?.trim()) this.validationErrors['entityBranch'] = 'Entity branch is required';
      }
    } else if (step === 3) {
      if (!this.formData['complaintCategory']) this.validationErrors['complaintCategory'] = 'Complaint category is required';
      if (!this.formData['complaintText']?.trim()) this.validationErrors['complaintText'] = 'Facts of the complaint is required';
      if (!this.formData['hasAccountWithRE']) this.validationErrors['hasAccountWithRE'] = 'Please select Yes or No';
      if (this.formData['hasAccountWithRE'] === 'yes') {
        if (!this.accountTypes.some(a => a.checked)) this.validationErrors['accountType'] = 'Please select at least one account type';
        if (this.isAccountTypeSelected('savings') && !this.formData['savingsAccountNumber']?.trim()) this.validationErrors['savingsAccountNumber'] = 'Savings account number is required';
        if (this.isAccountTypeSelected('loan') && !this.formData['loanAccountNumber']?.trim()) this.validationErrors['loanAccountNumber'] = 'Loan account number is required';
        if (this.isAccountTypeSelected('atm_debit') && !this.formData['atmDebitCardNumber']?.trim()) this.validationErrors['atmDebitCardNumber'] = 'ATM/Debit card number is required';
        if (this.isAccountTypeSelected('credit_card') && !this.formData['creditCardNumber']?.trim()) this.validationErrors['creditCardNumber'] = 'Credit card number is required';
      }
      if (!this.formData['isWalletComplaint']) this.validationErrors['isWalletComplaint'] = 'Please select Yes or No';
      if (this.formData['isWalletComplaint'] === 'yes') {
        if (!this.formData['walletName']?.trim()) this.validationErrors['walletName'] = 'Name of wallet is required';
        if (!this.formData['transactionRefNumber']?.trim()) this.validationErrors['transactionRefNumber'] = 'Transaction/Reference number is required';
      }
      if (!this.formData['isBusinessCorrespondent']) this.validationErrors['isBusinessCorrespondent'] = 'Please select Yes or No';
      // UST66: Consequential loss cap ₹30 lakh
      const compAmount = parseFloat((this.formData['compensationSought'] || '0').replace(/,/g, ''));
      if (compAmount > 3000000) {
        this.validationErrors['compensationSought'] = 'Compensation for consequential loss can be awarded only up to ₹30 lakh. Please enter an amount up to ₹30 lakh.';
      }
      // UST67: Expenses/Harassment/Mental Anguish cap ₹3 lakh
      const reliefAmount = parseFloat((this.formData['reliefSought'] || '0').replace(/,/g, ''));
      if (reliefAmount > 300000) {
        this.validationErrors['reliefSought'] = 'Compensation for expenses, harassment, and mental anguish can be awarded only up to ₹3 lakh. Please enter an amount up to ₹3 lakh.';
      }
    } else if (step === 4) {
      if (this.formData['authorizeRepresentative'] === 'yes') {
        if (!this.formData['repName']?.trim()) this.validationErrors['repName'] = 'Representative name is required';
      }
    } else if (step === 5) {
      if (!this.declarationChecked || !this.declaration2Checked) this.validationErrors['declaration'] = 'You must accept all declarations to proceed';
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
    repName: 'Full name of the person authorised to represent you',
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

  // FR-G-019: Download review form as PDF (captures the rendered Review & Submit section)
  async downloadAcknowledgement() {
    const element = this.formCard?.nativeElement;
    if (!element) return;

    const html2canvas = (await import('html2canvas')).default;
    const { jsPDF } = await import('jspdf');

    const stepHeader = element.querySelector('.step-header') as HTMLElement;
    const navActions = element.closest('.page-container')?.querySelector('.eligibility-actions') as HTMLElement;
    if (stepHeader) stepHeader.style.display = 'none';
    if (navActions) navActions.style.display = 'none';

    const canvas = await html2canvas(element, {
      scale: 2,
      useCORS: true,
      logging: false,
      backgroundColor: '#ffffff'
    });

    if (stepHeader) stepHeader.style.display = '';
    if (navActions) navActions.style.display = '';

    const imgData = canvas.toDataURL('image/png');
    const imgWidth = canvas.width;
    const imgHeight = canvas.height;

    const pdfWidth = 210;
    const pdfHeight = 297;
    const margin = 10;
    const contentWidth = pdfWidth - margin * 2;

    const doc = new jsPDF('p', 'mm', 'a4');
    const pageContentHeight = pdfHeight - margin * 2;
    const scaledHeight = (imgHeight * contentWidth) / imgWidth;

    if (scaledHeight <= pageContentHeight) {
      doc.addImage(imgData, 'PNG', margin, margin, contentWidth, scaledHeight);
    } else {
      let remainingHeight = imgHeight;
      let sourceY = 0;
      let page = 0;

      while (remainingHeight > 0) {
        if (page > 0) doc.addPage();

        const sliceHeight = Math.min(remainingHeight, (pageContentHeight / contentWidth) * imgWidth);

        const sliceCanvas = document.createElement('canvas');
        sliceCanvas.width = imgWidth;
        sliceCanvas.height = sliceHeight;
        const ctx = sliceCanvas.getContext('2d')!;
        ctx.drawImage(canvas, 0, sourceY, imgWidth, sliceHeight, 0, 0, imgWidth, sliceHeight);

        const sliceData = sliceCanvas.toDataURL('image/png');
        const sliceScaledHeight = (sliceHeight * contentWidth) / imgWidth;
        doc.addImage(sliceData, 'PNG', margin, margin, contentWidth, sliceScaledHeight);

        sourceY += sliceHeight;
        remainingHeight -= sliceHeight;
        page++;
      }
    }

    const fileName = this.referenceNumber ? `Complaint_${this.referenceNumber}.pdf` : 'Draft.pdf';
    doc.save(fileName);
  }

  // ══════ MULTI-STEP FORM ══════
  nextStep() {
    if (!this.validateCurrentStep()) return;
    if (this.currentStep() < this.totalSteps) {
      this.currentStep.update(s => s + 1);
      if (this.currentStep() > this.highestStepReached()) {
        this.highestStepReached.set(this.currentStep());
      }
      this.saveDraft();
    }
  }

  prevStep() {
    if (this.currentStep() > 1) {
      this.currentStep.update(s => s - 1);
    }
  }

  goToStep(step: number) {
    if (step <= this.highestStepReached()) {
      this.currentStep.set(step);
    }
  }

  private readonly DRAFT_VERSION = 4;

  // FR-G-008: Save Draft
  saveDraft() {
    const attachmentMeta = this.attachmentPreviews.map(f => ({ name: f.name, type: f.type, size: f.size }));
    const draft = { version: this.DRAFT_VERSION, formData: this.formData, eligibilityAnswers: this.eligibilityAnswers, eligibilityStep: this.eligibilityStep(), currentStep: this.currentStep(), phase: this.phase(), entityName: this.getSelectedBankName(), declarationChecked: this.declarationChecked, declaration2Checked: this.declaration2Checked, attachmentMeta };
    localStorage.setItem('cms_complaint_draft', JSON.stringify(draft));
    localStorage.setItem('cms_draft_saved_at', new Date().toISOString());
    this.draftSaved.set(true);
    const now = new Date();
    this.lastSavedAt.set(now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }));
    setTimeout(() => this.draftSaved.set(false), 2000);

    this.saveDraftToServer();
  }

  private saveDraftToServer() {
    const phone = this.publicAuth.userIdentifier();
    if (!phone) return;
    this.complaintService.saveDraft({
      phone,
      entityName: this.getSelectedBankName(),
      formData: this.formData,
      eligibilityAnswers: this.eligibilityAnswers,
      currentStep: this.currentStep(),
      phase: this.phase()
    }).subscribe({
      next: (res) => {
        if (res?.draftId) {
          localStorage.setItem('cms_draft_id', res.draftId);
        }
      },
      error: () => {}
    });
  }

  private startAutoSave() {
    this.autoSaveTimer = setInterval(() => {
      if (this.phase() === 'form' || this.phase() === 'eligibility') {
        this.saveDraft();
      }
    }, 30000);
  }

  private stopAutoSave() {
    if (this.autoSaveTimer) { clearInterval(this.autoSaveTimer); this.autoSaveTimer = null; }
  }

  loadDraft() {
    const saved = localStorage.getItem('cms_complaint_draft');
    if (saved) {
      try {
        const draft = JSON.parse(saved);
        if (draft.version !== this.DRAFT_VERSION) {
          localStorage.removeItem('cms_complaint_draft');
          return;
        }
        if (draft.phase === 'form') {
          if (draft.formData) {
            const validKeys = Object.keys(this.formData);
            for (const key of validKeys) {
              if (draft.formData[key] !== undefined) {
                this.formData[key] = draft.formData[key];
              }
            }
          }
          if (draft.eligibilityAnswers) {
            this.eligibilityAnswers = draft.eligibilityAnswers;
          }
          if (draft.declarationChecked !== undefined) {
            this.declarationChecked = draft.declarationChecked;
          }
          if (draft.declaration2Checked !== undefined) {
            this.declaration2Checked = draft.declaration2Checked;
          }
          if (draft.attachmentMeta?.length) {
            this.attachmentPreviews = draft.attachmentMeta.map((m: any) => ({ name: m.name, type: m.type, size: m.size, url: '' }));
          }
          this.phase.set('form');
          if (draft.currentStep) {
            this.currentStep.set(draft.currentStep);
            this.highestStepReached.set(draft.currentStep);
          }
          this.initDateDisplays();
        }
      } catch (e) {}
    }
  }

  clearDraft() {
    localStorage.removeItem('cms_complaint_draft');
    const draftId = localStorage.getItem('cms_draft_id');
    if (draftId) {
      this.complaintService.deleteDraft(draftId).subscribe({ error: () => {} });
      localStorage.removeItem('cms_draft_id');
    }
  }

  // FR-G-012 + NFR-006: File handling with validation and preview
  isDragOver = false;
  isRepDragOver = false;
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
      this.attachmentPreviews.push({ name: file.name, url, type: file.type, size: file.size });
      this.validationErrors['attachments'] = '';
    }
    input.value = '';
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    this.fileUploadError = '';
    const newFiles = Array.from(event.dataTransfer.files);
    const setResult = validateFileSet(newFiles, this.attachments.length);
    if (!setResult.valid) {
      this.fileUploadError = setResult.error!;
      announceToScreenReader(setResult.error!, 'assertive');
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
      this.attachmentPreviews.push({ name: file.name, url, type: file.type, size: file.size });
      this.validationErrors['attachments'] = '';
    }
  }

  removeAttachment(index: number) {
    if (this.attachmentPreviews[index].url) {
      URL.revokeObjectURL(this.attachmentPreviews[index].url);
    }
    if (this.attachments[index]) {
      this.attachments.splice(index, 1);
    }
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

    if (!this.duplicateCheckDone) {
      this.checkDuplicate();
      return;
    }

    this.duplicateCheckDone = false;
    this.performSubmit();
  }

  private checkDuplicate() {
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
      error: () => {
        this.duplicateCheckDone = true;
        this.submit();
      }
    });
  }

  dismissDuplicatePopup() {
    this.showDuplicatePopup.set(false);
    this.duplicateMessage = '';
  }

  proceedDespiteDuplicate() {
    this.showDuplicatePopup.set(false);
    this.duplicateCheckDone = true;
    this.submit();
  }

  private performSubmit() {
    this.submitting.set(true);

    const selectedEntityId = this.eligibilityAnswers['regulatedEntity'];
    const selectedBank = this.banks.find(b => String(b.id) === selectedEntityId);

    const payload = {
      filingType: 'ONLINE',
      category: this.formData['complaintCategory'] || 'GENERAL',
      complainantName: [this.formData['firstName'], this.formData['middleName'], this.formData['lastName']].filter(Boolean).join(' '),
      complainantEmail: this.formData['email'],
      complainantPhone: this.formData['phone'],
      complainantAddress: this.formData['address'],
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
        this.submitting.set(false);
        this.phase.set('success');
        this.clearDraft();
      },
      error: (err) => {
        this.submitting.set(false);
        this.validationErrors['submit'] = err?.error?.message || 'Failed to submit complaint. Please try again.';
      }
    });
  }

  get today(): string {
    return new Date().toLocaleDateString('en-IN');
  }

  get todayISO(): string {
    return new Date().toISOString().split('T')[0];
  }

  // CEPC vs RBIO entity type detection
  get selectedEntityType(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const bank = this.banks.find(b => String(b.id) === val);
    return bank?.department?.toUpperCase() || 'RBIO';
  }

  get isCEPCEntity(): boolean {
    return this.selectedEntityType === 'CEPC';
  }

  isIndividualCategory(): boolean {
    const cat = this.formData['complainantCategory'];
    return cat === 'individual' || cat === 'senior_citizen';
  }

  getCategoryLabel(): string {
    const map: Record<string, string> = {
      individual: 'Individual', pwd: 'Person with Disabilities', senior_citizen: 'Senior Citizen',
      individual_business: 'Individual – Business', proprietorship: 'Proprietorship',
      partnership: 'Partnership', msme: 'MSME', association: 'Association', trust: 'Trust',
      limited_company: 'Limited Company', government_department: 'Government Department', psu: 'PSU'
    };
    return map[this.formData['complainantCategory']] || this.formData['complainantCategory'] || '—';
  }

  getGenderLabel(): string {
    const map: Record<string, string> = {
      male: 'Male', female: 'Female', transgender: 'Transgender',
      not_disclosed: 'Do not wish to disclose', other: 'Other'
    };
    return map[this.formData['gender']] || this.formData['gender'] || '—';
  }

  getComplaintCategoryLabel(): string {
    const cat = this.categories.find(c => c.value === this.formData['complaintCategory']);
    return cat?.label || this.formData['complaintCategory'] || '—';
  }

  validateAge() {
    const ageStr = String(this.formData['age'] || '');
    const age = Number(ageStr);
    if (ageStr && isNaN(age)) {
      this.validationErrors['age'] = 'Age must be a number';
    } else if (ageStr.length > 3) {
      this.validationErrors['age'] = 'Age must not exceed 3 digits';
    } else if (age < 1 || age > 150) {
      this.validationErrors['age'] = 'Age must be between 1 and 150';
    } else if (this.formData['complainantCategory'] === 'senior_citizen' && age < 60) {
      this.validationErrors['age'] = 'Age must be 60 or above for Senior Citizen';
    } else {
      delete this.validationErrors['age'];
    }
  }

  onBankComplaintDateChange() {
    this.eligibilityFieldError = '';
    const bankDate = this.formData['bankComplaintDate'];
    if (bankDate) {
      const selected = new Date(bankDate);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (selected > today) {
        this.eligibilityFieldError = 'Date cannot be a future date';
      }
    }
  }

  // Auto-closure for reply date within 30 days
  showReplyDateAutoClosure = signal(false);
  replyDateAutoCloseMessage = '';

  replyDateError = '';
  replyFileError = '';
  reminderDateError = '';
  reminderFileError = '';

  onReplyDateChange() {
    this.replyDateError = '';
    const replyDate = this.formData['replyDate'];
    const complaintDate = this.formData['bankComplaintDate'];
    if (replyDate && complaintDate && new Date(replyDate) < new Date(complaintDate)) {
      this.replyDateError = 'Reply date cannot be earlier than the complaint filing date';
    }
  }

  onReminderDateChange() {
    this.reminderDateError = '';
    const reminderDate = this.formData['reminderDate'];
    const complaintDate = this.formData['bankComplaintDate'];
    if (reminderDate && complaintDate && new Date(reminderDate) < new Date(complaintDate)) {
      this.reminderDateError = 'Reminder date cannot be earlier than the complaint filing date';
    }
  }

  onEmployerRelationshipAnswer(value: string) {
    this.eligibilityAnswers['employerRelationship'] = value;
    if (value === 'yes') {
      this.eligibilityBlocked.set(true);
      this.eligibilityBlockMessage.set(
        'As your complaint involves the employee-employer relationship with the Regulated Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2026.'
      );
      this.eligibilityBlockMessageKey.set('eligibility.block_employer_relationship');
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  onAdvocateSubAnswer(value: string) {
    this.formData['isComplainantSelf'] = value;
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

  // Get visible eligibility questions based on entity type
  get visibleEligibilityQuestions(): EligibilityQuestion[] {
    return this.eligibilityQuestions.filter((q, i) => this.isQuestionVisible(q, i));
  }

  get radioEligibilityQuestions(): EligibilityQuestion[] {
    return this.visibleEligibilityQuestions.filter(q => q.type === 'radio');
  }

  isQuestionVisible(q: EligibilityQuestion, _index: number): boolean {
    if (q.key === 'regulatedEntity') return true;
    if (q.key === 'filedWithRE') return true;
    if (q.key === 'receivedReply') return true;

    if (q.key === 'sentReminder') return true;

    // Questions hidden for CEPC (isSubJudice, alreadySettled, pendingBeforeOmbudsman, settledByOmbudsman)
    if (this.isCEPCEntity) {
      if (['isSubJudice', 'alreadySettled', 'pendingBeforeOmbudsman', 'settledByOmbudsman'].includes(q.key)) {
        return false;
      }
    }

    // previouslyFiledWithCEPC shown only for CEPC
    if (q.key === 'previouslyFiledWithCEPC') {
      return this.isCEPCEntity;
    }

    // employeeOfRE shown only for CEPC
    if (q.key === 'employeeOfRE') return this.isCEPCEntity;

    // employerRelationship rendered inline as sub-question of employeeOfRE
    if (q.key === 'employerRelationship') return false;

    // staffOfRE shown only for RBIO
    if (q.key === 'staffOfRE') return this.selectedEntityType === 'RBIO';

    // throughAdvocateEligibility shown only for RBIO
    if (q.key === 'throughAdvocateEligibility') return this.selectedEntityType === 'RBIO';

    return true;
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
