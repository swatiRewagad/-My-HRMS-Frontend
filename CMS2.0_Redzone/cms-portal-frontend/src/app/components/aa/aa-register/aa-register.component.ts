import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { UploadLimitsService } from '../../../services/upload-limits.service';
import { environment } from '../../../../environments/environment';
import { AppShellComponent } from '../../shared/app-shell/app-shell.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/** Standard response envelope used by every AA endpoint. */
interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  messageKey?: string | null;
  retryable?: boolean;
  data: T;
  timestamp: string;
}

/**
 * A field the server could not autofill, with the reason.
 *
 * The reason travels as a key rather than prose so the blank can be explained in the officer's
 * locale. An unexplained blank reads as a page failure; an explained one reads as missing data.
 */
interface UnresolvedField {
  field: string;
  reasonKey: string;
}

interface ComplainantAutofill {
  appellantName: string | null;
  appellantEmail: string | null;
  appellantPhone: string | null;
  appellantAddress1: string | null;
  appellantAddress2: string | null;
  appellantState: string | null;
  appellantDistrict: string | null;
  appellantCity: string | null;
  appellantCountry: string | null;
  appellantPincode: string | null;
  categoryId: number | null;
}

interface EntityAutofill {
  entityCode: string | null;
  entityName: string | null;
  entityRegion: string | null;
  entityCategory: string | null;
  entityBranch: string | null;
  bsrIfscCode: string | null;
  accountNumber: string | null;
  cardNumber: string | null;
  nodalOfficerName: string | null;
}

interface RegisterFormPayload {
  complaintNumber: string;
  appealEligible: boolean;
  appealEligibilityKey: string | null;
  complainant: ComplainantAutofill;
  entity: EntityAutofill;
  modeOfReceipt: string;
  modeOfReceiptReadOnly: boolean;
  unresolvedFields: UnresolvedField[];
  closureClause: string | null;
  edApprovalRequired: boolean;
}

interface GroundOption {
  id: number;
  groundCode: string;
  label: string;
  labelKey: string | null;
}

interface ClauseOption {
  clauseCode: string;
  label: string;
  labelKey: string | null;
  appealableByComplainant: boolean;
  appealableByEntity: boolean;
}

interface RegisterResult {
  appealNumber: string;
  classificationType: string;
  status: string;
  modeOfReceipt: string;
  assignedOfficer: string | null;
  promptLegalCaseEntry: boolean;
  promptLegalCaseEntryKey: string | null;
}

/**
 * The register-milestone form model.
 *
 * The three declaration answers are `boolean | null`, not `boolean`. A "No" is a real answer that the
 * officer has given; an unanswered question is not. Collapsing them would make `false` indistinguishable
 * from "not asked yet" and let an incomplete form through the client-side gate, which is exactly the
 * distinction the server's validator draws (it lists them in missingFields only when null).
 */
interface RegisterForm {
  appealFiledBy: string;
  sourceOfAppeal: string;
  appealGround: string;
  reliefSought: string;
  reasonForDelay: string;
  appellantName: string;
  appellantEmail: string;
  appellantPhone: string;
  appellantAddress1: string;
  appellantAddress2: string;
  appellantCity: string;
  appellantDistrict: string;
  appellantState: string;
  appellantCountry: string;
  appellantPincode: string;
  categoryId: number | null;
  entityName: string;
  entityRegion: string;
  entityCategory: string;
  entityBranch: string;
  bsrIfscCode: string;
  accountNumber: string;
  cardNumber: string;
  nodalOfficerName: string;
  isComplainantAdvocate: boolean | null;
  hasRelatedCourtTrial: boolean | null;
  edApprovalGiven: boolean | null;
  edApprovalDate: string;
  edApprovalComments: string;
}

