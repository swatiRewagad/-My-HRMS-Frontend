import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaqService, Faq } from '../../../services/faq.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../services/translation.service';
import { SeoService } from '../../../services/seo.service';
import { faqPage } from '../../../seo/structured-data';

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './faq.component.html',
  styleUrl: './faq.component.scss'
})
export class FaqComponent implements OnInit, OnDestroy {

  private faqService = inject(FaqService);
  private translationService = inject(TranslationService);
  private seo = inject(SeoService);

  faqs = signal<Faq[]>([]);
  loading = signal(true);
  error = signal('');
  searchTerm = signal('');
  selectedCategory = signal<string>('');
  expandedIds = signal<Set<number>>(new Set());

  categories = computed(() => {
    const cats = new Set<string>();
    this.faqs().forEach(f => {
      if (f.category) cats.add(f.category);
    });
    return Array.from(cats).sort();
  });

  filteredFaqs = computed(() => {
    let result = this.faqs();
    const cat = this.selectedCategory();
    if (cat) {
      result = result.filter(f => f.category === cat);
    }
    const term = this.searchTerm().toLowerCase().trim();
    if (term) {
      // Matching the translated text, not the key: a citizen searching in Hindi would otherwise
      // only ever match the English key fragments and see an empty result.
      result = result.filter(f =>
        this.translationService.translate(f.questionKey).toLowerCase().includes(term) ||
        this.translationService.translate(f.answerKey).toLowerCase().includes(term)
      );
    }
    return result;
  });

  categoryLabel(category: string): string {
    return this.translationService.translate(`faq.cat_${category}`);
  }

  ngOnInit() {
    this.loadFaqs();
  }

  ngOnDestroy() {
    // The block lives in <head>, so leaving it behind would describe FAQ content on whatever page the
    // user navigates to next.
    this.seo.clearJsonLd('faq');
  }

  /**
   * FAQPage structured data, which is what can surface expandable answers directly in search results.
   *
   * <p>Built from the RESOLVED text the component renders, in the active locale, because Google requires
   * the marked-up answer to be visible on the page. Emitting the raw translation keys, or prose the user
   * cannot see, is a structured-data violation rather than a shortcut.
   */
  private publishFaqStructuredData(): void {
    const entries = this.faqs()
      .map(f => ({
        question: this.translationService.translate(f.questionKey),
        answer: this.translationService.translate(f.answerKey),
      }))
      // A key that resolves to itself means the bundle lacks it; marking that up would publish
      // "faq.q_how_long" to a search engine as though it were a question.
      .filter(e => e.question && e.answer
        && !e.question.startsWith('faq.') && !e.answer.startsWith('faq.'));

    if (entries.length === 0) {
      this.seo.clearJsonLd('faq');
      return;
    }
    this.seo.setJsonLd('faq', faqPage(entries));
  }

  private loadFaqs() {
    this.loading.set(true);
    this.faqService.getAll().subscribe({
      next: (data) => {
        this.faqs.set(data);
        this.loading.set(false);
        this.publishFaqStructuredData();
      },
      error: () => {
        this.error.set('faq.load_error');
        this.loading.set(false);
      }
    });
  }

  toggleFaq(id: number) {
    this.expandedIds.update(set => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  isExpanded(id: number): boolean {
    return this.expandedIds().has(id);
  }

  filterByCategory(category: string) {
    this.selectedCategory.set(category);
  }

  onSearchInput(value: string) {
    this.searchTerm.set(value);
  }
}
