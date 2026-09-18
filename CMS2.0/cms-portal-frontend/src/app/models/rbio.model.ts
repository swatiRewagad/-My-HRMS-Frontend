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
    nodalOfficer: string | null;
    principalNodalOfficer: string | null;
    complaintColor: string;
    isRead: boolean;
    slaColor: string | null;
    statusColor: string;
}

export interface AdvancedSearchCriteria {
    complaintNumber: string | null;
    complainantName: string | null;
    id: number | null;
    statusCode: string | null;
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
}

export interface FilterCategory {
    id: string;
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
    label: string;
    value: number;
    colorClass?: 'color-red' | 'color-orange' | 'color-green';
}

export interface InternalKpiConfig {
    id: string;
    title: string;
    icon: string;
    styleClass: 'bg-blue' | 'bg-orange' | 'bg-red';

    requiredRoles: string[];
    layout: '1:1' | '2:1' | '3:1' | '4:1';
    metrics: MetricItem[];
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
    pendingAtMeetingSchedule: number;
    slaBreached: number;
    sla0To15Days: number;
    sla16To30Days: number;
}

export interface TabCounts {
    draft: number;
    meetingScheduled: number;
    sentBackToMe: number;
    sentToRe: number;
    responseFromRe: number;
    withdrawnComplaints: number;
}

export interface ApiResponseData {
    complaints: ComplaintsPageable;
    kpiCounts: KpiCounts;
    tabCounts: TabCounts;
}

export interface RootApiResponse {
    success: boolean;
    data: ApiResponseData;
    timestamp: string;
}
