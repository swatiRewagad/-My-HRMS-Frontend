export interface Realm {
  id: string;
  name: string;
  displayName: string;
  initials: string;
  description: string;
  realmId: string;
  owner: string;
  ownerEmail: string;
  department: string;
  type: string;
  userCount: number;
  status: 'active' | 'inactive';
  createdAt: string;
  syncedAt: string;
}

export interface SubService {
  id: string;
  label: string;
  description: string;
}

export interface PlatformService {
  id: string;
  label: string;
  description: string;
  group: 'basic' | 'ai';
  dependent: boolean;
  subServices?: SubService[];
}

export interface ServiceApiInfo {
  invocation: string;
  endpoint: string;
  method: string;
  contentType: string;
  requestBody: string;
  requestHeaders: string;
}

export interface SchemaConfig {
  schemaName: string;
  dbType: string;
  connectionString: string;
  secretPath: string;
  showConn: boolean;
}

export interface ExistingConfig {
  mode: 'app-designer' | 'non-app-designer';
  version: string;
  deployType: 'dependent' | 'independent';
  configuredAt: string;
  configuredBy: string;
  services: string[];
}

export interface ModeOption {
  value: 'app-designer' | 'non-app-designer';
  label: string;
  description: string;
  features: string[];
  color: string;
  bg: string;
  border: string;
}

export interface WizardStep {
  label: string;
  short: string;
}

export type DeploymentType = 'dependent' | 'independent' | '';
