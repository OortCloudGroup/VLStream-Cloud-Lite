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
              { value: res.data.totalGbNum, name: '国标设备数' },
              { value: res.data.totalIsupNum, name: '海康设备数' },
              { value: res.data.totalOnvifNum, name: 'onvif设备数' },
              { value: res.data.totalRtspNum, name: 'rtsp设备数' },
              { value: res.data.totalDahuaNum, name: '大华设备数' },
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
