import { Component, inject, signal } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { EmailIngestRequest, EmailDraft } from '../../../models/email-syndication.model';

interface SimulationResult {
  email: EmailIngestRequest;
  response: EmailDraft | null;
  message: string;
  status: 'success' | 'ignored' | 'error';
  timestamp: Date;
}

@Component({
  selector: 'app-email-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule, KeyValuePipe],
  templateUrl: './email-simulator.component.html',
  styleUrl: './email-simulator.component.scss'
})
export class EmailSimulatorComponent {

  private emailService = inject(EmailSyndicationService);
  private router = inject(Router);

  mode = signal<'manual' | 'batch'>('manual');
  sending = signal(false);
  results = signal<SimulationResult[]>([]);

  // Manual form
  manualEmail = signal<EmailIngestRequest>({
    senderEmail: '',
    subject: '',
    body: '',
    messageId: ''
  });

  // Attachment
  attachedFile = signal<File | null>(null);
  ocrExtracted = signal<Record<string, string> | null>(null);

  // Batch simulation
  batchCount = signal(5);
  batchDelay = signal(1000);
  batchRunning = signal(false);
  batchProgress = signal(0);

  sampleEmails: EmailIngestRequest[] = [
    {
      senderEmail: 'mohan.kumar@gmail.com',
      subject: 'ATM swallowed my card and debited money',
      body: 'Dear Sir,\n\nOn 28th May 2026 at 9:30 PM, I went to withdraw Rs 20,000 from PNB ATM (ID: PNB-ATM-4521) at Connaught Place, New Delhi. The machine swallowed my debit card and the screen went blank. However, Rs 20,000 was debited from my account (A/C: 12345678901234).\n\nI have reported to the bank branch but no action taken in 35 days.\n\nName: Mohan Kumar\nPhone: 9811234567\nBank: Punjab National Bank\nBranch: Connaught Place, Delhi',
      messageId: ''
    },
    {
      senderEmail: 'priya.singh@yahoo.co.in',
      subject: 'Unauthorized UPI transaction of Rs 45000',
      body: 'Hello,\n\nI am writing to report unauthorized UPI transactions from my account.\n\n3 transactions were made without my knowledge:\n1. Rs 15,000 on 20-May-2026 (UPI Ref: 612345678901)\n2. Rs 15,000 on 20-May-2026 (UPI Ref: 612345678902)\n3. Rs 15,000 on 21-May-2026 (UPI Ref: 612345678903)\n\nI did not authorize any of these. My UPI PIN was never shared.\n\nName: Priya Singh\nPhone: 7890123456\nBank: State Bank of India\nUPI ID: priya.singh@sbi',
      messageId: ''
    },
    {
      senderEmail: 'suresh.reddy@hotmail.com',
      subject: 'Home loan EMI wrongly increased without notice',
      body: 'Dear RBI Ombudsman,\n\nMy home loan EMI with ICICI Bank has been increased from Rs 35,000 to Rs 42,000 without any prior notice or communication. Loan Account: HL/2020/MUM/78901.\n\nI have written to the bank 4 times (complaints dated 01-Apr, 15-Apr, 01-May, 15-May 2026) but received no satisfactory response.\n\nThe interest rate revision was not communicated as per RBI guidelines on transparency.\n\nSuresh Reddy\n9876012345\nHyderabad, Telangana\nCPGRAMS: CPGRS/E/2026/0056789',
      messageId: ''
    },
    {
      senderEmail: 'kavita.joshi@gmail.com',
      subject: 'Credit card billing dispute - charges for cancelled subscription',
      body: 'To Whom It May Concern,\n\nI cancelled my subscription service (Netflix) on 1st April 2026 and informed Axis Bank to stop recurring payments on my credit card (ending 4532). Despite this:\n\n- Rs 1,499 charged on 1-May-2026\n- Rs 1,499 charged on 1-Jun-2026\n\nBank refuses to reverse saying \"merchant responsibility\". This violates RBI circular on recurring payment mandates.\n\nKavita Joshi\nCard: XXXX-XXXX-XXXX-4532\nPhone: 8765098712\nAxis Bank, Pune Branch',
      messageId: ''
    },
    {
      senderEmail: 'ahmed.khan@outlook.com',
      subject: 'NEFT amount not credited to beneficiary for 10 days',
      body: 'Sir/Madam,\n\nI transferred Rs 5,00,000 via NEFT from my Bank of Baroda account to HDFC Bank account on 22-May-2026.\n\nTransaction Reference: BARB0NEFT2026052200123456\nSender A/C: 98765432100 (Bank of Baroda, Lucknow)\nBeneficiary A/C: 12345678900 (HDFC Bank, Mumbai)\n\nAmount was debited from my account but NOT credited to beneficiary even after 10 working days. Both banks are blaming each other.\n\nAhmed Khan\nPhone: 9012345678\nLucknow, UP',
      messageId: ''
    },
    {
      senderEmail: 'lakshmi.nair@gmail.com',
      subject: 'Fixed deposit premature closure penalty excessive',
      body: 'Dear Ombudsman,\n\nI had an FD of Rs 10,00,000 with Canara Bank (FD No: FD/2024/BLR/45678) for 3 years. Due to medical emergency, I requested premature closure.\n\nBank deducted Rs 75,000 as penalty which seems excessive compared to RBI guidelines (1% max). When I questioned, they cited \"special scheme terms\" which were never disclosed at the time of booking.\n\nLakshmi Nair\n9944556677\nBangalore, Karnataka',
      messageId: ''
    },
    {
      senderEmail: 'vikram.mehta@gmail.com',
      subject: 'Insurance mis-selling by bank staff',
      body: 'Dear Sir,\n\nI visited SBI branch (Andheri West, Mumbai) on 10-April-2026 to open a Fixed Deposit. The branch manager forced me to buy a ULIP insurance policy (Policy: LI/2026/MUM/89012, Premium: Rs 1,50,000) saying it was mandatory for FD.\n\nThis is clear mis-selling. I want the policy cancelled and full refund of premium.\n\nVikram Mehta\nSBI A/C: 38765432100\nPhone: 9988776655\nMumbai',
      messageId: ''
    },
    {
      senderEmail: 'deepa.agarwal@rediffmail.com',
      subject: 'Pension not credited for 3 months',
      body: 'Respected Sir/Madam,\n\nI am a retired government employee. My pension (Rs 45,000/month) has not been credited for May, June 2026 by Union Bank of India.\n\nPension A/C: 5432109876 (Union Bank, Varanasi branch)\nPPO No: PPO/2015/UP/34567\n\nI am 72 years old and this is my only source of income. The bank says \"technical issue\" but 3 months is unacceptable.\n\nDeepa Agarwal\nPhone: 8877665544\nVaranasi, UP',
      messageId: ''
    }
  ];

