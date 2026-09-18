import { Component, inject, model, output, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { KpiCounts, InternalKpiConfig } from '../../../models/rbio.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

@Component({
  selector: 'app-rbio-dashboard-kpi',
  standalone: true,
  imports: [CommonModule, CardModule],
  templateUrl: './rbio-dashboard-kpi.component.html',
  styleUrl: './rbio-dashboard-kpi.component.scss'
})
export class RbioDashboardKpiComponent {
  private readonly auth = inject(KeycloakAuthService);

  readonly selectedCardId = model<string | null>(null);
  readonly onKpiSelectionChange = output<string | null>();
  readonly metricsInput = input<KpiCounts | null>(null);

  private readonly allMappedCards = computed<InternalKpiConfig[]>(() => {
    const d = this.metricsInput();
    return [
      {
        id: 'Total Pending Complaints',
        title: 'Total Pending Complaints',
        icon: 'pi pi-copy',
        styleClass: 'bg-blue',

        requiredRoles: ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_ADMIN'],
        layout: '1:1',
        metrics: [{ label: 'Total Pending Complaints', value: d?.totalPendingComplaints ?? 0 }]
      },
      {
        id: 'Pending with Me',
        title: 'Pending with Me',
        icon: 'pi pi-user',
        styleClass: 'bg-orange',

        requiredRoles: ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_ADMIN'],
        layout: '1:1',
        metrics: [{ label: 'Pending with Me', value: d?.pendingWithMe ?? 0 }]
      },
      {
        id: 'Pending with RE',
        title: 'Pending with RE',
        icon: 'pi pi-building',
        styleClass: 'bg-orange',

        requiredRoles: ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_ADMIN'],
        layout: '1:1',
        metrics: [{ label: 'Pending with RE', value: d?.pendingWithRe ?? 0 }]
      },
      {
        id: 'Pending at Meeting Scheduled',
        title: 'Pending at Meeting Scheduled',
        icon: 'pi pi-calendar',
        styleClass: 'bg-orange',

        requiredRoles: ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_ADMIN'],
        layout: '1:1',
        metrics: [{ label: 'Pending at Meeting Scheduled', value: d?.pendingAtMeetingSchedule ?? 0 }]
      },
      {
        id: 'SLA Breached',
        title: 'SLA Analysis Tracking',
        icon: 'pi pi-clock',
        styleClass: 'bg-red',

        requiredRoles: ['RBIO_DO', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_ADMIN'],
        layout: '3:1',
        metrics: [
          { label: 'SLA Breached', value: d?.slaBreached ?? 0, colorClass: 'color-red' },
          { label: '0-15 Days', value: d?.sla0To15Days ?? 0, colorClass: 'color-orange' },
          { label: '16-30 Days', value: d?.sla16To30Days ?? 0, colorClass: 'color-green' }
        ]
      }
    ];
  });

  readonly computedRoleCards = computed<InternalKpiConfig[]>(() => {
    const userRoles = this.auth.getRoles() || [];
    return this.allMappedCards().filter(card =>
      card.requiredRoles.some(role => userRoles.includes(role))
    );
  });

  handleCardSelection(cardId: string): void {
    const targetId = this.selectedCardId() === cardId ? null : cardId;
    this.selectedCardId.set(targetId);
    this.onKpiSelectionChange.emit(targetId);
  }
}
