import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/layout/layout.component').then(m => m.LayoutComponent),
    children: [
      { path: '', loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent) },
      { path: 'file-complaint', loadComponent: () => import('./components/file-complaint/file-complaint.component').then(m => m.FileComplaintComponent) },
      { path: 'track-complaint', loadComponent: () => import('./components/track-complaint/track-complaint.component').then(m => m.TrackComplaintComponent) },
      { path: 'email-simulation', loadComponent: () => import('./components/email-simulation/email-simulation.component').then(m => m.EmailSimulationComponent) },
      { path: 'physical-complaint', loadComponent: () => import('./components/physical-complaint/physical-complaint.component').then(m => m.PhysicalComplaintComponent) },
    ],
  },
];
