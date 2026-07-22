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
    path: 'admin/extraction-rules',
    loadComponent: () => import('./components/admin/extraction-rules/extraction-rules.component').then(m => m.ExtractionRulesComponent)
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
    path: 'rbio',
    loadComponent: () => import('./components/rbio/rbio-home/rbio-home.component').then(m => m.RbioHomeComponent)
  },
  {
    path: 'rbio/complaint/:id',
    loadComponent: () => import('./components/rbio/rbio-complaint-detail/rbio-complaint-detail.component').then(m => m.RbioComplaintDetailComponent)
  },
  {
    path: 'rbio/supervisor-dashboard',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/rbio/rbio-supervisor-dashboard/rbio-supervisor-dashboard.component').then(m => m.RbioSupervisorDashboardComponent)
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
  // ── CEPC Module ──
  {
    path: 'cepc/dashboard',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/cepc/cepc-dashboard/cepc-dashboard.component').then(m => m.CepcDashboardComponent)
  },
  {
    path: 'cepc/complaint/:id',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/cepc/cepc-complaint-detail/cepc-complaint-detail.component').then(m => m.CepcComplaintDetailComponent)
  },
  {
    path: 'cepc/sla-dashboard',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/cepc/cepc-sla-dashboard/cepc-sla-dashboard.component').then(m => m.CepcSlaDashboardComponent)
  },
  // ── Report Builder & Senior Dashboard ──
  {
    path: 'staff/reports',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/report-builder/report-builder.component').then(m => m.ReportBuilderComponent)
  },
  {
    path: 'staff/senior-dashboard',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/senior-dashboard/senior-dashboard.component').then(m => m.SeniorDashboardComponent)
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
    path: 'crpc/ops-head',
    loadComponent: () => import('./components/crpc/ops-head/ops-head.component').then(m => m.OpsHeadComponent)
  },
  {
    path: 'crpc/reviewer',
    loadComponent: () => import('./components/crpc/reviewer-home/reviewer-home.component').then(m => m.ReviewerHomeComponent)
  },
  {
    path: 'crpc/reviewer/draft/:id',
    loadComponent: () => import('./components/crpc/reviewer-assessment/reviewer-assessment.component').then(m => m.ReviewerAssessmentComponent)
  },
  // ── CRPC Help Desk ──
  {
    path: 'crpc/help-desk',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/crpc/help-desk/help-desk.component').then(m => m.HelpDeskComponent)
  },
  // ── CRPC In-Charge ──
  {
    path: 'crpc/in-charge',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/crpc/in-charge-dashboard/in-charge-dashboard.component').then(m => m.InChargeDashboardComponent)
  },
  // ── Admin — Team Management ──
  {
    path: 'admin/team-management',
    loadComponent: () => import('./components/admin/team-management/team-management.component').then(m => m.TeamManagementComponent)
  },
  // ── Admin — Template Management ──
  {
    path: 'admin/comment-templates',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/admin/comment-templates/comment-templates.component').then(m => m.CommentTemplatesComponent)
  },
  {
    path: 'admin/communication-templates',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/admin/communication-templates/communication-templates.component').then(m => m.CommunicationTemplatesComponent)
  },
  // ── RE Portal (Regulated Entity) ──
  {
    path: 're-portal/login',
    loadComponent: () => import('./components/re-portal/re-login/re-login.component').then(m => m.ReLoginComponent)
  },
  {
    path: 're-portal',
    loadComponent: () => import('./components/re-portal/re-layout/re-layout.component').then(m => m.ReLayoutComponent),
    canActivate: [staffAuthGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./components/re-portal/re-dashboard/re-dashboard.component').then(m => m.ReDashboardComponent) },
      { path: 'complaints/:complaintNumber', loadComponent: () => import('./components/re-portal/re-complaint-detail/re-complaint-detail.component').then(m => m.ReComplaintDetailComponent) },
      { path: 'profile', loadComponent: () => import('./components/re-portal/re-profile/re-profile.component').then(m => m.ReProfileComponent) },
    ]
  },
  // ── Appellate Authority (AA) Module ──
  {
    path: 'aa/dashboard',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/aa/aa-dashboard/aa-dashboard.component').then(m => m.AaDashboardComponent)
  },
  {
    path: 'aa/appeal/:appealNumber',
    canActivate: [staffAuthGuard],
    loadComponent: () => import('./components/aa/aa-appeal-detail/aa-appeal-detail.component').then(m => m.AaAppealDetailComponent)
  },
  // ── Public-facing Complaint Portal ──
  {
    path: 'public',
    loadComponent: () => import('./components/public/public-layout/public-layout.component').then(m => m.PublicLayoutComponent),
    children: [
      { path: '', loadComponent: () => import('./components/public/public-home/public-home.component').then(m => m.PublicHomeComponent) },
      { path: 'login', loadComponent: () => import('./components/public/public-login/public-login.component').then(m => m.PublicLoginComponent) },
      { path: 'eligibility-wizard', loadComponent: () => import('./components/public/eligibility-wizard/eligibility-wizard.component').then(m => m.EligibilityWizardComponent) },
      { path: 'track', loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent) },
      { path: 'track/:id', loadComponent: () => import('./components/complaint-tracker/complaint-tracker.component').then(m => m.ComplaintTrackerComponent) },
      // Protected routes — require active session (NFR-005: 15-minute session)
      { path: 'file-complaint', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/file-complaint/file-complaint.component').then(m => m.PublicFileComplaintComponent) },
      { path: 'withdraw', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/withdraw-complaint/withdraw-complaint.component').then(m => m.WithdrawComplaintComponent) },
      { path: 'withdraw/:id', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/withdraw-complaint/withdraw-complaint.component').then(m => m.WithdrawComplaintComponent) },
      { path: 'feedback', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/submit-feedback/submit-feedback.component').then(m => m.SubmitFeedbackComponent) },
      { path: 'appeal', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/file-appeal/file-appeal.component').then(m => m.FileAppealComponent) },
      { path: 'history', canActivate: [publicAuthGuard], loadComponent: () => import('./components/public/complaint-history/complaint-history.component').then(m => m.ComplaintHistoryComponent) },
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
