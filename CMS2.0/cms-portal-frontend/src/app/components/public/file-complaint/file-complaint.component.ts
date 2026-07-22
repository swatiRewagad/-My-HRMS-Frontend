import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
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
  nonMaintainableCaseId = '';
  showSimplified = signal(false);

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
    },
    {
      key: 'sentReminder',
      question: 'Have you sent any reminder to the <RE Name>?',
      translationKey: 'eligibility.q_sent_reminder',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: null,
      blockMessage: '',
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
      simplifiedText: 'Are you filing this complaint with the help of a lawyer or legal representative?',
      simplifiedTextKey: 'eligibility.q_through_advocate_simple',
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
      simplifiedText: 'Have you already filed a complaint about this same issue with the Ombudsman and it is still under review?',
      simplifiedTextKey: 'eligibility.q_pending_ombudsman_simple',
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
      simplifiedText: 'Has the Ombudsman already reviewed and resolved this same complaint in the past?',
      simplifiedTextKey: 'eligibility.q_settled_ombudsman_simple',
    },
    {
      key: 'staffOfRE',
      question: 'Is the Complainant a staff of the RE and complaint involves employer-employee relationship?',
      translationKey: 'eligibility.q_staff_of_re',
      type: 'radio',
      options: [{ label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' }, { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' }],
      blockOn: 'yes',
      blockMessage: 'Complaints involving employer-employee relationship between the complainant and the Regulated Entity cannot be filed under the Integrated Ombudsman Scheme.',
      blockMessageKey: 'eligibility.block_staff_of_re',
      nonMaintainable: true,
      simplifiedText: 'Are you an employee of the bank/NBFC you are complaining against, and is your complaint about your job or employment?',
      simplifiedTextKey: 'eligibility.q_staff_of_re_simple',
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
    'Representative Authorization',
    'Declaration',
    'Review and Submit'
  ];

  declarationChecked = false;
  declaration2Checked = false;
  submitting = signal(false);
  referenceNumber = '';

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
    hasAccountWithRE: '',
    accountType: '',
    savingsAccountNumber: '',
    atmDebitCardNumber: '',
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
    { label: 'ATM/CDM/Debit card', value: 'ATM' },
    { label: 'Loans and Advances', value: 'LOAN' },
    { label: 'Mobile / Electronic Banking', value: 'MOBILE_BANKING' },
    { label: 'Notes and Coins', value: 'NOTES_COINS' },
    { label: 'Credit Card', value: 'CREDIT_CARD' },
    { label: 'Opening/Operation of Deposit accounts', value: 'DEPOSIT' },
    { label: 'Remittance and collection of instruments', value: 'REMITTANCE' },
    { label: 'Pension related', value: 'PENSION' },
    { label: 'Other products and services', value: 'OTHER' },
  ];

  accountTypes = [
    { label: 'Savings Account', value: 'savings', checked: false },
    { label: 'Loan Account', value: 'loan', checked: false },
    { label: 'ATM/Debit Card', value: 'atm_debit', checked: false },
    { label: 'Credit Card', value: 'credit_card', checked: false },
  ];

  stateDistrictMap: Record<string, string[]> = {
    'andhra-pradesh': ['Anantapur', 'Chittoor', 'East Godavari', 'Guntur', 'Krishna', 'Kurnool', 'Nellore', 'Prakasam', 'Srikakulam', 'Visakhapatnam', 'Vizianagaram', 'West Godavari', 'YSR Kadapa'],
    'assam': ['Baksa', 'Barpeta', 'Dibrugarh', 'Guwahati', 'Jorhat', 'Kamrup', 'Nagaon', 'Sivasagar', 'Sonitpur', 'Tinsukia'],
    'bihar': ['Araria', 'Bhagalpur', 'Gaya', 'Muzaffarpur', 'Nalanda', 'Patna', 'Purnia', 'Samastipur', 'Saran', 'Vaishali'],
    'delhi': ['Central Delhi', 'East Delhi', 'New Delhi', 'North Delhi', 'North East Delhi', 'North West Delhi', 'Shahdara', 'South Delhi', 'South East Delhi', 'South West Delhi', 'West Delhi'],
    'gujarat': ['Ahmedabad', 'Anand', 'Bharuch', 'Bhavnagar', 'Gandhinagar', 'Jamnagar', 'Junagadh', 'Kutch', 'Mehsana', 'Rajkot', 'Surat', 'Vadodara'],
    'karnataka': ['Bagalkot', 'Ballari', 'Bengaluru Rural', 'Bengaluru Urban', 'Belagavi', 'Dakshina Kannada', 'Dharwad', 'Hassan', 'Hubli', 'Mandya', 'Mysuru', 'Tumkur', 'Udupi'],
    'kerala': ['Alappuzha', 'Ernakulam', 'Idukki', 'Kannur', 'Kasaragod', 'Kollam', 'Kottayam', 'Kozhikode', 'Malappuram', 'Palakkad', 'Pathanamthitta', 'Thiruvananthapuram', 'Thrissur', 'Wayanad'],
    'madhya-pradesh': ['Bhopal', 'Gwalior', 'Indore', 'Jabalpur', 'Rewa', 'Sagar', 'Satna', 'Ujjain'],
    'maharashtra': ['Ahmednagar', 'Aurangabad', 'Kolhapur', 'Mumbai City', 'Mumbai Suburban', 'Nagpur', 'Nashik', 'Pune', 'Ratnagiri', 'Sangli', 'Satara', 'Solapur', 'Thane'],
    'punjab': ['Amritsar', 'Bathinda', 'Jalandhar', 'Ludhiana', 'Mohali', 'Patiala', 'Sangrur'],
    'rajasthan': ['Ajmer', 'Alwar', 'Bikaner', 'Jaipur', 'Jodhpur', 'Kota', 'Udaipur'],
    'tamil-nadu': ['Chennai', 'Coimbatore', 'Erode', 'Kancheepuram', 'Madurai', 'Salem', 'Thanjavur', 'Tiruchirappalli', 'Tirunelveli', 'Vellore'],
    'telangana': ['Adilabad', 'Hyderabad', 'Karimnagar', 'Khammam', 'Mahabubnagar', 'Medak', 'Nalgonda', 'Nizamabad', 'Rangareddy', 'Warangal'],
    'uttar-pradesh': ['Agra', 'Allahabad', 'Bareilly', 'Ghaziabad', 'Gorakhpur', 'Kanpur', 'Lucknow', 'Mathura', 'Meerut', 'Moradabad', 'Noida', 'Varanasi'],
    'west-bengal': ['Bankura', 'Darjeeling', 'Hooghly', 'Howrah', 'Kolkata', 'Malda', 'Medinipur', 'Murshidabad', 'Nadia', 'North 24 Parganas', 'South 24 Parganas'],
  };

  get entityDistricts(): string[] {
    const state = this.formData['entityState'];
    return state ? (this.stateDistrictMap[state] || []) : [];
  }

  onEntityStateChange() {
    this.formData['entityDistrict'] = '';
  }

  accountTypeDropdownOpen = false;

  toggleAccountTypeDropdown() {
    this.accountTypeDropdownOpen = !this.accountTypeDropdownOpen;
  }

  get selectedAccountTypes(): string {
    const selected = this.accountTypes.filter(a => a.checked);
    return selected.length ? selected.map(a => a.label).join(', ') : '';
  }

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

  // Pincode lookup
  pincodeLoading = false;
  complainantStates: string[] = [];
  complainantDistricts: string[] = [];

  onPincodeInput() {
    const value = this.formData['pincode'];
    if (value && value.length === 6 && /^\d{6}$/.test(value)) {
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
          }
        },
        error: () => {
          this.pincodeLoading = false;
        }
      });
    } else {
      this.formData['state'] = '';
      this.formData['district'] = '';
      this.complainantStates = [];
      this.complainantDistricts = [];
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

  // Eligibility file upload
  eligibilityFileName = '';

  onEligibilityFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      this.eligibilityFileName = input.files[0].name;
    }
  }

  // FR-G-013: Speech to text
  isRecording = signal(false);
  speechSupported = false;
  private recognition: any = null;

  ngOnInit() {
    setPageTitle('File a Complaint');
    this.eligibilityQuestions[0].options = this.banks.map(b => ({ label: b.name, value: String(b.id) }));
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

  ngOnDestroy() {
    this.stopAutoSave();
    this.stopRecording();
  }

  get sessionMinutes(): string {
    return this.publicAuth.getFormattedTime();
  }

  // ══════ ELIGIBILITY (FR-G-007 step 2) ══════
  get currentQuestion(): EligibilityQuestion {
    return this.eligibilityQuestions[this.eligibilityStep() - 1];
  }

  get totalEligibilitySteps(): number {
    return this.eligibilityQuestions.length;
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
    } else {
      this.eligibilityBlocked.set(false);
      this.eligibilityBlockMessage.set('');
    }
  }

  eligibilityFieldError = '';

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
    if (q.key === 'filedWithRE' && this.eligibilityAnswers['filedWithRE'] === 'yes') {
      if (!this.formData['bankComplaintDate']) {
        this.eligibilityFieldError = 'Complaint date with RE is required';
        return;
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

  validateCurrentStep(): boolean {
    this.validationErrors = {};
    const step = this.currentStep();

    if (step === 1) {
      if (!this.formData['firstName']?.trim()) this.validationErrors['name'] = 'First name is required';
      if (!this.formData['pincode'] || !/^\d{6}$/.test(this.formData['pincode'])) this.validationErrors['pincode'] = 'Valid 6-digit pincode is required';
      if (!this.formData['state']) this.validationErrors['state'] = 'Enter valid pincode to auto-fill state';
      if (this.formData['email'] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData['email'])) this.validationErrors['email'] = 'Invalid email format';
    } else if (step === 2) {
      if (!this.formData['isCreditCardComplaint']) this.validationErrors['isCreditCardComplaint'] = 'Please select Yes or No';
      if (!this.formData['entityState']) this.validationErrors['entityState'] = 'Entity state is required';
      if (!this.formData['entityDistrict']) this.validationErrors['entityDistrict'] = 'Entity district is required';
      if (!this.formData['entityBranch']?.trim()) this.validationErrors['entityBranch'] = 'Entity branch is required';
    } else if (step === 3) {
      if (!this.formData['complaintCategory']) this.validationErrors['complaintCategory'] = 'Category is required';
      if (!this.formData['complaintText']?.trim()) this.validationErrors['complaintText'] = 'Complaint description is required';
      if (!this.formData['disputeAmount']?.trim()) this.validationErrors['disputeAmount'] = 'Amount involved is required';
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

      const addRow = (label: string, value: string) => {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont('helvetica', 'bold');
        doc.text(`${label}:`, 25, y);
        doc.setFont('helvetica', 'normal');
        const lines = doc.splitTextToSize(value || 'N/A', pageWidth - 90);
        doc.text(lines, 80, y);
        y += lines.length * 5 + 3;
      };

      // Header
      doc.setFontSize(16);
      doc.setFont('helvetica', 'bold');
      doc.text('RESERVE BANK OF INDIA', pageWidth / 2, y, { align: 'center' });
      y += 8;
      doc.setFontSize(11);
      doc.setFont('helvetica', 'normal');
      doc.text('Integrated Ombudsman Scheme, 2026', pageWidth / 2, y, { align: 'center' });
      y += 12;

      // Title
      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('ACKNOWLEDGEMENT OF COMPLAINT', pageWidth / 2, y, { align: 'center' });
      y += 4;
      doc.setLineWidth(0.5);
      doc.line(40, y, pageWidth - 40, y);
      y += 12;

      // Reference
      doc.setFontSize(10);
      addRow('Reference Number', this.referenceNumber);
      addRow('Date of Filing', new Date().toLocaleDateString('en-IN'));
      y += 5;

      // Complainant Details
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.text('1. Complainant Details', 20, y);
      y += 8;
      doc.setFontSize(10);

      const fullName = [this.formData['firstName'], this.formData['middleName'], this.formData['lastName']].filter(Boolean).join(' ');
      addRow('Name', fullName);
      addRow('Category', this.formData['complainantCategory']);
      addRow('Age', this.formData['age']);
      addRow('Gender', this.formData['gender']);
      addRow('Email', this.formData['email']);
      addRow('Mobile', this.formData['phone']);
      addRow('Pincode', this.formData['pincode']);
      addRow('State', this.formData['state']);
      addRow('District', this.formData['district']);
      addRow('Address', this.formData['address']);
      y += 5;

      // Regulated Entity Details
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.text('2. Regulated Entity Details', 20, y);
      y += 8;
      doc.setFontSize(10);

      addRow('Entity Name', this.getSelectedBankName());
      addRow('Complaint Date with RE', this.formData['bankComplaintDate']);
      addRow('Complaint Ref (RE)', this.formData['bankComplaintRef']);
      addRow('Dispute Date', this.formData['disputeDate']);
      addRow('Reply from Entity', this.formData['receivedReplyFromEntity']);
      if (this.formData['receivedReplyFromEntity'] === 'yes') {
        addRow('Reply Date', this.formData['replyDate']);
      }
      addRow('Wallet Complaint', this.formData['isWalletComplaint']);
      if (this.formData['isWalletComplaint'] === 'yes') {
        addRow('Wallet Name', this.formData['walletName']);
        addRow('Transaction Ref', this.formData['transactionRefNumber']);
      }
      addRow('Business Correspondent', this.formData['isBusinessCorrespondent']);
      y += 5;

      // Complaint Details
      if (y > 250) { doc.addPage(); y = 20; }
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.text('3. Complaint Details', 20, y);
      y += 8;
      doc.setFontSize(10);

      addRow('Category', this.formData['complaintCategory']);
      addRow('Account with RE', this.formData['hasAccountWithRE']);
      addRow('Account Type', this.formData['accountType']);
      addRow('Savings A/C No.', this.formData['savingsAccountNumber']);
      addRow('Loan A/C No.', this.formData['loanAccountNumber']);
      addRow('ATM/Debit Card No.', this.formData['atmDebitCardNumber']);
      addRow('Credit Card No.', this.formData['cardNumber']);
      addRow('Dispute Amount', this.formData['disputeAmount'] ? `Rs. ${this.formData['disputeAmount']}` : '');
      addRow('Compensation Sought', this.formData['compensationSought'] ? `Rs. ${this.formData['compensationSought']}` : '');
      addRow('Relief Sought', this.formData['reliefSought']);

      if (y > 250) { doc.addPage(); y = 20; }
      doc.setFont('helvetica', 'bold');
      doc.text('Facts of Complaint:', 25, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      const factLines = doc.splitTextToSize(this.formData['complaintText'] || 'N/A', pageWidth - 40);
      doc.text(factLines, 25, y);
      y += factLines.length * 5 + 8;

      // Representative
      if (y > 250) { doc.addPage(); y = 20; }
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.text('4. Representative Authorization', 20, y);
      y += 8;
      doc.setFontSize(10);

      addRow('Through Advocate', this.formData['throughAdvocate']);
      addRow('Authorize Rep', this.formData['authorizeRepresentative']);
      if (this.formData['authorizeRepresentative'] === 'yes') {
        addRow('Rep Name', this.formData['repName']);
        addRow('Rep Phone', this.formData['repPhone']);
        addRow('Rep Email', this.formData['repEmail']);
        addRow('Rep Address', this.formData['repAddress']);
      }
      y += 5;

      // Attachments
      if (this.attachmentPreviews.length > 0) {
        addRow('Attachments', this.attachmentPreviews.map(f => f.name).join(', '));
      }
      y += 10;

      // Footer
      if (y > 250) { doc.addPage(); y = 20; }
      const trackText = `Your complaint has been registered and will be processed as per the provisions of the Scheme. You may track the status using your reference number: ${this.referenceNumber}`;
      const trackLines = doc.splitTextToSize(trackText, pageWidth - 40);
      doc.text(trackLines, 20, y);
      y += trackLines.length * 5 + 8;

      doc.setFont('helvetica', 'bold');
      doc.text('Expected Resolution Timeline:', 20, y);
      doc.setFont('helvetica', 'normal');
      doc.text(' Within 30 days from the date of receipt.', 72, y);
      y += 14;

      doc.setFont('helvetica', 'bold');
      doc.text('For any queries, please contact:', 20, y);
      y += 7;
      doc.setFont('helvetica', 'normal');
      doc.text('Toll-free: 14448', 25, y);
      y += 6;
      doc.text('Website: https://cms.rbi.org.in', 25, y);
      y += 14;

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
      y += 12;

      // FR-G-028: Digital signature indicator
      doc.setDrawColor(0, 100, 0);
      doc.setFillColor(240, 255, 240);
      doc.roundedRect(20, y, pageWidth - 40, 18, 2, 2, 'FD');
      doc.setTextColor(0, 100, 0);
      doc.setFontSize(9);
      doc.setFont('helvetica', 'bold');
      doc.text('DIGITALLY SIGNED', 25, y + 7);
      doc.setFont('helvetica', 'normal');
      doc.text(`Signed by: RBI CMS Digital Certificate Authority | Date: ${new Date().toISOString().slice(0, 19)}Z`, 25, y + 13);
      doc.setTextColor(0);

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

  private readonly DRAFT_VERSION = 3;

  // FR-G-008: Save Draft
  saveDraft() {
    const draft = { version: this.DRAFT_VERSION, formData: this.formData, eligibilityAnswers: this.eligibilityAnswers, currentStep: this.currentStep(), phase: this.phase() };
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
        if (draft.formData) {
          const validKeys = Object.keys(this.formData);
          for (const key of validKeys) {
            if (draft.formData[key] !== undefined) {
              this.formData[key] = draft.formData[key];
            }
          }
        }
        if (draft.eligibilityAnswers) this.eligibilityAnswers = draft.eligibilityAnswers;
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

    const payload = {
      filingType: 'ONLINE',
      category: this.formData['complaintCategory'] || 'GENERAL',
      complainantName: [this.formData['firstName'], this.formData['middleName'], this.formData['lastName']].filter(Boolean).join(' '),
      complainantEmail: this.formData['email'],
      complainantPhone: this.formData['phone'],
      complainantAddress: this.formData['address'],
      entityName: this.getSelectedBankName(),
      entityType: 'BANK',
      subject: this.formData['subCategory1'] || this.formData['complaintCategory'] || 'General Complaint',
      description: this.formData['complaintText'],
      amountInvolved: this.formData['disputeAmount'] ? parseFloat(this.formData['disputeAmount']) : undefined,
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
