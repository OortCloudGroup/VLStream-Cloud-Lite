<template>
  <div style="width: 100%;height: 300px;" id="leftTopChart"></div>
</template>

<script setup name="LeftTop">
import { getHomeChart, chartTextColor } from './homeChart';
import {countDeviceNum} from "../../../api/index.js";

const myChart = ref({})

onMounted(()=>{
  countDeviceNum().then((res)=>{
    nextTick(() => {
      const chartDom = document.getElementById('leftTopChart');
      let chart = getHomeChart(chartDom);
    if (!chart) return;
      myChart.value = chart
      let option;
      option = {
        tooltip: {
          trigger: 'item'
        },
        toolbox: {
          show: true,
          feature: {
            restore: { show: true },
            saveAsImage: { show: true }
          }
        },
        legend: {
          orient: 'horizontal',
          bottom: 0,
          left: 'center'
        },
        series: [
          {

            type: 'pie',
            radius: ['35%', '58%'],
            center: ['50%', '43%'],
            label: { show: false },
            data: [
              { value: res.data.totalGbNum, get name() { return translatePhrase("国标设备数") } },
              { value: res.data.totalIsupNum, get name() { return translatePhrase("海康设备数") } },
              { value: res.data.totalOnvifNum, get name() { return translatePhrase("onvif设备数") } },
              { value: res.data.totalRtspNum, get name() { return translatePhrase("rtsp设备数") } },
              { value: res.data.totalDahuaNum, get name() { return translatePhrase("大华设备数") } },
            ],
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      };
      window.addEventListener("resize", resize);

      chart.setOption(option);
    })
  })
})


function resize() {
  myChart.value?.resize?.();
}

onBeforeUnmount(() => {
  window.removeEventListener("resize",resize);
  myChart.value?.dispose?.();
})
</script>

<style scoped>

</style>
