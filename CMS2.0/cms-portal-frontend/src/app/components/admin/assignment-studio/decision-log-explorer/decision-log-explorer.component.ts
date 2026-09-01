import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-decision-log-explorer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './decision-log-explorer.component.html',
  styleUrl: './decision-log-explorer.component.scss'
})
export class DecisionLogExplorerComponent implements OnInit {

  logs = signal<any[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  filters = {
    decisionPoint: '',
    caseRef: '',
    ruleCode: '',
    dryRunOnly: false
  };
  selectedLog = signal<any>(null);

  private baseUrl = '/cms-assignment/api/v1/assignment/decision-logs';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.search();
  }

  search() {
    this.loading.set(true);
    this.errorMessage.set('');
    const params: Record<string, string> = {};
    if (this.filters.decisionPoint) params['decisionPoint'] = this.filters.decisionPoint;
    if (this.filters.caseRef) params['caseRef'] = this.filters.caseRef;
    if (this.filters.ruleCode) params['ruleCode'] = this.filters.ruleCode;
    if (this.filters.dryRunOnly) params['dryRun'] = 'true';

    this.http.get<any[]>(this.baseUrl, { params }).subscribe({
      next: (results) => {
        this.logs.set(results);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load decision logs');
      }
    });
  }

  viewDetail(log: any) {
    this.selectedLog.set(this.selectedLog()?.id === log.id ? null : log);
  }
}
