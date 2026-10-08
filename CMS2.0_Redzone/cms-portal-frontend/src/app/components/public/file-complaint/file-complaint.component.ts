import { Component, OnInit, OnDestroy, inject, HostListener, ViewChild, ElementRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { take } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { TranslationService } from '../../../services/translation.service';
import { PublicAuthService } from '../../../services/public-auth.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { announceToScreenReader, setPageTitle } from '../../../utils/accessibility';
import { Select } from 'primeng/select';
import { Dialog } from 'primeng/dialog';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { FormErrorComponent } from '../../../shared/form-error/form-error.component';
import { FileUploadComponent } from '../../../shared/file-upload/file-upload.component';
import { ComplaintFacadeService } from '../services';
import { ComplaintService } from '../../../services/complaint.service';
import { AccountType } from '../models';
import { TOOLTIPS, STEP_TITLES, CATEGORY_LABEL_MAP, GENDER_LABEL_MAP, NON_MAINTAINABLE_CLAUSES } from '../configs';

@Component({
  selector: 'app-public-file-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink, TranslatePipe, Select, Dialog, FormErrorComponent, FileUploadComponent, Toast],
  providers: [ComplaintFacadeService, MessageService],
  templateUrl: './file-complaint.component.html',
  styleUrl: './file-complaint.component.scss'
})
export class PublicFileComplaintComponent implements OnInit, OnDestroy {

  @ViewChild('formCard') formCard!: ElementRef<HTMLElement>;

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private publicAuth = inject(PublicAuthService);
  private messageService = inject(MessageService);
  private complaintService = inject(ComplaintService);
  facade = inject(ComplaintFacadeService);
  translationService = inject(TranslationService);

  readonly tooltips = TOOLTIPS;
  readonly stepTitles = STEP_TITLES;
  readonly totalSteps = 6;
  readonly watermarkRows = Array.from({ length: 80 }, (_, i) => i + 1);

  private uploadErrorSub!: Subscription;
  private draftLoadSub?: Subscription;
  accountTypeDropdownOpen = false;
  isDragOver = false;
  isRepDragOver = false;

  // ── Delegate form accessors for template ──
  get complaintStepperForm() { return this.facade.complaintStepperForm; }
  get complainantDetailsForm() { return this.facade.complainantDetailsForm; }
  get regulatedEntityForm() { return this.facade.regulatedEntityForm; }
  get complaintDetailsForm() { return this.facade.complaintDetailsForm; }
  get repAuthorizationForm() { return this.facade.repAuthorizationForm; }
  get declarationForm() { return this.facade.declarationForm; }
  get eligibilityStageForm() { return this.facade.eligibilityStageForm; }

  // ── Signal/state pass-throughs for template binding ──
  get phase() { return this.facade.phase; }
  get eligibilityStep() { return this.facade.eligibilityStep; }
  get eligibilityBlocked() { return this.facade.eligibilityBlocked; }
  get eligibilityBlockMessage() { return this.facade.eligibilityBlockMessage; }
  get eligibilityBlockMessageKey() { return this.facade.eligibilityBlockMessageKey; }
  get showSimplified() { return this.facade.showSimplified; }
  get currentStep() { return this.facade.currentStep; }
  get highestStepReached() { return this.facade.highestStepReached; }
  get submitting() { return this.facade.submitting; }
  get draftSaved() { return this.facade.draftSaved; }
  get lastSavedAt() { return this.facade.lastSavedAt; }
  get showDuplicatePopup() { return this.facade.showDuplicatePopup; }
  get isRecording() { return this.facade.isRecording; }

  get formData() { return this.facade.formData; }
  get eligibilityAnswers() { return this.facade.eligibilityAnswers; }
  get validationErrors() { return this.facade.validationErrors; }
  get categories() { return this.facade.categories; }
  get accountTypes() { return this.facade.accountTypes; }
  get banks() { return this.facade.banks; }
  get attachmentPreviews() { return this.facade.attachmentPreviews; }
  get complainantStates() { return this.facade.complainantStates; }
  get complainantDistricts() { return this.facade.complainantDistricts; }
  get repStates() { return this.facade.repStates; }
  get repDistricts() { return this.facade.repDistricts; }
  get repCities() { return this.facade.repCities; }
  get pincodeLoading() { return this.facade.pincodeLoading; }
  get repPincodeLoading() { return this.facade.repPincodeLoading; }
  get dateDisplay() { return this.facade.dateDisplay; }
  get entitySelectOptions() { return this.facade.entitySelectOptions; }
  get nonCoveredEntityOptions() { return this.facade.nonCoveredEntityOptions; }
  get referenceNumber() { return this.facade.referenceNumber; }
  get nonMaintainableCaseId() { return this.facade.nonMaintainableCaseId; }
  get duplicateMessage() { return this.facade.duplicateMessage; }
  get declarationChecked() { return this.facade.declarationChecked; }
  get declaration2Checked() { return this.facade.declaration2Checked; }
  get filesNeedReupload() { return this.facade.filesNeedReupload; }
  get fileUploadError() { return this.facade.fileUploadError; }
  get uploading() { return this.facade.uploading; }
  get eligibilityFieldError() { return this.facade.eligibilityFieldError; }
  get eligibilityFileError() { return this.facade.eligibilityFileError; }
  get eligibilityRefError() { return this.facade.eligibilityRefError; }
  get replyDateError() { return this.facade.replyDateError; }
  get replyFileError() { return this.facade.replyFileError; }
  get reminderDateError() { return this.facade.reminderDateError; }
  get reminderFileError() { return this.facade.reminderFileError; }
  get complaintFileWithRE() { return this.facade.complaintFileWithRE; }
  get complaintFileWithREName() { return this.facade.complaintFileWithREName; }
  get reminderFile() { return this.facade.reminderFile; }
  get reminderFileName() { return this.facade.reminderFileName; }
  get replyFile() { return this.facade.replyFile; }
  get replyFileName() { return this.facade.replyFileName; }
  get selectedEntityName() { return this.facade.selectedEntityName; }
  get isCEPCEntity() { return this.facade.isCEPCEntity; }
  get totalEligibilitySteps() { return this.facade.totalEligibilitySteps; }
  get currentQuestion() { return this.facade.currentQuestion; }
  get entitiesLoadFailed() { return this.facade.entitiesLoadFailed; }
  retryLoadEntities() { this.facade.retryLoadEntities(); }
  // Public: the template renders the server-configured per-file limit in each upload hint, rather
  // than a figure baked into the bundle at build time.
  get uploadLimits() { return this.facade.uploadLimits; }

  // ── Delegated methods for template ──
  saveDraft() {
    this.facade.draftSaveResult$.pipe(take(1)).subscribe(result => {
      if (result.success) {
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Draft saved successfully.', life: 2000 });
      } else {
        this.messageService.add({ severity: 'error', summary: 'Not Saved', detail: 'Could not save your draft. Please check your connection and try again.', life: 5000 });
      }
    });
    this.facade.saveDraft();
  }
  nextStep() { this.facade.nextStep(); }
  prevStep() { this.facade.prevStep(); }
  goToStep(step: number) { this.facade.goToStep(step); }
  goToEligibility() { this.facade.goToEligibility(); }
  isNextDisabled() { return this.facade.isNextDisabled(); }
  nextEligibility() { this.facade.nextEligibility(); }
  prevEligibility() { this.facade.prevEligibility(); }
  selectEligibilityAnswer(value: string) { this.facade.selectEligibilityAnswer(value); }
  onEntityStateChange() { this.facade.onEntityStateChange(); }
  onEntityDistrictChange() { this.facade.onEntityDistrictChange(); }
  onRepPincodeInput() { this.facade.onRepPincodeInput(); }
  onDateInput(field: string, event: Event) { this.facade.onDateInput(field, event); }
  onDatePickerChange(field: string, event: Event) { this.facade.onDatePickerChange(field, event); }
  onEmployerRelationshipAnswer(value: string) { this.facade.onEmployerRelationshipAnswer(value); }
  onAdvocateSubAnswer(value: string) { this.facade.onAdvocateSubAnswer(value); }
  submit() {
    this.facade.validationErrors['submit'] = '';
    this.facade.submitResult$.pipe(take(1)).subscribe((result) => {
      if (result.success) {
        this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Complaint submitted successfully.', life: 2000 });
      } else {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: result.message || 'Failed to submit complaint. Please try again.', life: 2000 });
      }
    });
    this.facade.submit();
  }
  dismissDuplicatePopup() { this.facade.dismissDuplicatePopup(); }
  proceedDespiteDuplicate() { this.facade.proceedDespiteDuplicate(); }

  /**
   * Closing via the X or Escape must behave as "cancel", not as consent to file: the dialog is a
   * confirmation gate, so dismissing it leaves the complaint unsubmitted.
   */
  onDuplicateVisibleChange(visible: boolean) {
    if (!visible) this.facade.dismissDuplicatePopup();
  }
  toggleRecording() { this.facade.toggleRecording(); }
  formatDate(iso: string) { return this.facade.formatDate(iso); }
  amountInWords(value: string) { return this.facade.amountInWords(value); }

  // ── Lifecycle ──
  ngOnInit(): void {
    setPageTitle('File a Complaint');
    this.facade.initialize();

    this.uploadErrorSub = this.facade.uploadError$.subscribe(msg => {
      this.messageService.add({ severity: 'error', summary: 'Upload Failed', detail: msg, life: 5000 });
    });

    const draftId = this.route.snapshot.queryParamMap.get('draftId');
    if (draftId) {
      this.draftLoadSub = this.facade.draftLoadResult$.pipe(take(1)).subscribe(result => {
        if (!result.success) {
          this.messageService.add({
            severity: 'error', summary: 'Could Not Open Draft',
            detail: 'We could not load your saved draft. Please go back to My Complaints and try again — your draft has not been deleted.',
            life: 8000
          });
        }
      });
      this.facade.loadDraftFromServer(draftId);
    }
  }

  ngOnDestroy(): void {
    this.uploadErrorSub?.unsubscribe();
    this.draftLoadSub?.unsubscribe();
    this.facade.destroy();
  }

  // ── Template helpers ──
  get sessionMinutes(): string {
    return this.publicAuth.getFormattedTime();
  }

  get isComplainantStepValid(): boolean {
    return this.facade.complainantDetailsForm.valid;
  }

  get currentQuestionText(): string {
    const q = this.facade.currentQuestion;
    const translated = q.translationKey
      ? this.translationService.translate(q.translationKey)
      : q.question;
    const text = (translated !== q.translationKey) ? translated : q.question;
    return text.replace(/<RE Name>/g, this.facade.selectedEntityName).replace(/\{\{reName\}\}/g, this.facade.selectedEntityName);
  }

  get isCurrentEligibilityStepValid(): boolean {
    return this.facade.isCurrentEligibilityStepValid;
  }

  get isEligibilityStageValid(): boolean {
    if (this.facade.eligibilityBlocked()) return false;
    const form = this.facade.eligibilityStageForm;
    if (!form.get('regulatedEntity')!.value) return false;
    const visibleKeys = this.facade.visibleEligibilityQuestions.map(q => q.key);
    for (const key of visibleKeys) {
      const ctrl = form.get(key);
      if (ctrl && ctrl.invalid) return false;
      if (ctrl && !ctrl.value && key !== 'bankComplaintRef') {
        const q = this.facade.eligibilityQuestions.find(eq => eq.key === key);
        if (q && q.type === 'radio') return false;
      }
    }
    if (form.get('filedWithRE')!.value === 'yes') {
      if (!form.get('bankComplaintDate')!.value || !form.get('complaintFileWithRE')!.value) return false;
    }
    if (form.get('receivedReply')!.value === 'yes') {
      if (!form.get('replyDate')!.value || !form.get('replyFileMeta')!.value) return false;
    }
    if (form.get('sentReminder')!.value === 'yes') {
      if (!form.get('reminderDate')!.value || !form.get('reminderFileMeta')!.value) return false;
    }
    if (form.get('throughAdvocateEligibility')!.value === 'yes') {
      if (!form.get('isComplainantSelf')!.value) return false;
    }
    if (form.get('employeeOfRE')!.value === 'yes') {
      if (!form.get('employerRelationship')!.value) return false;
    }
    return true;
  }

  get entityStateKeys(): string[] {
    return this.facade.states.map(s => s.value);
  }

  entityStateLabel(key: string): string {
    const state = this.facade.states.find(s => s.value === key);
    return state?.label || key.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  }

  get entityDistricts(): string[] { return this.facade.districts; }
  get entityBranches(): string[] { return this.facade.branches; }
  get entityBranchOptions(): string[] { return this.facade.entityBranchOptions; }
  get filteredSubCategories() { return this.facade.subCategories[this.facade.formData['complaintCategory']] || []; }
  get radioEligibilityQuestions() { return this.facade.visibleEligibilityQuestions.filter(q => q.type === 'radio'); }
  get today(): string { return new Date().toLocaleDateString('en-IN'); }
  get todayISO(): string { return new Date().toISOString().split('T')[0]; }

  get reviewEligibilityItems(): { num: number; key: string; question: string; answer: string; subItems?: { prefix: string; label: string; value: string; fileMeta?: { fileName: string; fileSize: number; viewUrl: string } | null }[] }[] {
    const items: { num: number; key: string; question: string; answer: string; subItems?: { prefix: string; label: string; value: string; fileMeta?: { fileName: string; fileSize: number; viewUrl: string } | null }[] }[] = [];
    let num = 1;
    const ea = this.eligibilityAnswers;
    const ef = this.facade.eligibilityStageForm;
    const reName = this.facade.selectedEntityName;

    if (ea['filedWithRE']) {
      items.push({
        num: num++, key: 'filedWithRE',
        question: `Have you filed a written / electronic complaint with the ${reName}?`,
        answer: ea['filedWithRE'] === 'yes' ? 'Yes' : 'No',
        subItems: ea['filedWithRE'] === 'yes' ? [
          { prefix: 'a', label: `Date of complaint filed with ${reName}`, value: this.formatDate(ef.get('bankComplaintDate')?.value || '') || '—' },
          { prefix: 'b', label: 'Complaint Reference/Acknowledgement Number', value: ef.get('bankComplaintRef')?.value || '—' },
          { prefix: 'c', label: 'Complaint copy uploaded', value: this.complaintFileWithREName || '—', fileMeta: this.facade.complaintFileMeta },
        ] : undefined,
      });
    }

    if (ea['receivedReply']) {
      items.push({
        num: num++, key: 'receivedReply',
        question: 'Have you received any reply from the Entity?',
        answer: ea['receivedReply'] === 'yes' ? 'Yes' : 'No',
        subItems: ea['receivedReply'] === 'yes' ? [
          { prefix: 'a', label: 'Date of reply received', value: this.formatDate(ef.get('replyDate')?.value || '') || '—' },
          { prefix: 'b', label: 'Reply copy uploaded', value: this.replyFileName || '—', fileMeta: this.facade.replyFileMeta },
        ] : undefined,
      });
    }

    if (ea['sentReminder']) {
      items.push({
        num: num++, key: 'sentReminder',
        question: `Have you sent any reminder to the ${reName}?`,
        answer: ea['sentReminder'] === 'yes' ? 'Yes' : 'No',
        subItems: ea['sentReminder'] === 'yes' ? [
          { prefix: 'a', label: 'Date of reminder sent', value: this.formatDate(ef.get('reminderDate')?.value || '') || '—' },
          { prefix: 'b', label: 'Reminder copy uploaded', value: this.reminderFileName || '—', fileMeta: this.facade.reminderFileMeta },
        ] : undefined,
      });
    }

    const remainingKeys = ['isSubJudice', 'alreadySettled', 'throughAdvocateEligibility', 'pendingBeforeOmbudsman', 'settledByOmbudsman', 'staffOfRE', 'previouslyFiledWithCEPC', 'employeeOfRE'];
    for (const key of remainingKeys) {
      const q = this.facade.eligibilityQuestions.find(eq => eq.key === key);
      if (q && this.facade.isQuestionVisible(q) && ea[key]) {
        const questionText = q.question.replace(/<RE Name>/g, reName);
        let subItems: { prefix: string; label: string; value: string }[] | undefined;
        if (key === 'employeeOfRE' && ea['employeeOfRE'] === 'yes' && ea['employerRelationship']) {
          subItems = [{ prefix: 'a', label: 'Does your complaint involve employer-employee relationship?', value: ea['employerRelationship'] === 'yes' ? 'Yes' : 'No' }];
        }
        if (key === 'throughAdvocateEligibility' && ea['throughAdvocateEligibility'] === 'yes' && ea['isComplainantSelf']) {
          subItems = [{ prefix: 'a', label: 'Is the complainant filing the complaint himself/herself?', value: ea['isComplainantSelf'] === 'yes' ? 'Yes' : 'No' }];
        }
        items.push({ num: num++, key, question: questionText, answer: ea[key] === 'yes' ? 'Yes' : 'No', subItems });
      }
    }

    return items;
  }

  get reviewComplainantFieldItems(): { num: number; label: string; value: string }[] {
    const items: { num: number; label: string; value: string }[] = [];
    let num = 2;
    const f = this.complainantDetailsForm;

    const firstName = f.get('firstName')?.value;
    if (firstName) items.push({ num: num++, label: 'First Name', value: firstName });

    const middleName = f.get('middleName')?.value;
    if (middleName) items.push({ num: num++, label: 'Middle Name', value: middleName });

    const lastName = f.get('lastName')?.value;
    if (lastName) items.push({ num: num++, label: 'Surname', value: lastName });

    const age = f.get('age')?.value;
    if (age) items.push({ num: num++, label: 'Age', value: String(age) });

    const gender = this.getGenderLabel();
    if (gender && gender !== '—') items.push({ num: num++, label: 'Gender', value: gender });

    const email = f.get('email')?.value;
    if (email) items.push({ num: num++, label: 'Email ID', value: email });

    const phone = f.get('phone')?.value;
    if (phone) items.push({ num: num++, label: 'Mobile Number', value: '+91 ' + phone });

    const pincode = f.get('pincode')?.value;
    if (pincode) items.push({ num: num++, label: 'Pincode', value: pincode });

    const state = f.get('state')?.value;
    if (state) items.push({ num: num++, label: 'State', value: state });

    const district = f.get('city')?.value;
    if (district) items.push({ num: num++, label: 'District', value: district });

    const address = f.get('addressDetails')?.value;
    if (address) items.push({ num: num++, label: 'Address', value: address });

    return items;
  }

  get reviewComplainantItems(): { num: number; label: string; value: string }[] {
    const items: { num: number; label: string; value: string }[] = [];
    let num = 1;
    const f = this.complainantDetailsForm;

    const category = this.getCategoryLabel();
    if (category && category !== '—') items.push({ num: num++, label: 'Complainant Category', value: category });

    const firstName = f.get('firstName')?.value;
    if (firstName) items.push({ num: num++, label: 'First Name', value: firstName });

    const middleName = f.get('middleName')?.value;
    if (middleName) items.push({ num: num++, label: 'Middle Name', value: middleName });

    const lastName = f.get('lastName')?.value;
    if (lastName) items.push({ num: num++, label: 'Surname', value: lastName });

    const age = f.get('age')?.value;
    if (age) items.push({ num: num++, label: 'Age', value: String(age) });

    const gender = this.getGenderLabel();
    if (gender && gender !== '—') items.push({ num: num++, label: 'Gender', value: gender });

    const email = f.get('email')?.value;
    if (email) items.push({ num: num++, label: 'Email ID', value: email });

    const phone = f.get('phone')?.value;
    if (phone) items.push({ num: num++, label: 'Mobile Number', value: '+91 ' + phone });

    const pincode = f.get('pincode')?.value;
    if (pincode) items.push({ num: num++, label: 'Pincode', value: pincode });

    const state = f.get('state')?.value;
    if (state) items.push({ num: num++, label: 'State', value: state });

    const district = f.get('city')?.value;
    if (district) items.push({ num: num++, label: 'District', value: district });

    const address = f.get('addressDetails')?.value;
    if (address) items.push({ num: num++, label: 'Address', value: address });

    return items;
  }

  get reviewRegulatedEntityItems(): { num: number; label: string; value: string }[] {
    const items: { num: number; label: string; value: string }[] = [];
    let num = 1;
    const rf = this.regulatedEntityForm;

    if (this.facade.selectedEntityName) items.push({ num: num++, label: 'Regulated Entity Name', value: this.facade.selectedEntityName });

    const isCreditCard = rf.get('isCreditCardComplaint')?.value;
    if (isCreditCard) items.push({ num: num++, label: 'Is your complaint related to credit card?', value: isCreditCard === 'yes' ? 'Yes' : 'No' });

    if (isCreditCard === 'no') {
      const entityState = rf.get('entityState')?.value;
      if (entityState) items.push({ num: num++, label: 'Entity State', value: entityState });

      const entityDistrict = rf.get('entityDistrict')?.value;
      if (entityDistrict) items.push({ num: num++, label: 'Entity District', value: entityDistrict });

      const entityBranch = rf.get('entityBranch')?.value;
      if (entityBranch) items.push({ num: num++, label: 'Entity Branch', value: entityBranch });
    }

    return items;
  }

  get reviewComplaintStartNum(): number {
    let num = 1;
    if (this.getComplaintCategoryLabel() && this.getComplaintCategoryLabel() !== '—') num++;
    if (this.facade.formData['subCategory1']) num++;
    if (this.facade.formData['subCategory2']) num++;
    return num;
  }

  get reviewDisputeDateNum(): number {
    let num = this.reviewComplaintStartNum;
    if (this.complaintDetailsForm.get('complaintText')?.value) num++;
    if (this.complaintDetailsForm.get('hasAccountWithRE')?.value) num++;
    if (this.complaintDetailsForm.get('isWalletComplaint')?.value) num++;
    if (this.complaintDetailsForm.get('isBusinessCorrespondent')?.value) num++;
    return num;
  }

  get reviewComplaintAmountStart(): number {
    let num = this.reviewDisputeDateNum;
    if (this.facade.dateDisplay['disputeDate']) num++;
    return num;
  }

  get reviewComplaintDocNum(): number {
    let num = this.reviewComplaintAmountStart;
    if (this.displayAmount('disputeAmount')) num++;
    if (this.displayAmount('compensationSought')) num++;
    if (this.displayAmount('reliefSought')) num++;
    return num;
  }

  get reviewComplaintItems(): { num: number; label: string; value: string; fullRow?: boolean }[] {
    const items: { num: number; label: string; value: string; fullRow?: boolean }[] = [];
    let num = 1;
    const cd = this.complaintDetailsForm;

    const category = this.getComplaintCategoryLabel();
    if (category && category !== '—') items.push({ num: num++, label: 'Complaint Category', value: category });

    const facts = cd.get('complaintText')?.value;
    if (facts) items.push({ num: num++, label: 'Facts of the complaint', value: facts, fullRow: true });

    const hasAccount = cd.get('hasAccountWithRE')?.value;
    if (hasAccount) items.push({ num: num++, label: `Do you have an account with ${this.facade.selectedEntityName}?`, value: hasAccount === 'yes' ? 'Yes' : 'No' });

    const isWallet = cd.get('isWalletComplaint')?.value;
    if (isWallet) items.push({ num: num++, label: 'Is your complaint against a Wallet transaction?', value: isWallet === 'yes' ? 'Yes' : 'No' });

    const isBusiness = cd.get('isBusinessCorrespondent')?.value;
    if (isBusiness) items.push({ num: num++, label: 'Is your complaint against a Business Correspondent?', value: isBusiness === 'yes' ? 'Yes' : 'No' });

    const disputeDate = this.facade.dateDisplay['disputeDate'];
    if (disputeDate) items.push({ num: num++, label: 'Date of the Disputed Transaction', value: disputeDate });

    const dispute = this.displayAmount('disputeAmount');
    if (dispute) items.push({ num: num++, label: 'Amount Involved in the Dispute, If Any', value: '₹' + dispute });

    const compensation = this.displayAmount('compensationSought');
    if (compensation) items.push({ num: num++, label: 'Compensation Sought For Dispute, If Any', value: '₹' + compensation });

    const relief = this.displayAmount('reliefSought');
    if (relief) items.push({ num: num++, label: 'Compensation For Harassment, If Any', value: '₹' + relief });

    return items;
  }

  get reviewRepItems(): { num: number; label: string; value: string }[] {
    const items: { num: number; label: string; value: string }[] = [];
    let num = 1;
    const ra = this.repAuthorizationForm;

    const hasRep = ra.get('hasAuthRep')?.value;
    if (hasRep) items.push({ num: num++, label: 'Is the complaint being filed through an Authorised Representative on behalf of you / complainant?', value: hasRep === 'yes' ? 'Yes' : 'No' });

    if (hasRep === 'yes') {
      const repName = ra.get('repName')?.value;
      if (repName) items.push({ num: num++, label: 'Representative Name', value: repName });

      const repPhone = ra.get('repPhone')?.value;
      if (repPhone) items.push({ num: num++, label: 'Phone', value: '+91 ' + repPhone });

      const repEmail = ra.get('repEmail')?.value;
      if (repEmail) items.push({ num: num++, label: 'Email', value: repEmail });

      const repPincode = ra.get('repPincode')?.value;
      if (repPincode) items.push({ num: num++, label: 'Pincode', value: repPincode });

      const repState = ra.get('repState')?.value;
      if (repState) items.push({ num: num++, label: 'State', value: repState });

      const repDistrict = ra.get('repDistrict')?.value;
      if (repDistrict) items.push({ num: num++, label: 'District', value: repDistrict });

      const repCity = ra.get('repCity')?.value;
      if (repCity) items.push({ num: num++, label: 'City', value: repCity });

      const repAddress = ra.get('repAddress')?.value;
      if (repAddress) items.push({ num: num++, label: 'Address', value: repAddress });
    }

    return items;
  }

  isIndividualCategory(): boolean {
    const cat = this.facade.complainantDetailsForm.controls.complaintCategory.value;
    return cat === 'individual' || cat === 'senior_citizen' || cat === 'pwd';
  }

  getCategoryLabel(): string {
    return CATEGORY_LABEL_MAP[this.facade.formData['complainantCategory']] || this.facade.formData['complainantCategory'] || '—';
  }

  getGenderLabel(): string {
    return GENDER_LABEL_MAP[this.facade.formData['gender']] || this.facade.formData['gender'] || '—';
  }

  getComplaintCategoryLabel(): string {
    const cat = this.facade.categories.find(c => c.value === this.facade.formData['complaintCategory']);
    return cat?.label || this.facade.formData['complaintCategory'] || '—';
  }

  getSelectedAccountTypesLabel(): string {
    const selected = this.facade.accountTypes.filter(a => a.checked);
    if (selected.length === 0) return '';
    return selected.map(a => a.label).join(', ');
  }

  isAccountTypeSelected(type: string): boolean {
    return this.facade.accountTypes.find(at => at.value === type)?.checked ?? false;
  }

  displayAmount(field: string): string {
    const raw = this.facade.complaintDetailsForm.get(field)?.value || '';
    return raw ? this.facade.formatIndianNumber(raw) : '';
  }

  // ── Event handlers ──
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (this.accountTypeDropdownOpen && !target.closest('.multiselect-dropdown')) {
      this.accountTypeDropdownOpen = false;
    }
  }

  onCategoryChange() {
    this.facade.formData['subCategory1'] = '';
    this.facade.formData['subCategory2'] = '';
  }

  get selectAllAccounts() { return this.facade.selectAllAccounts; }

  onAccountTypeToggle(accountType: AccountType) {
    this.facade.onAccountTypeToggle(accountType);
  }

  onSelectAllAccountTypes(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.facade.toggleSelectAllAccountTypes(checked);
  }

  onAmountInput(field: string, event: Event) {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/[^0-9]/g, '');
    const ctrl = this.facade.complaintDetailsForm.get(field);
    if (ctrl) {
      ctrl.setValue(raw, { emitEvent: true });
      ctrl.markAsTouched();
    }
    input.value = raw ? this.facade.formatIndianNumber(raw) : '';
    this.facade.formData[field] = raw ? this.facade.formatIndianNumber(raw) : '';
  }

  validateAge() {
    const ageStr = String(this.facade.formData['age'] || '');
    const age = Number(ageStr);
    if (ageStr && isNaN(age)) {
      this.facade.validationErrors['age'] = 'Age must be a number';
    } else if (ageStr.length > 3) {
      this.facade.validationErrors['age'] = 'Age must not exceed 3 digits';
    } else if (age < 1 || age > 150) {
      this.facade.validationErrors['age'] = 'Age must be between 1 and 150';
    } else if (this.facade.formData['complainantCategory'] === 'senior_citizen' && age < 60) {
      this.facade.validationErrors['age'] = 'Age must be 60 or above for Senior Citizen';
    } else {
      delete this.facade.validationErrors['age'];
    }
  }

  openDatePicker(event: Event) {
    const btn = event.currentTarget as HTMLElement;
    const hiddenInput = btn.parentElement?.querySelector('.date-hidden-picker') as HTMLInputElement;
    if (hiddenInput) hiddenInput.showPicker();
  }

  closeEntityDropdown() {
    setTimeout(() => this.facade.entityDropdownOpen = false, 200);
  }

  selectEntityFromDropdown(value: string) {
    if (value) this.facade.selectEligibilityAnswer(value);
  }

  previewFile(file: File | null) {
    if (file) {
      const url = URL.createObjectURL(file);
      window.open(url, '_blank');
    }
  }

  // ── Eligibility file handling (shared component) ──
  onEligibilityFileChanged(type: 'complaint' | 'reminder' | 'reply', files: File[]) {
    const file = files.length > 0 ? files[0] : null;
    if (file) {
      this.facade.uploadEligibilityFile(type, file);
    } else {
      this.facade.removeEligibilityFile(type);
    }
  }
  removeComplaintFile() { this.facade.removeEligibilityFile('complaint'); }
  removeReminderFile() { this.facade.removeEligibilityFile('reminder'); }
  removeReplyFile() { this.facade.removeEligibilityFile('reply'); }

  previewPersistedFile(url: string) {
    if (url) window.open(url, '_blank');
  }

  // ── File upload delegates ──
  onAttachmentsChanged(files: File[]) {
    const existingCount = this.facade.attachments.length;
    const newFiles = files.slice(existingCount);
    if (newFiles.length > 0) {
      this.facade.uploadAndSyncFiles(newFiles, 'complaintDetails');
    }
  }

  onAttachmentRemoved(index: number) {
    this.facade.removeUploadedFile('complaintDetails', index);
  }

  onRepFileChanged(files: File[]) {
    const existingCount = this.facade.repFiles.length;
    const newFiles = files.slice(existingCount);
    if (newFiles.length > 0) {
      this.facade.uploadAndSyncFiles(newFiles, 'repAuth');
    }
  }

  removeRepFile(index: number) {
    this.facade.removeUploadedFile('repAuth', index);
  }

  // ── Keyboard navigation ──
  onStepKeydown(event: KeyboardEvent) {
    if (event.key === 'Tab' && !event.shiftKey) {
      const focusable = document.querySelectorAll('.step-content input:not([disabled]), .step-content select:not([disabled]), .step-content textarea:not([disabled])');
      const last = focusable[focusable.length - 1] as HTMLElement;
      if (document.activeElement === last) {
        event.preventDefault();
        if (this.facade.currentStep() < this.totalSteps) this.facade.nextStep();
      }
    }
  }

  // ── PDF: Closure letter ──
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
      doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, 20, y);
      y += 14;
      const entityName = this.facade.selectedEntityName || 'the Regulated Entity';

      if (this.facade.isCEPCEntity) {
        const cepcContent = this.facade.closureLetterPara2();
        const cepcParas = cepcContent.split('\n');
        for (const para of cepcParas) {
          const pLines = doc.splitTextToSize(para, pw - 40);
          doc.text(pLines, 20, y);
          y += pLines.length * 6 + 6;
        }
        y += 8;
        doc.text('Regards,', 20, y);
        y += 7;
        doc.setFont('helvetica', 'bold');
        doc.text('RBI CMS Team.', 20, y);
        y += 14;
        doc.setFont('helvetica', 'normal');
        doc.text('This is a system-generated letter and does not require a signature.', 20, y);
        y += 14;
      } else {
        const complainantName = this.facade.complainantDetailsForm.get('firstName')?.value
          ? `${this.facade.complainantDetailsForm.get('firstName')?.value || ''} ${this.facade.complainantDetailsForm.get('lastName')?.value || ''}`.trim()
          : 'Complainant';
        doc.text(`Dear ${complainantName},`, 20, y);
        y += 10;

        const bodyPara1 = `Please refer to your representation alleging deficiency in service on the part of ${entityName}.`;
        const lines1 = doc.splitTextToSize(bodyPara1, pw - 40);
        doc.text(lines1, 20, y);
        y += lines1.length * 6 + 6;

        const bodyPara2 = `2. ${this.facade.closureLetterPara2()}`;
        const lines2 = doc.splitTextToSize(bodyPara2, pw - 40);
        doc.text(lines2, 20, y);
        y += lines2.length * 6 + 6;

        const bodyPara3 = `3. Accordingly, we regret to inform you that your present grievance against ${entityName} cannot be registered under the Scheme. In case the response was furnished erroneously, you may submit a fresh complaint.`;
        const lines3 = doc.splitTextToSize(bodyPara3, pw - 40);
        doc.text(lines3, 20, y);
        y += lines3.length * 6 + 14;

        doc.text('Regards,', 20, y);
        y += 7;
        doc.setFont('helvetica', 'bold');
        doc.text('RBI CMS Team.', 20, y);
        y += 14;
        doc.setFont('helvetica', 'normal');
        doc.text('This is a system-generated letter and does not require a signature.', 20, y);
        y += 14;
      }

      const caseId = this.facade.nonMaintainableCaseId;
      if (caseId) {
        doc.setFontSize(10);
        doc.setFont('helvetica', 'bold');
        doc.text(`Case ID: ${caseId}`, 20, y);
        y += 12;
      }

      doc.setDrawColor(0, 100, 0);
      doc.setFillColor(240, 255, 240);
      doc.roundedRect(20, y, pw - 40, 12, 2, 2, 'FD');
      doc.setTextColor(0, 100, 0);
      doc.setFontSize(8);
      doc.setFont('helvetica', 'bold');
      doc.text('DIGITALLY SIGNED | RBI CMS Digital Certificate Authority', 25, y + 8);
      doc.setTextColor(0);

      doc.save(caseId ? `Closure_Letter_${caseId}.pdf` : 'Closure_Letter.pdf');
    });
  }

  // ── PDF: List of Non-Maintainable Clauses (FR-G-013) ──
  downloadNonMaintainableClausesList() {
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
      doc.text('LIST OF NON-MAINTAINABLE CLAUSES', pw / 2, y, { align: 'center' });
      y += 14;

      doc.setFontSize(10);
      for (const clause of NON_MAINTAINABLE_CLAUSES) {
        doc.setFont('helvetica', 'bold');
        doc.text(`Clause ${clause.clauseCode}`, 20, y);
        y += 6;
        doc.setFont('helvetica', 'normal');
        const lines = doc.splitTextToSize(clause.description, pw - 40);
        doc.text(lines, 20, y);
        y += lines.length * 6 + 8;
        if (y > doc.internal.pageSize.getHeight() - 20) {
          doc.addPage();
          y = 20;
        }
      }

      doc.save('Non_Maintainable_Clauses.pdf');
    });
  }

  // ── PDF: Build off-screen HTML matching step 6 review layout, capture with html2canvas ──
  async downloadAcknowledgement(isDraft = false) {
    const html2canvas = (await import('html2canvas')).default;
    const { jsPDF } = await import('jspdf');

    const container = document.createElement('div');
    container.style.cssText = 'position:fixed;left:-9999px;top:0;width:800px;background:#fff;padding:32px;font-family:Poppins,sans-serif;color:#1e293b;';
    document.body.appendChild(container);

    const esc = (v: string) => {
      const d = document.createElement('div');
      d.textContent = v;
      return d.innerHTML;
    };

    const buildSection = (title: string, bodyHtml: string) =>
      `<div style="border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;margin-bottom:16px;">
        <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 20px;background:rgba(237,249,236,0.85);border-bottom:1px solid #d4edda;">
          <h3 style="font-size:15px;font-weight:600;color:#1e293b;margin:0;">${esc(title)}</h3>
        </div>
        <div style="padding:18px 24px;display:flex;flex-direction:column;gap:16px;">${bodyHtml}</div>
      </div>`;

    const buildNumberedItem = (num: number, label: string, value: string, subHtml = '') =>
      `<div style="display:flex;flex-direction:column;gap:4px;padding-bottom:16px;border-bottom:1px solid #f1f5f9;">
        <span style="font-size:13.5px;font-weight:500;color:#475569;"><span style="font-weight:600;margin-right:4px;">${num}.</span> ${esc(label)}</span>
        <span style="font-size:14px;font-weight:600;color:#1e293b;padding-left:20px;">${esc(value)}</span>
        ${subHtml}
      </div>`;

    const buildSubItem = (prefix: string, label: string, value: string) =>
      `<div style="display:flex;flex-direction:column;gap:2px;">
        <span style="font-size:13px;font-weight:500;color:#64748b;"><span style="font-weight:600;margin-right:4px;">${esc(prefix)}.</span> ${esc(label)}</span>
        <span style="font-size:13.5px;font-weight:600;color:#1e293b;padding-left:16px;">${esc(value)}</span>
      </div>`;

    const buildGridItem = (num: number, label: string, value: string) =>
      `<div style="display:flex;flex-direction:column;gap:4px;">
        <span style="font-size:12.5px;font-weight:500;color:#64748b;"><span style="font-weight:600;margin-right:4px;">${num}.</span> ${esc(label)}</span>
        <span style="font-size:14px;font-weight:500;color:#1e293b;padding-left:20px;">${esc(value)}</span>
      </div>`;

    let html = '';

    // Header — RBI branding
    html += `<div style="text-align:center;margin-bottom:8px;">
      <h1 style="font-size:18px;font-weight:700;color:#000;margin:0 0 4px;letter-spacing:0.5px;">RESERVE BANK OF INDIA</h1>
      <p style="font-size:12px;color:#333;margin:2px 0;">Integrated Ombudsman Scheme, 2026</p>
      <h2 style="font-size:15px;font-weight:700;color:#000;margin:6px 0 0;">${isDraft ? 'COMPLAINT DETAILS — DRAFT' : 'COMPLAINT ACKNOWLEDGEMENT'}</h2>
    </div>
    <hr style="border:none;border-top:1px solid #000;margin:8px 0 12px;">`;

    // Date row
    html += `<div style="display:flex;justify-content:flex-end;margin-bottom:16px;font-size:12px;color:#333;">
      <span>Date: ${new Date().toLocaleDateString('en-IN')}</span>
    </div>`;

    // Eligibility Details
    const eligItems = this.reviewEligibilityItems;
    if (eligItems.length > 0) {
      let body = '';
      for (const item of eligItems) {
        let subHtml = '';
        if (item.subItems) {
          subHtml = '<div style="padding-left:24px;margin-top:6px;display:flex;flex-direction:column;gap:6px;">';
          for (const sub of item.subItems) {
            subHtml += buildSubItem(sub.prefix, sub.label, sub.fileMeta ? sub.fileMeta.fileName : sub.value);
          }
          subHtml += '</div>';
        }
        body += buildNumberedItem(item.num, item.question, item.answer, subHtml);
      }
      html += buildSection('Eligibility Details', body);
    }

    // Complainant Details
    {
      let body = '';
      const catLabel = this.getCategoryLabel();
      if (catLabel && catLabel !== '—') {
        body += buildNumberedItem(1, 'Complainant Category', catLabel);
      }
      const fieldItems = this.reviewComplainantFieldItems;
      if (fieldItems.length > 0) {
        body += '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:20px;">';
        for (const item of fieldItems) {
          body += buildGridItem(item.num, item.label, item.value);
        }
        body += '</div>';
      }
      html += buildSection('Complainant Details', body);
    }

    // Regulated Entity Details
    {
      let body = '';
      const reItems = this.reviewRegulatedEntityItems;
      for (const item of reItems) {
        if (item.num <= 2) {
          body += buildNumberedItem(item.num, item.label, item.value);
        }
      }
      const gridItems = reItems.filter(i => i.num > 2);
      if (gridItems.length > 0) {
        body += '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:20px;">';
        for (const item of gridItems) {
          body += buildGridItem(item.num, item.label, item.value);
        }
        body += '</div>';
      }
      html += buildSection('Regulated Entity Details', body);
    }

    // Complaint Details
    {
      let body = '';
      const cItems = this.reviewComplaintItems;
      for (const item of cItems) {
        if (item.fullRow) {
          body += buildNumberedItem(item.num, item.label, item.value);
        }
      }
      const gridCItems = cItems.filter(i => !i.fullRow);
      if (gridCItems.length > 0) {
        body += '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:20px;">';
        for (const item of gridCItems) {
          body += buildGridItem(item.num, item.label, item.value);
        }
        body += '</div>';
      }
      const uploadedDocs = this.complaintDetailsForm.controls.fileUpload.value;
      if (uploadedDocs && uploadedDocs.length > 0) {
        body += `<div style="display:flex;flex-direction:column;gap:4px;padding-top:8px;border-top:1px solid #f1f5f9;">
          <span style="font-size:13.5px;font-weight:500;color:#475569;"><span style="font-weight:600;margin-right:4px;">${this.reviewComplaintDocNum}.</span> Uploaded Documents</span>
          <div style="display:flex;flex-wrap:wrap;gap:12px;padding-left:20px;">`;
        for (const f of uploadedDocs) {
          body += `<div style="display:flex;align-items:center;gap:10px;padding:10px 16px;border-radius:8px;background:#f8fafc;border:1px solid #e2e8f0;">
            <span style="font-size:13px;font-weight:500;color:#1e293b;">${esc(f.fileName)}</span>
            ${f.fileSize ? `<span style="font-size:11px;color:#94a3b8;">${(f.fileSize / 1024).toFixed(0)} KB</span>` : ''}
          </div>`;
        }
        body += '</div></div>';
      }
      html += buildSection('Complaint Details', body);
    }

    // Representative Authorisation
    {
      const repItems = this.reviewRepItems;
      if (repItems.length > 0) {
        let body = '';
        const fullItem = repItems.find(i => i.num === 1);
        if (fullItem) body += buildNumberedItem(fullItem.num, fullItem.label, fullItem.value);
        const gridItems = repItems.filter(i => i.num > 1);
        if (gridItems.length > 0) {
          body += '<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:20px;">';
          for (const item of gridItems) {
            body += buildGridItem(item.num, item.label, item.value);
          }
          body += '</div>';
        }
        const repDocs = this.repAuthorizationForm.controls.repFileUpload?.value;
        if (repDocs && repDocs.length > 0) {
          body += `<div style="margin-top:8px;display:flex;flex-direction:column;gap:4px;">
            <span style="font-size:13.5px;font-weight:500;color:#475569;"><span style="font-weight:600;margin-right:4px;">${repItems.length + 1}.</span> Uploaded Documents</span>
            <div style="display:flex;flex-wrap:wrap;gap:12px;padding-left:20px;">`;
          for (const f of repDocs) {
            body += `<div style="display:flex;align-items:center;gap:10px;padding:10px 16px;border-radius:8px;background:#f8fafc;border:1px solid #e2e8f0;">
              <span style="font-size:13px;font-weight:500;color:#1e293b;">${esc(f.fileName)}</span>
              ${f.fileSize ? `<span style="font-size:11px;color:#94a3b8;">${(f.fileSize / 1024).toFixed(0)} KB</span>` : ''}
            </div>`;
          }
          body += '</div></div>';
        }
        html += buildSection('Representative Authorisation', body);
      }
    }

    // Declaration
    {
      let body = '';
      body += `<div style="display:flex;align-items:flex-start;gap:12px;padding-bottom:12px;">
        <span style="font-size:18px;color:#409A31;flex-shrink:0;">&#10003;</span>
        <span style="font-size:14px;color:#334155;line-height:1.7;">
          (i) I/ We, the complainant/s herein declare that:<br>
          &nbsp;&nbsp;&nbsp;&nbsp;a) the information furnished above is true and correct; and<br>
          &nbsp;&nbsp;&nbsp;&nbsp;b) I/We have not concealed or misrepresented any fact stated above and in the documents submitted herewith.
        </span>
      </div>`;
      body += `<div style="display:flex;align-items:flex-start;gap:12px;">
        <span style="font-size:18px;color:#409A31;flex-shrink:0;">&#10003;</span>
        <span style="font-size:14px;color:#334155;line-height:1.7;">
          (ii) The complaint is filed before the expiry of a period of one year reckoned in accordance with the provisions of clause 10 (2) of the Scheme.
        </span>
      </div>`;
      html += buildSection('Declaration', body);
    }

    container.innerHTML = html;

    const canvas = await html2canvas(container, {
      scale: 1.5,
      useCORS: true,
      logging: false,
      backgroundColor: '#ffffff',
      width: 800,
    });

    document.body.removeChild(container);

    const pdfWidth = 210;
    const pdfHeight = 297;
    const margin = 10;
    const contentWidth = pdfWidth - margin * 2;
    const imgWidth = canvas.width;
    const imgHeight = canvas.height;

    const doc = new jsPDF('p', 'mm', 'a4');
    const pageContentHeight = pdfHeight - margin * 2;

    // Prepare watermark: draw it onto a transparent PNG canvas so it won't cover content
    const watermarkImg = new Image();
    watermarkImg.src = 'assets/draft-watermark.jpg';
    await new Promise<void>((resolve) => {
      watermarkImg.onload = () => resolve();
      watermarkImg.onerror = () => resolve();
    });

    let wmData: string | null = null;
    if (watermarkImg.complete && watermarkImg.naturalWidth > 0) {
      const wmCanvas = document.createElement('canvas');
      wmCanvas.width = watermarkImg.naturalWidth;
      wmCanvas.height = watermarkImg.naturalHeight;
      const wmCtx = wmCanvas.getContext('2d')!;
      wmCtx.clearRect(0, 0, wmCanvas.width, wmCanvas.height);
      wmCtx.globalAlpha = 0.15;
      wmCtx.drawImage(watermarkImg, 0, 0);
      wmData = wmCanvas.toDataURL('image/png');
    }

    let remainingHeight = imgHeight;
    let sourceY = 0;
    let page = 0;
    while (remainingHeight > 0) {
      if (page > 0) doc.addPage();

      // Draw watermark FIRST (background layer) so content renders on top
      if (wmData) {
        doc.addImage(wmData, 'PNG', 0, 0, pdfWidth, pdfHeight, undefined, 'MEDIUM');
      }

      // Draw content slice on top of watermark
      const sliceHeight = Math.min(remainingHeight, (pageContentHeight / contentWidth) * imgWidth);
      const sliceCanvas = document.createElement('canvas');
      sliceCanvas.width = imgWidth;
      sliceCanvas.height = sliceHeight;
      const ctx = sliceCanvas.getContext('2d')!;
      ctx.drawImage(canvas, 0, sourceY, imgWidth, sliceHeight, 0, 0, imgWidth, sliceHeight);
      const sliceData = sliceCanvas.toDataURL('image/jpeg', 0.8);
      const sliceScaledHeight = (sliceHeight * contentWidth) / imgWidth;
      doc.addImage(sliceData, 'JPEG', margin, margin, contentWidth, sliceScaledHeight, undefined, 'MEDIUM');

      sourceY += sliceHeight;
      remainingHeight -= sliceHeight;
      page++;
    }

    const fileName = this.facade.referenceNumber ? `Complaint_${this.facade.referenceNumber}.pdf` : 'Draft.pdf';
    doc.save(fileName);
  }

  // ── PDF: Complaint Status Report (success page — same format as tracker) ──
  downloadStatusReport() {
    const refNum = this.facade.referenceNumber;
    if (!refNum) return;

    this.complaintService.trackComplaint(refNum).pipe(take(1)).subscribe({
      next: (s) => {
        import('jspdf').then(({ jsPDF }) => {
          const doc = new jsPDF();
          const pw = doc.internal.pageSize.getWidth();
          let y = 20;

          doc.setFontSize(14);
          doc.setFont('helvetica', 'bold');
          doc.text('COMPLAINT STATUS REPORT', pw / 2, y, { align: 'center' });
          y += 10;
          doc.setFontSize(10);
          doc.setFont('helvetica', 'normal');
          doc.text(`Generated: ${new Date().toLocaleString('en-IN')}`, pw / 2, y, { align: 'center' });
          y += 12;

          const addRow = (label: string, value: string) => {
            doc.setFont('helvetica', 'bold');
            doc.text(`${label}:`, 20, y);
            doc.setFont('helvetica', 'normal');
            doc.text(value || 'N/A', 70, y);
            y += 7;
          };

          addRow('Complaint ID', s.complaintId);
          addRow('Status', s.status);
          addRow('Category', s.category);
          addRow('Registered', s.registeredAt ? new Date(s.registeredAt).toLocaleDateString('en-IN') : '');
          addRow('SLA Due', s.slaDueDate ? new Date(s.slaDueDate).toLocaleDateString('en-IN') : '');
          if (s.assignedTeam) addRow('Assigned Team', s.assignedTeam);
          if (s.resolutionSummary) addRow('Resolution', s.resolutionSummary);

          if (s.timeline && s.timeline.length > 0) {
            y += 5;
            doc.setFont('helvetica', 'bold');
            doc.text('Timeline:', 20, y);
            y += 7;
            doc.setFont('helvetica', 'normal');
            for (const entry of s.timeline) {
              if (y > 270) { doc.addPage(); y = 20; }
              doc.text(`${new Date(entry.timestamp).toLocaleDateString('en-IN')} - ${entry.action} (${entry.fromStatus} -> ${entry.toStatus})`, 25, y);
              y += 6;
            }
          }

          y += 10;
          doc.setDrawColor(0, 100, 0);
          doc.setFillColor(240, 255, 240);
          doc.roundedRect(20, y, pw - 40, 14, 2, 2, 'FD');
          doc.setTextColor(0, 100, 0);
          doc.setFontSize(8);
          doc.setFont('helvetica', 'bold');
          doc.text('DIGITALLY SIGNED | RBI CMS Digital Certificate Authority', 25, y + 9);
          doc.setTextColor(0);

          doc.save(`Complaint_${s.complaintId}.pdf`);
        });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to fetch complaint details for PDF.', life: 3000 });
      }
    });
  }

  // ── Navigation ──
  trackComplaint() {
    this.router.navigate(['/public/track', this.facade.referenceNumber]);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  withdrawComplaint() {
    this.router.navigate(['/public/withdraw', this.facade.referenceNumber]);
  }
}
