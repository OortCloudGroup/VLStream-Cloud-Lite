import * as echarts from 'echarts'

export function chartTextColor(element) {
  return getComputedStyle(element).getPropertyValue('--el-text-color-regular').trim() || '#606266'
}

export function getHomeChart(element) {
  if (!element) return null
  const existing = echarts.getInstanceByDom(element)
  if (existing) return existing
  const style = getComputedStyle(element)
  const color = chartTextColor(element)
  const border = style.getPropertyValue('--el-border-color-light').trim() || '#e4e7ed'
  const axis = { axisLabel: { color, hideOverlap: true }, axisLine: { lineStyle: { color: border } }, splitLine: { lineStyle: { color: border } } }
  return echarts.init(element, {
    color: ['#5b8ff9', '#62c4a4', '#f5bd64', '#e87c87', '#78b8d0'],
    textStyle: { color },
    legend: { textStyle: { color } },
    title: { textStyle: { color } },
    categoryAxis: axis,
    valueAxis: axis,
    toolbox: { iconStyle: { borderColor: color } },
    pie: { label: { color } }
  })
}
