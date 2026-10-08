import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-cepc-complaint-meta-row',
  standalone: true,
  imports: [CommonModule, TranslateOrPipe],
  templateUrl: './cepc-complaint-meta-row.component.html',
  styleUrl: './cepc-complaint-meta-row.component.scss'
})
export class CepcComplaintMetaRowComponent {
  readonly complaintNumber = input<string>('');
  readonly complainantName = input<string>('');
  readonly entityName = input<string>('');
  readonly status = input<string>('');
  readonly statusClass = input<string>('');
  readonly category = input<string>('');
  readonly sla = input<string>('');
  readonly slaBreach = input<boolean>(false);

  readonly back = output<void>();
}
