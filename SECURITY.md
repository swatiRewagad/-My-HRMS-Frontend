# Security Rules

Baseline rules Claude Code must follow when writing or editing code in this
project. These are deliberately specific and actionable — vague advice like
"write secure code" gets ignored.

## Secrets & credentials
- Never hardcode secrets, API keys, tokens, passwords, or connection strings
  in source files. Use environment variables or a secrets manager.
- Never commit `.env` files or credential files. Add them to `.gitignore`.
- Never log full request/response bodies, auth headers, tokens, or PII.

## Injection
- All SQL must use parameterized queries / prepared statements — never
  string-concatenate or format user input into a query.
- Never pass unsanitized user input to a shell, `exec`, `eval`, or
  reflection-based call.
- Validate and escape user input used in HTML output, file paths, and
  OS commands.

## TLS / crypto
- Never disable TLS certificate verification (e.g. `verify=False`,
  `InsecureSkipVerify: true`, custom trust-all `X509TrustManager`).
- Use vetted crypto libraries only — no hand-rolled hashing/encryption.
- Passwords must be hashed with a modern algorithm (bcrypt/argon2/scrypt),
  never MD5/SHA1, never stored in plaintext.

## Auth & access control
- Every new endpoint must have an explicit authorization check — don't
  assume authentication implies authorization.
- Never trust client-supplied role/permission fields without server-side
  verification.

## Deserialization & dependencies
- Never deserialize untrusted data with unsafe deserializers (e.g. Java
  native `ObjectInputStream`, Python `pickle`, YAML `unsafe_load`).
- Don't add a new dependency without checking it's actively maintained and
  has no known critical CVEs.

## Error handling
- Never expose stack traces, internal paths, or raw exception messages to
  end users/API responses — log details server-side, return generic
  messages externally.
