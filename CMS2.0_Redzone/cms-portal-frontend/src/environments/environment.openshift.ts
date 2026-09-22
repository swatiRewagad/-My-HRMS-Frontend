export const environment = {
  production: true,
  apiBaseUrl: '',
  // Same origin; the ingress routes /cms-workflow/** to the workflow service.
  workflowBaseUrl: '',
  keycloakUrl: '/auth',
  realm: 'cms',

  sessionTimeoutMinutes: 15,
  // Fallback only. The live limits come from GET /api/v1/config/upload-limits via
  // UploadLimitsService; a compiled constant cannot track a configuration change, which is how
  // this said 2 while the server's own upload hint promised 5 in all ten locales.
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
