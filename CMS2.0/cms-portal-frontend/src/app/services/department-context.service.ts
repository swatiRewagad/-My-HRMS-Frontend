import { Injectable, computed, inject, signal } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAuthService } from './keycloak-auth.service';

export type DeptCode = 'RBIO' | 'CEPC';

/**
 * Rung values are the strings the existing templates already compare against
 * (`userRole() === 'DEPUTY_OMBUDSMAN'`), so they stay stable across departments even though CEPC
 * calls those rungs Incharge and Closing Authority.
 */
export type Rung = 'DO' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN' | 'ADMIN';

export type AssignmentTarget = 'DEALING_OFFICER' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN';

export interface DepartmentConfig {
  code: DeptCode;
  apiSegment: 'rbio' | 'cepc';
  routePrefix: '/rbio' | '/cepc';
  label: string;
  longLabel: string;
  logo: string;
  storagePrefix: string;
  roles: Partial<Record<Rung, string>>;
  rungTitles: Partial<Record<Rung, string>>;
  targetRoles: Record<AssignmentTarget, string>;
  features: { adjudicationRungs: Rung[]; supervisorRungs: Rung[] };
}

const RBIO_CONFIG: DepartmentConfig = {
  code: 'RBIO',
  apiSegment: 'rbio',
  routePrefix: '/rbio',
  label: 'RBIO',
  longLabel: 'Reserve Bank Integrated Ombudsman',
  logo: 'assets/RBIO_LOGO.svg',
  storagePrefix: 'rbio',
  roles: {
    DO: 'RBIO_DO',
    REVIEWER: 'RBIO_REVIEWER',
    DEPUTY_OMBUDSMAN: 'RBIO_DEPUTY_OMBUDSMAN',
    OMBUDSMAN: 'RBIO_OMBUDSMAN',
    ADMIN: 'RBIO_ADMIN'
  },
  rungTitles: {
    DO: 'Dealing Officer',
    REVIEWER: 'Reviewer',
    DEPUTY_OMBUDSMAN: 'Deputy Ombudsman',
    OMBUDSMAN: 'Ombudsman',
    ADMIN: 'Administrator'
  },
  targetRoles: {
    DEALING_OFFICER: 'RBIO_DO',
    REVIEWER: 'RBIO_REVIEWER',
    DEPUTY_OMBUDSMAN: 'RBIO_DEPUTY_OMBUDSMAN',
    OMBUDSMAN: 'RBIO_OMBUDSMAN'
  },
  features: {
    adjudicationRungs: ['DEPUTY_OMBUDSMAN', 'OMBUDSMAN'],
    supervisorRungs: ['REVIEWER']
  }
};

const CEPC_CONFIG: DepartmentConfig = {
  code: 'CEPC',
  apiSegment: 'cepc',
  routePrefix: '/cepc',
  label: 'CEPC',
  longLabel: 'Consumer Education and Protection Cell',
  // CEPC has no logo asset of its own yet; sharing the RBI mark keeps the header from breaking.
  logo: 'assets/RBIO_LOGO.svg',
  storagePrefix: 'cepc',
  roles: {
    DO: 'CEPC_DO',
    REVIEWER: 'CEPC_REVIEWER',
    DEPUTY_OMBUDSMAN: 'CEPC_INCHARGE',
    OMBUDSMAN: 'CEPC_CLOSING_AUTHORITY',
    ADMIN: 'CEPC_ADMIN'
  },
  rungTitles: {
    DO: 'Dealing Officer',
    REVIEWER: 'Reviewer',
    DEPUTY_OMBUDSMAN: 'Incharge',
    OMBUDSMAN: 'Closing Authority',
    ADMIN: 'Administrator'
  },
  targetRoles: {
    DEALING_OFFICER: 'CEPC_DO',
    REVIEWER: 'CEPC_REVIEWER',
    DEPUTY_OMBUDSMAN: 'CEPC_INCHARGE',
    OMBUDSMAN: 'CEPC_CLOSING_AUTHORITY'
  },
  features: {
    // CEPC has no adjudicator or supervisor role, so Incharge carries adjudication.
    adjudicationRungs: ['DEPUTY_OMBUDSMAN'],
    supervisorRungs: ['REVIEWER']
  }
};

const CONFIGS: Record<DeptCode, DepartmentConfig> = { RBIO: RBIO_CONFIG, CEPC: CEPC_CONFIG };

