import { Component, input, model, output, DestroyRef, inject, computed, } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { TabsModule } from "primeng/tabs";
import { Subject } from "rxjs";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { RbioDashboardTableComponent } from "../rbio-dashboard-table/rbio-dashboard-table.component";
import { ComplaintColumn, TabConfig, TabCounts, TableQueryMetadata, } from "../../../models/rbio.model";
import { BadgeModule } from "primeng/badge";

@Component({
  selector: "app-rbio-dashboard-tabs",
  standalone: true,
  imports: [CommonModule, RbioDashboardTableComponent, TabsModule, BadgeModule],
  templateUrl: "./rbio-dashboard-tabs.component.html",
  styleUrl: "./rbio-dashboard-tabs.component.scss",
})
export class RbioDashboardTabsComponent {
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
    return [
      {
        value: "0",
        label: "All",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
      },
      {
        value: "1",
        label: "Draft",
        visibleFields: ["complaintId", "assignedTo", "mode", "complainantName", "status", "entityName", "complaintCategory",
          "createdDate", "lastUpdatedDate", "priority"],
        count: tabCount?.draft,
      },
      {
        value: "2",
        label: "Meeting Scheduled",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
        count: tabCount?.meetingScheduled,
      },
      {
        value: "3",
        label: "Sent Back to Me",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
        count: tabCount?.sentBackToMe,
      },
      {
        value: "4",
        label: "Sent to RE",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
        count: tabCount?.sentToRe,
      },
      {
        value: "5",
        label: "Response from RE",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
        count: tabCount?.responseFromRe,
      },
      {
        value: "6",
        label: "Withdrawn Complaints",
        visibleFields: ["complaintId", "complaintNumber", "assignedTo", "slaBreachIn", "mode", "complainantName", "status",
          "entityName", "complaintCategory", "createdDate", "lastUpdatedDate", "priority", "subject"],
        count: tabCount?.withdrawnComplaints,
      },
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
