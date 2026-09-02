<template>
  <div class="evidence-timeline" ref="timelineRef" @click="onTimelineClick">
    <div class="timeline-track">
      <!-- 时间刻度 -->
      <div class="timeline-scale">
        <span v-for="tick in ticks" :key="tick.ms" class="timeline-tick" :style="{ left: tick.percent + '%' }">
          {{ tick.label }}
        </span>
      </div>

      <!-- 进度条背景 -->
      <div class="timeline-bar"></div>

      <!-- 播放位置指示器 -->
      <div
        v-if="videoDuration > 0"
        class="playhead"
        :style="{ left: playheadPercent + '%' }"
      ></div>

      <!-- 证据标记点 -->
      <button
        v-for="(marker, i) in markers"
        :key="i"
        class="timeline-marker"
        :class="{ active: activeIndex === i }"
        :style="{ left: marker.percent + '%' }"
        :title="marker.tooltip"
        @click.stop="$emit('seek', marker.timestampMs); $emit('select', i)"
        @mouseenter="hoveredMarker = i"
        @mouseleave="hoveredMarker = null"
      >
        <div class="marker-dot"></div>
        <div v-if="hoveredMarker === i" class="marker-tooltip">
          <span class="tooltip-time">{{ formatTime(marker.timestampMs) }}</span>
          <span class="tooltip-content">{{ marker.tooltip }}</span>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  insights: { type: Array, default: () => [] },
  takeaways: { type: Array, default: () => [] },
  videoDuration: { type: Number, default: 0 },
  currentTime: { type: Number, default: 0 },
  activeIndex: { type: Number, default: null }
})

defineEmits(['seek', 'select'])

const timelineRef = ref(null)
const hoveredMarker = ref(null)

// 收集所有证据时间戳作为标记点
const markers = computed(() => {
  const all = []
  for (const insight of props.insights) {
    if (!insight.evidence) continue
    for (const ev of insight.evidence) {
      all.push({
        timestampMs: ev.timestampMs,
        percent: props.videoDuration > 0
          ? (ev.timestampMs / (props.videoDuration * 1000)) * 100
          : 0,
        tooltip: `${ev.source}: ${ev.content}`.slice(0, 80)
      })
    }
  }
  for (const takeaway of props.takeaways) {
    if (!takeaway.evidence) continue
    for (const ev of takeaway.evidence) {
      all.push({
        timestampMs: ev.timestampMs,
        percent: props.videoDuration > 0
          ? (ev.timestampMs / (props.videoDuration * 1000)) * 100
          : 0,
        tooltip: `${ev.source}: ${ev.content}`.slice(0, 80)
      })
    }
  }
  // 去重并按时间排序
  const seen = new Set()
  return all
    .filter(m => {
      if (seen.has(m.timestampMs)) return false
      seen.add(m.timestampMs)
      return true
    })
    .sort((a, b) => a.timestampMs - b.timestampMs)
})

// 时间刻度（5-8 个刻度）
const ticks = computed(() => {
  if (props.videoDuration <= 0) return []
  const duration = props.videoDuration
  const count = Math.min(8, Math.max(3, Math.floor(duration / 60)))
  const interval = duration / count
  const result = []
  for (let i = 0; i <= count; i++) {
    const ms = Math.round(i * interval * 1000)
    result.push({
      ms,
      percent: (i / count) * 100,
      label: formatTime(ms)
    })
  }
  return result
})

const playheadPercent = computed(() => {
  if (props.videoDuration <= 0) return 0
  return Math.min(100, (props.currentTime / props.videoDuration) * 100)
})

function onTimelineClick(event) {
  if (props.videoDuration <= 0 || !timelineRef.value) return
  const rect = timelineRef.value.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percent = x / rect.width
  const timestampMs = Math.round(percent * props.videoDuration * 1000)
  // 找到最近的标记点（±15s 范围内）
  const nearby = markers.value.find(m => Math.abs(m.timestampMs - timestampMs) < 15000)
  if (nearby) {
    // 点击在标记点附近，跳转标记点
    // (handled by the marker button's click handler via stopPropagation)
  }
}

function formatTime(ms) {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  const time = `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${time}` : time
}
</script>

<style scoped>
.evidence-timeline {
  width: 100%;
  padding: 16px 20px;
  background: var(--surface-1);
  border-top: 1px solid var(--hairline);
}
.timeline-track {
  position: relative;
  height: 40px;
  cursor: pointer;
}

.timeline-scale {
  position: absolute;
  top: -16px;
  left: 0;
  right: 0;
  height: 16px;
}
.timeline-tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: 10px;
  color: var(--ink-tertiary);
  font-family: monospace;
}

.timeline-bar {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--surface-3);
  border-radius: 2px;
  transform: translateY(-50%);
}

.playhead {
  position: absolute;
  top: 50%;
  width: 2px;
  height: 16px;
  background: var(--primary);
  transform: translate(-50%, -50%);
  border-radius: 1px;
  transition: left 0.3s linear;
  z-index: 2;
}

.timeline-marker {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  z-index: 3;
}
.marker-dot {
  width: 12px;
  height: 12px;
  background: var(--primary);
  border-radius: 50%;
  border: 2px solid var(--surface-1);
  transition: all var(--duration-fast) var(--ease-spring);
  box-shadow: 0 0 0 0 rgba(94, 106, 210, 0);
}
.timeline-marker:hover .marker-dot {
  transform: scale(1.5);
  box-shadow: 0 0 0 4px rgba(94, 106, 210, 0.3);
}
.timeline-marker.active .marker-dot {
  background: var(--primary-hover);
  box-shadow: 0 0 0 4px rgba(94, 106, 210, 0.4);
  transform: scale(1.4);
}

.marker-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--surface-2);
  border: 1px solid var(--hairline-strong);
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 180px;
  max-width: 280px;
  z-index: 10;
  box-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}
.tooltip-time {
  display: block;
  font-size: 11px;
  font-family: monospace;
  color: var(--primary);
  margin-bottom: 4px;
}
.tooltip-content {
  display: block;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.4;
}
</style>
