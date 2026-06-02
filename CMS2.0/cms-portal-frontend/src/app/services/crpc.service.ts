import { Injectable, signal } from '@angular/core';
import { Observable, of, delay, tap } from 'rxjs';
import { ReviewerUser } from '../models/crpc.model';

@Injectable({ providedIn: 'root' })
export class CrpcService {

  private reviewers = signal<ReviewerUser[]>([
    { id: 'REV-001', displayName: 'Mr. A.K. Singh', email: 'ak.singh@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 12, region: 'NORTH', sortOrder: 1 },
    { id: 'REV-002', displayName: 'Ms. Priya Gupta', email: 'priya.gupta@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 8, region: 'WEST', sortOrder: 2 },
    { id: 'REV-003', displayName: 'Mr. R. Krishnamurthy', email: 'r.krishnamurthy@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 18, region: 'SOUTH', sortOrder: 3 },
    { id: 'REV-004', displayName: 'Ms. Neha Sharma', email: 'neha.sharma@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 22, region: 'NORTH', sortOrder: 4 },
    { id: 'REV-005', displayName: 'Mr. D. Menon', email: 'd.menon@rbi.org.in', isActive: false, isOnLeave: false, maxLoad: 25, currentLoad: 25, region: 'SOUTH', sortOrder: 5 },
  ]);

  getReviewers(): Observable<ReviewerUser[]> {
    return of(this.reviewers());
  }

  getActiveReviewers(): Observable<ReviewerUser[]> {
    return of(this.reviewers().filter(r => r.isActive && !r.isOnLeave));
  }

  addReviewer(reviewer: { id: string; displayName: string; email: string; maxLoad: number; region: string }): Observable<ReviewerUser> {
    const newReviewer: ReviewerUser = {
      ...reviewer,
      isActive: true,
      isOnLeave: false,
      currentLoad: 0,
      sortOrder: this.reviewers().length + 1,
    };
    this.reviewers.set([...this.reviewers(), newReviewer]);
    return of(newReviewer).pipe(delay(300));
  }

  updateReviewerThreshold(id: string, maxLoad: number): Observable<ReviewerUser | undefined> {
    this.reviewers.set(this.reviewers().map(r => r.id === id ? { ...r, maxLoad } : r));
    return of(this.reviewers().find(r => r.id === id)).pipe(delay(200));
  }

  toggleReviewerActive(id: string): Observable<ReviewerUser | undefined> {
    this.reviewers.set(this.reviewers().map(r => r.id === id ? { ...r, isActive: !r.isActive } : r));
    return of(this.reviewers().find(r => r.id === id)).pipe(delay(200));
  }

  toggleReviewerLeave(id: string): Observable<ReviewerUser | undefined> {
    this.reviewers.set(this.reviewers().map(r => r.id === id ? { ...r, isOnLeave: !r.isOnLeave } : r));
    return of(this.reviewers().find(r => r.id === id)).pipe(delay(200));
  }

  removeReviewer(id: string): Observable<void> {
    this.reviewers.set(this.reviewers().filter(r => r.id !== id));
    return of(undefined).pipe(delay(200));
  }

  resetRoundRobin(): Observable<void> {
    return of(undefined).pipe(delay(200));
  }
}
