const express = require('express');
const multer = require('multer');
const cors = require('cors');
const crypto = require('crypto');

const app = express();
const PORT = 8000;

app.use(cors());
app.use(express.json());

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 64 * 1024 * 1024 } });

const jobs = new Map();
let requestCount = 0;
let clientErrors = 0;
let documentsAccepted = 0;
let documentsRejected = 0;
const bounceStats = {};
const startTime = Date.now();

// --- GET /healthz ---
app.get('/healthz', (req, res) => {
  res.json({
    status: 'ok',
    profile: 'laptop',
    synthetic_only: true,
    banner: 'profile=laptop synthetic_only=true slm=mock threads=2x2',
    provider_profile_gauge: { laptop: 1.0, cpu_node: 0.0, gpu_node: 0.0 },
    models: [
      { name: 'ocr_detection', path: 'models/ocr/det.onnx', present: true },
      { name: 'ocr_rec_latin', path: 'models/ocr/rec_latn.onnx', present: true },
      { name: 'ocr_rec_devanagari', path: 'models/ocr/rec_deva.onnx', present: true },
      { name: 'language_id', path: 'models/lid/lid.176.ftz', present: true },
      { name: 'translation', path: 'models/mt/indic-en/model.bin', present: true },
      { name: 'page_classifier_fusion', path: 'models/classifier/fusion.json', present: true }
    ],
    database: 'ok',
    webhook_enabled: false,
    schema_version: '1.0.0'
  });
});

// --- GET /v1/stats ---
app.get('/v1/stats', (req, res) => {
  const uptimeSeconds = (Date.now() - startTime) / 1000;
  res.json({
    uptime_seconds: uptimeSeconds,
    requests: requestCount,
    client_errors: clientErrors,
    server_errors: 0,
    unhandled_exceptions: 0,
    error_rate: requestCount > 0 ? clientErrors / requestCount : 0,
    routes: [
      { route: '/v1/documents', requests: documentsAccepted + documentsRejected, client_errors: 0, server_errors: 0, error_rate: 0 },
      { route: '/v1/jobs/{job_id}', requests: 0, client_errors: 0, server_errors: 0, error_rate: 0 }
    ],
    documents: { accepted: documentsAccepted, rejected: documentsRejected },
    bounces: bounceStats,
    provider_calls: { ok: documentsAccepted, rate_limited: 0 }
  });
});

// --- GET /metrics ---
app.get('/metrics', (req, res) => {
  const uptimeSeconds = (Date.now() - startTime) / 1000;
  res.set('Content-Type', 'text/plain; charset=utf-8');
  res.send(`# HELP provider_profile Active provider profile.
# TYPE provider_profile gauge
provider_profile{profile="laptop"} 1.0
provider_profile{profile="cpu_node"} 0.0
provider_profile{profile="gpu_node"} 0.0
# HELP http_requests_total Total HTTP requests.
# TYPE http_requests_total counter
http_requests_total{method="POST",route="/v1/documents",status="200"} ${documentsAccepted + documentsRejected}
http_requests_total{method="GET",route="/v1/jobs/{job_id}",status="200"} ${requestCount}
# HELP process_uptime_seconds Process uptime.
# TYPE process_uptime_seconds gauge
process_uptime_seconds ${uptimeSeconds}
`);
});

// --- POST /v1/documents ---
app.post('/v1/documents', upload.single('file'), (req, res) => {
  requestCount++;

  if (!req.file) {
    clientErrors++;
    return res.status(422).json({ code: 'FILE_REQUIRED', detail: 'The "file" field is required.' });
  }

  const file = req.file;
  const sender = req.body.sender || null;
  const subject = req.body.subject || null;

  const fileHash = crypto.createHash('sha256').update(file.buffer).digest('hex');
  const documentId = `sha256:${fileHash}`;
  const jobId = crypto.randomBytes(16).toString('hex');
  const now = new Date().toISOString();

  // Bounce logic
  if (file.size === 0) {
    return respondBounce(res, jobId, documentId, now, 'EMPTY_FILE', 'attachment is zero bytes');
  }

  const mimeType = file.mimetype;
  const validMimes = ['application/pdf', 'image/jpeg', 'image/png'];
  if (!validMimes.includes(mimeType)) {
    return respondBounce(res, jobId, documentId, now, 'MIME_MISMATCH', 'content does not match any supported type (PDF, JPEG, PNG)');
  }

  // Check if image is too small (simulate resolution check)
  if (file.size < 500 && mimeType.startsWith('image/')) {
    return respondBounce(res, jobId, documentId, now, 'RESOLUTION_BELOW_FLOOR', 'page is below minimum resolution; the floor is 200 px on the short edge');
  }

  // Simulate successful extraction
  const processingStart = new Date();
  const durationMs = 800 + Math.random() * 2000;
  const processingEnd = new Date(processingStart.getTime() + durationMs);

  const envelope = buildSuccessEnvelope(jobId, documentId, processingStart, processingEnd, durationMs, file.originalname);

  const jobRecord = {
    job_id: jobId,
    document_id: documentId,
    status: 'completed',
    result_version: 1,
    is_final: true,
    created_at: now,
    updated_at: processingEnd.toISOString(),
    soft_duplicate_of: null,
    soft_duplicate_reason: null,
    error_code: null,
    versions: [1],
    result: envelope
  };

  jobs.set(jobId, jobRecord);
  documentsAccepted++;

  res.json({
    job_id: jobId,
    document_id: documentId,
    is_new: true,
    status: 'completed',
    soft_duplicate_of: null,
    soft_duplicate_reason: null,
    result: envelope
  });
});

