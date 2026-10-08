import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
  ViewChild,
  DestroyRef,
} from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Router } from "@angular/router";
import { of, Subject } from "rxjs";
import { catchError, debounceTime, finalize, switchMap } from "rxjs/operators";
import { KeycloakAuthService } from "../../../services/keycloak-auth.service";
import { NavigationService } from "../../../services/navigation.service";
import { CepcContextService } from "../../../services/cepc-context.service";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import {
  AdvancedSearchCriteria,
  ComplaintColumn,
  ComplaintColumnFilters,
  KpiCounts,
  SelectedFilters,
  TabCounts,
  TableQueryMetadata,
} from "../../../models/cepc.model";
import {
  CepcAdvancedSearchComponent,
} from "../cepc-advanced-search/cepc-advanced-search.component";
import { CepcDashboardHeaderComponent } from "../cepc-dashboard-header/cepc-dashboard-header.component";
import { CepcDashboardKpiComponent } from "../cepc-dashboard-kpi/cepc-dashboard-kpi.component";
import { CepcDashboardTabsComponent } from "../cepc-dashboard-tabs/cepc-dashboard-tabs.component";
import { ApiService } from "../../../services/api.service";
import { environment } from "../../../../environments/environment";

type FilterType = 'AdvancedSearchFilter' | 'DashboardFilter';

// Must match a STATUS_CODE in ROLE_STATUS_MAPPING. Sent on the first request, before the dropdown has
// loaded, so it has to be a code every role holds — ALL_COMPLAINTS is seeded first for all five.
const DEFAULT_STATUS_CODE = 'ALL_COMPLAINTS';

@Component({
  selector: "app-cepc-dashboard",
  standalone: true,
  imports: [
    CepcDashboardHeaderComponent,
    CepcDashboardKpiComponent,
    CepcDashboardTabsComponent,
    CepcAdvancedSearchComponent,
  ],
  templateUrl: "./cepc-dashboard.component.html",
  styleUrl: "./cepc-dashboard.component.scss",
})
export class CepcDashboardComponent implements OnInit, OnDestroy {
  @ViewChild("advSearchModal") advSearchModal!: CepcAdvancedSearchComponent;

