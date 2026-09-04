# VLStream WVP Lite v1.0.4

## Release highlights

- Publish the public container image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.4`.
- Rename the Compose project and release archive to VLStream WVP Lite.
- Keep the established Java module identifiers and `ry-wvp` database schema for runtime compatibility.
- Deploy MySQL, Redis, EMQX, ZLMediaKit, and the WVP backend as independent services.

- Patch release for the current MQTT, Flyway, local/SSO authentication, device
  playback, and OTA improvements.
- Default and external Compose deployments now explicitly enable the WVP MQTT
  extension and configure its broker through environment variables.
- The one-command package includes MySQL, Redis, EMQX, ZLMediaKit, and WVP.
- Includes the idempotent Flyway migration for legacy databases missing
  `sys_user.platform_user_id`.

The default administrator is `admin / 123456`; change it after the first login.
The ISUP and Dahua native SDK integrations are disabled by default. See
DEPLOYMENT.md before enabling them.
