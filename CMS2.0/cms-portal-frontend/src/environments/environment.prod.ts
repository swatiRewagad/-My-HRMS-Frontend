export const environment = {
  production: true,
  apiBaseUrl: 'https://cms.rbi.org.in',
  keycloakUrl: 'https://auth.rbi.org.in',
  realm: 'cms',

  // NFR-005: Session timeout in minutes
  sessionTimeoutMinutes: 15,

  // NFR-006: File upload constraints (EAAP guidelines)
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
