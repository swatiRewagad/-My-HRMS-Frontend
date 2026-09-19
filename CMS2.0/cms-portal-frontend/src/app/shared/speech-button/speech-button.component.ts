import { Component, Input, Output, EventEmitter, signal, computed, OnDestroy, NgZone, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

/** Fatal errors must not trigger the silence auto-restart, or the browser re-prompts in a loop. */
const FATAL_ERRORS = new Set(['not-allowed', 'service-not-allowed', 'audio-capture', 'bad-grammar']);

const ERROR_MESSAGES: Record<string, string> = {
  'not-allowed': 'Microphone blocked. Allow mic access for this site, then try again.',
  'service-not-allowed': 'Microphone blocked by browser policy.',
  'audio-capture': 'No microphone found.',
  network: 'Speech service unreachable. Check your connection.',
  'no-speech': "Didn't catch that. Try again.",
};

/**
 * Dictation into any text field. Emits each finalised phrase on {@link transcription}.
 *
 * Chrome's SpeechRecognition ends the session after a pause even with {@code continuous} set, so a
 * silence-triggered end is restarted transparently — otherwise dictation dies mid-sentence. The button
 * stays visible and explains itself when the browser cannot do speech at all, because a control that
 * silently disappears reads as a broken screen.
 */
@Component({
  selector: 'app-speech-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button type="button" class="speech-btn"
            [class.recording]="isRecording()"
            [disabled]="disabled || !!unavailableReason()"
            (click)="toggle()"
            [title]="hint()"
            [attr.aria-label]="hint()"
            [attr.aria-pressed]="isRecording()">
      <i class="pi" [class.pi-microphone]="!isRecording()" [class.pi-stop]="isRecording()"></i>
    </button>
    @if (isRecording()) {
      <span class="recording-indicator"><i class="pi pi-circle-fill"></i> Listening...</span>
    } @else if (error()) {
      <span class="speech-error" role="alert">{{ error() }}</span>
    }
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; gap: 6px; }
    .speech-btn {
      width: 34px; height: 34px; border-radius: 50%; border: 1px solid #ddd;
      background: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
      color: #666; font-size: 15px; transition: all 0.2s;
      &:hover:not(:disabled) { border-color: #2460b9; color: #2460b9; }
      &:disabled { opacity: 0.45; cursor: not-allowed; }
      &.recording { background: #d32f2f; border-color: #d32f2f; color: #fff; animation: pulse 1.5s infinite; }
    }
    .recording-indicator {
      font-size: 11px; color: #d32f2f; display: inline-flex; align-items: center; gap: 4px;
      i { font-size: 7px; animation: pulse 1s infinite; }
    }
    .speech-error { font-size: 11px; color: #d32f2f; max-width: 240px; line-height: 1.3; }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
  `]
})
export class SpeechButtonComponent implements OnDestroy {
  @Input() lang = 'en-IN';
  @Input() disabled = false;

  /** Each finalised phrase, trimmed. */
  @Output() transcription = new EventEmitter<string>();
  /** Live partial text while the user is still speaking; empty string once it finalises. */
  @Output() interim = new EventEmitter<string>();

  private zone = inject(NgZone);

  isRecording = signal(false);
  error = signal('');

  /** Null when dictation is usable, otherwise why it is not. */
  unavailableReason = computed(() => {
    if (typeof window === 'undefined') return 'Voice input is unavailable.';
    if (!this.speechApi()) return 'Voice input is not supported in this browser. Try Chrome or Edge.';
    if (window.isSecureContext === false) return 'Voice input needs a secure (HTTPS) connection.';
    return null;
  });

  hint = computed(() => this.unavailableReason()
    ?? (this.isRecording() ? 'Stop recording' : 'Voice input'));

  private recognition: any = null;
  /** Distinguishes a user stop from Chrome ending the session on silence. */
  private stopRequested = false;
  private restartsSinceLastResult = 0;

  ngOnDestroy() {
    this.teardown();
  }

  toggle() {
    if (this.isRecording()) {
      this.stop();
    } else {
      this.start();
    }
  }

  private speechApi(): any {
    const w = window as any;
    return w.SpeechRecognition || w.webkitSpeechRecognition || null;
  }

  private start() {
    if (this.disabled || this.unavailableReason()) return;

    this.error.set('');
    this.stopRequested = false;
    this.restartsSinceLastResult = 0;
    this.launch();
  }

  private launch() {
    const SpeechRecognition = this.speechApi();
    if (!SpeechRecognition) return;

    const recognition = new SpeechRecognition();
    this.recognition = recognition;
    recognition.lang = this.lang;
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event: any) => {
      this.restartsSinceLastResult = 0;
      let pending = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const text = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          const finalText = text.trim();
          if (finalText) this.zone.run(() => this.transcription.emit(finalText));
        } else {
          pending += text;
        }
      }
      this.zone.run(() => this.interim.emit(pending.trim()));
    };

    recognition.onerror = (event: any) => {
      const code = String(event?.error || '');
      if (code === 'aborted') return;
      if (FATAL_ERRORS.has(code)) this.stopRequested = true;
      this.zone.run(() => this.error.set(ERROR_MESSAGES[code] ?? 'Voice input failed. Try again.'));
    };

    // Chrome fires this on silence as well as on a real stop, hence the restart.
    recognition.onend = () => {
      if (this.stopRequested || this.restartsSinceLastResult >= 3) {
        this.zone.run(() => {
          this.isRecording.set(false);
          this.interim.emit('');
        });
        this.recognition = null;
        return;
      }
      this.restartsSinceLastResult++;
      this.launch();
    };

    try {
      recognition.start();
      this.zone.run(() => this.isRecording.set(true));
    } catch {
      // start() on an already-running instance throws; treat it as a failed session.
      this.zone.run(() => {
        this.isRecording.set(false);
        this.error.set('Could not start voice input. Try again.');
      });
      this.recognition = null;
    }
  }

  private stop() {
    this.stopRequested = true;
    if (this.recognition) {
      this.recognition.stop();
    }
    this.isRecording.set(false);
    this.interim.emit('');
  }

  private teardown() {
    this.stopRequested = true;
    const recognition = this.recognition;
    this.recognition = null;
    if (!recognition) return;
    recognition.onresult = null;
    recognition.onerror = null;
    recognition.onend = null;
    try {
      recognition.abort();
    } catch {
      // Already finished; nothing to release.
    }
    this.isRecording.set(false);
  }
}
