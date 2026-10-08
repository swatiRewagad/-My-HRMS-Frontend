import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Attachment } from '../right-side-panel.types';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-right-side-panel-past-complaints',
  standalone: true,
  imports: [CommonModule, TranslateOrPipe],
  templateUrl: './past-complaints.component.html',
  styleUrl: './past-complaints.component.scss',
})
export class PastComplaintsComponent {
  @Input() pastComplaints: any[] = [];
  @Input() loadingPastComplaints = false;
  @Input() sidebarAttachments: Attachment[] = [];
  @Input() loadingSidebarAttachments = false;
  @Input() complaintOffice = '';

  @Output() refresh = new EventEmitter<void>();
  @Output() downloadAttachment = new EventEmitter<Attachment>();
}
