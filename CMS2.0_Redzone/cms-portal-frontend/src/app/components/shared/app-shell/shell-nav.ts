/**
 * The single staff navigation vocabulary.
 *
 * Before this, crpc/rbio/aa/cepc each repeated their own header and sidebar markup, so the same
 * officer saw a different chrome per module and a nav change meant editing every page.
 *
 * Visibility is by Keycloak realm role. This is presentation only — it decides what a user is
 * OFFERED, not what they may do. The route guards and the server's @PreAuthorize remain the
 * enforcement, because a client-side list is trivially bypassed.
 */
export interface ShellNavItem {
  /** Translation key for the label. Never a literal — this renders in ten locales. */
  labelKey: string;
  icon: string;
  route: string;
  /** Any one of these realm roles reveals the item. Empty means "any authenticated staff user". */
  roles: string[];
}

export const SHELL_NAV: ShellNavItem[] = [
  {
    labelKey: 'ui.nav.crpc_complaints',
    icon: 'pi pi-list',
    route: '/crpc/home',
    roles: ['DO', 'CA', 'REVIEWER', 'INCHARGE', 'CP', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.cepc_dashboard',
    icon: 'pi pi-inbox',
    route: '/cepc/dashboard',
    roles: ['DO', 'REVIEWER', 'INCHARGE', 'CA', 'CP', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.rbio_workbench',
    icon: 'pi pi-briefcase',
    route: '/rbio',
    roles: ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_CONCILIATOR', 'RBIO_ADJUDICATOR', 'RBIO_ADMIN', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.aa_appeals',
    icon: 'pi pi-gavel',
    route: '/aa/dashboard',
    roles: ['AA_REGISTRAR', 'AA_BENCH_OFFICER', 'AA_AUTHORITY', 'AA_ADMIN', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.re_portal',
    icon: 'pi pi-building',
    route: '/re-portal/dashboard',
    roles: ['RE_NODAL_OFFICER', 'RE_PNO', 'RE_ADMIN'],
  },
  {
    labelKey: 'ui.nav.reports',
    icon: 'pi pi-chart-bar',
    route: '/admin/dashboard',
    roles: ['ADMIN', 'CP', 'INCHARGE', 'RBIO_SUPERVISOR', 'RBIO_ADMIN'],
  },
];
