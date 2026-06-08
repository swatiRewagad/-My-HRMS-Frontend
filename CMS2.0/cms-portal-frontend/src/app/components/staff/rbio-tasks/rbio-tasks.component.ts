import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface ComplaintTask {
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  entityName: string;
}

@Component({
  selector: 'app-rbio-tasks',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="tasks-container">
      <header class="topbar">
        <div class="topbar-left">
          <button class="back-btn" (click)="goBack()">&larr;</button>
          <h2>RBIO - {{ isHistoryView() ? 'Completed' : 'My Tasks' }}</h2>
        </div>
        <div class="topbar-right">
          @if (auth.currentUser(); as user) {
            <span class="role-badge">{{ user.roles[0] }}</span>
            <span class="user-name">{{ user.firstName }} {{ user.lastName }}</span>
          }
          <button class="logout-btn" (click)="logout()">Logout</button>
        </div>
      </header>

      <main class="content">
        <div class="stats-row">
          <div class="stat-card">
            <span class="stat-value">{{ tasks().length }}</span>
            <span class="stat-label">{{ isHistoryView() ? 'Total Completed' : 'Pending Tasks' }}</span>
          </div>
          <div class="stat-card warning">
            <span class="stat-value">{{ overdueTasks() }}</span>
            <span class="stat-label">{{ isHistoryView() ? 'Adjudicated' : 'Overdue' }}</span>
          </div>
          <div class="stat-card info">
            <span class="stat-value">{{ highPriorityTasks() }}</span>
            <span class="stat-label">High Priority</span>
          </div>
        </div>

        @if (loading()) {
          <div class="loading">Loading...</div>
        } @else if (tasks().length === 0) {
          <div class="empty-state">
            <div class="empty-icon">&#9745;</div>
            <h3>{{ isHistoryView() ? 'No completed tasks' : 'No pending tasks' }}</h3>
            <p>{{ isHistoryView() ? 'No complaints have been resolved yet.' : 'All complaints assigned to you have been processed.' }}</p>
          </div>
        } @else {
          <div class="tasks-list">
            @for (task of tasks(); track task.complaintNumber) {
              <div class="task-card" [class.overdue]="isOverdue(task)" (click)="openTask(task)">
                <div class="task-header">
                  <span class="complaint-num">{{ task.complaintNumber }}</span>
                  <span class="priority-badge" [class]="task.priority.toLowerCase()">{{ task.priority }}</span>
                </div>
                <h4 class="task-subject">{{ task.subject }}</h4>
                <div class="task-meta">
                  <span>Complainant: {{ task.complainantName }}</span>
                  <span>Entity: {{ task.entityName || 'N/A' }}</span>
                </div>
                <div class="task-footer">
                  <span class="status">{{ task.status }}</span>
                  <span class="due-date" [class.overdue-text]="isOverdue(task)">
                    SLA: {{ task.slaDueDate | date:'dd MMM yyyy' }}
                  </span>
                </div>
              </div>
            }
          </div>
        }
      </main>
    </div>
  `,
  styles: [`
    .tasks-container { min-height: 100vh; background: #f4f6f9; }
    .topbar {
      background: #1b5e20; color: white; padding: 12px 24px;
      display: flex; justify-content: space-between; align-items: center;
    }
    .topbar-left { display: flex; align-items: center; gap: 12px; }
    .topbar h2 { margin: 0; font-size: 18px; }
    .back-btn { background: none; border: none; color: white; font-size: 20px; cursor: pointer; padding: 4px 8px; }
    .topbar-right { display: flex; align-items: center; gap: 12px; font-size: 14px; }
    .role-badge { background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 4px; font-size: 11px; }
    .user-name { font-weight: 500; }
    .logout-btn { padding: 6px 14px; border: 1px solid rgba(255,255,255,0.5); border-radius: 4px; background: transparent; color: white; cursor: pointer; }
    .content { max-width: 900px; margin: 0 auto; padding: 24px 20px; }
    .stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 24px; }
    .stat-card { background: white; padding: 16px; border-radius: 8px; text-align: center; border-left: 4px solid #1b5e20; }
    .stat-card.warning { border-left-color: #e65100; }
    .stat-card.info { border-left-color: #1565c0; }
    .stat-value { display: block; font-size: 28px; font-weight: 700; color: #333; }
    .stat-label { font-size: 12px; color: #666; text-transform: uppercase; }
    .loading { text-align: center; padding: 40px; color: #666; }
    .empty-state { text-align: center; padding: 60px 20px; background: white; border-radius: 8px; }
    .empty-icon { font-size: 48px; margin-bottom: 12px; }
    .empty-state h3 { color: #333; margin: 0 0 8px; }
    .empty-state p { color: #666; margin: 0; }
    .tasks-list { display: flex; flex-direction: column; gap: 12px; }
    .task-card {
      background: white; padding: 16px 20px; border-radius: 8px; cursor: pointer;
      border: 1px solid #e0e0e0; transition: all 0.2s;
    }
    .task-card:hover { border-color: #1b5e20; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .task-card.overdue { border-left: 4px solid #d32f2f; }
    .task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .complaint-num { font-weight: 600; color: #1b5e20; font-size: 14px; }
    .priority-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; color: white; }
    .priority-badge.high { background: #d32f2f; }
    .priority-badge.medium { background: #f57c00; }
    .priority-badge.low { background: #388e3c; }
    .task-subject { margin: 0 0 8px; font-size: 15px; color: #333; }
    .task-meta { display: flex; gap: 20px; font-size: 13px; color: #666; margin-bottom: 10px; }
    .task-footer { display: flex; justify-content: space-between; align-items: center; }
    .status { font-size: 12px; padding: 2px 8px; background: #e3f2fd; color: #1565c0; border-radius: 4px; }
    .due-date { font-size: 12px; color: #666; }
    .overdue-text { color: #d32f2f; font-weight: 600; }
  `]
})
export class RbioTasksComponent implements OnInit {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);

  tasks = signal<ComplaintTask[]>([]);
  loading = signal(true);
  isHistoryView = signal(false);

  overdueTasks = signal(0);
  highPriorityTasks = signal(0);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    const path = this.route.snapshot.routeConfig?.path || '';
    this.isHistoryView.set(path.includes('history'));
    this.loadTasks();
  }

  private loadTasks() {
    this.loading.set(true);
    const role = this.auth.getRoles().find(r => r.startsWith('RBIO_')) || '';

    const url = this.isHistoryView()
      ? `${environment.apiBaseUrl}/api/v1/workflow/rbio/completed`
      : `${environment.apiBaseUrl}/api/v1/workflow/rbio/tasks?role=${role}`;

    this.http.get<any>(url)
      .subscribe({
        next: (res) => {
          const taskList = res?.data || [];
          this.tasks.set(taskList);
          this.overdueTasks.set(taskList.filter((t: ComplaintTask) => this.isOverdue(t)).length);
          this.highPriorityTasks.set(taskList.filter((t: ComplaintTask) => t.priority?.toLowerCase() === 'high').length);
          this.loading.set(false);
        },
        error: () => {
          this.tasks.set([]);
          this.loading.set(false);
        }
      });
  }

  isOverdue(task: ComplaintTask): boolean {
    if (!task.slaDueDate) return false;
    return new Date(task.slaDueDate) < new Date();
  }

  openTask(task: ComplaintTask) {
    this.router.navigate(['/staff/rbio/task', task.complaintNumber]);
  }

  goBack() {
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigate(['/staff/dashboard']);
    });
  }

  async logout() {
    await this.auth.logout();
  }
}
