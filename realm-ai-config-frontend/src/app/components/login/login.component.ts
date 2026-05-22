import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { Realm } from '../../models/realm.model';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly configuredRealms = this.auth.getConfiguredRealms();
  readonly selectedRealmId = signal('');
  readonly selectedUserName = signal('');
  readonly password = signal('');
  readonly showPassword = signal(false);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly realmDropdownOpen = signal(false);

  readonly selectedRealm = computed<Realm | null>(() => {
    const id = this.selectedRealmId();
    return this.configuredRealms.find(r => r.id === id) ?? null;
  });

  readonly realmUsers = computed(() => {
    const id = this.selectedRealmId();
    return id ? this.auth.getRealmUsers(id) : [];
  });

  selectRealm(realmId: string): void {
    this.selectedRealmId.set(realmId);
    this.selectedUserName.set('');
    this.password.set('');
    this.error.set('');
    this.realmDropdownOpen.set(false);
  }

  login(): void {
    this.error.set('');
    const realmId = this.selectedRealmId();
    const userName = this.selectedUserName();
    const pwd = this.password();

    if (!realmId) { this.error.set('Please select a realm'); return; }
    if (!userName) { this.error.set('Please select a user'); return; }
    if (!pwd) { this.error.set('Please enter your password'); return; }

    this.loading.set(true);
    setTimeout(() => {
      const result = this.auth.login(realmId, userName, pwd);
      this.loading.set(false);
      if (result.success) {
        this.router.navigate(['/portal']);
      } else {
        this.error.set(result.error ?? 'Login failed');
      }
    }, 600);
  }
}
