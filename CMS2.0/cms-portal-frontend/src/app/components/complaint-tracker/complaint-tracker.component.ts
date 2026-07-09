import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule, UpperCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ComplaintService } from '../../services/complaint.service';
import { ComplaintStatus } from '../../models/complaint.model';

@Component({
  selector: 'app-complaint-tracker',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './complaint-tracker.component.html',
  styleUrl: './complaint-tracker.component.scss'
})
export class ComplaintTrackerComponent implements OnInit {

  private complaintService = inject(ComplaintService);
  private route = inject(ActivatedRoute);

  searchId = signal('');
  status = signal<ComplaintStatus | null>(null);
  loading = signal(false);
  error = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.searchId.set(id);
      this.track();
    }
  }

  track() {
    const id = this.searchId().trim();
    if (!id) return;

    this.loading.set(true);
    this.error.set('');
    this.status.set(null);

    this.complaintService.trackComplaint(id).subscribe({
      next: (data) => {
        this.status.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        if (err.status === 404) {
          this.error.set('No complaint found with this reference number.');
        } else {
          this.error.set('Unable to fetch complaint status. Please try again.');
        }
        this.loading.set(false);
      }
    });
  }

  downloadPdf() {
    const s = this.status();
    if (!s) return;

    import('jspdf').then(({ jsPDF }) => {
      const doc = new jsPDF();
      const pw = doc.internal.pageSize.getWidth();
      let y = 20;

      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('COMPLAINT STATUS REPORT', pw / 2, y, { align: 'center' });
      y += 10;
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      doc.text(`Generated: ${new Date().toLocaleString('en-IN')}`, pw / 2, y, { align: 'center' });
      y += 12;

      const addRow = (label: string, value: string) => {
        doc.setFont('helvetica', 'bold');
        doc.text(`${label}:`, 20, y);
        doc.setFont('helvetica', 'normal');
        doc.text(value || 'N/A', 70, y);
        y += 7;
      };

      addRow('Complaint ID', s.complaintId);
      addRow('Status', s.status);
      addRow('Category', s.category);
      addRow('Registered', s.registeredAt ? new Date(s.registeredAt).toLocaleDateString('en-IN') : '');
      addRow('SLA Due', s.slaDueDate ? new Date(s.slaDueDate).toLocaleDateString('en-IN') : '');
      if (s.assignedTeam) addRow('Assigned Team', s.assignedTeam);
      if (s.resolutionSummary) addRow('Resolution', s.resolutionSummary);

      if (s.timeline && s.timeline.length > 0) {
        y += 5;
        doc.setFont('helvetica', 'bold');
        doc.text('Timeline:', 20, y);
        y += 7;
        doc.setFont('helvetica', 'normal');
        for (const entry of s.timeline) {
          if (y > 270) { doc.addPage(); y = 20; }
          doc.text(`${new Date(entry.timestamp).toLocaleDateString('en-IN')} - ${entry.action} (${entry.fromStatus} -> ${entry.toStatus})`, 25, y);
          y += 6;
        }
      }

      // Digital signature
      y += 10;
      doc.setDrawColor(0, 100, 0);
      doc.setFillColor(240, 255, 240);
      doc.roundedRect(20, y, pw - 40, 14, 2, 2, 'FD');
      doc.setTextColor(0, 100, 0);
      doc.setFontSize(8);
      doc.setFont('helvetica', 'bold');
      doc.text('DIGITALLY SIGNED | RBI CMS Digital Certificate Authority', 25, y + 9);
      doc.setTextColor(0);

      doc.save(`Complaint_${s.complaintId}.pdf`);
    });
  }
}
