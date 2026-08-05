/**
 * NFR-006: File type validation and size restrictions per EAAP guidelines.
 */

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

export const ALLOWED_FILE_TYPES: Record<string, string[]> = {
  document: ['application/pdf', 'application/msword'],
  image: ['image/jpeg', 'image/png'],
};

export const ALLOWED_EXTENSIONS = ['.pdf', '.doc', '.jpg', '.jpeg', '.png'];

export const MAX_FILE_SIZE_MB = 2;
export const MAX_TOTAL_SIZE_MB = 25;
export const MAX_FILE_COUNT = 10;

export function validateFile(file: File): FileValidationResult {
  const extension = '.' + file.name.split('.').pop()?.toLowerCase();

  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return { valid: false, error: `File type "${extension}" is not allowed. Allowed: PDF, DOC, JPG, PNG` };
  }

  const allMimeTypes = Object.values(ALLOWED_FILE_TYPES).flat();
  if (file.type && !allMimeTypes.includes(file.type)) {
    return { valid: false, error: `MIME type "${file.type}" is not permitted.` };
  }

  const sizeMB = file.size / (1024 * 1024);
  if (sizeMB > MAX_FILE_SIZE_MB) {
    return { valid: false, error: `File size (${sizeMB.toFixed(1)}MB) exceeds the ${MAX_FILE_SIZE_MB}MB limit.` };
  }

  if (file.name.includes('..') || /[<>:"|?*]/.test(file.name)) {
    return { valid: false, error: 'File name contains invalid characters.' };
  }

  return { valid: true };
}

export function validateFileSet(files: File[], existingCount: number = 0): FileValidationResult {
  if (existingCount + files.length > MAX_FILE_COUNT) {
    return { valid: false, error: `Maximum ${MAX_FILE_COUNT} files allowed. You have ${existingCount} already.` };
  }

  let totalSize = 0;
  for (const file of files) {
    const result = validateFile(file);
    if (!result.valid) return result;
    totalSize += file.size;
  }

  if (totalSize / (1024 * 1024) > MAX_TOTAL_SIZE_MB) {
    return { valid: false, error: `Total upload size exceeds ${MAX_TOTAL_SIZE_MB}MB limit.` };
  }

  return { valid: true };
}
