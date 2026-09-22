/**
 * Unit tests for TrackComplaintComponent utility functions.
 * These functions are tested as pure logic extracted from the component.
 */

describe('TrackComplaint - Utility Functions', () => {

  // ═══════ formatFileSize ═══════
  describe('formatFileSize', () => {
    const formatFileSize = (bytes: number): string => {
      if (!bytes) return '\u2014';
      if (bytes < 1024) return bytes + ' B';
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    };

    it('should return dash for 0 bytes', () => {
      expect(formatFileSize(0)).toBe('\u2014');
    });

    it('should return dash for null/undefined', () => {
      expect(formatFileSize(null as any)).toBe('\u2014');
      expect(formatFileSize(undefined as any)).toBe('\u2014');
    });

    it('should format bytes', () => {
      expect(formatFileSize(500)).toBe('500 B');
      expect(formatFileSize(1)).toBe('1 B');
      expect(formatFileSize(1023)).toBe('1023 B');
    });

    it('should format kilobytes', () => {
      expect(formatFileSize(1024)).toBe('1 KB');
      expect(formatFileSize(5120)).toBe('5 KB');
      expect(formatFileSize(1048575)).toBe('1024 KB');
    });

    it('should format megabytes', () => {
      expect(formatFileSize(1048576)).toBe('1.0 MB');
      expect(formatFileSize(5242880)).toBe('5.0 MB');
      expect(formatFileSize(10485760)).toBe('10.0 MB');
    });
  });

  // ═══════ getFileIcon ═══════
  describe('getFileIcon', () => {
    const getFileIcon = (fileName: string): string => {
      if (!fileName) return 'pi-file';
      const ext = fileName.split('.').pop()?.toLowerCase() || '';
      if (['pdf'].includes(ext)) return 'pi-file-pdf';
      if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) return 'pi-image';
      if (['doc', 'docx'].includes(ext)) return 'pi-file-word';
      if (['xls', 'xlsx', 'csv'].includes(ext)) return 'pi-file-excel';
      if (['zip', 'rar', '7z'].includes(ext)) return 'pi-box';
      return 'pi-file';
    };

    it('should return pi-file for empty filename', () => {
      expect(getFileIcon('')).toBe('pi-file');
      expect(getFileIcon(null as any)).toBe('pi-file');
    });

    it('should detect PDF files', () => {
      expect(getFileIcon('document.pdf')).toBe('pi-file-pdf');
      expect(getFileIcon('FILE.PDF')).toBe('pi-file-pdf');
    });

    it('should detect image files', () => {
      expect(getFileIcon('photo.jpg')).toBe('pi-image');
      expect(getFileIcon('image.jpeg')).toBe('pi-image');
      expect(getFileIcon('screenshot.png')).toBe('pi-image');
      expect(getFileIcon('animation.gif')).toBe('pi-image');
      expect(getFileIcon('pic.bmp')).toBe('pi-image');
      expect(getFileIcon('modern.webp')).toBe('pi-image');
    });

    it('should detect Word documents', () => {
      expect(getFileIcon('letter.doc')).toBe('pi-file-word');
      expect(getFileIcon('report.docx')).toBe('pi-file-word');
    });

    it('should detect Excel files', () => {
      expect(getFileIcon('data.xls')).toBe('pi-file-excel');
      expect(getFileIcon('sheet.xlsx')).toBe('pi-file-excel');
      expect(getFileIcon('export.csv')).toBe('pi-file-excel');
    });

    it('should detect archives', () => {
      expect(getFileIcon('files.zip')).toBe('pi-box');
      expect(getFileIcon('backup.rar')).toBe('pi-box');
      expect(getFileIcon('compressed.7z')).toBe('pi-box');
    });

    it('should return pi-file for unknown extensions', () => {
      expect(getFileIcon('data.json')).toBe('pi-file');
      expect(getFileIcon('script.py')).toBe('pi-file');
      expect(getFileIcon('noextension')).toBe('pi-file');
    });
  });

  // ═══════ getPreviewType ═══════
  describe('getPreviewType', () => {
    const getPreviewType = (fileName: string, contentType?: string): string => {
      const ext = (fileName.split('.').pop() || '').toLowerCase();
      const mime = (contentType || '').toLowerCase();
      if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(ext) || mime.startsWith('image/')) return 'image';
      if (['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'].includes(ext) || mime.startsWith('video/')) return 'video';
      if (['mp3', 'wav', 'ogg', 'aac', 'flac', 'wma'].includes(ext) || mime.startsWith('audio/')) return 'audio';
      if (ext === 'pdf' || mime === 'application/pdf') return 'pdf';
      return 'other';
    };

    it('should detect image types by extension', () => {
      expect(getPreviewType('file.jpg')).toBe('image');
      expect(getPreviewType('file.jpeg')).toBe('image');
      expect(getPreviewType('file.png')).toBe('image');
      expect(getPreviewType('file.gif')).toBe('image');
      expect(getPreviewType('file.bmp')).toBe('image');
      expect(getPreviewType('file.webp')).toBe('image');
      expect(getPreviewType('file.svg')).toBe('image');
    });

    it('should detect image types by MIME', () => {
      expect(getPreviewType('unknown', 'image/png')).toBe('image');
      expect(getPreviewType('unknown', 'image/gif')).toBe('image');
    });

    it('should detect video types by extension', () => {
      expect(getPreviewType('video.mp4')).toBe('video');
      expect(getPreviewType('video.webm')).toBe('video');
      expect(getPreviewType('video.mov')).toBe('video');
      expect(getPreviewType('video.avi')).toBe('video');
      expect(getPreviewType('video.mkv')).toBe('video');
    });

    it('should detect video types by MIME', () => {
      expect(getPreviewType('file', 'video/mp4')).toBe('video');
      expect(getPreviewType('file', 'video/webm')).toBe('video');
    });

    it('should detect audio types by extension', () => {
      expect(getPreviewType('song.mp3')).toBe('audio');
      expect(getPreviewType('sound.wav')).toBe('audio');
      expect(getPreviewType('track.aac')).toBe('audio');
      expect(getPreviewType('music.flac')).toBe('audio');
      expect(getPreviewType('audio.wma')).toBe('audio');
    });

    it('should detect audio types by MIME', () => {
      expect(getPreviewType('file', 'audio/mpeg')).toBe('audio');
      expect(getPreviewType('file', 'audio/wav')).toBe('audio');
    });

    it('should detect PDF', () => {
      expect(getPreviewType('document.pdf')).toBe('pdf');
      expect(getPreviewType('file', 'application/pdf')).toBe('pdf');
    });

    it('should return other for unknown types', () => {
      expect(getPreviewType('file.docx')).toBe('other');
      expect(getPreviewType('file.xlsx')).toBe('other');
      expect(getPreviewType('file.zip')).toBe('other');
      expect(getPreviewType('file.txt')).toBe('other');
      expect(getPreviewType('noext')).toBe('other');
    });
  });

  // ═══════ mapStatus ═══════
  describe('mapStatus', () => {
    const mapStatus = (status: string): string => {
      const s = (status || '').toLowerCase();
      if (s.includes('reject')) return 'rejected';
      if (s.includes('sent back') || s.includes('request')) return 'request_sent_back';
      if (s.includes('resolve')) return 'resolved';
      return 'in_progress';
    };

    it('should map rejected statuses', () => {
      expect(mapStatus('Rejected')).toBe('rejected');
      expect(mapStatus('REJECTED')).toBe('rejected');
      expect(mapStatus('Request Rejected')).toBe('rejected');
    });

    it('should map sent back statuses', () => {
      expect(mapStatus('Request Sent Back')).toBe('request_sent_back');
      expect(mapStatus('sent back to user')).toBe('request_sent_back');
    });

    it('should map resolved statuses', () => {
      expect(mapStatus('Resolved')).toBe('resolved');
      expect(mapStatus('RESOLVED')).toBe('resolved');
    });

    it('should default to in_progress', () => {
      expect(mapStatus('In Progress')).toBe('in_progress');
      expect(mapStatus('New')).toBe('in_progress');
      expect(mapStatus('')).toBe('in_progress');
      expect(mapStatus(null as any)).toBe('in_progress');
    });
  });

  // ═══════ mapAction ═══════
  describe('mapAction', () => {
    const mapAction = (status: string): string => {
      const s = (status || '').toLowerCase();
      if (s.includes('reject')) return 'act';
      if (s.includes('sent back') || s.includes('request')) return 'appeal';
      return 'withdraw';
    };

    it('should map rejected to act', () => {
      expect(mapAction('Rejected')).toBe('act');
    });

    it('should map sent back to appeal', () => {
      expect(mapAction('Request Sent Back')).toBe('appeal');
    });

    it('should default to withdraw', () => {
      expect(mapAction('In Progress')).toBe('withdraw');
      expect(mapAction('')).toBe('withdraw');
    });
  });

  // ═══════ formatStatus ═══════
  describe('formatStatus', () => {
    const formatStatus = (status: string): string => {
      if (!status) return '\u2014';
      return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    };

    it('should return dash for empty', () => {
      expect(formatStatus('')).toBe('\u2014');
      expect(formatStatus(null as any)).toBe('\u2014');
    });

    it('should format underscore-separated status', () => {
      expect(formatStatus('in_progress')).toBe('In Progress');
      expect(formatStatus('request_sent_back')).toBe('Request Sent Back');
    });

    it('should capitalize single words', () => {
      expect(formatStatus('resolved')).toBe('Resolved');
      expect(formatStatus('rejected')).toBe('Rejected');
    });
  });

  // ═══════ filteredComplaints logic ═══════
  describe('filteredComplaints', () => {
    const complaints = [
      { id: 'CMS-001', complaintAgainst: 'Adarsh Bank', complaintDate: '15-08-2026', status: 'in_progress', comments: 'Missing details' },
      { id: 'CMS-002', complaintAgainst: 'Varada Bank', complaintDate: '03-02-2026', status: 'rejected', comments: 'Duplicate complaint' },
      { id: 'CMS-003', complaintAgainst: 'Kaveri Bank', complaintDate: '22-11-2025', status: 'resolved', comments: 'Issue fixed' },
    ];

    const filter = (filters: any) => {
      return complaints.filter(c => {
        if (filters.id && !c.id.toLowerCase().includes(filters.id.toLowerCase())) return false;
        if (filters.against && !c.complaintAgainst.toLowerCase().includes(filters.against.toLowerCase())) return false;
        if (filters.date && !c.complaintDate.includes(filters.date)) return false;
        if (filters.status && c.status !== filters.status) return false;
        if (filters.comments && !c.comments.toLowerCase().includes(filters.comments.toLowerCase())) return false;
        return true;
      });
    };

    it('should return all when no filters', () => {
      expect(filter({})).toHaveLength(3);
    });

    it('should filter by ID', () => {
      expect(filter({ id: '001' })).toHaveLength(1);
      expect(filter({ id: 'CMS' })).toHaveLength(3);
    });

    it('should filter by complaint against', () => {
      expect(filter({ against: 'adarsh' })).toHaveLength(1);
      expect(filter({ against: 'bank' })).toHaveLength(3);
    });

    it('should filter by date', () => {
      expect(filter({ date: '15-08' })).toHaveLength(1);
    });

    it('should filter by status', () => {
      expect(filter({ status: 'rejected' })).toHaveLength(1);
      expect(filter({ status: 'in_progress' })).toHaveLength(1);
    });

    it('should filter by comments', () => {
      expect(filter({ comments: 'missing' })).toHaveLength(1);
    });

    it('should combine multiple filters', () => {
      expect(filter({ against: 'bank', status: 'resolved' })).toHaveLength(1);
    });

    it('should return empty for no match', () => {
      expect(filter({ id: 'NONEXIST' })).toHaveLength(0);
    });
  });

  // ═══════ getStatusClass ═══════
  describe('getStatusClass', () => {
    const getStatusClass = (status: string): string => {
      switch (status) {
        case 'pending': return 'status-pending';
        case 'in_progress': return 'status-progress';
        case 'resolved': return 'status-resolved';
        case 'escalated': return 'status-escalated';
        case 'closed': return 'status-closed';
        default: return '';
      }
    };

    it('should return correct class for each status', () => {
      expect(getStatusClass('pending')).toBe('status-pending');
      expect(getStatusClass('in_progress')).toBe('status-progress');
      expect(getStatusClass('resolved')).toBe('status-resolved');
      expect(getStatusClass('escalated')).toBe('status-escalated');
      expect(getStatusClass('closed')).toBe('status-closed');
    });

    it('should return empty for unknown status', () => {
      expect(getStatusClass('unknown')).toBe('');
      expect(getStatusClass('')).toBe('');
    });
  });
});
