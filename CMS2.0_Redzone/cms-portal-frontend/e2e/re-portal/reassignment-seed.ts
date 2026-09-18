/**
 * Deterministic fixture for the RE reassignment suite (UST838–UST845).
 *
 * WHY THIS EXISTS. The suite used to *discover* its own subject matter: it probed four hardcoded
 * entity names ('AXIS', 'Axis Bank', 'State Bank of India', 'Bank of Maharashtra') for a seeded
 * officer directory and, finding none, skipped 26 of its 30 tests with exit code 0. Both tables the
 * feature is built on — ENTITY_USERS and NODAL_OFFICER_RECORDS — are EMPTY in cms_db, so the whole
 * of UST838–845 reported green while never executing. A suite that cannot fail is worse than no
 * suite, so the data is now seeded rather than hoped for.
 *
 * Direct SQL rather than an API call, because there is no endpoint that writes either table: the
 * officer directory is master data with no admin CRUD surface, and NODAL_OFFICER_RECORDS is written
 * only by the RE-response path, which cannot produce a record with a chosen owner, status and
 * version. Same approach, same mysql CLI, as e2e/aa/aa-shared-fixtures.ts.
 *
 * ISOLATION. Everything is prefixed 'S4-' and every delete is scoped by that prefix. cms_db is
 * shared and holds ~1,600 real complaints — nothing here truncates and nothing here touches a row it
 * did not write.
 */
import { execFileSync } from 'child_process';

const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

/** Runs a statement against cms_db. utf8mb4 is mandatory or native scripts come back as '?'. */
export function sql(statement: string): string {
  return execFileSync(
    MYSQL_CLI,
    ['-u', 'cms_user', '-pcms_pass', 'cms_db', '--default-character-set=utf8mb4', '-N', '-B', '-e', statement],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  ).trim();
}

/** The seeded entity. A '-' separator, not '_', because '_' is a LIKE wildcard in the purge. */
export const ENTITY = 'S4-BANK';
/** A second entity, so cross-entity leakage has something real to leak. */
export const OTHER_ENTITY = 'S4-OTHERBANK';

/**
 * Officers in the seeded directory.
 *
 * `left` is deactivated and displays as 'Former Officer' precisely so the candidate filter has
 * something to wrongly include; `idle` holds nothing so the dashboard has an officer a GROUP BY
 * alone could not produce a row for.
 */
export const OFFICER = {
  no1: 'S4-re.no1',
  no2: 'S4-re.no2',
  cp1: 'S4-re.cp1',
  pno: 'S4-re.pno',
  idle: 'S4-re.idle',
  left: 'S4-re.left',
  otherEntity: 'S4-re.other',
} as const;

/** Complaint numbers of the seeded records, in the order they are inserted. */
export const RECORD = {
  /** Open, owned by no1. The default subject of a move. */
  open1: 'S4-CMP-0001',
  /** Open, owned by no1. */
  open2: 'S4-CMP-0002',
  /** Open, owned by no2. */
  open3: 'S4-CMP-0003',
  /** CLOSED — must be excluded from every workload figure. */
  closed1: 'S4-CMP-0004',
  /** RESOLVED — likewise excluded. */
  closed2: 'S4-CMP-0005',
  /** Belongs to OTHER_ENTITY, so cross-entity reads have something to wrongly return. */
  otherEntity: 'S4-CMP-0006',
} as const;

/**
 * Expected workload per officer, given the seed above.
 *
 * Written down as data so the assertions can be exact. The previous
 * `expect(total).toBeGreaterThanOrEqual(0)` was true of every possible count, including a broken
 * one, and `officers.some(w => w === 0)` passed on any pool containing one idle officer.
 */
export const EXPECTED_WORKLOAD: Record<string, number> = {
  [OFFICER.no1]: 2,
  [OFFICER.no2]: 1,
  [OFFICER.cp1]: 0,
  [OFFICER.pno]: 0,
  [OFFICER.idle]: 0,
};

/** Sum of EXPECTED_WORKLOAD — what /workload must report as totalActiveRecords. */
export const EXPECTED_TOTAL_ACTIVE = 3;
/** Records seeded into ENTITY in total, open and closed. Must exceed EXPECTED_TOTAL_ACTIVE. */
export const SEEDED_RECORDS_IN_ENTITY = 5;

export interface SeededRecord {
  id: number;
  complaintNumber: string;
  assignedTo: string;
  status: string;
}

/** Deletes every 'S4-' row, in FK-safe order. Safe to call when nothing was seeded. */
export function purge(): void {
  sql(`DELETE FROM reassignment_clarifications WHERE reassignment_request_id IN
         (SELECT id FROM reassignment_requests WHERE complaint_number LIKE 'S4-%')`);
  sql(`DELETE FROM reassignment_requests WHERE complaint_number LIKE 'S4-%'`);
  sql(`DELETE FROM reassignment_history WHERE complaint_number LIKE 'S4-%'`);
  sql(`DELETE FROM notification_delivery_log WHERE related_complaint_number LIKE 'S4-%'`);
  sql(`DELETE FROM in_app_notifications WHERE related_entity_id LIKE 'S4-%'`);
  sql(`DELETE FROM nodal_officer_records WHERE complaint_number LIKE 'S4-%'`);
  sql(`DELETE FROM entity_users WHERE entity_code LIKE 'S4-%'`);
}

