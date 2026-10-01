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

/**
 * Role names MUST be roles the `cms` realm actually issues, not the `rbi-cms` taxonomy
 * (DO, CA, CP, INCHARGE, AA_REGISTRAR, AA_BENCH_OFFICER, RE_NODAL_OFFICER). Both realms exist on
 * the same Keycloak, but every app here authenticates against `cms`, so an `rbi-cms` name matches
 * no token and its item silently never renders. A nav that is empty for every real user is
 * indistinguishable from one that is merely permissive, which is how the original list survived.
 *
 * Each list mirrors the corresponding guard in app.routes.ts: offering a link the guard would then
 * bounce to /staff/unauthorized is worse than not offering it.
 */
export const SHELL_NAV: ShellNavItem[] = [
  {
    labelKey: 'ui.nav.crpc_complaints',
    icon: 'pi pi-list',
    route: '/crpc/home',
    roles: ['DEO', 'REVIEWER', 'CRPC_HEAD', 'CRPC_INCHARGE', 'CRPC_ADMIN', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.cepc_dashboard',
    icon: 'pi pi-inbox',
    route: '/cepc/dashboard',
    roles: [
      'CEPC_DO', 'CEPC_REVIEWER', 'CEPC_INCHARGE', 'CEPC_OFFICER', 'CEPC_SUPERVISOR',
      'CEPC_CLOSING_AUTHORITY', 'CEPC_CONCILIATOR', 'CEPC_ADJUDICATOR', 'CEPC_CONTACT_PERSON',
      'CEPC_ADMIN', 'ADMIN',
    ],
  },
  {
    labelKey: 'ui.nav.rbio_workbench',
    icon: 'pi pi-briefcase',
    route: '/rbio',
    roles: [
      'RBIO_DEALING_OFFICIAL', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN',
      'RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_CONCILIATOR', 'RBIO_ADJUDICATOR',
      'RBIO_ADMIN', 'ADMIN',
    ],
  },
  {
    labelKey: 'ui.nav.aa_appeals',
    icon: 'pi pi-gavel',
    route: '/aa/dashboard',
    roles: ['AA_DO', 'AA_REVIEWER', 'AA_SECRETARIAT', 'AA_ADMIN', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.re_portal',
    icon: 'pi pi-building',
    route: '/re-portal/dashboard',
    roles: ['RE_PNO', 'ADMIN'],
  },
  {
    labelKey: 'ui.nav.reports',
    icon: 'pi pi-chart-bar',
    route: '/admin/dashboard',
    roles: ['ADMIN', 'CRPC_ADMIN', 'CRPC_HEAD'],
  },
];
