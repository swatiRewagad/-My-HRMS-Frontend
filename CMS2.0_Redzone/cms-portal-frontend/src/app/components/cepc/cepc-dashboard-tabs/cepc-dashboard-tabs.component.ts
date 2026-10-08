import { Component, input, model, output, DestroyRef, inject, computed, } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { Tabs } from "primeng/tabs";
import { TabList } from "primeng/tabs";
import { Tab } from "primeng/tabs";
import { TabPanels } from "primeng/tabs";
import { TabPanel } from "primeng/tabs";
import { Badge } from "primeng/badge";
import { Subject } from "rxjs";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { CepcDashboardTableComponent } from "../cepc-dashboard-table/cepc-dashboard-table.component";
import { ComplaintColumn, TabConfig, TabCounts, TableQueryMetadata, } from "../../../models/cepc.model";
import { TranslateOrPipe } from "../../../pipes/translate-or.pipe";

const COMPLAINT_FIELDS: (keyof ComplaintColumn)[] = [
  "complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
  "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject",
];

@Component({
  selector: "app-cepc-dashboard-tabs",
  standalone: true,
  imports: [CommonModule, CepcDashboardTableComponent, Tabs, TabList, Tab, TabPanels, TabPanel, Badge, TranslateOrPipe],
  templateUrl: "./cepc-dashboard-tabs.component.html",
  styleUrl: "./cepc-dashboard-tabs.component.scss",
})
export class CepcDashboardTabsComponent {
  readonly complaints = input<ComplaintColumn[]>([]);
  readonly isSearching = input<boolean>(false);
  readonly totalRecords = input<number>(0);
  readonly activeTab = model<string | number>("0");
  readonly tabCounts = input<TabCounts | null>(null);

  readonly tableQueryFields = output<TableQueryMetadata>();

  private readonly queryStream$ = new Subject<TableQueryMetadata>();
  private readonly destroyRef = inject(DestroyRef);

  readonly tabsConfig = computed<TabConfig[]>(() => {
    const tabCount = this.tabCounts();
    // Values are the contract with the dashboard's translateTabIdToStatus(): each one maps to a filter
    // code in CEPC_DASHBOARD_FILTER, so a value changed here silently changes which tab the server answers.
    return [
      { value: "0", labelKey: "ui.cepc.tab.all", label: "All", visibleFields: COMPLAINT_FIELDS },
      // Draft lists complaints in status DRAFT, not part-filled intake forms, so it gets the full column
      // set like every other tab.
      { value: "1", labelKey: "ui.cepc.tab.draft", label: "Draft", visibleFields: COMPLAINT_FIELDS, count: tabCount?.draft },
      { value: "2", labelKey: "ui.cepc.tab.meeting_scheduled", label: "Meeting Scheduled", visibleFields: COMPLAINT_FIELDS, count: tabCount?.meetingScheduled },
      { value: "3", labelKey: "ui.cepc.tab.sent_back_to_me", label: "Sent Back to Me", visibleFields: COMPLAINT_FIELDS, count: tabCount?.sentBackToMe },
      { value: "4", labelKey: "ui.cepc.tab.sent_to_re", label: "Sent to RE", visibleFields: COMPLAINT_FIELDS, count: tabCount?.sentToRe },
      { value: "5", labelKey: "ui.cepc.tab.contact_person", label: "Contact Person", visibleFields: COMPLAINT_FIELDS, count: tabCount?.responseFromCp },
    ];
  });

  constructor() {
    this.queryStream$.pipe(
      debounceTime(400),
      distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((finalMetadata) => {
      this.tableQueryFields.emit(finalMetadata);
    });
  }

  handleTableQuerySearch(metadata: TableQueryMetadata): void {
    metadata.tabValue = this.activeTab();
    this.queryStream$.next(metadata);
  }

  onTabContainerChange(newTabValue: string | number | undefined): void {
    const resolvedTab = newTabValue ?? "0";

    this.activeTab.set(resolvedTab);

    this.queryStream$.next({
      page: 1,
      size: 10,
      sortField: "createdAt",
      sortOrder: "desc",
      filters: null,
      showUnreadOnly: false,
      showWithoutAttachments: false,
      tabValue: resolvedTab,
    });
  }
}
