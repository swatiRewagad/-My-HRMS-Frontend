import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WizardStateService } from '../../services/wizard-state.service';
import {
  ALL_SERVICES, PLATFORM_VERSIONS, VERSION_SERVICE_MAP,
  V1_SERVICE_IDS, V2_SERVICE_IDS, MOST_USED_SERVICE_IDS,
} from '../../data/services.data';
import { PlatformService } from '../../models/realm.model';

@Component({
  selector: 'app-services-selection',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './services-selection.component.html',
  styleUrl: './services-selection.component.scss',
})
export class ServicesSelectionComponent {
  readonly wizard = inject(WizardStateService);
  readonly searchTerm = signal('');
  readonly suggestionsHidden = signal(false);
  readonly versions = PLATFORM_VERSIONS;

  readonly availableServices = computed(() => {
    const version = this.wizard.platformVersion();
    if (!version) return [];
    const ids = VERSION_SERVICE_MAP[version] ?? [];
    return ALL_SERVICES.filter((s) => ids.includes(s.id));
  });

  readonly filteredServices = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const available = this.availableServices();
    if (!term) return available;
    return available.filter(
      (s) =>
        s.label.toLowerCase().includes(term) ||
        s.description.toLowerCase().includes(term) ||
        s.subServices?.some(
          (sub) => sub.label.toLowerCase().includes(term) || sub.description.toLowerCase().includes(term)
        )
    );
  });

  readonly platformServices = computed(() => this.filteredServices().filter((s) => s.group === 'basic'));
  readonly aiServices = computed(() => this.filteredServices().filter((s) => s.group === 'ai'));

  readonly mostUsedServices = computed(() => {
    const available = this.availableServices();
    return ALL_SERVICES.filter(
      (s) => MOST_USED_SERVICE_IDS.includes(s.id) && available.some((a) => a.id === s.id)
    );
  });

  readonly allMostUsedSelected = computed(() => {
    const mostUsed = this.mostUsedServices();
    const selected = this.wizard.selectedServices();
    return mostUsed.length > 0 && mostUsed.every((s) => selected.has(s.id));
  });

  readonly subServiceCount = computed(() => {
    const services = this.availableServices();
    const selected = this.wizard.selectedServices();
    const subSelected = this.wizard.selectedSubServices();
    return services
      .filter((s) => s.subServices?.length && selected.has(s.id))
      .reduce((sum, s) => sum + (subSelected[s.id]?.size ?? 0), 0);
  });

  readonly missingSubServiceLabels = computed(() => {
    const version = this.wizard.platformVersion();
    if (!version) return [];
    const ids = VERSION_SERVICE_MAP[version] ?? [];
    const selected = this.wizard.selectedServices();
    const subSelected = this.wizard.selectedSubServices();
    return ALL_SERVICES
      .filter(
        (s) =>
          ids.includes(s.id) &&
          s.subServices?.length &&
          selected.has(s.id) &&
          (!subSelected[s.id] || subSelected[s.id].size === 0)
      )
      .map((s) => s.label);
  });

  readonly noSearchResults = computed(() => {
    return !!this.wizard.platformVersion() && this.filteredServices().length === 0 && this.searchTerm().trim().length > 0;
  });

  get v1Count() { return V1_SERVICE_IDS.length; }
  get v2Count() { return V2_SERVICE_IDS.length; }

  getPlatformCountForVersion(version: string): number {
    const ids = VERSION_SERVICE_MAP[version] ?? [];
    return ALL_SERVICES.filter((s) => s.group === 'basic' && ids.includes(s.id)).length;
  }

  getAiCountForVersion(version: string): number {
    const ids = VERSION_SERVICE_MAP[version] ?? [];
    return ALL_SERVICES.filter((s) => s.group === 'ai' && ids.includes(s.id)).length;
  }

  selectVersion(version: string): void {
    this.wizard.platformVersion.set(version);
    this.wizard.clearServices();
  }

  selectAllMostUsed(): void {
    const selected = this.wizard.selectedServices();
    for (const svc of this.mostUsedServices()) {
      if (!selected.has(svc.id)) {
        this.wizard.toggleService(svc.id);
      }
    }
  }

  isServiceSelected(id: string): boolean {
    return this.wizard.selectedServices().has(id);
  }

  isSubServiceSelected(serviceId: string, subId: string): boolean {
    return this.wizard.selectedSubServices()[serviceId]?.has(subId) ?? false;
  }

  getSubServiceCount(service: PlatformService): number {
    return this.wizard.selectedSubServices()[service.id]?.size ?? 0;
  }

  getServiceColor(service: PlatformService): string {
    return service.group === 'ai' ? '#6941c6' : '#1a56db';
  }

  getServiceBg(service: PlatformService): string {
    return service.group === 'ai' ? '#f4f0ff' : '#eef4ff';
  }

  clearSearch(): void {
    this.searchTerm.set('');
  }
}
