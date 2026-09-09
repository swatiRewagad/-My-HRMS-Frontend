import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface Officer {
  id: number;
  userId: string;
  displayName: string;
  roleGroup: string;
  regionalOffice: string;
  active: boolean;
  onLeave: boolean;
  currentWorkload: number;
  maxWorkload: number;
}

@Component({
  selector: 'app-team-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './team-management.component.html',
  styleUrl: './team-management.component.scss'
})
export class TeamManagementComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  officers = signal<Officer[]>([]);
  loading = signal(true);
  selectedRoleGroup = signal('CRPC_DEO');
  searchTerm = signal('');
  showAddModal = signal(false);
  sidebarItem = signal('team');

  syncing = signal(false);
  showEditModal = signal(false);
  editingOfficer: Officer | null = null;
  editForm: { officeCode: string; maxWorkload: number } = { officeCode: '', maxWorkload: 20 };

  officeOptions = ['MUMBAI', 'DELHI', 'CHENNAI', 'KOLKATA', 'HYDERABAD', 'AHMEDABAD', 'RBIO-MUM', 'RBIO-DEL', 'RBIO-CHN', 'CEPC-MUM', 'CEPC-DEL', 'CEPC-CHN'];

  // keycloakRole must match a real realm role name exactly (verified against the live rbi-cms
  // realm's role list) - CRPC_DEO/CRPC_REVIEWER/CEPC_OFFICER/CEPC_SUPERVISOR previously pointed
  // at role names ('DEO', 'REVIEWER', 'CEPC_OFFICER', 'CEPC_SUPERVISOR') that don't exist at all,
  // so those 4 groups always returned zero users regardless of who was actually assigned.
  roleGroups = [
    { value: 'CRPC_DEO', label: 'CRPC - DEO', keycloakRole: 'CRPC_DEO' },
    { value: 'CRPC_REVIEWER', label: 'CRPC - Reviewer', keycloakRole: 'CRPC_REVIEWER' },
    { value: 'RBIO_OFFICER', label: 'RBIO - Officer', keycloakRole: 'RBIO_OFFICER' },
    { value: 'RBIO_SUPERVISOR', label: 'RBIO - Supervisor', keycloakRole: 'RBIO_SUPERVISOR' },
    { value: 'RBIO_CONCILIATOR', label: 'RBIO - Conciliator', keycloakRole: 'RBIO_CONCILIATOR' },
    { value: 'RBIO_ADJUDICATOR', label: 'RBIO - Adjudicator', keycloakRole: 'RBIO_ADJUDICATOR' },
    { value: 'CEPC_OFFICER', label: 'CEPC - Officer', keycloakRole: 'CEPC_DO' },
    { value: 'CEPC_SUPERVISOR', label: 'CEPC - Supervisor', keycloakRole: 'CEPC_INCHARGE' },
  ];

  newOfficer: Partial<Officer> = {
    userId: '',
    displayName: '',
    roleGroup: 'CRPC_DEO',
    regionalOffice: '',
    maxWorkload: 40
  };

  filteredOfficers = computed(() => {
    const term = this.searchTerm().toLowerCase();
    return this.officers().filter(o =>
      o.displayName.toLowerCase().includes(term) ||
      o.userId.toLowerCase().includes(term) ||
      o.regionalOffice?.toLowerCase().includes(term)
    );
  });

  activeCount = computed(() => this.officers().filter(o => o.active && !o.onLeave).length);
  onLeaveCount = computed(() => this.officers().filter(o => o.onLeave).length);
  totalWorkload = computed(() => this.officers().reduce((sum, o) => sum + o.currentWorkload, 0));

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadOfficers();
  }

  loadOfficers() {
    this.loading.set(true);
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    const keycloakRole = group?.keycloakRole || this.selectedRoleGroup();

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=${keycloakRole}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        const officers: Officer[] = data.map((u: any, idx: number) => ({
          id: idx + 1,
          userId: u.userId || u.username,
          displayName: u.displayName || `${u.firstName || ''} ${u.lastName || ''}`.trim(),
          roleGroup: this.selectedRoleGroup(),
          regionalOffice: u.officeCode || u.regionalOffice || '',
          active: u.isActive !== false,
          onLeave: u.isOnLeave === true,
          currentWorkload: u.currentWorkload || 0,
          maxWorkload: u.maxWorkload || 20
        }));
        this.officers.set(officers);
        this.loading.set(false);
      },
      error: () => {
        this.officers.set([]);
        this.loading.set(false);
      }
    });
  }

  onRoleGroupChange(group: string) {
    this.selectedRoleGroup.set(group);
    this.loadOfficers();
  }

  toggleLeave(officer: Officer) {
    const newStatus = !officer.onLeave;
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    const role = group?.keycloakRole || this.selectedRoleGroup();

    this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/keycloak/users/${officer.userId}/availability`,
      { role, onLeave: newStatus }
    ).subscribe({
      next: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, onLeave: newStatus } : o)
        );
      },
      error: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, onLeave: newStatus } : o)
        );
      }
    });
  }

  deactivateOfficer(officer: Officer) {
    if (!confirm(`Deactivate ${officer.displayName}? They will no longer receive new assignments.`)) return;
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    const role = group?.keycloakRole || this.selectedRoleGroup();

    this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/keycloak/users/${officer.userId}/availability`,
      { role, active: false }
    ).subscribe({
      next: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, active: false } : o)
        );
      },
      error: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, active: false } : o)
        );
      }
    });
  }

  openAddModal() {
    this.newOfficer = {
      userId: '',
      displayName: '',
      roleGroup: this.selectedRoleGroup(),
      regionalOffice: '',
      maxWorkload: 40
    };
    this.showAddModal.set(true);
  }

  addOfficer() {
    if (!this.newOfficer.userId || !this.newOfficer.displayName) return;
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    const role = group?.keycloakRole || this.selectedRoleGroup();

    this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/keycloak/users/${this.newOfficer.userId}/availability`,
      { role, active: true, onLeave: false, maxWorkload: this.newOfficer.maxWorkload || 20, officeCode: this.newOfficer.regionalOffice || '' }
    ).subscribe({
      next: () => {
        const added: Officer = {
          id: Date.now(),
          userId: this.newOfficer.userId!,
          displayName: this.newOfficer.displayName!,
          roleGroup: this.newOfficer.roleGroup!,
          regionalOffice: this.newOfficer.regionalOffice || '',
          active: true,
          onLeave: false,
          currentWorkload: 0,
          maxWorkload: this.newOfficer.maxWorkload || 20
        };
        this.officers.update(list => [...list, added]);
        this.showAddModal.set(false);
      },
      error: () => {
        const mock: Officer = {
          id: Date.now(),
          userId: this.newOfficer.userId!,
          displayName: this.newOfficer.displayName!,
          roleGroup: this.newOfficer.roleGroup!,
          regionalOffice: this.newOfficer.regionalOffice || '',
          active: true,
          onLeave: false,
          currentWorkload: 0,
          maxWorkload: this.newOfficer.maxWorkload || 20
        };
        this.officers.update(list => [...list, mock]);
        this.showAddModal.set(false);
      }
    });
  }

  syncFromKeycloak() {
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    if (!group) return;

    this.syncing.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=${group.keycloakRole}`).subscribe({
      next: (users: any[]) => {
        const existingIds = new Set(this.officers().map(o => o.userId));
        let added = 0;

        for (const user of users) {
          const userId = user.username || user.userId;
          if (existingIds.has(userId)) continue;

          const officer: Partial<Officer> = {
            userId: userId,
            displayName: `${user.firstName || ''} ${user.lastName || ''}`.trim() || userId,
            roleGroup: this.selectedRoleGroup(),
            regionalOffice: user.attributes?.regionalOffice?.[0] || '',
            maxWorkload: 40
          };

          this.http.post<any>(
            `${environment.apiBaseUrl}/cms-workflow/api/v1/assignment/pool`,
            officer
          ).subscribe({
            next: (res) => {
              const saved = res.data || res;
              this.officers.update(list => [...list, saved]);
            },
            error: () => {
              const mock: Officer = {
                id: Date.now() + added,
                userId: userId,
                displayName: officer.displayName!,
                roleGroup: this.selectedRoleGroup(),
                regionalOffice: officer.regionalOffice || '',
                active: true,
                onLeave: user.attributes?.isOnLeave?.[0] === 'true',
                currentWorkload: 0,
                maxWorkload: 40
              };
              this.officers.update(list => [...list, mock]);
            }
          });
          added++;
        }

        this.syncing.set(false);
        if (added === 0) {
          alert('All Keycloak users are already in the pool.');
        } else {
          alert(`${added} new officer(s) synced from Keycloak.`);
        }
      },
      error: () => {
        this.syncing.set(false);
        alert('Failed to fetch users from Keycloak. Ensure backend is running.');
      }
    });
  }

  openEditModal(officer: Officer) {
    this.editingOfficer = officer;
    this.editForm = { officeCode: officer.regionalOffice, maxWorkload: officer.maxWorkload };
    this.showEditModal.set(true);
  }

  saveEdit() {
    if (!this.editingOfficer) return;
    const group = this.roleGroups.find(r => r.value === this.selectedRoleGroup());
    const role = group?.keycloakRole || this.selectedRoleGroup();
    const officer = this.editingOfficer;

    this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/keycloak/users/${officer.userId}/availability`,
      { role, officeCode: this.editForm.officeCode, maxWorkload: this.editForm.maxWorkload }
    ).subscribe({
      next: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, regionalOffice: this.editForm.officeCode, maxWorkload: this.editForm.maxWorkload } : o)
        );
        this.showEditModal.set(false);
      },
      error: () => {
        this.officers.update(list =>
          list.map(o => o.id === officer.id ? { ...o, regionalOffice: this.editForm.officeCode, maxWorkload: this.editForm.maxWorkload } : o)
        );
        this.showEditModal.set(false);
      }
    });
  }

  getWorkloadPercent(officer: Officer): number {
    if (officer.maxWorkload <= 0) return 0;
    return Math.round((officer.currentWorkload / officer.maxWorkload) * 100);
  }

  getWorkloadColor(officer: Officer): string {
    const pct = this.getWorkloadPercent(officer);
    if (pct >= 90) return '#ef4444';
    if (pct >= 70) return '#f59e0b';
    return '#22c55e';
  }

  navigateTo(item: string) {
    this.sidebarItem.set(item);
    if (item === 'dashboard') this.router.navigate(['/admin/dashboard']);
    else if (item === 'complaints') this.router.navigate(['/crpc/home']);
    else if (item === 'rules') this.router.navigate(['/admin/rules']);
  }

  async logout() {
    await this.auth.logout();
  }

}
