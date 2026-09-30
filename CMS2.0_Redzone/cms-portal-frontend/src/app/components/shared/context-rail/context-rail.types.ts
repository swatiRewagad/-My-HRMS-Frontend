/**
 * Configuration contract for the shared context rail.
 *
 * The rail is the RIGHT region of the canonical complaint-detail layout: a thin strip of icons holding
 * the material an officer CONSULTS — history, attachments, reference documents, correspondence — as
 * against the centre region, which is what they work in. RBIO established the pattern; CEPC and AA had
 * the same material stacked full-width below the fold, so consulting the history scrolled the form off
 * screen.
 */

/**
 * A panel the rail can open.
 *
 * `key` is opaque to the rail and is what comes back on {@link ContextRailComponent.opened} and what the
 * host switches on to choose body content. Hosts type it as a narrow string union so a typo in the
 * template's `@switch` is a compile error rather than a blank panel.
 */
export interface ContextRailPanel<K extends string = string> {
  key: K;

  /**
   * The heading shown in `.rail-header h4` and the icon's tooltip.
   *
   * Already-resolved text, not a translation key: `e2e/ui-homogenisation/rbio-three-panel.spec.ts:82-88`
   * asserts the exact English headings, and the three hosts are staff-only screens whose panel captions
   * were never seeded into the locale bundles. A host that needs a locale can pass
   * `i18n.translate(...)` — reading the signal in its own computed keeps the caption reactive.
   */
  label: string;

  /** A PrimeIcons class WITHOUT the `pi` base, e.g. 'pi-history'. The rail supplies `pi` itself. */
  icon: string;
}
