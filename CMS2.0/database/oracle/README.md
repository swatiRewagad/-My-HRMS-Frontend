# Oracle migration scripts

Paired one-for-one with the MySQL scripts in `../`: `oracle/V27__x.sql` and `../V27__x.sql` express the
same change in each dialect, and each carries a comment pointing at its counterpart. Add both halves.

**There is no Flyway or Liquibase in this repo.** The `V<n>__` prefix is a convention, not a tool — no
schema-history table exists and nothing verifies that a script ran. Scripts are applied by hand, in
numeric order, and most are *not* idempotent (re-running an `ALTER TABLE ... ADD` fails with ORA-01430).
The prod profile runs `ddl-auto=validate`, so a script nobody applied surfaces as cms-backend refusing to
start, naming the missing column.

Numbering quirks to be aware of: there are two `V15__*` files and no `V16`.

## SIMULATED_EMAILS is defined three times, inconsistently

Before changing this table, check which script your target schema actually ran — the three definitions
are not compatible:

| Script | Shape |
|---|---|
| `V1__complete_schema.sql:365` | A different 6-column table: `ID`, `FROM_ADDRESS`, `SUBJECT`, `BODY`, `STATUS DEFAULT 'RECEIVED'`, `RECEIVED_AT`. No `DIRECTION`, `THREAD_ID`, `MESSAGE_ID` or `COMPLAINT_NUMBER`. Does **not** match the `SimulatedEmail` entity. |
| `V4__complete_oracle_ddl.sql:530` | 15 columns, matching the entity as it stood then. |
| `V5__complete_ddl_dml.sql:548` | Same 15 columns as V4. |

A schema built from V1 alone cannot support the Email Communication feature at all. V4/V5 are the
definitions the entity was written against.

`CC_EMAIL`, `BCC_EMAIL`, `CREATED_BY` and `UPDATED_AT` were mapped on the entity without a corresponding
Oracle script — `V28__simulated_emails_cc_bcc_audit_columns.sql` repairs that, and
`V29__simulated_emails_delivery_status.sql` adds the outbound delivery lifecycle on top. V29 depends on
V28.

Consolidating V1/V4/V5 into one authoritative definition would be worth doing, but it needs to be done
against knowledge of what each environment has actually applied — hence this note rather than a fix.
