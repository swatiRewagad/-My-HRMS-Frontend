/**
 * File type validation and size restrictions per EAAP guidelines.
 *
 * <p>SIZE LIMITS ARE PASSED IN, NOT COMPILED IN. They live in `system_config`, are served by
 * GET /api/v1/config/upload-limits, and are held by `UploadLimitsService`. This module used to export
 * `MAX_FILE_SIZE_MB`, and every caller that read it enforced whatever figure was current when the bundle
 * was built — so the browser refused attachments the API would have taken, on the citizen complaint form,
 * which is where it matters most. A constant in the bundle cannot track a retuned configuration.
 *
 * <p>The defaults below exist only for a caller that supplies nothing, and must equal the server's own
 * fallback. Do not read them in place of asking UploadLimitsService.
 */

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

export interface FileSizeLimits {
  maxFileSizeMb: number;
  maxTotalSizeMb: number;
  maxFileCount: number;
}

export const DEFAULT_FILE_SIZE_LIMITS: FileSizeLimits = {
  maxFileSizeMb: 2,
  maxTotalSizeMb: 25,
  maxFileCount: 10,
};

export const ALLOWED_FILE_TYPES: Record<string, string[]> = {
  document: ['application/pdf', 'application/msword'],
  image: ['image/jpeg', 'image/png'],
};

export const ALLOWED_EXTENSIONS = ['.pdf', '.doc', '.jpg', '.jpeg', '.png'];

export function validateFile(
  file: File,
  limits: FileSizeLimits = DEFAULT_FILE_SIZE_LIMITS,
): FileValidationResult {
  const extension = '.' + file.name.split('.').pop()?.toLowerCase();

  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return { valid: false, error: `File type "${extension}" is not allowed. Allowed: PDF, DOC, JPG, PNG` };
  }

  const allMimeTypes = Object.values(ALLOWED_FILE_TYPES).flat();
  if (file.type && !allMimeTypes.includes(file.type)) {
    return { valid: false, error: `MIME type "${file.type}" is not permitted.` };
  }

  const sizeMB = file.size / (1024 * 1024);
  if (sizeMB > limits.maxFileSizeMb) {
    return { valid: false, error: `File size (${sizeMB.toFixed(1)}MB) exceeds the ${limits.maxFileSizeMb}MB limit.` };
  }

  if (file.name.includes('..') || /[<>:"|?*]/.test(file.name)) {
    return { valid: false, error: 'File name contains invalid characters.' };
  }

  return { valid: true };
}

export function validateFileSet(
  files: File[],
  existingCount: number = 0,
  limits: FileSizeLimits = DEFAULT_FILE_SIZE_LIMITS,
): FileValidationResult {
  if (existingCount + files.length > limits.maxFileCount) {
    return { valid: false, error: `Maximum ${limits.maxFileCount} files allowed. You have ${existingCount} already.` };
  }

  let totalSize = 0;
  for (const file of files) {
    const result = validateFile(file, limits);
    if (!result.valid) return result;
    totalSize += file.size;
  }

  if (totalSize / (1024 * 1024) > limits.maxTotalSizeMb) {
    return { valid: false, error: `Total upload size exceeds ${limits.maxTotalSizeMb}MB limit.` };
  }

  return { valid: true };
}
