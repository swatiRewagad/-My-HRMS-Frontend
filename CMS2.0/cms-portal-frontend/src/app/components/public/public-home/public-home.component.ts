import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { PublicAuthService } from '../../../services/public-auth.service';
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
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './public-home.component.html',
  styleUrl: './public-home.component.scss'
})
export class PublicHomeComponent implements OnInit {

  private http = inject(HttpClient);
  authService = inject(PublicAuthService);

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
      case 'RESOLVED': case 'CLOSED': return 'status-resolved';
      case 'IN_PROGRESS': case 'UNDER_REVIEW': return 'status-progress';
      case 'REQUEST_SENT_BACK': return 'status-sent-back';
      case 'REJECTED': case 'NON_MAINTAINABLE': return 'status-rejected';
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

  stats = [
    { value: '9,50,000', label: 'Complaints Received' },
    { value: '8,75,000', label: 'Complaints Handled' },
    { value: '96%', label: 'Satisfaction Rate' },
  ];

  educationCards = [
    { title: 'Data Savings Bank', subtitle: 'Customer liability in...', badge: 'RBI/KARENE' },
    { title: 'Customer Liability in...', subtitle: 'Unauthorized Electronic...', badge: 'RBI/KARENE' },
    { title: '#BEAWARE', subtitle: 'Stay alert from fraud...', badge: '#BEAWARE' },
    { title: 'Customer Complaint', subtitle: 'How to file complaint...', badge: 'GUIDE' },
  ];

  faqs = [
    { question: 'Which types of complaints can I lodge through this website?', answer: 'You are advised to make a complaint relating to deficiency in banking services (related to your bank accounts, loans, credit cards etc.) to the Ombudsman under the Integrated Ombudsman Scheme, 2021. To download your complaint closure letter, please click Create Complaint Closure letter. Please note: Same complaint resolution process is followed in all methods of complaint filing including email and physical letters.', open: true },
    { question: 'What is the process of filing a complaint?', answer: 'First file a complaint with your bank. If unsatisfied with the response (or no response within 30 days), file with RBI Ombudsman through this portal.', open: false },
    { question: 'Why should I use my mobile number while filing a complaint?', answer: 'Your mobile number is used for OTP verification and to track your complaints. It ensures security and allows status updates.', open: false },
  ];

  toggleFaq(index: number) {
    this.faqs[index].open = !this.faqs[index].open;
  }
}
