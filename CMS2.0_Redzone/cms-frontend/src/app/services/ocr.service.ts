import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

export interface OcrResponse {
  rawText: string;
  confidence: number;
}

@Injectable({ providedIn: 'root' })
export class OcrService {

  extractText(_file: File): Observable<OcrResponse> {
    const rawText = this.getMockOcrText();
    const confidence = 87 + Math.floor(Math.random() * 10);
    return of({ rawText, confidence }).pipe(delay(2500));
  }

  private getMockOcrText(): string {
    const samples = [
      `सेवा में,
शिकायत प्रबंधन प्रणाली, भारतीय रिज़र्व बैंक

विषय: ATM से पैसे नहीं निकले लेकिन खाते से कट गए

महोदय,
मेरा नाम राजेश कुमार है। मेरा खाता नंबर 1234567890 है जो SBI पुणे मुख्य शाखा में है। दिनांक 15 अप्रैल 2026 को मैंने ATM से ₹5000 निकालने का प्रयास किया। पैसे नहीं निकले लेकिन मेरे खाते से ₹5000 कट गए। मैंने बैंक में शिकायत की लेकिन कोई जवाब नहीं मिला।

मेरा फ़ोन नंबर: 9876543210
पता: शिवाजी नगर, पुणे, महाराष्ट्र - 411001

धन्यवाद,
राजेश कुमार`,

      `सेवा में,
लोकपाल कार्यालय, भारतीय रिज़र्व बैंक

विषय: क्रेडिट कार्ड पर अनधिकृत शुल्क

महोदय,
मेरा नाम सुनीता देवी है। मेरा HDFC बैंक में खाता है, खाता नंबर 5678901234, मुंबई अंधेरी शाखा। मेरे क्रेडिट कार्ड पर ₹12000 का अनधिकृत लेनदेन हुआ है दिनांक 10 मार्च 2026 को। मैंने बैंक को सूचित किया लेकिन 45 दिन बीत जाने के बाद भी कोई समाधान नहीं मिला।

फ़ोन: 7654321098
ईमेल: sunita.devi@gmail.com
पता: वर्सोवा, अंधेरी पश्चिम, मुंबई, महाराष्ट्र - 400061

सुनीता देवी`,

      `सेवा में,
शिकायत प्रबंधन प्रणाली, RBI

विषय: लोन EMI में गलत ब्याज दर

महोदय,
मेरा नाम अमित वर्मा है। मैंने ICICI बैंक लखनऊ शाखा से होम लोन लिया है। खाता नंबर 3456789012। बैंक ने मेरी EMI में ब्याज दर 8.5% से बढ़ाकर 11% कर दी बिना किसी सूचना के। ₹25000 अतिरिक्त वसूला गया। कृपया मेरी शिकायत दर्ज करें।

फ़ोन नंबर: 8123456789
पता: गोमती नगर, लखनऊ, उत्तर प्रदेश - 226010

अमित वर्मा`,

      `To,
Complaint Management System, Reserve Bank of India

Subject: Unauthorized debit from savings account

Sir/Madam,
My name is Priya Sharma. I have a savings account (9876543210) in HDFC Bank, Mumbai Fort Branch. On 20th March 2026, Rs. 15000 was debited from my account without my authorization. I suspect fraudulent UPI transaction. I have already complained to the bank on 22nd March but no resolution received.

Phone: 8765432109
Address: Andheri West, Mumbai, Maharashtra - 400058
Email: priya.sharma@email.com

Regards,
Priya Sharma`,

      `To,
The Ombudsman, Reserve Bank of India

Subject: Recovery agent harassment and threatening calls

Dear Sir/Madam,
I am Vikram Singh. I hold a personal loan account (7890123456) with Axis Bank, Jaipur MI Road Branch. I have been receiving abusive and threatening calls from recovery agents of the bank despite making regular EMI payments. Rs. 8000 extra was charged as penalty which is incorrect. I request your intervention.

Mobile: 9012345678
Address: Malviya Nagar, Jaipur, Rajasthan - 302017

Vikram Singh`,
    ];

    return samples[Math.floor(Math.random() * samples.length)];
  }
}
