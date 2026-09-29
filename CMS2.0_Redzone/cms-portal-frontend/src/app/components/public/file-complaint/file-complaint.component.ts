import { Component, OnInit, OnDestroy, inject, signal, HostListener, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { ComplaintService } from '../../../services/complaint.service';
import { PublicAuthService } from '../../../services/public-auth.service';
import { TranslationService } from '../../../services/translation.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { validateFile, validateFileSet } from '../../../utils/file-validator';
import { announceToScreenReader, setPageTitle } from '../../../utils/accessibility';
import { lookupPincode } from '../../../utils/pincode-data';
import { environment } from '../../../../environments/environment';
import { UploadLimitsService } from '../../../services/upload-limits.service';

interface EligibilityQuestion {
  key: string;
  question: string;
  translationKey?: string;
  type: 'select' | 'radio';
  options: { label: string; value: string; translationKey?: string }[];
  blockOn: string | null;
  blockMessage: string;
  blockMessageKey?: string;
  /** Scheme clause the block is issued under, interpolated into the message as {{clause}}. */
  clauseReference?: string;
  nonMaintainable?: boolean;
  simplifiedText?: string;
  simplifiedTextKey?: string;
  /** ALL | RBIO | CEPC | NON_CEPC — which selected-entity department the question applies to. */
  applicableEntityType?: string;
  /** Rendered inside its parent question rather than as a step of its own. */
  inlineSubQuestion?: boolean;
}

const YES_NO_OPTIONS = [
  { label: 'Yes', value: 'yes', translationKey: 'eligibility.opt_yes' },
  { label: 'No', value: 'no', translationKey: 'eligibility.opt_no' },
];

/**
 * Sub-answers that exist only while their parent gate holds `revealOn`. When the gate flips away the
 * sub-answer is no longer on screen, so leaving it in eligibilityAnswers would persist a maintainability
 * response the citizen can neither see nor correct — and it reaches the server on the next draft save.
 *
 * BRD row 15 (isComplainantSelf) has no ELIGIBILITY_QUESTION_MASTER row yet, so the linkage is declared
 * here rather than read from the master's inlineSubQuestion column.
 */
const DEPENDENT_SUB_ANSWERS: { parent: string; revealOn: string; dependents: string[] }[] = [
  { parent: 'employeeOfRE', revealOn: 'yes', dependents: ['employerRelationship'] },
  { parent: 'throughAdvocateEligibility', revealOn: 'yes', dependents: ['isComplainantSelf'] },
];

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
  // Public: the template renders the configured limit in its upload hint.
  uploadLimits = inject(UploadLimitsService);

  /** The configured limits in the shape file-validator expects, so no size is compiled in. */
  private fileLimits() {
    return {
      maxFileSizeMb: this.uploadLimits.maxFileSizeMb(),
      maxTotalSizeMb: this.uploadLimits.maxTotalSizeMb(),
      maxFileCount: this.uploadLimits.maxFileCount(),
    };
  }
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
  /**
   * Scheme clause for the active block, from ELIGIBILITY_QUESTION_MASTER.clauseReference. The clause is
   * interpolated into the block message as {{clause}} rather than being baked into the translated prose,
   * so correcting a citation is one DB UPDATE instead of an edit in each of the ten locales.
   */
  eligibilityBlockClause = signal('');
  /**
   * RE response window for the active block, interpolated into the message as {{days}}. The number
   * used to be baked into the translated prose in all ten locales, so raising
   * cms.mre.re-window-days changed the rule the wizard enforced while still telling the citizen
   * "30 days" — the same defect the clause citations had before V15.
   */
  eligibilityBlockDays = signal('');
  /** dd/mm/yyyy on which the RE window opens, so a citizen told to wait knows when to come back. */
  reWindowOpenDate = signal('');
  nonMaintainableCaseId = '';
  showSimplified = signal(false);

  banks: { id: number; name: string; department?: string; entityType?: string }[] = [];

  // FR-G-007 step 1 + D14: maintainability questions come from ELIGIBILITY_QUESTION_MASTER via
  // GET /api/v1/eligibility/questions. Hardcoding them here meant a Scheme amendment needed a
  // frontend release, and the text had already drifted to a wrong Scheme year.
  eligibilityQuestions: EligibilityQuestion[] = [];
  questionsLoadFailed = signal(false);
  /** Scheme cited in the non-maintainable closure letter; comes from cms.eligibility.scheme-name. */
  schemeName = '';

  // UST11/UST12: Scheme timing rules come from GET /api/v1/eligibility/questions (cms.mre.* and
  // cms.eligibility.*). These are only the values used before that response arrives.
  reWindowDays = 30;
  grievanceFilingWindowDays = 310;
  filingDeadlineDays = 90;

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

  // FR-G-020: Duplicate detection
  showDuplicatePopup = signal(false);
  duplicateMessage = '';
  duplicateCheckDone = false;
  checkingDuplicate = signal(false);

  // FR-G-008: Draft. draftSaved means the server accepted it — the sessionStorage copy alone is
  // not a save, because it dies with the tab.
  draftSaved = signal(false);
  draftSaveFailed = signal(false);

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


  /**
   * `label` is the English name, `labelKey` the translation key for display.
   *
   * <p>Both are kept because the label CANNOT be resolved when the list is fetched: the category
   * request and the locale bundle load concurrently, so resolving once at fetch time renders English
   * whenever the categories win that race, and nothing re-renders when the bundle later arrives or the
   * user switches language. The key is therefore resolved in the template on every change detection.
   */
  categories: { label: string; value: string; labelKey?: string }[] = [];

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

  /**
   * A LOCAL calendar date as yyyy-MM-dd. Date.toISOString() converts to UTC first, so in IST
   * (UTC+5:30) a local midnight becomes 18:30 the PREVIOUS day and slicing the string yields
   * yesterday. That is a one-day error in every date this component shows or bounds, and here it is
   * a one-day error in a statutory window: the RE-window-opens notice told the citizen the window
   * opened a day before it actually did.
   */
  private toLocalIso(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  /** Rejects 31/02, 30/02 and 29/02 in non-leap years, which a range check on d/m/y lets through. */
  private isRealCalendarDate(year: number, month: number, day: number): boolean {
    if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1) return false;
    const d = new Date(year, month - 1, day);
    return d.getFullYear() === year && d.getMonth() === month - 1 && d.getDate() === day;
  }

  /**
   * A malformed date leaves formData empty, so without this the citizen would be told the date is
   * "required" for a field they visibly filled in. Kept per field and outlives nextEligibility()'s
   * error reset so the format message wins over the required message.
   */
  dateFormatError: Record<string, string> = {};

  /** UST15/19/22: routes a date error to the field's own error slot so it renders next to the input. */
  private setDateError(field: string, message: string) {
    this.dateFormatError[field] = message;
    if (field === 'replyDate') this.replyDateError = message;
    else if (field === 'reminderDate') this.reminderDateError = message;
    else this.eligibilityFieldError = message;
  }

  private revalidateDate(field: string) {
    if (field === 'bankComplaintDate') this.onBankComplaintDateChange();
    else if (field === 'reminderDate') this.onReminderDateChange();
    else if (field === 'replyDate') this.onReplyDateChange();
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

    this.formData[field] = '';
    this.setDateError(field, '');

    if (val.length === 8) {
      const day = parseInt(val.substring(0, 2), 10);
      const month = parseInt(val.substring(2, 4), 10);
      const year = parseInt(val.substring(4, 8), 10);
      if (this.isRealCalendarDate(year, month, day)) {
        this.formData[field] = `${year}-${val.substring(2, 4)}-${val.substring(0, 2)}`;
        this.revalidateDate(field);
      } else {
        // Silently dropping the value left dateDisplay showing "31/02/2026" with no explanation and
        // a downstream "date is required" error the citizen could not act on.
        this.setDateError(field, this.t('validation.date_invalid', 'Enter a valid date in dd/mm/yyyy format'));
      }
    } else if (val.length > 0) {
      this.setDateError(field, this.t('validation.date_incomplete', 'Enter the full date as dd/mm/yyyy'));
    }
  }

  /** Translation with an English fallback, so a missing key never renders as a raw code to a citizen. */
  private t(key: string, fallback: string): string {
    const value = this.translationService.translate(key);
    return value === key ? fallback : value;
  }

  /**
   * The block message a citizen is shown, translated and with the Scheme clause interpolated.
   *
   * Single resolver for all three surfaces (the block note, the non-maintainable card and the PDF
   * closure letter) so they cannot drift apart. The clause comes from the master rather than from the
   * prose, and interpolation is a global regex rather than TranslationService's params argument:
   * translate() uses String.replace with a string pattern, which substitutes only the FIRST {{clause}}.
   *
   * If no clause is known the placeholder is removed rather than left visible — showing a citizen a
   * literal "{{clause}}" in a legal determination is worse than omitting the citation.
   */
  resolvedBlockMessage(): string {
    const key = this.eligibilityBlockMessageKey();
    const translated = key ? this.translationService.translate(key) : '';
    const text = (translated && translated !== key) ? translated : this.eligibilityBlockMessage();
    return this.interpolateDays(this.interpolateClause(text, this.eligibilityBlockClause()));
  }

  private interpolateClause(text: string, clause: string): string {
    if (!text.includes('{{clause}}')) return text;
    if (clause) return text.replace(/\{\{clause\}\}/g, clause);
    // Collapse "under clause  of the Scheme" style gaps left by removing the placeholder.
    return text.replace(/\s*\{\{clause\}\}/g, '').replace(/\s{2,}/g, ' ');
  }

  /**
   * Fills {{days}} from the configured RE window and {{windowOpenDate}} from the filing date.
   * Global regex for the same reason interpolateClause uses one: the prose mentions the window
   * twice, and TranslationService.translate() substitutes only the first occurrence.
   */
  private interpolateDays(text: string): string {
    const days = this.eligibilityBlockDays();
    return days ? text.replace(/\{\{days\}\}/g, days) : text;
  }

  /**
   * UST11 scenario 3: a citizen told to wait must be told until WHEN. Rendered as its own translated
   * line rather than spliced into the block prose, so adding it did not require re-translating the
   * block message in all ten locales.
   */
  reWindowOpensNotice(): string {
    const date = this.reWindowOpenDate();
    if (!date) return '';
    const template = this.t('eligibility.re_window_opens_on',
      'You may file your complaint with the RBI Ombudsman on or after {{date}}.');
    return template.replace(/\{\{date\}\}/g, date);
  }

  /** Raises a block from a master row, carrying its clause so the message can cite it. */
  private applyBlockFrom(q: EligibilityQuestion) {
    this.eligibilityBlocked.set(true);
    this.eligibilityBlockMessage.set(q.blockMessage);
    this.eligibilityBlockMessageKey.set(q.blockMessageKey || '');
    this.eligibilityBlockClause.set(q.clauseReference || '');
  }

  private clearBlock() {
    this.eligibilityBlocked.set(false);
    this.eligibilityBlockMessage.set('');
    this.eligibilityBlockMessageKey.set('');
    this.eligibilityBlockClause.set('');
    this.eligibilityBlockDays.set('');
    this.reWindowOpenDate.set('');
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
      this.setDateError(field, '');
      this.revalidateDate(field);
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

  /**
   * True when the grievance categories could not be loaded from the master table.
   *
   * <p>There is deliberately NO hardcoded category list any more. This flag drives a blocking message
   * with a retry instead: the category steers routing and maintainability, so filing a complaint under a
   * guessed category is worse for the complainant than being asked to try again.
   */
  categoriesUnavailable = false;

  retryLoadCategories() {
    this.loadMasterData();
  }

  private loadAccountTypesFromLocal() {
    this.http.get<any[]>('/assets/masters/account-types.json').subscribe({
      next: (data) => {
        this.accountTypes = (data ?? []).map(a => ({ label: a.label || a.name, value: a.value || a.code, checked: false }));
      },
      error: () => {}
    });
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

  /**
   * UST16/20/23: the eligibility uploads previously checked size only, so a renamed .exe or an
   * oversized-name traversal attempt passed. The `accept` attribute is a picker hint, not a control.
   */
  private readonly ELIGIBILITY_UPLOAD_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.png'];

  private acceptEligibilityFile(input: HTMLInputElement): File | string {
    const file = input.files![0];
    const result = validateFile(file, this.fileLimits());
    if (!result.valid) {
      input.value = '';
      return result.error!;
    }
    const extension = '.' + file.name.split('.').pop()!.toLowerCase();
    if (!this.ELIGIBILITY_UPLOAD_EXTENSIONS.includes(extension)) {
      input.value = '';
      return this.t('validation.file_type_not_allowed', 'Only PDF, JPG and PNG files are allowed');
    }
    return file;
  }

  onComplaintFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const result = this.acceptEligibilityFile(input);
    if (typeof result === 'string') { this.eligibilityFileError = result; return; }
    this.complaintFileWithRE = result;
    this.complaintFileWithREName = result.name;
    this.eligibilityFileError = '';
  }

  onReminderFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const result = this.acceptEligibilityFile(input);
    if (typeof result === 'string') { this.reminderFileError = result; return; }
    this.reminderFile = result;
    this.reminderFileName = result.name;
    this.reminderFileError = '';
  }

  onReplyFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const result = this.acceptEligibilityFile(input);
    if (typeof result === 'string') { this.replyFileError = result; return; }
    this.replyFile = result;
    this.replyFileName = result.name;
    this.replyFileError = '';
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

  repFileError = '';

  onRepFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    const result = validateFile(file, this.fileLimits());
    if (!result.valid) {
      input.value = '';
      this.repFileError = result.error!;
      return;
    }
    this.repFile = file;
    this.repFileName = file.name;
    this.repFileError = '';
  }

  onRepFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    const file = event.dataTransfer.files[0];
    const result = validateFile(file, this.fileLimits());
    if (!result.valid) { this.repFileError = result.error!; return; }
    this.repFile = file;
    this.repFileName = file.name;
    this.repFileError = '';
  }

  removeRepFile() {
    this.repFile = null;
    this.repFileName = '';
    this.repFileError = '';
  }

  // FR-G-013: Speech to text
  isRecording = signal(false);
  speechSupported = false;
  private recognition: any = null;

  ngOnInit() {
    setPageTitle('File a Complaint');
    this.loadEligibilityQuestions();
    this.loadRegulatedEntities();
    this.loadMasterData();
    this.speechSupported = !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
    this.formData['phone'] = this.publicAuth.userIdentifier() || '';

    const draftId = this.route.snapshot.queryParamMap.get('draftId');
    const resume = this.route.snapshot.queryParamMap.get('resume');
    if (draftId) {
      this.loadDraftFromServer(draftId);
    } else if (resume === 'true') {
      this.loadDraft();
    } else {
      sessionStorage.removeItem('cms_complaint_draft');
      sessionStorage.removeItem('cms_draft_id');
      sessionStorage.removeItem('cms_draft_saved_at');
    }

    this.startAutoSave();
  }

  /**
   * Loads the grievance categories the complainant chooses from.
   *
   * <p>SOURCE IS `/api/categories` (COMPLAINT_CATEGORIES) — the authoritative master holding the ten real
   * RBI grievance categories. This previously called `/api/v1/masters/categories` (CATEGORY_MASTER),
   * which contains nothing but inactive E2E test probes, so the endpoint returned an empty array and the
   * wizard quietly substituted a compiled-in list. The citizen was choosing from hardcoded constants that
   * no master table governed.
   *
   * <p>FAILS CLOSED: no hardcoded fallback. The category drives routing and maintainability, so if the
   * master cannot be reached the step is blocked with a retry rather than filed under a guess.
   *
   * <p>The selected value stays the category NAME, not the id: the submit payload sends `category` as a
   * string and also derives `subject` from it.
   */
  private loadMasterData() {
    this.categoriesUnavailable = false;
    this.http.get<any>(`${environment.apiBaseUrl}/api/categories`).subscribe({
      next: (res) => {
        const data = res?.data ?? res ?? [];
        const categoryMap: Record<string, { label: string; value: string }[]> = {};
        const categorySet = new Map<string, { label: string; labelKey?: string }>();
        data.forEach((item: any) => {
          // COMPLAINT_CATEGORIES exposes `name`; CATEGORY_MASTER used `categoryName`.
          const catValue = item.name || item.categoryName || item.value;
          const catLabel = item.name || item.categoryLabel || item.label || catValue;
          if (!catValue) return;
          if (!categorySet.has(catValue)) {
            categorySet.set(catValue, { label: catLabel, labelKey: item.labelKey });
          }
          if (item.subCategory) {
            if (!categoryMap[catValue]) categoryMap[catValue] = [];
            categoryMap[catValue].push({ label: item.subCategory, value: item.subCategoryValue || item.subCategory });
          }
        });
        this.categories = Array.from(categorySet.entries())
          .map(([value, meta]) => ({ label: meta.label, value, labelKey: meta.labelKey }));
        this.categoriesUnavailable = this.categories.length === 0;
        this.subCategories = categoryMap;
      },
      error: () => {
        this.categories = [];
        this.categoriesUnavailable = true;
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
        if (this.accountTypes.length === 0) this.loadAccountTypesFromLocal();
      },
      error: () => this.loadAccountTypesFromLocal()
    });
  }

  /**
   * Localises a category for DISPLAY while the submitted value stays the English name.
   *
   * <p>`value` is what the payload sends, what `subject` is derived from and what routing matches, so it
   * cannot be translated. Only the label can be.
   *
   * <p>Called from the template rather than computed when the list is fetched, because the categories
   * and the locale bundle load concurrently: resolving once at fetch time rendered English whenever the
   * categories arrived first, and never corrected itself when the user switched language.
   *
   * <p>The `!== labelKey` check matters: TranslationService.translate returns the KEY ITSELF on a miss,
   * so without it a locale lacking the key would show a citizen `category.atm_debit_card` in a
   * dropdown. Falling back to the English name is the right failure mode for a legally significant
   * choice.
   */
  categoryLabel(category: { label: string; value: string; labelKey?: string }): string {
    if (!category.labelKey) return category.label;
    const localised = this.translationService.translate(category.labelKey);
    return localised && localised !== category.labelKey ? localised : category.label;
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
        sessionStorage.setItem('cms_draft_id', draftId);
      },
      error: () => {
        this.loadDraft();
      }
    });
  }

  /**
   * D14: the questions, their block rules and their Scheme clause references are operator-maintained
   * master data. There is no hardcoded fallback on purpose — serving a stale copy of a legal
   * maintainability rule is worse than telling the citizen to come back, because a wrongly issued
   * non-maintainable closure denies them the Scheme.
   */
  private loadEligibilityQuestions() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/eligibility/questions`).subscribe({
      next: (res) => {
        const rows = res?.data ?? [];
        if (!rows.length) {
          this.questionsLoadFailed.set(true);
          return;
        }
        this.schemeName = res?.schemeName || '';
        if (res?.reWindowDays) this.reWindowDays = res.reWindowDays;
        if (res?.grievanceFilingWindowDays) this.grievanceFilingWindowDays = res.grievanceFilingWindowDays;
        if (res?.filingDeadlineDays) this.filingDeadlineDays = res.filingDeadlineDays;
        this.eligibilityQuestions = rows.map((r: any) => ({
          key: r.questionKey,
          question: r.questionText,
          translationKey: r.translationKey || undefined,
          type: r.questionType === 'select' ? 'select' : 'radio',
          options: r.questionType === 'select' ? this.entityOptions : YES_NO_OPTIONS,
          blockOn: r.blockOn || null,
          blockMessage: r.blockMessage || '',
          blockMessageKey: r.blockMessageKey || undefined,
          clauseReference: r.clauseReference || undefined,
          nonMaintainable: !!r.nonMaintainable,
          simplifiedText: r.simplifiedText || undefined,
          simplifiedTextKey: r.simplifiedTextKey || undefined,
          applicableEntityType: r.applicableEntityType || 'ALL',
          inlineSubQuestion: !!r.inlineSubQuestion,
        }));
        this.questionsLoadFailed.set(false);
      },
      error: () => {
        this.eligibilityQuestions = [];
        this.questionsLoadFailed.set(true);
      }
    });
  }

  retryLoadQuestions() {
    this.questionsLoadFailed.set(false);
    this.loadEligibilityQuestions();
  }

  /**
   * The entity list is real master data served by GET /api/v1/routing/entities/list. A failed fetch
   * used to be swallowed into setEntityOptions([]) — the citizen got a dropdown containing only the
   * disabled "Select Regulated Entity Name" placeholder and nothing saying anything had gone wrong,
   * so the screen looked like "RBI regulates no entities". Now the failure is shown and retryable.
   */
  entitiesLoadFailed = signal(false);

  retryLoadEntities() {
    this.entitiesLoadFailed.set(false);
    this.loadRegulatedEntities();
  }

  private loadRegulatedEntities() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`).subscribe({
      next: (res) => {
        const entities = res?.data ?? res ?? [];
        this.entitiesLoadFailed.set(!Array.isArray(entities) || entities.length === 0);
        this.banks = entities.map((e: any) => ({
          id: e.id,
          name: e.name,
          department: e.department || 'RBIO',
          entityType: e.entityType
        }));
        this.banks.sort((a, b) => a.name.localeCompare(b.name));
        this.setEntityOptions(this.banks.map(b => ({ label: b.name, value: String(b.id) })));
        this.filterEntities();
      },
      error: () => {
        this.setEntityOptions([]);
        this.filteredEntityOptions = [];
        this.entitiesLoadFailed.set(true);
      }
    });
  }

  /**
   * The entity list and the question master load in parallel, so the options are held here and
   * pushed onto the select question whichever request settles last.
   */
  private entityOptions: { label: string; value: string }[] = [];

  private setEntityOptions(options: { label: string; value: string }[]) {
    this.entityOptions = options;
    const select = this.eligibilityQuestions.find(q => q.type === 'select');
    if (select) select.options = options;
  }

  ngOnDestroy() {
    if (this.phase() === 'form' || this.phase() === 'eligibility') {
      this.saveDraft();
    }
    this.stopAutoSave();
    this.stopRecording();
  }

  get sessionMinutes(): string {
    return this.publicAuth.getFormattedTime();
  }

  // ══════ ELIGIBILITY (FR-G-007 step 2) ══════
  // Undefined until the question master has loaded; the template renders a retry notice instead.
  get currentQuestion(): EligibilityQuestion | undefined {
    const visible = this.visibleEligibilityQuestions;
    if (!visible.length) return undefined;
    const idx = Math.min(this.eligibilityStep() - 1, visible.length - 1);
    return visible[idx];
  }

  get currentQuestionKey(): string {
    return this.currentQuestion?.key ?? '';
  }

  /** 1-based wizard step showing the given question, or undefined when it is not visible. */
  private stepOfQuestion(key: string): number | undefined {
    const idx = this.visibleEligibilityQuestions.findIndex(q => q.key === key);
    return idx < 0 ? undefined : idx + 1;
  }

  get currentQuestionSimplifiedText(): string {
    const q = this.currentQuestion;
    if (!q?.simplifiedText) return '';
    if (!q.simplifiedTextKey) return q.simplifiedText;
    const translated = this.translationService.translate(q.simplifiedTextKey);
    return translated === q.simplifiedTextKey ? q.simplifiedText : translated;
  }

  get totalEligibilitySteps(): number {
    return this.visibleEligibilityQuestions.length;
  }

  get selectedEntityName(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const opt = this.entityOptions.find(o => o.value === val);
    return opt?.label ?? 'the Regulated Entity';
  }

  get currentQuestionText(): string {
    const q = this.currentQuestion;
    if (!q) return '';
    const translated = q.translationKey
      ? this.translationService.translate(q.translationKey)
      : q.question;
    const text = (translated !== q.translationKey) ? translated : q.question;
    return text.replace(/<RE Name>/g, this.selectedEntityName).replace(/\{\{reName\}\}/g, this.selectedEntityName);
  }

  /**
   * Drops sub-answers whose parent gate no longer reveals them, so a withdrawn response cannot survive
   * into the review summary, the session draft or the server draft.
   */
  private clearOrphanedSubAnswers(parentKey: string, parentValue: string) {
    for (const link of DEPENDENT_SUB_ANSWERS) {
      if (link.parent !== parentKey || parentValue === link.revealOn) continue;
      for (const dependent of link.dependents) {
        delete this.eligibilityAnswers[dependent];
        // formData declares isComplainantSelf up front, so reset rather than remove to keep its shape.
        if (dependent in this.formData) this.formData[dependent] = '';
      }
      // The orphaned answer may have been the one that issued a closure id.
      this.nonMaintainableCaseId = '';
    }
  }

  /**
   * Text for an inline sub-question, resolved from its ELIGIBILITY_QUESTION_MASTER row and translated.
   * Returns '' when the master row is absent so the template can hide the sub-question rather than
   * render an untranslated English literal.
   */
  subQuestionText(key: string): string {
    const q = this.eligibilityQuestions.find(x => x.key === key);
    if (!q) return '';
    const translated = q.translationKey ? this.translationService.translate(q.translationKey) : q.question;
    const text = (q.translationKey && translated !== q.translationKey) ? translated : q.question;
    return text.replace(/<RE Name>/g, this.selectedEntityName).replace(/\{\{reName\}\}/g, this.selectedEntityName);
  }

  hasSubQuestion(key: string): boolean {
    return this.eligibilityQuestions.some(x => x.key === key);
  }

  selectEligibilityAnswer(value: string) {
    const q = this.currentQuestion;
    if (!q) return;
    this.eligibilityAnswers[q.key] = value;
    this.clearOrphanedSubAnswers(q.key, value);
    this.mandatoryResponseError.set('');
    if (q.blockOn && value === q.blockOn) {
      this.applyBlockFrom(q);
    } else {
      this.clearBlock();
    }

    if (q.key === 'receivedReply' && value === 'no') {
      this.applyReWindowBlock();
    }
  }

  /** Whole days elapsed since an ISO date, counted from midnight so a time-of-day never shifts it. */
  private daysSince(iso: string): number {
    const from = new Date(iso);
    from.setHours(0, 0, 0, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.floor((today.getTime() - from.getTime()) / (1000 * 60 * 60 * 24));
  }

  /**
   * UST11: the RE is entitled to reWindowDays to respond. On day 30 exactly the window has elapsed,
   * so the complaint is maintainable — the previous `<= 30` wrongly closed complaints a day early.
   * Returns true when the citizen is blocked.
   */
  private applyReWindowBlock(): boolean {
    const filedDate = this.formData['bankComplaintDate'];
    if (!filedDate) return false;
    if (this.daysSince(filedDate) >= this.reWindowDays) return false;

    const opens = new Date(filedDate);
    opens.setHours(0, 0, 0, 0);
    opens.setDate(opens.getDate() + this.reWindowDays);
    this.reWindowOpenDate.set(this.isoToDisplay(this.toLocalIso(opens)));
    this.eligibilityBlockDays.set(String(this.reWindowDays));

    this.eligibilityBlocked.set(true);
    this.eligibilityBlockMessage.set(
      `As the Regulated Entity has not yet been given {{days}} days to respond to your ` +
      `complaint, your complaint cannot be registered at this time. Please wait until ` +
      `{{days}} days have elapsed from the date of filing your complaint with the ` +
      `Regulated Entity.`
    );
    this.eligibilityBlockMessageKey.set('eligibility.block_less_than_30_days');
    // Sourced from the master row that owns this rule rather than from a literal in this method.
    this.eligibilityBlockClause.set(
      this.eligibilityQuestions.find(q => q.key === 'receivedReply')?.clauseReference || '');
    this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
    return true;
  }

  /**
   * UST12: the statutory filing window. Returns true when the citizen is BLOCKED.
   *
   * Business ruling — the window REFUSES the filing rather than merely warning about it, and which
   * window applies depends on whether the RE replied:
   *
   *   RE replied      → grievanceFilingWindowDays from the RE COMPLAINT date. Past it the complaint is
   *                     refused; the unanswered RE complaint is auto-closed by the system instead.
   *   RE did not reply→ filingDeadlineDays from the REPLY date. The RE complaint date stops mattering
   *                     once a reply exists — a reply on day 300 still leaves a full 90 days — which is
   *                     why this cannot be collapsed into a single window.
   *
   * Both bounds are INCLUSIVE, matching the server guard in ComplaintApiV1Controller.filingWindowRefusal
   * so the two layers cannot disagree about the boundary day. The server repeats this check because a
   * browser-only block is bypassable.
   */
  private applyFilingWindowBlock(): boolean {
    const replied = this.eligibilityAnswers['receivedReply'] === 'yes';
    const replyDate = this.formData['replyDate'];
    const filedDate = this.formData['bankComplaintDate'];

    const from = replied && replyDate ? replyDate : filedDate;
    if (!from) return false;
    const limit = replied && replyDate ? this.filingDeadlineDays : this.grievanceFilingWindowDays;
    if (this.daysSince(from) <= limit) return false;

    // {{days}} is interpolated from the SERVED window, never from a literal in the prose. The same
    // defect V102 fixed for the RE window was present here: the translated message baked "310" into all
    // ten locales (Bengali in Bengali numerals, ৩১০), so raising the configured window changed the rule
    // the wizard ENFORCED while the citizen was still told it was 310 days. V107 rewrites those rows to
    // carry {{days}}.
    this.eligibilityBlockDays.set(String(limit));
    this.eligibilityBlocked.set(true);
    this.eligibilityBlockMessage.set(
      replied && replyDate
        ? `Complaint filing period has expired. A complaint must be filed within {{days}} days of ` +
          `the Regulated Entity's reply.`
        : `Complaint filing period has expired. A complaint must be filed within {{days}} days of ` +
          `your complaint to the Regulated Entity.`
    );
    this.eligibilityBlockMessageKey.set(
      replied && replyDate ? 'eligibility.block_post_reply_window' : 'eligibility.block_filing_window');
    this.eligibilityBlockClause.set(
      this.eligibilityQuestions.find(q => q.key === 'receivedReply')?.clauseReference || '');
    this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
    return true;
  }

  /**
   * UST13 S3: filter the entity list by NAME or by ENTITY TYPE.
   *
   * Deliberately client-side over the already-loaded list. The whole master is ~145 rows and is
   * fetched once by loadRegulatedEntities(), so a per-keystroke round trip would buy nothing and
   * would make the citizen's typing depend on the network. It also means the search term never
   * reaches a query: there is no injection surface here at all. (The server-side
   * /routing/entities/list?search= is parameterised via Spring Data @Param, so the other caller is
   * safe too.)
   *
   * Case-insensitive on both sides, and the type is matched as well as the name so that typing
   * "NBFC" narrows to NBFCs. No slice(): a 50-row cap silently hid matches, so a citizen whose bank
   * sorted 51st was shown "no results" for a name that IS on the list.
   */
  filterEntities() {
    const term = this.entitySearchText.toLowerCase().trim();
    const all = this.entityOptions.map(o => ({
      ...o,
      entityType: this.banks.find(b => String(b.id) === o.value)?.entityType,
    }));
    this.filteredEntityOptions = term
      ? all.filter(o => o.label.toLowerCase().includes(term) ||
                        !!o.entityType?.toLowerCase().includes(term))
      : all;
    this.entityDropdownOpen = true;
  }

  /** True only once the citizen has typed something that matches nothing — never on an empty box. */
  get entitySearchHasNoResults(): boolean {
    return this.entityDropdownOpen &&
           this.entitySearchText.trim().length > 0 &&
           this.filteredEntityOptions.length === 0;
  }

  /**
   * The "No results found" line, with the citizen's term interpolated HERE rather than in the
   * template. translate(key, params?) takes params optionally, so `{{ key | translate }}` on a key
   * carrying a placeholder prints a literal `{{term}}`; the pipe cannot pass params. Interpolating in
   * the component is the only way this key can render correctly.
   *
   * The term is bound as text (an Angular interpolation), never as HTML, so a term such as
   * `<img src=x onerror=alert(1)>` is escaped and displayed, not executed.
   */
  get entitySearchNoResultsText(): string {
    const term = this.entitySearchText.trim();
    const key = 'eligibility.entity_search_no_results';
    const raw = this.translationService.translate(key, { term });
    return raw === key ? `No results found for "${term}".` : raw;
  }

  onEntitySearchInput(value: string) {
    this.entitySearchText = value;
    this.filterEntities();
  }

  openEntityDropdown() {
    this.filterEntities();
  }

  selectEntityFromSearch(opt: { label: string; value: string }) {
    this.selectEligibilityAnswer(opt.value);
    // The chosen name stays in the box so the citizen can see what they picked; clearing it made the
    // control look unanswered even though an answer had been recorded.
    this.entitySearchText = opt.label;
    this.entityDropdownOpen = false;
    this.filteredEntityOptions = [];
  }

  getSelectedEntityLabel(): string {
    const val = this.eligibilityAnswers['regulatedEntity'];
    const opt = this.entityOptions.find(o => o.value === val);
    return opt?.label ?? '';
  }

  /**
   * UST13 QA10: clearing the box restores the FULL list, and also drops the selection — leaving the
   * answer behind while the box reads empty would let a citizen proceed against an entity the screen
   * no longer names.
   */
  clearEntitySelection() {
    this.eligibilityAnswers['regulatedEntity'] = '';
    this.entitySearchText = '';
    this.eligibilityBlocked.set(false);
    this.filterEntities();
  }

  closeEntityDropdown() {
    setTimeout(() => this.entityDropdownOpen = false, 200);
  }

  eligibilityFieldError = '';
  eligibilityFileError = '';
  eligibilityRefError = '';
  mandatoryResponseError = signal('');

  /**
   * UST13 S2 vs UST14/18 S3 — the two mandatory messages are DIFFERENT by design.
   *
   * The entity question is a named field, and QA's expected result for it is "Regulated Entity Name is
   * mandatory."; the Yes/No maintainability questions expect the generic "Response is mandatory.".
   * Emitting the generic line for the entity select told the citizen to answer a "response" on a
   * screen with nothing to respond to. Keyed off the question TYPE, not its key, so an operator
   * renaming the master row cannot silently revert this.
   */
  private failMandatory() {
    const isEntitySelect = this.currentQuestion?.type === 'select';
    this.mandatoryResponseError.set(isEntitySelect
      ? this.t('eligibility.entity_mandatory', 'Regulated Entity Name is mandatory.')
      : this.t('eligibility.response_mandatory', 'Response is mandatory.'));
    announceToScreenReader(this.mandatoryResponseError(), 'assertive');
  }

  nextEligibility() {
    const q = this.currentQuestion;
    if (!q) return;
    if (this.eligibilityBlocked()) {
      if (q.nonMaintainable) {
        this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
        this.phase.set('non-maintainable');
      }
      return;
    }
    if (!this.eligibilityAnswers[q.key]) {
      this.failMandatory();
      return;
    }
    this.mandatoryResponseError.set('');

    this.eligibilityFieldError = '';
    this.eligibilityFileError = '';
    this.eligibilityRefError = '';
    this.replyFileError = '';
    this.replyDateError = '';
    this.reminderFileError = '';
    this.reminderDateError = '';
    if (q.key === 'filedWithRE' && this.eligibilityAnswers['filedWithRE'] === 'yes') {
      if (this.dateFormatError['bankComplaintDate']) {
        this.eligibilityFieldError = this.dateFormatError['bankComplaintDate'];
        return;
      }
      if (!this.formData['bankComplaintDate']) {
        this.eligibilityFieldError = this.t('validation.complaint_date_required',
          'Complaint date with RE is required');
        return;
      }
      this.onBankComplaintDateChange();
      if (this.eligibilityFieldError) return;
      if (!this.complaintFileWithRE) {
        this.eligibilityFileError = this.t('validation.complaint_copy_required',
          'Please upload a copy of the complaint sent to the Regulated Entity');
        return;
      }
      if (!this.validateComplaintRef()) return;
    }

    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'yes') {
      if (this.dateFormatError['replyDate']) {
        this.replyDateError = this.dateFormatError['replyDate'];
        return;
      }
      if (!this.formData['replyDate']) {
        this.replyDateError = this.t('validation.reply_date_required',
          'Date on which reply was received is required');
        return;
      }
      this.onReplyDateChange();
      if (this.replyDateError) return;
      if (!this.replyFile) {
        this.replyFileError = this.t('validation.reply_copy_required',
          'Please upload a copy of the reply received from the Regulated Entity');
        return;
      }
      // Where the RE HAS replied the window runs from the reply date, so it is assessed here. The
      // 'no' branch below assesses the other limb, which runs from the RE complaint date.
      if (this.applyFilingWindowBlock()) {
        this.phase.set('non-maintainable');
        return;
      }
    }

    if (q.key === 'sentReminder' && this.eligibilityAnswers['sentReminder'] === 'yes') {
      if (this.dateFormatError['reminderDate']) {
        this.reminderDateError = this.dateFormatError['reminderDate'];
        return;
      }
      if (!this.formData['reminderDate']) {
        this.reminderDateError = this.t('validation.reminder_date_required',
          'Date on which reminder was sent is required');
        return;
      }
      this.onReminderDateChange();
      if (this.reminderDateError) return;
      if (!this.reminderFile) {
        this.reminderFileError = this.t('validation.reminder_copy_required',
          'Please upload a copy of the reminder sent to the Regulated Entity');
        return;
      }
    }

    if (q.key === 'employeeOfRE' && this.eligibilityAnswers['employeeOfRE'] === 'yes') {
      if (!this.eligibilityAnswers['employerRelationship']) {
        this.failMandatory();
        return;
      }
    }

    // UST27: the advocate follow-up decides maintainability, so it is as mandatory as its parent.
    if (q.key === 'throughAdvocateEligibility' && this.eligibilityAnswers['throughAdvocateEligibility'] === 'yes') {
      if (!this.eligibilityAnswers['isComplainantSelf']) {
        this.failMandatory();
        return;
      }
    }

    if (q.key === 'receivedReply' && this.eligibilityAnswers['receivedReply'] === 'no') {
      // The RE window can only be assessed against a filing date, so demand it rather than letting
      // an unanswered date wave the complaint through.
      if (!this.formData['bankComplaintDate']) {
        this.eligibilityFieldError = this.t('validation.complaint_date_required',
          'Complaint date with RE is required');
        this.eligibilityStep.set(this.stepOfQuestion('filedWithRE') ?? this.eligibilityStep());
        return;
      }
      if (this.applyReWindowBlock()) {
        this.phase.set('non-maintainable');
        return;
      }
      if (this.applyFilingWindowBlock()) {
        this.phase.set('non-maintainable');
        return;
      }
    }

    if (this.eligibilityStep() < this.totalEligibilitySteps) {
      this.showSimplified.set(false);
      this.eligibilityStep.update(s => s + 1);
      this.clearBlock();
    } else {
      this.phase.set('form');
      this.currentStep.set(1);
    }
  }

  prevEligibility() {
    if (this.eligibilityStep() > 1) {
      this.mandatoryResponseError.set('');
      this.showSimplified.set(false);
      this.eligibilityStep.update(s => s - 1);
      this.clearBlock();
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
      doc.text(this.schemeName, pw / 2, y, { align: 'center' });
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

      const reason = this.resolvedBlockMessage();
      const bodyText = `Your complaint has been closed as Non-Maintainable under the provisions of the ${this.schemeName}.`;
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

      // The letter is the citizen's record of the refusal, so it must carry the date the window opens.
      const opensNotice = this.reWindowOpensNotice();
      if (opensNotice) {
        const opensLines = doc.splitTextToSize(opensNotice, pw - 40);
        doc.text(opensLines, 20, y);
        y += opensLines.length * 6 + 8;
      }

      doc.text('This is a system-generated letter and does not require a signature.', 20, y);
      y += 14;

      doc.setFont('helvetica', 'bold');
      doc.text('Reserve Bank of India', 20, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      doc.text('Department of Consumer Education and Protection', 20, y);
      y += 14;

      // The letter already states it needs no signature; the old green "DIGITALLY SIGNED | RBI CMS
      // Digital Certificate Authority" seal directly contradicted that and asserted a certificate
      // authority that does not exist. Nothing here is cryptographically signed.
      doc.setDrawColor(150);
      doc.setFillColor(245, 245, 245);
      doc.roundedRect(20, y, pw - 40, 12, 2, 2, 'FD');
      doc.setTextColor(80);
      doc.setFontSize(8);
      doc.setFont('helvetica', 'bold');
      doc.text('SYSTEM-GENERATED LETTER — NOT A DIGITALLY SIGNED DOCUMENT', 25, y + 8);
      doc.setTextColor(0);

      doc.save(`Closure_Letter_${this.nonMaintainableCaseId}.pdf`);
    });
  }

  // FR-G-017: Form Validation
  validationErrors: Record<string, string> = {};

  onAmountInput(field: string, value: string) {
    // Non-digits are STRIPPED, not merely rejected. This previously self-assigned
    // (`formData[field] = formData[field]`) intending to revert the field, but the input binding is
    // one-way `[ngModel]`, so reassigning the same value changed nothing Angular could detect and the
    // rejected characters stayed on screen. A complainant typing "12a" in an amount kept the "a".
    const digitsOnly = value.replace(/[^\d]/g, '');
    this.formData[field] = digitsOnly ? this.formatIndianNumber(digitsOnly) : '';
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
      // The category decides WHICH name field exists, so it must be validated before the name is:
      // previously a blank category produced a `name` error whose only two renderers both sit inside
      // the category-conditional blocks, so Next was refused with nothing on screen to explain it.
      if (!this.formData['complainantCategory']) {
        this.validationErrors['complainantCategory'] = 'Complainant category is required';
      } else if (this.isIndividualCategory()) {
        if (!this.formData['firstName']?.trim()) this.validationErrors['name'] = 'First name is required';
      } else {
        // An organisation has no first name — the field is never rendered for these categories, so
        // requiring firstName made all ten organisation categories unable to leave step 1 at all.
        if (!this.formData['organizationName']?.trim()) {
          this.validationErrors['name'] = 'Name of complainant is required';
        }
      }
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
      // The representative speaks for the complainant before the Ombudsman, so an unidentifiable
      // representative makes the authorisation unverifiable. Every field the template stars is checked.
      if (!this.formData['hasAuthRep']) this.validationErrors['hasAuthRep'] = 'Please select Yes or No';
      if (this.formData['hasAuthRep'] === 'yes') {
        if (!this.formData['repName']?.trim()) this.validationErrors['repName'] = 'Representative name is required';
        if (!/^[6-9]\d{9}$/.test(this.formData['repPhone'] || '')) this.validationErrors['repPhone'] = 'Valid 10-digit mobile number is required';
        if (this.formData['repEmail'] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.formData['repEmail'])) this.validationErrors['repEmail'] = 'Invalid email format';
        if (!/^\d{6}$/.test(this.formData['repPincode'] || '')) this.validationErrors['repPincode'] = 'Valid 6-digit pincode is required';
        if (!this.formData['repState']) this.validationErrors['repState'] = 'Enter valid pincode to auto-fill state';
        if (!this.formData['repDistrict']) this.validationErrors['repDistrict'] = 'District is required';
        if (!this.formData['repCity']) this.validationErrors['repCity'] = 'City is required';
        if (!this.formData['repAddress']?.trim()) this.validationErrors['repAddress'] = 'Representative address is required';
        if (!this.repFile) this.validationErrors['repFile'] = 'Letter of authorisation is required';
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
    const watermarkEl = element.querySelector('.review-watermark') as HTMLElement;
    if (stepHeader) stepHeader.style.display = 'none';
    if (navActions) navActions.style.display = 'none';
    if (watermarkEl) watermarkEl.style.display = 'none';

    const canvas = await html2canvas(element, {
      scale: 2,
      useCORS: true,
      logging: false,
      backgroundColor: 'var(--surface-card)'
    });

    if (stepHeader) stepHeader.style.display = '';
    if (navActions) navActions.style.display = '';
    if (watermarkEl) watermarkEl.style.display = '';

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

    const watermarkImg = new Image();
    watermarkImg.src = 'assets/draft-watermark.jpg';
    await new Promise<void>((resolve) => {
      watermarkImg.onload = () => resolve();
      watermarkImg.onerror = () => resolve();
    });

    const addWatermark = (pdf: any) => {
      if (watermarkImg.complete && watermarkImg.naturalWidth > 0) {
        const wmCanvas = document.createElement('canvas');
        wmCanvas.width = watermarkImg.naturalWidth;
        wmCanvas.height = watermarkImg.naturalHeight;
        const wmCtx = wmCanvas.getContext('2d')!;
        wmCtx.globalAlpha = 0.35;
        wmCtx.drawImage(watermarkImg, 0, 0);
        const wmData = wmCanvas.toDataURL('image/png');
        pdf.addImage(wmData, 'PNG', 0, 0, pdfWidth, pdfHeight);
      }
    };

    if (scaledHeight <= pageContentHeight) {
      doc.addImage(imgData, 'PNG', margin, margin, contentWidth, scaledHeight);
      addWatermark(doc);
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
        addWatermark(doc);

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

  // FR-G-008: Save Draft. The sessionStorage copy is only a same-tab convenience; the server copy is
  // what survives a closed browser, so "Draft Saved" is claimed only once the server confirms.
  saveDraft() {
    const attachmentMeta = this.attachmentPreviews.map(f => ({ name: f.name, type: f.type, size: f.size }));
    const draft = { version: this.DRAFT_VERSION, formData: this.formData, eligibilityAnswers: this.eligibilityAnswers, eligibilityStep: this.eligibilityStep(), currentStep: this.currentStep(), phase: this.phase(), entityName: this.getSelectedBankName(), declarationChecked: this.declarationChecked, declaration2Checked: this.declaration2Checked, attachmentMeta };
    sessionStorage.setItem('cms_complaint_draft', JSON.stringify(draft));

    this.saveDraftToServer();
  }

  private saveDraftToServer() {
    const phone = this.publicAuth.userIdentifier();
    if (!phone) {
      this.onDraftSaveFailed();
      return;
    }
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
          sessionStorage.setItem('cms_draft_id', res.draftId);
        }
        this.onDraftSaved();
      },
      error: () => this.onDraftSaveFailed()
    });
  }

  private onDraftSaved() {
    const now = new Date();
    sessionStorage.setItem('cms_draft_saved_at', now.toISOString());
    this.draftSaveFailed.set(false);
    this.draftSaved.set(true);
    this.lastSavedAt.set(now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }));
    setTimeout(() => this.draftSaved.set(false), 2000);
  }

  private onDraftSaveFailed() {
    this.draftSaved.set(false);
    this.draftSaveFailed.set(true);
    announceToScreenReader(this.translationService.translate('form.draft_save_failed'), 'assertive');
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
    const saved = sessionStorage.getItem('cms_complaint_draft');
    if (saved) {
      try {
        const draft = JSON.parse(saved);
        if (draft.version !== this.DRAFT_VERSION) {
          sessionStorage.removeItem('cms_complaint_draft');
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
    sessionStorage.removeItem('cms_complaint_draft');
    sessionStorage.removeItem('cms_draft_saved_at');
    const draftId = sessionStorage.getItem('cms_draft_id');
    if (draftId) {
      // Keep the id until the server confirms the delete. Dropping it on failure orphans the server
      // draft, which then shows up in Complaint History with no way for the citizen to remove it.
      this.complaintService.deleteDraft(draftId).subscribe({
        next: () => sessionStorage.removeItem('cms_draft_id'),
        error: () => {}
      });
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
    const setResult = validateFileSet(newFiles, this.attachments.length, this.fileLimits());
    if (!setResult.valid) {
      this.fileUploadError = setResult.error!;
      announceToScreenReader(setResult.error!, 'assertive');
      input.value = '';
      return;
    }

    for (const file of newFiles) {
      const result = validateFile(file, this.fileLimits());
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
    const setResult = validateFileSet(newFiles, this.attachments.length, this.fileLimits());
    if (!setResult.valid) {
      this.fileUploadError = setResult.error!;
      announceToScreenReader(setResult.error!, 'assertive');
      return;
    }
    for (const file of newFiles) {
      const result = validateFile(file, this.fileLimits());
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

    this.checkingDuplicate.set(true);
    delete this.validationErrors['submit'];

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/check-duplicate`, {
      phone, email, entityName, category, disputeDate
    }).subscribe({
      next: (res) => {
        this.checkingDuplicate.set(false);
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
      // FR-G-020: the check must fail closed. Auto-submitting here would let a genuine
      // duplicate through whenever the endpoint is down, defeating the control entirely.
      error: () => {
        this.checkingDuplicate.set(false);
        this.validationErrors['submit'] =
          'We could not verify whether this complaint has already been filed. Please try submitting again.';
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
      // The post-reply filing window runs from this date, so the server cannot enforce the window
      // without it. Collected and validated in step 2, then previously dropped here.
      reReplyDate: this.formData['replyDate'] || undefined,
      // UST5: the server re-checks this, so it has to travel with the payload rather than living
      // only in the disabled state of the Submit button.
      declarationAccepted: this.declarationChecked && this.declaration2Checked,
      // D7: step 4 validates these as mandatory, so they must reach the server rather than be
      // collected and dropped — an officer has to be able to contact the representative.
      hasAuthRep: this.formData['hasAuthRep'] === 'yes',
      throughAdvocate: this.formData['throughAdvocate'] === 'yes',
      repName: this.formData['repName'] || undefined,
      repPhone: this.formData['repPhone'] || undefined,
      repEmail: this.formData['repEmail'] || undefined,
      repAddress: this.formData['repAddress'] || undefined,
      repState: this.formData['repState'] || undefined,
      repDistrict: this.formData['repDistrict'] || undefined,
      repCity: this.formData['repCity'] || undefined,
      repPincode: this.formData['repPincode'] || undefined,
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
    // Local, not UTC: this is the [max] of the RE-complaint-date pickers, and east of Greenwich the
    // UTC form is yesterday — so the citizen could not pick today's date at all.
    return this.toLocalIso(new Date());
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

  // `pwd` is a natural person and must get the person form (name / age / gender), not the organisation
  // form. Omitting it meant a Person with Disabilities was asked for an organisation name and could
  // never supply their own, which also silently loses the age/gender data the PwD priority rules read.
  private static readonly INDIVIDUAL_CATEGORIES = ['individual', 'pwd', 'senior_citizen'];

  isIndividualCategory(): boolean {
    return PublicFileComplaintComponent.INDIVIDUAL_CATEGORIES.includes(this.formData['complainantCategory']);
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
      not_disclosed: 'Do not wish to disclose'
    };
    return map[this.formData['gender']] || this.formData['gender'] || '—';
  }

  getComplaintCategoryLabel(): string {
    const cat = this.categories.find(c => c.value === this.formData['complaintCategory']);
    // Localised for the review step too: a citizen must not pick a Punjabi label on step 3 and then be
    // shown the English name when confirming what they are about to file.
    return (cat ? this.categoryLabel(cat) : '') || this.formData['complaintCategory'] || '—';
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

  private isFutureDate(iso: string): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return new Date(iso) > today;
  }

  onBankComplaintDateChange() {
    this.eligibilityFieldError = '';
    const bankDate = this.formData['bankComplaintDate'];
    if (bankDate && this.isFutureDate(bankDate)) {
      this.eligibilityFieldError = this.t('validation.date_future', 'Date cannot be a future date');
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
    if (!replyDate) return;
    if (this.isFutureDate(replyDate)) {
      this.replyDateError = this.t('validation.date_future', 'Date cannot be a future date');
    } else if (complaintDate && new Date(replyDate) < new Date(complaintDate)) {
      this.replyDateError = this.t('validation.reply_before_complaint',
        'Reply date cannot be earlier than the complaint filing date');
    }
  }

  onReminderDateChange() {
    this.reminderDateError = '';
    const reminderDate = this.formData['reminderDate'];
    const complaintDate = this.formData['bankComplaintDate'];
    if (!reminderDate) return;
    if (this.isFutureDate(reminderDate)) {
      this.reminderDateError = this.t('validation.date_future', 'Date cannot be a future date');
    } else if (complaintDate && new Date(reminderDate) < new Date(complaintDate)) {
      this.reminderDateError = this.t('validation.reminder_before_complaint',
        'Reminder date cannot be earlier than the complaint filing date');
    }
  }

  /**
   * UST17: the reference number is optional, but when supplied it is quoted back to the Regulated
   * Entity, so it is bounded and restricted to the characters REs actually issue.
   */
  static readonly COMPLAINT_REF_MAX = 100;

  validateComplaintRef(): boolean {
    this.eligibilityRefError = '';
    const ref = String(this.formData['bankComplaintRef'] ?? '').trim();
    if (!ref) return true;
    if (ref.length > PublicFileComplaintComponent.COMPLAINT_REF_MAX) {
      this.eligibilityRefError = this.t('validation.ref_too_long',
        `Reference number must not exceed ${PublicFileComplaintComponent.COMPLAINT_REF_MAX} characters`);
      return false;
    }
    if (!/^[A-Za-z0-9/_-]+$/.test(ref)) {
      this.eligibilityRefError = this.t('validation.ref_invalid_chars',
        'Reference number may contain only letters, digits, hyphen, underscore and slash');
      return false;
    }
    return true;
  }

  onEmployerRelationshipAnswer(value: string) {
    this.eligibilityAnswers['employerRelationship'] = value;
    // Inline sub-question, so its block comes from its own master row rather than the current step's.
    const q = this.eligibilityQuestions.find(x => x.key === 'employerRelationship');
    if (q?.blockOn && value === q.blockOn) {
      this.applyBlockFrom(q);
    } else {
      this.clearBlock();
    }
  }

  onAdvocateSubAnswer(value: string) {
    this.eligibilityAnswers['isComplainantSelf'] = value;
    this.formData['isComplainantSelf'] = value;
    this.mandatoryResponseError.set('');
    // Inline sub-question, so its block comes from its own master row rather than the current step's.
    const q = this.eligibilityQuestions.find(x => x.key === 'isComplainantSelf');
    if (q?.blockOn && value === q.blockOn) {
      this.applyBlockFrom(q);
      this.nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8);
    } else {
      this.clearBlock();
    }
  }

  // Get visible eligibility questions based on entity type
  get visibleEligibilityQuestions(): EligibilityQuestion[] {
    return this.eligibilityQuestions.filter((q, i) => this.isQuestionVisible(q, i));
  }

  get radioEligibilityQuestions(): EligibilityQuestion[] {
    return this.visibleEligibilityQuestions.filter(q => q.type === 'radio');
  }

  /** Applicability comes from the master's applicableEntityType / inlineSubQuestion columns (D14). */
  isQuestionVisible(q: EligibilityQuestion, _index: number): boolean {
    if (q.inlineSubQuestion) return false;

    switch (q.applicableEntityType || 'ALL') {
      case 'CEPC': return this.isCEPCEntity;
      case 'NON_CEPC': return !this.isCEPCEntity;
      case 'RBIO': return this.selectedEntityType === 'RBIO';
      default: return true;
    }
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
