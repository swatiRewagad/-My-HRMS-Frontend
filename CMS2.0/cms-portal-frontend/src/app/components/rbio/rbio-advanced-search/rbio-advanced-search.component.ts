import { Component, model, output, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { forkJoin, Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, switchMap, tap } from 'rxjs/operators';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { InputText } from 'primeng/inputtext';
import { Button } from 'primeng/button';
import { ApiService } from '../../../services/api.service';
import { AdvancedSearchCriteria, Entity, SelectOption } from '../../../models/rbio.model';

@Component({
  selector: 'rbio-advanced-search',
  standalone: true,
  imports: [CommonModule, FormsModule, Dialog, Select, DatePicker, InputText, Button],
  providers: [DatePipe],
  templateUrl: './rbio-advanced-search.component.html',
  styleUrls: ['./rbio-advanced-search.component.scss']
})
export class RbioAdvancedSearchComponent implements OnInit, OnDestroy {
  private apiService = inject(ApiService);
  private readonly subscriptions = new Subscription();

  readonly visible = model<boolean>(false);
  readonly searchClear = output<void>();
  private readonly entitySearch$ = new Subject<string>();
  private readonly DEBOUNCE_DELAY_MS = 400;

  readonly searchTriggered = output<AdvancedSearchCriteria>();

  readonly maxDate: Date | undefined = new Date();

  statusOptions: SelectOption[] = [
    { label: 'Draft', value: 'DRAFT' },
    { label: 'New Complaint', value: 'NEW_COMPLAINT' },
    { label: 'Award Passed', value: 'AWARD_PASSED' },
    { label: 'Information Required', value: 'INFORMATION_REQUIRED' },
    { label: 'Sent RBI', value: 'SENT_TO_RBI' },
    { label: 'Sent To Other Regulated Bodies', value: 'SENT_TO_OTHER_REGULATED_BODIES' },
    { label: 'Sent To Other Departments', value: 'SENT_TO_OTHER_DEPARTMENTS' },
    { label: 'Sent To Other Office', value: 'SENT_TO_OTHER_OFFICE' },
    { label: 'Complaint Re Open', value: 'COMPLAINT_REOPEN' },
    { label: 'Complaint Rejected', value: 'COMPLAINT_REJECTED' },
    { label: 'Complaint Settled', value: 'COMPLAINT_SETTLED' },
    { label: 'Complaint Withdrawn', value: 'COMPLAINT_WITHDRAWN' },
    { label: 'Complaint Closed', value: 'COMPLAINT_CLOSED' },
    { label: 'Advisory Complied', value: 'ADVISORY_COMPLIED' },
    { label: 'Sent Back To Deputy Ombudsman', value: 'SENT_BACK_TO_DEPUTY_OMBUDSMAN' },
    { label: 'Sent Back To Reviewer', value: 'SENT_BACK_TO_REVIEWER' },
    { label: 'Sent Back To DO', value: 'SENT_BACK_TO_DO' },
    { label: 'Deputy Ombudsman Decision', value: 'DEPUTY_OMBUDSMAN_DECISION' },
    { label: 'Ombudsman Decision', value: 'OMBUDSMAN_DECISION' },
    { label: 'Sent To Deputy Ombudsman', value: 'SENT_TO_DEPUTY_OMBUDSMAN' },
    { label: 'Sent To Reviewer', value: 'SENT_TO_REVIEWER' },
    { label: 'Sent To Ombudsman', value: 'SENT_TO_OMBUDSMAN' }
  ];

  receiptOptions: SelectOption[] = [
    { label: 'Portal', value: 'PORTAL' },
    { label: 'Email', value: 'EMAIL' },
    { label: 'Letter', value: 'LETTER' }
  ];

  categoryOptions: SelectOption[] = [];
  entityOptions: SelectOption[] = [];

  isEntityLoading = false;
  isMasterDataLoading = false;

  private readonly bankApiUrl = '/banks';
  private readonly categoriesUrl = '/categories';

  advSearch: AdvancedSearchCriteria = this.getInitialSearchState();

  ngOnInit(): void {
    this.loadMasterDropdownData();
    this.initializeEntitySearchStream();
  }

  private getInitialSearchState(): AdvancedSearchCriteria {
    return {
      complaintNumber: null,
      complainantName: null,
      id: null,
      statusCode: null,
      complainantPhone: null,
      complainantEmail: null,
      filingType: null,
      entityName: null,
      subject: null,
      categoryId: null,
      filedAt: null,
      nodalOfficerName: null,
      fromEmailId: null
    };
  }

  private loadMasterDropdownData(): void {
    this.isMasterDataLoading = true;
    const parallelLoadSub = forkJoin({
      categories: this.apiService.get<any[]>(this.categoriesUrl)
    })
    .pipe(finalize(() => this.isMasterDataLoading = false))
    .subscribe({
      next: (response) => {
        this.categoryOptions = response.categories?.map(c => ({
          label: c.name,
          value: c.id ?? c.name ?? ''
        })) ?? [];
      },
      error: (err) => console.error('Failed to resolve master data dropdowns:', err)
    });
    this.subscriptions.add(parallelLoadSub);
  }

  onEntitySearch(event: { filter?: string }): void {
    const query = event?.filter?.trim() ?? '';

    if (!query) {
      this.entityOptions = [];
      this.isEntityLoading = false;
      return;
    }

    this.entitySearch$.next(query);
  }

  private initializeEntitySearchStream(): void {
    const searchPipelineSub = this.entitySearch$.pipe(
      debounceTime(this.DEBOUNCE_DELAY_MS),
      distinctUntilChanged(),
      switchMap((query: string) => {
        this.isEntityLoading = true;
        const params = new HttpParams().set('search', query);

        return this.apiService.get<Entity[]>(this.bankApiUrl, {
          params
        }).pipe(
          tap({
            error: (err) => {
              console.error('Bank pipeline background fetch broken:', err);
              this.entityOptions = [];
              this.isEntityLoading = false;
            }
          })
        );
      })
    ).subscribe({
      next: (data: Entity[]) => {
        this.entityOptions = data.map(item => ({
          label: item.name,
          value: item.code ?? item.name
        }));
        this.isEntityLoading = false;
      }
    });

    this.subscriptions.add(searchPipelineSub);
  }

  applyAdvancedSearch(): void {
    const rawData = this.advSearch;

    const payload: AdvancedSearchCriteria = {
      complaintNumber: null,
      complainantName: null,
      id: null,
      statusCode: null,
      complainantPhone: null,
      complainantEmail: null,
      filingType: null,
      entityName: null,
      subject: null,
      categoryId: null,
      filedAt: null,
      nodalOfficerName: null,
      fromEmailId: null
    };

    const hasValue = (val: any) => val !== null && val !== undefined && String(val).trim() !== '';
    const cleanString = (val: any) => val ? String(val).trim() : null;

    if (hasValue(rawData.complaintNumber)) {
      payload.complaintNumber = cleanString(rawData.complaintNumber);
    }
    if (hasValue(rawData.id)) {
      payload.id = Number(rawData.id);
    }
    if (hasValue(rawData.statusCode)) {
      payload.statusCode = cleanString(rawData.statusCode);
    }
    if (hasValue(rawData.complainantName)) {
      payload.complainantName = cleanString(rawData.complainantName);
    }
    if (hasValue(rawData.complainantPhone)) {
      payload.complainantPhone = cleanString(rawData.complainantPhone);
    }
    if (hasValue(rawData.complainantEmail)) {
      payload.complainantEmail = cleanString(rawData.complainantEmail);
    }
    if (hasValue(rawData.fromEmailId)) {
      payload.fromEmailId = cleanString(rawData.fromEmailId);
    }
    // Fixed: Checking correctly against the aligned field name 'filingType'
    if (hasValue(rawData.filingType)) {
      payload.filingType = cleanString(rawData.filingType);
    }
    if (hasValue(rawData.entityName)) {
      payload.entityName = cleanString(rawData.entityName);
    }
    if (hasValue(rawData.subject)) {
      payload.subject = cleanString(rawData.subject);
    }
    // Fixed: Checking correctly against the aligned field name 'categoryId'
    if (hasValue(rawData.categoryId)) {
      payload.categoryId = Number(rawData.categoryId);
    }
    if (hasValue(rawData.nodalOfficerName)) {
      payload.nodalOfficerName = cleanString(rawData.nodalOfficerName);
    }
    // Fixed: Replaced this.datePipe.transform with standalone formatDate utility
    if (hasValue(rawData.filedAt)) {
      payload.filedAt = formatDate(rawData.filedAt!, 'dd-MM-yyyy', 'en-US');
    }

    this.searchTriggered.emit(payload);
    this.visible.set(false);
  }

  onInternalClearClick(): void {
    this.resetFormStateOnly();
    this.searchClear.emit();
    this.visible.set(false);
  }

  clearAllFilters(): void {
    this.resetFormStateOnly();
    this.visible.set(false);
  }

  private resetFormStateOnly(): void {
    this.advSearch = this.getInitialSearchState();
    this.entityOptions = [];
    this.isEntityLoading = false;
  }

  allowOnlyNumbersOnKeypress(event: KeyboardEvent): boolean {
    if (event.key < '0' || event.key > '9') {
      event.preventDefault();
      return false;
    }
    return true;
  }

  allowOnlyNumbersOnPaste(event: ClipboardEvent): void {
    const pastedData = event.clipboardData?.getData('text') || '';
    const isNumericOnly = /^[0-9]+$/.test(pastedData);
    if (!isNumericOnly) {
      event.preventDefault();
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