// --- GET /v1/jobs/:job_id ---
app.get('/v1/jobs/:job_id', (req, res) => {
  requestCount++;
  const jobId = req.params.job_id;
  const job = jobs.get(jobId);

  if (!job) {
    clientErrors++;
    return res.status(404).json({ code: 'JOB_NOT_FOUND', detail: `Job ${jobId} not found.` });
  }

  res.json(job);
});

function respondBounce(res, jobId, documentId, createdAt, reason, detail) {
  documentsRejected++;
  bounceStats[reason] = (bounceStats[reason] || 0) + 1;

  const envelope = {
    schema_version: '1.0.0',
    result_version: 1,
    is_final: true,
    document_id: documentId,
    job_id: jobId,
    created_at: createdAt,
    provider_profile: 'laptop',
    bounce_decision: { decision: 'bounce', reason, detail },
    escalation_flag: false,
    pages: [{
      page_no: 1,
      quality_score: 0.15 + Math.random() * 0.15,
      quality_flags: [reason],
      quality_verdict: 'bounce',
      ocr_tier: 'none',
      line_count: 0,
      primary_script: null,
      primary_language: null,
      language_distribution: {},
      is_code_mixed: false,
      escalated: false,
      shadow_sampled: false,
      repairs_applied: []
    }],
    document: null,
    processing: {
      started_at: createdAt,
      finished_at: createdAt,
      duration_ms: 50 + Math.random() * 100,
      pages_processed: 1,
      pages_bounced: 1,
      pages_escalated: 0,
      tokens_used: 0,
      stage_timings_ms: { s0_read: 0.01, s1_ingest: 10, s15_quality: 40 }
    },
    warnings: []
  };

  const jobRecord = {
    job_id: jobId,
    document_id: documentId,
    status: 'bounced',
    result_version: 1,
    is_final: true,
    created_at: createdAt,
    updated_at: createdAt,
    soft_duplicate_of: null,
    soft_duplicate_reason: null,
    error_code: null,
    versions: [1],
    result: envelope
  };
  jobs.set(jobId, jobRecord);

  res.json({
    job_id: jobId,
    document_id: documentId,
    is_new: true,
    status: 'bounced',
    soft_duplicate_of: null,
    soft_duplicate_reason: null,
    result: envelope
  });
}

