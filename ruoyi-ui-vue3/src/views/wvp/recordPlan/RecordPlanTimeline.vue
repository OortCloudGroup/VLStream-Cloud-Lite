<template>
  <div class="record-plan-timeline">
    <el-form v-if="repeat === 'next'" class="interval_form" label-position="top" :model="intervalForm">
      <el-form-item :label="$tp('每隔')">
        <el-input v-model.number="intervalForm.interval" class="interval_input" @change="emitChange">
          <template #suffix>{{ $tp("天") }}</template>
        </el-input>
      </el-form-item>
    </el-form>

    <div class="timeline_wrap" :class="{ 'is-month': repeat === 'month' }">
      <div class="timeline_head">
        <div class="head_left" />
        <div class="head_right">
          <div class="ticks">
            <div
              v-for="(t, index) in ticks"
              :key="t"
              class="tick"
              :style="tickStyle(index)"
            >
              <div class="tick_num">{{ t }}</div>
              <div class="tick_line" />
            </div>
          </div>
        </div>
      </div>

      <div v-for="day in dayRows" :key="day.key" class="timeline_row">
        <div
          class="row_label"
          :class="{ selected: selectedDayKey === day.key }"
          @click="handleRowLabelClick(day.key)"
        >
          {{ day.label }}
        </div>
        <div class="row_track" @mousedown="handleTrackMouseDown($event, day.key)">
          <div class="track_bg" />
          <div
            v-for="seg in segmentsByDay[day.key] || []"
            :key="seg.id"
            class="seg"
            :class="{ selected: selectedSegId === seg.id }"
            :style="segmentStyle(seg)"
            @mousedown.stop="handleSegmentMouseDown($event, day.key, seg)"
            @dblclick.stop="handleSegmentDelete(day.key, seg.id)"
          >
            <div class="seg_resize_left" @mousedown.stop="handleResizeStart($event, day.key, seg, 'left')" />
            <div class="seg_resize_right" @mousedown.stop="handleResizeStart($event, day.key, seg, 'right')" />
            <div class="seg_delete" @click.stop="handleSegmentDelete(day.key, seg.id)">
              <el-icon><Close /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="editPop.visible" class="edit_pop_mask" @click.self="closeEditPop">
      <div class="edit_pop" :style="{ left: `${editPop.left}px`, top: `${editPop.top}px` }">
        <div class="edit_pop_close" @click="closeEditPop">×</div>
        <div class="edit_time_row">
          <el-time-picker
            v-model="editPop.start"
            class="edit_time_picker"
            :placeholder="$tp('开始时间')"
            format="HH:mm"
            value-format="HH:mm:ss"
            :clearable="false"
          />
          <span class="edit_time_dash">-</span>
          <el-time-picker
            v-model="editPop.end"
            class="edit_time_picker"
            :placeholder="$tp('结束时间')"
            format="HH:mm"
            value-format="HH:mm:ss"
            :clearable="false"
          />
        </div>
        <div class="edit_actions">
          <div class="edit_delete" @click="handleEditDelete">{{ $tp("删除") }}</div>
          <div class="edit_save" @click="handleEditSave">{{ $tp("保存") }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from "element-plus";

const props = defineProps({
  repeat: {
    type: String,
    default: "day"
  },
  modelValue: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["update:modelValue"]);

const ticks = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24];
const weekdayKeys = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
const weekdayLabels = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"];

const segmentsByDay = reactive({});
const intervalForm = reactive({ interval: 1 });
const selectedSegId = ref(null);
const selectedDayKey = ref(null);
const isSyncing = ref(false);

const dayRows = computed(() => {
  if (props.repeat === "month") {
    return Array.from({ length: 31 }, (_, i) => ({ key: `day${i + 1}`, label: `${i + 1}` }));
  }
  if (props.repeat === "week") {
    return weekdayKeys.map((key, i) => ({ key, label: weekdayLabels[i] }));
  }
  if (props.repeat === "next") {
    return [{ key: "next", get label() { return translatePhrase("隔天") } }];
  }
  return [{ key: "day", get label() { return translatePhrase("每天") } }];
});

const tickStyle = (index) => ({
  left: `${(index / (ticks.length - 1)) * 100}%`,
  transform: "translateX(-50%)"
});

const snapHalfHour = (time) => Math.round(Math.max(0, Math.min(24, time)) * 2) / 2;

const segmentStyle = (seg) => {
  const duration = seg.end - seg.start;
  let widthPercent = (duration / 24) * 100;
  if (duration < 0.1) {
    widthPercent = Math.max((0.1 / 24) * 100, widthPercent);
  }
  return {
    left: `${(seg.start / 24) * 100}%`,
    width: `${widthPercent}%`,
    background: "var(--el-color-primary)"
  };
};

let segIdCounter = 0;
const generateSegId = () => {
  segIdCounter += 1;
  return `seg_${Date.now()}_${segIdCounter}`;
};

const clearSegments = () => {
  Object.keys(segmentsByDay).forEach((key) => delete segmentsByDay[key]);
};

const checkOverlap = (dayKey, start, end, excludeId) => {
  return (segmentsByDay[dayKey] || []).some((seg) => {
    if (excludeId && seg.id === excludeId) return false;
    return !(end <= seg.start || start >= seg.end);
  });
};

const hoursToStartIndex = (h) => Math.max(0, Math.floor(h * 2 + 1e-9));
const hoursToStopIndex = (h) => Math.min(47, Math.max(0, Math.ceil(h * 2 - 1e-9) - 1));
const startIndexToHours = (i) => i / 2;
const stopIndexToHours = (i) => (i + 1) / 2;

const mergeRanges = (ranges) => {
  if (!ranges.length) return [];
  const sorted = [...ranges].sort((a, b) => a.start - b.start);
  const result = [sorted[0]];
  for (let i = 1; i < sorted.length; i++) {
    const last = result[result.length - 1];
    const cur = sorted[i];
    if (cur.start <= last.stop + 1) {
      last.stop = Math.max(last.stop, cur.stop);
    } else {
      result.push({ ...cur });
    }
  }
  return result;
};

/** 转为后端 planItemList */
const toPlanItemList = (repeatOverride) => {
  const mode = repeatOverride || props.repeat;
  const pushDay = (weekDay, segs, list) => {
    (segs || []).forEach((seg) => {
      const start = hoursToStartIndex(seg.start);
      const stop = hoursToStopIndex(seg.end);
      if (stop >= start) {
        list.push({ weekDay, start, stop });
      }
    });
  };

  const list = [];
  if (mode === "day") {
    const segs = segmentsByDay.day || [];
    for (let weekDay = 1; weekDay <= 7; weekDay++) {
      pushDay(weekDay, segs, list);
    }
  } else if (mode === "month") {
    // 后端仅支持 weekDay：1-7 号映射到周一到周日，其余日期的时段并入对应星期
    for (let weekDay = 1; weekDay <= 7; weekDay++) {
      const merged = [];
      for (let d = weekDay; d <= 31; d += 7) {
        (segmentsByDay[`day${d}`] || []).forEach((s) => merged.push(s));
      }
      pushDay(weekDay, merged, list);
    }
  } else if (mode === "next") {
    const segs = segmentsByDay.next || [];
    const interval = Math.max(1, Number(intervalForm.interval) || 1);
    for (let weekDay = 1; weekDay <= 7; weekDay += interval) {
      pushDay(weekDay, segs, list);
    }
  } else if (mode === "week") {
    weekdayKeys.forEach((key, idx) => {
      pushDay(idx + 1, segmentsByDay[key] || [], list);
    });
  }
  return list;
};

/** 从后端 planItemList 回填 */
const fromPlanItemList = (planItemList = []) => {
  isSyncing.value = true;
  clearSegments();
  selectedSegId.value = null;
  selectedDayKey.value = null;

  const byWeek = {};
  planItemList.forEach((item) => {
    if (!item || item.weekDay == null) return;
    if (!byWeek[item.weekDay]) byWeek[item.weekDay] = [];
    byWeek[item.weekDay].push({
      start: startIndexToHours(item.start),
      end: stopIndexToHours(item.stop)
    });
  });

  Object.keys(byWeek).forEach((day) => {
    byWeek[day] = mergeRanges(
      byWeek[day].map((r) => ({
        start: hoursToStartIndex(r.start),
        stop: hoursToStopIndex(r.end)
      }))
    ).map((r, index) => ({
      id: `s_${day}_${index}`,
      start: startIndexToHours(r.start),
      end: stopIndexToHours(r.stop)
    }));
  });

  const days = Object.keys(byWeek).map(Number).sort((a, b) => a - b);
  const normalize = (arr) => (arr || []).map(({ start, end }) => ({ start, end }));
  const sameAs = (a, b) => JSON.stringify(normalize(a)) === JSON.stringify(normalize(b));

  let inferred = "week";
  if (days.length === 7 && days.every((d) => sameAs(byWeek[1], byWeek[d]))) {
    inferred = "day";
  } else if (days.length > 1) {
    const first = days[0];
    const gaps = days.slice(1).map((d, i) => d - days[i]);
    if (gaps.length && gaps.every((g) => g === gaps[0]) && gaps[0] > 1 && days.every((d) => sameAs(byWeek[first], byWeek[d]))) {
      inferred = "next";
      intervalForm.interval = gaps[0];
    }
  }

  if (inferred === "day") {
    segmentsByDay.day = (byWeek[1] || []).map((s, i) => ({ ...s, id: `s_day_${i}` }));
  } else if (inferred === "next") {
    const first = days[0];
    segmentsByDay.next = (byWeek[first] || []).map((s, i) => ({ ...s, id: `s_next_${i}` }));
  } else {
    weekdayKeys.forEach((key, idx) => {
      const segs = byWeek[idx + 1] || [];
      if (segs.length) {
        segmentsByDay[key] = segs.map((s, i) => ({ ...s, id: `s_${key}_${i}` }));
      }
    });
  }

  emit("update:modelValue", toPlanItemList(inferred));
  nextTick(() => {
    isSyncing.value = false;
  });
  return inferred;
};

const cloneSegs = (segs) => (segs || []).map((s) => ({ id: generateSegId(), start: s.start, end: s.end }));

const migrateSegments = (from, to) => {
  if (!from || from === to) return;
  if (from === "day" && to === "week") {
    const segs = segmentsByDay.day || [];
    weekdayKeys.forEach((key) => {
      segmentsByDay[key] = cloneSegs(segs);
    });
  } else if (from === "day" && to === "next") {
    segmentsByDay.next = cloneSegs(segmentsByDay.day || []);
  } else if (from === "week" && to === "day") {
    segmentsByDay.day = cloneSegs(segmentsByDay.mon || []);
  } else if (from === "week" && to === "next") {
    segmentsByDay.next = cloneSegs(segmentsByDay.mon || []);
  } else if (from === "next" && to === "day") {
    segmentsByDay.day = cloneSegs(segmentsByDay.next || []);
  } else if (from === "next" && to === "week") {
    const segs = segmentsByDay.next || [];
    weekdayKeys.forEach((key) => {
      segmentsByDay[key] = cloneSegs(segs);
    });
  } else if (to === "month" && (from === "day" || from === "next")) {
    const segs = cloneSegs(segmentsByDay[from] || []);
    for (let i = 1; i <= 31; i++) {
      segmentsByDay[`day${i}`] = cloneSegs(segs);
    }
  } else if (from === "month" && to === "day") {
    segmentsByDay.day = cloneSegs(segmentsByDay.day1 || []);
  } else if (from === "month" && to === "week") {
    weekdayKeys.forEach((key, idx) => {
      segmentsByDay[key] = cloneSegs(segmentsByDay[`day${idx + 1}`] || []);
    });
  }
};

const emitChange = () => {
  if (isSyncing.value) return;
  emit("update:modelValue", toPlanItemList());
};

watch(
  () => props.repeat,
  (val, oldVal) => {
    if (isSyncing.value) return;
    selectedSegId.value = null;
    selectedDayKey.value = null;
    migrateSegments(oldVal, val);
    emitChange();
  }
);

watch(segmentsByDay, () => emitChange(), { deep: true });

const handleRowLabelClick = (dayKey) => {
  selectedDayKey.value = selectedDayKey.value === dayKey ? null : dayKey;
  selectedSegId.value = null;
};

const handleSegmentDelete = (dayKey, segId) => {
  const segments = segmentsByDay[dayKey];
  if (!segments) return;
  const index = segments.findIndex((s) => s.id === segId);
  if (index > -1) segments.splice(index, 1);
  if (selectedSegId.value === segId) selectedSegId.value = null;
  if (editPop.visible && editPop.segId === segId) closeEditPop();
};

const editPop = reactive({
  visible: false,
  dayKey: "",
  segId: "",
  left: 0,
  top: 0,
  start: "00:00:00",
  end: "00:00:00"
});

const hoursToTime = (hours) => {
  const h = Math.floor(hours);
  const m = Math.round((hours - h) * 60);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:00`;
};

const timeToHours = (timeStr) => {
  const [hours, minutes] = timeStr.split(":").map(Number);
  return hours + minutes / 60;
};

const closeEditPop = () => {
  editPop.visible = false;
  editPop.dayKey = "";
  editPop.segId = "";
};

const openEditPop = (dayKey, segId, anchorEl) => {
  const seg = (segmentsByDay[dayKey] || []).find((s) => s.id === segId);
  if (!seg) return;
  selectedSegId.value = segId;
  editPop.dayKey = dayKey;
  editPop.segId = segId;
  editPop.start = hoursToTime(seg.start);
  editPop.end = hoursToTime(seg.end);
  let left = (window.innerWidth - 320) / 2;
  let top = (window.innerHeight - 140) / 2;
  if (anchorEl) {
    const rect = anchorEl.getBoundingClientRect();
    left = rect.left + rect.width / 2 - 160;
    top = rect.top - 160;
  }
  editPop.left = Math.max(10, Math.min(window.innerWidth - 330, left));
  editPop.top = Math.max(10, Math.min(window.innerHeight - 150, top));
  editPop.visible = true;
};

const handleEditDelete = () => {
  if (!editPop.dayKey || !editPop.segId) return;
  handleSegmentDelete(editPop.dayKey, editPop.segId);
  closeEditPop();
};

const handleEditSave = () => {
  const start = snapHalfHour(timeToHours(editPop.start));
  const end = snapHalfHour(timeToHours(editPop.end));
  if (!(start >= 0 && start <= 24 && end >= 0 && end <= 24) || end <= start) {
    ElMessage.error(translatePhrase("结束时间必须大于开始时间"));
    return;
  }
  if (checkOverlap(editPop.dayKey, start, end, editPop.segId)) {
    ElMessage.error(translatePhrase("时间段重叠，请调整时间"));
    return;
  }
  const seg = (segmentsByDay[editPop.dayKey] || []).find((s) => s.id === editPop.segId);
  if (!seg) return;
  seg.start = start;
  seg.end = end;
  closeEditPop();
};

const dragState = ref({
  isDragging: false,
  mode: null,
  dayKey: "",
  segId: null,
  startX: 0,
  startTime: 0,
  originalStart: 0,
  originalEnd: 0,
  moved: false,
  segElement: null,
  trackElement: null
});

const getTimeFromMouseX = (e, trackElement) => {
  const rect = trackElement.getBoundingClientRect();
  const x = e.clientX - rect.left;
  return snapHalfHour((x / rect.width) * 24);
};

const handleTrackMouseDown = (e, dayKey) => {
  if (e.button !== 0) return;
  const target = e.target;
  if (target.closest(".seg") || target.closest(".seg_delete") || target.closest(".seg_resize_left") || target.closest(".seg_resize_right")) {
    return;
  }
  selectedSegId.value = null;
  selectedDayKey.value = null;
  const trackElement = e.currentTarget;
  const startTime = getTimeFromMouseX(e, trackElement);
  if (!segmentsByDay[dayKey]) segmentsByDay[dayKey] = [];
  const newSeg = { id: generateSegId(), start: startTime, end: startTime };
  segmentsByDay[dayKey].push(newSeg);
  dragState.value = {
    isDragging: true,
    mode: "create",
    dayKey,
    segId: newSeg.id,
    startX: e.clientX,
    startTime,
    originalStart: startTime,
    originalEnd: startTime,
    moved: false,
    segElement: null,
    trackElement
  };
  document.addEventListener("mousemove", handleMouseMove);
  document.addEventListener("mouseup", handleMouseUp);
  e.preventDefault();
};

const handleSegmentMouseDown = (e, dayKey, seg) => {
  if (e.button !== 0) return;
  if (e.target.classList?.contains("seg_resize_left") || e.target.classList?.contains("seg_resize_right") || e.target.closest(".seg_delete")) {
    return;
  }
  selectedSegId.value = seg.id;
  selectedDayKey.value = null;
  const trackElement = e.currentTarget.closest(".row_track");
  dragState.value = {
    isDragging: true,
    mode: "move",
    dayKey,
    segId: seg.id,
    startX: e.clientX,
    startTime: getTimeFromMouseX(e, trackElement),
    originalStart: seg.start,
    originalEnd: seg.end,
    moved: false,
    segElement: e.currentTarget,
    trackElement
  };
  document.addEventListener("mousemove", handleMouseMove);
  document.addEventListener("mouseup", handleMouseUp);
  e.preventDefault();
  e.stopPropagation();
};

const handleResizeStart = (e, dayKey, seg, side) => {
  if (e.button !== 0) return;
  const trackElement = e.currentTarget.closest(".row_track");
  dragState.value = {
    isDragging: true,
    mode: side === "left" ? "resize-left" : "resize-right",
    dayKey,
    segId: seg.id,
    startX: e.clientX,
    startTime: getTimeFromMouseX(e, trackElement),
    originalStart: seg.start,
    originalEnd: seg.end,
    moved: false,
    segElement: null,
    trackElement
  };
  document.addEventListener("mousemove", handleMouseMove);
  document.addEventListener("mouseup", handleMouseUp);
  e.preventDefault();
  e.stopPropagation();
};

const handleMouseMove = (e) => {
  if (!dragState.value.isDragging || !dragState.value.trackElement) return;
  const currentTime = getTimeFromMouseX(e, dragState.value.trackElement);
  if (!dragState.value.moved) {
    if (Math.abs(e.clientX - dragState.value.startX) > 3 || Math.abs(currentTime - dragState.value.startTime) > 0.02) {
      dragState.value.moved = true;
    }
  }
  const seg = (segmentsByDay[dragState.value.dayKey] || []).find((s) => s.id === dragState.value.segId);
  if (!seg) return;

  if (dragState.value.mode === "create") {
    seg.start = Math.min(dragState.value.startTime, currentTime);
    seg.end = Math.max(dragState.value.startTime, currentTime);
  } else if (dragState.value.mode === "move") {
    const duration = dragState.value.originalEnd - dragState.value.originalStart;
    const offset = currentTime - dragState.value.startTime;
    const newStart = snapHalfHour(Math.max(0, Math.min(24 - duration, dragState.value.originalStart + offset)));
    const newEnd = snapHalfHour(newStart + duration);
    if (!checkOverlap(dragState.value.dayKey, newStart, newEnd, seg.id)) {
      seg.start = newStart;
      seg.end = newEnd;
    }
  } else if (dragState.value.mode === "resize-left") {
    const newStart = snapHalfHour(Math.max(0, Math.min(currentTime, dragState.value.originalEnd - 0.5)));
    if (newStart < seg.end && !checkOverlap(dragState.value.dayKey, newStart, seg.end, seg.id)) {
      seg.start = newStart;
    }
  } else if (dragState.value.mode === "resize-right") {
    const newEnd = snapHalfHour(Math.min(24, Math.max(currentTime, dragState.value.originalStart + 0.5)));
    if (newEnd > seg.start && !checkOverlap(dragState.value.dayKey, seg.start, newEnd, seg.id)) {
      seg.end = newEnd;
    }
  }
};

const handleMouseUp = () => {
  if (dragState.value.isDragging) {
    if (dragState.value.mode === "move" && dragState.value.segId && !dragState.value.moved) {
      const seg = (segmentsByDay[dragState.value.dayKey] || []).find((s) => s.id === dragState.value.segId);
      if (seg) {
        seg.start = dragState.value.originalStart;
        seg.end = dragState.value.originalEnd;
      }
      openEditPop(dragState.value.dayKey, dragState.value.segId, dragState.value.segElement);
    }
    if (dragState.value.mode === "create" && dragState.value.segId) {
      const segments = segmentsByDay[dragState.value.dayKey] || [];
      const seg = segments.find((s) => s.id === dragState.value.segId);
      if (seg && seg.end - seg.start < 0.5) {
        const index = segments.indexOf(seg);
        if (index > -1) segments.splice(index, 1);
      } else if (seg && checkOverlap(dragState.value.dayKey, seg.start, seg.end, seg.id)) {
        const index = segments.indexOf(seg);
        if (index > -1) segments.splice(index, 1);
        ElMessage.error(translatePhrase("时间段重叠，请重新选择"));
      }
    }
    dragState.value = {
      isDragging: false,
      mode: null,
      dayKey: "",
      segId: null,
      startX: 0,
      startTime: 0,
      originalStart: 0,
      originalEnd: 0,
      moved: false,
      segElement: null,
      trackElement: null
    };
  }
  document.removeEventListener("mousemove", handleMouseMove);
  document.removeEventListener("mouseup", handleMouseUp);
};

const reset = () => {
  isSyncing.value = true;
  clearSegments();
  intervalForm.interval = 1;
  selectedSegId.value = null;
  selectedDayKey.value = null;
  emit("update:modelValue", []);
  nextTick(() => {
    isSyncing.value = false;
  });
};

onBeforeUnmount(() => {
  document.removeEventListener("mousemove", handleMouseMove);
  document.removeEventListener("mouseup", handleMouseUp);
});

defineExpose({ toPlanItemList, fromPlanItemList, reset });
</script>

<style lang="scss" scoped>
.record-plan-timeline {
  width: 100%;
}

.interval_form {
  width: 280px;
  margin-bottom: 8px;

  :deep(.el-form-item__label) {
    font-size: 14px;
    color: #333;
    padding-bottom: 8px;
  }
}

.interval_input {
  width: 100%;

  :deep(.el-input__wrapper) {
    height: 40px;
    border-radius: 6px;
  }
}

.timeline_wrap {
  margin-top: 4px;
  background: #f3f4f6;
  border-radius: 8px;
  padding: 10px 14px 16px;

  &.is-month {
    max-height: 360px;
    overflow-y: auto;
  }
}

.timeline_head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.head_left {
  width: 70px;
  flex-shrink: 0;
}

.head_right {
  flex: 1;
}

.ticks {
  position: relative;
  width: 100%;
  height: 24px;
}

.tick {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;

  .tick_num {
    font-size: 14px;
    color: #333;
  }

  .tick_line {
    width: 1px;
    height: 8px;
    background: #d1d5db;
  }
}

.timeline_row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}

.row_label {
  width: 70px;
  flex-shrink: 0;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  user-select: none;

  &.selected {
    color: var(--el-color-primary);
  }
}

.row_track {
  flex: 1;
  position: relative;
  height: 34px;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
  cursor: crosshair;
  user-select: none;
}

.track_bg {
  position: absolute;
  inset: 0;
  background: #fff;
  pointer-events: none;
}

.seg {
  position: absolute;
  top: 0;
  height: 100%;
  cursor: move;
  user-select: none;
  overflow: hidden;

  &:hover {
    .seg_resize_left,
    .seg_resize_right,
    .seg_delete {
      opacity: 1;
      pointer-events: auto;
    }
  }

  &.selected {
    outline: 1px solid rgba(255, 255, 255, 0.8);
  }
}

.seg_delete {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 20;
  pointer-events: none;

  &:hover {
    background: rgba(255, 77, 79, 0.9);
  }

  .el-icon {
    font-size: 12px;
    color: #fff;
  }
}

.seg_resize_left,
.seg_resize_right {
  position: absolute;
  top: 0;
  width: 4px;
  height: 100%;
  background: rgba(255, 255, 255, 0.8);
  cursor: ew-resize;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 10;
}

.seg_resize_left {
  left: 0;
}

.seg_resize_right {
  right: 0;
}

.edit_pop_mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.edit_pop {
  position: fixed;
  width: 320px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
  padding: 18px 18px 0;
  z-index: 3001;
}

.edit_pop_close {
  position: absolute;
  right: 14px;
  top: 8px;
  font-size: 18px;
  color: #999;
  cursor: pointer;
  line-height: 1;
}

.edit_time_row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 0 16px;
}

.edit_time_picker {
  width: 130px;

  :deep(.el-input__wrapper) {
    height: 38px;
    border-radius: 6px;
  }
}

.edit_time_dash {
  color: #6b7280;
  font-size: 14px;
}

.edit_actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 -18px;
}

.edit_delete,
.edit_save {
  width: 50%;
  text-align: center;
  padding: 14px 0;
  cursor: pointer;
  font-size: 14px;
}

.edit_delete {
  color: #ff4d4f;
}

.edit_save {
  color: var(--el-color-primary);
  border-left: 1px solid #f3f4f6;
}
</style>
