import { InputSanitizerService } from './input-sanitizer.service';

describe('InputSanitizerService', () => {
  let service: InputSanitizerService;

  beforeEach(() => {
    service = new InputSanitizerService();
  });

  // ═══════ stripHtml ═══════
  describe('stripHtml', () => {
    it('should return empty string for null/undefined/empty', () => {
      expect(service.stripHtml('')).toBe('');
      expect(service.stripHtml(null as any)).toBe('');
      expect(service.stripHtml(undefined as any)).toBe('');
    });

    it('should strip HTML tags', () => {
      expect(service.stripHtml('<b>bold</b>')).toBe('bold');
      expect(service.stripHtml('<div class="x">text</div>')).toBe('text');
    });

    it('should strip script tags with content', () => {
      expect(service.stripHtml('<script>alert("xss")</script>Hello')).toBe('Hello');
      expect(service.stripHtml('<SCRIPT>var x=1;</SCRIPT>safe')).toBe('safe');
    });

    it('should strip event handlers', () => {
      expect(service.stripHtml('text onclick=alert(1) more')).toBe('text alert(1) more');
      expect(service.stripHtml('onmouseover=evil() test')).toBe('evil() test');
    });

    it('should strip javascript: URIs', () => {
      expect(service.stripHtml('javascript:alert(1)')).toBe('alert(1)');
    });

    it('should strip data: URIs with base64', () => {
      expect(service.stripHtml('data:text/html;base64,PHNjcmlwdD4=')).toBe(',PHNjcmlwdD4=');
    });

    it('should keep normal text unchanged', () => {
      expect(service.stripHtml('Hello World 123')).toBe('Hello World 123');
      expect(service.stripHtml('user@email.com')).toBe('user@email.com');
    });
  });

  // ═══════ escapeHtml ═══════
  describe('escapeHtml', () => {
    it('should return empty string for falsy input', () => {
      expect(service.escapeHtml('')).toBe('');
    });

    it('should escape & < > " \' ` /', () => {
      expect(service.escapeHtml('&')).toBe('&amp;');
      expect(service.escapeHtml('<')).toBe('&lt;');
      expect(service.escapeHtml('>')).toBe('&gt;');
      expect(service.escapeHtml('"')).toBe('&quot;');
      expect(service.escapeHtml("'")).toBe('&#x27;');
      expect(service.escapeHtml('`')).toBe('&#96;');
      expect(service.escapeHtml('/')).toBe('&#x2F;');
    });

    it('should escape multiple chars in string', () => {
      expect(service.escapeHtml('<script>alert("xss")</script>')).toBe(
        '&lt;script&gt;alert(&quot;xss&quot;)&lt;&#x2F;script&gt;'
      );
    });

    it('should not modify safe strings', () => {
      expect(service.escapeHtml('Hello World')).toBe('Hello World');
    });
  });

  // ═══════ sanitizeText ═══════
  describe('sanitizeText', () => {
    it('should return empty for empty/null input', () => {
      expect(service.sanitizeText('')).toBe('');
      expect(service.sanitizeText(null as any)).toBe('');
    });

    it('should strip HTML and trim', () => {
      expect(service.sanitizeText('  <b>Hello</b>  ')).toBe('Hello');
    });

    it('should remove null bytes', () => {
      expect(service.sanitizeText('Hello\x00World')).toBe('HelloWorld');
      expect(service.sanitizeText('test%00bad')).toBe('testbad');
    });

    it('should remove control characters', () => {
      expect(service.sanitizeText('Hello\x01\x02\x03World')).toBe('HelloWorld');
    });

    it('should preserve normal text', () => {
      expect(service.sanitizeText('Normal text with spaces')).toBe('Normal text with spaces');
    });
  });

  // ═══════ sanitizeFileName ═══════
  describe('sanitizeFileName', () => {
    it('should return empty for empty input', () => {
      expect(service.sanitizeFileName('')).toBe('');
    });

    it('should remove path traversal sequences', () => {
      expect(service.sanitizeFileName('../../../etc/passwd')).not.toContain('..');
      expect(service.sanitizeFileName('..\\..\\windows\\system32')).not.toContain('..');
    });

    it('should replace special characters with underscore', () => {
      const result = service.sanitizeFileName('file<name>|test.pdf');
      expect(result).not.toContain('<');
      expect(result).not.toContain('>');
      expect(result).not.toContain('|');
    });

    it('should replace spaces with underscore', () => {
      expect(service.sanitizeFileName('my file name.pdf')).toBe('my_file_name.pdf');
    });

    it('should prepend underscore to dotfiles', () => {
      expect(service.sanitizeFileName('.htaccess')).toBe('_.htaccess');
    });

    it('should truncate filenames longer than 255 chars', () => {
      const longName = 'a'.repeat(300) + '.pdf';
      const result = service.sanitizeFileName(longName);
      expect(result.length).toBeLessThanOrEqual(255);
      expect(result).toContain('.pdf');
    });

    it('should keep valid filenames unchanged', () => {
      expect(service.sanitizeFileName('document.pdf')).toBe('document.pdf');
      expect(service.sanitizeFileName('image_01.jpg')).toBe('image_01.jpg');
    });
  });

  // ═══════ hasSqlInjectionPattern ═══════
  describe('hasSqlInjectionPattern', () => {
    it('should return false for empty input', () => {
      expect(service.hasSqlInjectionPattern('')).toBe(false);
      expect(service.hasSqlInjectionPattern(null as any)).toBe(false);
    });

    it('should detect SELECT statements', () => {
      expect(service.hasSqlInjectionPattern("'; SELECT * FROM users--")).toBe(true);
    });

    it('should detect DROP TABLE', () => {
      expect(service.hasSqlInjectionPattern("DROP TABLE users")).toBe(true);
    });

    it('should detect UNION attacks', () => {
      expect(service.hasSqlInjectionPattern("1 UNION SELECT password FROM admin")).toBe(true);
    });

    it('should detect OR 1=1', () => {
      expect(service.hasSqlInjectionPattern("' OR 1=1 --")).toBe(true);
    });

    it('should not flag normal text', () => {
      expect(service.hasSqlInjectionPattern('My complaint about service')).toBe(false);
      expect(service.hasSqlInjectionPattern('Selection of documents')).toBe(false);
    });
  });

  // ═══════ hasPathTraversal ═══════
  describe('hasPathTraversal', () => {
    it('should return false for empty input', () => {
      expect(service.hasPathTraversal('')).toBe(false);
    });

    it('should detect forward-slash traversal', () => {
      expect(service.hasPathTraversal('../etc/passwd')).toBe(true);
    });

    it('should detect backslash traversal', () => {
      expect(service.hasPathTraversal('..\\windows')).toBe(true);
    });

    it('should detect encoded traversal', () => {
      expect(service.hasPathTraversal('%2e%2e/etc')).toBe(true);
    });

    it('should not flag normal paths', () => {
      expect(service.hasPathTraversal('documents/file.pdf')).toBe(false);
    });
  });

  // ═══════ hasXssPayload ═══════
  describe('hasXssPayload', () => {
    it('should return false for empty input', () => {
      expect(service.hasXssPayload('')).toBe(false);
    });

    it('should detect script tags', () => {
      expect(service.hasXssPayload('<script>alert(1)</script>')).toBe(true);
    });

    it('should detect event handlers', () => {
      expect(service.hasXssPayload('<img onerror=alert(1)>')).toBe(true);
    });

    it('should detect javascript: URIs', () => {
      expect(service.hasXssPayload('javascript:void(0)')).toBe(true);
    });

    it('should detect dangerous HTML tags', () => {
      expect(service.hasXssPayload('<iframe src=evil>')).toBe(true);
      expect(service.hasXssPayload('<object data=evil>')).toBe(true);
      expect(service.hasXssPayload('<embed src=evil>')).toBe(true);
      expect(service.hasXssPayload('<svg onload=evil>')).toBe(true);
    });

    it('should not flag normal text', () => {
      expect(service.hasXssPayload('Hello World')).toBe(false);
      expect(service.hasXssPayload('Total amount: $500')).toBe(false);
    });
  });

  // ═══════ getFileExtension ═══════
  describe('getFileExtension', () => {
    it('should return extension lowercase', () => {
      expect(service.getFileExtension('file.PDF')).toBe('pdf');
      expect(service.getFileExtension('image.JPG')).toBe('jpg');
    });

    it('should return last extension for multiple dots', () => {
      expect(service.getFileExtension('file.tar.gz')).toBe('gz');
    });

    it('should return empty for no extension', () => {
      expect(service.getFileExtension('noextension')).toBe('');
    });
  });

  // ═══════ hasDoubleExtension ═══════
  describe('hasDoubleExtension', () => {
    it('should detect dangerous double extensions', () => {
      expect(service.hasDoubleExtension('file.exe.pdf')).toBe(true);
      expect(service.hasDoubleExtension('doc.js.txt')).toBe(true);
      expect(service.hasDoubleExtension('payload.php.jpg')).toBe(true);
    });

    it('should not flag normal files', () => {
      expect(service.hasDoubleExtension('file.pdf')).toBe(false);
      expect(service.hasDoubleExtension('archive.tar.gz')).toBe(false);
    });
  });

  // ═══════ sanitizeComplaintNumber ═══════
  describe('sanitizeComplaintNumber', () => {
    it('should return empty for empty input', () => {
      expect(service.sanitizeComplaintNumber('')).toBe('');
    });

    it('should keep valid complaint numbers', () => {
      expect(service.sanitizeComplaintNumber('CMS-20260515-100234')).toBe('CMS-20260515-100234');
    });

    it('should strip special characters', () => {
      expect(service.sanitizeComplaintNumber("CMS-001'; DROP--")).toBe('CMS-001DROP--');
    });

    it('should truncate to 30 chars', () => {
      const long = 'A'.repeat(50);
      expect(service.sanitizeComplaintNumber(long).length).toBe(30);
    });
  });

  // ═══════ sanitizeNumeric ═══════
  describe('sanitizeNumeric', () => {
    it('should return empty for empty input', () => {
      expect(service.sanitizeNumeric('')).toBe('');
    });

    it('should strip non-digits', () => {
      expect(service.sanitizeNumeric('12345633w')).toBe('12345633');
      expect(service.sanitizeNumeric('+91-9876543210')).toBe('919876543210');
      expect(service.sanitizeNumeric('abc')).toBe('');
    });

    it('should keep pure numbers', () => {
      expect(service.sanitizeNumeric('9876543210')).toBe('9876543210');
    });
  });

  // ═══════ sanitizeAlphanumeric ═══════
  describe('sanitizeAlphanumeric', () => {
    it('should return empty for empty input', () => {
      expect(service.sanitizeAlphanumeric('')).toBe('');
    });

    it('should strip dangerous characters', () => {
      expect(service.sanitizeAlphanumeric('Hello<script>World')).toBe('HelloscriptWorld');
    });

    it('should allow spaces, hyphens, dots, underscores', () => {
      expect(service.sanitizeAlphanumeric('Hello-World_v2.0 test')).toBe('Hello-World_v2.0 test');
    });

    it('should respect maxLength', () => {
      expect(service.sanitizeAlphanumeric('abcdefghij', 5)).toBe('abcde');
    });
  });

  // ═══════ truncate ═══════
  describe('truncate', () => {
    it('should return empty for empty input', () => {
      expect(service.truncate('', 10)).toBe('');
    });

    it('should not truncate short strings', () => {
      expect(service.truncate('hello', 10)).toBe('hello');
    });

    it('should truncate long strings', () => {
      expect(service.truncate('hello world', 5)).toBe('hello');
    });
  });
});
