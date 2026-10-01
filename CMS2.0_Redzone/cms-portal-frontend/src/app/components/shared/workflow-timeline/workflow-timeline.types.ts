/**
 * One row of a complaint's workflow audit trail.
 *
 * Mirrors TimelineEntryDto, which is the canonical server shape. Both `performedAt` and `timestamp`
 * are accepted because the four read paths disagree about the name and the DTO deliberately emits
 * both — a renderer that bound only one silently showed an empty trail against the other endpoints.
 */
export interface WorkflowTimelineEntry {
  action?: string | null;
  fromStatus?: string | null;
  toStatus?: string | null;
  remarks?: string | null;
  performedBy?: string | null;
  performedByRole?: string | null;
  performedAt?: string | null;
  timestamp?: string | null;
  /** MANUAL | AUTOMATIC. An automatic row is attributed to the system, not to a blank person. */
  eventSource?: string | null;
  fieldName?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  closureClause?: string | null;
  destinationOffice?: string | null;
}
