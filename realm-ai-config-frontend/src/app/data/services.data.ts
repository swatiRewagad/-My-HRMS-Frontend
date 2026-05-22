import { PlatformService, ServiceApiInfo, ModeOption, WizardStep } from '../models/realm.model';

export const ALL_SERVICES: PlatformService[] = [
  {
    id: 'ecm',
    label: 'Enterprise Content Management',
    description: 'Enterprise Content Management — document lifecycle, archival and retrieval',
    group: 'basic',
    dependent: false,
    subServices: [
      {
        id: 'ecm-file-upload', label: 'File Upload', description: 'Secure multi-format file ingestion with virus scanning',
        configFields: [
          { key: 'mountPoint', label: 'Mount Point', type: 'text', placeholder: '/mnt/kavach/realms/ecm/uploads', hint: 'Filesystem path where uploaded files are stored.', required: true },
          { key: 'supportedFileTypes', label: 'Supported File Types', type: 'select', placeholder: 'Select file types...', hint: 'File type preset allowed for upload in this service.', required: true, options: ['PDF', 'PDF, DOCX', 'PDF, DOCX, XLSX', 'PDF, DOCX, XLSX, Images', 'All file types'] },
          { key: 'volumeSize', label: 'Volume Size (GB)', type: 'number', placeholder: 'e.g. 500', hint: 'Total volume capacity allocated for file uploads.', required: true, halfWidth: true },
          { key: 'maxFileSize', label: 'Max File Size (MB)', type: 'number', placeholder: 'e.g. 50', hint: 'Maximum size allowed per individual file upload.', required: true, halfWidth: true },
        ],
      },
      {
        id: 'ecm-archival', label: 'Archival', description: 'Long-term document retention with configurable retention policies',
        configFields: [
          { key: 'archivePeriod', label: 'Archive Period (days)', type: 'number', placeholder: 'e.g. 365', hint: 'Days before documents are moved to archival storage.', required: true, halfWidth: true },
          { key: 'frequency', label: 'Frequency', type: 'select', placeholder: 'Select frequency...', required: true, halfWidth: true, options: ['Daily', 'Weekly', 'Monthly', 'Quarterly'] },
          { key: 'archiveLocation', label: 'Archive Location', type: 'text', placeholder: 's3://kavach-archive/realms/ecm/ or nfs://archive.internal/ecm', hint: 'Destination path or bucket URL for archived documents.', required: true },
          { key: 'compression', label: 'Compression', type: 'select', placeholder: 'Select compression...', hint: 'Compression algorithm applied during archival. None = store as-is.', required: false, options: ['None', 'GZIP', 'ZSTD', 'LZ4', 'Snappy'] },
        ],
      },
      {
        id: 'ecm-ocr', label: 'OCR', description: 'Optical character recognition embedded in the ECM pipeline',
        configFields: [
          { key: 'ocrEngine', label: 'OCR Engine', type: 'select', placeholder: 'Select engine...', required: true, halfWidth: true, options: ['Tesseract', 'Google Vision', 'Azure Computer Vision', 'AWS Textract'] },
          { key: 'ocrLanguage', label: 'Language', type: 'select', placeholder: 'Select language...', required: true, halfWidth: true, options: ['English', 'Hindi', 'English + Hindi', 'Auto-detect'] },
          { key: 'ocrDpi', label: 'Render DPI', type: 'number', placeholder: 'e.g. 300', hint: 'DPI used to render PDF pages before OCR. Higher = better accuracy.', required: false, halfWidth: true },
          { key: 'ocrConfidence', label: 'Min Confidence (%)', type: 'number', placeholder: 'e.g. 80', hint: 'Minimum confidence threshold to accept OCR results.', required: false, halfWidth: true },
        ],
      },
    ],
  },
  {
    id: 'kavach',
    label: 'Kavach',
    description: 'Identity & Access Management — authentication and fine-grained authorisation',
    group: 'basic',
    dependent: true,
    subServices: [
      { id: 'kavach-auth', label: 'Authentication', description: 'SSO, MFA and session management powered by Keycloak' },
      { id: 'kavach-authz', label: 'Authorization', description: 'RBAC / ABAC policy engine with attribute-level control' },
    ],
  },
  { id: 'holiday-calendar', label: 'Holiday Calendar', description: 'Centralised holiday and working-day calendar for all realm entities', group: 'basic', dependent: false },
  { id: 'dashboard-widgets', label: 'Dashboard Widgets', description: 'Configurable widget library for role-based portal dashboards', group: 'basic', dependent: false },
  { id: 'notifications', label: 'Notifications', description: 'Multi-channel notification service — email, SMS, in-app and push', group: 'basic', dependent: false },
  { id: 'audit-trail', label: 'Audit Trail', description: 'Immutable event log capturing every user and system action', group: 'basic', dependent: false },
  { id: 'mdms', label: 'MDMS', description: 'Master Data Management System for reference data governance', group: 'basic', dependent: false },
  { id: 'job-scheduler', label: 'Job Scheduler', description: 'Cron-based and event-driven background job orchestration', group: 'basic', dependent: false },
  {
    id: 'search',
    label: 'Search',
    description: 'AI-powered full-text and semantic search across realm content',
    group: 'ai',
    dependent: false,
    subServices: [
      { id: 'search-kb', label: 'KB Creation - Knowledge based creation', description: 'Ingest and index documents into a searchable knowledge base' },
      { id: 'search-engine', label: 'Search Engine', description: 'Query engine with faceting, ranking and NLP query understanding' },
    ],
  },
  {
    id: 'document-ai',
    label: 'Document AI',
    description: 'Intelligent document processing — extract, classify and enrich content',
    group: 'ai',
    dependent: true,
    subServices: [
      { id: 'dai-ocr', label: 'OCR', description: 'High-accuracy optical character recognition for scanned content' },
      { id: 'dai-ner', label: 'NER — Named Entity Recognition', description: 'Identify persons, organisations, locations and custom entities in text' },
      { id: 'dai-feature', label: 'Feature Extraction', description: 'Derive structured attributes and metadata from unstructured documents' },
    ],
  },
  { id: 'workflow-engine', label: 'Workflow Engine', description: 'BPMN-based process automation with human-task management', group: 'basic', dependent: false },
  {
    id: 'reporting',
    label: 'Reporting & Dashboards',
    description: 'Self-service reports and scheduled data exports for realm stakeholders',
    group: 'basic',
    dependent: false,
    subServices: [
      { id: 'reporting-dashboards', label: 'Dashboards', description: 'Interactive visual dashboards with drill-down capabilities' },
      { id: 'reporting-exports', label: 'Data Exports', description: 'Scheduled and on-demand data exports in CSV, XLSX and PDF formats' },
    ],
  },
  { id: 'payment-gateway', label: 'Payment Gateway', description: 'Secure payment processing integration with government treasury systems', group: 'basic', dependent: true },
  { id: 'form-builder', label: 'Form Builder', description: 'Drag-and-drop dynamic form designer with validation and e-signature', group: 'basic', dependent: false },
  {
    id: 'user-directory',
    label: 'User Directory',
    description: 'Centralised directory sync and group management across realm users',
    group: 'basic',
    dependent: true,
    subServices: [
      { id: 'ud-sync', label: 'User Sync', description: 'Bi-directional sync with LDAP / AD and Kavach user store' },
      { id: 'ud-groups', label: 'Group Management', description: 'Hierarchical group and role assignment with bulk operations' },
    ],
  },
  {
    id: 'anomaly-detection',
    label: 'Anomaly Detection',
    description: 'Statistical and ML-based anomaly flagging in data streams',
    group: 'ai',
    dependent: true,
    subServices: [
      { id: 'anomaly-timeseries', label: 'Time Series', description: 'Detect statistical outliers in time-series data streams' },
      { id: 'anomaly-user-behavior', label: 'User Behavior Analysis', description: 'Identify unusual patterns in user logs' },
    ],
  },
  {
    id: 'face-detection',
    label: 'Face / Object Detection',
    description: 'AI-powered face and object detection with bounding boxes',
    group: 'ai',
    dependent: true,
    subServices: [
      { id: 'face-recognition', label: 'Face Recognition', description: 'Identify and verify individuals from images or video frames' },
      { id: 'object-detection', label: 'Object Detection', description: 'Detect and classify objects with bounding box coordinates' },
      { id: 'pose-estimation', label: 'Pose Estimation', description: 'Analyse human body posture and joint positions in real-time' },
    ],
  },
  { id: 'recommendations', label: 'Recommendations Engine', description: 'Collaborative filtering & content-based personalised recommendations', group: 'ai', dependent: true },
  {
    id: 'chatbot-nlp',
    label: 'Chatbot / NLP',
    description: 'Conversational AI with intent detection, entity extraction and dialogue management',
    group: 'ai',
    dependent: true,
    subServices: [
      { id: 'nlp-intent', label: 'Intent Detection', description: 'Classify user utterances into predefined intent categories' },
      { id: 'nlp-entity', label: 'Entity Extraction', description: 'Extract slot values and named entities from conversational input' },
      { id: 'nlp-response', label: 'Response Generation', description: 'Generate contextual replies using retrieval and generative models' },
    ],
  },
];

