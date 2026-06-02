import { TranslateService } from './translate.service';

describe('TranslateService', () => {
  let service: TranslateService;

  beforeEach(() => {
    localStorage.clear();
    service = new TranslateService();
  });

  describe('initial state', () => {
    it('should default to English', () => {
      expect(service.lang()).toBe('en');
    });

    it('should load saved language from localStorage', () => {
      localStorage.setItem('cms_language', 'hi');
      const svc = new TranslateService();
      expect(svc.lang()).toBe('hi');
    });

    it('should fallback to en for invalid saved language', () => {
      localStorage.setItem('cms_language', 'xx');
      const svc = new TranslateService();
      expect(svc.lang()).toBe('en');
    });
  });

  describe('setLanguage', () => {
    it('should change the current language', () => {
      service.setLanguage('hi');
      expect(service.lang()).toBe('hi');
    });

    it('should persist language to localStorage', () => {
      service.setLanguage('ta');
      expect(localStorage.getItem('cms_language')).toBe('ta');
    });

    it('should update document lang attribute', () => {
      service.setLanguage('bn');
      expect(document.documentElement.lang).toBe('bn');
    });
  });

  describe('t (translate)', () => {
    it('should return English translation by default', () => {
      expect(service.t('header.home')).toBe('HOME');
    });

    it('should return Hindi translation when lang is hi', () => {
      service.setLanguage('hi');
      expect(service.t('header.home')).toBe('होम');
    });

    it('should fallback to English for missing translation', () => {
      service.setLanguage('ta');
      expect(service.t('header.home')).toBe('முகப்பு');
    });

    it('should return key if translation not found in any language', () => {
      expect(service.t('nonexistent.key')).toBe('nonexistent.key');
    });

    it('should translate common keys', () => {
      expect(service.t('common.search')).toBe('Search');
      expect(service.t('common.cancel')).toBe('Cancel');
      expect(service.t('common.close')).toBe('Close');
    });
  });

  describe('langLabel', () => {
    it('should return English for default', () => {
      expect(service.langLabel()).toBe('English');
    });

    it('should return native label for Hindi', () => {
      service.setLanguage('hi');
      expect(service.langLabel()).toBe('हिन्दी');
    });

    it('should return native label for Tamil', () => {
      service.setLanguage('ta');
      expect(service.langLabel()).toBe('தமிழ்');
    });
  });

  describe('languages list', () => {
    it('should have 12 languages', () => {
      expect(service.languages.length).toBe(12);
    });

    it('should have code, label, and nativeLabel for each', () => {
      service.languages.forEach(lang => {
        expect(lang).toHaveProperty('code');
        expect(lang).toHaveProperty('label');
        expect(lang).toHaveProperty('nativeLabel');
      });
    });
  });
});
