import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface UserSession {
  id?: number;
  name: string;
  phone: string;
  email?: string;
  address?: string;
  pincode?: string;
  state?: string;
  district?: string;
  newUser?: boolean;
  loggedInAt: string;
}

@Injectable({ providedIn: 'root' })
export class UserSessionService {
  private api = environment.apiUrl;
  private storageKey = 'cms_user_session';
  private draftKey = 'cms_draft_complaint';
  private sessionSubject = new BehaviorSubject<UserSession | null>(this.loadSession());

  session$ = this.sessionSubject.asObservable();

  constructor(private http: HttpClient) {}

  get session(): UserSession | null {
    return this.sessionSubject.value;
  }

  get isLoggedIn(): boolean {
    return this.sessionSubject.value !== null;
  }

  get userName(): string {
    return this.sessionSubject.value?.name || '';
  }

  get userPhone(): string {
    return this.sessionSubject.value?.phone || '';
  }

  loginWithOtp(phone: string): Observable<any> {
    return this.http.post<any>(`${this.api}/users/login`, { phone, otp: '000000' }).pipe(
      tap(profile => {
        const session: UserSession = {
          id: profile.id,
          name: profile.name || '',
          phone: profile.phone,
          email: profile.email || '',
          address: profile.address || '',
          pincode: profile.pincode || '',
          state: profile.state || '',
          district: profile.district || '',
          newUser: profile.newUser,
          loggedInAt: new Date().toISOString(),
        };
        this.sessionSubject.next(session);
        localStorage.setItem(this.storageKey, JSON.stringify(session));
      }),
      catchError(() => {
        this.loginLocal('', phone);
        return of({ name: '', phone, newUser: true });
      }),
    );
  }

  loginLocal(name: string, phone: string): void {
    const session: UserSession = {
      name,
      phone,
      loggedInAt: new Date().toISOString(),
    };
    this.sessionSubject.next(session);
    localStorage.setItem(this.storageKey, JSON.stringify(session));
  }

  logout(): void {
    this.sessionSubject.next(null);
    localStorage.removeItem(this.storageKey);
  }

  updateProfile(data: { name?: string; email?: string; address?: string; pincode?: string; state?: string; district?: string }): Observable<any> {
    const phone = this.userPhone;
    if (!phone) return of(null);

    return this.http.put<any>(`${this.api}/users/profile/${phone}`, data).pipe(
      tap(profile => {
        const current = this.session;
        if (current) {
          const updated: UserSession = {
            ...current,
            name: profile.name || current.name,
            email: profile.email || current.email,
            address: profile.address || current.address,
            pincode: profile.pincode || current.pincode,
            state: profile.state || current.state,
            district: profile.district || current.district,
          };
          this.sessionSubject.next(updated);
          localStorage.setItem(this.storageKey, JSON.stringify(updated));
        }
      }),
      catchError(() => {
        this.updateNameLocal(data.name || '');
        return of(null);
      }),
    );
  }

  updateNameLocal(name: string): void {
    const current = this.session;
    if (current) {
      const updated = { ...current, name };
      this.sessionSubject.next(updated);
      localStorage.setItem(this.storageKey, JSON.stringify(updated));
    }
  }

  saveDraft(formData: Record<string, any>): void {
    const phone = this.userPhone;
    if (!phone) return;
    const draft = { phone, savedAt: new Date().toISOString(), data: formData };
    localStorage.setItem(this.draftKey, JSON.stringify(draft));
  }

  getDraft(): Record<string, any> | null {
    try {
      const raw = localStorage.getItem(this.draftKey);
      if (!raw) return null;
      const draft = JSON.parse(raw);
      if (draft.phone === this.userPhone) {
        return draft.data;
      }
      return null;
    } catch {
      return null;
    }
  }

  clearDraft(): void {
    localStorage.removeItem(this.draftKey);
  }

  hasDraft(): boolean {
    return this.getDraft() !== null;
  }

  private loadSession(): UserSession | null {
    try {
      const data = localStorage.getItem(this.storageKey);
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  }
}
