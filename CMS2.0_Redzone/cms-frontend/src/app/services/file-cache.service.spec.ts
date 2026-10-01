import { FileCacheService } from './file-cache.service';

describe('FileCacheService', () => {
  let service: FileCacheService;

  beforeEach(() => {
    service = new FileCacheService();
  });

  it('should store and retrieve files by complaint ID', async () => {
    const file = new File(['hello world'], 'test.pdf', { type: 'application/pdf' });
    await service.store('CMS-001', [file]);

    const retrieved = await service.get('CMS-001');
    expect(retrieved.length).toBe(1);
    expect(retrieved[0].name).toBe('test.pdf');
    expect(retrieved[0].type).toBe('application/pdf');
    expect(retrieved[0].size).toBe(11);
  });

  it('should return empty array for unknown complaint ID', async () => {
    const result = await service.get('UNKNOWN-ID');
    expect(result).toEqual([]);
  });

  it('should store multiple files', async () => {
    const files = [
      new File(['aaa'], 'doc1.pdf', { type: 'application/pdf' }),
      new File(['bbb'], 'image.png', { type: 'image/png' }),
      new File(['ccc'], 'video.mp4', { type: 'video/mp4' }),
    ];
    await service.store('CMS-002', files);

    const retrieved = await service.get('CMS-002');
    expect(retrieved.length).toBe(3);
    expect(retrieved[0].name).toBe('doc1.pdf');
    expect(retrieved[1].name).toBe('image.png');
    expect(retrieved[2].name).toBe('video.mp4');
  });

  it('should preserve file content', async () => {
    const content = 'This is test file content for verification';
    const file = new File([content], 'test.txt', { type: 'text/plain' });
    await service.store('CMS-003', [file]);

    const retrieved = await service.get('CMS-003');
    const text = await retrieved[0].text();
    expect(text).toBe(content);
  });

  it('should handle large files', async () => {
    const largeContent = new Uint8Array(5 * 1024 * 1024); // 5MB
    const file = new File([largeContent], 'large.bin', { type: 'application/octet-stream' });
    await service.store('CMS-004', [file]);

    const retrieved = await service.get('CMS-004');
    expect(retrieved.length).toBe(1);
    expect(retrieved[0].size).toBe(5 * 1024 * 1024);
  });

  it('should report has() correctly', async () => {
    expect(await service.has('CMS-005')).toBe(false);

    await service.store('CMS-005', [new File(['x'], 'x.txt', { type: 'text/plain' })]);
    expect(await service.has('CMS-005')).toBe(true);
  });

  it('should remove files', async () => {
    await service.store('CMS-006', [new File(['x'], 'x.txt', { type: 'text/plain' })]);
    expect(await service.has('CMS-006')).toBe(true);

    await service.remove('CMS-006');
    expect(await service.has('CMS-006')).toBe(false);
  });

  it('should not overwrite files for different complaint IDs', async () => {
    await service.store('CMS-A', [new File(['a'], 'a.txt', { type: 'text/plain' })]);
    await service.store('CMS-B', [new File(['b'], 'b.txt', { type: 'text/plain' })]);

    const filesA = await service.get('CMS-A');
    const filesB = await service.get('CMS-B');
    expect(filesA[0].name).toBe('a.txt');
    expect(filesB[0].name).toBe('b.txt');
  });

  it('should not store empty file arrays', async () => {
    await service.store('CMS-EMPTY', []);
    expect(await service.has('CMS-EMPTY')).toBe(false);
  });
});
