import { Component, Input, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-rbio-sla-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rbio-sla-progress.component.html',
  styleUrl: './rbio-sla-progress.component.scss'
})
export class RbioSlaProgressComponent {
  @Input() set complaint(value: any) { this._complaint.set(value); }
  @Input() set currentStage(value: string) { this._currentStage.set(value); }

  private _complaint = signal<any>(null);
  private _currentStage = signal<string>('');

  // Stage definitions with durations in days
  readonly stages = [
    { key: 'officer', label: 'Officer', days: 30 },
    { key: 'conciliation', label: 'Conciliation', days: 30 },
    { key: 'adjudication', label: 'Adjudication', days: 60 }
  ];

  readonly TOTAL_LIFECYCLE_DAYS = 120;

  activeStageKey = computed(() => {
    const stage = (this._currentStage() || '').toLowerCase();
    if (stage.includes('adjudication')) return 'adjudication';
    if (stage.includes('conciliation')) return 'conciliation';
    return 'officer';
  });

  activeStageIndex = computed(() => {
    const key = this.activeStageKey();
    return this.stages.findIndex(s => s.key === key);
  });

  stageStatuses = computed(() => {
    const activeIdx = this.activeStageIndex();
    return this.stages.map((stage, idx) => {
      if (idx < activeIdx) return 'completed';
      if (idx === activeIdx) return 'active';
      return 'pending';
    });
  });

  // Calculate time remaining in current stage
  stageStartDate = computed(() => {
    const c = this._complaint();
    // Use stageStartDate if available, otherwise registeredAt
    return c?.stageStartDate || c?.registeredAt || '';
  });

  currentStageDays = computed(() => {
    const idx = this.activeStageIndex();
    return this.stages[idx]?.days || 30;
  });

  daysElapsedInStage = computed(() => {
    const start = this.stageStartDate();
    if (!start) return 0;
    const startDate = new Date(start);
    const now = new Date();
    return Math.max(0, Math.ceil((now.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)));
  });

  daysRemainingInStage = computed(() => {
    return Math.max(0, this.currentStageDays() - this.daysElapsedInStage());
  });

  stageProgressPercent = computed(() => {
    const total = this.currentStageDays();
    if (total === 0) return 0;
    return Math.min(100, (this.daysElapsedInStage() / total) * 100);
  });

  // Total lifecycle progress
  totalDaysElapsed = computed(() => {
    const c = this._complaint();
    const regDate = c?.registeredAt;
    if (!regDate) return 0;
    const start = new Date(regDate);
    const now = new Date();
    return Math.max(0, Math.ceil((now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)));
  });

  totalProgressPercent = computed(() => {
    return Math.min(100, (this.totalDaysElapsed() / this.TOTAL_LIFECYCLE_DAYS) * 100);
  });

  totalDaysRemaining = computed(() => {
    return Math.max(0, this.TOTAL_LIFECYCLE_DAYS - this.totalDaysElapsed());
  });

  // Color coding based on percentage remaining
  stageColorClass = computed(() => {
    const remaining = this.daysRemainingInStage();
    const total = this.currentStageDays();
    if (total === 0) return 'green';
    const pctRemaining = (remaining / total) * 100;
    if (pctRemaining > 50) return 'green';
    if (pctRemaining > 25) return 'yellow';
    return 'red';
  });

  totalColorClass = computed(() => {
    const remaining = this.totalDaysRemaining();
    const pctRemaining = (remaining / this.TOTAL_LIFECYCLE_DAYS) * 100;
    if (pctRemaining > 50) return 'green';
    if (pctRemaining > 25) return 'yellow';
    return 'red';
  });
}
