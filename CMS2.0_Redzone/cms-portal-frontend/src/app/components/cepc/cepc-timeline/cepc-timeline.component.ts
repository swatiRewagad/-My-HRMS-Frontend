import { Component, Input, OnInit, OnDestroy, inject, signal } from '@angular/core';
import {
  ComplaintCorrespondenceService, TimelineEntry
} from '../../../services/complaint-correspondence.service';
import { WorkflowTimelineComponent } from '../../shared/workflow-timeline/workflow-timeline.component';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

/**
 * The CEPC complaint screen's audit trail.
 *
 * <p>WAS PERMANENTLY EMPTY. It fetched {@code /api/v1/complaints/{n}/timeline}, which does not exist —
 * the only timeline route is {@code /api/complaints/{id}/timeline} on the legacy controller, keyed by id
 * not number. The 404 was swallowed by {@code error: () => this.entries.set([])}, so every complaint in
 * the system rendered "No timeline entries yet." indistinguishably from one with no history.
 *
 * <p>Now reads {@code /{complaintNumber}/history}, the canonical feed, which additionally carries
 * {@code performedBy}/{@code performedByRole} — the acting officer that the old hardcoded shape had no
 * slot for.
 *
 * <p>Rendering is delegated to app-workflow-timeline so this screen cannot drift from the staff screen's
 * trail again; the English action-label map that used to live here was one of the ways it had.
 */
@Component({
  selector: 'app-cepc-timeline',
  standalone: true,
  imports: [WorkflowTimelineComponent, TranslateOrPipe],
  templateUrl: './cepc-timeline.component.html',
  styleUrl: './cepc-timeline.component.scss'
})
export class CepcTimelineComponent implements OnInit, OnDestroy {
  @Input() complaintNumber: string = '';

  private service = inject(ComplaintCorrespondenceService);
  private refreshInterval: any = null;

  entries = signal<TimelineEntry[]>([]);
  loading = signal(true);
  /** A failed load is reported, never rendered as an empty trail. */
  error = signal(false);

  ngOnInit() {
    if (this.complaintNumber) {
      this.loadTimeline();
      this.refreshInterval = setInterval(() => this.loadTimeline(), 30000);
    }
  }

  ngOnDestroy() {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  loadTimeline() {
    this.service.getHistory(this.complaintNumber).subscribe({
      next: rows => {
        this.entries.set(rows);
        this.error.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }
}
