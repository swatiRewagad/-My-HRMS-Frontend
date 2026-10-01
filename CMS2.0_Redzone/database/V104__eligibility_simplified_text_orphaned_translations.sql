-- V104: attach the four authored "Simplify for me" texts to the master rows that were never given one
-- MySQL version
--
-- eligibility.q_through_advocate_simple, q_pending_ombudsman_simple, q_settled_ombudsman_simple and
-- q_staff_of_re_simple were authored and seeded in en/hi/mr, but the ELIGIBILITY_QUESTION_MASTER rows
-- they belong to carried SIMPLIFIED_TEXT = NULL. The portal gates the affordance on the row, not the
-- translation (file-complaint.component.html:74 renders .simplify-btn only under
-- `currentQuestion?.simplifiedText`), so the icon could never appear and the translated plain-language
-- wording was unreachable in every locale. Only isSubJudice and alreadySettled were wired up.
--
-- The seeder is insert-if-absent on (SCHEME_VERSION, QUESTION_KEY), so correcting it cannot repair a
-- database that already holds these rows. Hence this migration.
--
-- Scoped to rows where SIMPLIFIED_TEXT IS NULL so an operator who has since authored their own
-- plain-language wording is not overwritten.

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET SIMPLIFIED_TEXT = 'Are you filing this complaint with the help of a lawyer or legal representative?',
       SIMPLIFIED_TEXT_KEY = 'eligibility.q_through_advocate_simple'
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND QUESTION_KEY = 'throughAdvocateEligibility'
   AND SIMPLIFIED_TEXT IS NULL;

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET SIMPLIFIED_TEXT = 'Have you already filed a complaint about this same issue with the Ombudsman and it is still under review?',
       SIMPLIFIED_TEXT_KEY = 'eligibility.q_pending_ombudsman_simple'
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND QUESTION_KEY = 'pendingBeforeOmbudsman'
   AND SIMPLIFIED_TEXT IS NULL;

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET SIMPLIFIED_TEXT = 'Has the Ombudsman already reviewed and resolved this same complaint in the past?',
       SIMPLIFIED_TEXT_KEY = 'eligibility.q_settled_ombudsman_simple'
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND QUESTION_KEY = 'settledByOmbudsman'
   AND SIMPLIFIED_TEXT IS NULL;

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET SIMPLIFIED_TEXT = 'Are you an employee of the bank/NBFC you are complaining against, and is your complaint about your job or employment?',
       SIMPLIFIED_TEXT_KEY = 'eligibility.q_staff_of_re_simple'
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND QUESTION_KEY = 'staffOfRE'
   AND SIMPLIFIED_TEXT IS NULL;
