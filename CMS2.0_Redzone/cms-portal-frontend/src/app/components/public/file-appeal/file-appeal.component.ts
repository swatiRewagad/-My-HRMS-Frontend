import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormsModule,
  ReactiveFormsModule,
  FormGroup,
  FormControl,
  Validators,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import {
  Subject,
  interval,
  switchMap,
  takeUntil,
  filter,
  catchError,
  EMPTY,
  finalize,
  forkJoin,
} from 'rxjs';
import {
  AppealService,
  FileMeta,
  AppealDraftPayload,
  AppealDraftRecord,
} from '../../../services/appeal.service';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { FormErrorComponent } from '../../../shared/form-error/form-error.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { validateFile } from '../../../utils/file-validator';
import { scrollToTop } from '../../../utils/accessibility';
import { UploadLimitsService } from '../../../services/upload-limits.service';

function repAuthValidator(group: AbstractControl): ValidationErrors | null {
  const name = group.get('representativeName')?.value;
  const doc = group.get('authorizationDoc')?.value;
  if (name?.trim() && !doc) {
    return { authDocRequired: true };
  }
  return null;
}

@Component({
  selector: 'app-file-appeal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SpeechButtonComponent,
    FormErrorComponent,
    TranslatePipe,
  ],
  templateUrl: './file-appeal.component.html',
  styleUrl: './file-appeal.component.scss',
})
export class FileAppealComponent implements OnInit, OnDestroy {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private appealService = inject(AppealService);
  // Public: the template renders the configured per-file limit in the upload hint.
  uploadLimits = inject(UploadLimitsService);
  private destroy$ = new Subject<void>();

  phase = signal<'search' | 'eligibility' | 'form' | 'success'>('search');
  submitting = signal(false);
  checking = signal(false);
  error = '';

  complaintId = '';
  complaintFound = false;

  /** Upper bound for the award-communication date picker: an award cannot be received in the future. */
  readonly todayISO = new Date().toISOString().slice(0, 10);

  eligibilityResult = signal<any>(null);
  classification = signal<'APPEAL' | 'REPRESENTATION' | null>(null);
  isDelayedFiling = signal(false);

  currentStep = signal(0);
  highestStepReached = signal(0);
  readonly stepLabels = [
    'Complaint Details',
    'Representative Authorization',
    'Review & Submit',
  ];

