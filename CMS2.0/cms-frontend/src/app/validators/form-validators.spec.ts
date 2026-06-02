import { SecureValidators, ALLOWED_FILE_TYPES, ALLOWED_MIME_TYPES, FILE_MAGIC_BYTES, MAX_FILE_SIZES, INPUT_LIMITS } from './form-validators';

// Helper to simulate Angular AbstractControl
function control(value: any) {
  return { value } as any;
}

describe('SecureValidators', () => {

  // ═══════ secureEmail ═══════
  describe('secureEmail', () => {
    const validator = SecureValidators.secureEmail();

    it('should return null for empty value', () => {
      expect(validator(control(''))).toBeNull();
      expect(validator(control(null))).toBeNull();
    });

    it('should accept valid emails', () => {
      expect(validator(control('user@example.com'))).toBeNull();
      expect(validator(control('test.user+tag@domain.co.in'))).toBeNull();
      expect(validator(control('a@b.c'))).toBeNull();
    });

    it('should reject invalid emails', () => {
      expect(validator(control('notanemail'))).toHaveProperty('email');
      expect(validator(control('@domain.com'))).toHaveProperty('email');
      expect(validator(control('user@'))).toHaveProperty('email');
      expect(validator(control('user @domain.com'))).toHaveProperty('email');
    });

    it('should reject emails exceeding 254 chars', () => {
      const longEmail = 'a'.repeat(250) + '@b.com';
      expect(validator(control(longEmail))).toHaveProperty('email');
    });

    it('should reject local part > 64 chars', () => {
      const longLocal = 'a'.repeat(65) + '@domain.com';
      expect(validator(control(longLocal))).toHaveProperty('email');
    });
  });

  // ═══════ securePhone ═══════
  describe('securePhone', () => {
    const validator = SecureValidators.securePhone();

    it('should return null for empty value', () => {
      expect(validator(control(''))).toBeNull();
      expect(validator(control(null))).toBeNull();
    });

    it('should accept valid Indian mobile numbers', () => {
      expect(validator(control('9876543210'))).toBeNull();
      expect(validator(control('6000000000'))).toBeNull();
      expect(validator(control('7999999999'))).toBeNull();
      expect(validator(control('8123456789'))).toBeNull();
    });

    it('should reject numbers not starting with 6-9', () => {
      expect(validator(control('5876543210'))).toHaveProperty('phone');
      expect(validator(control('1234567890'))).toHaveProperty('phone');
      expect(validator(control('0987654321'))).toHaveProperty('phone');
    });

    it('should reject wrong length', () => {
      expect(validator(control('98765'))).toHaveProperty('phone');
      expect(validator(control('98765432101'))).toHaveProperty('phone');
    });

    it('should strip non-digits before validation', () => {
      expect(validator(control('987-654-3210'))).toBeNull();
      expect(validator(control('+91 9876543210'))).toHaveProperty('phone'); // 12 digits after strip
    });
  });

  // ═══════ securePincode ═══════
  describe('securePincode', () => {
    const validator = SecureValidators.securePincode();

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid pincodes', () => {
      expect(validator(control('110001'))).toBeNull();
      expect(validator(control('560034'))).toBeNull();
      expect(validator(control('999999'))).toBeNull();
    });

    it('should reject pincode starting with 0', () => {
      expect(validator(control('012345'))).toHaveProperty('pincode');
    });

    it('should reject wrong length', () => {
      expect(validator(control('12345'))).toHaveProperty('pincode');
      expect(validator(control('1234567'))).toHaveProperty('pincode');
    });

    it('should reject non-numeric', () => {
      expect(validator(control('12345a'))).toHaveProperty('pincode');
    });
  });

  // ═══════ secureName ═══════
  describe('secureName', () => {
    const validator = SecureValidators.secureName(100);

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid names', () => {
      expect(validator(control('John Doe'))).toBeNull();
      expect(validator(control("O'Brien"))).toBeNull();
      expect(validator(control('Mary-Jane Watson'))).toBeNull();
    });

    it('should accept Indian language names (Devanagari)', () => {
      expect(validator(control('\u0930\u093E\u092E'))).toBeNull(); // राम
    });

    it('should reject too short names', () => {
      expect(validator(control('A'))).toHaveProperty('name');
    });

    it('should reject names exceeding maxLength', () => {
      const longName = 'A'.repeat(101);
      expect(validator(control(longName))).toHaveProperty('name');
    });

    it('should reject names with numbers or special chars', () => {
      expect(validator(control('John123'))).toHaveProperty('name');
      expect(validator(control('Test<script>'))).toHaveProperty('name');
    });
  });

  // ═══════ secureAccountNumber ═══════
  describe('secureAccountNumber', () => {
    const validator = SecureValidators.secureAccountNumber();

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid account numbers', () => {
      expect(validator(control('1234567890'))).toBeNull();
      expect(validator(control('ABCDEF'))).toBeNull();
      expect(validator(control('ABC1234567890'))).toBeNull();
    });

    it('should reject too short', () => {
      expect(validator(control('12345'))).toHaveProperty('accountNumber');
    });

    it('should reject too long', () => {
      expect(validator(control('A'.repeat(21)))).toHaveProperty('accountNumber');
    });

    it('should reject special characters', () => {
      expect(validator(control('1234-5678'))).toHaveProperty('accountNumber');
    });
  });

  // ═══════ secureAmount ═══════
  describe('secureAmount', () => {
    const validator = SecureValidators.secureAmount();

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid amounts', () => {
      expect(validator(control('1000'))).toBeNull();
      expect(validator(control('1000.50'))).toBeNull();
      expect(validator(control('0.01'))).toBeNull();
    });

    it('should accept amounts with currency symbols/commas', () => {
      expect(validator(control('\u20B91,000'))).toBeNull(); // ₹1,000
      expect(validator(control('1,00,000.00'))).toBeNull();
    });

    it('should reject invalid formats', () => {
      expect(validator(control('abc'))).toHaveProperty('amount');
      expect(validator(control('12.345'))).toHaveProperty('amount'); // 3 decimal places
    });

    it('should reject amounts exceeding max', () => {
      expect(validator(control('9999999999999'))).toHaveProperty('amount');
    });
  });

  // ═══════ secureText ═══════
  describe('secureText', () => {
    const validator = SecureValidators.secureText(100);

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept normal text', () => {
      expect(validator(control('This is a valid complaint description.'))).toBeNull();
    });

    it('should reject text exceeding maxLength', () => {
      expect(validator(control('A'.repeat(101)))).toHaveProperty('maxLength');
    });

    it('should reject XSS payloads', () => {
      expect(validator(control('<script>alert(1)</script>'))).toHaveProperty('xss');
    });

    it('should reject SQL injection', () => {
      expect(validator(control("'; DROP TABLE users--"))).toHaveProperty('sqlInjection');
    });
  });

  // ═══════ secureTextarea ═══════
  describe('secureTextarea', () => {
    const validator = SecureValidators.secureTextarea(5000);

    it('should accept long normal text', () => {
      expect(validator(control('A'.repeat(4999)))).toBeNull();
    });

    it('should reject text exceeding max', () => {
      expect(validator(control('A'.repeat(5001)))).toHaveProperty('maxLength');
    });

    it('should reject XSS in textarea', () => {
      expect(validator(control('<iframe src=evil></iframe>'))).toHaveProperty('xss');
    });
  });

  // ═══════ noXss ═══════
  describe('noXss', () => {
    const validator = SecureValidators.noXss();

    it('should return null for safe text', () => {
      expect(validator(control('Normal text'))).toBeNull();
    });

    it('should detect all XSS patterns', () => {
      expect(validator(control('<script>x</script>'))).toHaveProperty('xss');
      expect(validator(control('javascript:evil()'))).toHaveProperty('xss');
      expect(validator(control('onerror=alert(1)'))).toHaveProperty('xss');
      expect(validator(control('<iframe src=x>'))).toHaveProperty('xss');
      expect(validator(control('data:text/html;base64,abc'))).toHaveProperty('xss');
    });
  });

  // ═══════ noSqlInjection ═══════
  describe('noSqlInjection', () => {
    const validator = SecureValidators.noSqlInjection();

    it('should return null for safe text', () => {
      expect(validator(control('Normal complaint text'))).toBeNull();
    });

    it('should detect SQL injection patterns', () => {
      expect(validator(control('UNION SELECT * FROM users'))).toHaveProperty('sqlInjection');
      expect(validator(control('DROP TABLE complaints'))).toHaveProperty('sqlInjection');
      expect(validator(control("' OR 1=1 --"))).toHaveProperty('sqlInjection');
    });
  });

  // ═══════ secureDate ═══════
  describe('secureDate', () => {
    const validator = SecureValidators.secureDate();

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid past dates', () => {
      expect(validator(control('2024-01-15'))).toBeNull();
      expect(validator(control('2000-06-30'))).toBeNull();
    });

    it('should reject future dates', () => {
      expect(validator(control('2099-01-01'))).toHaveProperty('date');
    });

    it('should reject invalid dates', () => {
      expect(validator(control('not-a-date'))).toHaveProperty('date');
    });

    it('should reject very old dates', () => {
      expect(validator(control('1800-01-01'))).toHaveProperty('date');
    });
  });

  // ═══════ secureSelect ═══════
  describe('secureSelect', () => {
    it('should return null for empty value', () => {
      const validator = SecureValidators.secureSelect(['a', 'b']);
      expect(validator(control(''))).toBeNull();
      expect(validator(control(null))).toBeNull();
    });

    it('should accept allowed values', () => {
      const validator = SecureValidators.secureSelect(['banking', 'insurance']);
      expect(validator(control('banking'))).toBeNull();
    });

    it('should reject disallowed values', () => {
      const validator = SecureValidators.secureSelect(['banking', 'insurance']);
      expect(validator(control('hacking'))).toHaveProperty('select');
    });

    it('should reject XSS in select value', () => {
      const validator = SecureValidators.secureSelect();
      expect(validator(control('<script>alert(1)</script>'))).toHaveProperty('xss');
    });
  });

  // ═══════ secureComplaintNumber ═══════
  describe('secureComplaintNumber', () => {
    const validator = SecureValidators.secureComplaintNumber();

    it('should return null for empty', () => {
      expect(validator(control(''))).toBeNull();
    });

    it('should accept valid complaint numbers', () => {
      expect(validator(control('CMS-20260515-100234'))).toBeNull();
    });

    it('should reject special characters', () => {
      expect(validator(control("CMS'; DROP--"))).toHaveProperty('complaintNumber');
    });

    it('should reject if too long', () => {
      expect(validator(control('A'.repeat(31)))).toHaveProperty('complaintNumber');
    });
  });

  // ═══════ Constants ═══════
  describe('Constants', () => {
    it('should have correct file type categories', () => {
      expect(ALLOWED_FILE_TYPES['document']).toContain('pdf');
      expect(ALLOWED_FILE_TYPES['image']).toContain('jpg');
      expect(ALLOWED_FILE_TYPES['archive']).toContain('zip');
    });

    it('should have MIME types for common extensions', () => {
      expect(ALLOWED_MIME_TYPES['pdf']).toBe('application/pdf');
      expect(ALLOWED_MIME_TYPES['jpg']).toBe('image/jpeg');
      expect(ALLOWED_MIME_TYPES['png']).toBe('image/png');
    });

    it('should have magic bytes for known formats', () => {
      expect(FILE_MAGIC_BYTES['pdf']).toEqual([0x25, 0x50, 0x44, 0x46]);
      expect(FILE_MAGIC_BYTES['png']).toEqual([0x89, 0x50, 0x4E, 0x47]);
    });

    it('should have reasonable file size limits', () => {
      expect(MAX_FILE_SIZES['default']).toBe(5 * 1024 * 1024);
      expect(MAX_FILE_SIZES['total']).toBe(50 * 1024 * 1024);
    });

    it('should have input limits', () => {
      expect(INPUT_LIMITS.name).toBe(100);
      expect(INPUT_LIMITS.phone).toBe(10);
      expect(INPUT_LIMITS.pincode).toBe(6);
      expect(INPUT_LIMITS.description).toBe(5000);
    });
  });

  // ═══════ Regex patterns ═══════
  describe('Regex patterns', () => {
    it('PHONE_RE should match valid Indian numbers', () => {
      expect(SecureValidators.PHONE_RE.test('9876543210')).toBe(true);
      expect(SecureValidators.PHONE_RE.test('6000000000')).toBe(true);
      expect(SecureValidators.PHONE_RE.test('5000000000')).toBe(false);
      expect(SecureValidators.PHONE_RE.test('12345')).toBe(false);
    });

    it('PINCODE_RE should match valid Indian pincodes', () => {
      expect(SecureValidators.PINCODE_RE.test('110001')).toBe(true);
      expect(SecureValidators.PINCODE_RE.test('012345')).toBe(false);
    });

    it('EMAIL_RE should validate email format', () => {
      expect(SecureValidators.EMAIL_RE.test('user@domain.com')).toBe(true);
      expect(SecureValidators.EMAIL_RE.test('invalid')).toBe(false);
    });
  });
});
