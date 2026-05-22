import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay, map, switchMap, catchError } from 'rxjs';
import { environment } from '../../environments/environment';

export interface OcrResponse {
  rawText: string;
  confidence: number;
  language?: string;
  provider?: string;
}

@Injectable({ providedIn: 'root' })
export class OcrService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  extractText(file: File): Observable<OcrResponse> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<any>(`${this.apiUrl}/ocr/extract`, formData).pipe(
      map(res => ({
        rawText: res.rawText || '',
        confidence: res.confidence || 85,
        language: res.language || 'English',
        provider: res.provider || 'unknown',
      })),
      switchMap(result => {
        if (!result.rawText || result.rawText.trim().length === 0) {
          return of({
            rawText: '[OCR could not extract text from this image. Gemini may be rate-limited or the image is not readable. Please try again.]',
            confidence: 0,
            language: 'unknown',
            provider: result.provider,
          } as OcrResponse);
        }
        return of(result);
      }),
    );
  }

  getProvider(): Observable<string> {
    return this.http.get<any>(`${this.apiUrl}/ocr/provider`).pipe(
      map(res => res.provider),
      catchError(() => of('mock')),
    );
  }

  private getMockResponse(): Observable<OcrResponse> {
    const samples = [
      {
        rawText: `सेवा में,\nशिकायत प्रबंधन प्रणाली, भारतीय रिज़र्व बैंक\n\nविषय: ATM से पैसे नहीं निकले लेकिन खाते से कट गए\n\nमहोदय,\nमेरा नाम राजेश कुमार है। मेरा खाता नंबर 1234567890 है जो SBI पुणे मुख्य शाखा में है। दिनांक 15 अप्रैल 2026 को मैंने ATM से ₹5000 निकालने का प्रयास किया। पैसे नहीं निकले लेकिन मेरे खाते से ₹5000 कट गए।\n\nमेरा फ़ोन नंबर: 9876543210\nपता: शिवाजी नगर, पुणे, महाराष्ट्र - 411001\n\nराजेश कुमार`,
        confidence: 89,
        language: 'Hindi',
      },
      {
        rawText: `To,\nComplaint Management System, Reserve Bank of India\n\nSubject: Unauthorized debit from savings account\n\nSir/Madam,\nMy name is Priya Sharma. I have a savings account (9876543210) in HDFC Bank, Mumbai Fort Branch. On 20th March 2026, Rs. 15000 was debited from my account without my authorization. I suspect fraudulent UPI transaction.\n\nPhone: 8765432109\nAddress: Andheri West, Mumbai, Maharashtra - 400058\n\nRegards,\nPriya Sharma`,
        confidence: 92,
        language: 'English',
      },
      {
        rawText: `To,\nThe Ombudsman, Reserve Bank of India\n\nSubject: Recovery agent harassment and threatening calls\n\nDear Sir/Madam,\nI am Vikram Singh. I hold a personal loan account (7890123456) with Axis Bank, Jaipur MI Road Branch. I have been receiving abusive and threatening calls from recovery agents despite making regular EMI payments. Rs. 8000 extra was charged as penalty which is incorrect.\n\nMobile: 9012345678\nAddress: Malviya Nagar, Jaipur, Rajasthan - 302017\n\nVikram Singh`,
        confidence: 91,
        language: 'English',
      },
    ];

    const sample = samples[Math.floor(Math.random() * samples.length)];
    return of({
      rawText: sample.rawText,
      confidence: sample.confidence,
      language: sample.language,
      provider: 'mock (configure GEMINI_API_KEY for real OCR)',
    }).pipe(delay(2000));
  }
}
