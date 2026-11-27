# VLStream WVP Lite v1.0.13

## Release highlights

- Add factory device claim codes for device ownership: a trusted issuer signs per-device `vlstream-claim-v1` tags with a shared HMAC-SHA256 secret, owners scan the tag, and `POST /vlstream/device/claim` binds the device to the signed-in tenant with first-claim transactional semantics and cross-tenant conflict protection. The package ships the `tools/create-device-claim.mjs` issuance helper and the `V1_2_18` Flyway migration that adds the claim audit columns.
- EHome CMS/Stream now starts on Linux by default with the same initialization path as Windows, and `EHOME_MEDIA_BASE_URL` can route preview playback through a browser-reachable media gateway; `EHOME_ENABLED`/`EHOME_LINUX_ENABLED` remain explicit opt-outs.
- The device tenant mode is now configurable to match the VLS platform: `SSO_TENANT_TYPE` (`single`/`multi`) plus a `VLSTREAM_DEVICE_MULTI_TENANT_DEFAULT_TENANT_ID` pass-through in both Compose files.
- RTSP preview pulls are shared across viewers instead of starting duplicate proxy streams, with explicit stream-proxy failure reporting.
- ONVIF registration verifies media access before completing registration, and the cloud-AI source picker rejects offline VLStream sources.
- Local media configuration no longer inherits another deployment's global database default (`media.load-configured-only`), and the internal device API exposes device categories.
- Seed the optional AMap JS API and security key settings through the idempotent `V1_2_17` Flyway migration so map credentials stay per-installation values.
- Re-apply the public release boundary: restore the VLStream release identity and environment-driven defaults, and keep the historical SQL snapshot, the local SDK database, and the anonymous RTSP test endpoint out of the public package.

The default administrator is `admin / 123456`; change the password immediately after the first login. The default deployment contains MySQL, Redis, EMQX, ZLMediaKit, and WVP as separate services. Firmware/OTA storage is optional and must be configured with the operator's own MinIO-compatible endpoint and credentials. Existing databases upgrade through immutable Flyway migrations at startup; fresh installs initialize from the sanitized SQL.

EHome devices still need a reachable `EHOME_PUBLIC_HOST` and applicable native SDK permissions. ISUP and Dahua native SDK integrations stay disabled in the default Compose deployment (`DAHUA_ENABLED=false`, native ISUP SDK off) and are not verified as supported Linux deployment paths.
