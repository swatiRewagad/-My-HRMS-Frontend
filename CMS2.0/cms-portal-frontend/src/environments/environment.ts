export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8082',
  keycloakUrl: 'http://localhost:8180',
  realm: 'cms',

  // Dev mode: auto-populate OTP with default value for testing
  devAutoPopulateOtp: true,
  devDefaultOtp: '123456',

  // NFR-005: Session timeout in minutes
  sessionTimeoutMinutes: 15,

  // NFR-006: File upload constraints (EAAP guidelines)
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
