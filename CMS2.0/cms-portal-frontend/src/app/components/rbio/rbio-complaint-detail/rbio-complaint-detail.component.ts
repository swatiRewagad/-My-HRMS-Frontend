import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface ComplaintDetail {
  complaintId: string;
  complaintNumber: string;
  complainantName: string;
  complainantEmail: string;
  subject: string;
  description: string;
  status: string;
  category: string;
  entityName: string;
  priority: string;
  modeOfReceipt: string;
  receiptDate: string;
  assignedTo: string;
  slaDueDate: string;
  slaBreachHours: number;
  comments: string;
  proposedAction: string;
  proposedClause: string;
  speakingOrder: string;
}

interface Comment {
  id: string;
  author: string;
  text: string;
  timestamp: string;
}

@Component({
  selector: 'app-rbio-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-complaint-detail.component.html',
  styleUrl: './rbio-complaint-detail.component.scss'
})
export class RbioComplaintDetailComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaint = signal<ComplaintDetail | null>(null);
  loading = signal(true);
  activeTab = signal('summary');
  showApprovalDropdown = signal(false);
  showAssignmentModal = signal(false);
  assignmentTarget = signal('');
  editMode = signal(false);

  // Assessment fields
  proposedAction = signal('');
  proposedClause = signal('');
  newComment = signal('');
  speakingOrder = signal('');
  comments = signal<Comment[]>([]);

  // Assignment modal fields
  assignmentType = signal('Automatic');
  assigneeName = signal('');
  systemicIssue = signal(false);
  crpcProposedAction = signal('');
  crpcProposedClause = signal('');

  loggedInUser: { id: string; name: string; role: string } | null = null;

  tabs = [
    { key: 'summary', label: 'Summary' },
    { key: 'nodal', label: 'Nodal Officer Record' },
    { key: 'conciliation', label: 'Conciliation' },
    { key: 'forward', label: 'Forward' },
    { key: 'email', label: 'Email Communication' },
    { key: 'final', label: 'Final Decision' },
  ];

  approvalOptions = [
    { key: 'reviewer', label: 'Send to RBIO Reviewer', nameLabel: 'Name of RBIO Reviewer' },
    { key: 'deputy', label: 'Send to RBIO Deputy Ombudsman', nameLabel: 'Name of RBIO Deputy Ombudsman' },
    { key: 'ombudsman', label: 'Send to RBIO Ombudsman', nameLabel: 'Name of RBIO Ombudsman' },
  ];

  selectedApprovalOption = signal<any>(null);

  ngOnInit() {
    const stored = sessionStorage.getItem('rbio_user');
    if (stored) this.loggedInUser = JSON.parse(stored);

    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadComplaint(id);
  }

  loadComplaint(id: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const d = res.data || res;
        this.complaint.set({
          complaintId: d.complaintId || d.complaintNumber || id,
          complaintNumber: d.complaintNumber || 'Not Assigned',
          complainantName: d.complainantName || '',
          complainantEmail: d.complainantEmail || '',
          subject: d.subject || '',
          description: d.description || '',
          status: d.status || 'DRAFT',
          category: d.category || 'General',
          entityName: d.entityName || '',
          priority: d.priority || 'MEDIUM',
          modeOfReceipt: d.filingType || d.modeOfReceipt || 'Email',
          receiptDate: d.createdAt || '',
          assignedTo: d.assignedTo || d.assignedTeam || '',
          slaDueDate: d.slaDueDate || '',
          slaBreachHours: this.calculateSlaHours(d.slaDueDate),
          comments: '',
          proposedAction: '',
          proposedClause: '',
          speakingOrder: '',
        });
        this.comments.set([
          { id: '1', author: 'Full Name RO DO', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers. These systems provide a centralized platform...', timestamp: '2 hrs ago' },
          { id: '2', author: 'Full Name', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers.', timestamp: '1 hrs ago' },
        ]);
        this.loading.set(false);
      },
      error: () => {
        this.complaint.set({
          complaintId: id,
          complaintNumber: 'N20223317000005',
          complainantName: 'Sagar Chauhan',
          complainantEmail: 'saurabh.pradhan@gmail.com',
          subject: 'Re: URGENT: Closed Loan Account Falsely Reported as Delinquent Under Same CIF – Kankarbagh Branch',
          description: 'Hold placed on my Canara Bank account due to a cyber crime investigation since 16 February. My account has been blocked, and I am unable to operate it or withdraw/credit funds. ...',
          status: 'NEW',
          category: 'Loans and Advances',
          entityName: 'ASNU FINVEST...',
          priority: 'MEDIUM',
          modeOfReceipt: 'Email',
          receiptDate: '27-04-2026',
          assignedTo: 'Bhupinder Singh',
          slaDueDate: '2026-06-15',
          slaBreachHours: 36,
          comments: '',
          proposedAction: '',
          proposedClause: '',
          speakingOrder: '',
        });
        this.comments.set([
          { id: '1', author: 'Full Name RO DO', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers. These systems provide a centralized platform...', timestamp: '2 hrs ago' },
          { id: '2', author: 'Full Name', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers.', timestamp: '1 hrs ago' },
        ]);
        this.loading.set(false);
      }
    });
  }

  private calculateSlaHours(slaDueDate: string): number {
    if (!slaDueDate) return 0;
    const due = new Date(slaDueDate);
    const now = new Date();
    return Math.max(0, Math.ceil((due.getTime() - now.getTime()) / (1000 * 60 * 60)));
  }

  goBack() {
    this.router.navigate(['/rbio']);
  }

  openApprovalOption(option: any) {
    this.selectedApprovalOption.set(option);
    this.assigneeName.set(option.nameLabel.replace('Name of ', ''));
    this.showApprovalDropdown.set(false);
    this.showAssignmentModal.set(true);
  }

  confirmAssignment() {
    const c = this.complaint();
    if (!c) return;
    this.showAssignmentModal.set(false);
    this.router.navigate(['/rbio']);
  }

  cancelAssignment() {
    this.showAssignmentModal.set(false);
    this.selectedApprovalOption.set(null);
  }

  addComment() {
    if (!this.newComment()) return;
    this.comments.update(list => [...list, {
      id: Date.now().toString(),
      author: this.loggedInUser?.name || 'Officer',
      text: this.newComment(),
      timestamp: 'Just now'
    }]);
    this.newComment.set('');
  }
}
