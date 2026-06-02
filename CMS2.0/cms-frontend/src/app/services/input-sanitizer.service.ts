import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class InputSanitizerService {

  private static readonly HTML_TAG_RE = /<[^>]*>/g;
  private static readonly SCRIPT_RE = /<script[\s\S]*?>[\s\S]*?<\/script>/gi;
  private static readonly EVENT_HANDLER_RE = /\bon\w+\s*=/gi;
  private static readonly JAVASCRIPT_URI_RE = /javascript\s*:/gi;
  private static readonly DATA_URI_RE = /data\s*:[^,]*;base64/gi;
  private static readonly SQL_INJECTION_RE = /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|EXEC|EXECUTE|TRUNCATE|DECLARE|CAST|CONVERT|xp_)\b|--|;|\b(OR|AND)\b\s+\d+\s*=\s*\d+)/i;
  private static readonly PATH_TRAVERSAL_RE = /(\.\.[\\/]|~[\\/]|%2e%2e|%252e)/gi;
  private static readonly PATH_TRAVERSAL_TEST_RE = /(\.\.[\\/]|~[\\/]|%2e%2e|%252e)/i;
  private static readonly NULL_BYTE_RE = /\x00|%00/g;
  private static readonly CONTROL_CHAR_RE = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g;

  private static readonly ENTITY_MAP: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',
    '/': '&#x2F;',
    '`': '&#96;',
  };

  stripHtml(input: string): string {
    if (!input) return '';
    return input
      .replace(InputSanitizerService.SCRIPT_RE, '')
      .replace(InputSanitizerService.HTML_TAG_RE, '')
      .replace(InputSanitizerService.EVENT_HANDLER_RE, '')
      .replace(InputSanitizerService.JAVASCRIPT_URI_RE, '')
      .replace(InputSanitizerService.DATA_URI_RE, '');
  }

  escapeHtml(input: string): string {
    if (!input) return '';
    return input.replace(/[&<>"'`/]/g, ch => InputSanitizerService.ENTITY_MAP[ch] || ch);
  }

  sanitizeText(input: string): string {
    if (!input) return '';
    let clean = this.stripHtml(input);
    clean = clean.replace(InputSanitizerService.CONTROL_CHAR_RE, '');
    clean = clean.replace(InputSanitizerService.NULL_BYTE_RE, '');
    return clean.trim();
  }

  sanitizeFileName(name: string): string {
    if (!name) return '';
    let clean = name.replace(InputSanitizerService.PATH_TRAVERSAL_RE, '');
    clean = clean.replace(InputSanitizerService.NULL_BYTE_RE, '');
    clean = clean.replace(InputSanitizerService.CONTROL_CHAR_RE, '');
    clean = clean.replace(/[<>:"|?*\\\/]/g, '_');
    clean = clean.replace(/\s+/g, '_');

    if (clean.startsWith('.')) {
      clean = '_' + clean;
    }

    if (clean.length > 255) {
      const ext = this.getFileExtension(clean);
      clean = clean.substring(0, 255 - ext.length - 1) + '.' + ext;
    }

    return clean;
  }

  hasSqlInjectionPattern(input: string): boolean {
    if (!input) return false;
    return InputSanitizerService.SQL_INJECTION_RE.test(input);
  }

  hasPathTraversal(input: string): boolean {
    if (!input) return false;
    return InputSanitizerService.PATH_TRAVERSAL_TEST_RE.test(input);
  }

  hasXssPayload(input: string): boolean {
    if (!input) return false;
    return (
      InputSanitizerService.SCRIPT_RE.test(input) ||
      InputSanitizerService.EVENT_HANDLER_RE.test(input) ||
      InputSanitizerService.JAVASCRIPT_URI_RE.test(input) ||
      /<\s*(img|iframe|object|embed|svg|link|meta|base)\b/i.test(input)
    );
  }

  getFileExtension(fileName: string): string {
    const parts = fileName.split('.');
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
  }

  hasDoubleExtension(fileName: string): boolean {
    const dangerous = ['exe', 'bat', 'cmd', 'sh', 'ps1', 'vbs', 'js', 'jar', 'msi', 'scr', 'com', 'pif', 'hta', 'cpl', 'inf', 'reg', 'ws', 'wsf', 'php', 'asp', 'aspx', 'jsp'];
    const parts = fileName.split('.');
    if (parts.length <= 2) return false;
    return parts.slice(0, -1).some(p => dangerous.includes(p.toLowerCase()));
  }

  sanitizeComplaintNumber(input: string): string {
    if (!input) return '';
    return input.replace(/[^a-zA-Z0-9\-]/g, '').substring(0, 30);
  }

  sanitizeNumeric(input: string): string {
    if (!input) return '';
    return input.replace(/\D/g, '');
  }

  sanitizeAlphanumeric(input: string, maxLength = 100): string {
    if (!input) return '';
    return input.replace(/[^a-zA-Z0-9\s\-_.]/g, '').substring(0, maxLength);
  }

  truncate(input: string, maxLength: number): string {
    if (!input) return '';
    return input.length > maxLength ? input.substring(0, maxLength) : input;
  }
}
