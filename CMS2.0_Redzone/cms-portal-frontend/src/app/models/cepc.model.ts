import { Rung } from '../services/cepc-context.service';

export interface DraftComplaintColumn {
    complaintId: number;
    assignedTo: string | null;
    mode: string;
    complainantName: string;
    status: string;
    entityName: string | null;
    complaintCategory: string | null;
    createdDate: string;
    lastUpdatedDate: string;
    priority: string;
}

export interface ComplaintColumn {
    complaintId: number;
    complaintNumber: string;
    assignedTo: string | null;
    slaBreachIn: string | null;
    mode: string;
    complainantName: string;
    status: string;
    entityName: string | null;
    complaintCategory: string | null;
    createdDate: string;
    lastUpdatedDate: string;
    priority: string;
    subject: string;
    contactPerson: string | null;
    /** Row tint colour word, appended to `cc-row-`. Null for the statuses that carry no tint. */
    complaintColor: string | null;
    isRead: boolean;
    slaColor: string | null;
    /** Status chip class, applied verbatim — see `.custom-status-badge` in the dashboard table's stylesheet. */
    statusColor: string;
    /** `status` rendered as words, e.g. "Pending Office Head Approval". Display only — branch on `status`. */
    statusLabel: string;
    /**
     * Translation key for `statusLabel`, e.g. `ui.status.pending_office_head_approval`.
     *
     * Resolved server-side by `CepcStatus.labelKey()` because three statuses need `workflowStage` to pick the
     * right wording, and the stage is not on this row. Null for a blank status; fall back to `statusLabel`.
     */
    statusLabelKey: string | null;
}

/**
 * The advance-search panel's criteria.
 *
 * <p>No `statusCode` here on purpose. The panel used to carry its own status selector, applied by a separate
 * backend predicate from the toolbar dropdown's, so the two competed and asking for one status in each returned
 * nothing. Status now has one axis: the toolbar dropdown, which narrows a filter search rather than fighting it.
 */
export interface AdvancedSearchCriteria {
    complaintNumber: string | null;
    complainantName: string | null;
    id: number | null;
    complainantPhone: string | null;
    complainantEmail: string | null;
    filingType: string | null;
    entityName: string | null;
    subject: string | null;
    categoryId: number | null;
    filedAt: string | null;
    nodalOfficerName: string | null;
    fromEmailId: string | null;
}

export interface ColumnDefinition {
    field: keyof ComplaintColumn | keyof DraftComplaintColumn;
    /** Translation key for the header, resolved through `translateOr` against `header`. */
    labelKey: string;
    /** The English header, used verbatim whenever `labelKey` is not seeded. Must not be changed. */
    header: string;
}

export interface Entity {
    id: number;
    name: string;
    code: string;
    type: string;
    status: string;
    createdAt: string;
}

export interface SelectOption {
    label: string;
    value: string | number;
}

export interface SelectedFilters {
    states: string[];
    districts: string[];
    years: string[];
    quarters: string[];
    meetingTypes: string[];
    documentTypes: string[];
}

export interface TabConfig {
    value: string;
    labelKey: string;
    /** The English tab label, used verbatim whenever `labelKey` is not seeded. */
    label: string;
    visibleFields?: (keyof ComplaintColumn)[];
    count?: number;
}

export interface StatusStyle {
    icon: string;
    class: string;
}

export interface FilterOption {
    label: string;
    value: string;
    /**
     * Translation key, for the option lists that are a fixed vocabulary rather than data.
     *
     * Absent on states, districts and financial years: those are place names and derived year ranges, which
     * have no key to point at and are not CEPC's to translate. Present on quarters.
     */
    labelKey?: string;
}

export interface FilterCategory {
    id: string;
    labelKey: string;
    /** The English category label, used verbatim whenever `labelKey` is not seeded. */
    label: string;
    options: FilterOption[];
}

export type ComplaintColumnFilters = Partial<Record<keyof ComplaintColumn,
string | null>>;

export interface TableQueryMetadata {
    page: number;
    size: number;
    sortField: string | null;
    sortOrder: 'asc' | 'desc' | null;
    filters: ComplaintColumnFilters | null;
    showUnreadOnly: boolean;
    showWithoutAttachments: boolean;
    tabValue: string | number;
}

export interface UnifiedDashboardSearchQuery {
    advancedSearch: AdvancedSearchCriteria;
    filters: SelectedFilters;
    statusCode: string;
    kpi_cards: string;
    tabs: string;
    unread: boolean;
    withoutAttachments: boolean;
    search: Partial<Record<keyof ComplaintColumn, string | null>>;
}

export interface MetricItem {
    labelKey: string;
    /** The English metric label, used verbatim whenever `labelKey` is not seeded. */
    label: string;
    value: number;
    colorClass?: 'color-red' | 'color-orange' | 'color-green';
}

export interface InternalKpiConfig {
    /** Emitted to the dashboard as the selected filter. An English token, not display text — never translate. */
    id: string;
    titleKey: string;
    /** The English tile title, used verbatim whenever `titleKey` is not seeded. */
    title: string;
    icon: string;
    styleClass: 'bg-blue' | 'bg-orange' | 'bg-red';

    requiredRungs: Rung[];
    layout: '1:1' | '2:1' | '3:1' | '4:1';
    metrics: MetricItem[];
    /** Whether clicking the tile filters the grid. The SLA tile reports only; see the KPI component. */
    selectable: boolean;
}

export interface ComplaintsPageable {
    content: ComplaintColumn[] | DraftComplaintColumn[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

export interface KpiCounts {
    totalPendingComplaints: number;
    pendingWithMe: number;
    pendingWithRe: number;
    pendingAtMeetingScheduled: number;
    slaBreached: number;
    sla0To15Days: number;
    sla16To30Days: number;
}

export interface TabCounts {
    draft: number;
    meetingScheduled: number;
    sentBackToMe: number;
    sentToRe: number;
    responseFromCp: number;
    withdrawnComplaints: number;
}

/** The dashboard payload. Wrap it as `ApiResponse<ApiResponseData>` from `models/api-response.model`. */
export interface ApiResponseData {
    complaints: ComplaintsPageable;
    kpiCounts: KpiCounts;
    tabCounts: TabCounts;
}