  onFileAttached(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];
      if (!allowed.includes(file.type)) {
        this.attachedFile.set(null);
        input.value = '';
        return;
      }
      this.attachedFile.set(file);
    }
  }

  removeAttachment() {
    this.attachedFile.set(null);
    this.ocrExtracted.set(null);
  }

  updateManualField(field: string, value: string) {
    this.manualEmail.update(prev => ({ ...prev, [field]: value }));
  }

  sendManual() {
    const email = this.manualEmail();
    if (!email.senderEmail || !email.subject) return;

    if (!email.messageId) {
      email.messageId = `sim-${Date.now()}@simulator.local`;
    }

    this.sending.set(true);
    const file = this.attachedFile();

    const request$ = file
      ? this.emailService.ingestEmailWithAttachment(email, file)
      : this.emailService.ingestEmail(email);

    request$.subscribe({
      next: (response) => {
        const ocrFields = (response as any)?.ocrExtractedFields || null;
        if (ocrFields) {
          this.ocrExtracted.set(ocrFields);
        }

        const hasOcr = response?.ocrProcessed;
        const attachmentInfo = file ? ` [📎 ${file.name}${hasOcr ? ' → OCR extracted' : ''}]` : '';

        this.results.update(prev => [{
          email,
          response,
          message: response
            ? `Draft created: ${response.draftId} → Assigned to ${response.assignedTo}${attachmentInfo}`
            : 'Email ignored (on ignore list)',
          status: response ? 'success' : 'ignored',
          timestamp: new Date()
        }, ...prev]);
        this.sending.set(false);
        this.manualEmail.set({ senderEmail: '', subject: '', body: '', messageId: '' });
        this.attachedFile.set(null);
      },
      error: (err) => {
        this.results.update(prev => [{
          email,
          response: null,
          message: err.error?.message || 'Ingestion failed',
          status: 'error',
          timestamp: new Date()
        }, ...prev]);
        this.sending.set(false);
      }
    });
  }

  fillSample(index: number) {
    const sample = this.sampleEmails[index % this.sampleEmails.length];
    this.manualEmail.set({
      ...sample,
      messageId: `sim-${Date.now()}@simulator.local`
    });
  }

  async runBatch() {
    this.batchRunning.set(true);
    this.batchProgress.set(0);
    const count = this.batchCount();
    const delay = this.batchDelay();

    for (let i = 0; i < count; i++) {
      const sample = this.sampleEmails[i % this.sampleEmails.length];
      const email: EmailIngestRequest = {
        ...sample,
        messageId: `sim-batch-${Date.now()}-${i}@simulator.local`
      };

      try {
        const response = await this.emailService.ingestEmail(email).toPromise();
        this.results.update(prev => [{
          email,
          response: response || null,
          message: response ? `Draft ${response.draftId} → ${response.assignedTo}` : 'Ignored',
          status: response ? 'success' : 'ignored',
          timestamp: new Date()
        }, ...prev]);
      } catch (err: any) {
        this.results.update(prev => [{
          email,
          response: null,
          message: err.error?.message || 'Failed',
          status: 'error',
          timestamp: new Date()
        }, ...prev]);
      }

      this.batchProgress.set(i + 1);
      if (i < count - 1) {
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }

    this.batchRunning.set(false);
  }

  clearResults() {
    this.results.set([]);
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }
}
