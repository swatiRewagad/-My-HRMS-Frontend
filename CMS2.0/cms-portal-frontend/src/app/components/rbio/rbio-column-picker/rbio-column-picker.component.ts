import { Component, computed, input, model, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ColumnDefinition } from '../../../models/rbio.model';

interface PickerItem extends ColumnDefinition {
  visible: boolean;
}

@Component({
  selector: 'app-rbio-column-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, CheckboxModule, ButtonModule, InputTextModule],
  templateUrl: './rbio-column-picker.component.html',
  styleUrl: './rbio-column-picker.component.scss'
})
export class RbioColumnPickerComponent {
  activeTargetIndex: number | null = null;

  readonly allColumns = input.required<ColumnDefinition[]>();
  readonly initiallySelected = input.required<ColumnDefinition[]>();
  readonly tableName = input<string>('Complaints');

  readonly visible = model<boolean>(false);

  readonly columnsSaved = output<ColumnDefinition[]>();

  readonly searchColumnQuery = signal<string>('');

  private readonly tempColumns = signal<PickerItem[]>([]);
  private draggedIndex: number | null = null;

  initializePicker(): void {
    const currentSelectedFields = new Set(this.initiallySelected().map(c => c.field));
    const activeOrderedFields = this.initiallySelected();
    const remainingFields = this.allColumns().filter(c => !currentSelectedFields.has(c.field));

    this.tempColumns.set([
      ...activeOrderedFields.map(c => ({ ...c, visible: true })),
      ...remainingFields.map(c => ({ ...c, visible: false }))
    ]);

    this.searchColumnQuery.set('');
  }

  readonly filteredTempColumns = computed(() => {
    const query = this.searchColumnQuery().toLowerCase().trim();
    if (!query) return this.tempColumns();
    return this.tempColumns().filter(c => c.header.toLowerCase().includes(query));
  });

  onDragStart(index: number): void {
    this.draggedIndex = index;
  }

  onDragOver(event: DragEvent, index: number): void {
    event.preventDefault();
    if (this.draggedIndex === null || this.draggedIndex === index) return;

    const targetArray = [...this.tempColumns()];
    const movedItem = targetArray.splice(this.draggedIndex, 1)[0];
    targetArray.splice(index, 0, movedItem);

    this.tempColumns.set(targetArray);
    this.draggedIndex = index;
  }

  onDragEnd(): void {
    this.draggedIndex = null;
  }

  saveColumnConfiguration(): void {
    const savedActiveColumns = this.tempColumns()
      .filter(c => c.visible)
      .map(({ visible, ...col }) => col as ColumnDefinition);

    this.columnsSaved.emit(savedActiveColumns);
    this.visible.set(false);
  }

  toggleAll(checkAll: boolean): void {
    const updatedList = this.tempColumns().map(item => ({
      ...item,
      visible: checkAll
    }));
    this.tempColumns.set(updatedList);
  }

  onLocalDragOver(event: DragEvent, index: number): void {
    this.activeTargetIndex = index;
    this.onDragOver(event, index);
  }

  onLocalDragEnd(): void {
    this.activeTargetIndex = null;
    this.onDragEnd();
  }
}
