<script setup name="LeftBottom">
import { getHomeChart, chartTextColor } from './homeChart';
import moment from 'moment'

defineExpose({setData})

const myChart = ref({})

function setData(netList) {
  let timeList = netList?.map(i => moment(i.time).format("HH:mm:ss"))
  let inList = netList?.map(i => i.in.toFixed(2))
  let outList = netList?.map(i => i.out.toFixed(2))
  nextTick(() => {
    const chartDom = document.getElementById('leftBottomChart');
    let chart = getHomeChart(chartDom);
    if (!chart) return;
    myChart.value = chart
    let option;
    option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
      },
      toolbox: {
        show: true,
        feature: {
          magicType: { show: true, type: ['line', 'bar'] },
          restore: { show: true },
          saveAsImage: { show: true }
        }
      },
      legend: { left: 8 },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: timeList
      },
      yAxis: {
        type: 'value',
        splitNumber: 3,
        axisLabel: { formatter: '{value} MB' }
      },
      series: [
        {
          name: '下载',
          type: 'line',
          showSymbol: false,
          data: inList
        },
        {
          name: '上传',
          type: 'line',
          showSymbol: false,
          data: outList
        }
      ]
    };
    window.addEventListener("resize", resize);

    chart.setOption(option);
  })
}

function resize() {
  myChart.value?.resize?.();
}

onBeforeUnmount(() => {
  window.removeEventListener("resize",resize);
  myChart.value?.dispose?.();
})
</script>

<template>
  <div style="width: 100%;height: 300px;" id="leftBottomChart"></div>
</template>

<style scoped lang="scss">

</style>
