import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Attachment } from '../right-side-panel.types';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-right-side-panel-attachment',
  standalone: true,
  imports: [CommonModule, TranslateOrPipe],
  templateUrl: './attachment.component.html',
  styleUrl: './attachment.component.scss',
})
export class AttachmentComponent {
  @Input() sidebarAttachments: Attachment[] = [];
  @Input() loadingSidebarAttachments = false;
  @Input() uploadingDocument = false;

  @Output() close = new EventEmitter<void>();
  @Output() uploadFile = new EventEmitter<Event>();
  @Output() previewAttachment = new EventEmitter<Attachment>();
  @Output() downloadAttachment = new EventEmitter<Attachment>();
  @Output() downloadAll = new EventEmitter<void>();
}
