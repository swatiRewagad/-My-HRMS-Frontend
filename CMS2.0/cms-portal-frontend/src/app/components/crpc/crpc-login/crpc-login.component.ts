import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crpc-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './crpc-login.component.html',
  styleUrl: './crpc-login.component.scss'
})
export class CrpcLoginComponent {

  private router = inject(Router);

  username = '';
  password = '';
  loginError = '';
  loading = signal(false);

  // Mock users for all CRPC roles (DEO names match email syndication pool)
  private mockUsers = [
    { username: 'amit', password: 'deo123', role: 'DEO', name: 'Amit Verma', id: 'deo_001' },
    { username: 'sneha', password: 'deo123', role: 'DEO', name: 'Sneha Patil', id: 'deo_002' },
    { username: 'ramesh', password: 'deo123', role: 'DEO', name: 'Ramesh Iyer', id: 'deo_003' },
    { username: 'reviewer1', password: 'rev123', role: 'REVIEWER', name: 'A.K. Singh', id: 'REV-001' },
    { username: 'reviewer2', password: 'rev123', role: 'REVIEWER', name: 'Priya Gupta', id: 'REV-002' },
    { username: 'crpchead', password: 'head123', role: 'CRPC_HEAD', name: 'Dr. S. Menon', id: 'HEAD-001' },
    { username: 'crpcadmin', password: 'admin123', role: 'CRPC_ADMIN', name: 'R. Sharma', id: 'ADM-001' },
    { username: 'incharge', password: 'ic123', role: 'CRPC_INCHARGE', name: 'M. Krishnan', id: 'IC-001' },
    { username: 'helpdesk', password: 'hd123', role: 'TOLL_FREE_HELPDESK', name: 'Support Agent', id: 'HD-001' },
  ];

  login() {
    this.loginError = '';
    if (!this.username.trim() || !this.password.trim()) {
      this.loginError = 'Please enter username and password.';
      return;
    }

    this.loading.set(true);

    setTimeout(() => {
      const user = this.mockUsers.find(
        u => u.username === this.username.trim() && u.password === this.password.trim()
      );

      if (!user) {
        this.loginError = 'Invalid credentials. Please try again.';
        this.loading.set(false);
        return;
      }

      // Store session
      sessionStorage.setItem('crpc_user', JSON.stringify({
        id: user.id,
        name: user.name,
        role: user.role,
        username: user.username,
        loginTime: new Date().toISOString()
      }));

      this.loading.set(false);

      // Route based on role
      switch (user.role) {
        case 'DEO':
          this.router.navigate(['/crpc/home']);
          break;
        case 'REVIEWER':
          this.router.navigate(['/crpc/reviewer']);
          break;
        case 'CRPC_HEAD':
        case 'CRPC_ADMIN':
        case 'CRPC_INCHARGE':
        case 'TOLL_FREE_HELPDESK':
          this.router.navigate(['/crpc/home']);
          break;
        default:
          this.router.navigate(['/crpc/home']);
      }
    }, 800);
  }
}
