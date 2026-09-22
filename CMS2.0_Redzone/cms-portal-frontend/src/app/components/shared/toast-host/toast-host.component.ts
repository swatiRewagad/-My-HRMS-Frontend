import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ToastService, ToastSeverity } from '../../../services/toast.service';

/**
 * The one notification surface for the whole app. Mounted once in the shell.
 *
 * Replaces four raw `alert()` calls and six per-component banner implementations. `alert()` also
 * blocks the Playwright event loop until dismissed, so those calls hung UI tests.
 */
@Component({
  selector: 'app-toast-host',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './toast-host.component.html',
  styleUrl: './toast-host.component.scss'
})
export class ToastHostComponent {
  private toastService = inject(ToastService);

  readonly toasts = this.toastService.toasts;

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  icon(severity: ToastSeverity): string {
    switch (severity) {
      case 'success': return 'pi pi-check-circle';
      case 'warning': return 'pi pi-exclamation-triangle';
      case 'error': return 'pi pi-times-circle';
      default: return 'pi pi-info-circle';
    }
  }

  /** Errors are assertive so a screen reader interrupts; the rest are polite. */
  liveness(severity: ToastSeverity): 'assertive' | 'polite' {
    return severity === 'error' ? 'assertive' : 'polite';
  }
}
