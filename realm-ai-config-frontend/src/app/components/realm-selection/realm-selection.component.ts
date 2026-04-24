import { Component, inject, signal, computed, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WizardStateService } from '../../services/wizard-state.service';
import { REALMS, EXISTING_CONFIGS } from '../../data/realms.data';
import { Realm, ExistingConfig } from '../../models/realm.model';

@Component({
  selector: 'app-realm-selection',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './realm-selection.component.html',
  styleUrl: './realm-selection.component.scss',
})
export class RealmSelectionComponent {
  readonly wizard = inject(WizardStateService);
  readonly dropdownOpen = signal(false);
  readonly searchTerm = signal('');

  readonly filteredRealms = computed(() => {
    const term = this.searchTerm().toLowerCase();
    return REALMS.filter(
      (r) =>
        r.status === 'active' &&
        (r.displayName.toLowerCase().includes(term) || r.name.toLowerCase().includes(term))
    );
  });

  @ViewChild('dropdownContainer') dropdownRef!: ElementRef;

  @HostListener('document:mousedown', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.dropdownRef && !this.dropdownRef.nativeElement.contains(event.target)) {
      this.dropdownOpen.set(false);
      this.searchTerm.set('');
    }
  }

  toggleDropdown(): void {
    this.dropdownOpen.update((v) => !v);
    this.searchTerm.set('');
  }

  selectRealm(realm: Realm): void {
    this.wizard.selectedRealm.set(realm);
    this.wizard.reconfigureConfirmed.set(false);
    this.dropdownOpen.set(false);
    this.searchTerm.set('');
  }

  getExistingConfig(realmId: string): ExistingConfig | null {
    return EXISTING_CONFIGS[realmId] ?? null;
  }

  hasExistingConfig(realm: Realm | null): boolean {
    return !!realm && !!EXISTING_CONFIGS[realm.id];
  }

  get selectedRealm(): Realm | null {
    return this.wizard.selectedRealm();
  }

  get existingConfig(): ExistingConfig | null {
    const realm = this.selectedRealm;
    return realm ? this.getExistingConfig(realm.id) : null;
  }

  get reconfigureConfirmed(): boolean {
    return this.wizard.reconfigureConfirmed();
  }

  confirmReconfigure(): void {
    this.wizard.reconfigureConfirmed.set(true);
  }

  cancelReconfigure(): void {
    this.wizard.reconfigureConfirmed.set(false);
  }

  viewExistingConfig(): void {
    this.wizard.showExistingConfigModal.set(true);
  }
}
