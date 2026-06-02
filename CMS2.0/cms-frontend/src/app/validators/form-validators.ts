import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export class SecureValidators {

  static readonly EMAIL_RE = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
  static readonly PHONE_RE = /^[6-9]\d{9}$/;
  static readonly PINCODE_RE = /^[1-9]\d{5}$/;
  static readonly NAME_RE = /^[a-zA-Z\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\s.',-]+$/;
  static readonly ACCOUNT_NUMBER_RE = /^[a-zA-Z0-9]{6,20}$/;
  static readonly AMOUNT_RE = /^\d{1,12}(\.\d{1,2})?$/;
  static readonly COMPLAINT_NUMBER_RE = /^[A-Z]{2,5}-\d{8}-[A-Z0-9]{6,12}$/;
  static readonly ALPHANUMERIC_RE = /^[a-zA-Z0-9\s\-_.]+$/;

  private static readonly XSS_PATTERNS = [
    /<script/i,
    /javascript\s*:/i,
    /on\w+\s*=/i,
    /<\s*(iframe|object|embed|svg|img)\b/i,
    /data\s*:[^,]*;base64/i,
  ];

  private static readonly SQL_PATTERNS = [
    /(\bUNION\b\s+\bSELECT\b|\bDROP\b\s+\bTABLE\b|\bINSERT\b\s+\bINTO\b)/i,
    /(--|;)\s*(SELECT|DROP|DELETE|UPDATE|INSERT|ALTER|EXEC)/i,
    /'\s*(OR|AND)\s+\d+\s*=\s*\d+/i,
  ];

  static secureEmail(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (value.length > 254) return { email: 'Email exceeds maximum length' };
      if (!SecureValidators.EMAIL_RE.test(value)) return { email: 'Invalid email format' };
      const [local, domain] = value.split('@');
      if (local.length > 64) return { email: 'Email local part too long' };
      if (domain && domain.length > 253) return { email: 'Email domain too long' };
      return null;
    };
  }

  static securePhone(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      const cleaned = value.replace(/\D/g, '');
      if (cleaned.length !== 10) return { phone: 'Phone must be 10 digits' };
      if (!SecureValidators.PHONE_RE.test(cleaned)) return { phone: 'Invalid Indian mobile number' };
      return null;
    };
  }

  static securePincode(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (!SecureValidators.PINCODE_RE.test(value)) return { pincode: 'Invalid pincode (6 digits, cannot start with 0)' };
      return null;
    };
  }

  static secureName(maxLength = 100): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (value.length > maxLength) return { name: `Name exceeds ${maxLength} characters` };
      if (value.length < 2) return { name: 'Name must be at least 2 characters' };
      if (!SecureValidators.NAME_RE.test(value)) return { name: 'Name contains invalid characters' };
      return null;
    };
  }

  static secureAccountNumber(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (!SecureValidators.ACCOUNT_NUMBER_RE.test(value)) return { accountNumber: 'Invalid account number format' };
      return null;
    };
  }

  static secureAmount(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      const cleaned = value.replace(/[₹,\s]/g, '');
      if (!SecureValidators.AMOUNT_RE.test(cleaned)) return { amount: 'Invalid amount format' };
      const num = parseFloat(cleaned);
      if (num < 0) return { amount: 'Amount cannot be negative' };
      if (num > 999999999999) return { amount: 'Amount exceeds maximum limit' };
      return null;
    };
  }

  static secureText(maxLength = 500): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (value.length > maxLength) return { maxLength: `Exceeds ${maxLength} characters` };
      if (SecureValidators.containsXss(value)) return { xss: 'Input contains potentially unsafe content' };
      if (SecureValidators.containsSqlInjection(value)) return { sqlInjection: 'Input contains invalid characters' };
      return null;
    };
  }

  static secureTextarea(maxLength = 5000): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (value.length > maxLength) return { maxLength: `Exceeds ${maxLength} characters` };
      if (SecureValidators.containsXss(value)) return { xss: 'Input contains potentially unsafe content' };
      return null;
    };
  }

  static noXss(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (SecureValidators.containsXss(value)) return { xss: 'Input contains potentially unsafe content' };
      return null;
    };
  }

  static noSqlInjection(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      if (SecureValidators.containsSqlInjection(value)) return { sqlInjection: 'Input contains invalid characters' };
      return null;
    };
  }

  static secureDate(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      const date = new Date(value);
      if (isNaN(date.getTime())) return { date: 'Invalid date' };
      const now = new Date();
      const minDate = new Date('1900-01-01');
      if (date > now) return { date: 'Date cannot be in the future' };
      if (date < minDate) return { date: 'Date is too far in the past' };
      return null;
    };
  }

  static secureSelect(allowedValues?: any[]): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value && value !== 0) return null;
      if (allowedValues && !allowedValues.includes(value)) {
        return { select: 'Invalid selection' };
      }
      if (typeof value === 'string' && SecureValidators.containsXss(value)) {
        return { xss: 'Invalid selection value' };
      }
      return null;
    };
  }

  static secureComplaintNumber(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      const cleaned = value.replace(/[^a-zA-Z0-9\-]/g, '');
      if (cleaned !== value) return { complaintNumber: 'Contains invalid characters' };
      if (value.length > 30) return { complaintNumber: 'Too long' };
      return null;
    };
  }

  static fileSize(maxSizeBytes: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      return null; // File size validated in component
    };
  }

  private static containsXss(value: string): boolean {
    return SecureValidators.XSS_PATTERNS.some(pattern => pattern.test(value));
  }

  private static containsSqlInjection(value: string): boolean {
    return SecureValidators.SQL_PATTERNS.some(pattern => pattern.test(value));
  }
}

export const ALLOWED_FILE_TYPES: Record<string, string[]> = {
  document: ['pdf', 'doc', 'docx', 'txt', 'csv', 'xls', 'xlsx'],
  image: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'],
  archive: ['zip', 'rar', '7z'],
};

export const ALLOWED_MIME_TYPES: Record<string, string> = {
  'pdf': 'application/pdf',
  'doc': 'application/msword',
  'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'xls': 'application/vnd.ms-excel',
  'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'txt': 'text/plain',
  'csv': 'text/csv',
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'png': 'image/png',
  'gif': 'image/gif',
  'bmp': 'image/bmp',
  'webp': 'image/webp',
  'zip': 'application/zip',
};

export const FILE_MAGIC_BYTES: Record<string, number[]> = {
  'pdf': [0x25, 0x50, 0x44, 0x46],
  'png': [0x89, 0x50, 0x4E, 0x47],
  'jpg': [0xFF, 0xD8, 0xFF],
  'jpeg': [0xFF, 0xD8, 0xFF],
  'gif': [0x47, 0x49, 0x46, 0x38],
  'zip': [0x50, 0x4B, 0x03, 0x04],
  'doc': [0xD0, 0xCF, 0x11, 0xE0],
  'xls': [0xD0, 0xCF, 0x11, 0xE0],
  'bmp': [0x42, 0x4D],
};

export const MAX_FILE_SIZES: Record<string, number> = {
  default: 5 * 1024 * 1024,       // 5MB
  image: 10 * 1024 * 1024,        // 10MB
  document: 25 * 1024 * 1024,     // 25MB
  total: 50 * 1024 * 1024,        // 50MB total per complaint
};

export const INPUT_LIMITS = {
  name: 100,
  email: 254,
  phone: 10,
  pincode: 6,
  address: 500,
  description: 5000,
  subject: 200,
  accountNumber: 20,
  cardNumber: 19,
  transactionRef: 50,
  walletName: 100,
  complaintNumber: 30,
  amount: 15,
};
