<script setup name="BottomCenter">
import { getHomeChart, chartTextColor } from './homeChart';
import moment from 'moment'

defineExpose({setData})

const myChart = ref({})

function setData(memList) {
  let timeList = memList?.map(i => moment(i.time).format("HH:mm:ss"))
  let dataList = memList?.map(i => (i.data*100).toFixed(2))
  nextTick(() => {
    const chartDom = document.getElementById('bottomCenterChart');
    let chart = getHomeChart(chartDom);
    if (!chart) return;
    myChart.value = chart
    let option;
    option = {
      tooltip: {
        trigger: 'axis'
      },
      grid: { left: 12, right: 16, top: 50, bottom: 16, containLabel: true },
      toolbox: {
        show: true,
        feature: {
          magicType: { show: true, type: ['line', 'bar'] },
          restore: { show: true },
          saveAsImage: { show: true }
        }
      },
      calculable: true,
      xAxis: [
        {
          type: 'category',
          data: timeList
        }
      ],
      yAxis: [
        {
          type: 'value',
          min: 0,
          max: 100,
          axisLabel: {
            formatter: '{value} %'
          }
        }
      ],
      series: [
        {
          type: 'line',
          showSymbol: false,
          areaStyle: { opacity: 0.12 },
          data: dataList,
        },
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
  <div style="width: 100%;height: 300px;" id="bottomCenterChart"></div>
</template>

<style scoped lang="scss">

</style>
