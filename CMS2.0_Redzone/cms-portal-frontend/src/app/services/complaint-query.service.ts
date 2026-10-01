import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type QueryType =
  | 'CLARIFICATION'
  | 'EXTENSION_REQUEST'
  | 'DOCUMENT_REQUEST'
  | 'MEETING_REQUEST';

export interface QueryThread {
  id: number;
  queryType: QueryType;
  subject: string;
  direction: string;
  pendingWith: 'RE' | 'RBI' | 'NONE';
  status: 'OPEN' | 'RESOLVED';
  raisedByName: string;
  raisedBySide: 'RE' | 'RBI';
  raisedAt: string;
  awaitingMe: boolean;
  unread: boolean;
  proposedDeadline?: string | null;
  decision?: string | null;
  grantedDeadline?: string | null;
  decisionReason?: string | null;
  meetingPurpose?: string | null;
  meetingOutcome?: string | null;
  meetingDeclineReason?: string | null;
}

export interface QueryMessage {
  id: number;
  body: string;
  authorUserId: string;
  authorName: string;
  authorRole: string;
  authorSide: 'RE' | 'RBI';
  messageKind: 'MESSAGE' | 'SYSTEM';
  postedAt: string;
}

export interface ChecklistItem {
  id: number;
  label: string;
  description?: string | null;
  resolved: boolean;
  attachmentId?: number | null;
}

export interface MeetingSlot {
  id: number;
  proposedStart: string;
  proposedEnd?: string | null;
  slotStatus: 'PROPOSED' | 'ACCEPTED' | 'DECLINED' | 'SUPERSEDED';
  proposedBySide: 'RE' | 'RBI';
}

export interface ThreadDetail {
  success: boolean;
  messages: QueryMessage[];
  checklist: ChecklistItem[];
  slots: MeetingSlot[];
}

export interface InternalNote {
  id: number;
  body: string;
  authorUserId: string;
  authorName: string;
  createdAt: string;
  editCount: number;
  editable: boolean;
  editLockedAt: string;
}

export interface SlotInput {
  start: string;
  end?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ComplaintQueryService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/api/v1/complaint-queries`;

  /**
   * The entity code travels in a header rather than the body so the server can scope RE callers
   * without trusting a request field that a caller could swap for another entity's code.
   */
  private headers(entityCode?: string | null): { headers: HttpHeaders } {
    let headers = new HttpHeaders();
    if (entityCode) {
      headers = headers.set('X-Entity-Code', entityCode);
    }
    return { headers };
  }

  listThreads(complaintNumber: string, entityCode?: string | null): Observable<{ threads: QueryThread[] }> {
    return this.http.get<{ threads: QueryThread[] }>(
      `${this.base}/complaint/${complaintNumber}`, this.headers(entityCode));
  }

  getThread(queryId: number, entityCode?: string | null): Observable<ThreadDetail> {
    return this.http.get<ThreadDetail>(`${this.base}/${queryId}`, this.headers(entityCode));
  }

  raiseThread(complaintNumber: string, payload: Record<string, unknown>,
              entityCode?: string | null): Observable<{ queryId: number; thread: QueryThread }> {
    return this.http.post<{ queryId: number; thread: QueryThread }>(
      `${this.base}/complaint/${complaintNumber}`, payload, this.headers(entityCode));
  }

  reply(queryId: number, body: string, entityCode?: string | null): Observable<{ messageId: number }> {
    return this.http.post<{ messageId: number }>(
      `${this.base}/${queryId}/messages`, { body }, this.headers(entityCode));
  }

  decideExtension(queryId: number, approve: boolean, grantedDeadline?: string | null,
                  decisionReason?: string | null): Observable<{ decision: string }> {
    return this.http.post<{ decision: string }>(
      `${this.base}/${queryId}/extension-decision`,
      { approve, grantedDeadline, decisionReason }, this.headers(null));
  }

  resolveChecklistItem(itemId: number, attachmentId: number,
                       entityCode?: string | null): Observable<{ resolved: boolean }> {
    return this.http.post<{ resolved: boolean }>(
      `${this.base}/checklist-items/${itemId}/resolve`, { attachmentId }, this.headers(entityCode));
  }

  respondToMeeting(queryId: number, action: 'ACCEPT' | 'DECLINE' | 'COUNTER',
                   options: { acceptedSlotId?: number; counterSlots?: SlotInput[]; declineReason?: string },
                   entityCode?: string | null): Observable<{ meetingOutcome: string }> {
    return this.http.post<{ meetingOutcome: string }>(
      `${this.base}/${queryId}/meeting-response`, { action, ...options }, this.headers(entityCode));
  }

  awaitingMyResponse(entityCode?: string | null): Observable<{ count: number; threads: QueryThread[] }> {
    return this.http.get<{ count: number; threads: QueryThread[] }>(
      `${this.base}/awaiting-my-response`, this.headers(entityCode));
  }

  awaitingCount(entityCode?: string | null): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(
      `${this.base}/awaiting-my-response/count`, this.headers(entityCode));
  }

  markRead(queryId: number, entityCode?: string | null): Observable<unknown> {
    return this.http.post(`${this.base}/${queryId}/mark-read`, {}, this.headers(entityCode));
  }

  listInternalNotes(complaintNumber: string, entityCode?: string | null):
      Observable<{ notes: InternalNote[]; editWindowMinutes: number }> {
    return this.http.get<{ notes: InternalNote[]; editWindowMinutes: number }>(
      `${this.base}/complaint/${complaintNumber}/internal-notes`, this.headers(entityCode));
  }

  addInternalNote(complaintNumber: string, body: string,
                  entityCode?: string | null): Observable<{ noteId: number }> {
    return this.http.post<{ noteId: number }>(
      `${this.base}/complaint/${complaintNumber}/internal-notes`, { body }, this.headers(entityCode));
  }

  editInternalNote(noteId: number, body: string,
                   entityCode?: string | null): Observable<{ editCount: number }> {
    return this.http.put<{ editCount: number }>(
      `${this.base}/internal-notes/${noteId}`, { body }, this.headers(entityCode));
  }
}
