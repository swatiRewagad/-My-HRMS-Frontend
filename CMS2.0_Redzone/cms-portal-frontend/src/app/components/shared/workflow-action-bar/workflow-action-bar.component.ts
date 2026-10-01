import { Component, TemplateRef, computed, inject, input, model, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslationService } from '../../../services/translation.service';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { WorkflowAction, WorkflowActionLayout } from './workflow-action-bar.types';

/**
 * The one cluster of workflow-transition CTAs, plus the confirm step that commits one.
 *
 * Three screens hand-rolled this — staff/task-action, cepc-complaint-detail, aa-appeal-detail — and
 * diverged on every axis: the action key (`value` vs `id`), the caption (literal vs translation key),
 * whether remarks were per-action or unconditional, the style vocabulary, and the class names.
 *
 * <h2>Two layouts, one contract</h2>
 * `pills` is staff/task-action's horizontal row of outlined buttons; `cards` is the stacked
 * label-plus-description cards cepc and aa use in their sticky right panel. They are the same control
 * over the same data, so they are one component and only the stylesheet branches. Keeping both preserves
 * each host's appearance, which is what makes this a refactor rather than a restyle.
 *
 * <h2>Host-specific content is a template, never a config object</h2>
 * The confirm step is not uniform: cepc needs a department picker, aa an officer picker, staff a closure
 * clause, a custom-closure textarea, a concurrent-edit notice and draft badges. Encoding those as data
 * would mean modelling selects and per-status visibility rules in here. They stay caller templates —
 * `fields` above the remarks box, `extras` below it, `commitExtras` inside the commit row — the same
 * split the shared complaint summary and task grid already use for their host-specific actions.
 *
 * Projected content is styled by the DECLARING component, so each host keeps its own `.form-field`
 * rules for the controls it passes in.
 *
 * <h2>An empty action list renders nothing at all</h2>
 * Not an empty wrapper. `e2e/rbio/reopen-admin.spec.ts:172` asserts zero `.action-btn` AND zero
 * `.action-card` on a closed complaint, and `e2e/aa/s3b-appeal-screens.spec.ts:168` asserts the
 * `action-list` test id has count 0 on a disposed appeal. An empty wrapper is also indistinguishable
 * from a load failure for a sighted user, which is why both hosts pair it with a terminal banner.
 */
@Component({
  selector: 'app-workflow-action-bar',
  standalone: true,
  imports: [NgTemplateOutlet, SpeechButtonComponent],
  templateUrl: './workflow-action-bar.component.html',
  styleUrl: './workflow-action-bar.component.scss',
  host: {
    '[class.layout-pills]': "layout() === 'pills'",
    '[class.layout-cards]': "layout() === 'cards'"
  }
})
export class WorkflowActionBarComponent<T extends WorkflowAction = WorkflowAction> {

  /**
   * Resolved directly rather than through TranslatePipe: the captions are chosen by
   * {@link captionOf}, which has to decide between a key and a literal per action, and a pipe cannot
   * be applied conditionally. Reading `translate()` inside a template-called method still tracks the
   * service's signals, so a locale switch repaints.
   */
  private readonly i18n = inject(TranslationService);

  /**
   * Generic over the action type so `select` hands the host back its OWN shape. cepc and aa both extend
   * WorkflowAction with a target picker and act on that extra data in their submit; a non-generic output
   * would have widened the event to the base type and forced a cast at every call site.
   */
  readonly actions = input.required<readonly T[]>();

  /** The chosen action's id, or null/'' for none. The host owns the selection and the commit call. */
  readonly selectedId = input<string | null>(null);

  readonly layout = input<WorkflowActionLayout>('cards');

  /** Disables every button and swaps the commit caption for its busy text. */
  readonly processing = input(false);

  /** Two-way. Hosts hold this as a plain string field; the banana-in-a-box binding works either way. */
  readonly remarks = model('');

  /** Offers the microphone beside the remarks box. Off by default: aa has never had one. */
  readonly speech = input(false);

  /** Renders the selected action's caption as a heading above the form. cepc and aa do; staff does not. */
  readonly showFormTitle = input(false);

  readonly remarksRows = input(4);

  readonly remarksLabel = input('Remarks');
  readonly remarksLabelKey = input<string | null>(null);
  readonly remarksPlaceholder = input('Enter remarks (required)...');
  readonly remarksPlaceholderKey = input<string | null>(null);

  readonly commitLabel = input('Confirm');
  readonly commitLabelKey = input<string | null>(null);
  readonly processingLabel = input('Processing...');
  readonly processingLabelKey = input<string | null>(null);
  readonly cancelLabel = input('Cancel');
  readonly cancelLabelKey = input<string | null>(null);