export const V1_SERVICE_IDS: string[] = [
  'ecm', 'kavach', 'holiday-calendar', 'dashboard-widgets',
  'notifications', 'audit-trail', 'mdms', 'job-scheduler',
  'search', 'document-ai',
];

export const V2_SERVICE_IDS: string[] = [
  ...V1_SERVICE_IDS,
  'workflow-engine', 'reporting', 'payment-gateway', 'form-builder',
  'user-directory', 'anomaly-detection', 'face-detection',
  'recommendations', 'chatbot-nlp',
];

export const PLATFORM_VERSIONS = ['v1.0 (Stable)', 'v2.0 (Latest)'];

export const VERSION_SERVICE_MAP: Record<string, string[]> = {
  'v1.0 (Stable)': V1_SERVICE_IDS,
  'v2.0 (Latest)': V2_SERVICE_IDS,
};

export const DB_TYPES = ['ShaktiDB', 'Oracle'];

export const MOST_USED_SERVICE_IDS = [
  'kavach', 'notifications', 'audit-trail', 'workflow-engine', 'reporting', 'mdms',
];

export const MODE_OPTIONS: ModeOption[] = [
  {
    value: 'app-designer',
    label: 'App Designer',
    description: 'Design and deploy custom application workflows with drag-and-drop visual tooling and full service orchestration.',
    features: ['Visual workflow builder', 'Custom app templates', 'Low-code form designer', 'App-level service bindings'],
    color: '#6941c6',
    bg: '#f4f0ff',
    border: '#d3c5f8',
  },
  {
    value: 'non-app-designer',
    label: 'Non-App Designer',
    description: 'Configure platform-level services and AI capabilities directly without a visual app layer. Suited for backend integrations.',
    features: ['Direct service configuration', 'API key management', 'Schema & DB binding', 'Platform-level access only'],
    color: '#1a56db',
    bg: '#eef4ff',
    border: '#bfd7fa',
  },
];

