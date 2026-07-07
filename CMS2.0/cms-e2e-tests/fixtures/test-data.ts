export const VALID_COMPLAINT = {
  complainantName: 'Test User',
  complainantEmail: 'test.user@example.com',
  complainantPhone: '9876543210',
  complainantAddress: '123 Test Street, Mumbai 400001',
  bankId: 1,
  bankBranch: 'Test Branch',
  accountNumber: '12345678901',
  categoryId: 1,
  subject: 'ATM cash not dispensed - test case',
  description: 'Test complaint: ATM debited Rs 5000 but cash not dispensed from SBI ATM.',
  reliefSought: 'Refund of Rs 5000',
  priorReComplaint: true,
  reComplaintDate: '2026-05-01',
  reComplaintReference: 'SBI/TEST/2026/001',
  reRepliedAndDissatisfied: true,
  reLastCommunicationDate: '2026-05-20',
  sameGrievancePending: false,
};

export const COMPLAINT_NO_PRIOR_RE = {
  ...VALID_COMPLAINT,
  complainantName: 'No Prior RE Test',
  subject: 'UPI failed transaction - no prior complaint to bank',
  priorReComplaint: false,
  reComplaintDate: null,
  reComplaintReference: null,
  reRepliedAndDissatisfied: false,
};

export const COMPLAINT_BEFORE_WINDOW = {
  ...VALID_COMPLAINT,
  complainantName: 'Window Not Elapsed Test',
  subject: 'Loan overcharge - filed too early',
  reComplaintDate: new Date().toISOString().split('T')[0],
  reRepliedAndDissatisfied: false,
};

export const COMPLAINT_BEYOND_LIMITATION = {
  ...VALID_COMPLAINT,
  complainantName: 'Limitation Period Test',
  subject: 'FD not renewed - old complaint',
  reComplaintDate: '2022-01-15',
};

export const SAMPLE_PINCODE = {
  pincode: '400001',
  expectedDistrict: 'Mumbai',
  expectedState: 'Maharashtra',
};

export const KEYCLOAK_USERS = {
  rbioOfficer: { username: 'officer.rbio1', password: 'Test@1234' },
  cepcOfficer: { username: 'officer.cepc1', password: 'Test@1234' },
  admin: { username: 'admin', password: 'Admin@1234' },
};
