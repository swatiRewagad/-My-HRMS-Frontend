import { Injectable, computed, inject } from '@angular/core';
import { KeycloakAuthService } from './keycloak-auth.service';

/**
 * Rung values are the strings the CEPC templates compare against. They name the CEPC ladder
 * directly (Incharge, Closing Authority) rather than the Ombudsman vocabulary the RBIO module uses.
 */
export type Rung = 'DO' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY' | 'ADMIN';

export type AssignmentTarget = 'DEALING_OFFICER' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY';

export interface CepcConfig {
  /** Department identifier as the backend and the shared screens expect it (query params, `source`). */
  code: 'CEPC';
  apiSegment: 'cepc';
  routePrefix: '/cepc';
  label: string;
  longLabel: string;
  logo: string;
  storagePrefix: string;
  roles: Record<Rung, string>;
  rungTitles: Record<Rung, string>;
  targetRoles: Record<AssignmentTarget, string>;
  features: { adjudicationRungs: Rung[]; supervisorRungs: Rung[] };
}

const CEPC_CONFIG: CepcConfig = {
  code: 'CEPC',
  apiSegment: 'cepc',
  routePrefix: '/cepc',
  label: 'CEPC',
  longLabel: 'Consumer Education and Protection Cell',
  logo: 'assets/RBI_New_Logo.png',
  storagePrefix: 'cepc',
  roles: {
    DO: 'CEPC_DO',
    REVIEWER: 'CEPC_REVIEWER',
    INCHARGE: 'CEPC_INCHARGE',
    CLOSING_AUTHORITY: 'CEPC_CLOSING_AUTHORITY',
    ADMIN: 'CEPC_ADMIN'
  },
  rungTitles: {
    DO: 'Dealing Officer',
    REVIEWER: 'Reviewer',
    INCHARGE: 'Incharge',
    CLOSING_AUTHORITY: 'Closing Authority',
    ADMIN: 'Administrator'
  },
  targetRoles: {
    DEALING_OFFICER: 'CEPC_DO',
    REVIEWER: 'CEPC_REVIEWER',
    INCHARGE: 'CEPC_INCHARGE',
    CLOSING_AUTHORITY: 'CEPC_CLOSING_AUTHORITY'
  },
  features: {
    // CEPC has no separate adjudicator or supervisor role, so Incharge carries adjudication.
    adjudicationRungs: ['INCHARGE'],
    supervisorRungs: ['REVIEWER']
  }
};

/**
 * The rungs that carry workflow authority. ADMIN is deliberately absent: the ladders this replaces
 * treat an administrator as a Dealing Officer, and the templates have no ADMIN branch.
 */
export type WorkflowRung = Exclude<Rung, 'ADMIN'>;

/** Highest rung first, matching the precedence of the if/else ladders this replaces. */
const RUNG_ORDER: WorkflowRung[] = ['CLOSING_AUTHORITY', 'INCHARGE', 'REVIEWER', 'DO'];

@Injectable({ providedIn: 'root' })
export class CepcContextService {

  private auth = inject(KeycloakAuthService);

  readonly cfg = computed(() => CEPC_CONFIG);

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

  /**
   * Falls back to 'DO' for an unrecognised role, so it cannot stand in for actually holding
   * CEPC_DO — use hasRung('DO') for that.
   */
  currentRung(): WorkflowRung | 'HEAD' {
    const roles = this.currentRoles();
    if (roles.includes('CRPC_HEAD')) return 'HEAD';
    return RUNG_ORDER.find(rung => {
      const role = this.role(rung);
      return !!role && roles.includes(role);
    }) ?? 'DO';
  }

  /** The caller's own CEPC role, for availability lookups. */
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

  /** `/workflow/cepc/action/123` */
  wf(suffix: string): string {
    return `/workflow/${this.cfg().apiSegment}/${suffix}`;
  }

  /** `/api/complaints/cepc/1/summary` */
  cx(suffix: string): string {
    return `/api/complaints/${this.cfg().apiSegment}/${suffix}`;
  }

  route(...segments: string[]): string[] {
    return [this.cfg().routePrefix, ...segments];
  }

  /** Department-scoped storage key, so a CEPC session cannot read an RBIO cache or vice versa. */
  key(name: string): string {
    return `${this.cfg().storagePrefix}_${name}`;
  }
}
