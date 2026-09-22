import { Component, OnInit, inject, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { PublicAuthService } from '../../../services/public-auth.service';
import { FaqService, Faq } from '../../../services/faq.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  comments: string;
}

@Component({
  selector: 'app-public-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './public-home.component.html',
  styleUrl: './public-home.component.scss'
})
export class PublicHomeComponent implements OnInit {

  private http = inject(HttpClient);
  private router = inject(Router);
  private faqService = inject(FaqService);
  authService = inject(PublicAuthService);

  @ViewChild('eduScroll') eduScroll!: ElementRef;

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  // Filters
  filterById = '';
  filterByEntity = '';
  filterByDate = '';
  filterByStatus = '';
  filterByComments = '';

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.loadComplaints();
    } else {
      this.loading.set(false);
    }
    this.loadFaqs();
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const payload = res?.data || res || [];
        const data = Array.isArray(payload) ? payload : (payload.content || []);
        this.complaints.set(Array.isArray(data) ? data.map((c: any) => ({
          complaintId: c.complaintId || c.id,
          entityName: c.entityName || '—',
          complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
          status: c.status || 'PENDING',
          comments: c.comments || c.description?.substring(0, 50) || '—'
        })) : []);
        this.loading.set(false);
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  get filteredComplaints(): ComplaintRecord[] {
    return this.complaints().filter(c =>
      (!this.filterById || c.complaintId.toLowerCase().includes(this.filterById.toLowerCase())) &&
      (!this.filterByEntity || c.entityName.toLowerCase().includes(this.filterByEntity.toLowerCase())) &&
      (!this.filterByDate || c.complaintDate.includes(this.filterByDate)) &&
      (!this.filterByStatus || c.status.toLowerCase().includes(this.filterByStatus.toLowerCase())) &&
      (!this.filterByComments || c.comments.toLowerCase().includes(this.filterByComments.toLowerCase()))
    );
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'RESOLVED': case 'CLOSED': case 'NON_MAINTAINABLE': return 'status-resolved';
      case 'IN_PROGRESS': case 'UNDER_REVIEW': return 'status-progress';
      case 'REQUEST_SENT_BACK': return 'status-sent-back';
      case 'REJECTED': return 'status-rejected';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In-Progress';
      case 'REQUEST_SENT_BACK': return 'Request Sent Back.';
      case 'REJECTED': return 'Rejected';
      case 'RESOLVED': return 'Resolved';
      case 'UNDER_REVIEW': return 'Under Review';
      case 'NON_MAINTAINABLE': return 'Closed';
      default: return status.replace(/_/g, ' ');
    }
  }

  getActionLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': case 'UNDER_REVIEW': case 'PENDING': return 'Withdraw';
      case 'REQUEST_SENT_BACK': return 'Appeal';
      case 'REJECTED': return 'Act';
      default: return 'View';
    }
  }

  onAction(complaint: ComplaintRecord) {
    const action = this.getActionLabel(complaint.status);
    switch (action) {
      case 'Withdraw':
        this.router.navigate(['/public/withdraw', complaint.complaintId]);
        break;
      case 'Appeal':
        this.router.navigate(['/public/appeal'], { queryParams: { id: complaint.complaintId } });
        break;
      default:
        this.router.navigate(['/public/track', complaint.complaintId]);
        break;
    }
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' });
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
    { icon: 'pi pi-indian-rupee', title: 'Reserve Bank - Integrated Ombudsman Scheme, 2021', hasDownload: true },
    { icon: 'pi pi-indian-rupee', title: 'Regulated entities not covered under Reserve Bank - Integrated Ombudsman Scheme, 2021', hasDownload: true },
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

  faqs = signal<(Faq & { open: boolean })[]>([]);

  private loadFaqs() {
    this.faqService.getAll().subscribe({
      next: (data) => {
        // Show max 5 FAQs on home page, first one open by default
        const limited = data.slice(0, 5).map((f, i) => ({ ...f, open: i === 0 }));
        this.faqs.set(limited);
      },
      error: () => {
        this.faqs.set([]);
      }
    });
  }

  toggleFaq(index: number) {
    this.faqs.update(list => list.map((f, i) => i === index ? { ...f, open: !f.open } : f));
  }

  scrollEducation(direction: 'left' | 'right') {
    const el = this.eduScroll?.nativeElement;
    if (el) {
      const scrollAmount = 300;
      el.scrollBy({ left: direction === 'right' ? scrollAmount : -scrollAmount, behavior: 'smooth' });
    }
  }
}
