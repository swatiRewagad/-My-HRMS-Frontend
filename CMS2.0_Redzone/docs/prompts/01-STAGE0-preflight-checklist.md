# Stage 0 — Pre-flight (human done now CLaude has to do these)

Do all of this before opening any session. Baseline HEAD: c23b899.
Project is located at: C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone

## 1. Answer to the blockers is presented below

Minimum to unblock Wave 0 — **#1 and #4**. The rest can arrive during Wave 1, but #2 must land
before S6 starts and #5 before S7.

1. **"RBIOS 2026" closure clauses (UST774, UST769).** Code is `RBIOS_2021` everywhere;
   `ClosureClauseMasterSeeder.java:25` and `oracle/V29:18-20` explicitly refuse to invent 2026
   clause numbers pending RBI notification. Yet `WorkflowController.java:487-489` hardcodes two
   invented clauses `16(5)`/`16(6)` flagged `newIn2026` and serves them today with no
   scheme-date gate. **Need real gazette clause codes, or these stories stay blocked.**
   Note S4 already established the precedent for this class of decision: the AA award ceiling
   and sub-judice block were built but left config-OFF pending legal sign-off. Do the same here.
ANSWER: Can you please scan and pick from C:\Users\Admin\Desktop\Prompt\RBI Intergated Ombudsman 2026.pdf now the rule is the complaints logged before 2021 will be closed with the closure clauses of that scheme whereas logged after 2026 with the document I attached so the closure clauses need to be made configurable and to be picked basis the year of the complaint is the story. We will add the clauses to the DB config table later

2. **File size.** UST587 says 5 MB. `FileStorageConfig.java:28` says 2097152 (2 MB) per NFR-006.
   `cms-frontend/.../form-validators.ts:224` says 5 MB. `application.yml:48-49` sets the Spring
   servlet cap to 50 MB. Four numbers. Which wins?
ANSWER: Please make file size configurable for our purpose there will be two configurations, one single file max size will be configured as 5MB total of all files uploaded to the complaint will be configured as 25MB

3. **Compensation caps.** Rs 3L harassment / Rs 30L consequential are `private static final` in
   `RbioCompensationService.java:22-28`. Confirm the values and that they move to config.
   (S4's gate flagged these as needing legal sign-off, asserted only by RBIO code.)
ANSWER: Please make them configurable and move them out of Java to a DB table/ config file

4. **Roles: 4 new, or remap the existing 5?** Recommendation: create
   `RBIO_DEALING_OFFICIAL`, `RBIO_REVIEWER`, `RBIO_DEPUTY_OMBUDSMAN`, `RBIO_OMBUDSMAN`, keeping
   Conciliator/Adjudicator as **stages**. Remapping is semantically wrong — those are stages in
   the code's own escalation ladder, not ranks.
ANSWER YES Create

5. **UST613's "62 columns from the BRD Format and Logic Sheet."** That document is not in the
   repo. Send it, or S7 builds only the column-registry mechanism.
ANSWER: This is a reporting story what first 62 columns may be a mistake, what the story means is that complaint is a main domain entity so a flat view of all complaints with the history of their workflow is what needs to be built as a materialized view in DB and a grid which allows search and filter and extract with server side pagination not all coloumns but few. I suggest you do this AFTER THE RBIO as that may add columns so park this for now.

6. **Digital signature (UST580).** Needs a real DSC/HSM key. None exists;
   `ClosureLetterService.java:91-94` prints "NOT signed".
ANSWER: Implement mock for now
7. **SMS gateway.** `CitizenAuthController.java:124` is a `log.info` TODO; no gateway client
   exists anywhere. ~10 stories mandate SMS.
ANSWER: Please put all sent SMS to a new table with a column as sent, another service which already exists will read and mark sent flag later

8. **RBI-supplied master data.** Office overflow sequence, entity→DO mapping, category→DO
   mapping, state-district table.
ANSWER: Still not received can you pick from Internet if not add few to enable demo best you can find we will populate them later

## 2. Start shared servers once

```bash
# Keycloak — standing authority, port 9090 only
cd c:/tools/keycloak-26.0.0 && bin/kc.bat start-dev --http-port=9090

# Your own dev server. Use 4202 for harness work (already CORS-allowed).
cd cms-portal-frontend && npx ng serve --port 4202

# MySQL on 3306 — cms_db is persistent, never emptied
```

**Register 4202 as a Keycloak redirect URI** on the `cms-frontend` client in realm `cms`
(`redirectUris` += `http://localhost:4202/*`, `webOrigins` += the origin). Without BOTH the CORS
allowlist entry and the redirect URI, staff browser suites fail in a way that looks exactly like
a broken feature. Verify:

```bash
curl -s -o /dev/null -w "%{http_code}" -X OPTIONS \
  http://localhost:8092/api/v1/i18n/translations/en \
  -H "Origin: http://localhost:4202" -H "Access-Control-Request-Method: GET"
# must be 200, not 403
```

## 3. Pre-warm the Maven cache once

```bash
cd cms-backend && mvn -o -q compile
```

Do this alone. Seven sessions racing a cold `~/.m2` can corrupt artifacts.

## 4. Decide worktrees now

With seven sessions, create one `git worktree` per session. `cms-backend/target/` is a single
directory and concurrent `mvn spring-boot:run` clobbers class output. If you skip worktrees, you
must start backends one at a time and never run a bare `mvn compile` while another is starting.

## 5. Confirm the story ID list

The attached backlog contains **281 distinct UST IDs** (UST422-675 and UST754-780; UST676-753
are absent). You said 283. Reconcile before Wave 2 signs off on coverage.

Answer: confirmed
