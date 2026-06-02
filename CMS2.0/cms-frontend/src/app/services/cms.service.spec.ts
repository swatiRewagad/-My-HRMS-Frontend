import { CmsService } from './cms.service';

describe('CmsService', () => {
  let service: CmsService;
  let httpMock: any;

  beforeEach(() => {
    httpMock = {
      get: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      post: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      put: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      delete: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
    };
    service = new CmsService(httpMock);
  });

  describe('getDashboard', () => {
    it('should call GET /dashboard', () => {
      service.getDashboard();
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/dashboard'));
    });
  });

  describe('getComplaints', () => {
    it('should call GET /complaints without params', () => {
      service.getComplaints();
      expect(httpMock.get).toHaveBeenCalledWith(
        expect.stringContaining('/complaints'),
        expect.objectContaining({ params: expect.anything() })
      );
    });

    it('should include status param when provided', () => {
      service.getComplaints({ status: 'resolved' });
      const call = httpMock.get.mock.calls[0];
      expect(call[0]).toContain('/complaints');
    });
  });

  describe('getComplaint', () => {
    it('should call GET /complaints/:id', () => {
      service.getComplaint(5);
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/complaints/5'));
    });
  });

  describe('trackComplaint', () => {
    it('should call GET /complaints/track/:number', () => {
      service.trackComplaint('CMS-001');
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/complaints/track/CMS-001'));
    });
  });

  describe('fileComplaint', () => {
    it('should call POST /complaints', () => {
      const data = { category: 'bank' };
      service.fileComplaint(data);
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/complaints'), data);
    });
  });

  describe('updateComplaint', () => {
    it('should call PUT /complaints/:id', () => {
      const data = { status: 'resolved' };
      service.updateComplaint(3, data);
      expect(httpMock.put).toHaveBeenCalledWith(expect.stringContaining('/complaints/3'), data);
    });
  });

  describe('deleteComplaint', () => {
    it('should call DELETE /complaints/:id', () => {
      service.deleteComplaint(7);
      expect(httpMock.delete).toHaveBeenCalledWith(expect.stringContaining('/complaints/7'));
    });
  });

  describe('getTimeline', () => {
    it('should call GET /complaints/:id/timeline', () => {
      service.getTimeline(2);
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/complaints/2/timeline'));
    });
  });

  describe('getCategories', () => {
    it('should call GET /categories', () => {
      service.getCategories();
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/categories'));
    });
  });

  describe('getBanks', () => {
    it('should call GET /banks', () => {
      service.getBanks();
      expect(httpMock.get).toHaveBeenCalledWith(
        expect.stringContaining('/banks'),
        expect.anything()
      );
    });

    it('should pass type param', () => {
      service.getBanks('commercial');
      expect(httpMock.get).toHaveBeenCalled();
    });
  });

  describe('getFormSchema', () => {
    it('should call GET /form-config/:key', () => {
      service.getFormSchema('complaint_form');
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/form-config/complaint_form'));
    });
  });

  describe('Email Simulation', () => {
    it('simulateIncomingEmail should POST to /email-simulation/receive', () => {
      const data = { fromEmail: 'a@b.com', fromName: 'Test', subject: 'Subj', body: 'Body' };
      service.simulateIncomingEmail(data);
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/email-simulation/receive'), data);
    });

    it('replyWithForm should POST to /email-simulation/reply-with-form', () => {
      service.replyWithForm({ threadId: '1' });
      expect(httpMock.post).toHaveBeenCalledWith(expect.stringContaining('/email-simulation/reply-with-form'), expect.anything());
    });

    it('getEmailThreads should call GET', () => {
      service.getEmailThreads();
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/email-simulation/threads'));
    });

    it('getEmailThread should call GET with threadId', () => {
      service.getEmailThread('abc123');
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/email-simulation/threads/abc123'));
    });

    it('getEmailStats should call GET', () => {
      service.getEmailStats();
      expect(httpMock.get).toHaveBeenCalledWith(expect.stringContaining('/email-simulation/stats'));
    });
  });
});
