export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8082',
  // cms-workflow-service runs separately on 8083 with a /cms-workflow context path. Officer-pool
  // calls were previously sent to apiBaseUrl, which never serves /cms-workflow/** and 404'd.
  workflowBaseUrl: 'http://localhost:8083',
  keycloakUrl: 'http://localhost:9090',
  realm: 'cms',

  // Dev mode: auto-populate OTP with default value for testing
  devAutoPopulateOtp: true,
  devDefaultOtp: '123456',

  // NFR-005: Session timeout in minutes
  sessionTimeoutMinutes: 15,

  // NFR-006: File upload constraints (EAAP guidelines)
  // Fallback only. The live limits come from GET /api/v1/config/upload-limits via
  // UploadLimitsService; a compiled constant cannot track a configuration change, which is how
  // this said 2 while the server's own upload hint promised 5 in all ten locales.
  maxFileSizeMB: 5,
  maxTotalUploadSizeMB: 25,
  maxFileCount: 10,
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
