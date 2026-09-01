import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AssignmentStudioService, AttributeDefinition } from '../../../../services/assignment-studio.service';

interface RuleRow {
  id: number | null;
  ruleCode: string;
  name: string;
  priority: number;
  rowOrder: number;
  enabled: boolean;
  description: string;
  conditions: Record<string, string>;
  _outcomeType: string;
  _outcomeTarget: string;
  _assignMode: string;
  selected: boolean;
}

@Component({
  selector: 'app-assignment-rule-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-editor.component.html',
  styleUrl: './rule-editor.component.scss'
})
export class AssignmentRuleEditorComponent implements OnInit, OnDestroy {

  rulesetId = signal<number>(0);
  versionId = signal<number>(0);
  versionStatus = signal<string>('');
  hitPolicy = signal<string>('FIRST');
  loading = signal(false);
  saving = signal(false);
  dirty = signal(false);
  etag = signal<string>('0');
  errorMessage = signal('');
  successMessage = signal('');
  attributes = signal<AttributeDefinition[]>([]);
  defaultOutcomeType = signal('QUEUE');
  defaultOutcomeTarget = signal('GENERAL_INTAKE_POOL');

  rows = signal<RuleRow[]>([]);
  validationResult = signal<any>(null);

  private autosaveTimer: any = null;

