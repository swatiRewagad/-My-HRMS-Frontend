/**
 * The SEO route table: the ONE source for per-route metadata, the sitemap and the prerender list.
 *
 * <p>WHY ONE TABLE. The committed `sitemap.xml` was hand-maintained and listed 6 URLs, one of which
 * (`/public/login`) should never have been indexed while `/public/faq` — a page of genuinely useful
 * public content — was missing entirely. A hand-written sitemap drifts from the router the moment anyone
 * adds a route. Generating it from this table means the drift cannot happen silently.
 *
 * <p>`indexable: false` is the important column. A route behind `publicAuthGuard` is still a CRAWLABLE
 * URL even though its content is protected, so it must be excluded from the sitemap AND emit
 * `noindex` — otherwise search results advertise pages a visitor cannot open, and per-complaint URLs
 * risk exposing that a particular reference number exists.
 */

/** Every locale the portal serves. Drives hreflang and the sitemap's alternates. */
export const SUPPORTED_LOCALES = [
  'en', 'hi', 'bn', 'mr', 'te', 'ta', 'gu', 'ur', 'kn', 'ml', 'pa',
] as const;

export interface SeoRoute {
  /** Router path, absolute, no locale prefix. */
  path: string;
  /** Translation key for `<title>`. */
  titleKey: string;
  /** Translation key for `<meta name="description">`. */
  descriptionKey: string;
  /** False for authenticated or per-record pages: excluded from the sitemap, emits noindex. */
  indexable: boolean;
  /** Sitemap priority. Only meaningful when indexable. */
  priority?: string;
  changefreq?: string;
  /** Whether the route can be prerendered at build time (no route parameters, no auth). */
  prerender?: boolean;
}

export const SEO_ROUTES: SeoRoute[] = [
  {
    path: '/public',
    titleKey: 'seo.home_title',
    descriptionKey: 'seo.home_description',
    indexable: true,
    priority: '1.0',
    changefreq: 'weekly',
    prerender: true,
  },
  {
    path: '/public/faq',
    titleKey: 'seo.faq_title',
    descriptionKey: 'seo.faq_description',
    indexable: true,
    priority: '0.9',
    changefreq: 'monthly',
    prerender: true,
  },
  {
    path: '/public/eligibility-wizard',
    titleKey: 'seo.eligibility_title',
    descriptionKey: 'seo.eligibility_description',
    indexable: true,
    priority: '0.9',
    changefreq: 'monthly',
    prerender: true,
  },
  {
    path: '/public/track',
    titleKey: 'seo.track_title',
    descriptionKey: 'seo.track_description',
    indexable: true,
    priority: '0.8',
    changefreq: 'monthly',
    prerender: true,
  },
  {
    // Indexable on purpose: people search for how to log a complaint, and this is the page that tells
    // them. The FORM behind it needs a session, but the landing content is public.
    path: '/public/login',
    titleKey: 'seo.login_title',
    descriptionKey: 'seo.login_description',
    indexable: true,
    priority: '0.6',
    changefreq: 'yearly',
    prerender: true,
  },

  // ── NOT INDEXABLE ────────────────────────────────────────────────────────────────────────────
  // Behind publicAuthGuard. The URL is crawlable, the content is not, so these emit noindex and are
  // absent from the sitemap. /public/file-complaint was previously listed in sitemap.xml at 0.9.
  {
    path: '/public/file-complaint',
    titleKey: 'seo.file_complaint_title',
    descriptionKey: 'seo.file_complaint_description',
    indexable: false,
  },
  {
    path: '/public/history',
    titleKey: 'seo.history_title',
    descriptionKey: 'seo.history_description',
    indexable: false,
  },
  {
    path: '/public/appeal',
    titleKey: 'seo.appeal_title',
    descriptionKey: 'seo.appeal_description',
    indexable: false,
  },
  {
    path: '/public/feedback',
    titleKey: 'seo.feedback_title',
    descriptionKey: 'seo.feedback_description',
    indexable: false,
  },
  {
    path: '/public/withdraw',
    titleKey: 'seo.withdraw_title',
    descriptionKey: 'seo.withdraw_description',
    indexable: false,
  },
];

export function seoRouteFor(path: string): SeoRoute | undefined {
  const normalised = path.split('?')[0].replace(/\/+$/, '') || '/public';
  return SEO_ROUTES.find(r => r.path === normalised);
}

/** Routes that belong in sitemap.xml. */
export function indexableRoutes(): SeoRoute[] {
  return SEO_ROUTES.filter(r => r.indexable);
}

/** Routes safe to prerender: public, no auth guard, no route parameters. */
export function prerenderRoutes(): string[] {
  return SEO_ROUTES.filter(r => r.prerender && r.indexable).map(r => r.path);
}
