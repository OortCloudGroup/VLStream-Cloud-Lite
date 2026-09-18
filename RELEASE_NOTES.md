# VLStream WVP Lite v1.0.6

## Release highlights

- Publish the public container image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.6`.
- Keep the VLStream WVP Lite Compose project and release archive coordinates.
- Keep the established Java module identifiers and `ry-wvp` database schema for runtime compatibility.
- Deploy MySQL, Redis, EMQX, ZLMediaKit, and the WVP backend as independent services.

- Add opt-in EHome native integration with x86_64 Linux runtime support,
  registration/stream port configuration, and a Compose overlay.
- Add localized frontend support and reliability guidance for multi-platform
  GB28181 integration and unattended recovery.
- Persist workbench layouts, improve device classification, and harden device
  registration and position persistence.
- Continue the configurable non-expiring local/VLStream token behavior and
  clean SIP resource release during restart.
- Add immutable Flyway migrations for EHome access and workbench persistence.
- Remove development-only endpoints and credential defaults from the public
  image; deployment-specific values must be supplied through environment variables.
- Continue the current MQTT, Flyway, local/SSO authentication, device playback,
  and OTA improvements.
- Default and external Compose deployments now explicitly enable the WVP MQTT
  extension and configure its broker through environment variables.
- The one-command package includes MySQL, Redis, EMQX, ZLMediaKit, and WVP.
- Includes the idempotent Flyway migration for legacy databases missing
  `sys_user.platform_user_id`.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus
the applicable native SDK permissions. The default administrator is
`admin / 123456`; change it after the first login.

The ISUP and Dahua native SDK integrations are disabled by default. See
DEPLOYMENT.md before enabling them.
