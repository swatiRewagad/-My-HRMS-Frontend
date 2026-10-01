# Permissions: what was added, and how to port it to another project

Audited 2026-09-18 across 39 recent sessions / 5,075 Bash calls in
`~/.claude/projects/c--Projects-My-HRMS-Frontend/`.

## Where the allowlist actually lives

`c:\Projects\My-HRMS-Frontend\.claude\settings.json` — the **parent** directory, not
`CMS2.0_Redzone/.claude/` (which contains only `memory/`). Worth knowing before the RBIO wave,
because a per-session `git worktree` would NOT inherit it — see "Worktree warning" below.

## Added this pass (23 entries, 97 → 120)

The prior pass already covered the high-frequency commands. What still prompted:

| Pattern | Count | Why it slipped through |
|---|---|---|
| `timeout {600,900,1200,1800,2400} mvn *` | 31 | `mvn *` was allowed, but a `timeout` prefix makes the whole string a different command |
| `timeout {600,900,1500} npx playwright test *` | 22 | same defeat |
| `timeout 900 npx ng build *` | 2 | same defeat |
| `for i in *`, `for user in *`, `for k in *`, `for port in *` | 34 | loop vars not among the 11 already listed; a loop is ONE Bash string, so its body is not separately evaluated |
| `bin/kcadm.bat {get,get-roles,get-role-mappings,config} *` | 11 | `bin/kc.bat *` was allowed; `kcadm.bat` is a different binary |
| `tasklist`, `tasklist *` | 10 | Windows `ps`. `ps` is auto-allowed; `tasklist` is not |
| `where *` | 8 | Windows `which`. Same gap |
| `nohup npx ng serve *`, `nohup bin/kc.bat start-dev *` | 6 | `nohup` prefix defeats the base pattern, same as `timeout` |
| `pdftotext -layout *` | — | generalised from a single hardcoded PDF path |

**The generalisable lesson: a prefix defeats a pattern.** `timeout N`, `nohup` and `sudo` each
create a distinct command string. If a base command is allowlisted and still prompts, check whether
something is being prefixed onto it.

## `python -c` is fixed — leave it denied

It was the single largest prompt source (~797 calls historically). In the six most recent sessions
it is **zero**, with `scripts/qa/` helpers used instead. The guidance worked; do not weaken it.
`Bash(python *)` must never be allowlisted — reads and writes are not separable by pattern
(`s=io.open(p).read(); ...; io.open(p,'w').write(s)` looks read-only until the final call).

## Also removed

Two junk entries that got auto-added from my own analysis commands during this session:
`Bash(sed -E 's/^[A-Z_]...' allcmds.txt)` and a sibling. `sed` is already auto-allowed for
read-only expressions, so pinned one-off `sed` invocations are pure noise. Worth periodically
sweeping for these — one-off entries with a literal filename in them are almost always noise.

## Still prompting on purpose — do not "fix"

`rm`, `mv`, `taskkill`, `powershell`, `git commit`, `git push`, `git reset --hard`, and every
interpreter/shell/package-runner wildcard. `taskkill` can kill the user's own dev server on 4200 or
their IDE. The standing rule that Claude never commits or pushes would be quietly revoked by
allowlisting git writes.

## Worktree warning for the RBIO wave

If you create one `git worktree` per session (recommended in `01-STAGE0-preflight-checklist.md` to
avoid seven sessions clobbering `cms-backend/target/`), each worktree is a **different cwd**, so it
gets a different project settings scope and will **not** inherit this allowlist. Options:

1. Copy `.claude/settings.json` into each worktree, or
2. Promote the portable subset to `~/.claude/settings.json` (user scope, applies everywhere), or
3. Skip worktrees and start backends one at a time.

Option 2 is the least work for a seven-session wave.

---

# Porting to the other project (different machine)

Use **`portable-permissions-template.json`** in this folder. Copy it to
`<other-project>/.claude/settings.json`, then edit per its `_README` key and delete that key.

## Why not just copy the whole file

Of the 120 entries here, roughly 40 are unportable:

- **~20 hardcoded one-off MySQL queries** with the full
  `"/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" -u cms_user -pcms_pass cms_db -e "..."`
  string, including **live credentials**. Never copy these to another machine or repo.
- **~12 pinned Playwright invocations** naming specific spec files
  (`e2e/aa/s2a-parent-search.spec.ts`) and specific ports (8092/8094/8096) that don't exist there.
- **Project-specific scripts**: `deployment/aa-build.sh`, `deployment/run-test-backend.sh`,
  `deployment/provision-aa-roles.sh`, `scripts/qa/json_field.py`, `scripts/qa/i18n_check.py`.
- **`additionalDirectories`** — absolute paths to this machine's Keycloak, OpenSearch, Prometheus
  and `c:\tmp`.
- **One `powershell` entry** that force-kills node processes. Machine-specific and destructive.

The template keeps only the toolchain-shaped patterns (Maven, Angular CLI, Playwright, curl, loop
forms, common utilities) that generalise to any Java+Angular project.

## Recommended split on the new machine

- **User scope** (`~/.claude/settings.json`) — the toolchain blocks. They're useful in every project
  on that machine and survive worktrees and repo moves.
- **Project scope** (`<project>/.claude/settings.json`) — only that project's own scripts and paths.

Keep credential-bearing commands out of **both**. If a DB query needs allowlisting, prefer a
checked-in read-only script (the `scripts/qa/` pattern) and allowlist that one path instead — it is
reviewable, and it can't be widened by accident.
