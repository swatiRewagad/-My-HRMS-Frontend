import { Component, inject, signal } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { EmailIngestRequest, EmailDraft } from '../../../models/email-syndication.model';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

interface SimulationResult {
  email: EmailIngestRequest;
  response: EmailDraft | null;
  message: string;
  status: 'success' | 'ignored' | 'error';
  timestamp: Date;
  languageInfo?: {
    detectedLanguage: string;
    languageName: string;
    isVernacular: boolean;
    translationConfidence: number;
  };
}

@Component({
  selector: 'app-email-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule, KeyValuePipe, SpeechButtonComponent],
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

  // Attachments (multiple)
  attachedFiles = signal<File[]>([]);
  ocrExtracted = signal<Record<string, string> | null>(null);

  // backward-compat alias used in template
  get attachedFile() { return this.attachedFiles().length > 0 ? this.attachedFiles()[0] : null; }

  // Speech-to-Text
  isListening = signal(false);
  sttSupported = typeof window !== 'undefined' && ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window);
  private recognition: any = null;

  toggleSpeechToText() {
    if (this.isListening()) {
      this.recognition?.stop();
      this.isListening.set(false);
      return;
    }
    if (!this.sttSupported) return;
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    this.recognition = new SpeechRecognition();
    this.recognition.lang = 'en-IN';
    this.recognition.continuous = true;
    this.recognition.interimResults = false;
    this.recognition.onresult = (event: any) => {
      const transcript = Array.from(event.results as any[])
        .slice(event.resultIndex)
        .map((r: any) => r[0].transcript)
        .join(' ');
      const current = this.manualEmail().body;
      this.updateManualField('body', current ? current + ' ' + transcript : transcript);
    };
    this.recognition.onerror = () => this.isListening.set(false);
    this.recognition.onend = () => this.isListening.set(false);
    this.recognition.start();
    this.isListening.set(true);
  }

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
    },
    // Regional language samples
    {
      senderEmail: 'rajesh.sharma@gmail.com',
      subject: 'ATM से पैसे कटे लेकिन नहीं मिले (Hindi)',
      body: 'महोदय,\n\nमैं राजेश शर्मा, खाता संख्या 3456789012 (भारतीय स्टेट बैंक, जयपुर शाखा)। दिनांक 25 मई 2026 को मैंने SBI ATM (ID: SBI-ATM-7890) से ₹10,000 निकालने का प्रयास किया। मशीन से पैसे नहीं निकले लेकिन मेरे खाते से ₹10,000 कट गए।\n\nमैंने बैंक शाखा में शिकायत दर्ज कराई (शिकायत संख्या: CMP/2026/JAI/4567) लेकिन 30 दिन बीत जाने के बाद भी कोई समाधान नहीं मिला।\n\nकृपया मेरी सहायता करें।\n\nराजेश शर्मा\nफोन: 9414567890\nजयपुर, राजस्थान',
      messageId: ''
    },
    {
      senderEmail: 'ananya.das@yahoo.com',
      subject: 'ব্যাংক থেকে অননুমোদিত লেনদেন (Bengali)',
      body: 'মাননীয় মহোদয়,\n\nআমি অনন্যা দাস, অ্যাকাউন্ট নম্বর 7890123456 (পাঞ্জাব ন্যাশনাল ব্যাংক, কলকাতা শাখা)। গত 20 মে 2026 তারিখে আমার অ্যাকাউন্ট থেকে আমার অজান্তেই ₹35,000 টাকা UPI-র মাধ্যমে কেটে নেওয়া হয়েছে।\n\nUPI Ref: 723456789012\n\nআমি কোনো লেনদেন করিনি এবং আমার UPI PIN কাউকে দিইনি। ব্যাংকে অভিযোগ করেছি কিন্তু কোনো সমাধান পাইনি।\n\nঅনন্যা দাস\nফোন: 9830123456\nকলকাতা, পশ্চিমবঙ্গ',
      messageId: ''
    },
    {
      senderEmail: 'murugan.s@gmail.com',
      subject: 'வங்கிக் கடன் அதிக வட்டி (Tamil)',
      body: 'மதிப்பிற்குரிய அய்யா,\n\nஎன் பெயர் முருகன், கணக்கு எண் 5678901234 (இந்தியன் வங்கி, சென்னை கிளை). நான் ₹5,00,000 தனிநபர் கடன் பெற்றேன். ஒப்பந்தத்தின்படி வட்டி விகிதம் 10.5% ஆக இருக்க வேண்டும், ஆனால் வங்கி 14% வட்டி வசூலிக்கிறது.\n\nநான் பல முறை வங்கிக் கிளையில் புகார் அளித்தேன் (புகார் எண்: CMP/2026/CHN/8901) ஆனால் எந்த நடவடிக்கையும் எடுக்கவில்லை.\n\nதயவுசெய்து எனக்கு உதவுங்கள்.\n\nமுருகன் எஸ்\nதொலைபேசி: 9442345678\nசென்னை, தமிழ்நாடு',
      messageId: ''
    },
    {
      senderEmail: 'sneha.patil@gmail.com',
      subject: 'बँकेने चुकीचे व्याज आकारले (Marathi)',
      body: 'महोदय,\n\nमाझे नाव स्नेहा पाटील, खाते क्रमांक 2345678901 (बँक ऑफ महाराष्ट्र, पुणे शाखा). माझ्या गृहकर्जाचा EMI ₹28,000 वरून ₹35,000 करण्यात आला आहे, कोणतीही पूर्वसूचना न देता.\n\nकर्ज खाते: HL/2023/PUN/56789\n\nमी बँकेत 3 वेळा तक्रार केली (दि. 01-एप्रिल, 15-एप्रिल, 01-मे 2026) परंतु कोणतेही समाधानकारक उत्तर मिळाले नाही.\n\nकृपया मदत करा.\n\nस्नेहा पाटील\nफोन: 9822345678\nपुणे, महाराष्ट्र',
      messageId: ''
    },
    {
      senderEmail: 'hardik.patel@gmail.com',
      subject: 'FD માં ખોટી પેનલ્ટી લગાવી (Gujarati)',
      body: 'માનનીય મહોદય,\n\nમારું નામ હાર્દિક પટેલ છે, ખાતા નંબર 8901234567 (બેંક ઓફ બરોડા, અમદાવાદ શાખા). મારી ₹8,00,000 ની FD (FD No: FD/2024/AMD/34567) ને તબીબી કટોકટીને કારણે સમય પહેલાં બંધ કરવી પડી.\n\nબેંકે ₹60,000 પેનલ્ટી કાપી જે RBI માર્ગદર્શિકા (મહત્તમ 1%) કરતાં વધારે છે.\n\nહાર્દિક પટેલ\nફોન: 9879012345\nઅમદાવાદ, ગુજરાત',
      messageId: ''
    },
    {
      senderEmail: 'venkat.reddy@gmail.com',
      subject: 'బ్యాంకు ఖాతా నుండి అనధికార డెబిట్ (Telugu)',
      body: 'గౌరవనీయ మహోదయా,\n\nనా పేరు వెంకట్ రెడ్డి, ఖాతా నంబర్ 6789012345 (ఆంధ్రా బ్యాంక్, హైదరాబాద్ శాఖ). 2026 మే 18న నా ఖాతా నుండి నాకు తెలియకుండా ₹25,000 డెబిట్ అయింది.\n\nTransaction Ref: TXN/2026/HYD/78901\n\nనేను ఎటువంటి లావాదేవీ చేయలేదు. బ్యాంకుకు ఫిర్యాదు చేసినా 45 రోజులుగా ఎటువంటి పరిష్కారం లేదు.\n\nదయచేసి సహాయం చేయండి.\n\nవెంకట్ రెడ్డి\nఫోన్: 9848012345\nహైదరాబాద్, తెలంగాణ',
      messageId: ''
    },
    {
      senderEmail: 'prakash.gowda@gmail.com',
      subject: 'ಸಾಲದ ಮೇಲೆ ಅಧಿಕ ಬಡ್ಡಿ ವಿಧಿಸಲಾಗಿದೆ (Kannada)',
      body: 'ಗೌರವಾನ್ವಿತ ಮಹೋದಯರೇ,\n\nನನ್ನ ಹೆಸರು ಪ್ರಕಾಶ್ ಗೌಡ, ಖಾತೆ ಸಂಖ್ಯೆ 4567890123 (ಕೆನರಾ ಬ್ಯಾಂಕ್, ಬೆಂಗಳೂರು ಶಾಖೆ). ನಾನು ₹3,00,000 ವೈಯಕ್ತಿಕ ಸಾಲ ಪಡೆದಿದ್ದೇನೆ. ಒಪ್ಪಂದದ ಪ್ರಕಾರ ಬಡ್ಡಿ ದರ 11% ಆಗಿರಬೇಕು, ಆದರೆ ಬ್ಯಾಂಕ್ 15.5% ಬಡ್ಡಿ ವಿಧಿಸುತ್ತಿದೆ.\n\nSanction Letter: PL/2025/BLR/23456\n\nನಾನು ಬ್ಯಾಂಕ್ ಶಾಖೆಯಲ್ಲಿ ಹಲವು ಬಾರಿ ದೂರು ನೀಡಿದ್ದೇನೆ ಆದರೆ ಯಾವುದೇ ಪರಿಹಾರ ಸಿಕ್ಕಿಲ್ಲ.\n\nಪ್ರಕಾಶ್ ಗೌಡ\nಫೋನ್: 9900123456\nಬೆಂಗಳೂರು, ಕರ್ನಾಟಕ',
      messageId: ''
    },
    {
      senderEmail: 'arun.nair@gmail.com',
      subject: 'ബാങ്ക് ലോക്കർ തുറക്കാൻ അനുവദിക്കുന്നില്ല (Malayalam)',
      body: 'ബഹുമാനപ്പെട്ട മഹോദയൻ,\n\nഎന്റെ പേര് അരുൺ നായർ, അക്കൗണ്ട് നമ്പർ 3456789012 (ഫെഡറൽ ബാങ്ക്, കൊച്ചി ശാഖ). എന്റെ ബാങ്ക് ലോക്കർ (Locker No: LKR/2022/KCH/567) തുറക്കാൻ ബാങ്ക് അനുവദിക്കുന്നില്ല. കഴിഞ്ഞ 2 മാസമായി ലോക്കർ ആക്‌സസ് നിഷേധിച്ചിരിക്കുന്നു.\n\nലോക്കർ വാടക കൃത്യമായി അടച്ചിട്ടുണ്ട്. ബാങ്ക് ഒരു കാരണവും പറയുന്നില്ല.\n\nദയവായി സഹായിക്കുക.\n\nഅരുൺ നായർ\nഫോൺ: 9446012345\nകൊച്ചി, കേരളം',
      messageId: ''
    },
    {
      senderEmail: 'gurpreet.singh@gmail.com',
      subject: 'ਬੈਂਕ ਨੇ ਲੋਨ ਬਿਨਾਂ ਸੂਚਨਾ ਰੱਦ ਕੀਤਾ (Punjabi)',
      body: 'ਮਾਨਯੋਗ ਮਹੋਦਯ,\n\nਮੇਰਾ ਨਾਮ ਗੁਰਪ੍ਰੀਤ ਸਿੰਘ ਹੈ, ਖਾਤਾ ਨੰਬਰ 5678901234 (ਪੰਜਾਬ ਨੈਸ਼ਨਲ ਬੈਂਕ, ਅੰਮ੍ਰਿਤਸਰ ਸ਼ਾਖਾ). ਮੈਨੂੰ ₹10,00,000 ਦਾ ਹੋਮ ਲੋਨ ਮਨਜ਼ੂਰ ਹੋਇਆ ਸੀ (Sanction Ref: HL/2026/ASR/12345) ਪਰ ਬੈਂਕ ਨੇ ਬਿਨਾਂ ਕੋਈ ਕਾਰਨ ਦੱਸੇ ਲੋਨ ਰੱਦ ਕਰ ਦਿੱਤਾ।\n\nਮੈਂ ਪਹਿਲਾਂ ਹੀ ₹50,000 ਪ੍ਰੋਸੈਸਿੰਗ ਫੀਸ ਅਦਾ ਕਰ ਚੁੱਕਾ ਹਾਂ ਜੋ ਵਾਪਸ ਨਹੀਂ ਕੀਤੀ ਗਈ।\n\nਗੁਰਪ੍ਰੀਤ ਸਿੰਘ\nਫ਼ੋਨ: 9876543210\nਅੰਮ੍ਰਿਤਸਰ, ਪੰਜਾਬ',
      messageId: ''
    },
    {
      senderEmail: 'bijay.mohapatra@gmail.com',
      subject: 'ବ୍ୟାଙ୍କ ଖାତାରୁ ଅନଧିକୃତ ଟଙ୍କା କାଟିବା (Odia)',
      body: 'ମାନ୍ୟବର ମହୋଦୟ,\n\nମୋ ନାମ ବିଜୟ ମହାପାତ୍ର, ଖାତା ନମ୍ବର 7890123456 (ଷ୍ଟେଟ ବ୍ୟାଙ୍କ ଅଫ ଇଣ୍ଡିଆ, ଭୁବନେଶ୍ୱର ଶାଖା). ଗତ 15 ମଇ 2026 ତାରିଖରେ ମୋ ଖାତାରୁ ₹18,000 ଟଙ୍କା ମୋ ଅଜ୍ଞାତରେ କାଟି ନିଆଯାଇଛି।\n\nTransaction Ref: SBI/2026/BBR/45678\n\nମୁଁ ଏହି ଲେନଦେନ କରି ନାହିଁ। ବ୍ୟାଙ୍କରେ ଅଭିଯୋଗ କରିଛି କିନ୍ତୁ 40 ଦିନ ହେଲା କୌଣସି ସମାଧାନ ମିଳିନାହିଁ।\n\nବିଜୟ ମହାପାତ୍ର\nଫୋନ: 9437012345\nଭୁବନେଶ୍ୱର, ଓଡ଼ିଶା',
      messageId: ''
    }
  ];

  onFileAttached(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];
    const valid = Array.from(input.files).filter(f => allowed.includes(f.type));
    if (valid.length > 0) {
      this.attachedFiles.update(prev => {
        const existing = new Set(prev.map(f => f.name + f.size));
        return [...prev, ...valid.filter(f => !existing.has(f.name + f.size))];
      });
    }
    input.value = '';
  }

  removeAttachment(index: number) {
    this.attachedFiles.update(prev => prev.filter((_, i) => i !== index));
    if (this.attachedFiles().length === 0) this.ocrExtracted.set(null);
  }

  removeAllAttachments() {
    this.attachedFiles.set([]);
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
    const files = this.attachedFiles();

    const request$ = files.length > 0
      ? this.emailService.ingestEmailWithAttachment(email, files[0])
      : this.emailService.ingestEmail(email);

    request$.subscribe({
      next: (response) => {
        const ocrFields = (response as any)?.ocrExtractedFields || null;
        if (ocrFields) {
          this.ocrExtracted.set(ocrFields);
        }

        const hasOcr = response?.ocrProcessed;
        const attachmentInfo = files.length > 0
          ? ` [📎 ${files.length} file${files.length > 1 ? 's' : ''}${hasOcr ? ' → OCR extracted' : ''}]`
          : '';

        const langInfo = (response as any)?.isVernacular ? {
          detectedLanguage: (response as any).detectedLanguage,
          languageName: (response as any).languageName,
          isVernacular: (response as any).isVernacular,
          translationConfidence: (response as any).translationConfidence
        } : undefined;

        const langLabel = langInfo ? ` [🌐 ${langInfo.languageName} → English]` : '';

        this.results.update(prev => [{
          email,
          response,
          message: response
            ? `Draft created: ${(response as any).displayId || response.draftId} → Assigned to ${response.assignedTo}${attachmentInfo}${langLabel}`
            : 'Email ignored (on ignore list)',
          status: response ? 'success' : 'ignored',
          timestamp: new Date(),
          languageInfo: langInfo
        }, ...prev]);
        this.sending.set(false);
        this.manualEmail.set({ senderEmail: '', subject: '', body: '', messageId: '' });
        this.attachedFiles.set([]);
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
