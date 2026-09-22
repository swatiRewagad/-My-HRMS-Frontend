import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'success' | 'info' | 'warning' | 'error';

export interface Toast {
  id: number;
  severity: ToastSeverity;
  /** Translation key. Rendered through the translate pipe, never as raw text. */
  messageKey: string;
  params?: Record<string, string>;
  /** Shown only when the key is absent from the bundle, so a missing key is not a blank toast. */
  fallback?: string;
}

const DISMISS_MS: Record<ToastSeverity, number> = {
  success: 4000,
  info: 4000,
  warning: 6000,
  // Errors persist: a 4-second error is a defect report nobody reads.
  error: 0,
};

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  success(messageKey: string, params?: Record<string, string>, fallback?: string): void {
    this.push('success', messageKey, params, fallback);
  }

  info(messageKey: string, params?: Record<string, string>, fallback?: string): void {
    this.push('info', messageKey, params, fallback);
  }

  warning(messageKey: string, params?: Record<string, string>, fallback?: string): void {
    this.push('warning', messageKey, params, fallback);
  }

  error(messageKey: string, params?: Record<string, string>, fallback?: string): void {
    this.push('error', messageKey, params, fallback);
  }

  dismiss(id: number): void {
    this._toasts.update(list => list.filter(t => t.id !== id));
  }

  clear(): void {
    this._toasts.set([]);
  }

  private push(severity: ToastSeverity, messageKey: string, params?: Record<string, string>, fallback?: string): void {
    const id = this.nextId++;
    this._toasts.update(list => [...list, { id, severity, messageKey, params, fallback }]);
    const ttl = DISMISS_MS[severity];
    if (ttl > 0) {
      setTimeout(() => this.dismiss(id), ttl);
    }
  }
}
