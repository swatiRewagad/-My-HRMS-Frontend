import { Component, inject, model, output, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { KpiCounts, InternalKpiConfig } from '../../../models/cepc.model';
import { CepcContextService } from '../../../services/cepc-context.service';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-cepc-dashboard-kpi',
  standalone: true,
  imports: [CommonModule, CardModule, TranslateOrPipe],
  templateUrl: './cepc-dashboard-kpi.component.html',
  styleUrl: './cepc-dashboard-kpi.component.scss'
})
export class CepcDashboardKpiComponent {
  private readonly dept = inject(CepcContextService);

  readonly selectedCardId = model<string | null>(null);
  readonly onKpiSelectionChange = output<string | null>();
  readonly metricsInput = input<KpiCounts | null>(null);

  private readonly allMappedCards = computed<InternalKpiConfig[]>(() => {
    const d = this.metricsInput();
    return [
      {
        id: 'Total Pending Complaints',
        titleKey: 'ui.cepc.kpi.total_pending',
        title: 'Total Pending Complaints',
        icon: 'pi pi-copy',
        styleClass: 'bg-blue',

        requiredRungs: ['DO', 'REVIEWER', 'INCHARGE', 'CLOSING_AUTHORITY', 'ADMIN'],
        layout: '1:1',
        metrics: [{ labelKey: 'ui.cepc.kpi.total_pending', label: 'Total Pending Complaints', value: d?.totalPendingComplaints ?? 0 }],
        selectable: true
      },
      {
        id: 'Pending with Me',
        titleKey: 'ui.cepc.kpi.pending_with_me',
        title: 'Pending with Me',
        icon: 'pi pi-user',
        styleClass: 'bg-orange',

        requiredRungs: ['DO', 'REVIEWER', 'INCHARGE', 'CLOSING_AUTHORITY', 'ADMIN'],
        layout: '1:1',
        metrics: [{ labelKey: 'ui.cepc.kpi.pending_with_me', label: 'Pending with Me', value: d?.pendingWithMe ?? 0 }],
        selectable: true
      },
      {
        id: 'Pending with RE',
        titleKey: 'ui.cepc.kpi.pending_with_re',
        title: 'Pending with RE',
        icon: 'pi pi-building',
        styleClass: 'bg-orange',

        requiredRungs: ['DO', 'REVIEWER', 'INCHARGE', 'CLOSING_AUTHORITY', 'ADMIN'],
        layout: '1:1',
        metrics: [{ labelKey: 'ui.cepc.kpi.pending_with_re', label: 'Pending with RE', value: d?.pendingWithRe ?? 0 }],
        selectable: true
      },
      {
        id: 'Pending at Meeting Scheduled',
        titleKey: 'ui.cepc.kpi.meeting_scheduled',
        title: 'Meeting Scheduled',
        icon: 'pi pi-calendar',
        styleClass: 'bg-orange',

        requiredRungs: ['DO', 'REVIEWER', 'INCHARGE', 'CLOSING_AUTHORITY', 'ADMIN'],
        layout: '1:1',
        metrics: [{ labelKey: 'ui.cepc.kpi.pending_at_meeting_scheduled', label: 'Pending at Meeting Scheduled', value: d?.pendingAtMeetingScheduled ?? 0 }],
        selectable: true
      },
      {
        id: 'SLA Breached',
        titleKey: 'ui.cepc.kpi.sla_tracking',
        title: 'SLA Analysis Tracking',
        icon: 'pi pi-clock',
        styleClass: 'bg-red',

        requiredRungs: ['DO', 'REVIEWER', 'INCHARGE', 'CLOSING_AUTHORITY', 'ADMIN'],
        layout: '3:1',
        metrics: [
          { labelKey: 'ui.cepc.kpi.sla_breached', label: 'SLA Breached', value: d?.slaBreached ?? 0, colorClass: 'color-red' },
          { labelKey: 'ui.cepc.kpi.sla_0_15', label: '0-15 Days', value: d?.sla0To15Days ?? 0, colorClass: 'color-orange' },
          { labelKey: 'ui.cepc.kpi.sla_16_30', label: '16-30 Days', value: d?.sla16To30Days ?? 0, colorClass: 'color-green' }
        ],
        // Read-only: this tile shows three numbers at once, so a click has no single filter to stand for.
        // Its three CEPC_DASHBOARD_FILTER rows stay seeded — the count service reads them to fill the numbers.
        selectable: false
      }
    ];
  });

  readonly computedRoleCards = computed<InternalKpiConfig[]>(() => {
    return this.allMappedCards().filter(card => this.dept.hasRung(...card.requiredRungs));
  });

  cardClasses(card: InternalKpiConfig): string {
    if (!card.selectable) {
      return 'kpi-card-surface kpi-card-static';
    }
    return this.selectedCardId() === card.id ? 'kpi-card-surface kpi-selected' : 'kpi-card-surface';
  }

  handleCardSelection(card: InternalKpiConfig): void {
    if (!card.selectable) {
      return;
    }
    const targetId = this.selectedCardId() === card.id ? null : card.id;
    this.selectedCardId.set(targetId);
    this.onKpiSelectionChange.emit(targetId);
  }
}
