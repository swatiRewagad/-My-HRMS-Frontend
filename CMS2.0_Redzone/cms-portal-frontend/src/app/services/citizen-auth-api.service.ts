import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CaptchaResponse {
  token: string;
  imageData: string;
  audioQuestion: string;
  type: 'VISUAL' | 'MATH';
}

export interface SendOtpResponse {
  success: boolean;
  message: string;
  sessionId: string;
  expiresInSeconds: number;
  /**
   * Seconds that must pass before a RESEND is accepted, as the SERVER computes it.
   *
   * <p>The component used to disable its own Resend button for a hardcoded 120s. That is a rule the
   * server owns (cms.auth.otp.resend-cooldown-seconds, overridable via SYSTEM_CONFIG), so a literal
   * here means the button stays dead for two minutes in an environment where the server would have
   * accepted the resend immediately — and stays enabled if the window is ever raised above two minutes.
   */
  resendAfterSeconds?: number;
  devOtp?: string;
}

export interface VerifyOtpResponse {
  success: boolean;
  token: string;
  expiresInMinutes: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  cooloffActive?: boolean;
  retryAfterSeconds?: number;
}

@Injectable({ providedIn: 'root' })
export class CitizenAuthApiService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/citizen/auth`;

  getCaptcha(type: 'VISUAL' | 'MATH' = 'VISUAL'): Observable<CaptchaResponse> {
    return this.http.get<CaptchaResponse>(`${this.baseUrl}/captcha`, {
      params: { type },
      withCredentials: true
    });
  }

  sendOtp(mobile: string, captchaToken: string, captchaAnswer: string,
          consentGiven: boolean, locale: string): Observable<SendOtpResponse> {
    return this.http.post<SendOtpResponse>(`${this.baseUrl}/send-otp`, {
      mobile, captchaToken, captchaAnswer, consentGiven: String(consentGiven), locale
    }, { withCredentials: true });
  }

  sendOtpViaEmail(mobile: string, email: string, captchaToken: string, captchaAnswer: string,
                  consentGiven: boolean, locale: string): Observable<SendOtpResponse> {
    return this.http.post<SendOtpResponse>(`${this.baseUrl}/send-otp-email`, {
      mobile, email, captchaToken, captchaAnswer, consentGiven: String(consentGiven), locale
    }, { withCredentials: true });
  }

  /**
   * Requests a FRESH OTP for a mobile that already has a request in flight.
   *
   * <p>Takes no CAPTCHA by design — a citizen who has already solved one and is waiting for a code
   * should not have to solve another to be sent it again. The server only accepts this while an
   * earlier, CAPTCHA-gated request for the same mobile is still recent, and applies the same cooldown
   * and hourly limit as send-otp.
   */
  resendOtp(mobile: string, locale: string): Observable<SendOtpResponse> {
    return this.http.post<SendOtpResponse>(`${this.baseUrl}/resend-otp`, {
      mobile, locale
    }, { withCredentials: true });
  }

  verifyOtp(mobile: string, otp: string, sessionId: string): Observable<VerifyOtpResponse> {
    return this.http.post<VerifyOtpResponse>(`${this.baseUrl}/verify-otp`, {
      mobile, otp, sessionId
    }, { withCredentials: true });
  }

  initiateEmailVerification(mobile: string, email: string): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(`${this.baseUrl}/verify-email`, {
      mobile, email
    });
  }

  validateSession(token: string): Observable<{ valid: boolean }> {
    return this.http.post<{ valid: boolean }>(`${this.baseUrl}/validate-session`, { token });
  }

  logout(token: string): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>(`${this.baseUrl}/logout`, { token });
  }
}
