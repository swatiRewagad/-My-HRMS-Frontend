import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { KeycloakService } from '../../services/keycloak.service';

@Component({
  selector: 'app-realm-select',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './realm-select.component.html',
  styleUrl: './realm-select.component.scss',
})
export class RealmSelectComponent implements OnInit {
  realms: any[] = [];
  selectedRealmName = '';
  step: 'realm' | 'login' = 'realm';
  username = '';
  password = '';
  loginError = '';
  loggingIn = false;

  constructor(
    private http: HttpClient,
    private router: Router,
    private keycloak: KeycloakService
  ) {}

  ngOnInit() {
    this.http.get<any[]>(`${environment.apiUrl}/realms`).subscribe({
      next: (realms) => this.realms = realms,
      error: () => {
        this.realms = [
          { name: 'ecm', displayName: 'ECM - Enterprise Content Management' },
          { name: 'rbi-cms', displayName: 'CMS - Complaint Management System' },
        ];
      }
    });
  }

  proceed() {
    const realm = this.realms.find(r => r.name === this.selectedRealmName);
    if (!realm) return;
    sessionStorage.setItem('ecm_selected_realm', JSON.stringify(realm));
    this.keycloak.switchRealm(realm.name);
    this.step = 'login';
    this.loginError = '';
  }

  goBackToRealm() {
    this.step = 'realm';
    this.username = '';
    this.password = '';
    this.loginError = '';
  }

  async login() {
    if (!this.username || !this.password) return;
    this.loggingIn = true;
    this.loginError = '';
    try {
      await this.keycloak.directLogin(this.username, this.password);
      window.location.href = '/';
    } catch (e: any) {
      this.loginError = e.message || 'Login failed';
    } finally {
      this.loggingIn = false;
    }
  }
}
