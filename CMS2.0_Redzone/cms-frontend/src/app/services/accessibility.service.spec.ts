import { AccessibilityService } from './accessibility.service';

describe('AccessibilityService', () => {
  let service: AccessibilityService;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.style.fontSize = '';
    service = new AccessibilityService();
  });

  describe('initial state', () => {
    it('should default highContrast to false', () => {
      expect(service.highContrast()).toBe(false);
    });

    it('should default reducedMotion to false', () => {
      expect(service.reducedMotion()).toBe(false);
    });

    it('should default fontSize to 16', () => {
      expect(service.fontSize()).toBe(16);
    });

    it('should load saved preferences', () => {
      localStorage.setItem('cms_high_contrast', 'true');
      localStorage.setItem('cms_reduced_motion', 'true');
      localStorage.setItem('cms_font_size', '20');
      const svc = new AccessibilityService();
      expect(svc.highContrast()).toBe(true);
      expect(svc.reducedMotion()).toBe(true);
      expect(svc.fontSize()).toBe(20);
    });
  });

  describe('toggleHighContrast', () => {
    it('should toggle from false to true', () => {
      service.toggleHighContrast();
      expect(service.highContrast()).toBe(true);
    });

    it('should toggle from true to false', () => {
      service.toggleHighContrast();
      service.toggleHighContrast();
      expect(service.highContrast()).toBe(false);
    });

    it('should persist to localStorage', () => {
      service.toggleHighContrast();
      expect(localStorage.getItem('cms_high_contrast')).toBe('true');
    });

    it('should add high-contrast class to html', () => {
      service.toggleHighContrast();
      expect(document.documentElement.classList.contains('high-contrast')).toBe(true);
    });

    it('should remove high-contrast class when toggled off', () => {
      service.toggleHighContrast();
      service.toggleHighContrast();
      expect(document.documentElement.classList.contains('high-contrast')).toBe(false);
    });
  });

  describe('toggleReducedMotion', () => {
    it('should toggle reduced motion', () => {
      service.toggleReducedMotion();
      expect(service.reducedMotion()).toBe(true);
      expect(document.documentElement.classList.contains('reduced-motion')).toBe(true);
    });

    it('should persist to localStorage', () => {
      service.toggleReducedMotion();
      expect(localStorage.getItem('cms_reduced_motion')).toBe('true');
    });
  });

  describe('increaseFontSize', () => {
    it('should increase by 2', () => {
      service.increaseFontSize();
      expect(service.fontSize()).toBe(18);
    });

    it('should not exceed 24', () => {
      service.increaseFontSize(); // 18
      service.increaseFontSize(); // 20
      service.increaseFontSize(); // 22
      service.increaseFontSize(); // 24
      service.increaseFontSize(); // still 24
      expect(service.fontSize()).toBe(24);
    });

    it('should update document fontSize', () => {
      service.increaseFontSize();
      expect(document.documentElement.style.fontSize).toBe('18px');
    });

    it('should persist to localStorage', () => {
      service.increaseFontSize();
      expect(localStorage.getItem('cms_font_size')).toBe('18');
    });
  });

  describe('decreaseFontSize', () => {
    it('should decrease by 2', () => {
      service.decreaseFontSize();
      expect(service.fontSize()).toBe(14);
    });

    it('should not go below 12', () => {
      service.decreaseFontSize(); // 14
      service.decreaseFontSize(); // 12
      service.decreaseFontSize(); // still 12
      expect(service.fontSize()).toBe(12);
    });
  });

  describe('resetFontSize', () => {
    it('should reset to 16', () => {
      service.increaseFontSize();
      service.increaseFontSize();
      service.resetFontSize();
      expect(service.fontSize()).toBe(16);
    });

    it('should update document fontSize to 16px', () => {
      service.resetFontSize();
      expect(document.documentElement.style.fontSize).toBe('16px');
    });
  });

  describe('announce', () => {
    it('should set text on aria-live-region element', (done) => {
      const el = document.createElement('div');
      el.id = 'aria-live-region';
      document.body.appendChild(el);

      service.announce('Test message');

      setTimeout(() => {
        expect(el.textContent).toBe('Test message');
        document.body.removeChild(el);
        done();
      }, 150);
    });

    it('should not throw when element is missing', () => {
      expect(() => service.announce('No element')).not.toThrow();
    });
  });
});
