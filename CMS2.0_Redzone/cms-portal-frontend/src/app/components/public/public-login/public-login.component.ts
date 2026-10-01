import { Component, inject, signal, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { PublicAuthService } from '../../../services/public-auth.service';
import { CitizenAuthApiService, CaptchaResponse } from '../../../services/citizen-auth-api.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../services/translation.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-public-login',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './public-login.component.html',
  styleUrl: './public-login.component.scss'
})
export class PublicLoginComponent implements OnDestroy {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private authService = inject(PublicAuthService);
  private authApi = inject(CitizenAuthApiService);
  private translation = inject(TranslationService);

  mobile = '';
  captchaInput = '';
  captchaData = signal<CaptchaResponse | null>(null);
  captchaLoading = signal(false);
  captchaType = signal<'VISUAL' | 'MATH'>('VISUAL');

  otpSent = signal(false);
  otpDigits = ['', '', '', '', '', ''];
  otpVerified = signal(false);
  sessionId = '';

  loginError = '';
  otpError = '';
  resendTimer = 0;
  private resendInterval: any = null;

  showEmailFallback = signal(false);
  emailForOtp = '';
  emailVerificationSent = signal(false);
  emailVerificationMessage = '';

  cooloffActive = signal(false);
  cooloffSeconds = signal(0);
  private cooloffInterval: any = null;

  devOtpPopulated = signal(false);
  loading = signal(false);
  consentChecked = false;

  constructor() {
    this.loadCaptcha();
    if (this.authService.isSessionValid()) {
      this.router.navigate(['/public']);
    }
  }

  ngOnDestroy() {
    this.clearResendTimer();
    this.clearCooloffTimer();
  }

  loadCaptcha(onLoaded?: () => void) {
    this.captchaLoading.set(true);
    this.captchaInput = '';
    this.authApi.getCaptcha(this.captchaType()).subscribe({
      next: (data) => {
        this.captchaData.set(data);
        this.captchaLoading.set(false);
        onLoaded?.();
      },
      error: () => {
        this.loginError = 'Failed to load CAPTCHA. Please try again.';
        this.captchaLoading.set(false);
      }
    });
  }

  switchCaptchaType() {
    this.captchaType.set(this.captchaType() === 'VISUAL' ? 'MATH' : 'VISUAL');
    this.loadCaptcha();
  }

  playCaptchaAudio() {
    // A VISUAL challenge has no speakable form — its answer is deliberately not sent to the
    // client. Swap to the MATH challenge, which is both accessible and safe to read aloud.
    if (this.captchaType() !== 'MATH') {
      this.captchaType.set('MATH');
      this.loadCaptcha(() => this.speakCaptcha());
      return;
    }
    this.speakCaptcha();
  }

  private speakCaptcha() {
    const question = this.captchaData()?.audioQuestion;
    if (!question) return;
    const utterance = new SpeechSynthesisUtterance(question);
    utterance.rate = 0.7;
    utterance.lang = 'en-IN';
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }

