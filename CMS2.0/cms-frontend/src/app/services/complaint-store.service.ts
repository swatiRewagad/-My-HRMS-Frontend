import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface LocalFileMeta {
  name: string;
  size: number;
  type: string;
}

export interface LocalComplaint {
  id: string;
  complaintAgainst: string;
  complaintDate: string;
  status: 'in_progress' | 'request_sent_back' | 'rejected' | 'resolved';
  statusLabel: string;
  comments: string;
  action: 'withdraw' | 'appeal' | 'act';
  details?: Record<string, string>;
  backendId?: number;
  files?: LocalFileMeta[];
}

@Injectable({ providedIn: 'root' })
export class ComplaintStoreService {
  private storageKey = 'cms_local_complaints';
  private complaintsSubject = new BehaviorSubject<LocalComplaint[]>(this.load());

  complaints$ = this.complaintsSubject.asObservable();

  get complaints(): LocalComplaint[] {
    return this.complaintsSubject.value;
  }

  add(complaint: LocalComplaint): void {
    const current = [...this.complaintsSubject.value, complaint];
    this.complaintsSubject.next(current);
    this.save(current);
  }

  getById(id: string): LocalComplaint | undefined {
    return this.complaintsSubject.value.find(c => c.id === id);
  }

  update(id: string, patch: Partial<LocalComplaint>): void {
    const current = this.complaintsSubject.value.map(c =>
      c.id === id ? { ...c, ...patch } : c
    );
    this.complaintsSubject.next(current);
    this.save(current);
  }

  private load(): LocalComplaint[] {
    try {
      const data = localStorage.getItem(this.storageKey);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  private save(complaints: LocalComplaint[]): void {
    localStorage.setItem(this.storageKey, JSON.stringify(complaints));
  }
}
