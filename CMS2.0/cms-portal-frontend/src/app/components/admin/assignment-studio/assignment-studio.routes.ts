import { Routes } from '@angular/router';

export const ASSIGNMENT_STUDIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./ruleset-list/ruleset-list.component').then(m => m.RulesetListComponent)
  },
  {
    path: ':rulesetId/versions/:versionId/edit',
    loadComponent: () =>
      import('./rule-editor/rule-editor.component').then(m => m.AssignmentRuleEditorComponent)
  },
  {
    path: ':rulesetId/versions/:versionA/diff/:versionB',
    loadComponent: () =>
      import('./version-diff/version-diff.component').then(m => m.VersionDiffComponent)
  },
  {
    path: 'approvals',
    loadComponent: () =>
      import('./approval-inbox/approval-inbox.component').then(m => m.ApprovalInboxComponent)
  },
  {
    path: 'simulator',
    loadComponent: () =>
      import('./simulator/simulator.component').then(m => m.SimulatorComponent)
  },
  {
    path: 'decision-logs',
    loadComponent: () =>
      import('./decision-log-explorer/decision-log-explorer.component').then(m => m.DecisionLogExplorerComponent)
  },
  {
    path: 'fallback-config',
    loadComponent: () =>
      import('./fallback-config/fallback-config.component').then(m => m.FallbackConfigComponent)
  }
];
