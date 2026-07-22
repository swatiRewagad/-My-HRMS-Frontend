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

  roleGroups = [
    { value: 'CRPC_DEO', label: 'CRPC - DEO', keycloakRole: 'DEO' },
    { value: 'CRPC_REVIEWER', label: 'CRPC - Reviewer', keycloakRole: 'REVIEWER' },
    { value: 'RBIO_OFFICER', label: 'RBIO - Officer', keycloakRole: 'RBIO_OFFICER' },
    { value: 'RBIO_SUPERVISOR', label: 'RBIO - Supervisor', keycloakRole: 'RBIO_SUPERVISOR' },
    { value: 'CEPC_OFFICER', label: 'CEPC - Officer', keycloakRole: 'CEPC_OFFICER' },
    { value: 'CEPC_SUPERVISOR', label: 'CEPC - Supervisor', keycloakRole: 'CEPC_SUPERVISOR' },
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
    this.http.get<any>(`${environment.apiBaseUrl}/cms-workflow/api/v1/assignment/pool?roleGroup=${this.selectedRoleGroup()}`).subscribe({
      next: (res) => {
        this.officers.set(res.data || res || []);
        this.loading.set(false);
      },
      error: () => {
        this.officers.set(this.getMockData());
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
    this.http.put<any>(
      `${environment.apiBaseUrl}/cms-workflow/api/v1/assignment/pool/${officer.id}/leave?onLeave=${newStatus}`,
      {}
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
    this.http.put<any>(
      `${environment.apiBaseUrl}/cms-workflow/api/v1/assignment/pool/${officer.id}/deactivate`,
      {}
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
    this.http.post<any>(
      `${environment.apiBaseUrl}/cms-workflow/api/v1/assignment/pool`,
      this.newOfficer
    ).subscribe({
      next: (res) => {
        const added = res.data || res;
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
          maxWorkload: this.newOfficer.maxWorkload || 40
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

  private getMockData(): Officer[] {
    const group = this.selectedRoleGroup();
    if (group === 'CRPC_DEO') {
      return [
        { id: 1, userId: 'deo.raghav', displayName: 'Raghav Sharma', roleGroup: 'CRPC_DEO', regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 12, maxWorkload: 50 },
        { id: 2, userId: 'deo.priya', displayName: 'Priya Nair', roleGroup: 'CRPC_DEO', regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 8, maxWorkload: 50 },
        { id: 3, userId: 'deo.amit', displayName: 'Amit Kulkarni', roleGroup: 'CRPC_DEO', regionalOffice: 'DELHI', active: true, onLeave: true, currentWorkload: 15, maxWorkload: 50 },
        { id: 4, userId: 'deo.sunita', displayName: 'Sunita Desai', roleGroup: 'CRPC_DEO', regionalOffice: 'CHENNAI', active: true, onLeave: false, currentWorkload: 22, maxWorkload: 50 },
      ];
    } else if (group === 'CRPC_REVIEWER') {
      return [
        { id: 5, userId: 'rev.radhika', displayName: 'Radhika Rao', roleGroup: 'CRPC_REVIEWER', regionalOffice: 'MUMBAI', active: true, onLeave: true, currentWorkload: 10, maxWorkload: 30 },
        { id: 6, userId: 'rev.bhupinder', displayName: 'Bhupinder Singh', roleGroup: 'CRPC_REVIEWER', regionalOffice: 'DELHI', active: true, onLeave: false, currentWorkload: 5, maxWorkload: 30 },
        { id: 7, userId: 'rev.meena', displayName: 'Meena Iyer', roleGroup: 'CRPC_REVIEWER', regionalOffice: 'CHENNAI', active: true, onLeave: false, currentWorkload: 18, maxWorkload: 30 },
      ];
    } else if (group === 'RBIO_OFFICER') {
      return [
        { id: 8, userId: 'rbio.officer1', displayName: 'Vikram Mehta', roleGroup: 'RBIO_OFFICER', regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 20, maxWorkload: 40 },
        { id: 9, userId: 'rbio.officer2', displayName: 'Anjali Gupta', roleGroup: 'RBIO_OFFICER', regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 35, maxWorkload: 40 },
        { id: 10, userId: 'rbio.officer3', displayName: 'Suresh Kumar', roleGroup: 'RBIO_OFFICER', regionalOffice: 'DELHI', active: true, onLeave: false, currentWorkload: 15, maxWorkload: 40 },
        { id: 11, userId: 'rbio.officer4', displayName: 'Kavita Reddy', roleGroup: 'RBIO_OFFICER', regionalOffice: 'CHENNAI', active: true, onLeave: false, currentWorkload: 28, maxWorkload: 40 },
      ];
    }
    return [
      { id: 12, userId: 'cepc.officer1', displayName: 'Rohan Patil', roleGroup: group, regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 10, maxWorkload: 40 },
      { id: 13, userId: 'cepc.officer2', displayName: 'Deepa Krishnan', roleGroup: group, regionalOffice: 'MUMBAI', active: true, onLeave: false, currentWorkload: 25, maxWorkload: 40 },
    ];
  }
}
