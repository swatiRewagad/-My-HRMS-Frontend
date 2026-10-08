import { Component, OnInit, inject, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';
import { Table, TableModule } from 'primeng/table';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { Tooltip } from 'primeng/tooltip';
import { FilterService } from 'primeng/api';
import { ComplaintRecord } from '../models';
import { formatComplaintDate, parseComplaintDate, toDayKey } from '../../../utils/complaint-date.util';
import { FilingMethodPopupComponent, FilingMethodType } from '../filing-method-popup/filing-method-popup.component';

@Component({
  selector: 'app-public-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, TableModule, Select, DatePicker, Tooltip, FilingMethodPopupComponent],
  templateUrl: './public-home.component.html',
  styleUrl: './public-home.component.scss'
})
export class PublicHomeComponent implements OnInit {

  @ViewChild('dt') dt!: Table;
  @ViewChild('eduScroll') eduScroll!: ElementRef;

  private http = inject(HttpClient);
  private router = inject(Router);
  private complaintService = inject(ComplaintService);
  private filterService = inject(FilterService);
  authService = inject(PublicAuthService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  statusOptions = [
    { label: 'All', value: '' },
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Pending', value: 'PENDING' },
    { label: 'In-Progress', value: 'IN_PROGRESS' },
    { label: 'Complaint Closed', value: 'CLOSED' },
    { label: 'Rejected', value: 'REJECTED' },
    { label: 'Withdrawn', value: 'WITHDRAWN' },
    { label: 'Portal Rejection', value: 'PORTAL_REJECTION' }
  ];

  selectedStatus = '';
  dateFilter: Date | null = null;
  closureDateFilter: Date | null = null;

  // At the default 5 rows the table is left unconstrained so no vertical scrollbar
  // appears; larger page sizes get a capped height and scroll inside the card.
  readonly defaultRows = 5;
  rowsPerPage = signal(5);

  scrollHeight = computed(() => this.rowsPerPage() > this.defaultRows ? '440px' : undefined);

  showFilingPopup = false;
  filingMethodType: FilingMethodType = 'email';

  openFilingPopup(type: FilingMethodType) {
    this.filingMethodType = type;
    this.showFilingPopup = true;
  }

  onFileOnPortal() {
    this.router.navigate(['/public/file-complaint']);
  }

  ngOnInit() {
    this.registerDateEqualsFilter();
    if (this.authService.isAuthenticated()) {
      this.loadComplaints();
    } else {
      this.loading.set(false);
    }
  }

  private registerDateEqualsFilter() {
    this.filterService.register('complaintDateEquals', (value: any, filter: any): boolean => {
      if (!filter) return true;
      return toDayKey(parseComplaintDate(value)) === filter;
    });
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    if (!phone) {
      this.finalizeLoad([]);
      return;
    }

    // Both lists must resolve before the signal is set. Assigning them independently loses the
    // drafts whenever the drafts response arrives first, because finalizeLoad() overwrites the
    // whole signal — and drafts is consistently the faster of the two.
    forkJoin({
      submitted: this.http
        .get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`)
        .pipe(catchError(() => of(null))),
      drafts: this.complaintService.getDrafts(phone).pipe(catchError(() => of([])))
    }).subscribe(({ submitted, drafts }) => {
      const data = submitted?.data ?? submitted ?? [];
      const submittedRecords: ComplaintRecord[] = (Array.isArray(data) ? data : []).map((c: any) => ({
        complaintId: c.complaintId || c.id,
        entityName: c.entityName || c.regulatedEntityName || '—',
        complaintDate: c.complaintDate || c.createdAt || c.registeredDate || '—',
        status: c.status || 'PENDING',
        closureClause: c.closureClause || '',
        closureDate: c.closureDate || c.complaintClosureDate || '',
        appealable: c.appealable === true,
        // The API returns app-relative letter paths, but these are used as anchor hrefs — a plain
        // browser navigation, which does not go through the HttpClient base URL.
        acknowledgementLetterUrl: this.absoluteUrl(c.acknowledgementLetterUrl || c.acknowledgementLetter),
        closureLetterUrl: this.absoluteUrl(c.closureLetterUrl || c.closureLetter)
      }));

      const draftRecords: ComplaintRecord[] = drafts.map(d => ({
        complaintId: d.draftId,
        entityName: d.entityName || '—',
        complaintDate: d.updatedAt || '—',
        status: 'DRAFT',
        isDraft: true,
        draftId: d.draftId
      }));

      this.finalizeLoad([...draftRecords, ...submittedRecords]);
    });
  }

  private finalizeLoad(records: ComplaintRecord[]) {
    records.sort((a, b) => {
      if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
      if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
      return this.toTime(b.complaintDate) - this.toTime(a.complaintDate);
    });
    this.complaints.set(records);
    this.loading.set(false);
  }

  private toTime(value: string): number {
    return parseComplaintDate(value)?.getTime() ?? 0;
  }

  private absoluteUrl(path: string | undefined): string {
    if (!path) return '';
    return /^https?:\/\//.test(path) ? path : `${environment.apiBaseUrl}${path}`;
  }

  onStatusFilter(value: string) {
    this.selectedStatus = value;
    this.dt.filter(value, 'status', 'equals');
  }

  onDateFilterChange(date: Date | null) {
    this.dateFilter = date;
    this.dt.filter(toDayKey(date) || null, 'complaintDate', 'complaintDateEquals');
  }

  onClosureDateFilterChange(date: Date | null) {
    this.closureDateFilter = date;
    this.dt.filter(toDayKey(date) || null, 'closureDate', 'complaintDateEquals');
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'CLOSED': case 'NON_MAINTAINABLE': case 'PORTAL_REJECTION': case 'APPROVED': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': case 'REJECTED': return 'status-info-required';
      case 'PENDING': return 'status-pending';
      case 'DRAFT': return 'status-draft';
      case 'WITHDRAWN': return 'status-withdrawn';
      default: return 'status-pending';
    }
  }

  /** UST82: Portal Rejection is the actual status code for a Non-Maintainable closure (FR-G-013) — no complaint number was ever issued. */
  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In-Progress';
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Complaint Closed';
      case 'PORTAL_REJECTION': return 'Portal Rejection';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'PENDING': return 'Pending';
      case 'DRAFT': return 'Draft';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
      case 'WITHDRAWN': return 'Withdrawn';
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
    this.router.navigate(['/public/appeal', record.complaintId]);
  }

  /** UST111: appeal is only offered for a closure clause appealable by the complainant (15(1)(a)/(b)). */
  canFileAppeal(record: ComplaintRecord): boolean {
    return record.appealable === true;
  }

  shareFeedback(record: ComplaintRecord) {
    this.router.navigate(['/public/feedback', record.complaintId]);
  }

  withdrawComplaint(record: ComplaintRecord) {
    this.router.navigate(['/public/withdraw', record.complaintId]);
  }

  isWithdrawable(status: string): boolean {
    return status === 'PENDING' || status === 'IN_PROGRESS' || status === 'INFORMATION_REQUIRED';
  }

  resumeDraft(record: ComplaintRecord) {
    if (record.draftId) {
      this.router.navigate(['/public/file-complaint'], { queryParams: { draftId: record.draftId } });
    }
  }

  deleteDraft(record: ComplaintRecord) {
    if (!record.draftId) return;
    this.complaintService.deleteDraft(record.draftId).subscribe({
      next: () => {
        this.complaints.update(list => list.filter(c => c.draftId !== record.draftId));
      },
      error: () => {}
    });
  }

  formatDate(dateStr: string | undefined): string {
    return formatComplaintDate(dateStr);
  }

  complaintTypes = [
    { icon: 'pi pi-building', label: 'All Commercial Banks' },
    { icon: 'pi pi-briefcase', label: 'Non-Banking Financial Companies' },
    { icon: 'pi pi-id-card', label: 'Credit Information Companies' },
    { icon: 'pi pi-credit-card', label: 'Payment System Participants' },
  ];

  schemeCards = [
    { icon: 'pi pi-building-columns', title: 'Reserve Bank - Integrated Ombudsman Scheme, 2026', hasDownload: true, downloadUrl: 'assets/documents/Ombudsman_Scheme-2026.pdf' },
    { icon: 'pi pi-indian-rupee', title: 'Regulated Entities Not Covered Under Reserve Bank - Integrated Ombudsman Scheme, 2026', hasDownload: true, downloadUrl: 'assets/documents/Ombudsman_Scheme-2026.pdf' },
    { icon: 'pi pi-map-marker', title: 'Address of Centralised Receipt and Processing Centre', hasDownload: true, downloadUrl: 'assets/documents/CRPC_Address.pdf' },
    { icon: 'pi pi-map-marker', title: 'Address of Consumer Education and Protection Cell', hasDownload: true, downloadUrl: 'assets/documents/CEPC_Address_Protection-cell.pdf' },
  ];

  stats = [
    { value: '9,50,000', label: 'Complaints Received' },
    { value: '8,75,000', label: 'Complaints Handled' },
    { value: '96%', label: 'Satisfaction Rate' },
  ];

  educationCards = [
    { title: 'Basic Savings Bank...', subtitle: 'Basic Savings Bank Deposit Account BSBDA', image: 'assets/education/bsbda.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://youtu.be/hrqAnGrzxLQ' },
    { title: 'Customer Liability in...', subtitle: 'Customer Liability in Unauthorised Electronic Banking Transactions', image: 'assets/education/customer-liability.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://youtu.be/3XtvBgWyCCI' },
    { title: 'Safe Digital Banking...', subtitle: 'Safe Digital Banking - Badminton Court', image: 'assets/education/safe-digital-banking-badminton.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://youtu.be/RwgjbntJ9d0' },
    { title: 'Safe Digital Banking...', subtitle: 'Safe Digital Banking - Restaurant', image: 'assets/education/safe-digital-banking-restaurant.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://youtu.be/uf1wkzR4QMI' },
    { title: 'Facilities for Senior...', subtitle: 'Facilities for Senior Citizens', image: 'assets/education/senior-citizens.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://youtu.be/fMpGvIbIarM' },
    { title: 'Caution Against Fic...', subtitle: 'Caution Against Fictitious Mails - Husband and Wife', image: 'assets/education/fictitious-mails.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://www.youtube.com/watch?v=vFbyqJFXNoA' },
    { title: 'RBI Ombudsman', subtitle: 'RBI Ombudsman', image: 'assets/education/rbi-ombudsman.png', footerIcon: 'pi pi-play-circle', action: 'WATCH', link: 'https://www.youtube.com/watch?v=RqPe5jQaoFU' },
    { title: 'BE(A)WARE', subtitle: 'A booklet on modus operandi of financial fraudsters', image: 'assets/education/beaware-booklet.png', footerIcon: 'pi pi-book', action: 'READ', link: 'https://rbidocs.rbi.org.in/rdocs/content/pdfs/BEAWARE07032022.pdf' },
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
    const el = this.eduScroll?.nativeElement as HTMLElement | undefined;
    if (!el) return;
    const card = el.querySelector('.education-card') as HTMLElement | null;
    const gap = parseFloat(getComputedStyle(el).columnGap) || 20;
    const step = card ? card.offsetWidth + gap : 300;
    el.scrollBy({ left: direction === 'right' ? step : -step, behavior: 'smooth' });
  }
}
