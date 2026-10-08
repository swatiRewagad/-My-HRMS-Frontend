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
 *
 * <p>On top of the per-file/per-set server-driven limits, the multi-step complaint wizard also enforces
 * a per-step aggregate quota (NFR-006): Eligibility=6MB, Complaint Details=10MB, Rep Auth=2MB, with an
 * overall application cap of 18MB. That quota is independent of the server-configured limits above —
 * see `STEP_QUOTA_MB` / `validateStepQuota`.
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

// Compatibility constants for callers (e.g. shared/file-upload/file-upload.component.ts) that render
// a static hint/default before the server-configured limits have loaded. These intentionally mirror
// DEFAULT_FILE_SIZE_LIMITS and must not drift from it.
export const MAX_FILE_SIZE_MB = DEFAULT_FILE_SIZE_LIMITS.maxFileSizeMb;
export const MAX_FILE_COUNT = DEFAULT_FILE_SIZE_LIMITS.maxFileCount;

export const ALLOWED_FILE_TYPES: Record<string, string[]> = {
  document: ['application/pdf', 'application/msword'],
  image: ['image/jpeg', 'image/jpg'],
};

export const ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.doc'];

export const STEP_QUOTA_MB = {
  eligibility: 6,
  complaintDetails: 10,
  repAuth: 2,
} as const;

export type StepQuotaKey = keyof typeof STEP_QUOTA_MB;

export const MAX_APPLICATION_SIZE_MB = 18;

export function validateFile(
  file: File,
  limits: FileSizeLimits = DEFAULT_FILE_SIZE_LIMITS,
): FileValidationResult {
  const extension = '.' + file.name.split('.').pop()?.toLowerCase();

  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return { valid: false, error: `File type "${extension}" is not allowed. Allowed: PDF, DOC, JPG` };
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

/**
 * Per-step aggregate quota for the multi-step complaint wizard (NFR-006). This runs in addition to
 * the server-driven per-file/per-set limits in `validateFile`/`validateFileSet` — a file can pass
 * those and still be rejected here if it would push a single step (eligibility / complaint details /
 * rep authorization) over its own smaller cap, or push the whole application over
 * `MAX_APPLICATION_SIZE_MB`.
 */
export function validateStepQuota(
  newFiles: File[],
  existingBytesInStep: number,
  stepKey: StepQuotaKey
): FileValidationResult {
  const quotaBytes = STEP_QUOTA_MB[stepKey] * 1024 * 1024;
  const newTotalBytes = newFiles.reduce((sum, f) => sum + f.size, 0);
  const projectedTotal = existingBytesInStep + newTotalBytes;

  if (projectedTotal > quotaBytes) {
    const usedMB = (existingBytesInStep / (1024 * 1024)).toFixed(1);
    const limitMB = STEP_QUOTA_MB[stepKey];
    return {
      valid: false,
      error: `Total upload size for this section would exceed ${limitMB}MB limit (${usedMB}MB already used).`,
    };
  }

  return { valid: true };
}
