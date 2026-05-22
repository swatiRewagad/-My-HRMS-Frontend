import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { PlatformService } from '../../models/realm.model';

@Component({
  selector: 'app-realm-portal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './realm-portal.component.html',
  styleUrl: './realm-portal.component.scss',
})
export class RealmPortalComponent {
  readonly auth = inject(AuthService);
  private router = inject(Router);

  readonly user = this.auth.currentUser;
  readonly realm = this.auth.currentRealm;
  readonly config = this.auth.currentConfig;
  readonly services = this.auth.configuredServices;

  readonly serviceStats = computed(() => {
    const svcs = this.services();
    return {
      total: svcs.length,
      basic: svcs.filter(s => s.group === 'basic').length,
      ai: svcs.filter(s => s.group === 'ai').length,
    };
  });

  getServiceIcon(svc: PlatformService): string {
    const icons: Record<string, string> = {
      ecm: 'pi pi-folder-open',
      kavach: 'pi pi-shield',
      'holiday-calendar': 'pi pi-calendar',
      'dashboard-widgets': 'pi pi-chart-bar',
      notifications: 'pi pi-bell',
      'audit-trail': 'pi pi-list',
      mdms: 'pi pi-database',
      'job-scheduler': 'pi pi-clock',
      search: 'pi pi-search',
      'document-ai': 'pi pi-file',
      'workflow-engine': 'pi pi-sitemap',
      reporting: 'pi pi-chart-line',
      'payment-gateway': 'pi pi-credit-card',
      'form-builder': 'pi pi-pencil',
      'user-directory': 'pi pi-users',
      'anomaly-detection': 'pi pi-exclamation-triangle',
      'face-detection': 'pi pi-eye',
      recommendations: 'pi pi-star',
      'chatbot-nlp': 'pi pi-comments',
    };
    return icons[svc.id] ?? 'pi pi-cog';
  }

  getServiceColor(svc: PlatformService): string {
    return svc.group === 'ai' ? '#6941c6' : '#1a56db';
  }

  getServiceBg(svc: PlatformService): string {
    return svc.group === 'ai' ? '#f4f0ff' : '#eef4ff';
  }

  openService(svc: PlatformService): void {
    if (svc.id === 'ecm') {
      this.router.navigate(['/portal/ecm']);
    }
  }

  hasPortal(svc: PlatformService): boolean {
    return svc.id === 'ecm';
  }
}
