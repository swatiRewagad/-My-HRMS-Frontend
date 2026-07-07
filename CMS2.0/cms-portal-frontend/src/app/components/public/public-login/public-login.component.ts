import { Component, inject, signal, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { PublicAuthService } from '../../../services/public-auth.service';
import { CitizenAuthApiService, CaptchaResponse } from '../../../services/citizen-auth-api.service';

@Component({
  selector: 'app-public-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './public-login.component.html',
  styleUrl: './public-login.component.scss'
})
export class PublicLoginComponent implements OnDestroy {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private authService = inject(PublicAuthService);
  private authApi = inject(CitizenAuthApiService);

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

  loadCaptcha() {
    this.captchaLoading.set(true);
    this.captchaInput = '';
    this.authApi.getCaptcha(this.captchaType()).subscribe({
      next: (data) => {
        this.captchaData.set(data);
        this.captchaLoading.set(false);
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

  sendOtp() {
    this.loginError = '';

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

    this.loading.set(true);
    this.authApi.sendOtp(this.mobile, captcha.token, this.captchaInput.trim()).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpSent.set(true);
        this.otpDigits = ['', '', '', '', '', ''];
        this.devOtpPopulated.set(false);
        if (res.devOtp) {
          this.otpDigits = res.devOtp.split('').slice(0, 6);
          this.devOtpPopulated.set(true);
        }
        this.startResendTimer();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'COOLOFF_ACTIVE') {
          this.startCooloff(body.retryAfterSeconds);
        } else if (body?.error === 'RATE_LIMITED') {
          this.loginError = body.message || 'Too many OTP requests. Try again later.';
        } else if (body?.error === 'INVALID_CAPTCHA') {
          this.loginError = 'Invalid CAPTCHA. Please try again.';
          this.loadCaptcha();
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

    this.loading.set(true);
    this.authApi.sendOtpViaEmail(this.mobile, this.emailForOtp, captcha.token, this.captchaInput.trim()).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.otpSent.set(true);
        this.showEmailFallback.set(false);
        this.otpDigits = ['', '', '', '', '', ''];
        if (res.devOtp) {
          this.otpDigits = res.devOtp.split('').slice(0, 6);
        }
        this.startResendTimer();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const body = err.error;
        if (body?.error === 'EMAIL_NOT_VERIFIED') {
          this.loginError = 'Email not verified. Please verify your email first.';
        } else if (body?.error === 'COOLOFF_ACTIVE') {
          this.startCooloff(body.retryAfterSeconds);
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
        } else if (body?.error === 'OTP_EXPIRED') {
          this.otpError = 'OTP has expired. Please request a new one.';
          this.otpSent.set(false);
        } else if (body?.error === 'MAX_ATTEMPTS') {
          this.otpError = 'Too many incorrect attempts. Please request a new OTP.';
          this.otpSent.set(false);
        } else {
          this.otpError = body?.message || 'Verification failed.';
        }
      }
    });
  }

  resendOtp() {
    if (this.resendTimer > 0) return;
    this.otpSent.set(false);
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.loadCaptcha();
  }

  cancelVerification() {
    this.otpSent.set(false);
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.clearResendTimer();
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

  private startResendTimer() {
    this.clearResendTimer();
    this.resendTimer = 30;
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
