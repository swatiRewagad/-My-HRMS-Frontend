import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WizardStateService } from '../../services/wizard-state.service';
import { ALL_SERVICES, WIZARD_STEPS } from '../../data/services.data';
import { EXISTING_CONFIGS } from '../../data/realms.data';
import { SchemaConfig } from '../../models/realm.model';
import { RealmSelectionComponent } from '../realm-selection/realm-selection.component';
import { ModeSelectionComponent } from '../mode-selection/mode-selection.component';
import { ServicesSelectionComponent } from '../services-selection/services-selection.component';
import { ServiceConfigurationComponent } from '../service-configuration/service-configuration.component';

@Component({
  selector: 'app-wizard-layout',
  standalone: true,
  imports: [
    CommonModule,
    RealmSelectionComponent,
    ModeSelectionComponent,
    ServicesSelectionComponent,
    ServiceConfigurationComponent,
  ],
  templateUrl: './wizard-layout.component.html',
  styleUrl: './wizard-layout.component.scss',
})
export class WizardLayoutComponent {
  readonly wizard = inject(WizardStateService);
  readonly steps = WIZARD_STEPS;

  get currentStep() { return this.wizard.currentStep(); }
  get canProceed() { return this.wizard.canProceed(); }
  get isComplete() { return this.wizard.isComplete(); }
  get selectedRealm() { return this.wizard.selectedRealm(); }
  get selectedMode() { return this.wizard.selectedMode(); }
  get platformVersion() { return this.wizard.platformVersion(); }
  get selectedServicesSize() { return this.wizard.selectedServices().size; }
  get showExistingModal() { return this.wizard.showExistingConfigModal(); }

  get hasExistingConfig(): boolean {
    const realm = this.selectedRealm;
    return !!realm && !!EXISTING_CONFIGS[realm.id];
  }

  get existingConfig() {
    const realm = this.selectedRealm;
    return realm ? EXISTING_CONFIGS[realm.id] ?? null : null;
  }

  get isReconfiguring(): boolean {
    return this.hasExistingConfig && this.wizard.reconfigureConfirmed();
  }

  get deploySchemaName(): string {
    return this.wizard.serviceConfigs().__deploy_type__?.schemaName ?? '';
  }

  get serviceDetailEntries(): { key: string; label: string; config: SchemaConfig; isSubField: boolean; isAi: boolean }[] {
    const config = this.existingConfig;
    if (!config?.serviceDetails) return [];
    const entries: { key: string; label: string; config: SchemaConfig; isSubField: boolean; isAi: boolean }[] = [];
    for (const [key, val] of Object.entries(config.serviceDetails)) {
      const hasValue = val.schemaName || val.dbType || val.connectionString || val.secretPath;
      if (!hasValue) continue;

      const isSubField = key.includes('__');
      let label = key;
      let isAi = false;

      if (isSubField) {
        const [subId, fieldKey] = key.split('__');
        for (const svc of ALL_SERVICES) {
          const sub = svc.subServices?.find(s => s.id === subId);
          if (sub) {
            const field = sub.configFields?.find(f => f.key === fieldKey);
            label = `${svc.label} › ${sub.label} › ${field?.label ?? fieldKey}`;
            isAi = svc.group === 'ai';
            break;
          }
        }
      } else {
        const svc = ALL_SERVICES.find(s => s.id === key);
        if (svc) {
          label = svc.label;
          isAi = svc.group === 'ai';
        }
      }

      entries.push({ key, label, config: val, isSubField, isAi });
    }
    return entries;
  }

  next(): void { this.wizard.nextStep(); }
  back(): void { this.wizard.prevStep(); }
  reset(): void { this.wizard.reset(); }
  closeModal(): void { this.wizard.showExistingConfigModal.set(false); }

  getNextButtonLabel(): string {
    if (this.currentStep === 3) return 'Complete Configuration →';
    if (this.currentStep === 0 && this.hasExistingConfig && this.isReconfiguring) return 'Reconfigure →';
    if (this.currentStep === 0 && this.hasExistingConfig && !this.canProceed) return 'Confirm Reconfigure to Continue →';
    return 'Next →';
  }

  getNextButtonClass(): string {
    if (!this.canProceed) return 'btn-disabled';
    if (this.currentStep === 0 && this.isReconfiguring) return 'btn-amber';
    return 'btn-primary';
  }
}
