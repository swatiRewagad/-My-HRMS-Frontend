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
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { validateFile } from '../../../utils/file-validator';

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
    TranslatePipe,
  ],
  templateUrl: './file-appeal.component.html',
  styleUrl: './file-appeal.component.scss',
})
export class FileAppealComponent implements OnInit, OnDestroy {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private appealService = inject(AppealService);
  private destroy$ = new Subject<void>();

  phase = signal<'search' | 'eligibility' | 'form' | 'success'>('search');
  submitting = signal(false);
  checking = signal(false);
  error = '';

  complaintId = '';
  complaintFound = false;

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
      appealDetails: new FormControl('', Validators.required),
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
      this.searchComplaint();
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
  }

  // ── Stepper Navigation ─────────────────────────────────

  nextStep(): void {
    this.currentStepGroup.markAllAsTouched();
    if (this.currentStepGroup.invalid) return;
    if (this.currentStep() < this.stepLabels.length - 1) {
      this.currentStep.update((s) => s + 1);
      this.highestStepReached.update((h) => Math.max(h, this.currentStep()));
      this.saveDraftNow();
    }
  }

  prevStep(): void {
    if (this.currentStep() > 0) {
      this.currentStep.update((s) => s - 1);
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
      const result = validateFile(file);
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
    if (this.wizardForm.invalid) {
      this.error = 'Please complete all required fields.';
      return;
    }

    this.submitting.set(true);
    this.error = '';

    const payload = {
      complaintNumber: this.complaintId,
      classification: this.classification() || '',
      ...this.wizardForm.getRawValue(),
      draftId: this.draftId,
    };

    this.appealService.submitAppeal(payload).subscribe({
      next: (res) => {
        this.appealRefNumber = res.appealNumber;
        this.submitting.set(false);
        this.phase.set('success');
        if (this.draftId) {
          this.appealService.deleteDraft(this.draftId).subscribe();
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.error =
          err.error?.message || 'Failed to submit appeal. Please try again.';
      },
    });
  }

  goHome(): void {
    this.router.navigate(['/public']);
  }

  trackAppeal(): void {
    this.router.navigate(['/public/track', this.appealRefNumber]);
  }
}
