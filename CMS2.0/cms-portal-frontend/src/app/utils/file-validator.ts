/**
 * NFR-006: File type validation and size restrictions per EAAP guidelines.
 * Multi-stage quota: Eligibility=6MB, Complaint Details=10MB, Rep Auth=2MB (Total App Cap=18MB).
 */

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

export const ALLOWED_FILE_TYPES: Record<string, string[]> = {
  document: ['application/pdf', 'application/msword'],
  image: ['image/jpeg', 'image/jpg'],
};

export const ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.doc'];

export const MAX_FILE_SIZE_MB = 2;
export const MAX_FILE_COUNT = 10;

export const STEP_QUOTA_MB = {
  eligibility: 6,
  complaintDetails: 10,
  repAuth: 2,
} as const;

export type StepQuotaKey = keyof typeof STEP_QUOTA_MB;

export const MAX_APPLICATION_SIZE_MB = 18;

export function validateFile(file: File): FileValidationResult {
  const extension = '.' + file.name.split('.').pop()?.toLowerCase();

  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return { valid: false, error: `File type "${extension}" is not allowed. Allowed: PDF, DOC, JPG` };
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

export function validateFileSet(files: File[], existingCount: number = 0, maxCount: number = MAX_FILE_COUNT): FileValidationResult {
  if (existingCount + files.length > maxCount) {
    return { valid: false, error: `Maximum ${maxCount} files allowed. You have ${existingCount} already.` };
  }

  for (const file of files) {
    const result = validateFile(file);
    if (!result.valid) return result;
  }

  return { valid: true };
}

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
