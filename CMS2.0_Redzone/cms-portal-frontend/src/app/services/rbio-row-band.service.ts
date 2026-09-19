import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

/** One colour band, as configured server-side. */
export interface RbioRowBand {
  code: string;
  colour: string;
  labelKey: string;
}

/** The state of one row, reduced to just what banding needs. */
export interface BandInput {
  status: string;
  workflowStage: string;
  milestone: string;
  createdAt: string;
  crpcReceivedAt: string;
}

/**
 * Derives the UST436 row colour band for a complaint, at render time.
 *
 * <h2>Why derived and never stored</h2>
 * A band is a view of the complaint's CURRENT state. Persisting it would create a second copy of that
 * state which goes stale the moment the complaint moves, and the grid would then show a colour that
 * contradicts the status in the same row. So there is no band column anywhere; this computes it.
 *
 * <h2>Why the threshold comes from the server</h2>
 * The CRPC-delay band triggers after a configured number of days. That number is a business rule RBI can
 * change, and a literal here would need a release to alter — and, worse, would drift from whatever the
 * backend's own escalations use. The defaults below apply only until the config call returns, and they
 * are the documented UST436 values rather than invented ones.
 */
@Injectable({ providedIn: 'root' })
export class RbioRowBandService {

  private http = inject(HttpClient);

  /** Days a CRPC complaint may sit before it bands blue. Overwritten by configuration on load. */
  private crpcDelayDays = signal(3);

  bands = signal<RbioRowBand[]>([
    { code: 'WHITE', colour: '#ffffff', labelKey: 'rbio.band.white' },
    { code: 'RED', colour: '#fee2e2', labelKey: 'rbio.band.red' },
    { code: 'GREEN', colour: '#dcfce7', labelKey: 'rbio.band.green' },
    { code: 'YELLOW', colour: '#fef9c3', labelKey: 'rbio.band.yellow' },
    { code: 'PINK', colour: '#fce7f3', labelKey: 'rbio.band.pink' },
    { code: 'BLUE', colour: '#dbeafe', labelKey: 'rbio.band.blue' },
  ]);

  constructor() {
    this.loadConfig();
  }

  /**
   * Loads colours and the delay threshold from SYSTEM_CONFIG.
   *
   * A failure leaves the documented defaults in place rather than blanking the grid. This is the one
   * place a fallback is right: a band is a visual aid, not a legal determination, so losing the
   * configured palette must not stop an officer working. Contrast the status filters, which fail closed
   * because they decide what a role may see.
   */
  private loadConfig() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/rbio/grid-config`).subscribe({
      next: (res) => {
        const data = res?.data ?? res;
        if (typeof data?.crpcDelayDays === 'number' && data.crpcDelayDays > 0) {
          this.crpcDelayDays.set(data.crpcDelayDays);
        }
        if (Array.isArray(data?.bands) && data.bands.length) {
          this.bands.set(data.bands);
        }
      },
      error: () => { /* documented defaults stand; see loadConfig doc */ }
    });
  }

  /**
   * The band for one row.
   *
   * Order matters and is not arbitrary. Withdrawn is tested FIRST because a withdrawn complaint is
   * withdrawn whatever stage it reached — a complaint withdrawn while out with the entity must not show
   * as "awaiting RE response", which would tell the officer to chase a case the citizen has dropped.
   * The CRPC-delay band is tested LAST because it is an overlay on an otherwise-new complaint: a
   * complaint that has moved on is described by where it is now, not by how long it waited.
   */
  bandFor(input: BandInput): string {
    const status = (input.status || '').toLowerCase();
    const stage = (input.workflowStage || '').toUpperCase();

    if (status === 'withdrawn') return 'PINK';

    // Out with the regulated entity, awaiting their reply.
    if (stage === 'SENT_TO_RE' || status === 'sent_to_re' || status === 'info_requested') return 'RED';

    // The entity has answered.
    if (stage === 'RE_RESPONDED' || status === 're_responded') return 'GREEN';

    // Returned to the Dealing Official for rework.
    if (stage === 'SENT_BACK_DO' || status === 'sent_back_do' || stage === 'SENT_BACK') return 'YELLOW';

    const isNew = status === 'new' || status === 'pending' || status === 'assigned' || status === '';
    if (isNew && this.exceededCrpcDelay(input)) return 'BLUE';

    return 'WHITE';
  }

  /**
   * Whether a CRPC-sourced complaint has waited longer than the configured window.
   *
   * Uses the CRPC receipt date when present and falls back to creation. Returns false when neither is
   * usable: an unparseable date must not be reported as delayed, because that would flag every complaint
   * with bad data as overdue and train officers to ignore the colour.
   */
  private exceededCrpcDelay(input: BandInput): boolean {
    const raw = input.crpcReceivedAt || input.createdAt;
    if (!raw) return false;
    const received = new Date(raw);
    if (isNaN(received.getTime())) return false;
    const days = Math.floor((Date.now() - received.getTime()) / (1000 * 60 * 60 * 24));
    return days > this.crpcDelayDays();
  }

  colourFor(code: string): string {
    return this.bands().find(b => b.code === code)?.colour ?? '#ffffff';
  }
}
