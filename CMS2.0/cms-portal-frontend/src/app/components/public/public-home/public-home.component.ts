import { Component, OnInit, inject, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';
import { Table, TableModule } from 'primeng/table';
import { Select } from 'primeng/select';
import { ComplaintRecord } from '../models';

@Component({
  selector: 'app-public-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, TableModule, Select],
  templateUrl: './public-home.component.html',
  styleUrl: './public-home.component.scss'
})
export class PublicHomeComponent implements OnInit {

  @ViewChild('dt') dt!: Table;
  @ViewChild('eduScroll') eduScroll!: ElementRef;

  private http = inject(HttpClient);
  private router = inject(Router);
  private complaintService = inject(ComplaintService);
  authService = inject(PublicAuthService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  statusOptions = [
    { label: 'All', value: '' },
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Pending', value: 'PENDING' },
    { label: 'In-Progress', value: 'IN_PROGRESS' },
    { label: 'Complaint Closed', value: 'CLOSED' },
    { label: 'Rejected', value: 'REJECTED' }
  ];

  selectedStatus = '';
  dateFrom = '';
  private entityMap: Record<string, string> = {};

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.loadEntities();
      this.loadComplaints();
    } else {
      this.loading.set(false);
    }
  }

  private loadEntities() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`).subscribe({
      next: (res) => {
        const entities = res?.data ?? res ?? [];
        entities.forEach((e: any) => { this.entityMap[String(e.id)] = e.name; });
      },
      error: () => {}
    });
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    const allRecords: ComplaintRecord[] = [];

    const localDraft = this.getLocalDraft();
    if (localDraft) {
      allRecords.push(localDraft);
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data)) {
          allRecords.push(...data.map((c: any) => ({
            complaintId: c.complaintId || c.id,
            entityName: c.entityName || c.regulatedEntityName || '—',
            complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
            status: c.status || 'PENDING',
            closureClause: c.closureClause || '',
            closureDate: c.closureDate || c.complaintClosureDate || '',
            acknowledgementLetterUrl: c.acknowledgementLetterUrl || c.acknowledgementLetter || '',
            closureLetterUrl: c.closureLetterUrl || c.closureLetter || '',
          })));
        }
        this.finalizeLoad(allRecords);
      },
      error: () => {
        this.finalizeLoad(allRecords);
      }
    });

    this.complaintService.getDrafts(phone).subscribe({
      next: (drafts) => {
        const serverDrafts = drafts.map(d => ({
          complaintId: d.draftId,
          entityName: d.entityName || '—',
          complaintDate: d.updatedAt || '—',
          status: 'DRAFT',
          isDraft: true,
          draftId: d.draftId
        }));
        if (serverDrafts.length) {
          this.complaints.update(list => {
            const withoutLocal = list.filter(l => l.draftId !== 'local');
            const merged = [...serverDrafts.filter(sd => !withoutLocal.some(l => l.draftId === sd.draftId)), ...withoutLocal];
            merged.sort((a, b) => {
              if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
              if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
              return 0;
            });
            return merged;
          });
        }
      },
      error: () => {}
    });
  }

  private getLocalDraft(): ComplaintRecord | null {
    const saved = sessionStorage.getItem('cms_complaint_draft');
    if (!saved) return null;
    try {
      const draft = JSON.parse(saved);
      const entityName = draft.entityName
        || (draft.eligibilityAnswers?.['regulatedEntity'] ? (this.entityMap[draft.eligibilityAnswers['regulatedEntity']] || '—') : null)
        || '—';
      const savedAt = sessionStorage.getItem('cms_draft_saved_at') || new Date().toISOString();
      return {
        complaintId: 'DRAFT-LOCAL',
        entityName,
        complaintDate: savedAt,
        status: 'DRAFT',
        isDraft: true,
        draftId: 'local'
      };
    } catch {
      return null;
    }
  }

  private finalizeLoad(records: ComplaintRecord[]) {
    records.sort((a, b) => {
      if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
      if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
      return new Date(b.complaintDate).getTime() - new Date(a.complaintDate).getTime();
    });
    this.complaints.set(records);
    this.loading.set(false);
  }

  onStatusFilter(value: string) {
    this.selectedStatus = value;
    this.dt.filter(value, 'status', 'equals');
  }

  onDateFromChange(event: Event) {
    this.dateFrom = (event.target as HTMLInputElement).value;
    if (this.dateFrom) {
      this.dt.filter(this.dateFrom, 'complaintDate', 'dateAfter');
    } else {
      this.dt.filter('', 'complaintDate', 'contains');
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'CLOSED': case 'NON_MAINTAINABLE': case 'APPROVED': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': case 'REJECTED': return 'status-info-required';
      case 'PENDING': return 'status-pending';
      case 'DRAFT': return 'status-draft';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In-Progress';
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Complaint Closed';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'PENDING': return 'Pending';
      case 'DRAFT': return 'Draft';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
      default: return status.replace(/_/g, ' ');
    }
  }

  viewComplaint(record: ComplaintRecord) {
    if (record.isDraft) {
      this.resumeDraft(record);
    } else {
      this.router.navigate(['/public/complaint', record.complaintId]);
    }
  }

  fileAppeal(record: ComplaintRecord) {
    this.router.navigate(['/public/file-appeal', record.complaintId]);
  }

  shareFeedback(record: ComplaintRecord) {
    this.router.navigate(['/public/feedback', record.complaintId]);
  }

  resumeDraft(record: ComplaintRecord) {
    if (record.draftId === 'local') {
      this.router.navigate(['/public/file-complaint'], { queryParams: { resume: 'true' } });
    } else if (record.draftId) {
      this.router.navigate(['/public/file-complaint'], { queryParams: { draftId: record.draftId } });
    }
  }

  deleteDraft(record: ComplaintRecord) {
    if (!record.draftId) return;
    if (record.draftId === 'local') {
      sessionStorage.removeItem('cms_complaint_draft');
      sessionStorage.removeItem('cms_draft_saved_at');
      sessionStorage.removeItem('cms_draft_id');
      this.complaints.update(list => list.filter(c => c.draftId !== 'local'));
    } else {
      this.complaintService.deleteDraft(record.draftId).subscribe({
        next: () => {
          this.complaints.update(list => list.filter(c => c.draftId !== record.draftId));
        },
        error: () => {}
      });
    }
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return '—';
      return d.toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-');
    } catch {
      return dateStr;
    }
  }

  complaintTypes = [
    { icon: 'pi pi-building', label: 'All Commercial Banks' },
    { icon: 'pi pi-briefcase', label: 'Non-Banking Financial Companies' },
    { icon: 'pi pi-id-card', label: 'Credit Information Companies' },
    { icon: 'pi pi-credit-card', label: 'Payment System Participants' },
  ];

  schemeCards = [
    { icon: 'pi pi-indian-rupee', title: 'Reserve Bank - Integrated Ombudsman Scheme, 2026', hasDownload: true },
    { icon: 'pi pi-indian-rupee', title: 'Regulated entities not covered under Reserve Bank - Integrated Ombudsman Scheme, 2026', hasDownload: true },
    { icon: 'pi pi-map-marker', title: 'Address of Centralised Receipt and Processing Centre', hasDownload: false },
    { icon: 'pi pi-map-marker', title: 'Address of Consumer Education and Protection Cell', hasDownload: false },
  ];

  stats = [
    { value: '9,50,000', label: 'Complaints Received' },
    { value: '8,75,000', label: 'Complaints Handled' },
    { value: '96%', label: 'Satisfaction Rate' },
  ];

  educationCards = [
    { title: 'Basic Savings Bank...', subtitle: 'Basic Savings Bank Deposit Account BSBDA', image: 'assets/img3.jpg', footerIcon: 'pi pi-play-circle', action: 'WATCH' },
    { title: 'Customer Liability in...', subtitle: 'Customer Liability in Unauthorised Electronic Banking Transactions', image: 'assets/img3.jpg', footerIcon: 'pi pi-play-circle', action: 'WATCH' },
    { title: 'BE(A)WARE', subtitle: 'A booklet on modus operandi of financial fraudster', image: 'assets/img3.jpg', footerIcon: 'pi pi-book', action: 'READ' },
    { title: 'Customer Liability in...', subtitle: 'Basic Savings Bank Deposit Account BSBDA', image: 'assets/img3.jpg', footerIcon: 'pi pi-play-circle', action: 'WATCH' },
  ];

  faqs = [
    { question: 'Which types of complaints can I lodge through this website?', answer: 'You are advised to make a complaint relating to deficiency in banking services (related to your bank accounts, loans, credit cards etc.) to the Ombudsman under the Integrated Ombudsman Scheme, 2021. To download your complaint closure letter, please click Create Complaint Closure letter. Please note: Same complaint resolution process is followed in all methods of complaint filing including email and physical letters.', open: true },
    { question: 'What is the process of filing a complaint?', answer: 'First file a complaint with your bank. If unsatisfied with the response (or no response within 30 days), file with RBI Ombudsman through this portal.', open: false },
    { question: 'Why should I use my mobile number while filing a complaint?', answer: 'Your mobile number is used for OTP verification and to track your complaints. It ensures security and allows status updates.', open: false },
  ];

  toggleFaq(index: number) {
    this.faqs[index].open = !this.faqs[index].open;
  }

  scrollEducation(direction: 'left' | 'right') {
    const el = this.eduScroll?.nativeElement;
    if (el) {
      const scrollAmount = 300;
      el.scrollBy({ left: direction === 'right' ? scrollAmount : -scrollAmount, behavior: 'smooth' });
    }
  }
}
