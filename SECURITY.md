# Security Policy

## Supported versions

JavaTavern is pre-1.0 software. Security fixes are applied to the latest source revision only.

## Reporting a vulnerability

Until a dedicated security email is published, use GitHub's private vulnerability reporting feature. Include:

- affected version or commit;
- device and Android version;
- reproduction steps;
- expected impact;
- whether chat data, API keys, files, or Agent actions are involved.

Please do not include real API keys or private conversations. Use a test key and synthetic data.

## Security boundaries

- API keys are encrypted with an Android Keystore-backed key.
- Cleartext model endpoints are rejected.
- Android backup and device transfer are disabled for app data.
- Imported character cards and model output are untrusted content.
- Destructive Agent actions require a local confirmation step and audit record.

These controls reduce risk but do not make a compromised device or malicious model service safe.
