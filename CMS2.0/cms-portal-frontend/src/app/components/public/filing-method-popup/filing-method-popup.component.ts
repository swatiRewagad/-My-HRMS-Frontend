import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Dialog } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

export type FilingMethodType = 'email' | 'physical';

export interface FilingMethodConfig {
  type: FilingMethodType;
  title: string;
  icon: string;
  description: string;
  address?: string;
  emailId?: string;
  downloadLabel: string;
  downloadUrl: string;
  infoMessage: string;
  cmsPortalLabel: string;
}

export const FILING_METHOD_CONFIGS: Record<FilingMethodType, FilingMethodConfig> = {
  email: {
    type: 'email',
    title: 'Email',
    icon: 'pi pi-envelope',
    description: 'Send your complaint to use via email by filling the Complaint Form given below.',
    emailId: 'crpc@rbi.org.in',
    downloadLabel: 'Download Complaint Form',
    downloadUrl: 'assets/documents/ComplaintForm.pdf',
    infoMessage: 'Filing complaints through this website, which is called the CMS Portal, is the fastest way of resolving your complaints. Complainants are encouraged to file complaints here.',
    cmsPortalLabel: 'File Complaint on CMS Portal',
  },
  physical: {
    type: 'physical',
    title: 'Physical Letter',
    icon: 'pi pi-file-edit',
    description: 'Send your complaint to us via letter by filling the Complaint Form given below.',
    address: 'Centralised Receipt and Processing Centre (CRPC)\nReserve Bank of India\n4th Floor, Sector 17, Central Vista\nChandigarh - 160017...',
    downloadLabel: 'Download Complaint Form',
    downloadUrl: 'assets/documents/ComplaintForm.pdf',
    infoMessage: 'Filing complaints through this website, which is called the CMS Portal, is the fastest way of resolving your complaints. Complainants are encouraged to file complaints here.',
    cmsPortalLabel: 'File Complaint on CMS Portal',
  },
};

@Component({
  selector: 'app-filing-method-popup',
  standalone: true,
  imports: [CommonModule, Dialog, ButtonModule],
  templateUrl: './filing-method-popup.component.html',
  styleUrl: './filing-method-popup.component.scss',
})
export class FilingMethodPopupComponent {
  @Input() visible = false;
  @Input() methodType: FilingMethodType = 'email';
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() fileOnPortal = new EventEmitter<void>();

  get config(): FilingMethodConfig {
    return FILING_METHOD_CONFIGS[this.methodType];
  }

  onHide() {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  onCancel() {
    this.onHide();
  }

  onOk() {
    this.onHide();
  }

  onFileOnPortal() {
    this.fileOnPortal.emit();
    this.onHide();
  }

  onDownloadForm() {
    window.open(this.config.downloadUrl, '_blank');
  }
}
