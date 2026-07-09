export const environment = {
  production: true,
  apiBaseUrl: '',
  keycloakUrl: '/auth',
  realm: 'cms',

  sessionTimeoutMinutes: 15,
  maxFileSizeMB: 5,
  maxTotalUploadSizeMB: 25,
  maxFileCount: 10,
  allowedFileExtensions: ['.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png', '.xls', '.xlsx'],
  maxConcurrentRequests: 6,

  integrations: {
    ekamev: '/api/v1/integrations/ekamev',
    cdr: '/api/v1/integrations/cdr',
    siem: '/api/v1/integrations/siem',
    smsGateway: '/api/v1/integrations/sms',
    smtp: '/api/v1/integrations/smtp',
  },

  retentionPeriodYears: 7,
};
