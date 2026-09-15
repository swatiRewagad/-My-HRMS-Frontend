// @ts-nocheck
import { ComplaintTextProcessorService } from './complaint-text-processor.service';
import { ComplaintParserService } from './complaint-parser.service';

describe('ComplaintTextProcessorService', () => {
  let service: ComplaintTextProcessorService;

  beforeEach(() => {
    const parser = new ComplaintParserService();
    service = new ComplaintTextProcessorService(parser);
  });

  describe('detectLanguage', () => {
    it('should detect English text', () => {
      expect(service.detectLanguage('I have a complaint about my bank')).toBe('English');
    });

    it('should detect Hindi text', () => {
      expect(service.detectLanguage('मेरा नाम राजेश है और मुझे शिकायत करनी है')).toBe('Hindi');
    });

    it('should detect mixed text as Hindi when Hindi chars dominate', () => {
      expect(service.detectLanguage('मेरा ATM से पैसा नहीं मिला')).toBe('Hindi');
    });

    it('should detect English when Latin dominates', () => {
      expect(service.detectLanguage('ATM complaint in Mumbai branch area')).toBe('English');
    });
  });

  describe('process', () => {
    it('should process English text correctly', () => {
      const result = service.process('My name is Rajesh Kumar. SBI account. Phone 9876543210');
      expect(result.language).toBe('English');
      expect(result.rawText).toContain('Rajesh');
      expect(result.fields.name).toBe('Rajesh Kumar');
      expect(result.fields.mobileNumber).toBe('9876543210');
      expect(result.fields.bankName).toBe('sbi');
    });

    it('should include facts field with translated text', () => {
      const text = 'Simple English complaint about HDFC bank';
      const result = service.process(text);
      expect(result.fields.facts).toBe(text);
    });

    it('should extract all expected field keys', () => {
      const result = service.process('Test complaint');
      expect(result.fields).toHaveProperty('name');
      expect(result.fields).toHaveProperty('mobileNumber');
      expect(result.fields).toHaveProperty('email');
      expect(result.fields).toHaveProperty('bankName');
      expect(result.fields).toHaveProperty('accountNumber');
      expect(result.fields).toHaveProperty('branch');
      expect(result.fields).toHaveProperty('state');
      expect(result.fields).toHaveProperty('district');
      expect(result.fields).toHaveProperty('pincode');
      expect(result.fields).toHaveProperty('complaintCategory');
      expect(result.fields).toHaveProperty('facts');
      expect(result.fields).toHaveProperty('disputeAmount');
    });

    it('should translate Hindi to English before parsing', () => {
      const hindiText = 'मेरा नाम राजेश कुमार है। SBI बैंक पुणे शाखा। खाता नंबर 1234567890। फोन 9876543210।';
      const result = service.process(hindiText);
      expect(result.language).toBe('Hindi');
      expect(result.translatedText).toContain('Rajesh');
    });

    it('should return empty fields for undetectable data', () => {
      const result = service.process('Simple text without details');
      expect(result.fields.name).toBe('');
      expect(result.fields.mobileNumber).toBe('');
      expect(result.fields.email).toBe('');
    });
  });
});
