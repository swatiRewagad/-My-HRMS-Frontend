import { Injectable, signal, OnDestroy } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TtsService implements OnDestroy {
  private _enabled = signal(this.loadPreference());
  private synth = window.speechSynthesis;
  private boundHandler = this.handleFocusOrClick.bind(this);

  enabled = this._enabled.asReadonly();

  constructor() {
    if (this._enabled()) {
      this.attachListeners();
    }
  }

  toggle() {
    const next = !this._enabled();
    this._enabled.set(next);
    localStorage.setItem('cms_tts_enabled', String(next));

    if (next) {
      this.attachListeners();
    } else {
      this.detachListeners();
      this.stop();
    }
  }

  speak(text: string) {
    if (!this._enabled() || !text?.trim()) return;
    this.stop();
    const utterance = new SpeechSynthesisUtterance(text.trim());
    utterance.lang = document.documentElement.lang || 'en-IN';
    utterance.rate = 0.9;
    utterance.pitch = 1;
    this.synth.speak(utterance);
  }

  stop() {
    if (this.synth.speaking || this.synth.pending) {
      this.synth.cancel();
    }
  }

  ngOnDestroy() {
    this.detachListeners();
    this.stop();
  }

  private handleFocusOrClick(event: Event) {
    if (!this._enabled()) return;

    const target = event.target as HTMLElement;
    if (!target) return;

    const text = this.extractText(target);
    if (text) {
      this.speak(text);
    }
  }

  private extractText(el: HTMLElement): string {
    if (el.getAttribute('aria-label')) {
      return el.getAttribute('aria-label')!;
    }

    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
      const input = el as HTMLInputElement;
      const label = document.querySelector(`label[for="${el.id}"]`);
      const placeholder = input.placeholder || '';
      const value = input.value || '';
      const labelText = label?.textContent || el.getAttribute('aria-label') || '';
      return labelText ? `${labelText}. ${value || placeholder}` : (value || placeholder);
    }

    if (el.tagName === 'SELECT') {
      const select = el as HTMLSelectElement;
      const label = document.querySelector(`label[for="${el.id}"]`);
      const labelText = label?.textContent || el.getAttribute('aria-label') || '';
      const selectedText = select.options[select.selectedIndex]?.text || '';
      return labelText ? `${labelText}. Selected: ${selectedText}` : selectedText;
    }

    if (el.tagName === 'BUTTON') {
      return el.textContent?.trim() || el.getAttribute('aria-label') || '';
    }

    if (el.tagName === 'A') {
      return `Link: ${el.textContent?.trim() || ''}`;
    }

    const directText = el.textContent?.trim() || '';
    return directText.length <= 500 ? directText : directText.substring(0, 500);
  }

  private attachListeners() {
    document.addEventListener('focusin', this.boundHandler, true);
    document.addEventListener('click', this.boundHandler, true);
  }

  private detachListeners() {
    document.removeEventListener('focusin', this.boundHandler, true);
    document.removeEventListener('click', this.boundHandler, true);
  }

  private loadPreference(): boolean {
    return localStorage.getItem('cms_tts_enabled') === 'true';
  }
}
