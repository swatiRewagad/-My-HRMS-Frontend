// Used only by the cms-infra/docker-compose.extras.yml local Docker Desktop stack — see
// cms-infra/README or the setup guide for how this fits together. NOT for OpenShift or any
// other deployment target; environment.openshift.ts is the relative-path-proxied one for that.
//
// apiBaseUrl and keycloakUrl point at ports cms-infra publishes to your host machine directly
// (this file's calls run in the browser, not inside a container, so they need to be
// host-visible absolute URLs, not Docker service hostnames).
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8082',

  // ↓↓↓ Set this to your real external OCR service's address before building the frontend
  // image — same value as OCR_EXTERNAL_URL in cms-infra/.env, since this is the browser talking
  // to it directly (the "AI Extract" button on the physical-letter screen), separate from
  // cms-backend's own server-side call to the same service via cms.ocr.external-url.
  ocrServiceUrl: 'http://REPLACE-WITH-YOUR-OCR-HOST:8000',

  keycloakUrl: 'http://localhost:8180',
  realm: 'cms',

  devAutoPopulateOtp: true,
  devDefaultOtp: '123456',

  sessionTimeoutMinutes: 15,
  maxFileSizeMB: 2,
  maxTotalUploadSizeMB: 25,
  maxFileCount: 10,
  allowedFileExtensions: ['.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png', '.xls', '.xlsx'],
  maxConcurrentRequests: 6,

  integrations: {
    ekamev: 'http://localhost:9001/ekamev',
    cdr: 'http://localhost:9002/cdr',
    siem: 'http://localhost:9003/siem',
    smsGateway: 'http://localhost:9004/sms',
    smtp: 'http://localhost:9005/mail',
  },

  retentionPeriodYears: 7,
};
