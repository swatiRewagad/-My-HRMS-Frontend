import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Comment, CommentVisibility } from '../components/shared/comment-thread/comment-thread.types';

/**
 * Staff comment threads on a complaint.
 *
 * Separate from {@link ComplaintQueryService} despite the overlap in shape: that service serves the
 * RE↔RBI query workflow, whose endpoints are pinned by e2e/re-portal/query-threads.spec.ts against a
 * fixed request/response contract. Comments needed a visibility dimension those rows do not have, so
 * they are their own resource rather than a fourth message kind on an existing one.
 */
@Injectable({ providedIn: 'root' })
export class ComplaintCommentService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/api/v1/complaint-comments`;

  /**
   * No editWindowMinutes, unlike the internal-notes thread: a comment has no time lock, so the server
   * has nothing to tell the client about one. Whether the pencil shows is per-comment `editable`.
   */
  list(complaintNumber: string): Observable<{ comments: Comment[]; count: number }> {
    return this.http.get<{ comments: Comment[]; count: number }>(
      `${this.base}/complaint/${complaintNumber}`);
  }

  post(complaintNumber: string, payload: {
    body: string;
    visibility: CommentVisibility;
    restrictedToRoles?: string[];
    restrictedToUserIds?: string[];
    parentId?: number | null;
  }): Observable<{ commentId: number }> {
    return this.http.post<{ commentId: number }>(
      `${this.base}/complaint/${complaintNumber}`, payload);
  }

  edit(commentId: number, body: string): Observable<{ commentId: number }> {
    return this.http.put<{ commentId: number }>(`${this.base}/${commentId}`, { body });
  }
}
