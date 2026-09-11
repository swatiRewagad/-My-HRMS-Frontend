import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';

// 1. Import the specific event interface from PrimeNG
import { SelectChangeEvent } from 'primeng/select';
import { RbioDashboardFilterComponent } from '../rbio-dashboard-filter/rbio-dashboard-filter.component';
import { SelectedFilters } from '../../../models/rbio.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

export interface StatusCodeOption {
  label: string;
  value: string | number;
}

@Component({
  selector: 'app-rbio-dashboard-header',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RbioDashboardFilterComponent,
    ButtonModule,
    SelectModule
  ],
  templateUrl: './rbio-dashboard-header.component.html',
  styleUrl: './rbio-dashboard-header.component.scss',
})
export class RbioDashboardHeaderComponent {
  readonly auth = inject(KeycloakAuthService);
  isDOUser = computed(() => !!this.auth?.currentUser()?.roles?.includes('RBIO_DO'));

  isFilterOpen = signal<boolean>(false);
  onFiltersChanged = output<SelectedFilters>();

  // Input signals fed down from the parent dashboard
  readonly isSearching = input<boolean>(false);
  readonly advSearchActive = input<boolean>(false);
  readonly statusCodes = input<StatusCodeOption[]>([]);

  // Model signal matching selection modifications back up automatically
  readonly selectedStatusCode = model<string | number | null>(null);

  // Structural execution intent notifications sent back up to parent components
  readonly onToggleAdvancedSearch = output<boolean>();
  readonly onClearSearch = output<void>();
  readonly onRefreshFilters = output<void>();
  readonly onCreateComplaint = output<void>();

  // 2. Strong-typed output emitter matching PrimeNG's structural signature
  readonly onStatusFilterSelect = output<SelectChangeEvent>();

  /**
   * Forwards PrimeNG select dropdown choice modifications upstream
   */
  onStatusCodeChange(event: SelectChangeEvent): void {
    this.onStatusFilterSelect.emit(event);
  }

  onFilterSelectionChanged(selectedFilters: SelectedFilters): void {
    console.log('Applied Filter Dataset Model:', selectedFilters);

    // Forward the structured filter object payload to your dashboard table context
    this.onFiltersChanged.emit(selectedFilters);
  }
}
