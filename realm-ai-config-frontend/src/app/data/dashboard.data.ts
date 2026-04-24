import { REALMS, EXISTING_CONFIGS } from './realms.data';
import { ALL_SERVICES } from './services.data';

export interface DashboardStats {
  realmsConfigured: number;
  realmsActive: number;
  servicesRegistered: number;
  servicesActive: number;
  deprecatedServices: number;
  platformVersion: string;
  platformReleaseDate: string;
}

export interface RealmServiceBar {
  realmName: string;
  realmInitials: string;
  servicesConfigured: number;
  capacity: number;
  color: string;
}

export interface ServiceCategory {
  category: string;
  count: number;
  color: string;
}

export interface RecentConfig {
  realmName: string;
  realmInitials: string;
  version: string;
  deployType: string;
  status: 'Active' | 'Pending' | 'Draft';
  color: string;
}

export interface RegisteredService {
  name: string;
  slug: string;
  version: string;
  category: string;
  status: 'Active' | 'Inactive';
}

export interface ActivityItem {
  icon: string;
  iconColor: string;
  iconBg: string;
  title: string;
  subtitle: string;
  time: string;
  badge: string;
  badgeBg: string;
}

export const DASHBOARD_STATS: DashboardStats = {
  realmsConfigured: 4,
  realmsActive: 3,
  servicesRegistered: 9,
  servicesActive: 8,
  deprecatedServices: 1,
  platformVersion: 'v3.2.1',
  platformReleaseDate: 'Released Mar 2026',
};

export const REALM_SERVICE_BARS: RealmServiceBar[] = [
  { realmName: 'NGCB - GPX (G...', realmInitials: 'NGCB', servicesConfigured: 3, capacity: 5, color: '#1a56db' },
  { realmName: 'Central for F...', realmInitials: 'CFL', servicesConfigured: 2, capacity: 5, color: '#2563eb' },
  { realmName: 'Public Debt M...', realmInitials: 'PDM', servicesConfigured: 2, capacity: 5, color: '#7c3aed' },
  { realmName: 'Invoice Proce...', realmInitials: 'IPS', servicesConfigured: 1, capacity: 5, color: '#0891b2' },
];

export const TOP_SERVICES = ['Face / Object Detection', 'Anomaly Detection', 'Document Services', 'Allowed Storage'];

export const SERVICE_CATEGORIES: ServiceCategory[] = [
  { category: 'AI / ML', count: 7, color: '#2563eb' },
  { category: 'Authentication', count: 5, color: '#16a34a' },
  { category: 'Document', count: 4, color: '#ea580c' },
  { category: 'Infrastructure', count: 3, color: '#0891b2' },
  { category: 'Messaging', count: 3, color: '#d97706' },
  { category: 'Security', count: 2, color: '#dc2626' },
];

export const STATUS_DISTRIBUTION = {
  total: 8,
  active: 5,
  inactive: 2,
  deprecated: 1,
};

export const DEPLOYMENT_TYPES = {
  independent: 3,
  dependent: 1,
  modeBreakdown: {
    appDesigner: 1,
    nonAppDesigner: 3,
  },
};

export const RECENT_CONFIGS: RecentConfig[] = [
  { realmName: 'NGCB - GPX (Government Payment Exchange)', realmInitials: 'NGCB', version: 'v3.2.1', deployType: 'Independent', status: 'Active', color: '#1a56db' },
  { realmName: 'Central for Financial Literacy', realmInitials: 'CFL', version: 'v3.1.0', deployType: 'Dependent', status: 'Active', color: '#6941c6' },
  { realmName: 'Public Debt Management', realmInitials: 'PDM', version: 'v3.2.0', deployType: 'Independent', status: 'Pending', color: '#d97706' },
  { realmName: 'Invoice Processing System', realmInitials: 'IPS', version: 'v3.0.5', deployType: 'Independent', status: 'Draft', color: '#64748b' },
];

export const REGISTERED_SERVICES: RegisteredService[] = [
  { name: 'Auth Gateway', slug: 'auth-gateway', version: 'v4.1.1', category: 'Authentication', status: 'Active' },
  { name: 'Document Processor', slug: 'doc-processor', version: 'v5.6.8', category: 'Document', status: 'Active' },
  { name: 'Face Detect Engine', slug: 'face-detect3', version: 'v4.3.2', category: 'AI / ML', status: 'Active' },
  { name: 'Storage Manager', slug: 'storage-mgr', version: 'v1.2.4', category: 'Infrastructure', status: 'Inactive' },
];

export const RECENT_ACTIVITY: ActivityItem[] = [
  { icon: 'pi pi-cog', iconColor: '#1a56db', iconBg: '#eef4ff', title: 'Realm configured', subtitle: 'NGCB - GPX (Governme...', time: '2 hours ago', badge: 'REALM', badgeBg: '#eef4ff' },
  { icon: 'pi pi-check-circle', iconColor: '#16a34a', iconBg: '#f0fdf4', title: 'Service registered', subtitle: 'Auth Gateway v2.4.1', time: '5 hours ago', badge: 'SVC', badgeBg: '#f0fdf4' },
  { icon: 'pi pi-pencil', iconColor: '#d97706', iconBg: '#fffbeb', title: 'Deployment type changed', subtitle: 'Central for Financial lite...', time: '1 day ago', badge: 'REALM', badgeBg: '#fffbeb' },
  { icon: 'pi pi-exclamation-triangle', iconColor: '#dc2626', iconBg: '#fef2f2', title: 'Service deprecated', subtitle: 'Notification Bus', time: '2 days ago', badge: 'SVC', badgeBg: '#fef2f2' },
  { icon: 'pi pi-refresh', iconColor: '#64748b', iconBg: '#f8fafc', title: 'Version updated', subtitle: 'Public Debt Management', time: '3 days ago', badge: 'REALM', badgeBg: '#f8fafc' },
  { icon: 'pi pi-times-circle', iconColor: '#dc2626', iconBg: '#fef2f2', title: 'Service deregistered', subtitle: 'Legacy Auth v1.0', time: '5 days ago', badge: 'SVC', badgeBg: '#fef2f2' },
];
