import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WizardStateService } from '../../services/wizard-state.service';
import { ALL_SERVICES, DB_TYPES, SERVICE_API_INFO } from '../../data/services.data';
import { PlatformService, SchemaConfig, ServiceApiInfo } from '../../models/realm.model';

@Component({
  selector: 'app-service-configuration',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './service-configuration.component.html',
  styleUrl: './service-configuration.component.scss',
})
export class ServiceConfigurationComponent {
  readonly wizard = inject(WizardStateService);
  readonly dbTypes = DB_TYPES;
  readonly dbDropdownOpen = signal(false);
  readonly expandedApiPanels = signal<Set<string>>(new Set());

  readonly selectedServicesList = computed(() => {
    return ALL_SERVICES.filter((s) => this.wizard.selectedServices().has(s.id));
  });

  get deployType(): string {
    return this.wizard.serviceConfigs().__deploy_type__?.schemaName ?? '';
  }

  get deploySchema(): SchemaConfig {
    return this.wizard.serviceConfigs().__deploy_schema__ ?? this.emptySchema();
  }

  setDeployType(type: string): void {
    const current = this.wizard.serviceConfigs().__deploy_type__ ?? this.emptySchema();
    this.wizard.updateServiceConfig('__deploy_type__', { ...current, schemaName: type });
  }

  updateDeploySchema(partial: Partial<SchemaConfig>): void {
    const current = this.wizard.serviceConfigs().__deploy_schema__ ?? this.emptySchema();
    this.wizard.updateServiceConfig('__deploy_schema__', { ...current, ...partial });
  }

  getServiceConfig(serviceId: string): SchemaConfig {
    return this.wizard.serviceConfigs()[serviceId] ?? this.emptySchema();
  }

  updateServiceField(serviceId: string, partial: Partial<SchemaConfig>): void {
    const current = this.getServiceConfig(serviceId);
    this.wizard.updateServiceConfig(serviceId, { ...current, ...partial });
  }

  getApiInfo(serviceId: string): ServiceApiInfo | null {
    return SERVICE_API_INFO[serviceId] ?? null;
  }

  getServiceColor(svc: PlatformService): string {
    return svc.group === 'ai' ? '#6941c6' : '#1a56db';
  }

  getServiceBg(svc: PlatformService): string {
    return svc.group === 'ai' ? '#f4f0ff' : '#eef4ff';
  }

  getGroupLabel(svc: PlatformService): string {
    return svc.group === 'ai' ? 'AI Service' : 'Platform Service';
  }

  toggleApiPanel(serviceId: string): void {
    this.expandedApiPanels.update((prev) => {
      const next = new Set(prev);
      if (next.has(serviceId)) {
        next.delete(serviceId);
      } else {
        next.add(serviceId);
      }
      return next;
    });
  }

  isApiPanelExpanded(serviceId: string): boolean {
    return this.expandedApiPanels().has(serviceId);
  }

  selectDbType(type: string): void {
    this.updateDeploySchema({ dbType: type });
    this.dbDropdownOpen.set(false);
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text);
  }

  getSubServiceFieldValue(subServiceId: string, fieldKey: string): string {
    const config = this.wizard.serviceConfigs()[`${subServiceId}__${fieldKey}`];
    return config?.schemaName ?? '';
  }

  updateSubServiceField(subServiceId: string, fieldKey: string, value: string): void {
    this.wizard.updateServiceConfig(`${subServiceId}__${fieldKey}`, { ...this.emptySchema(), schemaName: value });
  }

  private emptySchema(): SchemaConfig {
    return { schemaName: '', dbType: '', connectionString: '', secretPath: '', showConn: false };
  }
}