/**
 * Seeds the directory and the records, and returns the record rows with their generated ids.
 *
 * `version` is seeded to 0 rather than left NULL on purpose. ReassignmentExecutor.apply skips the
 * optimistic-lock comparison entirely when the stored version is NULL, so a NULL-versioned fixture
 * would make the stale-version conflict test pass a move it should have refused — the exact class of
 * false green this file exists to remove.
 */
export function seed(): SeededRecord[] {
  purge();

  const officers: Array<[string, string, string, string, number, string]> = [
    // userId, displayName, reRole, designation, active, entityCode
    [OFFICER.no1, 'S4 Nodal Officer One', 'NODAL_OFFICER', 'Manager', 1, ENTITY],
    [OFFICER.no2, 'S4 Nodal Officer Two', 'NODAL_OFFICER', 'Manager', 1, ENTITY],
    [OFFICER.cp1, 'S4 Contact Person One', 'CONTACT_PERSON', 'Officer', 1, ENTITY],
    [OFFICER.pno, 'S4 Principal Nodal Officer', 'PNO', 'General Manager', 1, ENTITY],
    [OFFICER.idle, 'S4 Nodal Officer Idle', 'NODAL_OFFICER', 'Manager', 1, ENTITY],
    // Deactivated. Named 'Former Officer' so the active filter has something to fail on.
    [OFFICER.left, 'Former Officer', 'NODAL_OFFICER', 'Manager', 0, ENTITY],
    // A different entity entirely, for the cross-entity assertions.
    [OFFICER.otherEntity, 'S4 Other Entity Nodal', 'NODAL_OFFICER', 'Manager', 1, OTHER_ENTITY],
  ];

  const officerValues = officers
    .map(
      ([userId, name, role, designation, active, entityCode]) =>
        `('${userId}', '${entityCode}', '${name}', '${userId}@s4.example.com', ` +
        `'${designation}', '${role}', ${active}, 'Mumbai', NOW(), NOW())`
    )
    .join(',');

  sql(
    `INSERT INTO entity_users
       (user_id, entity_code, display_name, email, designation, re_role, active, territory,
        created_at, last_modified_at)
     VALUES ${officerValues}`
  );

  const records: Array<[string, string, string, string]> = [
    // complaintNumber, assignedTo, status, entityCode
    [RECORD.open1, OFFICER.no1, 'INFORMATION_REQUIRED', ENTITY],
    [RECORD.open2, OFFICER.no1, 'PENDING', ENTITY],
    [RECORD.open3, OFFICER.no2, 'INFORMATION_REQUIRED', ENTITY],
    [RECORD.closed1, OFFICER.no1, 'CLOSED', ENTITY],
    [RECORD.closed2, OFFICER.no2, 'RESOLVED', ENTITY],
    [RECORD.otherEntity, OFFICER.otherEntity, 'INFORMATION_REQUIRED', OTHER_ENTITY],
  ];

  const recordValues = records
    .map(
      ([complaintNumber, assignedTo, status, entityCode]) =>
        `('${complaintNumber}', '${entityCode}', 'S4 Fixture Entity', '${assignedTo}', ` +
        `'${status}', 'S4 Seeded Officer', 'S4 Seeded PNO', 'Manager', ` +
        `'${assignedTo}@s4.example.com', '9876543210', 0, NOW(), NOW())`
    )
    .join(',');

  sql(
    `INSERT INTO nodal_officer_records
       (complaint_number, entity_code, entity_name, assigned_to, status, nodal_officer_name,
        pno_name, designation, email, phone, version, created_at, last_modified_at)
     VALUES ${recordValues}`
  );

  return readSeededRecords();
}

/** Re-reads the seeded records, so a test that needs a fresh owner/version sees the current row. */
export function readSeededRecords(): SeededRecord[] {
  const rows = sql(
    `SELECT id, complaint_number, assigned_to, status FROM nodal_officer_records
      WHERE complaint_number LIKE 'S4-%' ORDER BY complaint_number`
  );
  if (!rows) return [];
  return rows.split('\n').map((line) => {
    const [id, complaintNumber, assignedTo, status] = line.split('\t');
    return { id: Number(id), complaintNumber, assignedTo, status };
  });
}

/** The seeded record for a complaint number, or throws — an absent fixture is a test bug, not a skip. */
export function recordFor(records: SeededRecord[], complaintNumber: string): SeededRecord {
  const found = records.find((r) => r.complaintNumber === complaintNumber);
  if (!found) {
    throw new Error(
      `Fixture record ${complaintNumber} was not seeded — check reassignment-seed.ts and cms_db`
    );
  }
  return found;
}

/** The stored @Version of a record, read fresh. Needed for the optimistic-locking assertions. */
export function versionOf(recordId: number): number | null {
  const value = sql(`SELECT version FROM nodal_officer_records WHERE id = ${recordId}`);
  if (!value || value === 'NULL') return null;
  return Number(value);
}

/** The current owner of a record, read fresh — used to prove a move actually landed. */
export function ownerOf(recordId: number): string | null {
  const value = sql(`SELECT assigned_to FROM nodal_officer_records WHERE id = ${recordId}`);
  return !value || value === 'NULL' ? null : value;
}
