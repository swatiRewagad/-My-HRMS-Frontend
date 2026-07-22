export function highlightEmailText(emailBody: string, fieldValue: string): string {
  if (!emailBody || !fieldValue || fieldValue.trim().length < 2) {
    return escapeHtml(emailBody || '');
  }

  const escaped = escapeHtml(emailBody);
  const searchTerm = fieldValue.trim();
  const escapedTerm = escapeHtml(searchTerm);
  const regexSafe = escapedTerm.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

  try {
    const regex = new RegExp(`(${regexSafe})`, 'gi');
    return escaped.replace(regex, '<mark style="background-color:#fff9c4;border-radius:2px;padding:0 2px">$1</mark>');
  } catch {
    return escaped;
  }
}

export function escapeHtml(text: string): string {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
