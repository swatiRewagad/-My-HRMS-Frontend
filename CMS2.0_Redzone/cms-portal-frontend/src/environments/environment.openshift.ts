export const environment = {
  production: true,
  apiBaseUrl: '',
  // Same origin; the ingress routes /cms-workflow/** to the workflow service.
  workflowBaseUrl: '',
  keycloakUrl: '/auth',
  realm: 'cms',

  sessionTimeoutMinutes: 15,
  // The SIZE and COUNT limits deliberately do not live here. They are configuration, read from
  // GET /api/v1/config/upload-limits by UploadLimitsService, which also holds the only permitted
  // fallback literal. Constants here said 5 while the server enforced 2 and nothing read them.
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
