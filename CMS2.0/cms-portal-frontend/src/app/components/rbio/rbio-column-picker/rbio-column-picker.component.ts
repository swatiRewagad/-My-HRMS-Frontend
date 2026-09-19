import { Component, computed, input, model, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { Checkbox } from 'primeng/checkbox';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Tag } from 'primeng/tag';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { Tooltip } from 'primeng/tooltip';
import { Divider } from 'primeng/divider';
import { ColumnDefinition } from '../../../models/rbio.model';

interface PickerItem extends ColumnDefinition {
  visible: boolean;
}

@Component({
  selector: 'app-rbio-column-picker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    Checkbox,
    Button,
    InputText,
    Tag,
    IconField,
    InputIcon,
    Tooltip,
    Divider
  ],
  templateUrl: './rbio-column-picker.component.html',
  styleUrl: './rbio-column-picker.component.scss'
})
export class RbioColumnPickerComponent {

  readonly allColumns = input.required<ColumnDefinition[]>();
  readonly initiallySelected = input.required<ColumnDefinition[]>();
  readonly tableName = input<string>('Complaints');

  readonly visible = model<boolean>(false);
  readonly columnsSaved = output<ColumnDefinition[]>();
  readonly columnsReset = output<void>();

  readonly searchQuery = signal('');
  readonly items = signal<PickerItem[]>([]);

  private draggedField: string | null = null;
  dropTargetField: string | null = null;

  readonly filteredItems = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return this.items();
    return this.items().filter(c => c.header.toLowerCase().includes(q));
  });

  readonly selectedCount = computed(() => this.items().filter(c => c.visible).length);
  readonly totalCount = computed(() => this.items().length);

  readonly allFilteredSelected = computed(() => {
    const filtered = this.filteredItems();
    return filtered.length > 0 && filtered.every(c => c.visible);
  });

  readonly noneFilteredSelected = computed(() => {
    const filtered = this.filteredItems();
    return filtered.length > 0 && filtered.every(c => !c.visible);
  });

  readonly isSearchActive = computed(() => this.searchQuery().trim().length > 0);

  initializePicker(): void {
    const selectedFields = new Set(this.initiallySelected().map(c => c.field));
    const selected = this.initiallySelected().map(c => ({ ...c, visible: true }));
    const unselected = this.allColumns()
      .filter(c => !selectedFields.has(c.field))
      .map(c => ({ ...c, visible: false }));

    this.items.set([...selected, ...unselected]);
    this.searchQuery.set('');
  }

  toggleItem(field: string): void {
    this.items.update(list =>
      list.map(item => item.field === field ? { ...item, visible: !item.visible } : item)
    );
  }

  toggleAll(selectAll: boolean): void {
    const visibleFields = new Set(this.filteredItems().map(c => c.field));
    this.items.update(list =>
      list.map(item => visibleFields.has(item.field) ? { ...item, visible: selectAll } : item)
    );
  }

  resetToDefault(): void {
    this.columnsReset.emit();
    this.visible.set(false);
  }

  onDragStart(field: string): void {
    this.draggedField = field;
  }

  onDragOver(event: DragEvent, targetField: string): void {
    event.preventDefault();
    if (!this.draggedField || this.draggedField === targetField) return;
    if (this.isSearchActive()) return;

    this.dropTargetField = targetField;

    this.items.update(list => {
      const result = [...list];
      const fromIdx = result.findIndex(c => c.field === this.draggedField);
      const toIdx = result.findIndex(c => c.field === targetField);
      if (fromIdx === -1 || toIdx === -1) return list;

      const [moved] = result.splice(fromIdx, 1);
      result.splice(toIdx, 0, moved);
      return result;
    });
  }

  onDragEnd(): void {
    this.draggedField = null;
    this.dropTargetField = null;
  }

  save(): void {
    const active = this.items()
      .filter(c => c.visible)
      .map(({ visible, ...col }) => col as ColumnDefinition);

    this.columnsSaved.emit(active);
    this.visible.set(false);
  }

  cancel(): void {
    this.visible.set(false);
  }
}
