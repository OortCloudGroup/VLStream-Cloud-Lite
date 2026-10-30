# VLStream WVP Lite v1.0.11

## Release highlights

- Add revocable, multi-protocol source grants for Cloud AI.
- Add tenant-scoped VLStream event locations and scoped ONVIF device-preset endpoints.
- Make the GB28181 platform identity configurable at runtime and editable from the WVP UI.
- Validate RTSP device hosts and ports, bound SIP worker usage, and honor configured SIP log levels.
- Include immutable Flyway migrations through `V1_2_16` for SIP identity configuration and Cloud AI source grants. Existing databases upgrade through Flyway; fresh installations use the sanitized release SQL.
- Retain tenant-scoped VLStream device sharing with explicit owner approval, short-lived read-only access, and authorization checks. New sharing permissions are not assigned to existing roles automatically.
- Keep the VLStream WVP Lite image and archive coordinates consistent. Exclude historical SQL snapshots, the local Windows SDK database, and the anonymous RTSP test endpoint from the public release.

The default administrator is `admin / 123456`; change the password immediately after the first login. The default deployment contains MySQL, Redis, EMQX, ZLMediaKit, and WVP as separate services. Firmware/OTA storage is optional and must be configured with the operator's own MinIO-compatible endpoint and credentials.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus applicable native SDK permissions. ISUP and Dahua native SDK integrations are disabled by default and are not verified as supported Linux deployment paths.
