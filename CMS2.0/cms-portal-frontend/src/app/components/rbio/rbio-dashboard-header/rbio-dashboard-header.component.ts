import { Component, computed, inject, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Chip } from 'primeng/chip';
import { Select, SelectChangeEvent } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { RbioDashboardFilterComponent } from '../rbio-dashboard-filter/rbio-dashboard-filter.component';
import { SelectedFilters } from '../../../models/rbio.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

const EMPTY_FILTERS: SelectedFilters = {
  states: [], districts: [], years: [], quarters: [], meetingTypes: [], documentTypes: []
};

const CATEGORY_LABELS: Record<string, string> = {
  states: 'State', districts: 'District', years: 'Year',
  quarters: 'Quarter', meetingTypes: 'Meeting', documentTypes: 'Document'
};

@Component({
  selector: 'app-rbio-dashboard-header',
  standalone: true,
  imports: [
    FormsModule,
    RbioDashboardFilterComponent,
    Button,
    Chip,
    Select,
    Tag
  ],
  templateUrl: './rbio-dashboard-header.component.html',
  styleUrl: './rbio-dashboard-header.component.scss',
})
export class RbioDashboardHeaderComponent {
  private readonly auth = inject(KeycloakAuthService);

  isDOUser = computed(() => this.auth.currentUser()?.roles?.includes('RBIO_DO') ?? false);

  isFilterOpen = model<boolean>(false);

  readonly isSearching = input<boolean>(false);
  readonly advSearchActive = input<boolean>(false);
  readonly statusCodes = input<{ label: string; value: string }[]>([]);
  readonly activeFilterCount = input<number>(0);
  readonly activeFilters = input<SelectedFilters | null>(null);

  readonly selectedStatusCode = model<string | null>(null);

  readonly toggleAdvancedSearch = output<boolean>();
  readonly clearSearch = output<void>();
  readonly createComplaint = output<void>();
  readonly statusFilterSelect = output<SelectChangeEvent>();
  readonly filtersChanged = output<SelectedFilters>();
  readonly filterRemoved = output<SelectedFilters>();

  readonly filterChips = computed(() => {
    const filters = this.activeFilters();
    if (!filters) return [];
    const chips: { category: string; value: string; label: string }[] = [];
    for (const [category, values] of Object.entries(filters)) {
      const prefix = CATEGORY_LABELS[category] || category;
      for (const value of values) {
        chips.push({ category, value, label: `${prefix}: ${value}` });
      }
    }
    return chips;
  });

  removeChip(category: string, value: string): void {
    const current = this.activeFilters();
    if (!current) return;
    const updated = {
      ...current,
      [category]: current[category as keyof SelectedFilters].filter((v: string) => v !== value)
    };
    this.filterRemoved.emit(updated);
  }

  clearAllFilters(): void {
    this.filterRemoved.emit({ ...EMPTY_FILTERS });
  }
}