/**
 * The rungs that carry workflow authority. ADMIN is deliberately absent: the ladders this replaces
 * treat an administrator as a Dealing Officer, and the templates have no ADMIN branch.
 */
export type WorkflowRung = Exclude<Rung, 'ADMIN'>;

/** Highest rung first, matching the precedence of the if/else ladders this replaces. */
const RUNG_ORDER: WorkflowRung[] = ['OMBUDSMAN', 'DEPUTY_OMBUDSMAN', 'REVIEWER', 'DO'];

@Injectable({ providedIn: 'root' })
export class DepartmentContextService {

  private auth = inject(KeycloakAuthService);
  private router = inject(Router);

  private code = signal<DeptCode>('RBIO');

  readonly cfg = computed(() => CONFIGS[this.code()]);
  readonly department = computed(() => this.code());

  constructor() {
    this.code.set(this.fromAuth());
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => this.setFromRoute(this.router.routerState.snapshot.root));
  }

  /**
   * Driven by departmentContextGuard, which runs before component activation. A NavigationEnd
   * subscription alone would be too late: activation constructs the components first.
   */
  setFromRoute(root: ActivatedRouteSnapshot | null): void {
    let snapshot = root;
    let found: DeptCode | null = null;
    while (snapshot) {
      const data = snapshot.data?.['department'];
      if (data === 'RBIO' || data === 'CEPC') found = data;
      snapshot = snapshot.firstChild;
    }
    this.code.set(found ?? this.fromAuth());
  }

  private fromAuth(): DeptCode {
    return this.auth.currentUser()?.department === 'CEPC' ? 'CEPC' : 'RBIO';
  }

  /**
   * Reads the currentUser signal rather than getRoles() so callers inside computed() stay reactive
   * to auth resolving; falls back to the raw token roles before the signal is populated.
   */
  private currentRoles(): string[] {
    const user = this.auth.currentUser();
    return user?.roles?.length ? user.roles : this.auth.getRoles();
  }

  role(rung: Rung): string | undefined {
    return this.cfg().roles[rung];
  }

  rolesFor(...rungs: Rung[]): string[] {
    return rungs.map(r => this.role(r)).filter((r): r is string => !!r);
  }

  hasRung(...rungs: Rung[]): boolean {
    const roles = this.currentRoles();
    return this.rolesFor(...rungs).some(r => roles.includes(r));
  }

  currentRung(): WorkflowRung | 'HEAD' {
    const roles = this.currentRoles();
    if (roles.includes('CRPC_HEAD')) return 'HEAD';
    return RUNG_ORDER.find(rung => {
      const role = this.role(rung);
      return !!role && roles.includes(role);
    }) ?? 'DO';
  }

  /** The caller's own role within the active department, for availability lookups. */
  primaryRole(): string {
    const departmentRoles = Object.values(this.cfg().roles);
    return this.currentRoles().find(r => departmentRoles.includes(r)) ?? this.role('DO')!;
  }

  targetRole(target: AssignmentTarget): string | undefined {
    return this.cfg().targetRoles[target];
  }

  canAdjudicate(): boolean {
    return this.hasRung(...this.cfg().features.adjudicationRungs);
  }

  canSupervise(): boolean {
    return this.hasRung(...this.cfg().features.supervisorRungs);
  }

  rungTitle(rung: Rung): string {
    return this.cfg().rungTitles[rung] ?? '';
  }

  /** `/workflow/rbio/action/123` or `/workflow/cepc/action/123` */
  wf(suffix: string): string {
    return `/workflow/${this.cfg().apiSegment}/${suffix}`;
  }

  /** `/api/complaints/rbio/1/summary` or `/api/complaints/cepc/1/summary` */
  cx(suffix: string): string {
    return `/api/complaints/${this.cfg().apiSegment}/${suffix}`;
  }

  route(...segments: string[]): string[] {
    return [this.cfg().routePrefix, ...segments];
  }

  /** Department-scoped storage key, so an RBIO session cannot read a CEPC cache or vice versa. */
  key(name: string): string {
    return `${this.cfg().storagePrefix}_${name}`;
  }
}

export const departmentContextGuard: CanActivateFn = (route) => {
  inject(DepartmentContextService).setFromRoute(route.root ?? route);
  return true;
};
