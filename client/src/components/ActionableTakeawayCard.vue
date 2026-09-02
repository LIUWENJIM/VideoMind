<template>
  <div class="takeaway-card">
    <div class="takeaway-header">
      <span class="takeaway-number">{{ index + 1 }}</span>
      <span class="takeaway-label">{{ takeaway.takeaway?.slice(0, 50) }}{{ takeaway.takeaway?.length > 50 ? '...' : '' }}</span>
    </div>
    <p class="takeaway-text"><AnnotatedText :text="takeaway.takeaway" :terms="terms" /></p>

    <div v-if="takeaway.application" class="takeaway-section">
      <span class="section-label">如何应用</span>
      <p><AnnotatedText :text="takeaway.application" :terms="terms" /></p>
    </div>

    <div v-if="takeaway.nextSteps?.length" class="takeaway-section">
      <span class="section-label">下一步</span>
      <ul>
        <li v-for="(step, i) in takeaway.nextSteps" :key="i">{{ step }}</li>
      </ul>
    </div>

    <div v-if="takeaway.evidence?.length" class="takeaway-section">
      <span class="section-label">证据</span>
      <div class="evidence-list">
        <button
          v-for="(ev, i) in takeaway.evidence"
          :key="i"
          class="evidence-item"
          @click="$emit('play-evidence', ev.timestampMs)"
        >
          <span class="evidence-time">{{ formatTime(ev.timestampMs) }}</span>
          <span class="evidence-content">{{ ev.content }}</span>
          <svg class="evidence-play" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import AnnotatedText from './AnnotatedText.vue'

defineProps({
  takeaway: { type: Object, required: true },
  index: { type: Number, required: true },
  terms: { type: Array, default: () => [] }
})

defineEmits(['play-evidence'])

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
.takeaway-card {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  padding: 16px;
  transition: all var(--duration-normal) var(--ease-out);
}
.takeaway-card:hover {
  border-color: var(--hairline-strong);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.15);
}
.takeaway-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.takeaway-number {
  width: 22px; height: 22px;
  display: flex; align-items: center; justify-content: center;
  background: var(--surface-3);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-subtle);
  flex-shrink: 0;
}
.takeaway-label { font-size: 13px; font-weight: 500; color: var(--ink-muted); letter-spacing: -0.01em; }
.takeaway-text { color: var(--ink); font-size: 15px; line-height: 1.6; margin-bottom: 12px; }

.takeaway-section { margin-bottom: 12px; }
.takeaway-section:last-child { margin-bottom: 0; }
.section-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--primary);
  margin-bottom: 6px;
}
.takeaway-section p {
  font-size: 14px;
  color: var(--ink-muted);
  line-height: 1.6;
  margin: 0;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}
li {
  font-size: 13px;
  color: var(--ink-muted);
  line-height: 1.5;
  padding-left: 16px;
  position: relative;
}
li::before {
  content: '→';
  position: absolute;
  left: 0;
  color: var(--primary);
}

.evidence-list { display: grid; gap: 6px; }
.evidence-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  background: var(--surface-2);
  border: 1px solid var(--hairline);
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--duration-fast);
  text-align: left;
  color: var(--ink-muted);
  font-size: 13px;
}
.evidence-item:hover {
  border-color: var(--primary);
  color: var(--ink);
  transform: translateY(-1px);
}
.evidence-time { color: var(--primary); font-weight: 500; font-family: monospace; font-size: 12px; }
.evidence-content { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-play { color: var(--primary); font-size: 12px; opacity: 0.7; }
.evidence-item:hover .evidence-play { opacity: 1; }
</style>
