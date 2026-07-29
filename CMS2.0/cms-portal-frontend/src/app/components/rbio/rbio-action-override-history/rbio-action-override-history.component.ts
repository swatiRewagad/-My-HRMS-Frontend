import { Component, Input, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RbioWorkflowService, ActionOverride } from '../../../services/rbio-workflow.service';

@Component({
  selector: 'app-rbio-action-override-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rbio-action-override-history.component.html',
  styleUrl: './rbio-action-override-history.component.scss'
})
export class RbioActionOverrideHistoryComponent implements OnInit {
  @Input() complaint: any = null;

  private workflowService = inject(RbioWorkflowService);

  overrides = signal<ActionOverride[]>([]);
  loading = signal(false);

  ngOnInit() {
    this.loadOverrides();
  }

  loadOverrides() {
    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    if (!complaintId) return;
    this.loading.set(true);
    this.workflowService.getActionOverrides(complaintId).subscribe({
      next: (data) => {
        this.overrides.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}
