import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HistoryEntry } from '../right-side-panel.types';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-right-side-panel-history',
  standalone: true,
  imports: [CommonModule, TranslateOrPipe],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
})
export class HistoryComponent {
  @Input() historyEntries: HistoryEntry[] = [];
  @Input() loadingHistory = false;
  @Input() hideComments = false;

  @Output() hideCommentsChange = new EventEmitter<boolean>();
  @Output() close = new EventEmitter<void>();
}
