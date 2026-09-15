import { Injectable } from '@angular/core';
import { ComplaintParserService } from './complaint-parser.service';

export interface ProcessedComplaint {
  rawText: string;
  language: string;
  translatedText: string;
  fields: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class ComplaintTextProcessorService {

  constructor(private parser: ComplaintParserService) {}

  process(rawText: string): ProcessedComplaint {
    const language = this.detectLanguage(rawText);
    const translatedText = language === 'Hindi' ? this.translateHindiToEnglish(rawText) : rawText;
    const parsed = this.parser.parseComplaint(translatedText);

    const fields: Record<string, string> = {
      name: parsed['name'] || '',
      mobileNumber: parsed['mobileNumber'] || '',
      email: parsed['email'] || '',
      bankName: parsed['bankName'] || '',
      accountNumber: parsed['accountNumber'] || '',
      branch: parsed['branch'] || '',
      state: parsed['state'] || '',
      district: parsed['district'] || '',
      pincode: parsed['pincode'] || '',
      address: parsed['address'] || '',
      complaintCategory: parsed['subCategory1'] || '',
      facts: translatedText,
      disputeAmount: parsed['amount'] || '',
    };

    return { rawText, language, translatedText, fields };
  }

  detectLanguage(text: string): string {
    const hindiChars = (text.match(/[\u0900-\u097F]/g) || []).length;
    const latinChars = (text.match(/[a-zA-Z]/g) || []).length;
    if (hindiChars > latinChars) return 'Hindi';
    if (hindiChars > 0 && hindiChars > latinChars * 0.3) return 'Hindi';
    return 'English';
  }

  private translateHindiToEnglish(hindiText: string): string {
    const extracted: Record<string, string> = {};

    const nameMatch = hindiText.match(/(?:मेरा नाम|नाम)\s+([^\s।,.]+(?:\s+[^\s।,.]+){0,2})\s+(?:है|हैं)/);
    if (nameMatch) extracted['name'] = this.transliterateHindi(nameMatch[1]);

    const phoneMatch = hindiText.match(/(?:फ़?ोन(?:\s*नंबर)?|मोबाइल)[\s:]*(\d{10})/);
    if (phoneMatch) extracted['phone'] = phoneMatch[1];

    const accountMatch = hindiText.match(/(?:खाता(?:\s*नंबर)?|खाता\s*संख्या)[\s:]*(\d{7,18})/);
    if (accountMatch) extracted['account'] = accountMatch[1];

    const amountMatch = hindiText.match(/[₹Rs.]\s*([\d,]+)/);
    if (amountMatch) extracted['amount'] = amountMatch[1].replace(/,/g, '');

    const pincodeMatch = hindiText.match(/(\d{6})(?:\s|$|।)/);
    if (pincodeMatch) extracted['pincode'] = pincodeMatch[1];

    const emailMatch = hindiText.match(/(?:ईमेल|email)[\s:]*([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/i);
    if (emailMatch) extracted['email'] = emailMatch[0].replace(/^(?:ईमेल|email)[\s:]*/i, '');

    const bankMap: Record<string, string> = {
      'SBI': 'SBI', 'HDFC': 'HDFC', 'ICICI': 'ICICI', 'Axis': 'Axis',
      'PNB': 'PNB', 'Kotak': 'Kotak', 'Canara': 'Canara', 'BOB': 'Bank of Baroda',
    };
    for (const [key, val] of Object.entries(bankMap)) {
      if (hindiText.includes(key)) { extracted['bank'] = val; break; }
    }

    const subjectMap: Record<string, string> = {
      'ATM': 'ATM cash not dispensed but account debited',
      'क्रेडिट कार्ड': 'Unauthorized credit card charges',
      'लोन': 'Incorrect loan interest rate / EMI overcharge',
      'EMI': 'Incorrect loan interest rate / EMI overcharge',
      'UPI': 'Unauthorized UPI transaction',
      'अनधिकृत': 'Unauthorized transaction on account',
    };
    let subject = 'Complaint regarding banking services';
    for (const [key, val] of Object.entries(subjectMap)) {
      if (hindiText.includes(key)) { subject = val; break; }
    }

    const cityMap: Record<string, { en: string; state: string; district: string }> = {
      'पुणे': { en: 'Pune', state: 'Maharashtra', district: 'Pune' },
      'मुंबई': { en: 'Mumbai', state: 'Maharashtra', district: 'Mumbai' },
      'दिल्ली': { en: 'Delhi', state: 'Delhi', district: 'New Delhi' },
      'लखनऊ': { en: 'Lucknow', state: 'Uttar Pradesh', district: 'Lucknow' },
      'जयपुर': { en: 'Jaipur', state: 'Rajasthan', district: 'Jaipur' },
      'बेंगलुरु': { en: 'Bengaluru', state: 'Karnataka', district: 'Bengaluru Urban' },
      'चेन्नई': { en: 'Chennai', state: 'Tamil Nadu', district: 'Chennai' },
      'कोलकाता': { en: 'Kolkata', state: 'West Bengal', district: 'Kolkata' },
      'हैदराबाद': { en: 'Hyderabad', state: 'Telangana', district: 'Hyderabad' },
      'अंधेरी': { en: 'Andheri', state: 'Maharashtra', district: 'Mumbai' },
    };
    let city = '', state = '', district = '';
    for (const [hindi, info] of Object.entries(cityMap)) {
      if (hindiText.includes(hindi)) {
        city = info.en; state = info.state; district = info.district; break;
      }
    }

    const stateHindiMap: Record<string, string> = {
      'महाराष्ट्र': 'Maharashtra', 'उत्तर प्रदेश': 'Uttar Pradesh', 'राजस्थान': 'Rajasthan',
      'कर्नाटक': 'Karnataka', 'तमिलनाडु': 'Tamil Nadu', 'गुजरात': 'Gujarat',
      'मध्य प्रदेश': 'Madhya Pradesh', 'बिहार': 'Bihar', 'पंजाब': 'Punjab',
      'केरल': 'Kerala', 'दिल्ली': 'Delhi',
    };
    if (!state) {
      for (const [hindi, en] of Object.entries(stateHindiMap)) {
        if (hindiText.includes(hindi)) { state = en; break; }
      }
    }

    let branch = city ? `${city} Branch` : '';
    const branchContext = hindiText.match(/([^\s।,]+(?:\s+[^\s।,]+){0,2})\s+(?:शाखा|ब्रांच)/);
    if (branchContext) {
      const branchName = this.transliterateHindi(branchContext[1]);
      branch = `${branchName} Branch`;
    }

    const addressMatch = hindiText.match(/पता[\s:]*([^।\n]+)/);
    let address = '';
    if (addressMatch) {
      address = this.transliterateHindiAddress(addressMatch[1].trim());
    }

    const lines: string[] = [];
    lines.push(`Subject: ${subject}`);
    lines.push('');
    if (extracted['name']) lines.push(`My name is ${extracted['name']}.`);
    if (extracted['bank']) {
      let bankLine = `I have an account in ${extracted['bank']} Bank`;
      if (branch) bankLine += `, ${branch}`;
      if (extracted['account']) bankLine += `. Account number ${extracted['account']}`;
      lines.push(bankLine + '.');
    }
    if (extracted['amount']) {
      lines.push(`Rs. ${extracted['amount']} is involved in this complaint. ${subject}.`);
    }
    lines.push('I have complained to the bank but have not received any satisfactory response.');
    if (extracted['phone']) lines.push(`Phone: ${extracted['phone']}`);
    if (extracted['email']) lines.push(`Email: ${extracted['email']}`);
    if (address) {
      lines.push(`Address: ${address}${state ? ', ' + state : ''}${extracted['pincode'] ? ' - ' + extracted['pincode'] : ''}`);
    } else if (city) {
      lines.push(`Address: ${city}${state ? ', ' + state : ''}${extracted['pincode'] ? ' - ' + extracted['pincode'] : ''}`);
    }

    return lines.join('\n');
  }

  private transliterateHindi(text: string): string {
    const nameMap: Record<string, string> = {
      'राजेश': 'Rajesh', 'कुमार': 'Kumar', 'सुनीता': 'Sunita', 'देवी': 'Devi',
      'अमित': 'Amit', 'वर्मा': 'Verma', 'प्रिया': 'Priya', 'शर्मा': 'Sharma',
      'विक्रम': 'Vikram', 'सिंह': 'Singh', 'मोहन': 'Mohan', 'गुप्ता': 'Gupta',
      'अनिल': 'Anil', 'पटेल': 'Patel', 'रवि': 'Ravi', 'यादव': 'Yadav',
      'संजय': 'Sanjay', 'मिश्रा': 'Mishra', 'दीपक': 'Deepak', 'जोशी': 'Joshi',
      'सुरेश': 'Suresh', 'त्रिपाठी': 'Tripathi', 'पुणे': 'Pune', 'मुंबई': 'Mumbai',
      'लखनऊ': 'Lucknow', 'दिल्ली': 'Delhi', 'जयपुर': 'Jaipur', 'मुख्य': 'Main',
      'अंधेरी': 'Andheri', 'पश्चिम': 'West', 'गोमती': 'Gomti', 'नगर': 'Nagar',
    };
    const words = text.split(/\s+/);
    return words.map(w => nameMap[w] || w).join(' ');
  }

  private transliterateHindiAddress(text: string): string {
    const map: Record<string, string> = {
      'शिवाजी': 'Shivaji', 'नगर': 'Nagar', 'पुणे': 'Pune', 'मुंबई': 'Mumbai',
      'महाराष्ट्र': 'Maharashtra', 'दिल्ली': 'Delhi', 'लखनऊ': 'Lucknow',
      'उत्तर': 'Uttar', 'प्रदेश': 'Pradesh', 'राजस्थान': 'Rajasthan',
      'गोमती': 'Gomti', 'वर्सोवा': 'Versova', 'अंधेरी': 'Andheri', 'पश्चिम': 'West',
      'मालवीय': 'Malviya', 'जयपुर': 'Jaipur',
    };
    const words = text.split(/[\s,]+/);
    return words.map(w => {
      const clean = w.replace(/[-\d]/g, '').trim();
      return map[clean] || w;
    }).filter(w => w && !/^\d{6}$/.test(w)).join(' ');
  }
}
