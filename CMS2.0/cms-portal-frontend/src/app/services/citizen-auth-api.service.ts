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

  sendOtp(mobile: string, captchaToken: string, captchaAnswer: string): Observable<SendOtpResponse> {
    return this.http.post<SendOtpResponse>(`${this.baseUrl}/send-otp`, {
      mobile, captchaToken, captchaAnswer
    }, { withCredentials: true });
  }

  sendOtpViaEmail(mobile: string, email: string, captchaToken: string, captchaAnswer: string): Observable<SendOtpResponse> {
    return this.http.post<SendOtpResponse>(`${this.baseUrl}/send-otp-email`, {
      mobile, email, captchaToken, captchaAnswer
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
