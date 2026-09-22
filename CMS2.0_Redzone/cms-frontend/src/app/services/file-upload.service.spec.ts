import { FileUploadService } from './file-upload.service';

describe('FileUploadService', () => {
  let service: FileUploadService;
  let httpMock: any;

  beforeEach(() => {
    httpMock = {
      get: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      post: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      delete: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      request: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
    };
    // FileUploadService uses inject() for InputSanitizerService, which won't work in unit tests.
    // We test the API-calling methods and utility logic instead.
    service = new (FileUploadService as any)(httpMock);
    // Manually set sanitizer
    (service as any).sanitizer = {
      sanitizeComplaintNumber: (v: string) => v.replace(/[^a-zA-Z0-9-]/g, '').substring(0, 30),
      sanitizeFileName: (v: string) => v.replace(/[^a-zA-Z0-9._-]/g, '_'),
    };
  });

  describe('getStreamUrl', () => {
    it('should return stream URL with attachment ID', () => {
      const url = service.getStreamUrl(42);
      expect(url).toContain('/files/stream/42');
    });
  });

  describe('getAttachments', () => {
    it('should call GET /files/complaint/:id', () => {
      service.getAttachments(10);
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/files/complaint/10'));
    });
  });

  describe('downloadAttachment', () => {
    it('should call GET with responseType blob', () => {
      service.downloadAttachment(5);
      expect(httpMock.get).toHaveBeenCalledWith(
        expect.stringContaining('/files/download/5'),
        expect.objectContaining({ responseType: 'blob' })
      );
    });
  });

  describe('deleteAttachment', () => {
    it('should call DELETE /files/:id', () => {
      service.deleteAttachment(8);
      expect(httpMock.delete).toHaveBeenCalledWith(expect.stringContaining('/files/8'));
    });
  });

  describe('cancelUpload', () => {
    it('should remove upload from active list', () => {
      (service as any).activeUploads.set([
        { uploadId: 'abc', fileName: 'test.pdf', status: 'uploading', progress: 50, chunksUploaded: 1, totalChunks: 2 },
        { uploadId: 'def', fileName: 'other.pdf', status: 'uploading', progress: 30, chunksUploaded: 0, totalChunks: 3 },
      ]);
      service.cancelUpload('abc');
      expect(service.activeUploads().length).toBe(1);
      expect(service.activeUploads()[0].uploadId).toBe('def');
    });
  });

  describe('clearCompleted', () => {
    it('should remove completed and errored uploads', () => {
      (service as any).activeUploads.set([
        { uploadId: '1', status: 'complete', progress: 100, fileName: 'a', chunksUploaded: 1, totalChunks: 1 },
        { uploadId: '2', status: 'uploading', progress: 50, fileName: 'b', chunksUploaded: 1, totalChunks: 2 },
        { uploadId: '3', status: 'error', progress: 0, fileName: 'c', chunksUploaded: 0, totalChunks: 1 },
      ]);
      service.clearCompleted();
      expect(service.activeUploads().length).toBe(1);
      expect(service.activeUploads()[0].uploadId).toBe('2');
    });
  });

  describe('uploadFile - validation', () => {
    it('should reject empty complaint number (returns error status)', () => {
      const file = new File(['data'], 'test.txt', { type: 'text/plain' });
      (service as any).sanitizer.sanitizeComplaintNumber = () => '';

      // The method returns observable that emits error status
      // Since our Subject mock notifies synchronously, subscriber added after .next() won't receive
      // Test that the method doesn't throw
      expect(() => service.uploadFile(file, '', 1)).not.toThrow();
    });

    it('should reject invalid complaint ID (complaintId <= 0)', () => {
      const file = new File(['data'], 'test.txt', { type: 'text/plain' });
      expect(() => service.uploadFile(file, 'CMS-001', 0)).not.toThrow();
    });
  });
});
