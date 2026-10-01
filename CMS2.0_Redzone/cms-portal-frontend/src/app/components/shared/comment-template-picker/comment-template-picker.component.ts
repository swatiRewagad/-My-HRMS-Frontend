import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { CommentTemplate, CommentTemplateService } from '../../../services/comment-template.service';

/**
 * Picks one of the admin-maintained comment templates and hands its body back to the caller (§5.3.6).
 *
 * <h2>Reuses the existing service, adds no endpoint</h2>
 * `CommentTemplateService` and `COMMENT_TEMPLATES` already existed and were read by exactly one screen,
 * `admin/comment-templates`, which maintains them. Nothing ever consumed them, so an officer could
 * never actually use a template that an administrator had carefully written. This component is the
 * consumer side, and it calls `/filtered` — the endpoint that is NOT `@PreAuthorize`-gated to
 * ADMIN/CRPC_HEAD, unlike `GET /api/v1/comment-templates`, so an ordinary officer gets a list rather
 * than a 403.
 *
 * <h2>It never writes into the remarks box itself</h2>
 * It emits. The action bar owns the remarks model, so inserting from here would mean two writers for
 * one field. The emitted value is the raw `content`; what the host does with it — replace or append —
 * is the host's decision, and the action bar's slot callback appends so an officer who has already
 * started typing does not lose the sentence.
 *
 * <h2>A select, deliberately, not a popup with a preview textarea</h2>
 * Seven e2e specs locate the remarks control as `.action-form textarea` / `.remarks-section textarea`,
 * which is a STRICT single-element locator in Playwright. A second textarea anywhere inside the action
 * form — a template preview, for instance — turns every one of those `fill()` calls into a
 * strict-mode violation. The preview is therefore read-only prose in a `<p>`, and the control is a
 * native `<select>`, which also gets keyboard and screen-reader behaviour for free.
 */
@Component({
  selector: 'app-comment-template-picker',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './comment-template-picker.component.html',
  styleUrl: './comment-template-picker.component.scss'
})
export class CommentTemplatePickerComponent implements OnInit {

  private service = inject(CommentTemplateService);

  /** Narrows the list, e.g. 'CLOSURE' while a closure action is selected. Null means all active. */
  readonly category = input<string | null>(null);

  /** EMAIL | PHYSICAL_LETTER | PORTAL. Null means every mode. */
  readonly modeOfReceipt = input<string | null>(null);

  /** The chosen template's body. The host decides whether to replace or append. */
  readonly apply = output<string>();

  templates = signal<CommentTemplate[]>([]);
  loading = signal(false);
  /** A load failure is SHOWN. A silent empty list here reads as "no templates exist". */
  failed = signal(false);
  selectedId = signal<number | null>(null);

  readonly selected = computed<CommentTemplate | null>(
    () => this.templates().find(t => t.id === this.selectedId()) ?? null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.service.getFiltered(this.category() ?? undefined, this.modeOfReceipt() ?? undefined)
      .subscribe({
        next: list => {
          // The endpoint's unfiltered branch returns only active rows, but `/filtered` with a category
          // goes through getByCategory, which does NOT filter on active — so an administrator's
          // deactivated template would reappear here. Filtered client-side rather than left to leak.
          this.templates.set((list ?? []).filter(t => t.active));
          this.loading.set(false);
        },
        error: () => {
          this.templates.set([]);
          this.failed.set(true);
          this.loading.set(false);
        }
      });
  }

  onSelect(raw: string): void {
    const id = Number(raw);
    this.selectedId.set(Number.isFinite(id) && raw !== '' ? id : null);
  }

  applySelected(): void {
    const template = this.selected();
    if (!template?.content) return;
    this.apply.emit(template.content);
  }
}
