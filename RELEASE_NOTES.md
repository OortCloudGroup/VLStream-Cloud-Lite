# VLStream WVP Lite v1.0.10

## Release highlights

- Publish the versioned backend image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.10` and retain the established Compose, Java module, and `ry-wvp` database identifiers.
- Add tenant-scoped VLStream device sharing with explicit owner approval, short-lived read-only access, and authorization checks when listing or starting a preview. New sharing permissions are not assigned to existing roles automatically.
- Align device ownership with single- and multi-tenant modes, and strip unused gateway identity metadata before firewall enforcement.
- Include the immutable Flyway migration for device sharing. Existing databases upgrade through Flyway; fresh installations use the sanitized release SQL.
- Retain the v1.0.9 improvements: protocol-scoped device catalog and playback, isolated media nodes, hardened ZLMediaKit access, and environment-only deployment secrets.
- Exclude historical SQL snapshots and the local Windows SDK database from the release tree; keep Druid monitoring disabled by default.

The default administrator is `admin / 123456`; change the password immediately after the first login. The default deployment contains MySQL, Redis, EMQX, ZLMediaKit, and WVP as separate services. Firmware/OTA storage is optional and must be configured with the operator's own MinIO-compatible endpoint and credentials.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus applicable native SDK permissions. ISUP and Dahua native SDK integrations are disabled by default and are not verified as supported Linux deployment paths.
