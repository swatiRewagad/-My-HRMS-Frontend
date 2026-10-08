export const environment = {
  production: true,
  apiBaseUrl: 'https://cms.rbi.org.in',
  // Same host in production; the gateway forwards /cms-workflow/** to the workflow service.
  workflowBaseUrl: 'https://cms.rbi.org.in',
  keycloakUrl: 'https://auth.rbi.org.in',
  realm: 'cms',
  // Points at the same host as apiBaseUrl, so storage follows the rest of the API rather than the
  // origin serving the bundle. Absent here entirely until now, which failed the production build.
  storageBaseUrl: 'https://cms.rbi.org.in/api/v1/storage',

  // Dev mode: never auto-populate OTP in production
  devAutoPopulateOtp: false,
  devDefaultOtp: '',

  // NFR-005: Session timeout in minutes
  sessionTimeoutMinutes: 15,

  // NFR-006: File upload constraints (EAAP guidelines)
  // The SIZE and COUNT limits deliberately do not live here. They are configuration, read from
  // GET /api/v1/config/upload-limits by UploadLimitsService, which also holds the only permitted
  // fallback literal. Constants here said 5 while the server enforced 2 and nothing read them.
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
