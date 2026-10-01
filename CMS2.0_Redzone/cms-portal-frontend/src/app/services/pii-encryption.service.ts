import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { PublicAuthService } from './public-auth.service';

const PII_FIELDS = ['complainantName', 'complainantEmail', 'complainantPhone',
  'accountNumber', 'complainantAddress', 'name', 'email', 'mobileNumber', 'address', 'pincode'];

@Injectable({ providedIn: 'root' })
export class PiiEncryptionService {

  private http = inject(HttpClient);
  private authService = inject(PublicAuthService);
  private sessionKey: CryptoKey | null = null;
  private rawKeyBase64: string = '';

  async initializeForSession(): Promise<void> {
    const token = this.authService.getToken();
    if (!token) return;

    try {
      const response = await firstValueFrom(
        this.http.post<{ key: string }>(`${environment.apiBaseUrl}/api/v1/citizen/auth/encryption-key`, {
          token,
          sessionId: token
        })
      );

      this.rawKeyBase64 = response.key;
      const keyBytes = Uint8Array.from(atob(response.key), c => c.charCodeAt(0));
      this.sessionKey = await crypto.subtle.importKey(
        'raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt']
      );
    } catch (e) {
      console.error('Failed to initialize encryption key:', e);
      this.sessionKey = null;
    }
  }

  async encryptPiiFields(data: Record<string, any>): Promise<Record<string, any>> {
    if (!this.sessionKey) {
      await this.initializeForSession();
    }

    if (!this.sessionKey) {
      return data;
    }

    const encrypted = { ...data };

    for (const field of PII_FIELDS) {
      if (encrypted[field] && typeof encrypted[field] === 'string' && encrypted[field].trim()) {
        encrypted[field] = await this.encryptField(encrypted[field]);
      }
    }

    return encrypted;
  }

  private async encryptField(plaintext: string): Promise<string> {
    if (!this.sessionKey) return plaintext;

    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encodedText = new TextEncoder().encode(plaintext);

    const ciphertext = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      this.sessionKey,
      encodedText
    );

    const combined = new Uint8Array(iv.length + new Uint8Array(ciphertext).length);
    combined.set(iv);
    combined.set(new Uint8Array(ciphertext), iv.length);

    return 'ENC:' + btoa(String.fromCharCode(...combined));
  }

  clearKey(): void {
    this.sessionKey = null;
    this.rawKeyBase64 = '';
  }

  hasKey(): boolean {
    return this.sessionKey !== null;
  }
}
