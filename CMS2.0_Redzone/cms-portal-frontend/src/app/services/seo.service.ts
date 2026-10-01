import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Meta, Title } from '@angular/platform-browser';
import { TranslationService } from './translation.service';
import { SUPPORTED_LOCALES } from '../seo/seo-routes';

/**
 * Per-route title, description, canonical, Open Graph, Twitter and hreflang tags.
 *
 * <h2>Why this exists</h2>
 *
 * <p>There were ZERO calls to {@code Title.setTitle} or {@code Meta.updateTag} anywhere in the
 * application. All 84 routes shared one static title from {@code index.html}, so a crawler could not
 * tell the FAQ from the complaint form, and every social-media share of any page showed the same
 * generic card. For a government grievance service that people search for by name, that is the
 * difference between being found and not.
 *
 * <h2>Every string is a translation key</h2>
 *
 * <p>Titles and descriptions are looked up, never hardcoded. This is an eleven-locale product; baking
 * English into a title tag would rebuild exactly the defect the i18n work removed. The key is resolved
 * through {@link TranslationService}, which falls back to the English default when a locale lacks it —
 * a real sentence rather than a raw key.
 *
 * <h2>hreflang</h2>
 *
 * <p>Eleven {@code <link rel="alternate" hreflang>} tags plus {@code x-default} tell a search engine
 * that the Hindi and Gurmukhi pages are the same document in another language, rather than duplicate
 * content competing with each other. This is the single biggest SEO differentiator available to an
 * Indian-language government service, and it serves precisely the users the Scheme exists for.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly translation = inject(TranslationService);
  private readonly document = inject(DOCUMENT);

  /**
   * Canonical origin. Overridable at runtime so the value is not compiled in: the portal is served from
   * nginx with `envsubst`, and a canonical tag pointing at the wrong host de-indexes the real one.
   */
  canonicalOrigin(): string {
    const configured = (this.document.defaultView as any)?.__CMS_CANONICAL_ORIGIN__;
    return typeof configured === 'string' && configured ? configured : 'https://cms.rbi.org.in';
  }

  /**
   * Applies every tag for one route.
   *
   * @param titleKey       translation key for the page title
   * @param descriptionKey translation key for the meta description
   * @param path           route path WITHOUT a locale prefix, e.g. `/public/faq`
   * @param indexable      false for an authenticated or per-complaint page
   */
  apply(titleKey: string, descriptionKey: string, path: string, indexable = true): void {
    const locale = this.translation.currentLocale();
    const pageTitle = this.translation.translate(titleKey);
    const description = this.translation.translate(descriptionKey);
    const canonical = `${this.canonicalOrigin()}${path}`;

    // The suffix is the service's name, so a tab and a search result both identify the page AND the
    // service. Title-cased separately from the key so translators never have to reproduce it.
    const fullTitle = `${pageTitle} | ${this.translation.translate('seo.site_name')}`;
    this.title.setTitle(fullTitle);

    this.meta.updateTag({ name: 'description', content: description });

    // A page behind a login must never be indexed: the URL is crawlable even though the content is
    // guarded, and an indexed /public/history invites people to a page they cannot use.
    this.meta.updateTag({
      name: 'robots',
      content: indexable ? 'index, follow' : 'noindex, nofollow',
    });

    this.meta.updateTag({ property: 'og:title', content: fullTitle });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: canonical });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:locale', content: locale });
    this.meta.updateTag({ property: 'og:site_name', content: this.translation.translate('seo.site_name') });

    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: fullTitle });
    this.meta.updateTag({ name: 'twitter:description', content: description });

    this.setCanonical(canonical);
    this.setHreflang(path, indexable);
    this.setHtmlLang(locale);
  }

  /** `<html lang>`, which screen readers use to choose a voice and crawlers to confirm the language. */
  setHtmlLang(locale: string): void {
    this.document.documentElement.setAttribute('lang', locale);
  }

  private setCanonical(href: string): void {
    const head = this.document.head;
    let link = head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  /**
   * Rewrites the hreflang set for the current path.
   *
   * <p>Previous tags are REMOVED first. Leaving stale ones behind would accumulate alternates for pages
   * the user has navigated away from, which tells a crawler that unrelated URLs are translations of one
   * another. Not emitted for a non-indexable page, where declaring alternates would be meaningless.
   */
  private setHreflang(path: string, indexable: boolean): void {
    const head = this.document.head;
    head.querySelectorAll('link[rel="alternate"][hreflang]').forEach(el => el.remove());
    if (!indexable) return;

    const origin = this.canonicalOrigin();
    for (const locale of SUPPORTED_LOCALES) {
      const link = this.document.createElement('link');
      link.setAttribute('rel', 'alternate');
      link.setAttribute('hreflang', locale);
      link.setAttribute('href', `${origin}${path}?lang=${locale}`);
      head.appendChild(link);
    }

    // x-default is what a crawler serves to a user whose language matches none of the eleven.
    const fallback = this.document.createElement('link');
    fallback.setAttribute('rel', 'alternate');
    fallback.setAttribute('hreflang', 'x-default');
    fallback.setAttribute('href', `${origin}${path}`);
    head.appendChild(fallback);
  }

  /** Injects or replaces a JSON-LD block, identified by `id` so a re-render does not duplicate it. */
  setJsonLd(id: string, data: unknown): void {
    const head = this.document.head;
    const existing = head.querySelector(`script[type="application/ld+json"][data-seo-id="${id}"]`);
    existing?.remove();

    const script = this.document.createElement('script');
    script.setAttribute('type', 'application/ld+json');
    script.setAttribute('data-seo-id', id);
    script.textContent = JSON.stringify(data);
    head.appendChild(script);
  }

  /** Removes a JSON-LD block, so a page's structured data does not leak onto the next route. */
  clearJsonLd(id: string): void {
    this.document.head
      .querySelector(`script[type="application/ld+json"][data-seo-id="${id}"]`)
      ?.remove();
  }
}
