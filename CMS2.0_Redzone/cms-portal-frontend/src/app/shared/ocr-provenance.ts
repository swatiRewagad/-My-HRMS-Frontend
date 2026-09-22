import { signal, Signal, WritableSignal } from '@angular/core';

/**
 * Tracks which form fields were filled by OCR and which the operator typed (UST624, UST625).
 *
 * <h2>Why this exists</h2>
 * OCR values were written into ordinary form fields with no marking of any kind. The only signal to the
 * operator was a single form-wide amber banner ("Content extraction may not be 100% accurate"), which is
 * dismissable — and dismissing it silently re-armed the extract button. There was no way to tell, looking
 * at a filled form, which values came from a scan and which a person had verified.
 *
 * <h2>Why a separate helper rather than per-component state</h2>
 * Three components pre-populate from OCR — rbio-create-complaint, physical-letter and draft-assessment —
 * and they disagreed about the most important rule. draft-assessment prefilled blank fields only;
 * the other two overwrote unconditionally, so an operator who corrected a field and re-ran extraction
 * silently lost the correction. UST625 requires the operator's value to win, so the rule has to be in one
 * place that all three call.
 *
 * <h2>The override rule</h2>
 * {@link applyOcrValue} writes an OCR value only when the field is empty OR its current value is itself
 * unedited OCR output. Once the operator touches a field, {@link markEdited} removes it from the OCR set
 * and later extractions leave it alone. That is what makes "override wins" true rather than aspirational.
 *
 * <p>Note this deliberately does NOT key off any existing "OCR complete" flag: in those components the
 * same flag is set both when extraction succeeds and when the operator skips it, so it cannot distinguish
 * "these values came from OCR" from "there was no OCR".
 */
export class OcrProvenance {

  /** Field names currently holding unedited OCR output. */
  private readonly ocrFields: WritableSignal<Set<string>> = signal(new Set<string>());

  /** Field names the operator has typed into or corrected. */
  private readonly editedFields: WritableSignal<Set<string>> = signal(new Set<string>());

  /** Exposed for templates, so a field can render its provenance badge. */
  get ocrFilled(): Signal<Set<string>> {
    return this.ocrFields.asReadonly();
  }

  get edited(): Signal<Set<string>> {
    return this.editedFields.asReadonly();
  }

  /** True when this field currently holds OCR output the operator has not altered. */
  isFromOcr(field: string): boolean {
    return this.ocrFields().has(field);
  }

  /** True when the operator has overridden this field. */
  isEdited(field: string): boolean {
    return this.editedFields().has(field);
  }

  /** How many fields still hold unverified OCR output — drives the "please verify" summary. */
  unverifiedCount(): number {
    return this.ocrFields().size;
  }

  /**
   * Decides whether an OCR value may be written to a field, and records the provenance if so.
   *
   * @param field        the form field name
   * @param currentValue whatever the field holds right now
   * @returns true when the caller should assign the OCR value
   */
  shouldApply(field: string, currentValue: unknown): boolean {
    if (this.editedFields().has(field)) {
      // UST625: the operator's input is the record. A later extraction must not overwrite it.
      return false;
    }
    const isEmpty = currentValue === null || currentValue === undefined || currentValue === ''
      || (typeof currentValue === 'string' && currentValue.trim() === '');
    return isEmpty || this.ocrFields().has(field);
  }

  /** Records that a field now holds OCR output. */
  markFromOcr(field: string): void {
    this.ocrFields.update(set => {
      const next = new Set(set);
      next.add(field);
      return next;
    });
    this.editedFields.update(set => {
      if (!set.has(field)) return set;
      const next = new Set(set);
      next.delete(field);
      return next;
    });
  }

  /**
   * Records that the operator has typed into a field.
   *
   * <p>Call this from the field's own change handler. Once marked, the field is theirs: it keeps its
   * value through any subsequent extraction and its badge changes from "from scan" to "edited".
   */
  markEdited(field: string): void {
    const wasOcr = this.ocrFields().has(field);
    if (wasOcr) {
      this.ocrFields.update(set => {
        const next = new Set(set);
        next.delete(field);
        return next;
      });
    }
    this.editedFields.update(set => {
      const next = new Set(set);
      next.add(field);
      return next;
    });
  }

  /**
   * Clears all provenance.
   *
   * <p>Used when the attachment is removed — the previous scan's provenance no longer describes anything.
   * Deliberately does NOT clear the operator's edits from the form, only the record of where values came
   * from; removing a file must not discard typed work.
   */
  reset(): void {
    this.ocrFields.set(new Set<string>());
    this.editedFields.set(new Set<string>());
  }

  /** The CSS class for a field, so provenance is visible rather than merely known. */
  cssClass(field: string): string {
    if (this.isFromOcr(field)) return 'ocr-filled';
    if (this.isEdited(field)) return 'ocr-overridden';
    return '';
  }

  /** Translation key for the field's provenance badge. */
  badgeKey(field: string): string | null {
    if (this.isFromOcr(field)) return 'ocr.field_from_scan';
    if (this.isEdited(field)) return 'ocr.field_operator_entered';
    return null;
  }

  /**
   * The provenance map to send with a submit, so the server knows which values a human vouched for.
   *
   * <p>The server's existing {@code firstNonBlank(operatorValue, ocrValue)} can tell a blank from a
   * filled field but cannot tell an operator-TYPED value from an operator-ACCEPTED OCR value. This closes
   * that gap for UST625's "saves as the DO's input".
   */
  toPayload(): { ocrFields: string[]; operatorEditedFields: string[] } {
    return {
      ocrFields: Array.from(this.ocrFields()),
      operatorEditedFields: Array.from(this.editedFields())
    };
  }
}
