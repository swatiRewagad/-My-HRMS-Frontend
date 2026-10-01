import { Injectable } from '@angular/core';

interface DictionaryEntry {
  keywords: string[];
  value: string;
  field: string;
}

@Injectable({ providedIn: 'root' })
export class ComplaintParserService {

  private dictionary: DictionaryEntry[] = [
    // Entity types
    { keywords: ['bank', 'banking'], value: 'bank', field: 'entityType' },
    { keywords: ['nbfc', 'non-banking', 'finance company'], value: 'nbfc', field: 'entityType' },
    { keywords: ['payment', 'upi', 'wallet', 'paytm', 'phonepe', 'gpay'], value: 'payment', field: 'entityType' },

    // Complaint categories
    { keywords: ['atm', 'debit card', 'debit'], value: 'atm_debit', field: 'subCategory1' },
    { keywords: ['credit card'], value: 'credit_cards', field: 'subCategory1' },
    { keywords: ['internet banking', 'mobile banking', 'net banking', 'online banking', 'upi'], value: 'internet_mobile_banking', field: 'subCategory1' },
    { keywords: ['loan', 'emi', 'advance', 'mortgage', 'home loan', 'personal loan'], value: 'loans_advances', field: 'subCategory1' },
    { keywords: ['pension'], value: 'pension', field: 'subCategory1' },
    { keywords: ['transfer', 'remittance', 'neft', 'rtgs', 'imps'], value: 'remittances', field: 'subCategory1' },
    { keywords: ['staff', 'rude', 'misbehave', 'behaviour', 'behavior'], value: 'staff_behaviour', field: 'subCategory1' },
    { keywords: ['deposit', 'fd', 'fixed deposit', 'recurring'], value: 'deposit_accounts', field: 'subCategory1' },
    { keywords: ['insurance'], value: 'insurance', field: 'subCategory1' },
    { keywords: ['recovery agent', 'harassment', 'threatening', 'collection agent'], value: 'recovery_agents', field: 'subCategory1' },
    { keywords: ['account opening', 'account open', 'open account', 'kyc'], value: 'account_opening', field: 'subCategory1' },

    // Bank names
    { keywords: ['sbi', 'state bank of india', 'state bank'], value: 'sbi', field: 'bankName' },
    { keywords: ['hdfc'], value: 'hdfc', field: 'bankName' },
    { keywords: ['icici'], value: 'icici', field: 'bankName' },
    { keywords: ['axis'], value: 'axis', field: 'bankName' },
    { keywords: ['pnb', 'punjab national'], value: 'pnb', field: 'bankName' },
    { keywords: ['bob', 'bank of baroda'], value: 'bob', field: 'bankName' },
    { keywords: ['canara'], value: 'canara', field: 'bankName' },
    { keywords: ['union bank'], value: 'union', field: 'bankName' },
    { keywords: ['kotak', 'kotak mahindra'], value: 'kotak', field: 'bankName' },
    { keywords: ['yes bank'], value: 'yes_bank', field: 'bankName' },
    { keywords: ['idbi'], value: 'idbi', field: 'bankName' },
    { keywords: ['indusind'], value: 'indusind', field: 'bankName' },
    { keywords: ['bandhan'], value: 'bandhan', field: 'bankName' },
    { keywords: ['rbl'], value: 'rbl', field: 'bankName' },
    { keywords: ['federal bank'], value: 'federal', field: 'bankName' },

    // States
    { keywords: ['maharashtra'], value: 'MH', field: 'state' },
    { keywords: ['delhi'], value: 'DL', field: 'state' },
    { keywords: ['karnataka', 'bangalore', 'bengaluru'], value: 'KA', field: 'state' },
    { keywords: ['tamil nadu', 'chennai'], value: 'TN', field: 'state' },
    { keywords: ['uttar pradesh'], value: 'UP', field: 'state' },
    { keywords: ['gujarat', 'ahmedabad'], value: 'GJ', field: 'state' },
    { keywords: ['rajasthan', 'jaipur'], value: 'RJ', field: 'state' },
    { keywords: ['west bengal', 'kolkata'], value: 'WB', field: 'state' },
    { keywords: ['kerala', 'kochi'], value: 'KL', field: 'state' },
    { keywords: ['telangana', 'hyderabad'], value: 'TG', field: 'state' },
    { keywords: ['punjab', 'amritsar', 'ludhiana'], value: 'PB', field: 'state' },
    { keywords: ['haryana', 'gurugram', 'gurgaon'], value: 'HR', field: 'state' },
    { keywords: ['madhya pradesh', 'bhopal', 'indore'], value: 'MP', field: 'state' },
    { keywords: ['bihar', 'patna'], value: 'BR', field: 'state' },
    { keywords: ['andhra pradesh', 'visakhapatnam'], value: 'AP', field: 'state' },
    { keywords: ['odisha', 'bhubaneswar'], value: 'OD', field: 'state' },
    { keywords: ['mumbai', 'pune', 'nagpur', 'thane'], value: 'MH', field: 'state' },
    { keywords: ['noida', 'lucknow', 'varanasi', 'agra'], value: 'UP', field: 'state' },

    // Districts
    { keywords: ['mumbai'], value: 'mumbai', field: 'district' },
    { keywords: ['pune'], value: 'pune', field: 'district' },
    { keywords: ['nagpur'], value: 'nagpur', field: 'district' },
    { keywords: ['thane'], value: 'thane', field: 'district' },
    { keywords: ['nashik'], value: 'nashik', field: 'district' },
    { keywords: ['aurangabad'], value: 'aurangabad', field: 'district' },
    { keywords: ['solapur'], value: 'solapur', field: 'district' },
    { keywords: ['kolhapur'], value: 'kolhapur', field: 'district' },
    { keywords: ['central delhi'], value: 'central_delhi', field: 'district' },
    { keywords: ['east delhi'], value: 'east_delhi', field: 'district' },
    { keywords: ['new delhi'], value: 'new_delhi', field: 'district' },
    { keywords: ['south delhi'], value: 'south_delhi', field: 'district' },
    { keywords: ['north delhi'], value: 'north_delhi', field: 'district' },
    { keywords: ['west delhi'], value: 'west_delhi', field: 'district' },
    { keywords: ['bengaluru', 'bangalore'], value: 'bengaluru_urban', field: 'district' },
    { keywords: ['mysuru', 'mysore'], value: 'mysuru', field: 'district' },
    { keywords: ['chennai'], value: 'chennai', field: 'district' },
    { keywords: ['coimbatore'], value: 'coimbatore', field: 'district' },
    { keywords: ['lucknow'], value: 'lucknow', field: 'district' },
    { keywords: ['noida'], value: 'noida', field: 'district' },
    { keywords: ['ahmedabad'], value: 'ahmedabad', field: 'district' },
    { keywords: ['surat'], value: 'surat', field: 'district' },
    { keywords: ['jaipur'], value: 'jaipur', field: 'district' },
    { keywords: ['kolkata'], value: 'kolkata', field: 'district' },
    { keywords: ['kochi'], value: 'kochi', field: 'district' },
    { keywords: ['hyderabad'], value: 'hyderabad', field: 'district' },
    { keywords: ['bhopal'], value: 'bhopal', field: 'district' },
    { keywords: ['indore'], value: 'indore', field: 'district' },
    { keywords: ['patna'], value: 'patna', field: 'district' },

    // Complainant category
    { keywords: ['company', 'firm', 'business', 'organization', 'corporate'], value: 'business', field: 'complainantCategory' },
    { keywords: ['individual', 'personal', 'myself', 'i am'], value: 'individual', field: 'complainantCategory' },
  ];