const EMPTY_FORM: RegisterForm = {
  appealFiledBy: '',
  sourceOfAppeal: '',
  appealGround: '',
  reliefSought: '',
  reasonForDelay: '',
  appellantName: '',
  appellantEmail: '',
  appellantPhone: '',
  appellantAddress1: '',
  appellantAddress2: '',
  appellantCity: '',
  appellantDistrict: '',
  appellantState: '',
  appellantCountry: '',
  appellantPincode: '',
  categoryId: null,
  entityName: '',
  entityRegion: '',
  entityCategory: '',
  entityBranch: '',
  bsrIfscCode: '',
  accountNumber: '',
  cardNumber: '',
  nodalOfficerName: '',
  isComplainantAdvocate: null,
  hasRelatedCourtTrial: null,
  edApprovalGiven: null,
  edApprovalDate: '',
  edApprovalComments: ''
};

/**
 * AA "Register milestone": records an Appeal or Representation against a closed/reopened parent
 * complaint (stories 7-11, 15, 16).
 *
 * Two things are deliberately NOT decided here:
 *   - Appeal vs Representation. That follows from the parent's closure clause and the appealing party,
 *     and is derived server-side. When the clause is not in the master the server answers 503 and this
 *     screen shows a Retry, because guessing the classification could wrongly deny statutory recourse.
 *   - Mode of receipt. Derived from the intake channel and rendered read-only (story 10).
 */
