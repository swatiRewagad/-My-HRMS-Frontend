import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ServiceRegistryService, RegisterServicePayload } from '../../services/service-registry.service';

@Component({
  selector: 'app-register-service',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register-service.component.html',
  styleUrl: './register-service.component.scss',
})
export class RegisterServiceComponent {
  private svc = inject(ServiceRegistryService);
  private router = inject(Router);

  form: RegisterServicePayload = {
    name: '',
    baseUrl: '',
    slug: '',
    category: '',
    version: '',
    description: '',
    healthCheckEndpoint: '/health',
    authType: 'No Authentication',
    ownerName: '',
    ownerEmail: '',
    tags: '',
    status: 'Active',
  };

  urlValid = true;
  categories = ['AI & ML', 'Data & Analytics', 'Communication', 'Storage', 'Identity', 'Infrastructure', 'Authentication', 'Security'];
  authTypes = ['No Authentication', 'API Key', 'Bearer Token', 'OAuth 2.0'];
  statuses = ['Active', 'Inactive'];
  submitting = false;

  get autoSlug(): string {
    if (this.form.slug) return this.form.slug;
    return this.form.name.toLowerCase().replaceAll(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

  validateUrl() {
    try {
      if (this.form.baseUrl) new URL(this.form.baseUrl);
      this.urlValid = true;
    } catch {
      this.urlValid = this.form.baseUrl === '';
    }
  }

  onSubmit() {
    if (!this.form.name || !this.form.baseUrl || this.submitting) return;

    this.submitting = true;
    const payload: RegisterServicePayload = {
      ...this.form,
      slug: this.autoSlug,
    };

    this.svc.register(payload).subscribe({
      next: () => this.router.navigate(['/services']),
      error: () => this.submitting = false,
    });
  }

  cancel() {
    this.router.navigate(['/services']);
  }
}
