import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { ReviewerUser } from '../models/crpc.model';
import { environment } from '../../environments/environment';

interface ApiResponse<T> {
  success: boolean;
  data: T;
}

interface KeycloakUserResponse {
  userId: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  maxLoad: number;
  currentLoad: number;
  region: string;
  sortOrder: number;
}

@Injectable({ providedIn: 'root' })
export class CrpcService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/keycloak`;

  getReviewers(): Observable<ReviewerUser[]> {
    return this.http.get<ApiResponse<KeycloakUserResponse[]>>(`${this.baseUrl}/users/reviewers`).pipe(
      map(res => (res.data || []).map(r => ({
        id: r.userId,
        displayName: r.displayName,
        email: r.email || '',
        isActive: r.isActive ?? true,
        isOnLeave: r.isOnLeave ?? false,
        maxLoad: r.maxLoad ?? 25,
        currentLoad: r.currentLoad ?? 0,
        region: r.region || '',
        sortOrder: r.sortOrder ?? 1,
      }))),
      catchError(() => of([]))
    );
  }

  getActiveReviewers(): Observable<ReviewerUser[]> {
    return this.getReviewers().pipe(
      map(reviewers => reviewers.filter(r => r.isActive && !r.isOnLeave))
    );
  }

  getDeos(): Observable<any[]> {
    return this.http.get<ApiResponse<any[]>>(`${this.baseUrl}/users/deos`).pipe(
      map(res => res.data || []),
      catchError(() => of([]))
    );
  }

  addReviewer(reviewer: { id: string; displayName: string; email: string; maxLoad: number; region: string }): Observable<ReviewerUser> {
    return of({
      ...reviewer,
      isActive: true,
      isOnLeave: false,
      currentLoad: 0,
      sortOrder: 1,
    });
  }

  updateReviewerThreshold(id: string, maxLoad: number): Observable<ReviewerUser | undefined> {
    return of(undefined);
  }

  toggleReviewerActive(id: string): Observable<ReviewerUser | undefined> {
    return of(undefined);
  }

  toggleReviewerLeave(id: string): Observable<ReviewerUser | undefined> {
    return of(undefined);
  }

  removeReviewer(id: string): Observable<void> {
    return of(undefined);
  }

  resetRoundRobin(): Observable<void> {
    return of(undefined);
  }
}