  wizardForm = new FormGroup({
    complaintDetails: new FormGroup({
      appealGround: new FormControl('', Validators.required),
      // UST113 caps appellant comments at 500 characters. Enforced as a validator and NOT as a
      // template maxlength: the attribute truncates silently at the limit, which both loses the end of
      // what the appellant wrote and makes the "too long" error unreachable.
      appealDetails: new FormControl('', [Validators.required, Validators.maxLength(500)]),
      // UST113's appellant identity fields. These did not exist, so an appeal could not record who was
      // appealing or how to reach them — the backend could only copy the parent complaint's details.
      appellantName: new FormControl('', [
        Validators.required,
        Validators.maxLength(100),
        Validators.pattern(/^[\p{L}\s.'-]+$/u),
      ]),
      appellantPhone: new FormControl('', [Validators.required, Validators.pattern(/^\d{10}$/)]),
      appellantEmail: new FormControl('', [Validators.email, Validators.maxLength(64)]),
      // Optional per UST113's In-Scope list. (AC1 of the same story calls it mandatory; the field
      // specification is the narrower, more specific statement, so it governs. Flagged to the BA.)
      awardCommunicationDate: new FormControl(''),
      reliefSought: new FormControl(''),
      reasonForDelay: new FormControl(''),
      attachments: new FormControl<FileMeta[]>([]),
    }),
    repAuth: new FormGroup(
      {
        representativeName: new FormControl(''),
        authorizationDoc: new FormControl<FileMeta | null>(null),
      },
      { validators: repAuthValidator },
    ),
    declaration: new FormGroup({
      declarationChecked: new FormControl(false, Validators.requiredTrue),
    }),
  });

  readonly appealGrounds = [
    'The complaint was not resolved within 30 days',
    'Dissatisfied with the resolution/award',
    'The complaint was rejected without valid reason',
    'Partial relief was granted',
    'Non-implementation of the award by Regulated Entity',
    'Other',
  ];

  uploading = signal<Record<string, boolean>>({});
  isDragOver = false;

  draftId: string | null = null;
  autoSaveStatus = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');

  appealRefNumber = '';

  get stepGroups(): FormGroup[] {
    return [
      this.wizardForm.get('complaintDetails') as FormGroup,
      this.wizardForm.get('repAuth') as FormGroup,
      this.wizardForm.get('declaration') as FormGroup,
    ];
  }

  get currentStepGroup(): FormGroup {
    return this.stepGroups[this.currentStep()];
  }

  ngOnInit(): void {
    const idFromRoute = this.route.snapshot.paramMap.get('id');
    if (idFromRoute) {
      this.complaintId = idFromRoute;
    }
    this.startAutoSave();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Search ──────────────────────────────────────────────

  searchComplaint(): void {
    if (!this.complaintId.trim()) {
      this.error = 'Please enter your complaint reference number.';
      return;
    }
    this.error = '';
    this.checking.set(true);

    this.appealService.checkEligibility(this.complaintId.trim()).subscribe({
      next: (data) => {
        this.checking.set(false);
        if (data) {
          this.eligibilityResult.set(data);
          this.classification.set(data.classification || null);
          this.complaintFound = true;
          this.phase.set('eligibility');
          scrollToTop();
        } else {
          this.error = 'Complaint not found. Please check the reference number.';
        }
      },
      error: (err) => {
        this.checking.set(false);
        this.error =
          err.error?.message || 'Unable to verify complaint. Please try again.';
      },
    });
  }

  // ── Eligibility → Form ─────────────────────────────────

  proceedToForm(): void {
    const result = this.eligibilityResult();
    if (!result?.eligible) {
      this.error = 'This complaint is not eligible for appeal.';
      return;
    }
    this.error = '';

    const days = result.complaintSummary?.daysSinceDecision || 0;
    this.isDelayedFiling.set(days > 30 && days <= 60);

    const delayCtrl = this.wizardForm.get('complaintDetails.reasonForDelay')!;
    if (this.isDelayedFiling()) {
      delayCtrl.setValidators([Validators.required, Validators.maxLength(500)]);
    } else {
      delayCtrl.clearValidators();
    }
    delayCtrl.updateValueAndValidity();

    const existingDraftId = result.draftId as string | undefined;
    if (existingDraftId) {
      this.appealService.getDraft(existingDraftId).subscribe({
        next: (draft) => this.hydrateDraft(draft),
        error: () => {},
      });
    }

    this.currentStep.set(0);
    this.phase.set('form');
    scrollToTop();
  }

  // ── Stepper Navigation ─────────────────────────────────

  nextStep(): void {
    this.currentStepGroup.markAllAsTouched();
    if (this.currentStepGroup.invalid) return;
    if (this.currentStep() < this.stepLabels.length - 1) {
      this.currentStep.update((s) => s + 1);
      this.highestStepReached.update((h) => Math.max(h, this.currentStep()));
      this.saveDraftNow();
      scrollToTop();
    }
  }

  prevStep(): void {
    if (this.currentStep() > 0) {
      this.currentStep.update((s) => s - 1);
      scrollToTop();
    }
  }

  // ── File Upload ────────────────────────────────────────

  onFileSelected(event: Event, controlPath: string): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    this.uploadFiles(Array.from(input.files), controlPath);
    input.value = '';
  }

  onFileDrop(event: DragEvent, controlPath: string): void {
    event.preventDefault();
    this.isDragOver = false;
    if (!event.dataTransfer?.files?.length) return;
    this.uploadFiles(Array.from(event.dataTransfer.files), controlPath);
  }

  private uploadFiles(files: File[], controlPath: string): void {
    if (!files.length) return;
    const control = this.wizardForm.get(controlPath);
    if (!control) return;

    for (const file of files) {
      // Server-configured limits, not a size compiled into the bundle: a figure baked in at build
      // time can go stale against GET /api/v1/config/upload-limits and refuse files the API accepts.
      const result = validateFile(file, {
        maxFileSizeMb: this.uploadLimits.maxFileSizeMb(),
        maxTotalSizeMb: this.uploadLimits.maxTotalSizeMb(),
        maxFileCount: this.uploadLimits.maxFileCount(),
      });
      if (!result.valid) {
        this.error = result.error!;
        return;
      }
    }
    this.error = '';

    const isArray = Array.isArray(control.value);
    this.uploading.update((s) => ({ ...s, [controlPath]: true }));

    forkJoin(files.map((f) => this.appealService.uploadFile(f)))
      .pipe(
        finalize(() =>
          this.uploading.update((s) => ({ ...s, [controlPath]: false })),
        ),
      )
      .subscribe({
        next: (metas) => {
          if (isArray) {
            control.setValue([...(control.value || []), ...metas]);
          } else {
            control.setValue(metas[metas.length - 1]);
          }
          control.markAsDirty();
        },
        error: () => {
          this.error = 'File upload failed. Please try again.';
        },
      });
  }

  removeUploadedFile(controlPath: string, index?: number): void {
    const control = this.wizardForm.get(controlPath);
    if (!control) return;

    let fileId: string | undefined;
    if (Array.isArray(control.value) && index !== undefined) {
      fileId = control.value[index]?.fileId;
      const updated = [...control.value];
      updated.splice(index, 1);
      control.setValue(updated);
    } else {
      fileId = (control.value as FileMeta | null)?.fileId;
      control.setValue(null);
    }
    control.markAsDirty();

    if (fileId) {
      this.appealService.deleteFile(fileId).subscribe();
    }
  }

  // ── Speech-to-Text ────────────────────────────────────

  appendTranscription(controlPath: string, text: string): void {
    const control = this.wizardForm.get(controlPath);
    if (!control) return;
    const current = ((control.value as string) || '').trim();
    control.setValue(current ? current + ' ' + text : text);
    control.markAsDirty();
  }

  // ── Auto-Save / Draft ─────────────────────────────────

  private startAutoSave(): void {
    interval(120_000)
      .pipe(
        takeUntil(this.destroy$),
        filter(() => this.phase() === 'form' && this.wizardForm.dirty),
        switchMap(() => {
          this.autoSaveStatus.set('saving');
          return this.appealService.saveDraft(this.buildDraftPayload()).pipe(
            catchError(() => {
              this.autoSaveStatus.set('error');
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((res) => {
        this.draftId = res.draftId;
        this.autoSaveStatus.set('saved');
        this.wizardForm.markAsPristine();
      });
  }

  saveDraftNow(): void {
    this.autoSaveStatus.set('saving');
    this.appealService
      .saveDraft(this.buildDraftPayload())
      .pipe(
        catchError(() => {
          this.autoSaveStatus.set('error');
          return EMPTY;
        }),
      )
      .subscribe((res) => {
        this.draftId = res.draftId;
        this.autoSaveStatus.set('saved');
        this.wizardForm.markAsPristine();
      });
  }

  private buildDraftPayload(): AppealDraftPayload {
    return {
      phone: '',
      complaintNumber: this.complaintId,
      classification: this.classification() || '',
      formData: this.wizardForm.getRawValue(),
      currentStep: this.currentStep(),
      highestStepReached: this.highestStepReached(),
      phase: this.phase(),
    };
  }

  private hydrateDraft(draft: AppealDraftRecord): void {
    this.wizardForm.patchValue(draft.formData);
    this.currentStep.set(draft.currentStep);
    this.highestStepReached.set(draft.highestStepReached);
    this.draftId = draft.draftId;
  }

  // ── Submit ─────────────────────────────────────────────

  submitAppeal(): void {
    this.wizardForm.markAllAsTouched();
    // Mark dirty too: app-form-error only shows once a control is touched or dirty, and a control the
    // appellant never focused is neither — so the banner appeared with no indication of which field.
    this.markAllDirty(this.wizardForm);
    if (this.wizardForm.invalid) {
      this.error = 'Please complete all required fields.';
      this.focusFirstInvalidStep();
      return;
    }

    this.submitting.set(true);
    this.error = '';

    const details = this.wizardForm.getRawValue().complaintDetails;
    const payload = {
      complaintNumber: this.complaintId,
      classification: this.classification() || '',
      ground: details.appealGround || '',
      details: details.appealDetails || '',
      reliefSought: details.reliefSought || '',
      reasonForDelay: details.reasonForDelay || '',
      appellantName: details.appellantName || '',
      appellantPhone: details.appellantPhone || '',
      appellantEmail: details.appellantEmail || '',
      // The backend's POST /api/v1/appeals/file only binds a `dateOfReceipt` @RequestParam — it has
      // no `awardCommunicationDate` parameter, so sending it under that name was silently dropped by
      // Spring and the date the citizen entered was never persisted.
      dateOfReceipt: details.awardCommunicationDate || '',
    };

    this.appealService.submitAppeal(payload).subscribe({
      next: (res) => {
        this.appealRefNumber = res.appealNumber;
        this.submitting.set(false);
        this.phase.set('success');
        scrollToTop();
        if (this.draftId) {
          this.appealService.deleteDraft(this.draftId).subscribe();
        }
      },
      error: (err) => {
        this.submitting.set(false);
        // 409 is the duplicate-appeal case (UST106 AC3). The server's message names the complaint
        // number, which is noise to someone who just tried to appeal it — state the rule instead.
        this.error = err.status === 409
          ? 'Appeal already filed for this complaint.'
          : err.error?.message || 'Failed to submit appeal. Please try again.';
      },
    });
  }

  /** Marks every control in a group dirty, recursing into nested groups. */
  private markAllDirty(group: FormGroup): void {
    Object.values(group.controls).forEach((control) => {
      control.markAsDirty();
      if (control instanceof FormGroup) this.markAllDirty(control);
    });
  }

  /** Navigates to the earliest wizard step holding an invalid control. */
  private focusFirstInvalidStep(): void {
    const groups: Array<[number, AbstractControl | null]> = [
      [0, this.wizardForm.get('complaintDetails')],
      [1, this.wizardForm.get('repAuth')],
      [2, this.wizardForm.get('declaration')],
    ];
    const firstInvalid = groups.find(([, group]) => group?.invalid);
    if (firstInvalid) this.currentStep.set(firstInvalid[0]);
  }

  goHome(): void {
    this.router.navigate(['/public']);
  }

  trackAppeal(): void {
    this.router.navigate(['/public/track', this.appealRefNumber]);
  }
}
