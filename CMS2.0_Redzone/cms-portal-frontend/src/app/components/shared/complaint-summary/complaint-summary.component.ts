import { Component, TemplateRef, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { ComplaintSummaryItem } from './complaint-summary.types';

/**
 * The one complaint summary strip.
 *
 * Every screen that opens a complaint leads with the same band of identifying facts, then a cluster of
 * page-level buttons on the right. Five screens implemented that separately — task-action,
 * rbio-complaint-detail, and the three CRPC assessment screens — and drifted in every dimension that
 * was not load-bearing: raw hex versus design tokens, 14px versus 13px values, one shared indigo icon
 * disc versus five per-item tones, and `.complaint-strip` versus `.meta-row` for the same element.
 *
 * <h2>Items are data, actions are a template</h2>
 * The identifying facts are uniform enough to be a list of {@link ComplaintSummaryItem}. The buttons are
 * not: CRPC's reviewer carries Save/Edit/Send-Back/Approve, RBIO carries a Send-for-Approval dropdown,
 * and task-action carries a single Take Action. Modelling those as data would mean encoding dropdowns and
 * per-role disable rules in a config object, so they stay a caller template — the same split the shared
 * task grid already uses for its headerActions.
 *
 * <h2>Status renders through app-status-badge, always</h2>
 * Not as text. Three of the five copies printed the raw status or their own hardcoded label, which is how
 * one complaint came to read "In Progress" on its detail screen and "Under Examination" on the dashboard
 * that linked to it.
 */
@Component({
  selector: 'app-complaint-summary',
  standalone: true,
  imports: [NgTemplateOutlet, TranslatePipe, StatusBadgeComponent],
  templateUrl: './complaint-summary.component.html',
  styleUrl: './complaint-summary.component.scss'
})
export class ComplaintSummaryComponent {

  readonly items = input.required<readonly ComplaintSummaryItem[]>();

  /** Shows the circular back affordance. Off for screens reached without a parent list. */
  readonly showBack = input(true);

  /** Page-level buttons, rendered right-aligned. See the class doc for why this is a template. */
  readonly actions = input<TemplateRef<unknown> | null>(null);

  readonly back = output<void>();

  /** Defaults to the indigo brand disc that three of the five copies used. */
  toneClass(item: ComplaintSummaryItem): string {
    return item.tone ?? 'brand';
  }
}
