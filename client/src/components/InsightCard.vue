<template>
  <div class="insight-card" :class="{ active: isActive }" @click="$emit('select', insight, index)">
    <button class="insight-header" @click.stop="toggle">
      <div class="insight-title">
        <span class="insight-number">{{ index + 1 }}</span>
        <span class="insight-label">{{ insight.insight?.slice(0, 50) }}{{ insight.insight?.length > 50 ? '...' : '' }}</span>
      </div>
      <span class="expand-icon">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline :points="expanded ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"></polyline></svg>
      </span>
    </button>
    <transition name="slide">
      <div v-show="expanded" class="insight-body">
        <p class="insight-text"><AnnotatedText :text="insight.insight" :terms="terms" /></p>

        <div v-if="insight.reasoning" class="insight-section">
          <span class="section-label">为什么重要</span>
          <p><AnnotatedText :text="insight.reasoning" :terms="terms" /></p>
        </div>

        <div v-if="insight.evidence?.length" class="insight-section">
          <span class="section-label">视频证据</span>
          <div class="evidence-list">
            <button
              v-for="(ev, i) in insight.evidence"
              :key="i"
              class="evidence-item"
              @click.stop="$emit('play-evidence', ev.timestampMs)"
            >
              <span class="evidence-time">{{ formatTime(ev.timestampMs) }}</span>
              <span class="evidence-source">{{ ev.source }}</span>
              <span class="evidence-content">{{ ev.content }}</span>
              <svg class="evidence-play" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
            </button>
          </div>
        </div>

        <div v-if="insight.implications" class="insight-section">
          <span class="section-label">启示</span>
          <p><AnnotatedText :text="insight.implications" :terms="terms" /></p>
        </div>

        <div v-if="insight.relatedConcepts?.length" class="insight-section">
          <span class="section-label">相关概念</span>
          <div class="concept-tags">
            <span v-for="concept in insight.relatedConcepts" :key="concept" class="concept-tag">
              {{ concept }}
            </span>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AnnotatedText from './AnnotatedText.vue'

const props = defineProps({
  insight: { type: Object, required: true },
  index: { type: Number, required: true },
  isActive: { type: Boolean, default: false },
  terms: { type: Array, default: () => [] }
})

defineEmits(['play-evidence', 'select'])

const expanded = ref(true)
const toggle = () => { expanded.value = !expanded.value }

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
.insight-card {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  transition: all var(--duration-normal) var(--ease-out);
  overflow: hidden;
}
.insight-card:hover {
  border-color: var(--hairline-strong);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.2);
}
.insight-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.15), 0 4px 16px -4px rgba(94, 106, 210, 0.2);
}
.insight-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--ink);
  transition: background var(--duration-fast);
}
.insight-header:hover { background: var(--surface-2); }
.insight-title { display: flex; align-items: center; gap: 10px; }
.insight-number {
  width: 22px; height: 22px;
  display: flex; align-items: center; justify-content: center;
  background: var(--surface-3);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-subtle);
  flex-shrink: 0;
}
.insight-label { font-size: 13px; font-weight: 500; color: var(--ink-muted); letter-spacing: -0.01em; }
.expand-icon { color: var(--ink-tertiary); display: flex; align-items: center; }

.insight-body { padding: 0 16px 16px; }
.insight-text { color: var(--ink); font-size: 15px; line-height: 1.6; margin-bottom: 12px; }

.insight-section { margin-bottom: 12px; }
.insight-section:last-child { margin-bottom: 0; }
.section-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--primary);
  margin-bottom: 6px;
}
.insight-section p {
  font-size: 14px;
  color: var(--ink-muted);
  line-height: 1.6;
  margin: 0;
}

.evidence-list { display: grid; gap: 6px; }
.evidence-item {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
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
.evidence-source {
  font-size: 11px;
  padding: 1px 6px;
  background: rgba(94, 106, 210, 0.1);
  color: var(--primary);
  border-radius: 4px;
  white-space: nowrap;
}
.evidence-content { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-play { color: var(--primary); font-size: 12px; opacity: 0.7; }
.evidence-item:hover .evidence-play { opacity: 1; }

.concept-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.concept-tag {
  font-size: 12px;
  padding: 3px 10px;
  background: var(--surface-2);
  border: 1px solid var(--hairline);
  border-radius: 9999px;
  color: var(--ink-subtle);
}

.slide-enter-active, .slide-leave-active {
  transition: all var(--duration-normal) var(--ease-out);
  max-height: 800px;
  overflow: hidden;
}
.slide-enter-from, .slide-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
