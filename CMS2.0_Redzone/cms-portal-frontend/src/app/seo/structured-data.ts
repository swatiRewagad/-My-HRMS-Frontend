/**
 * JSON-LD builders. There were ZERO occurrences of structured data anywhere in the portal.
 *
 * <p>WHY IT MATTERS HERE SPECIFICALLY. Structured data is what produces a rich result rather than a
 * plain blue link: a GovernmentOrganization panel identifying RBI as the operator, a sitelinks search
 * box, and — the valuable one — expandable FAQ answers directly in the results page. For a citizen
 * searching "how do I complain about my bank", an answer visible without clicking is the difference
 * between being helped and bouncing.
 *
 * <p>NOTHING HERE ASSERTS LEGAL EFFECT. The FAQ entries come from the eighteen active FAQ rows, whose
 * wording already went through review; this only re-expresses them in a machine-readable form. No clause
 * numbers, no statutory text and no compensation figures are emitted, because a structured-data snippet
 * is not a place to make a legal statement.
 */

/** Values that need RBI sign-off before go-live are collected here rather than scattered inline. */
export const RBI_ORGANISATION = {
  legalName: 'Reserve Bank of India',
  url: 'https://www.rbi.org.in',
  /** The Scheme's published toll-free number, already shown on the portal's own pages. */
  telephone: '14448',
} as const;

export function governmentOrganisation(origin: string, siteName: string, description: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'GovernmentOrganization',
    name: siteName,
    legalName: RBI_ORGANISATION.legalName,
    url: origin,
    sameAs: [RBI_ORGANISATION.url],
    description,
    contactPoint: {
      '@type': 'ContactPoint',
      telephone: RBI_ORGANISATION.telephone,
      contactType: 'customer service',
      areaServed: 'IN',
      // The eleven locales the portal actually serves. Claiming more would be a false signal.
      availableLanguage: ['en', 'hi', 'bn', 'mr', 'te', 'ta', 'gu', 'ur', 'kn', 'ml', 'pa'],
    },
  };
}

/**
 * WebSite with a SearchAction, which is what can earn a sitelinks search box.
 *
 * <p>The target is the complaint TRACKER, because that is the portal's only genuine public search: it
 * takes a reference number and returns a status. Pointing a SearchAction at a page that cannot search
 * would produce a box that silently does nothing.
 */
export function webSiteWithSearch(origin: string, siteName: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: siteName,
    url: origin,
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: `${origin}/public/track?ref={search_term_string}`,
      },
      'query-input': 'required name=search_term_string',
    },
  };
}

/**
 * FAQPage built from the live FAQ rows.
 *
 * <p>Google requires the marked-up answer to be VISIBLE on the page, so this is generated from exactly
 * what the component renders — the same resolved question and answer text, in the active locale. Marking
 * up content the user cannot see is a structured-data violation, not a shortcut.
 */
export function faqPage(entries: { question: string; answer: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: entries.map(e => ({
      '@type': 'Question',
      name: e.question,
      acceptedAnswer: { '@type': 'Answer', text: e.answer },
    })),
  };
}

/** BreadcrumbList, so a result shows "rbi.org.in › Complaints › FAQ" rather than a bare URL. */
export function breadcrumbs(origin: string, trail: { name: string; path: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((crumb, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: crumb.name,
      item: `${origin}${crumb.path}`,
    })),
  };
}
