-- A historical heartbeat accepted an invalid 2088 clock value for this device.
-- Reset only that exact watermark; keep the device, ownership, state and streams.
-- A fresh real heartbeat will populate the watermark and state after startup.
UPDATE wvp_vlstream_device
SET last_reported_at = NULL
WHERE device_id = 'AETY-00-L8RV-81AA-00005000'
  AND last_message_id = 'up-heartbeat-3748204956-1'
  AND last_reported_at = '2088-10-10 08:02:36';
