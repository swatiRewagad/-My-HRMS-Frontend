import { Component, Input, Output, EventEmitter, signal, OnDestroy, NgZone, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-speech-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (supported) {
      <button type="button" class="speech-btn" [class.recording]="isRecording()"
              (click)="toggle()" [title]="isRecording() ? 'Stop recording' : 'Voice input'">
        <i class="pi" [class.pi-microphone]="!isRecording()" [class.pi-stop]="isRecording()"></i>
      </button>
      @if (isRecording()) {
        <span class="recording-indicator"><i class="pi pi-circle-fill"></i> Listening...</span>
      }
    }
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; gap: 6px; }
    .speech-btn {
      width: 34px; height: 34px; border-radius: 50%; border: 1px solid #ddd;
      background: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
      color: #666; font-size: 15px; transition: all 0.2s;
      &:hover { border-color: #2460b9; color: #2460b9; }
      &.recording { background: #d32f2f; border-color: #d32f2f; color: #fff; animation: pulse 1.5s infinite; }
    }
    .recording-indicator {
      font-size: 11px; color: #d32f2f; display: inline-flex; align-items: center; gap: 4px;
      i { font-size: 7px; animation: pulse 1s infinite; }
    }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
  `]
})
export class SpeechButtonComponent implements OnDestroy {
  @Input() lang = 'en-IN';
  @Output() transcription = new EventEmitter<string>();

  private zone = inject(NgZone);
  supported = !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
  isRecording = signal(false);
  private recognition: any = null;

  ngOnDestroy() {
    this.stop();
  }

  toggle() {
    if (this.isRecording()) {
      this.stop();
    } else {
      this.start();
    }
  }

  private start() {
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SR) return;

    this.recognition = new SR();
    this.recognition.lang = this.lang;
    this.recognition.continuous = true;
    this.recognition.interimResults = true;

    let finalTranscript = '';

    this.recognition.onresult = (event: any) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const text = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalTranscript += text;
          this.zone.run(() => this.transcription.emit(text.trim()));
        }
      }
    };

    this.recognition.onerror = (event: any) => {
      console.warn('Speech recognition error:', event.error);
      this.zone.run(() => this.isRecording.set(false));
    };

    this.recognition.onend = () => {
      this.zone.run(() => this.isRecording.set(false));
    };

    try {
      this.recognition.start();
      this.isRecording.set(true);
    } catch (e) {
      console.warn('Speech recognition failed to start:', e);
      this.isRecording.set(false);
    }
  }

  private stop() {
    if (this.recognition) {
      this.recognition.stop();
      this.recognition = null;
    }
    this.isRecording.set(false);
  }
}
