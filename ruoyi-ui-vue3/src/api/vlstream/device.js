import request from '@/utils/request'

export function listDevices(query) {
  return request({ url: '/vlstream/device/list', method: 'get', params: query })
}

export function listStreams(deviceRowId) {
  return request({ url: `/vlstream/device/${deviceRowId}/streams`, method: 'get' })
}

export function getDeviceDetail(deviceRowId) {
  return request({ url: `/vlstream/device/${deviceRowId}/detail`, method: 'get' })
}

export function startPreview(deviceRowId, streamId) {
  return request({
    url: `/vlstream/device/${deviceRowId}/preview`,
    method: 'post',
    data: { streamId }
  })
}

export function getMediaStatus() {
  return request({ url: '/vlstream/device/media/status', method: 'get' })
}
