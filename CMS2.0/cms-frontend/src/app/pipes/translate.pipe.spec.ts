import { TranslatePipe } from './translate.pipe';
import { TranslateService } from '../services/translate.service';

describe('TranslatePipe', () => {
  let pipe: TranslatePipe;
  let translateService: TranslateService;

  beforeEach(() => {
    localStorage.clear();
    translateService = new TranslateService();
    pipe = new TranslatePipe(translateService);
  });

  it('should translate known key', () => {
    expect(pipe.transform('header.home')).toBe('HOME');
  });

  it('should return key for unknown translation', () => {
    expect(pipe.transform('nonexistent.key')).toBe('nonexistent.key');
  });

  it('should translate after language change', () => {
    translateService.setLanguage('hi');
    expect(pipe.transform('header.home')).toBe('होम');
  });

  it('should translate common keys', () => {
    expect(pipe.transform('common.search')).toBe('Search');
    expect(pipe.transform('common.cancel')).toBe('Cancel');
  });
});
