import { Component, computed, inject, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Chip } from 'primeng/chip';
import { Select, SelectChangeEvent } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { CepcDashboardFilterComponent } from '../cepc-dashboard-filter/cepc-dashboard-filter.component';
import { SelectedFilters } from '../../../models/cepc.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { CepcContextService } from '../../../services/cepc-context.service';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';
import { TranslationService } from '../../../services/translation.service';

const EMPTY_FILTERS: SelectedFilters = {
  states: [], districts: [], years: [], quarters: [], meetingTypes: [], documentTypes: []
};

/** Key plus the English it replaces, so an unseeded key still reads exactly as the chip always has. */
const CATEGORY_LABELS: Record<string, { key: string; en: string }> = {
  states: { key: 'ui.cepc.filter.cat.states', en: 'State' },
  districts: { key: 'ui.cepc.filter.cat.districts', en: 'District' },
  years: { key: 'ui.cepc.filter.cat.years', en: 'Year' },
  quarters: { key: 'ui.cepc.filter.cat.quarters', en: 'Quarter' },
  meetingTypes: { key: 'ui.cepc.filter.cat.meeting_types', en: 'Meeting' },
  documentTypes: { key: 'ui.cepc.filter.cat.document_types', en: 'Document' }
};

@Component({
  selector: 'app-cepc-dashboard-header',
  standalone: true,
  imports: [
    FormsModule,
    CepcDashboardFilterComponent,
    Button,
    Chip,
    Select,
    Tag,
    TranslateOrPipe
  ],
  templateUrl: './cepc-dashboard-header.component.html',
  styleUrl: './cepc-dashboard-header.component.scss',
})
export class CepcDashboardHeaderComponent {
  private readonly auth = inject(KeycloakAuthService);
  private readonly i18n = inject(TranslationService);
  readonly dept = inject(CepcContextService);

  isDOUser = computed(() => this.dept.hasRung('DO'));

  isFilterOpen = model<boolean>(false);

  readonly isSearching = input<boolean>(false);
  readonly advSearchActive = input<boolean>(false);
  readonly statusCodes = input<{ label: string; value: string; labelKey?: string }[]>([]);
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
      const label = CATEGORY_LABELS[category];
      const prefix = label ? this.i18n.translateOr(label.key, label.en) : category;
      for (const value of values) {
        chips.push({ category, value, label: `${prefix}: ${value}` });
      }
    }
    return chips;
  });

  /**
   * The dropdown's entries in the current locale.
   *
   * <p>`labelKey` is derived server-side from the `CEPC_DASHBOARD_FILTER` code; the server's English `label`
   * is the fallback, so an unseeded key leaves the entry reading exactly as it did before.
   */
  readonly translatedStatusCodes = computed(() =>
    this.statusCodes().map(entry => entry.labelKey
      ? { ...entry, label: this.i18n.translateOr(entry.labelKey, entry.label) }
      : entry)
  );

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
