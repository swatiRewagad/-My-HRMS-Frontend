import { OcrService } from './ocr.service';

describe('OcrService', () => {
  let service: OcrService;

  beforeEach(() => {
    service = new OcrService();
  });

  describe('extractText', () => {
    it('should return an observable-like object', () => {
      const file = new File(['test'], 'document.pdf', { type: 'application/pdf' });
      const result = service.extractText(file);
      expect(result).toBeDefined();
      expect(result).toHaveProperty('pipe');
    });

    it('should not throw for any file type', () => {
      const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' });
      expect(() => service.extractText(file)).not.toThrow();
    });
  });
});