  /** Staff's commit button carries a spinner glyph; the other two show text only. */
  readonly commitSpinner = input(false);

  /** Rendered above the remarks box — target pickers. See the class doc. */
  readonly fields = input<TemplateRef<unknown> | null>(null);

  /**
   * §5.3.6 — rendered immediately above the remarks LABEL, for a saved-remark template picker.
   *
   * A template rather than a boolean flag, so a screen that does not want one is bit-for-bit
   * unaffected: when this is null the slot container is not rendered at all, and the DOM between
   * `.action-form` and its textarea is exactly what it was before. That matters because seven specs
   * locate the remarks control as `.action-form textarea` / `.remarks-section textarea`.
   *
   * It sits ABOVE the label rather than below the textarea because an officer picks a template before
   * writing, and because `extras` below the box is already crowded with closure fields and draft
   * badges on the one host that uses both.
   *
   * The slot receives `{ $implicit: action, insert: fn }`. `insert` APPENDS to the remarks model
   * rather than replacing it — a template chosen after the officer has started typing must not
   * discard the sentence they were mid-way through. Hosts should bind it as
   * `let-insert="insert"` and call `insert(content)`.
   */
  readonly templateSlot = input<TemplateRef<unknown> | null>(null);
  /** Rendered below the remarks box — closure fields, warnings, draft badges. */
  readonly extras = input<TemplateRef<unknown> | null>(null);
  /** Rendered between commit and cancel — staff's "Save in Draft". */
  readonly commitExtras = input<TemplateRef<unknown> | null>(null);

  readonly select = output<T>();
  readonly commit = output<void>();
  readonly cancel = output<void>();

  readonly selected = computed<T | null>(
    () => this.actions().find(a => a.id === this.selectedId()) ?? null);

  /**
   * Absent means required. staff/task-action demanded remarks for every one of its actions and stated it
   * nowhere, so the fail-safe default reproduces that host; an action that omits the flag is gated
   * rather than waved through.
   */
  readonly remarksRequired = computed(() => this.selected()?.requiresRemarks !== false);

  readonly canCommit = computed(() =>
    !this.processing() && (!this.remarksRequired() || this.remarks().trim().length > 0));

  captionOf(action: WorkflowAction): string {
    return action.labelKey ? this.i18n.translate(action.labelKey) : (action.label ?? action.id);
  }

  subCaptionOf(action: WorkflowAction): string {
    return action.descriptionKey ? this.i18n.translate(action.descriptionKey) : (action.description ?? '');
  }

  /**
   * Both historical class names on every button, plus the style and selection modifiers. See the
   * template header for the specs that pin each name.
   */
  buttonClass(action: WorkflowAction): string {
    const parts = ['action-btn', 'action-card', action.style ?? 'primary'];
    if (action.id === this.selectedId()) parts.push('selected');
    return parts.join(' ');
  }

  resolvedRemarksLabel = () => this.resolve(this.remarksLabelKey(), this.remarksLabel());
  resolvedRemarksPlaceholder = () => this.resolve(this.remarksPlaceholderKey(), this.remarksPlaceholder());
  resolvedCommitLabel = () => this.resolve(this.commitLabelKey(), this.commitLabel());
  resolvedProcessingLabel = () => this.resolve(this.processingLabelKey(), this.processingLabel());
  resolvedCancelLabel = () => this.resolve(this.cancelLabelKey(), this.cancelLabel());

  onRemarksInput(value: string) {
    this.remarks.set(value);
  }

  /**
   * Appends a template body to the remarks, handed to the §5.3.6 slot as its `insert` context member.
   *
   * An arrow property, not a method: it is passed as a value into `ngTemplateOutletContext` and called
   * from the host's template, where a plain method reference would lose `this`.
   *
   * APPENDS rather than replaces, for the same reason the speech transcription does — an officer who
   * has typed half a sentence and then reaches for a template must not silently lose it. Separated by
   * a blank line so the two blocks read as distinct paragraphs, and trimmed so picking a template
   * first does not leave the remarks starting with whitespace (which `canCommit`'s trim tolerates but
   * which looks like a bug in the saved record).
   */
  readonly insertTemplate = (content: string): void => {
    if (!content) return;
    const current = this.remarks();
    this.remarks.set(current.trim() ? `${current.trimEnd()}\n\n${content}` : content);
  };

  onTranscription(text: string) {
    this.remarks.set(`${this.remarks()} ${text}`);
  }

  /** A translation key wins over a literal, so a host can migrate one caption at a time. */
  private resolve(key: string | null, literal: string): string {
    return key ? this.i18n.translate(key) : literal;
  }
}
