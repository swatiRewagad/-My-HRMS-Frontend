import { ComplaintParserService } from './complaint-parser.service';

describe('ComplaintParserService', () => {
  let service: ComplaintParserService;

  beforeEach(() => {
    service = new ComplaintParserService();
  });

  describe('parseComplaint - Entity Type Detection', () => {
    it('should detect bank entity type', () => {
      const result = service.parseComplaint('I have a problem with my bank account');
      expect(result['entityType']).toBe('bank');
    });

    it('should detect NBFC entity type', () => {
      const result = service.parseComplaint('I took a loan from NBFC company');
      expect(result['entityType']).toBe('nbfc');
    });

    it('should detect payment entity type', () => {
      const result = service.parseComplaint('My UPI transaction failed');
      expect(result['entityType']).toBe('payment');
    });
  });

  describe('parseComplaint - Category Detection', () => {
    it('should detect ATM/debit category', () => {
      const result = service.parseComplaint('ATM did not dispense cash');
      expect(result['subCategory1']).toBe('atm_debit');
    });

    it('should detect credit card category', () => {
      const result = service.parseComplaint('Unauthorized credit card charge');
      expect(result['subCategory1']).toBe('credit_cards');
    });

    it('should detect internet banking category', () => {
      const result = service.parseComplaint('Internet banking transaction failed');
      expect(result['subCategory1']).toBe('internet_mobile_banking');
    });

    it('should detect loan category', () => {
      const result = service.parseComplaint('My home loan EMI is wrong');
      expect(result['subCategory1']).toBe('loans_advances');
    });

    it('should detect deposit accounts', () => {
      const result = service.parseComplaint('Issue with my fixed deposit maturity');
      expect(result['subCategory1']).toBe('deposit_accounts');
    });

    it('should detect recovery agents', () => {
      const result = service.parseComplaint('Recovery agent is harassing me');
      expect(result['subCategory1']).toBe('recovery_agents');
    });
  });

  describe('parseComplaint - Bank Name Detection', () => {
    it('should detect SBI', () => {
      const result = service.parseComplaint('I have account in SBI');
      expect(result['bankName']).toBe('sbi');
    });

    it('should detect HDFC', () => {
      const result = service.parseComplaint('HDFC bank charged extra fee');
      expect(result['bankName']).toBe('hdfc');
    });

    it('should detect ICICI', () => {
      const result = service.parseComplaint('Issue with ICICI net banking');
      expect(result['bankName']).toBe('icici');
    });

    it('should detect Kotak', () => {
      const result = service.parseComplaint('Kotak Mahindra customer service is poor');
      expect(result['bankName']).toBe('kotak');
    });
  });

  describe('parseComplaint - Regex Extraction', () => {
    it('should extract name from "my name is" pattern', () => {
      const result = service.parseComplaint('My name is Rajesh Kumar. I have a complaint');
      expect(result['name']).toBe('Rajesh Kumar');
    });

    it('should extract phone number', () => {
      const result = service.parseComplaint('Contact me at 9876543210');
      expect(result['mobileNumber']).toBe('9876543210');
    });

    it('should extract phone with +91 prefix', () => {
      const result = service.parseComplaint('Call +91 8765432109');
      expect(result['mobileNumber']).toBe('8765432109');
    });

    it('should extract email', () => {
      const result = service.parseComplaint('Email me at user@example.com for details');
      expect(result['email']).toBe('user@example.com');
    });

    it('should extract pincode', () => {
      const result = service.parseComplaint('I live in area pincode 411001');
      expect(result['pincode']).toBe('411001');
    });

    it('should extract account number', () => {
      const result = service.parseComplaint('Account no 123456789012 was debited');
      expect(result['accountNumber']).toBe('123456789012');
    });

    it('should extract amount', () => {
      const result = service.parseComplaint('Rs. 15,000 was debited wrongly');
      expect(result['amount']).toBe('15000');
    });

    it('should extract amount with ₹ symbol', () => {
      const result = service.parseComplaint('₹ 25000 deducted without authorization');
      expect(result['amount']).toBe('25000');
    });
  });

  describe('parseComplaint - State Detection', () => {
    it('should detect Maharashtra', () => {
      const result = service.parseComplaint('I live in Maharashtra');
      expect(result['state']).toBe('MH');
    });

    it('should detect state from city name', () => {
      const result = service.parseComplaint('I am from Bangalore');
      expect(result['state']).toBe('KA');
    });

    it('should detect Delhi', () => {
      const result = service.parseComplaint('Delhi branch is not responding');
      expect(result['state']).toBe('DL');
    });
  });

  describe('parseComplaint - District Detection', () => {
    it('should detect Mumbai district', () => {
      const result = service.parseComplaint('Mumbai branch ATM issue');
      expect(result['district']).toBe('mumbai');
    });

    it('should detect Pune district', () => {
      const result = service.parseComplaint('Pune SBI branch problem');
      expect(result['district']).toBe('pune');
    });
  });

  describe('parseComplaint - Defaults', () => {
    it('should set facts to original text', () => {
      const text = 'This is my complaint text';
      const result = service.parseComplaint(text);
      expect(result['facts']).toBe(text);
    });

    it('should default complainantCategory to individual', () => {
      const result = service.parseComplaint('Simple complaint');
      expect(result['complainantCategory']).toBe('individual');
    });

    it('should detect business complainant', () => {
      const result = service.parseComplaint('Our company has a complaint');
      expect(result['complainantCategory']).toBe('business');
    });

    it('should map bankName to entityName', () => {
      const result = service.parseComplaint('SBI charged extra');
      expect(result['entityName']).toBe('sbi');
    });

    it('should map subCategory1 to complaintCategory number', () => {
      const result = service.parseComplaint('ATM issue');
      expect(result['complaintCategory']).toBe(1);
    });
  });

  describe('getDictionary', () => {
    it('should return a copy of the dictionary', () => {
      const dict = service.getDictionary();
      expect(dict.length).toBeGreaterThan(0);
      expect(dict[0]).toHaveProperty('keywords');
      expect(dict[0]).toHaveProperty('value');
      expect(dict[0]).toHaveProperty('field');
    });
  });

  describe('addDictionaryEntry', () => {
    it('should add a new entry', () => {
      const before = service.getDictionary().length;
      service.addDictionaryEntry({ keywords: ['test'], value: 'test_val', field: 'testField' });
      expect(service.getDictionary().length).toBe(before + 1);
    });
  });

  describe('removeDictionaryEntry', () => {
    it('should remove entry at index', () => {
      const before = service.getDictionary().length;
      service.removeDictionaryEntry(0);
      expect(service.getDictionary().length).toBe(before - 1);
    });
  });
});
