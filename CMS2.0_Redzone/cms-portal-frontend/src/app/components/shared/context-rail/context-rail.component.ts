import { Component, TemplateRef, computed, input, model } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ContextRailPanel } from './context-rail.types';

/**
 * The one context rail — the RIGHT region of the canonical complaint-detail layout.
 *
 * Three screens open a complaint (RBIO, CEPC, AA) and each had the same problem: history, attachments
 * and correspondence were FULL-WIDTH blocks stacked below the working area, so consulting any of them
 * scrolled the form out of sight. RBIO solved it with a 48px icon strip whose body overlays the panels;
 * this is that solution extracted so the other two cannot re-diverge from it.
 *
 * <h2>Why the host element is the `aside`</h2>
 * An attribute selector rather than an element one, so the rendered DOM is literally `aside.context-rail`
 * and the rail IS the grid child. Wrapping it in an `<app-context-rail>` element would put a non-grid
 * element in the third column and break the width contract below, and
 * `e2e/ui-homogenisation/rbio-three-panel.spec.ts` measures the LEFT panel across a rail toggle to catch
 * exactly that.
 *
 * <h2>The rail occupies a fixed 48px whether open or closed</h2>
 * Its body is absolutely positioned and pulled LEFTWARD over the working panels, so opening it adds no
 * width to the grid. An earlier RBIO version let the column grow to 360px and a layout test measured the
 * left panel losing 151px per toggle — which moves form fields under the officer's cursor mid-edit. The
 * rail is glanced at, so covering the panels for the moment it is open costs less than reflowing them.
 *
 * <h2>Panel bodies are ONE template, keyed by the open panel</h2>
 * The bodies have nothing in common — RBIO projects three feature components, CEPC an upload control and
 * an audit trail, AA an inline timeline. Modelling them as data would mean encoding components in a
 * config object. The host passes a single `body` template and switches on the `$implicit` key inside it,
 * the same split the shared complaint summary and workflow action bar use for host-specific content.
 * Projected content is styled by the DECLARING component, so each host keeps its own panel rules.
 */
@Component({
  selector: 'aside[app-context-rail]',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './context-rail.component.html',
  styleUrl: './context-rail.component.scss',
  host: {
    // `.context-rail` is the selector every layout spec reaches the rail through.
    'class': 'context-rail',
    'role': 'complementary'
  }
})
export class ContextRailComponent<K extends string = string> {

  readonly panels = input.required<readonly ContextRailPanel<K>[]>();

  /**
   * The open panel's key, or null for collapsed.
   *
   * Two-way so a host can drive it from outside the rail: RBIO's tab-strip `+` means "show me the
   * documents" and sets this to 'attachments' directly. Collapsed is the only sane default — a rail that
   * opened itself would cover the working panels on every page load.
   */
  readonly open = model<K | null>(null);

  /** Rendered inside `.rail-body` with the open key as `$implicit`. See the class doc. */
  readonly body = input<TemplateRef<unknown> | null>(null);

  readonly activePanel = computed<ContextRailPanel<K> | null>(() => {
    const key = this.open();
    return key === null ? null : this.panels().find(p => p.key === key) ?? null;
  });

  /** Clicking the ACTIVE icon collapses the rail, so there is never an open rail with no way to shut it. */
  toggle(key: K): void {
    this.open.update(current => (current === key ? null : key));
  }

  close(): void {
    this.open.set(null);
  }
}
