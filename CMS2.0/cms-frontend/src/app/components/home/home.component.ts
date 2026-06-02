import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CmsService } from '../../services/cms.service';
import { TranslateService } from '../../services/translate.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

interface ComplaintRecord {
  id: string;
  complaintAgainst: string;
  complaintDate: string;
  status: 'in_progress' | 'request_sent_back' | 'rejected' | 'resolved';
  statusLabel: string;
  comments: string;
  action: 'withdraw' | 'appeal' | 'act';
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslatePipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  isLoggedIn = false;

  complaints: ComplaintRecord[] = [
    { id: '1234', complaintAgainst: 'Adarsh Bank', complaintDate: '15-08-2026', status: 'in_progress', statusLabel: 'In Progress', comments: 'Missing details ple...', action: 'withdraw' },
    { id: '1234', complaintAgainst: 'Varada Bank', complaintDate: '03-02-2026', status: 'request_sent_back', statusLabel: 'Request Sent Back', comments: 'Missing details ple...', action: 'appeal' },
    { id: '1234', complaintAgainst: 'Kaveri Bank', complaintDate: '22-11-2025', status: 'rejected', statusLabel: 'Rejected', comments: 'Missing details ple...', action: 'act' },
  ];

  // Table filters
  filterIdSearch = '';
  filterAgainstSearch = '';
  filterDateSearch = '';
  filterStatusSearch = '';
  filterCommentsSearch = '';

  // Sort state
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // Landing page data
  complaintTypes: { icon: string; label: string; translationKey?: string }[] = [
    { icon: 'pi pi-building', label: 'All Commercial Banks', translationKey: 'ct.allBanks' },
    { icon: 'pi pi-briefcase', label: 'Non-Banking Financial Companies', translationKey: 'ct.nbfc' },
    { icon: 'pi pi-id-card', label: 'Credit Information Companies', translationKey: 'ct.creditInfo' },
    { icon: 'pi pi-credit-card', label: 'Payment System Participants', translationKey: 'ct.paymentSystem' },
  ];

  stats = [
    { valueKey: 'stats.receivedValue', labelKey: 'stats.received' },
    { valueKey: 'stats.handledValue', labelKey: 'stats.handled' },
    { valueKey: 'stats.resolvedValue', labelKey: 'stats.resolved' },
  ];

  pressReleases = [
    { day: '07', monthKey: 'press.month1', titleKey: 'press.title1', descKey: 'press.desc1' },
    { day: '08', monthKey: 'press.month2', titleKey: 'press.title2', descKey: 'press.desc2' },
  ];

  educationCards = [
    { titleKey: 'edu.card1Title', subtitleKey: 'edu.card1Subtitle', type: 'WATCH', image: 'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=400&h=500&fit=crop' },
    { titleKey: 'edu.card2Title', subtitleKey: 'edu.card2Subtitle', type: 'WATCH', image: 'https://images.unsplash.com/photo-1552664730-d307ca884978?w=400&h=500&fit=crop' },
    { titleKey: 'edu.card3Title', subtitleKey: 'edu.card3Subtitle', type: 'READ', image: 'https://images.unsplash.com/photo-1531482615713-2afd69097998?w=400&h=500&fit=crop' },
    { titleKey: 'edu.card4Title', subtitleKey: 'edu.card4Subtitle', type: 'WATCH', image: 'https://images.unsplash.com/photo-1560472355-536de3962603?w=400&h=500&fit=crop' },
  ];

  faqs = [
    { questionKey: 'faq.q1', answerKey: 'faq.a1', open: true },
    { questionKey: 'faq.q2', answerKey: 'faq.a2', open: false },
    { questionKey: 'faq.q3', answerKey: 'faq.a3', open: false },
  ];

  constructor(private cms: CmsService, public translate: TranslateService) {}

  ngOnInit() {
    this.cms.getComplaints().subscribe({
      next: (data: any[]) => {
        if (data && data.length > 0) {
          this.complaints = data.map(c => ({
            id: c.complaintNumber || c.id || '',
            complaintAgainst: c.bankName || c.complaintAgainst || '',
            complaintDate: c.createdAt ? new Date(c.createdAt).toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-') : '',
            status: this.mapStatus(c.status),
            statusLabel: c.status || '',
            comments: c.comments || c.description?.substring(0, 20) + '...' || '',
            action: this.mapAction(c.status),
          }));
        }
      },
      error: () => {},
    });
  }

  private mapStatus(status: string): ComplaintRecord['status'] {
    const s = (status || '').toLowerCase();
    if (s.includes('reject')) return 'rejected';
    if (s.includes('sent back') || s.includes('request')) return 'request_sent_back';
    if (s.includes('resolve')) return 'resolved';
    return 'in_progress';
  }

  private mapAction(status: string): ComplaintRecord['action'] {
    const s = (status || '').toLowerCase();
    if (s.includes('reject')) return 'act';
    if (s.includes('sent back') || s.includes('request')) return 'appeal';
    return 'withdraw';
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  toggleFaq(index: number) {
    this.faqs[index].open = !this.faqs[index].open;
  }
}
