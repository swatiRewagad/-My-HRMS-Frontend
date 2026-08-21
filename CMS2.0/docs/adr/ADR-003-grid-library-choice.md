# ADR-003: Grid Library Choice

**Status:** Accepted  
**Date:** 2026-08-17  
**Decision Makers:** Architecture team  

## Context

The Assignment Studio requires an Excel-like grid with:
- 2,000+ rows with virtual scrolling
- Custom cell editors (multi-select, typeahead, date picker, range input)
- Paste from Excel (TSV clipboard parsing)
- Fill down, keyboard navigation
- Inline validation with cell-level error indicators

The existing CMS frontend uses PrimeNG 21. We evaluated:
1. **PrimeNG Table** (already in the project)
2. **AG Grid Community** (new dependency)
3. **Handsontable** (new dependency, dual-licensed)

## Decision

AG Grid Community Edition, wrapped behind a `GridAdapter` service abstraction.

## Rationale

| Criteria | PrimeNG Table | AG Grid Community | Handsontable |
|----------|---------------|-------------------|--------------|
| Virtual scrolling (5,000 rows) | Limited (virtual scroller exists but buggy with editable cells) | Excellent | Good |
| Custom cell editors | Possible but verbose | First-class API | Good |
| Clipboard paste | Must implement from scratch | Community supports cell paste; range paste is Enterprise but achievable via custom handler | Built-in |
| Column virtualisation | No | Yes | No |
| Licence | MIT | MIT (Community) | Commercial for non-OSS |
| Bundle size | Already included | +250KB gzipped | +300KB |

Key factors:
- PrimeNG Table cannot handle 5,000 editable rows with custom editors without significant performance issues.
- AG Grid Community provides the cell editing and virtualisation foundation. Range selection and fill handle are Enterprise-only, but we implement fill-down via a custom keyboard handler (`Ctrl+D`) over the cell selection API.
- Handsontable's commercial licence is incompatible with our deployment model.

## GridAdapter Abstraction

All AG Grid vendor calls are isolated behind a `GridAdapter` service interface:

```typescript
export interface GridAdapter {
  setRowData(rows: RuleRow[]): void;
  getSelectedRows(): RuleRow[];
  getSelectedRange(): CellRange | null;
  startEditing(rowIndex: number, colId: string): void;
  stopEditing(): void;
  refreshCells(params: RefreshParams): void;
  exportToClipboard(): string;
  importFromClipboard(tsv: string): ParseResult;
}
```

This allows swapping to AG Grid Enterprise (if a licence is acquired) or another library without touching feature code.

## Consequences

- New dependency: `ag-grid-community` added to `cms-portal-frontend`.
- PrimeNG remains for all other UI (tables, dialogs, forms, etc.) — only the decision-table grid uses AG Grid.
- Custom implementation required for: clipboard paste parsing, fill-down, range operations. These are scoped to Phase 2.
- Must not accidentally import any `@ag-grid-enterprise/*` package.

## Risks

- If AG Grid Community's cell selection API proves insufficient for the paste preview (select target range → paste), we will implement a modal-based paste flow (paste into a preview panel, then apply). This is acceptable UX.
