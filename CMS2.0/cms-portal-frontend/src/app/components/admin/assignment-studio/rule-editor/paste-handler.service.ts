import { Injectable } from '@angular/core';

export interface ParsedRow {
  [column: string]: any;
}

export interface PasteError {
  row: number;
  col: string;
  message: string;
}

export interface PasteResult {
  success: boolean;
  rows: ParsedRow[];
  errors: PasteError[];
}

@Injectable({ providedIn: 'root' })
export class PasteHandlerService {

  parse(tsv: string, targetColumns: string[]): PasteResult {
    const errors: PasteError[] = [];
    const rows: ParsedRow[] = [];

    const lines = tsv.split(/\r?\n/).filter(line => line.trim() !== '');

    for (let i = 0; i < lines.length; i++) {
      const cells = lines[i].split('\t');
      const parsed: ParsedRow = {};

      for (let j = 0; j < cells.length && j < targetColumns.length; j++) {
        const col = targetColumns[j];
        const value = cells[j].trim();
        parsed[col] = this.coerce(col, value);
      }
      rows.push(parsed);
    }

    return { success: errors.length === 0, rows, errors };
  }

  private coerce(col: string, value: string): any {
    if (col === 'priority' || col === 'rowOrder') {
      const num = parseInt(value, 10);
      return isNaN(num) ? 0 : num;
    }
    if (col === 'enabled') {
      const lower = value.toLowerCase();
      return lower === 'true' || lower === 'yes' || lower === '1' || lower === '✅';
    }
    return value;
  }
}
