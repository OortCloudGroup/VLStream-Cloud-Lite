# VLStream WVP Lite v1.0.12

## Release highlights

- Scope federated WVP permissions to grants verified by VLStream.
- Add repository documentation for device and media workflows.
- Restore the VLStream WVP Lite release identity and exclude historical SQL snapshots, the local SDK database, and the anonymous RTSP test endpoint from the public package.
- Keep deployment credentials and service endpoints environment-driven.
- No new Flyway migration is introduced; the existing schema lineage remains through `V1_2_16`.

The default administrator is `admin / 123456`; change the password immediately after the first login. The default deployment contains MySQL, Redis, EMQX, ZLMediaKit, and WVP as separate services. Firmware/OTA storage is optional and must be configured with the operator's own MinIO-compatible endpoint and credentials.

EHome remains opt-in and requires a device-reachable `EHOME_PUBLIC_HOST` plus applicable native SDK permissions. ISUP and Dahua native SDK integrations are disabled by default and are not verified as supported Linux deployment paths.
