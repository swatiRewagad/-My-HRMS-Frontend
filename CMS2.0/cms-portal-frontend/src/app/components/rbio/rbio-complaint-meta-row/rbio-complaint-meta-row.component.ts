import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-rbio-complaint-meta-row',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rbio-complaint-meta-row.component.html',
  styleUrl: './rbio-complaint-meta-row.component.scss'
})
export class RbioComplaintMetaRowComponent {
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
