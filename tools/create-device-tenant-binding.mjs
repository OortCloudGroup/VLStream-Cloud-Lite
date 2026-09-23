import { createHmac } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const [deviceId, tenantId, outputPath] = process.argv.slice(2)
const secret = process.env.VLSTREAM_DEVICE_TENANT_BINDING_SECRET || ''
if (!/^[A-Za-z0-9_-]{1,100}$/.test(deviceId || '') ||
    !/^[A-Za-z0-9_-]{1,64}$/.test(tenantId || '') || !outputPath ||
    Buffer.byteLength(secret, 'utf8') < 32) {
  console.error('Usage: node tools/create-device-tenant-binding.mjs DEVICE_ID TENANT_ID OUTPUT_JSON; set a 32-byte or longer VLSTREAM_DEVICE_TENANT_BINDING_SECRET in the trusted provisioning environment.')
  process.exit(1)
}
const tenantBindingProof = 'v1.' + createHmac('sha256', secret)
  .update(`v1\n${deviceId}\n${tenantId}`, 'utf8').digest('base64url')
const target = resolve(outputPath)
try {
  writeFileSync(target, JSON.stringify({ deviceId, tenantId, tenantBindingProof }, null, 2) + '\n', {
    mode: 0o600, flag: 'wx'
  })
  console.log('Created device-specific tenant binding. The signing secret was not exported.')
} catch {
  console.error('Cannot create output file; existing files are never overwritten.')
  process.exit(1)
}