  private readonly http = inject(HttpClient);
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);
  private readonly navService = inject(NavigationService);
  private readonly auth = inject(KeycloakAuthService);
  readonly dept = inject(CepcContextService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly complaintsSearchUrl = `${environment.apiBaseUrl}/api/v1/search/complaints/search`;

  private readonly masterQueryStream$ = new Subject<void>();

  readonly complaints = signal<ComplaintColumn[]>([]);
  readonly isSearching = signal<boolean>(false);
  readonly advSearchActive = signal<boolean>(false);
  readonly showAdvancedSearch = signal<boolean>(false);
  readonly statusCodes = signal<{ label: string; value: string; labelKey?: string }[]>([]);
  readonly totalRecordsCount = signal<number>(0);
  readonly KpiMetricsObject = signal<KpiCounts>({
    totalPendingComplaints: 0,
    pendingWithMe: 0,
    pendingWithRe: 0,
    pendingAtMeetingScheduled: 0,
    slaBreached: 0,
    sla0To15Days: 0,
    sla16To30Days: 0
  });

  readonly dashboardTabCountsObject = signal<TabCounts>({
    draft: 0,
    meetingScheduled: 0,
    sentBackToMe: 0,
    sentToRe: 0,
    responseFromCp: 0,
    withdrawnComplaints: 0
  });

  readonly selectedDashboardTab = signal<string | number>("0");
  readonly selectedStatusCode = signal<string | null>(DEFAULT_STATUS_CODE);
  readonly currentKpiFilter = signal<string | null>(null);
  readonly showUnreadOnly = signal<boolean>(false);
  readonly showWithoutAttachments = signal<boolean>(false);

  readonly currentPage = signal<number>(1);
  readonly rowsPerPage = signal<number>(10);
  sortField: string | null = "createdDate";
  sortOrder: "asc" | "desc" = "desc";

  private cachedColumnFilters: ComplaintColumnFilters | null = null;
  private cachedAdvancedSearchPayload: AdvancedSearchCriteria | null = null;
  readonly cachedSelectedFilter = signal<SelectedFilters | null>(null);

  readonly activeFilterCount = computed(() => {
    const f = this.cachedSelectedFilter();
    if (!f) return 0;
    return Object.values(f).reduce((sum, arr) => sum + arr.length, 0);
  });

  readonly selectedIds = signal<Set<string>>(new Set());

  loggedInUser: { id: string; name: string; role: string } | null = null;
  readonly userRole = signal<string>("SUPERVISOR");

  constructor() {
    this.initializeMasterSearchPipeline();
  }

  ngOnInit(): void {
    this.hydrateUserAndSessionState();
    this.loadStatusCodes();
  }

  private initializeMasterSearchPipeline(): void {
    this.masterQueryStream$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          this.isSearching.set(true);

          const urlParams = new HttpParams()
            .set("page", (this.currentPage() - 1).toString())
            .set("size", this.rowsPerPage().toString())
            .set("sort", `${this.sortField ?? "createdDate"},${this.sortOrder.toString()}`);

          const cleanPayloadKeys = (obj: any): any => {
            if (!obj || typeof obj !== 'object') return obj;

            const cleaned: any = {};
            Object.keys(obj).forEach(key => {
              const val = obj[key];

              const isValidValue = val !== null && val !== undefined && String(val).trim() !== '';
              const isNonEmptyArray = Array.isArray(val) && val.length > 0;
              const isNonEmptyObject = val && typeof val === 'object' && !Array.isArray(val) && Object.keys(cleanPayloadKeys(val)).length > 0;

              if (isNonEmptyArray) {
                cleaned[key] = val;
              } else if (isNonEmptyObject) {
                cleaned[key] = cleanPayloadKeys(val);
              } else if (!Array.isArray(val) && typeof val !== 'object' && isValidValue) {
                cleaned[key] = val;
              }
            });
            return cleaned;
          };

          const rawPayload = {
            advancedSearch: {
              complaintNumber: this.cachedAdvancedSearchPayload?.complaintNumber || null,
              complainantName: this.cachedAdvancedSearchPayload?.complainantName || null,
              id: this.cachedAdvancedSearchPayload?.id ? Number(this.cachedAdvancedSearchPayload.id) : null,
              complainantPhone: this.cachedAdvancedSearchPayload?.complainantPhone || null,
              complainantEmail: this.cachedAdvancedSearchPayload?.complainantEmail || null,
              filingType: this.cachedAdvancedSearchPayload?.filingType || null,
              entityName: this.cachedAdvancedSearchPayload?.entityName || null,
              subject: this.cachedAdvancedSearchPayload?.subject || null,
              categoryId: this.cachedAdvancedSearchPayload?.categoryId ? Number(this.cachedAdvancedSearchPayload.categoryId) : null,
              filedAt: this.cachedAdvancedSearchPayload?.filedAt || null,
              nodalOfficerName: this.cachedAdvancedSearchPayload?.nodalOfficerName || null,
              fromEmailId: this.cachedAdvancedSearchPayload?.fromEmailId || null
            },
            filters: {
              states: this.cachedSelectedFilter()?.states || [],
              districts: this.cachedSelectedFilter()?.districts || [],
              years: this.cachedSelectedFilter()?.years || [],
              quarters: this.cachedSelectedFilter()?.quarters || [],
              meetingTypes: this.cachedSelectedFilter()?.meetingTypes || [],
              documentTypes: this.cachedSelectedFilter()?.documentTypes || []
            },
            statusCode: this.selectedStatusCode(),
            kpi_cards: this.currentKpiFilter(),
            tabs: this.translateTabIdToStatus(this.selectedDashboardTab()),
            unread: this.showUnreadOnly(),
            withoutAttachments: this.showWithoutAttachments(),
            search: {
              complaintId: this.cachedColumnFilters?.complaintId || null,
              complaintNumber: this.cachedColumnFilters?.complaintNumber || null,
              assignedTo: this.cachedColumnFilters?.assignedTo || null,
              slaBreachIn: this.cachedColumnFilters?.slaBreachIn || null,
              mode: this.cachedColumnFilters?.mode || null,
              complainantName: this.cachedColumnFilters?.complainantName || null,
              status: this.cachedColumnFilters?.status || null,
              entityName: this.cachedColumnFilters?.entityName || null,
              complaintCategory: this.cachedColumnFilters?.complaintCategory || null,
              createdDate: this.cachedColumnFilters?.createdDate || null,
              lastUpdatedDate: this.cachedColumnFilters?.lastUpdatedDate || null,
              priority: this.cachedColumnFilters?.priority || null,
              subject: this.cachedColumnFilters?.subject || null
            }
          };

          const unifiedRequestBodyPayload = cleanPayloadKeys(rawPayload);

          return this.http
            .post<any>(
              this.complaintsSearchUrl,
              unifiedRequestBodyPayload,
              { params: urlParams },
            )
            .pipe(
              catchError((err) => {
                console.error(
                  "Unified dashboard search resolution failure:",
                  err,
                );
                this.complaints.set([]);
                this.totalRecordsCount.set(0);
                return of(null);
              }),
              finalize(() => this.isSearching.set(false)),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (response) => {
          if (!response || !response.success || !response.data?.complaints) return;

          const freshData = response.data.complaints.content || [];
          this.complaints.set(freshData);
          this.totalRecordsCount.set(response.data.complaints.totalElements || freshData.length);

          if (response.data.kpiCounts) {
            this.KpiMetricsObject.set(response.data.kpiCounts);
          }

          if (response.data.tabCounts) {
            this.dashboardTabCountsObject.set(response.data.tabCounts);
          }
        },
      });
  }

  /**
   * Emits a trigger token down the master pipeline to execute the search query
   */
  triggerUnifiedSearch(): void {
    this.masterQueryStream$.next();
  }

  /**
   * Fired when the inner datatable handles lazy configurations (Sorting / Pagination / Inline Column Filters)
   */
  executeServerSideSearchLookup(queryConfig: TableQueryMetadata): void {
    this.currentPage.set(queryConfig.page);
    this.rowsPerPage.set(queryConfig.size);
    this.sortField = queryConfig.sortField;
    this.sortOrder = queryConfig.sortOrder === "desc" ? "desc" : "asc";
    this.cachedColumnFilters = queryConfig.filters;
    this.showUnreadOnly.set(queryConfig.showUnreadOnly);
    this.showWithoutAttachments.set(queryConfig.showWithoutAttachments);

    this.triggerUnifiedSearch();
  }

  /**
   * Stands the two filter-search surfaces down when the other is used, and returns the status dropdown to
   * "All Complaints".
   *
   * <p>The dropdown is a secondary filter over whatever a filter search selected, not a competitor to it:
   * applying a search starts the status unrestricted, and the officer narrows from there. It is therefore
   * reset here rather than cleared, and `onStatusCodeChange` does not call this at all — picking a status
   * keeps the filter search in place.
   *
   * <p>The advanced-search modal and the filter panel do still displace each other. They are two
   * presentations of the same job, and their criteria overlap, so a district chosen in one and a different
   * district chosen in the other asks for complaints in two districts at once.
   */
  private clearAlternativeFilters(activeType: FilterType): void {
    if (activeType !== 'AdvancedSearchFilter') {
      this.cachedAdvancedSearchPayload = null;
      this.advSearchActive.set(false);
      if (this.advSearchModal) {
        this.advSearchModal.clearAllFilters();
      }
    }
    if (activeType !== 'DashboardFilter') {
      this.cachedSelectedFilter.set(null);
    }
    this.selectedStatusCode.set(DEFAULT_STATUS_CODE);
  }

  onStatusCodeChange(event: { value: any }): void {
    this.selectedStatusCode.set(event.value);
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  handleKpiFilteringChange(selectedKpiCardId: string | null): void {
    this.currentKpiFilter.set(selectedKpiCardId);
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  executeAdvancedFilterLookup(
    advancedSearchPayload: AdvancedSearchCriteria,
  ): void {
    this.clearAlternativeFilters('AdvancedSearchFilter');
    this.cachedAdvancedSearchPayload = advancedSearchPayload;
    this.advSearchActive.set(true);
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  handleAdvancedSearchClear(): void {
    this.cachedAdvancedSearchPayload = null;
    this.advSearchActive.set(false);
    if (this.advSearchModal) {
      this.advSearchModal.resetFiltersOnly();
    }
    this.cachedSelectedFilter.set(null);
    this.selectedStatusCode.set(DEFAULT_STATUS_CODE);
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  applyHeaderFilter(event: SelectedFilters): void {
    this.clearAlternativeFilters('DashboardFilter');
    const isEmpty = Object.values(event).every(arr => arr.length === 0);
    this.cachedSelectedFilter.set(isEmpty ? null : event);
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  handleFilterRemoval(updated: SelectedFilters): void {
    const isEmpty = Object.values(updated).every(arr => arr.length === 0);
    this.cachedSelectedFilter.set(isEmpty ? null : updated);
    if (isEmpty) {
      this.cachedAdvancedSearchPayload = null;
      this.advSearchActive.set(false);
      if (this.advSearchModal) {
        this.advSearchModal.clearAllFilters();
      }
      this.selectedStatusCode.set(DEFAULT_STATUS_CODE);
    }
    this.currentPage.set(1);
    this.triggerUnifiedSearch();
  }

  loadComplaints(): void {
    this.triggerUnifiedSearch();
  }

  private translateTabIdToStatus(tabId: string | number): string {
    switch (String(tabId)) {
      case "1":
        return "Draft";
      case "2":
        return "Meeting Scheduled";
      case "3":
        return "Sent Back to Me";
      case "4":
        return "Sent to RE";
      case "5":
        return "Contact Person";
      default:
        return "All";
    }
  }

  private loadStatusCodes(): void {
    const role = this.loggedInUser?.role || this.dept.primaryRole();
    this.isSearching.set(true);
    this.apiService
      .get<{ success: boolean; message: string;
        data: { code: string; label: string; labelKey?: string }[] }>(
        `/departments/${this.dept.cfg().code}/role-status`,
        { params: { role } },
      )
      .subscribe({
        next: (response) => {
          const entries = response.data || [];
          // A caller holding no CEPC role, or one whose role has no ROLE_STATUS_MAPPING rows, comes back
          // as an empty 200 rather than an error; without this the dropdown renders blank.
          if (entries.length === 0) {
            this.applyDefaultStatusCodes();
          } else {
            this.statusCodes.set(entries.map(({ code, label, labelKey }) => ({ label, value: code, labelKey })));
          }
          this.isSearching.set(false);
        },
        error: () => {
          this.applyDefaultStatusCodes();
          this.isSearching.set(false);
        },
      });
  }

  // Only ALL_COMPLAINTS. The previous fallback also offered "assigned to me", but that is seeded for
  // CEPC_ADMIN alone — offering it to every role that fails this call showed most of them a filter their
  // role is not given.
  private applyDefaultStatusCodes(): void {
    this.statusCodes.set([
      { label: "All Complaints", value: DEFAULT_STATUS_CODE, labelKey: "ui.filter.all_complaints" },
    ]);
  }

  private hydrateUserAndSessionState(): void {
    const stored = sessionStorage.getItem(this.dept.key("user"));
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        this.loggedInUser = {
          id: user.username,
          name: `${user.firstName} ${user.lastName}`.trim() || user.username,
          role: this.dept.primaryRole(),
        };
        sessionStorage.setItem(this.dept.key("user"), JSON.stringify(this.loggedInUser));
      }
    }
  }

  navigateToCreateComplaint(): void {
    this.navService.navigate(this.dept.route("create-complaint"));
  }

  ngOnDestroy(): void {
  }
}
