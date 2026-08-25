import { Component, OnInit, inject, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { PublicAuthService } from '../../../services/public-auth.service';
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
  authService = inject(PublicAuthService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  statusOptions = [
    { label: 'All', value: '' },
    { label: 'In-Progress', value: 'IN_PROGRESS' },
    { label: 'Under Review', value: 'UNDER_REVIEW' },
    { label: 'Resolved', value: 'RESOLVED' },
    { label: 'Closed', value: 'CLOSED' },
    { label: 'Request Sent Back', value: 'REQUEST_SENT_BACK' },
    { label: 'Rejected', value: 'REJECTED' },
    { label: 'Pending', value: 'PENDING' }
  ];

  selectedStatus = '';
  dateFrom = '';

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.loadComplaints();
    } else {
      this.loading.set(false);
    }
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
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
      case 'REQUEST_SENT_BACK': return 'Request Sent Back';
      case 'REJECTED': return 'Rejected';
      case 'RESOLVED': return 'Resolved';
      case 'UNDER_REVIEW': return 'Under Review';
      case 'NON_MAINTAINABLE': return 'Closed';
      case 'CLOSED': return 'Closed';
      case 'PENDING': return 'Pending';
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
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-');
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
