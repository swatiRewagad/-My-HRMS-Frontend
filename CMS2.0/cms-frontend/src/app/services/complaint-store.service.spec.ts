import { ComplaintStoreService, LocalComplaint } from './complaint-store.service';

describe('ComplaintStoreService', () => {
  let service: ComplaintStoreService;

  beforeEach(() => {
    localStorage.clear();
    service = new ComplaintStoreService();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should add a complaint with file metadata', () => {
    const complaint: LocalComplaint = {
      id: 'CMS-TEST-001',
      complaintAgainst: 'Test Bank',
      complaintDate: '15-05-2026',
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Test complaint',
      action: 'withdraw',
      files: [
        { name: 'document.pdf', size: 1024, type: 'application/pdf' },
        { name: 'photo.jpg', size: 2048, type: 'image/jpeg' },
      ],
    };

    service.add(complaint);

    expect(service.complaints.length).toBe(1);
    expect(service.complaints[0].files?.length).toBe(2);
    expect(service.complaints[0].files?.[0].name).toBe('document.pdf');
  });

  it('should persist complaints to localStorage', () => {
    service.add({
      id: 'CMS-PERSIST',
      complaintAgainst: 'Bank',
      complaintDate: '15-05-2026',
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Persist test',
      action: 'withdraw',
      files: [{ name: 'file.pdf', size: 500, type: 'application/pdf' }],
    });

    const stored = JSON.parse(localStorage.getItem('cms_local_complaints') || '[]');
    expect(stored.length).toBe(1);
    expect(stored[0].files[0].name).toBe('file.pdf');
  });

  it('should load complaints from localStorage on init', () => {
    const data: LocalComplaint[] = [{
      id: 'CMS-LOAD',
      complaintAgainst: 'Bank',
      complaintDate: '15-05-2026',
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Load test',
      action: 'withdraw',
      files: [{ name: 'loaded.pdf', size: 100, type: 'application/pdf' }],
    }];
    localStorage.setItem('cms_local_complaints', JSON.stringify(data));

    const newService = new ComplaintStoreService();
    expect(newService.complaints.length).toBe(1);
    expect(newService.complaints[0].files?.[0].name).toBe('loaded.pdf');
  });

  it('should find complaint by ID', () => {
    service.add({
      id: 'CMS-FIND',
      complaintAgainst: 'Bank',
      complaintDate: '15-05-2026',
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Find test',
      action: 'withdraw',
      files: [{ name: 'found.pdf', size: 200, type: 'application/pdf' }],
    });

    const found = service.getById('CMS-FIND');
    expect(found).toBeDefined();
    expect(found?.files?.[0].name).toBe('found.pdf');
  });

  it('should return undefined for unknown ID', () => {
    const found = service.getById('UNKNOWN');
    expect(found).toBeUndefined();
  });

  it('should update a complaint', () => {
    service.add({
      id: 'CMS-UPDATE',
      complaintAgainst: 'Bank',
      complaintDate: '15-05-2026',
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Before update',
      action: 'withdraw',
    });

    service.update('CMS-UPDATE', {
      status: 'resolved',
      statusLabel: 'Resolved',
      files: [{ name: 'new.pdf', size: 300, type: 'application/pdf' }],
    });

    const updated = service.getById('CMS-UPDATE');
    expect(updated?.status).toBe('resolved');
    expect(updated?.files?.[0].name).toBe('new.pdf');
  });
});
