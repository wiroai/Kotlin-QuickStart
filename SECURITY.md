# Security Policy

## Supported versions

Security fixes are provided for the latest published WiroKit release.
Pre-release source snapshots are supported on a best-effort basis.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability.

Email [hello@wiro.ai](mailto:hello@wiro.ai) with:

- the affected version or commit;
- a minimal reproduction;
- the expected impact;
- any suggested mitigation.

Do not include production API keys, signing keys, customer data, or other
secrets. Use redacted examples and wait for a secure exchange method if
additional evidence is required.

We will acknowledge the report, assess severity, and coordinate disclosure
and remediation with the reporter.

## Credential guidance

WiroKit supports direct API-key authentication for development and trusted
environments. Shipped mobile applications should use a backend proxy so
long-lived Wiro credentials are not embedded in the application.
