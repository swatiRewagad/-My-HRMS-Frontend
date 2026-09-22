import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { SecurityAdminService, SecurityAlert, RevealAuditEntry, RetentionPolicyView } from '../../../services/security-admin.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/**
 * Security console: alerts, PII reveal trail, and retention policies
 * (UST873, UST875, UST890).
 *
 * Read-mostly by design. The destructive retention switch is deliberately not exposed here — it is a
 * SYSTEM_CONFIG value an operator sets deliberately, not a button someone can click by accident.
 */
@Component({
  selector: 'app-security-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './security-alerts.component.html',
  styleUrl: './security-alerts.component.scss'
})
export class SecurityAlertsComponent implements OnInit {
  private router = inject(Router);
  private service = inject(SecurityAdminService);
  auth = inject(KeycloakAuthService);

  activeTab = signal<'alerts' | 'reveals' | 'retention'>('alerts');

  alerts = signal<SecurityAlert[]>([]);
  openCount = signal(0);
  statusFilter = signal('OPEN');
  loadingAlerts = signal(false);

  reveals = signal<RevealAuditEntry[]>([]);
  revealUserFilter = signal('');
  loadingReveals = signal(false);

  policies = signal<RetentionPolicyView[]>([]);
  destructiveEnabled = signal(false);
  loadingPolicies = signal(false);
  retentionMessage = signal('');

  errorMessage = signal('');
  notAuthorised = signal(false);

  criticalCount = computed(() =>
    this.alerts().filter(a => a.severity === 'HIGH' && a.status === 'OPEN').length);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadAlerts();
  }

  selectTab(tab: 'alerts' | 'reveals' | 'retention') {
    this.activeTab.set(tab);
    this.errorMessage.set('');
    if (tab === 'alerts' && this.alerts().length === 0) {
      this.loadAlerts();
    } else if (tab === 'reveals' && this.reveals().length === 0) {
      this.loadReveals();
    } else if (tab === 'retention' && this.policies().length === 0) {
      this.loadPolicies();
    }
  }

  loadAlerts() {
    this.loadingAlerts.set(true);
    this.errorMessage.set('');
    this.service.getAlerts(this.statusFilter() || undefined).subscribe({
      next: (res) => {
        this.alerts.set(res?.data ?? []);
        this.openCount.set(res?.openCount ?? 0);
        this.loadingAlerts.set(false);
      },
      error: (err) => this.handleError(err, () => this.loadingAlerts.set(false))
    });
  }

  onStatusFilterChange(status: string) {
    this.statusFilter.set(status);
    this.loadAlerts();
  }

  acknowledge(alert: SecurityAlert, note: string) {
    this.service.acknowledgeAlert(alert.id, note).subscribe({
      next: () => this.loadAlerts(),
      error: (err) => this.handleError(err)
    });
  }

  loadReveals() {
    this.loadingReveals.set(true);
    this.service.getPiiReveals(this.revealUserFilter() || undefined).subscribe({
      next: (res) => {
        this.reveals.set(res?.data ?? []);
        this.loadingReveals.set(false);
      },
      error: (err) => this.handleError(err, () => this.loadingReveals.set(false))
    });
  }

  loadPolicies() {
    this.loadingPolicies.set(true);
    this.service.getRetentionPolicies().subscribe({
      next: (res) => {
        this.policies.set(res?.data ?? []);
        this.destructiveEnabled.set(res?.destructiveEnabled ?? false);
        this.loadingPolicies.set(false);
      },
      error: (err) => this.handleError(err, () => this.loadingPolicies.set(false))
    });
  }

  runRetentionPreview() {
    this.retentionMessage.set('');
    this.service.runRetention().subscribe({
      next: (res) => {
        this.retentionMessage.set(res?.message ?? 'Retention evaluated.');
        this.loadPolicies();
      },
      error: (err) => this.handleError(err)
    });
  }

  severityClass(severity: string): string {
    switch (severity) {
      case 'HIGH': return 'sev-high';
      case 'MEDIUM': return 'sev-medium';
      default: return 'sev-low';
    }
  }

  private handleError(err: any, always?: () => void) {
    always?.();
    if (err?.status === 403 || err?.status === 401) {
      // Surfaced rather than swallowed: an admin seeing an empty console would otherwise assume
      // there were no alerts, when in fact they were denied.
      this.notAuthorised.set(true);
      this.errorMessage.set('Administrator access is required to view the security console.');
      return;
    }
    this.errorMessage.set(err?.error?.message || 'The security data could not be loaded.');
  }
}
