import request from '@/utils/request'

export function listCustomDevices(query) { return request({ url: '/custom/device/list', method: 'get', params: query }) }
export function getCustomDevice(id) { return request({ url: `/custom/device/${id}`, method: 'get' }) }
export function addCustomDevice(data) { return request({ url: '/custom/device', method: 'post', data }) }
export function updateCustomDevice(data) { return request({ url: '/custom/device', method: 'put', data }) }
export function deleteCustomDevices(ids) { return request({ url: `/custom/device/${ids}`, method: 'delete' }) }
export function previewCustomDevice(id) { return request({ url: `/custom/device/${id}/preview`, method: 'post', timeout: 35000 }) }
export function getCustomMediaStatus() { return request({ url: '/custom/device/media/status', method: 'get' }) }
export function startCustomRecord(id) { return request({ url: `/custom/device/${id}/record/start`, method: 'post' }) }
export function stopCustomRecord(id) { return request({ url: `/custom/device/${id}/record/stop`, method: 'post' }) }
export function getCustomRecordStatus(id) { return request({ url: `/custom/device/${id}/record/status`, method: 'get' }) }
export function getCustomRecordPlan(id) { return request({ url: `/custom/device/${id}/record-plan`, method: 'get' }) }
export function saveCustomRecordPlan(id, data) { return request({ url: `/custom/device/${id}/record-plan`, method: 'put', data }) }