function buildSuccessEnvelope(jobId, documentId, startedAt, finishedAt, durationMs, filename) {
  const isHindi = filename && (filename.includes('hindi') || filename.includes('deva'));

  const fields = {
    bank_name: buildField('State Bank of India', null, 'State Bank of India', 0.97, 'lookup_table', true, 1, [100, 200, 400, 200, 400, 240, 100, 240], 3, 0, 20),
    ifsc_code: buildField('SBIN0001234', null, 'SBIN0001234', 0.95, 'regex_validator', true, 1, [100, 260, 350, 260, 350, 300, 100, 300], 5, 0, 11),
    account_number: buildField('1234567890123456', null, '1234567890123456', 0.92, 'regex_validator', true, 1, [100, 320, 450, 320, 450, 360, 100, 360], 7, 0, 16),
    amount: buildField('Rs. 45,000/-', null, '45000.00', 0.88, 'regex_validator', true, 1, [100, 380, 320, 380, 320, 420, 100, 420], 9, 0, 12),
    transaction_date: buildField('15/07/2026', null, '2026-07-15', 0.91, 'regex_validator', true, 1, [100, 440, 300, 440, 300, 480, 100, 480], 11, 0, 10),
    complainant_name: buildField(
      isHindi ? '\u0930\u093E\u091C\u0947\u0936 \u0915\u0941\u092E\u093E\u0930' : 'Rajesh Kumar Sharma',
      isHindi ? 'Rajesh Kumar' : null,
      null,
      0.85,
      'slm',
      null,
      1, [100, 500, 400, 500, 400, 540, 100, 540], 13, 0, isHindi ? 10 : 19
    ),
    prior_complaint_reference: buildField('CMS/2026/401687', null, null, 0.99, 'slm', null, 1, [100, 560, 380, 560, 380, 600, 100, 600], 15, 0, 15)
  };

  if (isHindi) {
    fields.complainant_name.mt_engine = 'indictrans2-indic-en-dist-200M-ct2-int8';
  }

  return {
    schema_version: '1.0.0',
    result_version: 1,
    is_final: true,
    document_id: documentId,
    job_id: jobId,
    created_at: finishedAt.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    provider_profile: 'laptop',
    bounce_decision: { decision: 'accept', reason: null, detail: null },
    escalation_flag: false,
    pages: [{
      page_no: 1,
      quality_score: 0.92 + Math.random() * 0.08,
      quality_flags: [],
      quality_verdict: 'accept',
      ocr_tier: 'tier1',
      line_count: 25,
      primary_script: isHindi ? 'Deva' : 'Latn',
      primary_language: isHindi ? 'hin' : 'eng',
      language_distribution: isHindi ? { hin: 0.7, eng: 0.3 } : { eng: 1.0 },
      is_code_mixed: isHindi,
      escalated: false,
      shadow_sampled: false,
      repairs_applied: []
    }],
    document: {
      document_class: 'complaint_form',
      document_class_confidence: 0.95 + Math.random() * 0.05,
      fields,
      complaint_summary: {
        text: 'I am writing to report an unauthorized debit of Rs. 45,000 from my savings account on 15/07/2026. I did not authorize this transaction and request immediate reversal and investigation.',
        spans: [
          { evidence: { page_no: 1, bbox: [80, 700, 900, 700, 900, 800, 80, 800], span: { line_index: 17, start: 0, end: 180 } }, text: 'I am writing to report an unauthorized debit of Rs. 45,000 from my savings account on 15/07/2026. I did not authorize this transaction and request immediate reversal and investigation.' }
        ],
        separator: '\n'
      },
      supporting_doc_tags: [],
      page_count: 1,
      primary_script: isHindi ? 'Deva' : 'Latn',
      primary_language: isHindi ? 'hin' : 'eng',
      language_distribution: isHindi ? { hin: 0.7, eng: 0.3 } : { eng: 1.0 },
      is_code_mixed: isHindi
    },
    processing: {
      started_at: startedAt.toISOString(),
      finished_at: finishedAt.toISOString(),
      duration_ms: durationMs,
      pages_processed: 1,
      pages_bounced: 0,
      pages_escalated: 0,
      tokens_used: 0,
      stage_timings_ms: {
        s0_read: 0.01,
        s1_ingest: 50 + Math.random() * 20,
        s15_quality: 200 + Math.random() * 50,
        s4_ocr: 400 + Math.random() * 300,
        s2b_lid: 50 + Math.random() * 20,
        s3_classify: 250 + Math.random() * 50,
        s5_validators: 5 + Math.random() * 5,
        s5_slm: durationMs * 0.4
      }
    },
    warnings: []
  };
}

function buildField(value, valueEn, normalized, confidence, source, validatorPassed, pageNo, bbox, lineIndex, start, end) {
  return {
    value,
    value_en: valueEn,
    normalized,
    confidence,
    source,
    validator_passed: validatorPassed,
    evidence: { page_no: pageNo, bbox, span: { line_index: lineIndex, start, end } },
    mt_engine: null,
    flags: []
  };
}

app.listen(PORT, () => {
  console.log(`Mock OCR API running at http://localhost:${PORT}`);
  console.log(`Endpoints:`);
  console.log(`  POST /v1/documents       - Submit a document`);
  console.log(`  GET  /v1/jobs/:job_id    - Re-read a job`);
  console.log(`  GET  /healthz            - Health check`);
  console.log(`  GET  /metrics            - Prometheus metrics`);
  console.log(`  GET  /v1/stats           - JSON stats`);
});
