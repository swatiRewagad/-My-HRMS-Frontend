import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PastComplaintsComponent } from './past-complaints/past-complaints.component';
import { AttachmentComponent } from './attachment/attachment.component';
import { HistoryComponent } from './history/history.component';
import { LegalCaseComponent } from './legal-case/legal-case.component';
import { Attachment, HistoryEntry, LegalCase, LegalCaseFormShape, RightSidePanelKey } from './right-side-panel.types';

@Component({
  selector: 'app-right-side-panel',
  standalone: true,
  imports: [CommonModule, PastComplaintsComponent, AttachmentComponent, HistoryComponent, LegalCaseComponent],
  templateUrl: './right-side-panel.component.html',
  styleUrl: './right-side-panel.component.scss',
})
export class RightSidePanelComponent {
  @Input() activePanel: RightSidePanelKey = null;

  // Past Complaints + Attachment (sidebarAttachments/loadingSidebarAttachments shared by both)
  @Input() pastComplaints: any[] = [];
  @Input() loadingPastComplaints = false;
  @Input() sidebarAttachments: Attachment[] = [];
  @Input() loadingSidebarAttachments = false;
  @Input() uploadingDocument = false;
  @Input() complaintOffice = '';

  // History
  @Input() historyEntries: HistoryEntry[] = [];
  @Input() loadingHistory = false;
  @Input() hideComments = false;

  // Legal Case
  @Input() legalCase: LegalCase | null = null;
  @Input() loadingLegalCase = false;
  @Input() showLegalCaseDialog = false;
  @Input() legalCaseSaving = false;
  @Input() legalCaseError = '';
  @Input() legalCaseForm!: LegalCaseFormShape;
  @Input() legalCaseRegionOptions: { officeCode: string; officeName: string; officeType: string }[] = [];

  @Output() closePanel = new EventEmitter<void>();
  @Output() refreshPastComplaints = new EventEmitter<void>();
  @Output() downloadAttachment = new EventEmitter<Attachment>();
  @Output() uploadFile = new EventEmitter<Event>();
  @Output() previewAttachment = new EventEmitter<Attachment>();
  @Output() downloadAllAttachments = new EventEmitter<void>();
  @Output() hideCommentsChange = new EventEmitter<boolean>();
  @Output() openLegalCaseEditor = new EventEmitter<void>();
  @Output() closeLegalCaseDialog = new EventEmitter<void>();
  @Output() legalCaseFormChange = new EventEmitter<LegalCaseFormShape>();
  @Output() saveLegalCase = new EventEmitter<void>();
}