  sendOtp() {
    this.loginError = '';

    // An ABSENT number and a MALFORMED one are different problems and must not share a message: telling a
    // citizen who typed nothing that what they typed is invalid is both untrue and unactionable. Wording
    // is kept identical to the server's CitizenAuthController.MSG_MOBILE_REQUIRED so the same omission is
    // described the same way whether it is caught here or there.
    if (!this.mobile.trim()) {
      this.loginError = 'Mobile number is required to request OTP.';
      return;
    }

    if (!/^[6-9]\d{9}$/.test(this.mobile)) {
      this.loginError = 'Enter a valid 10-digit Indian mobile number starting with 6-9.';
      return;
    }

    if (!this.captchaInput.trim()) {
      this.loginError = 'Please enter the CAPTCHA.';
      return;
    }

    const captcha = this.captchaData();
    if (!captcha) {
      this.loginError = 'CAPTCHA not loaded. Please refresh.';
      return;
    }

    if (!this.consentChecked) {
      this.loginError = 'You must accept the data processing declaration to continue.';
      return;
    }

    this.loading.set(true);
    this.authApi.sendOtp(this.mobile, captcha.token, this.captchaInput.trim(),
                         this.consentChecked, this.translation.currentLocale()).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpSent.set(true);
        this.otpDigits = ['', '', '', '', '', ''];
        this.devOtpPopulated.set(false);
        const devOtp = res.devOtp || (environment.devAutoPopulateOtp ? environment.devDefaultOtp : '');
        if (devOtp) {
          this.otpDigits = devOtp.split('').slice(0, 6);
          this.devOtpPopulated.set(true);
        }
        this.startResendTimer(res.resendAfterSeconds);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'COOLOFF_ACTIVE') {
          this.startCooloff(body.retryAfterSeconds);
        } else if (body?.error === 'RATE_LIMITED') {
          this.loginError = body.message || 'Too many OTP requests. Try again later.';
        } else if (body?.error === 'RESEND_COOLDOWN') {
          // The server owns the cooldown, so re-show the OTP step with its remaining time rather than
          // stranding the citizen on the mobile step with no way back to the code they already have.
          // Its own wording is shown verbatim: the gap is configurable, and a sentence composed here from
          // retryAfterSeconds gave two different descriptions of one rule.
          this.otpSent.set(true);
          this.startResendTimer(body.retryAfterSeconds);
          this.otpError = body.message;
        } else if (body?.error === 'INVALID_CAPTCHA') {
          this.loginError = 'Invalid CAPTCHA. Please try again.';
          this.loadCaptcha();
          // UST4/UST6: a wrong CAPTCHA now counts towards lockout, so the wait must be shown here too
          // or the citizen would keep retrying into a silent 429.
          if (body.cooloffActive) this.startCooloff(body.retryAfterSeconds);
        } else {
          this.loginError = body?.message || 'Failed to send OTP. Please try again.';
        }
      }
    });
  }

  sendOtpViaEmail() {
    this.loginError = '';

    if (!this.emailForOtp || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.emailForOtp)) {
      this.loginError = 'Enter a valid email address.';
      return;
    }

    const captcha = this.captchaData();
    if (!captcha) {
      this.loadCaptcha();
      this.loginError = 'Please complete the CAPTCHA first.';
      return;
    }

    if (!this.consentChecked) {
      this.loginError = 'You must accept the data processing declaration to continue.';
      return;
    }

    this.loading.set(true);
    this.authApi.sendOtpViaEmail(this.mobile, this.emailForOtp, captcha.token, this.captchaInput.trim(),
                                 this.consentChecked, this.translation.currentLocale()).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpSent.set(true);
        this.showEmailFallback.set(false);
        this.otpDigits = ['', '', '', '', '', ''];
        const devOtp = res.devOtp || (environment.devAutoPopulateOtp ? environment.devDefaultOtp : '');
        if (devOtp) {
          this.otpDigits = devOtp.split('').slice(0, 6);
          this.devOtpPopulated.set(true);
        }
        this.startResendTimer(res.resendAfterSeconds);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'EMAIL_NOT_VERIFIED') {
          this.loginError = 'Email not verified. Please verify your email first.';
        } else if (body?.error === 'COOLOFF_ACTIVE') {
          this.startCooloff(body.retryAfterSeconds);
        } else if (body?.error === 'RESEND_COOLDOWN') {
          this.otpSent.set(true);
          this.startResendTimer(body.retryAfterSeconds);
          this.otpError = body.message;
        } else if (body?.error === 'INVALID_CAPTCHA') {
          this.loginError = 'Invalid CAPTCHA. Please try again.';
          this.loadCaptcha();
          if (body.cooloffActive) this.startCooloff(body.retryAfterSeconds);
        } else {
          this.loginError = body?.message || 'Failed to send OTP via email.';
        }
      }
    });
  }

  initiateEmailVerification() {
    if (!this.emailForOtp || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.emailForOtp)) {
      this.loginError = 'Enter a valid email address.';
      return;
    }

    this.authApi.initiateEmailVerification(this.mobile, this.emailForOtp).subscribe({
      next: (res) => {
        this.emailVerificationSent.set(true);
        this.emailVerificationMessage = res.message;
      },
      error: (err) => {
        this.loginError = err.error?.message || 'Failed to send verification email.';
      }
    });
  }

  onOtpInput(index: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, '');
    this.otpDigits[index] = val ? val[0] : '';
    if (val && index < 5) {
      const next = input.parentElement?.querySelectorAll('input')[index + 1] as HTMLInputElement;
      if (next) next.focus();
    }
  }

  onOtpKeydown(index: number, event: KeyboardEvent) {
    if (event.key === 'Backspace' && !this.otpDigits[index] && index > 0) {
      const prev = (event.target as HTMLElement).parentElement?.querySelectorAll('input')[index - 1] as HTMLInputElement;
      if (prev) prev.focus();
    }
  }

  verifyOtp() {
    const code = this.otpDigits.join('');
    if (code.length < 6) { this.otpError = 'Enter all 6 digits'; return; }
    this.otpError = '';
    this.loginError = '';
    this.loading.set(true);

    this.authApi.verifyOtp(this.mobile, code, this.sessionId).subscribe({
      next: (res) => {
        this.otpVerified.set(true);
        this.clearResendTimer();
        this.authService.login(this.mobile, res.token);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(returnUrl || '/public');
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'INVALID_OTP') {
          this.otpError = 'Incorrect OTP. Please try again.';
          if (body.cooloffActive) {
            this.startCooloff(body.retryAfterSeconds);
          }
        } else if (body?.error === 'OTP_EXPIRED' || body?.error === 'MAX_ATTEMPTS') {
          // Both of these send the citizen back to the mobile step, and BOTH used to lose the reason for
          // it. `.field-error` — the only element that renders otpError — lives inside the @if (otpSent())
          // block, so flipping otpSent to false in the same tick unmounted the message being set: the
          // screen silently reverted to "Enter Mobile Number" with nothing said about the expiry, and the
          // citizen's only clue was that the OTP boxes had vanished. The text is therefore put on
          // loginError, whose banner renders on BOTH steps, and the server's wording is used verbatim so
          // the sentence cannot drift from the one the API returns.
          this.loginError = body?.message
            || (body?.error === 'OTP_EXPIRED'
              ? 'OTP has expired. Please request a new one.'
              : 'Too many incorrect attempts. Please request a new OTP.');
          this.otpError = '';
          this.otpSent.set(false);
          // The abandoned CAPTCHA belonged to the attempt just ended; a stale one would refuse the
          // citizen's next request for a reason unrelated to what they did.
          this.captchaInput = '';
          this.otpDigits = ['', '', '', '', '', ''];
          this.devOtpPopulated.set(false);
          this.clearResendTimer();
          this.loadCaptcha();
        } else {
          this.otpError = body?.message || 'Verification failed.';
        }
      }
    });
  }

  /**
   * Requests a NEW OTP for the number already entered.
   *
   * <p>This used to call no API at all: it set otpSent to false and reloaded the CAPTCHA, so "Resend
   * OTP" actually meant "start again" — no new code was issued, and the citizen who had not received
   * one was sent back to re-solve a CAPTCHA. It now hits POST /resend-otp, which issues a fresh code
   * to the same mobile and retires the previous one.
   *
   * <p>The server's own refusal message is shown verbatim on RESEND_COOLDOWN rather than a sentence
   * composed here from retryAfterSeconds: the cooldown is configurable, and two wordings for one rule
   * is how the citizen ends up being told a different figure from the one being enforced.
   */
  resendOtp() {
    if (this.resendTimer > 0 || this.loading()) return;
    this.otpError = '';
    this.loading.set(true);

    this.authApi.resendOtp(this.mobile, this.translation.currentLocale()).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpDigits = ['', '', '', '', '', ''];
        this.devOtpPopulated.set(false);
        const devOtp = res.devOtp || (environment.devAutoPopulateOtp ? environment.devDefaultOtp : '');
        if (devOtp) {
          this.otpDigits = devOtp.split('').slice(0, 6);
          this.devOtpPopulated.set(true);
        }
        this.startResendTimer(res.resendAfterSeconds);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'COOLOFF_ACTIVE') {
          this.startCooloff(body.retryAfterSeconds);
        } else if (body?.error === 'RESEND_COOLDOWN') {
          // The server states the wait; the countdown uses its seconds so the button re-enables exactly
          // when a resend would in fact be accepted.
          this.otpError = body.message;
          this.startResendTimer(body.retryAfterSeconds);
        } else if (body?.error === 'NO_ACTIVE_REQUEST') {
          // The earlier CAPTCHA-gated request has lapsed, so a resend is no longer a continuation of it.
          this.otpSent.set(false);
          this.loginError = body.message || 'Please request an OTP first.';
          this.loadCaptcha();
        } else {
          this.otpError = body?.message || 'Failed to resend OTP. Please try again.';
        }
      }
    });
  }

  /**
   * "Change Phone Number" — returns to the mobile + CAPTCHA screen.
   *
   * <p>The mobile and the CAPTCHA answer are CLEARED. They were not: the old number stayed pre-filled,
   * so a citizen who clicked this because they had mistyped their number was handed the mistake back,
   * and a shoulder-surfer saw the previous number. loadCaptcha() blanks captchaInput itself, but it is
   * cleared here as well so the field is never briefly showing an answer for a challenge that has gone.
   */
  cancelVerification() {
    this.otpSent.set(false);
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.loginError = '';
    this.mobile = '';
    this.captchaInput = '';
    this.sessionId = '';
    this.devOtpPopulated.set(false);
    this.clearResendTimer();
    // The challenge tied to the abandoned attempt is single-use; a fresh one is needed for the new number.
    this.loadCaptcha();
  }

  toggleEmailFallback() {
    this.showEmailFallback.set(!this.showEmailFallback());
  }

  private startCooloff(seconds: number) {
    this.cooloffActive.set(true);
    this.cooloffSeconds.set(seconds);
    this.loginError = `Too many attempts. Please wait ${seconds} seconds.`;
    this.clearCooloffTimer();
    this.cooloffInterval = setInterval(() => {
      const remaining = this.cooloffSeconds() - 1;
      this.cooloffSeconds.set(remaining);
      if (remaining <= 0) {
        this.clearCooloffTimer();
        this.cooloffActive.set(false);
        this.loginError = '';
      }
    }, 1000);
  }

  private clearCooloffTimer() {
    if (this.cooloffInterval) {
      clearInterval(this.cooloffInterval);
      this.cooloffInterval = null;
    }
  }

  /**
   * Starts the Resend countdown from the gap the SERVER reports.
   *
   * <p>This defaulted to 120 seconds. The cooldown is cms.auth.otp.resend-cooldown-seconds (overridable
   * via SYSTEM_CONFIG), so a literal here disabled the Resend button for two minutes in environments
   * where the server would have accepted the resend at once — and would have left it enabled if the gap
   * were ever raised above two minutes, sending the citizen into a 429 they were not warned about.
   * Every caller now passes what send-otp / resend-otp returned as resendAfterSeconds; an undefined or
   * zero value means the server applies no gap, so no countdown is shown and the button stays live.
   */
  private startResendTimer(seconds?: number) {
    this.clearResendTimer();
    if (!seconds || seconds <= 0) return;
    this.resendTimer = seconds;
    this.resendInterval = setInterval(() => {
      this.resendTimer--;
      if (this.resendTimer <= 0) this.clearResendTimer();
    }, 1000);
  }

  private clearResendTimer() {
    if (this.resendInterval) { clearInterval(this.resendInterval); this.resendInterval = null; }
    this.resendTimer = 0;
  }
}
