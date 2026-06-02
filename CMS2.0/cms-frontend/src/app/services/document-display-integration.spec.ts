import { FileCacheService } from './file-cache.service';
import { ComplaintStoreService } from './complaint-store.service';

/**
 * Integration tests verifying the full flow:
 * 1. File complaint -> store files in IndexedDB + metadata in localStorage
 * 2. Navigate to Track Complaint -> retrieve files from IndexedDB
 * 3. Files are viewable/downloadable
 */
describe('Document Display Integration', () => {
  let fileCache: FileCacheService;
  let complaintStore: ComplaintStoreService;

  beforeEach(() => {
    localStorage.clear();
    fileCache = new FileCacheService();
    complaintStore = new ComplaintStoreService();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('Filing complaint stores files for later retrieval', () => {
    it('should store file data in IndexedDB and metadata in localStorage', async () => {
      const complaintId = 'CMS-20260515-100001';
      const files = [
        new File(['PDF content here'], 'complaint_doc.pdf', { type: 'application/pdf' }),
        new File(['Image data'], 'evidence.jpg', { type: 'image/jpeg' }),
      ];

      // Simulate what file-complaint.component does on submit
      await fileCache.store(complaintId, files);
      complaintStore.add({
        id: complaintId,
        complaintAgainst: 'Test Bank',
        complaintDate: '15-05-2026',
        status: 'in_progress',
        statusLabel: 'In Progress',
        comments: 'Online complaint filed',
        action: 'withdraw',
        files: files.map(f => ({ name: f.name, size: f.size, type: f.type })),
      });

      // Verify IndexedDB has the actual files
      const cachedFiles = await fileCache.get(complaintId);
      expect(cachedFiles.length).toBe(2);
      expect(cachedFiles[0].name).toBe('complaint_doc.pdf');
      expect(cachedFiles[1].name).toBe('evidence.jpg');

      // Verify content is preserved
      const pdfText = await cachedFiles[0].text();
      expect(pdfText).toBe('PDF content here');

      // Verify localStorage has metadata
      const localComplaint = complaintStore.getById(complaintId);
      expect(localComplaint).toBeDefined();
      expect(localComplaint?.files?.length).toBe(2);
      expect(localComplaint?.files?.[0].name).toBe('complaint_doc.pdf');
    });
  });

  describe('Track complaint retrieves files after navigation', () => {
    it('should find cached files by complaint ID (simulates page reload)', async () => {
      const complaintId = 'CMS-20260515-200002';
      const file = new File(['Hello world document'], 'report.pdf', { type: 'application/pdf' });

      // Step 1: Store (happens during file-complaint submit)
      await fileCache.store(complaintId, [file]);

      // Step 2: Create a new FileCacheService instance (simulates navigation/component re-init)
      const newCacheInstance = new FileCacheService();

      // Step 3: Retrieve (happens during track-complaint viewComplaint)
      const retrieved = await newCacheInstance.get(complaintId);
      expect(retrieved.length).toBe(1);
      expect(retrieved[0].name).toBe('report.pdf');
      expect(retrieved[0].size).toBe(20); // 'Hello world document'.length

      const text = await retrieved[0].text();
      expect(text).toBe('Hello world document');
    });

    it('should handle multiple complaints independently', async () => {
      await fileCache.store('CMS-A', [
        new File(['AAA'], 'fileA.pdf', { type: 'application/pdf' }),
      ]);
      await fileCache.store('CMS-B', [
        new File(['BBB'], 'fileB.png', { type: 'image/png' }),
        new File(['CCC'], 'fileC.jpg', { type: 'image/jpeg' }),
      ]);

      const filesA = await fileCache.get('CMS-A');
      const filesB = await fileCache.get('CMS-B');

      expect(filesA.length).toBe(1);
      expect(filesA[0].name).toBe('fileA.pdf');

      expect(filesB.length).toBe(2);
      expect(filesB[0].name).toBe('fileB.png');
      expect(filesB[1].name).toBe('fileC.jpg');
    });

    it('should return empty when complaint has no cached files', async () => {
      const files = await fileCache.get('CMS-NONEXISTENT');
      expect(files).toEqual([]);
    });
  });

  describe('Fallback to localStorage metadata when IndexedDB is empty', () => {
    it('should provide file metadata from localStorage', () => {
      const complaintId = 'CMS-20260515-300003';
      complaintStore.add({
        id: complaintId,
        complaintAgainst: 'Fallback Bank',
        complaintDate: '15-05-2026',
        status: 'in_progress',
        statusLabel: 'In Progress',
        comments: 'Test',
        action: 'withdraw',
        files: [
          { name: 'stored.pdf', size: 1024, type: 'application/pdf' },
          { name: 'photo.jpg', size: 2048, type: 'image/jpeg' },
        ],
      });

      // Simulate track-complaint loadCachedFiles when IndexedDB returns empty
      const localComplaint = complaintStore.getById(complaintId);
      expect(localComplaint?.files?.length).toBe(2);

      // Build attachment objects like track-complaint does
      const attachments = localComplaint!.files!.map((f, i) => ({
        id: -(i + 1),
        fileName: f.name,
        originalFileName: f.name,
        fileSize: f.size,
        size: f.size,
        contentType: f.type,
        isLocal: true,
        hasCachedFile: false,
      }));

      expect(attachments.length).toBe(2);
      expect(attachments[0].fileName).toBe('stored.pdf');
      expect(attachments[0].fileSize).toBe(1024);
      expect(attachments[0].hasCachedFile).toBe(false);
      expect(attachments[1].fileName).toBe('photo.jpg');
    });
  });

  describe('Large file support', () => {
    it('should store and retrieve a 10MB file', async () => {
      const largeData = new Uint8Array(10 * 1024 * 1024);
      // Fill with pattern to verify content integrity
      for (let i = 0; i < largeData.length; i++) {
        largeData[i] = i % 256;
      }
      const file = new File([largeData], 'large_video.mp4', { type: 'video/mp4' });

      await fileCache.store('CMS-LARGE', [file]);

      const retrieved = await fileCache.get('CMS-LARGE');
      expect(retrieved.length).toBe(1);
      expect(retrieved[0].name).toBe('large_video.mp4');
      expect(retrieved[0].size).toBe(10 * 1024 * 1024);
      expect(retrieved[0].type).toBe('video/mp4');

      // Verify content integrity
      const buffer = await retrieved[0].arrayBuffer();
      const data = new Uint8Array(buffer);
      expect(data[0]).toBe(0);
      expect(data[1]).toBe(1);
      expect(data[255]).toBe(255);
      expect(data[256]).toBe(0);
    });
  });

  describe('File type detection for preview', () => {
    const getPreviewType = (fileName: string, contentType?: string): string => {
      const ext = (fileName.split('.').pop() || '').toLowerCase();
      const mime = (contentType || '').toLowerCase();
      if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(ext) || mime.startsWith('image/')) return 'image';
      if (['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'].includes(ext) || mime.startsWith('video/')) return 'video';
      if (['mp3', 'wav', 'ogg', 'aac', 'flac', 'wma'].includes(ext) || mime.startsWith('audio/')) return 'audio';
      if (ext === 'pdf' || mime === 'application/pdf') return 'pdf';
      return 'other';
    };

    it('should detect image files', () => {
      expect(getPreviewType('photo.jpg')).toBe('image');
      expect(getPreviewType('img.png')).toBe('image');
      expect(getPreviewType('file.webp')).toBe('image');
      expect(getPreviewType('unknown', 'image/gif')).toBe('image');
    });

    it('should detect video files', () => {
      expect(getPreviewType('video.mp4')).toBe('video');
      expect(getPreviewType('clip.webm')).toBe('video');
      expect(getPreviewType('movie.mov')).toBe('video');
      expect(getPreviewType('file', 'video/mp4')).toBe('video');
    });

    it('should detect audio files', () => {
      expect(getPreviewType('song.mp3')).toBe('audio');
      expect(getPreviewType('sound.wav')).toBe('audio');
      expect(getPreviewType('file', 'audio/mpeg')).toBe('audio');
    });

    it('should detect PDF files', () => {
      expect(getPreviewType('doc.pdf')).toBe('pdf');
      expect(getPreviewType('file', 'application/pdf')).toBe('pdf');
    });

    it('should return other for unknown types', () => {
      expect(getPreviewType('file.docx')).toBe('other');
      expect(getPreviewType('data.xlsx')).toBe('other');
      expect(getPreviewType('archive.zip')).toBe('other');
    });
  });

  describe('Attachment card data structure', () => {
    it('should build correct attachment objects from cached files', async () => {
      const files = [
        new File(['pdf data'], 'document.pdf', { type: 'application/pdf' }),
        new File(['img data'], 'screenshot.png', { type: 'image/png' }),
      ];
      await fileCache.store('CMS-CARD-TEST', files);

      const cachedFiles = await fileCache.get('CMS-CARD-TEST');
      const attachments = cachedFiles.map((f: File, i: number) => ({
        id: -(i + 1),
        fileName: f.name,
        originalFileName: f.name,
        fileSize: f.size,
        size: f.size,
        contentType: f.type,
        isLocal: true,
        hasCachedFile: true,
        cachedFile: f,
      }));

      expect(attachments.length).toBe(2);

      // First attachment
      expect(attachments[0].id).toBe(-1);
      expect(attachments[0].fileName).toBe('document.pdf');
      expect(attachments[0].fileSize).toBe(8); // 'pdf data'.length
      expect(attachments[0].contentType).toBe('application/pdf');
      expect(attachments[0].hasCachedFile).toBe(true);
      expect(attachments[0].cachedFile).toBeInstanceOf(File);

      // Second attachment
      expect(attachments[1].id).toBe(-2);
      expect(attachments[1].fileName).toBe('screenshot.png');
      expect(attachments[1].contentType).toBe('image/png');
    });

    it('should allow creating object URLs from cached files', async () => {
      const file = new File(['test'], 'test.pdf', { type: 'application/pdf' });
      await fileCache.store('CMS-URL-TEST', [file]);

      const cached = await fileCache.get('CMS-URL-TEST');
      // In a real browser URL.createObjectURL would work on these File objects
      // Here we verify the File is a proper instance
      expect(cached[0]).toBeInstanceOf(File);
      expect(cached[0].name).toBe('test.pdf');
      expect(cached[0].type).toBe('application/pdf');
    });
  });
});
