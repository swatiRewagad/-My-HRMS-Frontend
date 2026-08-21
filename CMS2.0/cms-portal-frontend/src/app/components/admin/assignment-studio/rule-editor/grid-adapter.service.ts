import { Injectable } from '@angular/core';

export interface CellRange {
  startRow: number;
  endRow: number;
  startCol: string;
  endCol: string;
}

export interface ParseResult {
  success: boolean;
  rows: Record<string, string>[];
  errors: { row: number; col: string; message: string }[];
}

export interface RefreshParams {
  rowNodes?: any[];
  columns?: string[];
  force?: boolean;
}

/**
 * Abstraction over the grid vendor (AG Grid Community).
 * All AG Grid API calls are routed through this service.
 * Swap the implementation to migrate to another grid library or AG Grid Enterprise.
 */
@Injectable({ providedIn: 'root' })
export class GridAdapterService {

  private gridApi: any = null;

  setGridApi(api: any): void {
    this.gridApi = api;
  }

  getGridApi(): any {
    return this.gridApi;
  }

  setRowData(rows: any[]): void {
    this.gridApi?.setGridOption('rowData', rows);
  }

  getRowData(): any[] {
    const rows: any[] = [];
    this.gridApi?.forEachNode((node: any) => rows.push(node.data));
    return rows;
  }

  getSelectedRows(): any[] {
    return this.gridApi?.getSelectedRows() ?? [];
  }

  getSelectedRange(): CellRange | null {
    const ranges = this.gridApi?.getCellRanges?.();
    if (!ranges || ranges.length === 0) return null;
    const range = ranges[0];
    return {
      startRow: range.startRow.rowIndex,
      endRow: range.endRow.rowIndex,
      startCol: range.columns[0].getColId(),
      endCol: range.columns[range.columns.length - 1].getColId()
    };
  }

  startEditing(rowIndex: number, colId: string): void {
    this.gridApi?.startEditingCell({ rowIndex, colKey: colId });
  }

  stopEditing(): void {
    this.gridApi?.stopEditing();
  }

  refreshCells(params: RefreshParams): void {
    this.gridApi?.refreshCells(params);
  }

  exportToClipboard(): string {
    const rows = this.getRowData();
    return rows.map(r => Object.values(r).join('\t')).join('\n');
  }

  importFromClipboard(tsv: string, columns: string[]): ParseResult {
    const lines = tsv.split('\n').filter(l => l.trim().length > 0);
    const rows: Record<string, string>[] = [];
    const errors: { row: number; col: string; message: string }[] = [];

    for (let i = 0; i < lines.length; i++) {
      const cells = lines[i].split('\t');
      const row: Record<string, string> = {};
      for (let j = 0; j < columns.length && j < cells.length; j++) {
        row[columns[j]] = cells[j]?.trim() ?? '';
      }
      rows.push(row);
    }

    return { success: errors.length === 0, rows, errors };
  }
}
