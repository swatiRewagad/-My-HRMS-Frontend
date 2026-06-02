import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="app-header">
      <div class="header-content">
        <a routerLink="/" class="brand">
          <span class="brand-text">RBI CMS</span>
          <span class="brand-subtitle">Complaint Management System</span>
        </a>
        <nav class="header-nav">
          <a routerLink="/" class="nav-link">Home</a>
          <a routerLink="/track" class="nav-link">Track Complaint</a>
        </nav>
      </div>
    </header>
    <main>
      <router-outlet/>
    </main>
    <footer class="app-footer">
      <p>© Reserve Bank of India | Integrated Ombudsman Scheme 2021</p>
    </footer>
  `,
  styles: [`
    .app-header {
      background: #1a237e;
      padding: 0.8rem 2rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }
    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      max-width: 1200px;
      margin: 0 auto;
    }
    .brand {
      text-decoration: none;
      color: #fff;
      display: flex;
      flex-direction: column;
    }
    .brand-text { font-size: 1.4rem; font-weight: 700; }
    .brand-subtitle { font-size: 0.75rem; opacity: 0.8; }
    .header-nav { display: flex; gap: 1.5rem; }
    .nav-link {
      color: rgba(255,255,255,0.9);
      text-decoration: none;
      font-weight: 500;
      &:hover { color: #fff; }
    }
    main { min-height: calc(100vh - 120px); }
    .app-footer {
      background: #263238;
      color: rgba(255,255,255,0.7);
      text-align: center;
      padding: 1rem;
      font-size: 0.85rem;
    }
  `]
})
export class AppComponent {}
