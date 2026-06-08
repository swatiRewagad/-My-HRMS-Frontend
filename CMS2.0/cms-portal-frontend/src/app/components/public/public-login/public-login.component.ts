import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { PublicAuthService } from '../../../services/public-auth.service';

@Component({
  selector: 'app-public-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './public-login.component.html',
  styleUrl: './public-login.component.scss'
})
export class PublicLoginComponent {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private authService = inject(PublicAuthService);

  loginMode = signal<'mobile' | 'email'>('mobile');
  mobile = '';
  email = '';
  password = '';
  captchaText = '';
  captchaInput = '';
  otpSent = signal(false);
  otpDigits = ['', '', '', '', '', ''];
  otpVerified = signal(false);
  loginError = '';
  otpError = '';
  resendTimer = 0;
  private resendInterval: any = null;

  constructor() {
    this.generateCaptcha();
    if (this.authService.isSessionValid()) {
      this.router.navigate(['/public']);
    }
  }

  switchMode(mode: 'mobile' | 'email') {
    this.loginMode.set(mode);
    this.loginError = '';
  }

  generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    this.captchaText = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  }

  sendOtp() {
    this.loginError = '';
    if (this.loginMode() === 'mobile') {
      if (!/^[6-9]\d{9}$/.test(this.mobile)) {
        this.loginError = 'Enter a valid 10-digit Indian mobile number starting with 6-9.';
        return;
      }
    } else {
      if (!this.email || !this.email.includes('@')) {
        this.loginError = 'Enter a valid email address.';
        return;
      }
    }
    if (this.captchaInput !== this.captchaText) {
      this.loginError = 'Invalid CAPTCHA. Please try again.';
      this.generateCaptcha();
      this.captchaInput = '';
      return;
    }
    this.otpSent.set(true);
    this.otpDigits = ['', '', '', '', '', ''];
    this.startResendTimer();
  }

  loginWithPassword() {
    this.loginError = '';
    if (!this.email || !this.password) {
      this.loginError = 'Please enter email and password.';
      return;
    }
    if (this.captchaInput !== this.captchaText) {
      this.loginError = 'Invalid CAPTCHA. Please try again.';
      this.generateCaptcha();
      this.captchaInput = '';
      return;
    }
    this.completeLogin(this.email);
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
    this.otpVerified.set(true);
    this.clearResendTimer();
    const identifier = this.loginMode() === 'mobile' ? this.mobile : this.email;
    this.completeLogin(identifier);
  }

  resendOtp() {
    if (this.resendTimer > 0) return;
    this.otpDigits = ['', '', '', '', '', ''];
    this.startResendTimer();
  }

  cancelVerification() {
    this.otpSent.set(false);
    this.otpDigits = ['', '', '', '', '', ''];
    this.otpError = '';
    this.clearResendTimer();
  }

  private completeLogin(identifier: string) {
    const mockToken = 'pub_' + Date.now().toString(36);
    this.authService.login(identifier, mockToken);

    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    this.router.navigateByUrl(returnUrl || '/public');
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
