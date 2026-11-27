import { createHmac } from 'node:crypto'
import { writeFileSync } from 'node:fs'

const [deviceId, target] = process.argv.slice(2)
const secret = process.env.VLSTREAM_DEVICE_CLAIM_SECRET || ''
if (!/^[A-Za-z0-9_-]{1,100}$/.test(deviceId || '') || !target || Buffer.byteLength(secret) < 32) {
  throw new Error('Usage: set VLSTREAM_DEVICE_CLAIM_SECRET (32+ bytes), then run node tools/create-device-claim.mjs DEVICE_ID OUTPUT_JSON')
}
const type = 'vlstream-claim-v1'
const claimProof = createHmac('sha256', secret).update(type + '\n' + deviceId).digest('base64url')
writeFileSync(target, JSON.stringify({ type, deviceId, claimProof }), { flag: 'wx', mode: 0o600 })
