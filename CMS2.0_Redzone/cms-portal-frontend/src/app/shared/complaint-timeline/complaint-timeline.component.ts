import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StageInfo } from '../../models/complaint.model';

@Component({
  selector: 'app-complaint-timeline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stage-timeline" role="list" aria-label="Complaint progress stages">
      @for (stage of stages; track stage.stage; let last = $last) {
        <div class="stage-item" role="listitem"
             [attr.aria-current]="stage.status === 'current' ? 'step' : null"
             [class.completed]="stage.status === 'completed'"
             [class.current]="stage.status === 'current'"
             [class.pending]="stage.status === 'pending'">
          <div class="stage-circle">
            @if (stage.status === 'completed') {
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3 8.5L6 11.5L13 4.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            } @else {
              <span class="stage-number">{{ stage.stage }}</span>
            }
          </div>
          @if (!last) {
            <div class="stage-connector"
                 [class.completed]="stage.status === 'completed'">
            </div>
          }
          <div class="stage-label">{{ stage.label }}</div>
          @if (stage.date && stage.status !== 'pending') {
            <div class="stage-date">{{ formatDate(stage.date) }}</div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .stage-timeline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: 1.5rem 0;
      gap: 0;
    }

    .stage-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
      flex: 1;
      min-width: 0;
    }

    .stage-circle {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 14px;
      position: relative;
      z-index: 1;
      flex-shrink: 0;
    }

    .stage-item.completed .stage-circle {
      background: #2e7d32;
      color: #fff;
    }

    .stage-item.current .stage-circle {
      background: #1565c0;
      color: #fff;
      box-shadow: 0 0 0 4px rgba(21, 101, 192, 0.25);
      animation: pulse-ring 2s ease-in-out infinite;
    }

    .stage-item.pending .stage-circle {
      background: #fff;
      color: #9e9e9e;
      border: 2px dashed #bdbdbd;
    }

    .stage-number {
      font-size: 13px;
    }

    .stage-connector {
      position: absolute;
      top: 18px;
      left: calc(50% + 18px);
      right: calc(-50% + 18px);
      height: 3px;
      background: #e0e0e0;
      z-index: 0;
    }

    .stage-connector.completed {
      background: #2e7d32;
    }

    .stage-label {
      margin-top: 10px;
      font-size: 12px;
      font-weight: 600;
      color: #444;
      text-align: center;
      line-height: 1.3;
    }

    .stage-item.pending .stage-label {
      color: #9e9e9e;
    }

    .stage-item.current .stage-label {
      color: #1565c0;
    }

    .stage-date {
      margin-top: 4px;
      font-size: 11px;
      color: #888;
      text-align: center;
    }

    @keyframes pulse-ring {
      0% { box-shadow: 0 0 0 4px rgba(21, 101, 192, 0.25); }
      50% { box-shadow: 0 0 0 8px rgba(21, 101, 192, 0.1); }
      100% { box-shadow: 0 0 0 4px rgba(21, 101, 192, 0.25); }
    }

    @media (max-width: 600px) {
      .stage-timeline {
        flex-direction: column;
        align-items: flex-start;
        gap: 0;
      }
      .stage-item {
        flex-direction: row;
        align-items: center;
        gap: 12px;
      }
      .stage-connector {
        position: absolute;
        top: calc(50% + 18px);
        bottom: calc(-50% + 18px);
        left: 18px;
        right: auto;
        width: 3px;
        height: auto;
      }
      .stage-label, .stage-date {
        text-align: left;
        margin-top: 0;
      }
      .stage-date {
        margin-left: 4px;
      }
    }
  `]
})
export class ComplaintTimelineComponent {
  @Input() stages: StageInfo[] = [];
  @Input() currentStage: number = 1;

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }
}
