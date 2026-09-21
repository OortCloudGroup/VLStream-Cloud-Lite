<script setup name="RightBottom">
import { getHomeChart, chartTextColor } from './homeChart';

defineExpose({setData})

const myChart = ref({})

function setData(diskList) {
  let pathList = diskList?.map(i => i.path)
  let freeList = diskList?.map(i => i.free.toFixed(2))
  let useList = diskList?.map(i => i.use.toFixed(2))
  nextTick(() => {
    const chartDom = document.getElementById('rightBottomChart');
    let chart = getHomeChart(chartDom);
    if (!chart) return;
    myChart.value = chart
    let option;
    option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      toolbox: {
        show: true,
        feature: {
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
        type: 'value',
        splitNumber: 3,
        boundaryGap: [0, 0.01],
        axisLabel: {
          formatter: '{value} GB'
        }
      },
      yAxis: {
        type: 'category',
        data: pathList,
      },
      series: [
        {
          get name() { return translatePhrase("未使用") },
          type: 'bar',
          data: freeList
        },
        {
          get name() { return translatePhrase("已使用") },
          type: 'bar',
          data: useList
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
  <div style="width: 100%;height: 300px;" id="rightBottomChart"></div>
</template>

<style scoped lang="scss"></style>