@Component({
  selector: 'app-aa-register',
  standalone: true,
  imports: [CommonModule, FormsModule, AppShellComponent, TranslatePipe],
  templateUrl: './aa-register.component.html',
  styleUrl: './aa-register.component.scss'
})
export class AaRegisterComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaintNumber = signal<string>('');

  loading = signal<boolean>(true);
  loadError = signal<boolean>(false);
  /** Key for whatever went wrong last, load or submit. Rendered, never only logged. */
  errorMessageKey = signal<string | null>(null);
  /** Server prose, shown alongside the key when the server sent something more specific. */
  errorMessage = signal<string | null>(null);
  /** True only for the 503 clause-not-configured path, which is the one error worth retrying as-is. */
  errorRetryable = signal<boolean>(false);

  appealEligible = signal<boolean>(false);
  closureClause = signal<string | null>(null);
  modeOfReceipt = signal<string>('');
  edApprovalRequired = signal<boolean>(false);
  unresolvedFields = signal<UnresolvedField[]>([]);

  grounds = signal<GroundOption[]>([]);
  clauses = signal<ClauseOption[]>([]);

  form = signal<RegisterForm>({ ...EMPTY_FORM });

  submitting = signal<boolean>(false);
  /** Field names the server rejected as missing; drives the per-field highlight. */
  missingFields = signal<string[]>([]);
  result = signal<RegisterResult | null>(null);

  selectedFiles = signal<File[]>([]);
  uploadErrorKey = signal<string | null>(null);

  /**
   * Interpolation values for the upload error keys, which carry {{size}}/{{total}}/{{count}}.
   *
   * <p>Rendering those keys with NO params printed a literal "{{size}}" to the registrar in all ten
   * locales: V67 added the placeholder to aa.upload.error_file_too_large but nothing here supplied it.
   */
  readonly uploadErrorParams = computed<Record<string, string>>(() => ({
    size: String(this.uploadLimits.maxFileSizeMb()),
    total: String(this.uploadLimits.maxTotalSizeMb()),
    count: String(this.uploadLimits.maxFileCount())
  }));

  // Asked of the server rather than compiled in: an administrator can change the limit without a
  // release, and a bundled constant would silently disagree with what /api/files/upload enforces.
  private uploadLimits = inject(UploadLimitsService);

  /**
   * Who may be recorded as filing, taken from the parent clause's appealability flags rather than a
   * literal list: a complainant may appeal 15(1)(a) and 15(1)(b), an entity only 15(1)(b). When the
   * clause is absent from the master both are offered and the server's 503 settles it.
   *
   * The two option labels reuse the section-heading keys because no dedicated party-name key is seeded
   * yet and inventing one would render as a raw key in all ten locales. Worth replacing with proper
   * aa.register.party_* keys when the seeder is next touched.
   */
  filedByOptions = computed<{ value: string; labelKey: string }[]>(() => {
    const clause = this.clauses().find(c => c.clauseCode === this.closureClause());
    const options: { value: string; labelKey: string }[] = [];
    if (!clause || clause.appealableByComplainant) {
      options.push({ value: 'COMPLAINANT', labelKey: 'aa.register.complainant_heading' });
    }
    if (!clause || clause.appealableByEntity) {
      options.push({ value: 'ENTITY', labelKey: 'aa.register.entity_heading' });
    }
    return options;
  });

  /**
   * Client-side mandatory gate (stories 7, 8, 11, 15).
   *
   * A convenience only — the server revalidates every one of these and returns the full missing list.
   * Note the explicit `!== null` on the declarations: a falsy check would read a valid "No" as blank.
   */
  mandatoryComplete = computed<boolean>(() => {
    const f = this.form();
    const filled = (value: string): boolean => value.trim().length > 0;

    const complete =
      filled(f.appealFiledBy) &&
      filled(f.sourceOfAppeal) &&
      filled(f.appealGround) &&
      filled(f.appellantName) &&
      filled(f.appellantPhone) &&
      filled(f.appellantAddress1) &&
      filled(f.appellantCity) &&
      filled(f.appellantState) &&
      f.categoryId !== null &&
      f.isComplainantAdvocate !== null &&
      f.hasRelatedCourtTrial !== null;

    if (!complete) {
      return false;
    }
    if (this.edApprovalRequired() && f.edApprovalGiven === null) {
      return false;
    }
    return true;
  });

  canSubmit = computed<boolean>(() =>
    this.mandatoryComplete() &&
    this.appealEligible() &&
    !this.submitting() &&
    this.uploadErrorKey() === null &&
    this.result() === null
  );

  async ngOnInit(): Promise<void> {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const complaintNumber = this.route.snapshot.paramMap.get('complaintNumber');
    if (!complaintNumber) {
      this.loading.set(false);
      this.loadError.set(true);
      this.errorMessageKey.set('aa.search.error_failed');
      return;
    }
    this.complaintNumber.set(complaintNumber);

    this.loadMasters();
    this.loadRegisterForm();
  }

  /** Grounds and closure clauses come from the masters; a literal list would drift from the scheme. */
  private loadMasters(): void {
    const base = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/masters`;

    this.http.get<ApiEnvelope<GroundOption[]>>(`${base}/grounds`).subscribe({
      next: res => this.grounds.set(res?.data ?? []),
      error: () => {
        // An empty ground list makes a mandatory field unanswerable, so it is an error, not a blank.
        this.grounds.set([]);
        this.errorMessageKey.set('aa.search.error_failed');
      }
    });

    this.http.get<ApiEnvelope<ClauseOption[]>>(`${base}/closure-clauses`).subscribe({
      next: res => this.clauses.set(res?.data ?? []),
      error: () => this.clauses.set([])
    });
  }

  loadRegisterForm(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.errorMessageKey.set(null);
    this.errorMessage.set(null);
    this.errorRetryable.set(false);

    const url = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/`
      + `${encodeURIComponent(this.complaintNumber())}/register-form`;

    this.http.get<ApiEnvelope<RegisterFormPayload>>(url).subscribe({
      next: res => {
        const data = res?.data;
        if (!data) {
          this.loading.set(false);
          this.loadError.set(true);
          this.errorMessageKey.set('aa.search.error_failed');
          return;
        }
        this.applyAutofill(data);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // Surfaced, not swallowed: a blank form must never be mistaken for a loaded one.
        this.loading.set(false);
        this.loadError.set(true);
        this.applyServerError(err);
      }
    });
  }

  private applyAutofill(data: RegisterFormPayload): void {
    this.appealEligible.set(data.appealEligible);
    this.closureClause.set(data.closureClause);
    this.modeOfReceipt.set(data.modeOfReceipt ?? '');
    this.edApprovalRequired.set(data.edApprovalRequired);
    this.unresolvedFields.set(data.unresolvedFields ?? []);

    if (!data.appealEligible && data.appealEligibilityKey) {
      this.errorMessageKey.set(data.appealEligibilityKey);
    }

    const c = data.complainant;
    const e = data.entity;

    // `?? ''` only maps an absent value onto an empty input. It never substitutes a stand-in: every
    // null here is a field with no data source, listed in unresolvedFields and explained in the UI.
    // Writing a guessed address or account number into an appeal would corrupt a legal record.
    this.form.set({
      ...EMPTY_FORM,
      appellantName: c?.appellantName ?? '',
      appellantEmail: c?.appellantEmail ?? '',
      appellantPhone: c?.appellantPhone ?? '',
      appellantAddress1: c?.appellantAddress1 ?? '',
      appellantAddress2: c?.appellantAddress2 ?? '',
      appellantCity: c?.appellantCity ?? '',
      appellantDistrict: c?.appellantDistrict ?? '',
      appellantState: c?.appellantState ?? '',
      appellantCountry: c?.appellantCountry ?? '',
      appellantPincode: c?.appellantPincode ?? '',
      categoryId: c?.categoryId ?? null,
      entityName: e?.entityName ?? '',
      entityRegion: e?.entityRegion ?? '',
      entityCategory: e?.entityCategory ?? '',
      entityBranch: e?.entityBranch ?? '',
      bsrIfscCode: e?.bsrIfscCode ?? '',
      accountNumber: e?.accountNumber ?? '',
      cardNumber: e?.cardNumber ?? '',
      nodalOfficerName: e?.nodalOfficerName ?? ''
    });
  }

  /** Single writer for the form signal, so every edit produces a new object the computeds can see. */
  setField<K extends keyof RegisterForm>(key: K, value: RegisterForm[K]): void {
    this.form.update(current => ({ ...current, [key]: value }));
    // Editing a field the server flagged clears its highlight; leaving it would nag about fixed input.
    if (this.missingFields().includes(key as string)) {
      this.missingFields.update(fields => fields.filter(f => f !== key));
    }
  }

  /**
   * categoryId is numeric on the wire, and an empty or unparseable input must stay null rather than
   * become 0 — the server treats 0 as present, so a blank would slip past the mandatory check.
   *
   * The parameter is widened because a `type="number"` ngModel emits a number (or null when cleared),
   * while a text input would emit a string; assuming one and calling string methods on the other throws.
   */
  setCategoryId(raw: string | number | null): void {
    if (raw === null || raw === '') {
      this.setField('categoryId', null);
      return;
    }
    const parsed = Number(raw);
    this.setField('categoryId', Number.isFinite(parsed) ? parsed : null);
  }

  /** The reason a field is blank, or null when the server did autofill it. */
  unresolvedReason(field: string): string | null {
    return this.unresolvedFields().find(u => u.field === field)?.reasonKey ?? null;
  }

  isMissing(field: string): boolean {
    return this.missingFields().includes(field);
  }

  /**
   * Prefers the master row's own translation key, falling back to its stored label.
   * The pipe echoes an unknown key verbatim, so a null labelKey still shows readable text.
   */
  groundLabel(ground: GroundOption): string {
    return ground.labelKey ?? ground.label;
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    this.uploadErrorKey.set(null);

    if (files.length > this.uploadLimits.maxFileCount()) {
      this.uploadErrorKey.set('aa.upload.error_too_many_files');
      this.selectedFiles.set([]);
      return;
    }
    if (files.some(f => f.size > this.uploadLimits.maxFileSizeBytes())) {
      this.uploadErrorKey.set('aa.upload.error_file_too_large');
      this.selectedFiles.set([]);
      return;
    }
    if (files.reduce((total, f) => total + f.size, 0) > this.uploadLimits.maxTotalSizeBytes()) {
      this.uploadErrorKey.set('aa.upload.error_total_too_large');
      this.selectedFiles.set([]);
      return;
    }
    this.selectedFiles.set(files);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }

    this.submitting.set(true);
    this.missingFields.set([]);
    this.errorMessageKey.set(null);
    this.errorMessage.set(null);
    this.errorRetryable.set(false);

    const f = this.form();
    // modeOfReceipt is intentionally absent: the server derives the channel and ignores a client value.
    const body = {
      appealFiledBy: f.appealFiledBy,
      sourceOfAppeal: f.sourceOfAppeal,
      appealGround: f.appealGround,
      reliefSought: f.reliefSought,
      reasonForDelay: f.reasonForDelay,
      appellantName: f.appellantName,
      appellantEmail: f.appellantEmail,
      appellantPhone: f.appellantPhone,
      appellantAddress1: f.appellantAddress1,
      appellantAddress2: f.appellantAddress2,
      appellantCity: f.appellantCity,
      appellantDistrict: f.appellantDistrict,
      appellantState: f.appellantState,
      appellantCountry: f.appellantCountry,
      appellantPincode: f.appellantPincode,
      categoryId: f.categoryId,
      entityName: f.entityName,
      entityRegion: f.entityRegion,
      entityCategory: f.entityCategory,
      entityBranch: f.entityBranch,
      bsrIfscCode: f.bsrIfscCode,
      accountNumber: f.accountNumber,
      cardNumber: f.cardNumber,
      nodalOfficerName: f.nodalOfficerName,
      isComplainantAdvocate: f.isComplainantAdvocate,
      hasRelatedCourtTrial: f.hasRelatedCourtTrial,
      edApprovalGiven: f.edApprovalGiven,
      edApprovalDate: f.edApprovalDate,
      edApprovalComments: f.edApprovalComments
    };

    const url = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/`
      + `${encodeURIComponent(this.complaintNumber())}/appeals`;

    this.http.post<ApiEnvelope<RegisterResult>>(url, body).subscribe({
      next: res => {
        this.submitting.set(false);
        if (res?.data) {
          this.result.set(res.data);
        } else {
          this.errorMessageKey.set('aa.search.error_failed');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.applyServerError(err);
      }
    });
  }

  /**
   * Maps a failure onto a rendered key.
   *
   * 503 is the fail-closed clause path and is the only retryable case: the classification is genuinely
   * undecidable until the clause is configured, so retrying the same request is the correct action and
   * falling back to a guessed classification is not.
   */
  private applyServerError(err: HttpErrorResponse): void {
    const body = err.error as ApiEnvelope<{ missingFields?: string[] }> | null;

    if (body?.messageKey) {
      this.errorMessageKey.set(body.messageKey);
    } else if (err.status === 403) {
      this.errorMessageKey.set('aa.register.error_not_permitted');
    } else {
      this.errorMessageKey.set('aa.search.error_failed');
    }

    this.errorMessage.set(body?.message ?? null);
    this.errorRetryable.set(err.status === 503 || body?.retryable === true);

    if (err.status === 400 && body?.data?.missingFields) {
      this.missingFields.set(body.data.missingFields);
    }
  }

  /** Retries the failed step: the 503 comes from the POST, everything else from the initial load. */
  retry(): void {
    if (this.loadError()) {
      this.loadRegisterForm();
      return;
    }
    this.submit();
  }

  goToAppeal(): void {
    const created = this.result();
    if (created) {
      this.router.navigate(['/aa/appeal', created.appealNumber]);
    }
  }

  goBack(): void {
    this.router.navigate(['/aa/dashboard']);
  }
}