  readonly isReadOnly = computed(() => this.versionStatus() !== 'DRAFT');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private studioService: AssignmentStudioService
  ) {}

  ngOnInit() {
    const params = this.route.snapshot.paramMap;
    this.rulesetId.set(+(params.get('rulesetId') || 0));
    this.versionId.set(+(params.get('versionId') || 0));
    this.loadAttributes();
  }

  ngOnDestroy() {
    if (this.autosaveTimer) clearInterval(this.autosaveTimer);
  }

  private loadAttributes() {
    this.studioService.getAttributes().subscribe({
      next: (attrs) => {
        this.attributes.set(attrs);
        this.loadVersionData();
      },
      error: () => {
        this.showError('Failed to load attributes');
        this.loadVersionData();
      }
    });
  }

  private loadVersionData() {
    this.loading.set(true);
    this.studioService.getVersion(this.rulesetId(), this.versionId()).subscribe({
      next: (payload: any) => {
        this.versionStatus.set(payload.version.status);
        this.hitPolicy.set(payload.ruleSet.hitPolicy);
        this.etag.set(payload.etag || '0');
        if (payload.defaultOutcome) {
          this.defaultOutcomeType.set(payload.defaultOutcome.outcomeType);
          this.defaultOutcomeTarget.set(payload.defaultOutcome.targetId);
        }
        this.rows.set(this.mapRules(payload.rules));
        this.loading.set(false);
        this.dirty.set(false);
        this.startAutosave();
      },
      error: () => {
        this.loading.set(false);
        this.showError('Failed to load version');
      }
    });
  }

  private mapRules(rules: any[]): RuleRow[] {
    return rules.map((r: any) => {
      const conditions: Record<string, string> = {};
      if (r.conditions) {
        for (const cond of r.conditions) {
          conditions[cond.attributeCode] = this.formatCondition(cond);
        }
      }
      return {
        id: r.id,
        ruleCode: r.ruleCode,
        name: r.name,
        priority: r.priority,
        rowOrder: r.rowOrder,
        enabled: r.enabled,
        description: r.description || '',
        conditions,
        _outcomeType: r.outcome?.outcomeType || '',
        _outcomeTarget: r.outcome?.targetId || '',
        _assignMode: r.outcome?.assignMode || '',
        selected: false
      };
    });
  }

  private formatCondition(cond: any): string {
    const op = cond.operator;
    if (op === 'GTE') return `>= ${this.fmtNum(cond.valueNumFrom)}`;
    if (op === 'LTE') return `<= ${this.fmtNum(cond.valueNumFrom)}`;
    if (op === 'GT') return `> ${this.fmtNum(cond.valueNumFrom)}`;
    if (op === 'LT') return `< ${this.fmtNum(cond.valueNumFrom)}`;
    if (op === 'BETWEEN') return `${this.fmtNum(cond.valueNumFrom)} - ${this.fmtNum(cond.valueNumTo)}`;
    if (op === 'EQ') return cond.valueText || '';
    if (op === 'NEQ') return `!= ${cond.valueText || ''}`;
    if (op === 'IN') return cond.valueList || '';
    if (op === 'NOT_IN') return `NOT: ${cond.valueList || ''}`;
    if (op === 'IS_NULL') return 'NULL';
    if (op === 'IS_NOT_NULL') return 'NOT NULL';
    if (op === 'IS_TRUE') return 'True';
    if (op === 'IS_FALSE') return 'False';
    if (op === 'STARTS_WITH') return `${cond.valueText}*`;
    if (op === 'CONTAINS') return `*${cond.valueText}*`;
    return cond.valueText || cond.valueList || '';
  }

  private fmtNum(num: any): string {
    if (num == null) return '';
    return Number(num).toLocaleString('en-IN');
  }

  markDirty() {
    this.dirty.set(true);
  }

  addRow() {
    const current = [...this.rows()];
    const nextPrio = current.length > 0 ? Math.max(...current.map(r => r.priority)) + 10 : 10;
    current.push({
      id: null,
      ruleCode: `R-${String(current.length + 1).padStart(3, '0')}`,
      name: 'New Rule',
      priority: nextPrio,
      rowOrder: nextPrio,
      enabled: true,
      description: '',
      conditions: {},
      _outcomeType: 'GROUP',
      _outcomeTarget: '',
      _assignMode: 'AS_GROUP',
      selected: false
    });
    this.rows.set(current);
    this.dirty.set(true);
  }

  duplicateSelected() {
    const current = [...this.rows()];
    const selected = current.filter(r => r.selected);
    if (selected.length === 0) return;
    for (const sel of selected) {
      current.push({
        ...sel,
        id: null,
        ruleCode: sel.ruleCode + '_COPY',
        priority: sel.priority + 5,
        selected: false,
        conditions: { ...sel.conditions }
      });
    }
    current.sort((a, b) => a.priority - b.priority);
    this.rows.set(current);
    this.dirty.set(true);
  }

  deleteSelected() {
    const remaining = this.rows().filter(r => !r.selected);
    if (remaining.length === this.rows().length) return;
    this.rows.set(remaining);
    this.dirty.set(true);
  }

  toggleSelectAll(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.rows.set(this.rows().map(r => ({ ...r, selected: checked })));
  }

  save() {
    if (this.saving()) return;
    this.saving.set(true);
    this.clearMessages();

    const request = this.buildSaveRequest();
    this.studioService.saveRules(this.rulesetId(), this.versionId(), request as any, this.etag()).subscribe({
      next: (resp: any) => {
        this.etag.set(resp.optLock?.toString() || '0');
        this.dirty.set(false);
        this.saving.set(false);
        this.showSuccess('Rules saved successfully');
        this.loadVersionData();
      },
      error: (err) => {
        this.saving.set(false);
        if (err.status === 409) {
          this.showError('Conflict: this version was modified by another user. Please reload.');
        } else {
          this.showError('Save failed: ' + (err.error?.detail || err.message));
        }
      }
    });
  }

  private buildSaveRequest(): any {
    const attrs = this.attributes();
    const rules = this.rows().map(row => {
      const conditions: any[] = [];
      for (const attr of attrs) {
        const cellValue = row.conditions[attr.code];
        if (cellValue && cellValue.trim() !== '') {
          conditions.push(this.parseCellToCondition(attr.code, cellValue));
        }
      }
      return {
        id: row.id,
        ruleCode: row.ruleCode,
        name: row.name,
        description: row.description,
        priority: row.priority,
        rowOrder: row.rowOrder || row.priority,
        enabled: row.enabled,
        conditions,
        outcome: {
          outcomeType: row._outcomeType || 'GROUP',
          targetId: row._outcomeTarget || '',
          assignMode: row._assignMode || null,
          distributionStrategy: null,
          chainOrder: null,
          orgUnitFromAttribute: null,
        }
      };
    });

    return {
      rules,
      defaultOutcome: {
        outcomeType: this.defaultOutcomeType(),
        targetId: this.defaultOutcomeTarget(),
        assignMode: null,
        distributionStrategy: null,
      }
    };
  }

  private parseCellToCondition(attrCode: string, display: string): any {
    const trimmed = display.trim();
    if (trimmed.startsWith('>=')) return { attributeCode: attrCode, operator: 'GTE', valueNumFrom: this.parseNum(trimmed.substring(2)) };
    if (trimmed.startsWith('>')) return { attributeCode: attrCode, operator: 'GT', valueNumFrom: this.parseNum(trimmed.substring(1)) };
    if (trimmed.startsWith('<=')) return { attributeCode: attrCode, operator: 'LTE', valueNumFrom: this.parseNum(trimmed.substring(2)) };
    if (trimmed.startsWith('<')) return { attributeCode: attrCode, operator: 'LT', valueNumFrom: this.parseNum(trimmed.substring(1)) };
    if (trimmed.includes(' - ') && !isNaN(Number(trimmed.split(' - ')[0].replace(/[,\s]/g, '')))) {
      const parts = trimmed.split(' - ');
      return { attributeCode: attrCode, operator: 'BETWEEN', valueNumFrom: this.parseNum(parts[0]), valueNumTo: this.parseNum(parts[1]) };
    }
    if (trimmed.startsWith('!=')) return { attributeCode: attrCode, operator: 'NEQ', valueText: trimmed.substring(2).trim() };
    if (trimmed.toUpperCase() === 'NULL') return { attributeCode: attrCode, operator: 'IS_NULL' };
    if (trimmed.toUpperCase() === 'NOT NULL') return { attributeCode: attrCode, operator: 'IS_NOT_NULL' };
    if (trimmed === 'True') return { attributeCode: attrCode, operator: 'IS_TRUE' };
    if (trimmed === 'False') return { attributeCode: attrCode, operator: 'IS_FALSE' };
    if (trimmed.startsWith('NOT:')) return { attributeCode: attrCode, operator: 'NOT_IN', valueList: trimmed.substring(4).trim() };
    if (trimmed.includes(',')) return { attributeCode: attrCode, operator: 'IN', valueList: trimmed };
    return { attributeCode: attrCode, operator: 'EQ', valueText: trimmed };
  }

  private parseNum(s: string): string {
    return s.trim().replace(/[,\s₹]/g, '');
  }

  private startAutosave() {
    if (this.autosaveTimer) clearInterval(this.autosaveTimer);
    this.autosaveTimer = setInterval(() => {
      if (this.dirty() && !this.isReadOnly() && !this.saving()) {
        this.save();
      }
    }, 60000);
  }

  validateRules() {
    this.validationResult.set(null);
    this.studioService.validate(this.rulesetId(), this.versionId()).subscribe({
      next: (result) => {
        this.validationResult.set(result);
        if (result.valid) this.showSuccess('Validation passed — no errors found');
        else this.showError('Validation found issues — review below');
      },
      error: () => this.showError('Validation request failed')
    });
  }

  submitForApproval() {
    const remarks = prompt('Enter submission remarks (optional):') || '';
    this.studioService.submit(this.rulesetId(), this.versionId(), remarks).subscribe({
      next: () => { this.showSuccess('Version submitted for approval'); this.loadVersionData(); },
      error: (err) => this.showError(err.error?.message || 'Submit failed')
    });
  }

  publishVersion() {
    if (!confirm('Publish this version? It will become the active ruleset.')) return;
    this.studioService.publish(this.rulesetId(), this.versionId()).subscribe({
      next: () => { this.showSuccess('Version published successfully'); this.loadVersionData(); },
      error: (err) => this.showError(err.error?.message || 'Publish failed')
    });
  }

  onKeyDown(event: KeyboardEvent) {
    if (event.ctrlKey && event.key === 's') { event.preventDefault(); this.save(); }
  }

  private showError(msg: string) { this.errorMessage.set(msg); this.successMessage.set(''); }
  private showSuccess(msg: string) { this.successMessage.set(msg); this.errorMessage.set(''); }
  private clearMessages() { this.errorMessage.set(''); this.successMessage.set(''); }
}
