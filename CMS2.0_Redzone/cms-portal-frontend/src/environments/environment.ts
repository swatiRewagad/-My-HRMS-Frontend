export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8082',
  // cms-workflow-service runs separately on 8083 with a /cms-workflow context path. Officer-pool
  // calls were previously sent to apiBaseUrl, which never serves /cms-workflow/** and 404'd.
  workflowBaseUrl: 'http://localhost:8083',
  keycloakUrl: 'http://localhost:8180',
  realm: 'cms',
  storageBaseUrl: '/api/v1/storage',

  // Dev mode: auto-populate OTP with default value for testing
  devAutoPopulateOtp: true,
  devDefaultOtp: '123456',

  // NFR-005: Session timeout in minutes
  sessionTimeoutMinutes: 15,

  // NFR-006: File upload constraints (EAAP guidelines)
  // The SIZE and COUNT limits deliberately do not live here. They are configuration, read from
  // GET /api/v1/config/upload-limits by UploadLimitsService, which also holds the only permitted
  // fallback literal. Constants here said 5 while the server enforced 2 and nothing read them.
  allowedFileExtensions: ['.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png', '.xls', '.xlsx'],

  // NFR-007: Concurrency - connection pool settings
  maxConcurrentRequests: 6,

  // NFR-015: Integration endpoints
  integrations: {
    ekamev: 'http://localhost:9001/ekamev',
    cdr: 'http://localhost:9002/cdr',
    siem: 'http://localhost:9003/siem',
    smsGateway: 'http://localhost:9004/sms',
    smtp: 'http://localhost:9005/mail',
  },

  // NFR-008: Retention policy (display only, enforced by backend)
  retentionPeriodYears: 7,
};
