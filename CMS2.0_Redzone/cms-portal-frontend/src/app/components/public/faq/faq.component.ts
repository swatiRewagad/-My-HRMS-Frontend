import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaqService, Faq } from '../../../services/faq.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './faq.component.html',
  styleUrl: './faq.component.scss'
})
export class FaqComponent implements OnInit {

  private faqService = inject(FaqService);

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
      result = result.filter(f =>
        f.questionKey.toLowerCase().includes(term) ||
        f.answerKey.toLowerCase().includes(term)
      );
    }
    return result;
  });

  ngOnInit() {
    this.loadFaqs();
  }

  private loadFaqs() {
    this.loading.set(true);
    this.faqService.getAll().subscribe({
      next: (data) => {
        this.faqs.set(data);
        this.loading.set(false);
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
