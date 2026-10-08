import { Component, model, output, signal, computed, inject, input, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DrawerModule } from 'primeng/drawer';
import { ListboxModule } from 'primeng/listbox';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';
import { FilterCategory, SelectedFilters } from '../../../models/cepc.model';
import { ApiService } from '../../../services/api.service';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

const EMPTY_FILTERS: SelectedFilters = {
  states: [],
  districts: [],
  years: [],
  quarters: [],
  meetingTypes: [],
  documentTypes: []
};

const QUARTER_OPTIONS = [
  { label: 'Quarter 1 (Apr - Jun)', value: 'Q1', labelKey: 'ui.cepc.filter.quarter_1' },
  { label: 'Quarter 2 (Jul - Sep)', value: 'Q2', labelKey: 'ui.cepc.filter.quarter_2' },
  { label: 'Quarter 3 (Oct - Dec)', value: 'Q3', labelKey: 'ui.cepc.filter.quarter_3' },
  { label: 'Quarter 4 (Jan - Mar)', value: 'Q4', labelKey: 'ui.cepc.filter.quarter_4' }
] as const;

@Component({
  selector: 'app-cepc-dashboard-filter',
  standalone: true,
  imports: [FormsModule, DrawerModule, ListboxModule, CheckboxModule, ButtonModule, TranslateOrPipe],
  templateUrl: './cepc-dashboard-filter.component.html',
  styleUrl: './cepc-dashboard-filter.component.scss'
})
export class CepcDashboardFilterComponent {
  private apiService = inject(ApiService);

  visible = model<boolean>(false);
  selectedCategoryId = model<string>('states');
  readonly activeFilters = input<SelectedFilters | null>(null);

  filterApply = output<SelectedFilters>();

  selectedValues = signal<SelectedFilters>({ ...EMPTY_FILTERS });

  readonly stateOptions = signal<{ label: string; value: string }[]>([]);
  readonly districtOptions = signal<{ label: string; value: string }[]>([]);
  readonly loadingDistricts = signal(false);
  private districtCache = new Map<string, { label: string; value: string }[]>();

  private readonly yearOptions = this.generateFinancialYears(5);

  readonly filterCategories = computed<FilterCategory[]>(() => [
    { id: 'states', labelKey: 'ui.cepc.filter.cat.states', label: 'State', options: this.stateOptions() },
    { id: 'districts', labelKey: 'ui.cepc.filter.cat.districts', label: 'District', options: this.districtOptions() },
    { id: 'years', labelKey: 'ui.cepc.filter.cat.years', label: 'Year', options: this.yearOptions },
    { id: 'quarters', labelKey: 'ui.cepc.filter.cat.quarters', label: 'Quarter', options: [...QUARTER_OPTIONS] }
  ]);

  activeCategoryOptions = computed(() => {
    const match = this.filterCategories().find(c => c.id === this.selectedCategoryId());
    return match?.options ?? [];
  });

  totalSelectedCount = computed(() => {
    const vals = this.selectedValues();
    return Object.values(vals).reduce((sum, arr) => sum + arr.length, 0);
  });

  isAllActiveCategorySelected = computed(() => {
    const options = this.activeCategoryOptions();
    if (options.length === 0) return false;
    const categoryId = this.selectedCategoryId() as keyof SelectedFilters;
    const selected = this.selectedValues()[categoryId] ?? [];
    return options.every(opt => selected.includes(opt.value));
  });

  constructor() {
    this.loadStates();

    effect(() => {
      if (this.visible()) {
        const parent = this.activeFilters();
        this.selectedValues.set(parent ? { ...parent } : { ...EMPTY_FILTERS });
      }
    });
  }

  private async loadStates(): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.apiService.get<{ success: boolean; message: string; data: string[] }>(
          `/location/states`
        )
      );
      this.stateOptions.set(response.data.map(state => ({ label: state, value: state })));
    } catch (error) {
      console.error('Failed to load states:', error);
    }
  }

  async onCheckboxChange(value: string, checked: boolean): Promise<void> {
    const categoryId = this.selectedCategoryId() as keyof SelectedFilters;

    this.selectedValues.update(store => {
      const current = [...(store[categoryId] || [])];
      if (checked) {
        if (!current.includes(value)) current.push(value);
      } else {
        const index = current.indexOf(value);
        if (index > -1) current.splice(index, 1);
      }
      return { ...store, [categoryId]: current };
    });

    if (categoryId === 'states') {
      await this.handleStateSelectionChange();
    }
  }

  private async handleStateSelectionChange(): Promise<void> {
    const selectedStates = this.selectedValues().states;

    if (selectedStates.length === 0) {
      this.districtOptions.set([]);
      this.clearOrphanDistricts([]);
      return;
    }

    this.loadingDistricts.set(true);
    try {
      const results = await Promise.all(
        selectedStates.map(async (stateName) => {
          if (this.districtCache.has(stateName)) {
            return this.districtCache.get(stateName)!;
          }
          const response = await firstValueFrom(
            this.apiService.get<{ success: boolean; message: string; data: string[] }>(
              `/location/districts?state=${encodeURIComponent(stateName)}`
            )
          );
          const districts = response.data.map(d => ({ label: d, value: d }));
          this.districtCache.set(stateName, districts);
          return districts;
        })
      );

      const aggregated = results.flat();
      this.districtOptions.set(aggregated);
      this.clearOrphanDistricts(aggregated);
    } catch (error) {
      console.error('Failed to load districts:', error);
    } finally {
      this.loadingDistricts.set(false);
    }
  }

  private clearOrphanDistricts(validDistricts: { label: string; value: string }[]): void {
    const validSet = new Set(validDistricts.map(d => d.value));
    this.selectedValues.update(store => ({
      ...store,
      districts: store.districts.filter(d => validSet.has(d))
    }));
  }

  private generateFinancialYears(count: number): { label: string; value: string }[] {
    const today = new Date();
    const fyStart = today.getMonth() >= 3 ? today.getFullYear() : today.getFullYear() - 1;

    return Array.from({ length: count }, (_, i) => {
      const start = fyStart - i;
      const end = start + 1;
      return { label: `FY ${start}-${String(end).slice(-2)}`, value: `${start}-${end}` };
    });
  }

  async toggleAllActiveCategory(): Promise<void> {
    const categoryId = this.selectedCategoryId() as keyof SelectedFilters;
    const options = this.activeCategoryOptions();

    if (this.isAllActiveCategorySelected()) {
      this.selectedValues.update(store => ({ ...store, [categoryId]: [] }));
    } else {
      this.selectedValues.update(store => ({
        ...store,
        [categoryId]: options.map(o => o.value)
      }));
    }

    if (categoryId === 'states') {
      await this.handleStateSelectionChange();
    }
  }

  isOptionChecked(value: string): boolean {
    const categoryId = this.selectedCategoryId() as keyof SelectedFilters;
    return this.selectedValues()[categoryId]?.includes(value) ?? false;
  }

  clearAll(): void {
    this.districtOptions.set([]);
    this.selectedValues.set({ ...EMPTY_FILTERS });
    this.filterApply.emit(this.selectedValues());
  }

  applyFilters(): void {
    this.filterApply.emit(this.selectedValues());
    this.visible.set(false);
  }

  getSelectedCount(categoryId: string): number {
    const vals = this.selectedValues();
    return (vals[categoryId as keyof SelectedFilters] ?? []).length;
  }
}
