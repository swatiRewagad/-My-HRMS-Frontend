export interface NonMaintainableClause {
  clauseCode: string;
  description: string;
}

// RBIOS 2026 clause table a Non-Maintainable complaint can close under (FR-G-013). Mirrors the
// clauseCode values assigned to the screening questions in eligibility-questions.config.ts.
export const NON_MAINTAINABLE_CLAUSES: NonMaintainableClause[] = [
  {
    clauseCode: '10(1)(e)',
    description: 'The complainant has not filed a written/electronic complaint with the Regulated Entity before approaching the Ombudsman.',
  },
  {
    clauseCode: '10(1)(h)',
    description: 'The complaint relates to the same grievance already pending before the Ombudsman.',
  },
  {
    clauseCode: '10(1)(i)',
    description: 'The complaint relates to the same grievance already settled or dealt with on merits by the Ombudsman.',
  },
  {
    clauseCode: '10(1)(j)',
    description: 'The complaint relates to the same grievance already pending before a Court, Tribunal, Arbitrator or other judicial/quasi-judicial forum (excluding criminal proceedings or police investigation).',
  },
  {
    clauseCode: '10(1)(k)',
    description: 'The complaint relates to the same grievance already settled or dealt with by a Court, Tribunal, Arbitrator or other judicial/quasi-judicial forum (excluding criminal proceedings or police investigation).',
  },
  {
    clauseCode: '10(2)(g)',
    description: 'The complaint involves an employer-employee relationship between the complainant and the Regulated Entity.',
  },
];
