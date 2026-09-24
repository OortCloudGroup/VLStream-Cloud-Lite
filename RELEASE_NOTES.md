# VLStream WVP Lite v1.0.7

## Release highlights

- Publish the public container image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.7`.
- Keep the VLStream WVP Lite Compose project and release archive coordinates.
- Keep the established Java module identifiers and `ry-wvp` database schema for runtime compatibility.
- Deploy MySQL, Redis, EMQX, ZLMediaKit, and the WVP backend as independent services.

- Enforce tenant ownership for VLStream MQTT devices. Authorization scopes come
  from the authenticated server identity; the new Flyway migration adds nullable
  device ownership without guessing legacy ownership.
- Support authenticated first-time tenant binding with a device-specific
  HMAC proof. Legacy devices that omit a tenant remain in the configured default
  tenant. See `docs/VLSTREAM_TENANT_BINDING.md` before provisioning.
- Complete localization across the management frontend.
- Add opt-in EHome native integration with x86_64 Linux runtime support,
  registration/stream port configuration, and a Compose overlay.
- Persist VLStream device state snapshots, device locations, and workbench layouts;
  improve device classification and registration persistence.
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
  workbench persistence, and VLStream device tenant ownership.
- The release archive includes EHome and reliability documentation plus the
  tenant-binding guide and provisioning tool.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus
the applicable native SDK permissions. The default administrator is
`admin / 123456`; change it after the first login.

The ISUP and Dahua native SDK integrations are disabled by default. See
`DEPLOYMENT.md` before enabling them. Device-specific tenant binding is optional;
the HMAC signing secret is required only when provisioning an explicit tenant.