export const WIZARD_STEPS: WizardStep[] = [
  { label: 'Realm Selection', short: 'Realm' },
  { label: 'Mode Selection', short: 'Mode' },
  { label: 'Services Selection', short: 'Services' },
  { label: 'Service Configuration', short: 'Configure' },
];

export const SERVICE_API_INFO: Record<string, ServiceApiInfo> = {
  ecm: { invocation: 'Rest API', endpoint: '/api/ecm/fileupload', method: 'POST', contentType: 'Multipart/Form', requestBody: '{\n  "file": "YOUR_FILE.PDF",\n  "file_type": "application/pdf"\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT"\n}' },
  kavach: { invocation: 'Rest API', endpoint: '/api/kavach/auth/token', method: 'POST', contentType: 'application/json', requestBody: '{\n  "client_id": "YOUR_CLIENT_ID",\n  "client_secret": "YOUR_SECRET",\n  "grant_type": "client_credentials"\n}', requestHeaders: '{\n  "Content-Type": "application/json"\n}' },
  'holiday-calendar': { invocation: 'Rest API', endpoint: '/api/holiday-calendar/list', method: 'GET', contentType: 'application/json', requestBody: '{\n  "year": 2026,\n  "region": "IN"\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT"\n}' },
  notifications: { invocation: 'Rest API', endpoint: '/api/notifications/send', method: 'POST', contentType: 'application/json', requestBody: '{\n  "recipient": "USER_ID",\n  "channel": "email",\n  "template_id": "TEMPLATE_ID",\n  "payload": {}\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  'audit-trail': { invocation: 'Rest API', endpoint: '/api/audit/events', method: 'GET', contentType: 'application/json', requestBody: '{\n  "from": "2026-01-01",\n  "to": "2026-03-31",\n  "user_id": "USER_ID"\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT"\n}' },
  mdms: { invocation: 'Rest API', endpoint: '/api/mdms/data', method: 'POST', contentType: 'application/json', requestBody: '{\n  "moduleName": "MODULE_NAME",\n  "masterName": "MASTER_NAME",\n  "filter": "$.*.active"\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  search: { invocation: 'Rest API', endpoint: '/api/search/query', method: 'POST', contentType: 'application/json', requestBody: '{\n  "query": "YOUR_SEARCH_TERM",\n  "index": "INDEX_NAME",\n  "size": 10,\n  "from": 0\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  'document-ai': { invocation: 'Rest API', endpoint: '/api/document-ai/process', method: 'POST', contentType: 'Multipart/Form', requestBody: '{\n  "file": "YOUR_FILE.PDF",\n  "operations": ["ocr", "ner"]\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT"\n}' },
  'workflow-engine': { invocation: 'Rest API', endpoint: '/api/workflow/process/start', method: 'POST', contentType: 'application/json', requestBody: '{\n  "process_definition_key": "PROCESS_KEY",\n  "business_key": "BUSINESS_KEY",\n  "variables": {}\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  reporting: { invocation: 'Rest API', endpoint: '/api/reporting/generate', method: 'POST', contentType: 'application/json', requestBody: '{\n  "report_id": "REPORT_ID",\n  "format": "pdf",\n  "filters": {}\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  'anomaly-detection': { invocation: 'Rest API', endpoint: '/api/anomaly/detect', method: 'POST', contentType: 'application/json', requestBody: '{\n  "data_stream": "STREAM_ID",\n  "window_size": 100,\n  "threshold": 0.95\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  'face-detection': { invocation: 'Rest API', endpoint: '/api/vision/detect', method: 'POST', contentType: 'Multipart/Form', requestBody: '{\n  "image": "YOUR_IMAGE.JPG",\n  "modes": ["face", "object"]\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT"\n}' },
  recommendations: { invocation: 'Rest API', endpoint: '/api/recommendations/fetch', method: 'POST', contentType: 'application/json', requestBody: '{\n  "user_id": "USER_ID",\n  "context": "CONTEXT_KEY",\n  "limit": 10\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
  'chatbot-nlp': { invocation: 'Rest API', endpoint: '/api/nlp/chat', method: 'POST', contentType: 'application/json', requestBody: '{\n  "session_id": "SESSION_ID",\n  "message": "USER_MESSAGE",\n  "language": "en"\n}', requestHeaders: '{\n  "Authorization": "YOUR_JWT",\n  "Content-Type": "application/json"\n}' },
};
