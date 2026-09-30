# VLStream WVP Lite v1.0.8

## Release highlights

- Publish the public container image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.8`.
- Keep the VLStream WVP Lite Compose project and release archive coordinates.
- Keep the established Java module identifiers and `ry-wvp` database schema for runtime compatibility.
- Deploy MySQL, Redis, EMQX, ZLMediaKit, and the WVP backend as independent services.

- Enforce tenant ownership for VLStream MQTT devices. Authorization scopes come
  from the authenticated server identity; Flyway adds nullable device ownership
  without guessing legacy assignments.
- Support authenticated first-time tenant binding with device-specific HMAC
  proofs; legacy devices that omit a tenant remain in the configured default.
- Complete localization across the management frontend and keep EHome native
  integration opt-in through its Compose overlay.
- Block playback previews for offline VLStream devices, hide retired Dahua menu
  entry points, and disable unavailable GAT1400 menu entries.
- Add an immutable Flyway migration to disable legacy GAT1400 menu entries on
  existing databases without deleting menu or role data.
- Persist VLStream device state snapshots, device locations, and workbench
  layouts; improve device classification and registration persistence.
- Release SIP resources cleanly during restart and retain the configurable
  non-expiring local/VLStream token behavior.
- Remove development-only endpoints and credential defaults from the public
  image; deployment-specific values must be supplied through environment variables.
- Continue the current MQTT, Flyway, local/SSO authentication, device playback,
  and OTA improvements.
- Default and external Compose deployments now explicitly enable the WVP MQTT
  extension and configure its broker through environment variables.
- The one-command package includes MySQL, Redis, EMQX, ZLMediaKit, and WVP.
- Includes idempotent Flyway migrations for legacy platform-user IDs, EHome,
  workbench persistence, VLStream device tenant ownership, and GAT1400 menu
  availability (`V1_2_10__disable_unavailable_gat1400_menus.sql`).
- Excludes the legacy database snapshot from release source archives and Docker
  build context; fresh deployments use the sanitized release initialization SQL.
- Packages EHome and reliability documentation plus the tenant-binding guide
  and provisioning tool.

The default administrator is `admin / 123456`; change it after the first login.
EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus
applicable native SDK permissions. ISUP and Dahua native SDK integrations are
disabled by default. See `DEPLOYMENT.md` before enabling them.
