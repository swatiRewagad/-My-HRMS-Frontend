import { Component, model, output, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DrawerModule } from 'primeng/drawer';
import { ListboxModule } from 'primeng/listbox';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';
import { FilterCategory, SelectedFilters } from '../../../models/rbio.model';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-rbio-dashboard-filter',
  standalone: true,
  imports: [CommonModule, FormsModule, DrawerModule, ListboxModule, CheckboxModule, ButtonModule],
  templateUrl: './rbio-dashboard-filter.component.html',
  styleUrl: './rbio-dashboard-filter.component.scss'
})
export class RbioDashboardFilterComponent {
  private apiService = inject(ApiService);

  visible = model<boolean>(false);

  onFilterApply = output<SelectedFilters>();

  selectedCategoryId = signal<string>('states');

  selectedValues = signal<SelectedFilters>({
    states: [],
    districts: [],
    years: [],
    quarters: [],
    meetingTypes: [],
    documentTypes: []
  });

  readonly stateOptions = signal<{ label: string; value: string }[]>([]);
  readonly districtOptions = signal<{ label: string; value: string }[]>([]);
  readonly loadingDistricts = signal<boolean>(false);
  private districtCache = new Map<string, { label: string; value: string }[]>();

  readonly filterCategories = computed<FilterCategory[]>(() => [
    { id: 'states', label: 'State', options: this.stateOptions() },
    { id: 'districts', label: 'District', options: this.districtOptions() },
    { id: 'year', label: 'Year', options: this.generateFinancialYears(5) },
    {
      id: 'quarter',
      label: 'Quarter',
      options: [
        { label: 'Quarter 1 (Apr - Jun)', value: 'Q1' },
        { label: 'Quarter 2 (Jul - Sep)', value: 'Q2' },
        { label: 'Quarter 3 (Oct - Dec)', value: 'Q3' },
        { label: 'Quarter 4 (Jan - Mar)', value: 'Q4' }
      ]
    }
  ]);

  activeCategoryOptions = computed(() => {
    const currentId = this.selectedCategoryId();
    const match = this.filterCategories().find(c => c.id === currentId);
    return match ? match.options : [];
  });

  constructor() {
    this.loadStates();
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
      console.error('Failed to resolve States configuration master data', error);
    }
  }

  async onCheckboxChange(value: string, checked: boolean): Promise<void> {
    const currentId = this.selectedCategoryId() as keyof SelectedFilters;

    this.selectedValues.update(store => {
      const currentSelection = [...(store[currentId] || [])];

      if (checked) {
        if (!currentSelection.includes(value)) currentSelection.push(value);
      } else {
        const index = currentSelection.indexOf(value);
        if (index > -1) currentSelection.splice(index, 1);
      }
      return { ...store, [currentId]: currentSelection };
    });

    if (currentId === 'states' || currentId === ('states' as any)) {
      await this.handleStateSelectionChange();
    }
  }

  private async handleStateSelectionChange(): Promise<void> {
    const selectedStates = this.selectedValues()['states'];

    if (!selectedStates || selectedStates.length === 0) {
      this.districtOptions.set([]);
      this.clearOrphanDistricts([]);
      return;
    }

    this.loadingDistricts.set(true);
    try {
      const aggregatedDistricts: { label: string; value: string }[] = [];

      for (const stateName of selectedStates) {
        if (this.districtCache.has(stateName)) {
          aggregatedDistricts.push(...this.districtCache.get(stateName)!);
        } else {
          const response = await firstValueFrom(
            this.apiService.get<{ success: boolean; message: string; data: string[] }>(
              `/location/districts?state=${encodeURIComponent(stateName)}`
            )
          );

          const formattedDistricts = response.data.map(district => ({
            label: district,
            value: district
          }));

          this.districtCache.set(stateName, formattedDistricts);
          aggregatedDistricts.push(...formattedDistricts);
        }
      }

      this.districtOptions.set(aggregatedDistricts);
      this.clearOrphanDistricts(aggregatedDistricts);
    } catch (error) {
      console.error('Failed to load dependent districts code', error);
    } finally {
      this.loadingDistricts.set(false);
    }
  }

  private clearOrphanDistricts(validDistricts: { label: string; value: string }[]): void {
    const validSet = new Set(validDistricts.map(d => d.value));
    this.selectedValues.update(store => {
      const activeDistricts = (store?.['districts'] || []).filter(d => validSet.has(d));
      return { ...store, districts: activeDistricts };
    });
  }

  private generateFinancialYears(numberOfYears: number): { label: string; value: string }[] {
    const options: { label: string; value: string }[] = [];
    const today = new Date();
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth();
    let currentFinancialYearStart = currentMonth >= 3 ? currentYear : currentYear - 1;

    for (let i = 0; i < numberOfYears; i++) {
      const startYear = currentFinancialYearStart - i;
      const endYear = startYear + 1;
      const shortEndYear = String(endYear).slice(-2);
      options.push({ label: `FY ${startYear}-${shortEndYear}`, value: `${startYear}-${endYear}` });
    }

    return options;
  }

  isOptionChecked(value: string): boolean {
    const currentId = this.selectedCategoryId() as keyof SelectedFilters;
    return this.selectedValues()[currentId]?.includes(value) || false;
  }

  clearAll(): void {
    this.districtOptions.set([]);
    this.selectedValues.set({ states: [], districts: [], meetingTypes: [], years: [], quarters: [], documentTypes: [] });
    this.onFilterApply.emit(this.selectedValues());
  }

  applyFilters(): void {
    this.onFilterApply.emit(this.selectedValues());
    this.visible.set(false);
  }

  getSelectedCount(categoryId: keyof SelectedFilters): number {
    return this.selectedValues()[categoryId]?.length || 0;
  }
}
