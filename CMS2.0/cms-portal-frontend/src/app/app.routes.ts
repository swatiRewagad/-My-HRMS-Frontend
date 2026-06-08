import { Routes } from '@angular/router';
import { publicAuthGuard } from './guards/public-auth.guard';
import { staffAuthGuard } from './guards/staff-auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/landing/landing.component').then(m => m.LandingComponent)
  },
  {
    path: 'file-complaint',
    loadComponent: () => import('./components/complaint-form/complaint-form.component').then(m => m.ComplaintFormComponent)
  },
  {
    path: 'track',
    loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent)
  },
  {
    path: 'track/:id',
    loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent)
  },
  {
    path: 'search',
    loadComponent: () => import('./components/search/search.component').then(m => m.SearchComponent)
  },
  {
    path: 'email-syndication',
    loadComponent: () => import('./components/email-syndication/email-queue/email-queue.component').then(m => m.EmailQueueComponent)
  },
  {
    path: 'email-syndication/draft/:draftId',
    loadComponent: () => import('./components/email-syndication/draft-detail/draft-detail.component').then(m => m.DraftDetailComponent)
  },
  {
    path: 'email-syndication/ignore-list',
    loadComponent: () => import('./components/email-syndication/ignore-list/ignore-list.component').then(m => m.IgnoreListComponent)
  },
  {
    path: 'email-syndication/deo-management',
    loadComponent: () => import('./components/email-syndication/deo-management/deo-management.component').then(m => m.DeoManagementComponent)
  },
  {
    path: 'email-syndication/simulator',
    loadComponent: () => import('./components/email-syndication/email-simulator/email-simulator.component').then(m => m.EmailSimulatorComponent)
  },
  {
    path: 'admin/dashboard',
    loadComponent: () => import('./components/admin/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent)
  },
  {
    path: 'admin/rules',
    loadComponent: () => import('./components/admin/rules-management/rules-management.component').then(m => m.RulesManagementComponent)
  },
  {
    path: 'admin/rules/new',
    loadComponent: () => import('./components/admin/rule-editor/rule-editor.component').then(m => m.RuleEditorComponent)
  },
  {
    path: 'admin/rules/edit/:id',
    loadComponent: () => import('./components/admin/rule-editor/rule-editor.component').then(m => m.RuleEditorComponent)
  },
  {
    path: 'admin/rules/test',
    loadComponent: () => import('./components/admin/rule-tester/rule-tester.component').then(m => m.RuleTesterComponent)
  },
  {
    path: 'officer',
    loadComponent: () => import('./components/officer/officer-dashboard/officer-dashboard.component').then(m => m.OfficerDashboardComponent)
  },
  {
    path: 'officer/complaint/:id',
    loadComponent: () => import('./components/officer/complaint-action/complaint-action.component').then(m => m.ComplaintActionComponent)
  },
  // ── Staff Portal (Keycloak SSO) ──
  {
    path: 'staff/login',
    loadComponent: () => import('./components/staff/staff-login/staff-login.component').then(m => m.StaffLoginComponent)
  },
  {
    path: 'staff/dashboard',
    canActivate: [staffAuthGuard],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('./components/staff/staff-dashboard/staff-dashboard.component').then(m => m.StaffDashboardComponent)
  },
  {
    path: 'staff/unauthorized',
    loadComponent: () => import('./components/staff/staff-unauthorized/staff-unauthorized.component').then(m => m.StaffUnauthorizedComponent)
  },
  {
    path: 'staff/rbio/tasks',
    canActivate: [staffAuthGuard],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('./components/staff/rbio-tasks/rbio-tasks.component').then(m => m.RbioTasksComponent)
  },
  {
    path: 'staff/rbio/task/:id',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/task-action/task-action.component').then(m => m.TaskActionComponent)
  },
  {
    path: 'staff/rbio/history',
    canActivate: [staffAuthGuard],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('./components/staff/rbio-tasks/rbio-tasks.component').then(m => m.RbioTasksComponent)
  },
  {
    path: 'staff/rbio/escalations',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/rbio-tasks/rbio-tasks.component').then(m => m.RbioTasksComponent)
  },
  {
    path: 'staff/cepc/tasks',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/cepc-tasks/cepc-tasks.component').then(m => m.CepcTasksComponent)
  },
  {
    path: 'staff/cepc/task/:id',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/task-action/task-action.component').then(m => m.TaskActionComponent)
  },
  {
    path: 'staff/cepc/history',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/cepc-tasks/cepc-tasks.component').then(m => m.CepcTasksComponent)
  },
  {
    path: 'staff/cepc/escalations',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/staff/cepc-tasks/cepc-tasks.component').then(m => m.CepcTasksComponent)
  },
  // ── CRPC (DEO / Reviewer) ──
  {
    path: 'crpc/login',
    loadComponent: () => import('./components/crpc/crpc-login/crpc-login.component').then(m => m.CrpcLoginComponent)
  },
  {
    path: 'crpc/home',
    loadComponent: () => import('./components/crpc/deo-home/deo-home.component').then(m => m.DeoHomeComponent)
  },
  {
    path: 'crpc/physical-letter',
    loadComponent: () => import('./components/crpc/physical-letter/physical-letter.component').then(m => m.PhysicalLetterComponent)
  },
  {
    path: 'crpc/draft/:id',
    loadComponent: () => import('./components/crpc/draft-assessment/draft-assessment.component').then(m => m.DraftAssessmentComponent)
  },
  {
    path: 'crpc/reviewer-management',
    loadComponent: () => import('./components/crpc/reviewer-management/reviewer-management.component').then(m => m.ReviewerManagementComponent)
  },
  {
    path: 'crpc/reviewer',
    loadComponent: () => import('./components/crpc/reviewer-home/reviewer-home.component').then(m => m.ReviewerHomeComponent)
  },
  {
    path: 'crpc/reviewer/draft/:id',
    loadComponent: () => import('./components/crpc/reviewer-assessment/reviewer-assessment.component').then(m => m.ReviewerAssessmentComponent)
  },
  // ── Public-facing Complaint Portal ──
  {
    path: 'public',
    loadComponent: () => import('./components/public/public-layout/public-layout.component').then(m => m.PublicLayoutComponent),
    children: [
      { path: '', loadComponent: () => import('./components/public/public-home/public-home.component').then(m => m.PublicHomeComponent) },
      { path: 'login', loadComponent: () => import('./components/public/public-login/public-login.component').then(m => m.PublicLoginComponent) },
      { path: 'track', loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent) },
      { path: 'track/:id', loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent) },
      // Protected routes — require active session (NFR-005: 15-minute session)
      { path: 'file-complaint', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/file-complaint/file-complaint.component').then(m => m.PublicFileComplaintComponent) },
      { path: 'withdraw', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/withdraw-complaint/withdraw-complaint.component').then(m => m.WithdrawComplaintComponent) },
      { path: 'withdraw/:id', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/withdraw-complaint/withdraw-complaint.component').then(m => m.WithdrawComplaintComponent) },
      { path: 'feedback', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/submit-feedback/submit-feedback.component').then(m => m.SubmitFeedbackComponent) },
      { path: 'appeal', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/file-appeal/file-appeal.component').then(m => m.FileAppealComponent) },
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
