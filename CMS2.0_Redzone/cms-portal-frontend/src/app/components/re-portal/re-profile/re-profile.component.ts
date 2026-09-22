import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface EntityProfile {
  name: string;
  type: string;
  city: string;
  state: string;
  registrationDate: string;
}

interface NodalOfficer {
  name: string;
  email: string;
  phone: string;
  designation: string;
}

interface PrincipalNodalOfficer {
  name: string;
  email: string;
  phone: string;
  designation: string;
}

@Component({
  selector: 'app-re-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './re-profile.component.html',
  styleUrl: './re-profile.component.scss'
})
export class ReProfileComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal('');
  saveError = signal('');

  entity = signal<EntityProfile>({ name: '', type: '', city: '', state: '', registrationDate: '' });
  nodalOfficer = signal<NodalOfficer>({ name: '', email: '', phone: '', designation: '' });
  pno = signal<PrincipalNodalOfficer>({ name: '', email: '', phone: '', designation: '' });

  // Editable nodal officer fields
  editName = '';
  editEmail = '';
  editPhone = '';
  editDesignation = '';
  isEditing = signal(false);

  ngOnInit() {
    this.loadProfile();
  }

  loadProfile() {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/profile`).subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.entity.set(data.entity || { name: '', type: '', city: '', state: '', registrationDate: '' });
        this.nodalOfficer.set(data.nodalOfficer || { name: '', email: '', phone: '', designation: '' });
        this.pno.set(data.principalNodalOfficer || { name: '', email: '', phone: '', designation: '' });
        this.resetEditFields();
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  startEditing() {
    this.isEditing.set(true);
    this.saveSuccess.set('');
    this.saveError.set('');
  }

  cancelEditing() {
    this.isEditing.set(false);
    this.resetEditFields();
    this.saveError.set('');
  }

  resetEditFields() {
    const no = this.nodalOfficer();
    this.editName = no.name;
    this.editEmail = no.email;
    this.editPhone = no.phone;
    this.editDesignation = no.designation;
  }

  saveNodalOfficer() {
    if (!this.editName.trim() || !this.editEmail.trim()) {
      this.saveError.set('Name and email are required.');
      return;
    }

    this.saving.set(true);
    this.saveError.set('');
    this.saveSuccess.set('');

    const payload = {
      name: this.editName.trim(),
      email: this.editEmail.trim(),
      phone: this.editPhone.trim(),
      designation: this.editDesignation.trim()
    };

    this.http.put<any>(`${environment.apiBaseUrl}/api/v1/re-portal/profile/nodal-officer`, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.isEditing.set(false);
        this.saveSuccess.set('Nodal officer details updated successfully.');
        this.nodalOfficer.set(payload);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(err.error?.message || 'Failed to update nodal officer details.');
      }
    });
  }
}
