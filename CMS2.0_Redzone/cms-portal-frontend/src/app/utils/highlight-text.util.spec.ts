import { highlightEmailText, escapeHtml } from './highlight-text.util';

describe('escapeHtml', () => {
  it('should escape HTML special characters', () => {
    expect(escapeHtml('<script>alert("xss")</script>')).toBe(
      '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;'
    );
  });

  it('should handle ampersands', () => {
    expect(escapeHtml('a & b')).toBe('a &amp; b');
  });

  it('should return empty string for null/undefined', () => {
    expect(escapeHtml('')).toBe('');
    expect(escapeHtml(null as any)).toBe('');
    expect(escapeHtml(undefined as any)).toBe('');
  });
});

describe('highlightEmailText', () => {
  const sampleEmail = 'Dear Sir,\n\nMy name is Mohan Kumar. Phone: 9811234567.\nBank: Punjab National Bank\nBranch: Connaught Place, Delhi';

  it('should highlight exact match (case insensitive)', () => {
    const result = highlightEmailText(sampleEmail, 'Mohan Kumar');
    expect(result).toContain('<mark class="field-highlight">Mohan Kumar</mark>');
  });

  it('should highlight partial text match', () => {
    const result = highlightEmailText(sampleEmail, '9811234567');
    expect(result).toContain('<mark class="field-highlight">9811234567</mark>');
  });

  it('should be case insensitive', () => {
    const result = highlightEmailText(sampleEmail, 'mohan kumar');
    expect(result).toContain('<mark class="field-highlight">Mohan Kumar</mark>');
  });

  it('should highlight multiple occurrences', () => {
    const body = 'Account 12345 was debited. Reference: 12345.';
    const result = highlightEmailText(body, '12345');
    const matches = result.match(/<mark class="field-highlight">/g);
    expect(matches?.length).toBe(2);
  });

  it('should return escaped text when no match found', () => {
    const result = highlightEmailText(sampleEmail, 'NonExistentValue');
    expect(result).not.toContain('<mark');
    expect(result).toContain('Mohan Kumar');
  });

  it('should return escaped body when fieldValue is empty', () => {
    const result = highlightEmailText(sampleEmail, '');
    expect(result).not.toContain('<mark');
  });

  it('should return escaped body when fieldValue is too short (< 2 chars)', () => {
    const result = highlightEmailText(sampleEmail, 'a');
    expect(result).not.toContain('<mark');
  });

  it('should return empty string when emailBody is empty', () => {
    expect(highlightEmailText('', 'test')).toBe('');
  });

  it('should handle special regex characters in search term', () => {
    const body = 'Reference: CMP/2026/JAI/4567 is your complaint.';
    const result = highlightEmailText(body, 'CMP/2026/JAI/4567');
    expect(result).toContain('<mark class="field-highlight">CMP/2026/JAI/4567</mark>');
  });

  it('should handle parentheses in search term', () => {
    const body = 'Amount (Rs 20,000) was debited.';
    const result = highlightEmailText(body, '(Rs 20,000)');
    expect(result).toContain('<mark class="field-highlight">(Rs 20,000)</mark>');
  });

  it('should escape HTML in email body to prevent XSS', () => {
    const body = '<script>alert("xss")</script> Hello World';
    const result = highlightEmailText(body, 'Hello');
    expect(result).not.toContain('<script>');
    expect(result).toContain('&lt;script&gt;');
    expect(result).toContain('<mark class="field-highlight">Hello</mark>');
  });

  it('should preserve newlines in escaped text', () => {
    const result = highlightEmailText('Line 1\nLine 2', 'Line 1');
    expect(result).toContain('\n');
  });
});
