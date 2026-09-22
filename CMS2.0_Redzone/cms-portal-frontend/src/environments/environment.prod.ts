export const environment = {
  production: true,
  apiBaseUrl: 'https://cms.rbi.org.in',
  // Same host in production; the gateway forwards /cms-workflow/** to the workflow service.
  workflowBaseUrl: 'https://cms.rbi.org.in',
  keycloakUrl: 'https://auth.rbi.org.in',
  realm: 'cms',

  // Dev mode: never auto-populate OTP in production
  devAutoPopulateOtp: false,
  devDefaultOtp: '',

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

  // NFR-007: Concurrency
  maxConcurrentRequests: 6,

  // NFR-015: Integration endpoints
  integrations: {
    ekamev: 'https://ekamev.rbi.org.in/api',
    cdr: 'https://cdr.rbi.org.in/api',
    siem: 'https://siem.rbi.org.in/api',
    smsGateway: 'https://sms-gateway.rbi.org.in/api',
    smtp: 'https://mail.rbi.org.in/api',
  },

  // NFR-008: Retention policy
  retentionPeriodYears: 7,
};
