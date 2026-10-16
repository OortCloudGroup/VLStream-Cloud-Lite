# VLStream WVP Lite v1.0.9

## Release highlights

- Publish the versioned backend image as `ghcr.io/oortcloudgroup/vlstream-cloud-lite:1.0.9` and retain the established Compose, Java module, and `ry-wvp` database identifiers.
- Add a tenant-scoped video catalog across supported protocols and improve RTSP and ONVIF device access isolation.
- Add leased RTSP preview sessions and scoped ONVIF playback, with more reliable stream-availability checks for custom video sources.
- Improve ONVIF discovery and PTZ capability handling, device classification, scene tags, and device-management workflows.
- Isolate configured media nodes, recover completed health timers, tighten ZLMediaKit API validation, and remove credentials from media connection logs.
- Remove the anonymous development RTSP-to-RTMP test endpoint, move deployment and SDK secrets to environment-only configuration, and disable the Druid monitor page by default.
- Include the immutable Flyway migrations through the protocol-device tenant ownership update. Existing databases upgrade through Flyway; fresh installations use the sanitized release SQL.
- Exclude historical SQL snapshots and the local Windows SDK database from the release tree; remove private runtime defaults from the image and require deployment secrets through environment variables.

The default administrator is `admin / 123456`; change the password immediately after the first login. The default deployment contains MySQL, Redis, EMQX, ZLMediaKit, and WVP as separate services. Firmware/OTA storage is optional and must be configured with the operator's own MinIO-compatible endpoint and credentials.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus applicable native SDK permissions. ISUP and Dahua native SDK integrations are disabled by default and are not verified as supported Linux deployment paths.
