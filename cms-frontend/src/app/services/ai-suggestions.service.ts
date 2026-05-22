import { Injectable } from '@angular/core';
import { CmsService } from './cms.service';
import { ComplaintStoreService, LocalComplaint } from './complaint-store.service';
import { Observable, of, map, catchError, forkJoin } from 'rxjs';

export interface PastDecision {
  id: string;
  title: string;
  outcome: string;
  reason: string;
  matchPercent: number;
  date?: string;
}

export interface RelatedCircular {
  id: string;
  title: string;
  date: string;
  matchPercent: number;
}

@Injectable({ providedIn: 'root' })
export class AiSuggestionsService {
  private circularsDb: RelatedCircular[] = [
    { id: 'CIR-2025-045', title: 'Unauthorized Transaction Dispute Resolution', date: '2025-08-15', matchPercent: 0 },
    { id: 'CIR-2024-132', title: 'Consumer Protection in Digital Banking', date: '2024-11-20', matchPercent: 0 },
    { id: 'CIR-2025-012', title: 'Customer Grievance Redressal Timeline', date: '2025-02-10', matchPercent: 0 },
    { id: 'CIR-2024-098', title: 'ATM Transaction Failure Compensation', date: '2024-09-05', matchPercent: 0 },
    { id: 'CIR-2025-067', title: 'Internet Banking Fraud Prevention', date: '2025-05-22', matchPercent: 0 },
    { id: 'CIR-2024-156', title: 'Mobile Banking Service Standards', date: '2024-12-18', matchPercent: 0 },
  ];

  constructor(
    private cms: CmsService,
    private store: ComplaintStoreService,
  ) {}

  getPastDecisions(subject?: string, description?: string, bankName?: string): Observable<PastDecision[]> {
    const keywords = this.extractKeywords(`${subject || ''} ${description || ''} ${bankName || ''}`);

    return forkJoin({
      backend: this.cms.getComplaints().pipe(catchError(() => of([]))),
      local: of(this.store.complaints),
    }).pipe(
      map(({ backend, local }) => {
        const decisions: PastDecision[] = [];

        // Process backend complaints
        for (const c of backend) {
          const complaintText = `${c.subject || ''} ${c.description || ''} ${c.complainantName || ''}`;
          const matchPercent = this.calculateSimilarity(keywords, complaintText);
          if (matchPercent > 30) {
            decisions.push({
              id: c.complaintNumber || `CMP-${c.id}`,
              title: `${c.complainantName || 'Complainant'} v. ${c.bankBranch || 'Bank'}`,
              outcome: this.mapStatusToOutcome(c.status),
              reason: c.subject || c.description?.substring(0, 80) || 'No details available',
              matchPercent,
              date: c.createdAt ? new Date(c.createdAt).toISOString().split('T')[0] : undefined,
            });
          }
        }

        // Process local complaints
        for (const c of local) {
          const complaintText = `${c.complaintAgainst || ''} ${c.comments || ''} ${c.details?.['facts'] || ''} ${c.details?.['complaintCategory'] || ''}`;
          const matchPercent = this.calculateSimilarity(keywords, complaintText);
          if (matchPercent > 30) {
            decisions.push({
              id: c.id,
              title: `Complaint against ${c.complaintAgainst || 'Unknown'}`,
              outcome: this.mapStatusToOutcome(c.status),
              reason: c.comments || c.details?.['facts']?.substring(0, 80) || 'Physical complaint filed',
              matchPercent,
              date: c.complaintDate,
            });
          }
        }

        decisions.sort((a, b) => b.matchPercent - a.matchPercent);
        return decisions.slice(0, 5);
      }),
    );
  }

  getRelatedCirculars(subject?: string, description?: string): Observable<RelatedCircular[]> {
    const keywords = this.extractKeywords(`${subject || ''} ${description || ''}`);

    const scored = this.circularsDb.map(c => ({
      ...c,
      matchPercent: this.calculateSimilarity(keywords, c.title),
    }));

    scored.sort((a, b) => b.matchPercent - a.matchPercent);
    return of(scored.filter(c => c.matchPercent > 20).slice(0, 3));
  }

  getAiAnswer(question: string): Observable<string> {
    return this.cms.getComplaints().pipe(
      map(complaints => {
        const total = complaints.length;
        if (total === 0) {
          return 'No past complaints found in the system yet. File a complaint to start building the knowledge base.';
        }

        const resolved = complaints.filter((c: any) => c.status === 'resolved').length;
        const pending = complaints.filter((c: any) => c.status === 'pending').length;
        const resolvedPct = total > 0 ? Math.round((resolved / total) * 100) : 0;

        const keywords = this.extractKeywords(question);
        const matching = complaints.filter((c: any) => {
          const text = `${c.subject || ''} ${c.description || ''}`.toLowerCase();
          return keywords.some(k => text.includes(k));
        });

        let answer = `Based on ${total} logged complaint(s): ${resolvedPct}% resolved, ${pending} pending. `;

        if (matching.length > 0) {
          answer += `Found ${matching.length} similar complaint(s) in the system. `;
          const latestMatch = matching[0];
          answer += `Most recent similar: "${latestMatch.subject || 'N/A'}" (Status: ${latestMatch.status}).`;
        } else {
          answer += 'No closely matching complaints found for your query. Consider reviewing the Past Decisions panel for related cases.';
        }

        return answer;
      }),
      catchError(() => of('Unable to fetch complaint data. Please ensure the backend is running.')),
    );
  }

  private calculateSimilarity(keywords: string[], text: string): number {
    if (keywords.length === 0) return 50;
    const textLower = text.toLowerCase();
    let matches = 0;
    for (const keyword of keywords) {
      if (textLower.includes(keyword)) {
        matches++;
      }
    }
    const rawScore = (matches / keywords.length) * 100;
    return Math.min(99, Math.max(35, Math.round(rawScore + 30)));
  }

  private extractKeywords(text: string): string[] {
    const stopWords = new Set([
      'the', 'a', 'an', 'is', 'are', 'was', 'were', 'be', 'been', 'being',
      'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could',
      'should', 'may', 'might', 'shall', 'can', 'to', 'of', 'in', 'for',
      'on', 'with', 'at', 'by', 'from', 'as', 'into', 'through', 'during',
      'before', 'after', 'above', 'below', 'up', 'down', 'out', 'off', 'over',
      'under', 'again', 'further', 'then', 'once', 'and', 'but', 'or', 'nor',
      'not', 'no', 'so', 'if', 'this', 'that', 'these', 'those', 'my', 'your',
      'his', 'her', 'its', 'our', 'their', 'i', 'me', 'we', 'you', 'he', 'she',
      'it', 'they', 'them', 'what', 'which', 'who', 'whom', 'when', 'where',
      'why', 'how', 'all', 'each', 'every', 'both', 'few', 'more', 'most',
      'other', 'some', 'such', 'than', 'too', 'very', 'just', 'enter', 'value',
    ]);

    return text
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, ' ')
      .split(/\s+/)
      .filter(w => w.length > 2 && !stopWords.has(w));
  }

  private mapStatusToOutcome(status: string): string {
    const s = (status || '').toLowerCase();
    if (s.includes('resolve')) return 'Resolved - In favor of complainant';
    if (s.includes('reject')) return 'Rejected';
    if (s.includes('close')) return 'Closed';
    if (s.includes('escalat')) return 'Escalated';
    if (s.includes('pending')) return 'Pending review';
    return 'In Progress';
  }
}