  parseComplaint(text: string): Record<string, any> {
    const result: Record<string, any> = {};
    const lower = text.toLowerCase();

    // Extract using dictionary
    for (const entry of this.dictionary) {
      if (result[entry.field]) continue;
      for (const keyword of entry.keywords) {
        if (lower.includes(keyword)) {
          result[entry.field] = entry.value;
          break;
        }
      }
    }

    // Regex-based extraction
    const nameMatch = text.match(/(?:my name is|i am|name[:\s]+)\s*([a-zA-Z]+(?: [a-zA-Z]+){0,3})/i);
    if (nameMatch) {
      let name = nameMatch[1].trim();
      // Remove leading "is" if accidentally captured
      name = name.replace(/^is\s+/i, '');
      result['name'] = name.replace(/\b\w/g, c => c.toUpperCase());
    }

    const phoneMatch = text.match(/(?:\+91[\s-]?)?([6-9]\d{9})/);
    if (phoneMatch) result['mobileNumber'] = phoneMatch[1];

    const emailMatch = text.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/);
    if (emailMatch) result['email'] = emailMatch[0];

    const pincodeMatch = text.match(/(?:pin(?:code)?[\s:-]*)?(\b[1-9]\d{5}\b)/);
    if (pincodeMatch) result['pincode'] = pincodeMatch[1];

    const accountMatch = text.match(/(?:account(?:\s*(?:no|number|num|#)?)?[\s.:\-()]*?)(\d{9,18})/i);
    if (accountMatch) result['accountNumber'] = accountMatch[1];

    // Branch extraction: "in SBI Pune branch" or "branch: Pune"
    const branchMatch = text.match(/(?:in\s+\w+\s+)([A-Z][a-z]+(?:\s[A-Z][a-z]+)*)\s+branch/i)
      || text.match(/branch[\s:.-]+([A-Za-z\s]+?)(?:\.|,|$)/i);
    if (branchMatch) result['branch'] = branchMatch[1].trim();

    const amountMatch = text.match(/(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{2})?)/i);
    if (amountMatch) result['amount'] = amountMatch[1].replace(/,/g, '');

    // Map bankName to entityName for form compatibility
    if (result['bankName']) {
      result['entityName'] = result['bankName'];
    }

    // Map subCategory1 to complaintCategory ID
    const categoryMap: Record<string, number> = {
      'atm_debit': 1,
      'credit_cards': 2,
      'internet_mobile_banking': 3,
      'loans_advances': 5,
      'deposit_accounts': 6,
      'pension': 7,
      'remittances': 8,
      'insurance': 9,
      'account_opening': 10,
      'staff_behaviour': 10,
      'recovery_agents': 10,
    };
    if (result['subCategory1'] && !result['complaintCategory']) {
      result['complaintCategory'] = categoryMap[result['subCategory1']] || null;
    }

    // Use the full text as complaint facts
    result['facts'] = text;

    // Default complainant category
    if (!result['complainantCategory']) {
      result['complainantCategory'] = 'individual';
    }

    return result;
  }

  getDictionary(): DictionaryEntry[] {
    return [...this.dictionary];
  }

  addDictionaryEntry(entry: DictionaryEntry): void {
    this.dictionary.push(entry);
  }

  removeDictionaryEntry(index: number): void {
    this.dictionary.splice(index, 1);
  }
}
