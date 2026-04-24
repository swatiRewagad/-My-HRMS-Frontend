import { Injectable, signal, computed } from '@angular/core';
import { Realm, SchemaConfig } from '../models/realm.model';
import { ALL_SERVICES, VERSION_SERVICE_MAP } from '../data/services.data';
import { EXISTING_CONFIGS } from '../data/realms.data';

function emptySchema(): SchemaConfig {
  return { schemaName: '', dbType: '', connectionString: '', secretPath: '', showConn: false };
}

@Injectable({ providedIn: 'root' })
export class WizardStateService {
  readonly currentStep = signal(0);
  readonly selectedRealm = signal<Realm | null>(null);
  readonly reconfigureConfirmed = signal(false);
  readonly selectedMode = signal<'app-designer' | 'non-app-designer' | null>(null);
  readonly platformVersion = signal('');
  readonly selectedServices = signal<Set<string>>(new Set());
  readonly selectedSubServices = signal<Record<string, Set<string>>>({});
  readonly deploymentType = signal<'dependent' | 'independent' | ''>('');
  readonly deploySchema = signal<SchemaConfig>(emptySchema());
  readonly serviceConfigs = signal<Record<string, SchemaConfig>>({});
  readonly showExistingConfigModal = signal(false);
  readonly isComplete = signal(false);

  readonly availableServices = computed(() => {
    const version = this.platformVersion();
    if (!version) return [];
    const ids = VERSION_SERVICE_MAP[version] ?? [];
    return ALL_SERVICES.filter((s) => ids.includes(s.id));
  });

  readonly canProceed = computed(() => {
    const step = this.currentStep();
    const realm = this.selectedRealm();
    const mode = this.selectedMode();
    const services = this.selectedServices();
    const subServices = this.selectedSubServices();
    const version = this.platformVersion();

    switch (step) {
      case 0: {
        if (!realm) return false;
        const hasExisting = this.hasExistingConfig();
        return !hasExisting || this.reconfigureConfirmed();
      }
      case 1:
        return !!mode;
      case 2: {
        if (services.size === 0 || !version) return false;
        const versionIds = VERSION_SERVICE_MAP[version] ?? [];
        const available = ALL_SERVICES.filter((s) => versionIds.includes(s.id));
        for (const svc of available) {
          if (svc.subServices?.length && services.has(svc.id)) {
            if (!subServices[svc.id] || subServices[svc.id].size === 0) return false;
          }
        }
        return true;
      }
      case 3:
        return true;
      default:
        return false;
    }
  });

  hasExistingConfig(): boolean {
    const realm = this.selectedRealm();
    if (!realm) return false;
    return !!EXISTING_CONFIGS[realm.id];
  }

  toggleService(serviceId: string): void {
    this.selectedServices.update((prev) => {
      const next = new Set(prev);
      if (next.has(serviceId)) {
        next.delete(serviceId);
        this.selectedSubServices.update((ss) => {
          const copy = { ...ss };
          delete copy[serviceId];
          return copy;
        });
      } else {
        next.add(serviceId);
      }
      return next;
    });
  }

  toggleSubService(serviceId: string, subId: string): void {
    this.selectedSubServices.update((prev) => {
      const current = new Set(prev[serviceId] ?? []);
      if (current.has(subId)) {
        current.delete(subId);
      } else {
        current.add(subId);
      }
      return { ...prev, [serviceId]: current };
    });
  }

  clearServices(): void {
    this.selectedServices.set(new Set());
    this.selectedSubServices.set({});
  }

  updateServiceConfig(key: string, config: SchemaConfig): void {
    this.serviceConfigs.update((prev) => ({ ...prev, [key]: config }));
  }

  nextStep(): void {
    if (this.currentStep() < 3) {
      this.currentStep.update((s) => s + 1);
    } else {
      this.isComplete.set(true);
    }
  }

  prevStep(): void {
    if (this.currentStep() > 0) {
      if (this.currentStep() === 1) {
        this.reconfigureConfirmed.set(false);
      }
      this.currentStep.update((s) => s - 1);
    }
  }

  reset(): void {
    this.currentStep.set(0);
    this.selectedRealm.set(null);
    this.reconfigureConfirmed.set(false);
    this.selectedMode.set(null);
    this.platformVersion.set('');
    this.selectedServices.set(new Set());
    this.selectedSubServices.set({});
    this.deploymentType.set('');
    this.deploySchema.set(emptySchema());
    this.serviceConfigs.set({});
    this.isComplete.set(false);
  }
}
