<template>
  <span class="term-annotation" @click="toggle" @mouseenter="showTooltip = true" @mouseleave="showTooltip = false">
    <span class="term-text">{{ term }}</span>
    <transition name="tooltip-fade">
      <div v-if="showTooltip || forceOpen" class="term-tooltip" @click.stop>
        <div class="tooltip-term">{{ term }}</div>
        <div class="tooltip-definition">{{ definition }}</div>
        <div v-if="context" class="tooltip-context">{{ context }}</div>
        <button v-if="forceOpen" class="tooltip-close" @click="forceOpen = false">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
      </div>
    </transition>
  </span>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  term: { type: String, required: true },
  definition: { type: String, required: true },
  context: { type: String, default: '' }
})

const showTooltip = ref(false)
const forceOpen = ref(false)

function toggle() {
  forceOpen.value = !forceOpen.value
}
</script>

<style scoped>
.term-annotation {
  position: relative;
  display: inline;
  cursor: pointer;
}
.term-text {
  border-bottom: 1px dashed var(--primary);
  color: var(--primary);
  font-weight: 500;
  transition: all 0.15s;
}
.term-annotation:hover .term-text {
  background: rgba(94, 106, 210, 0.08);
}

.term-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  width: 260px;
  background: var(--surface-2);
  border: 1px solid var(--hairline-strong);
  border-radius: 8px;
  padding: 12px 14px;
  z-index: 100;
  box-shadow: 0 8px 24px -8px rgba(0, 0, 0, 0.3);
  pointer-events: auto;
}
.tooltip-term {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 6px;
}
.tooltip-definition {
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.5;
  margin-bottom: 4px;
}
.tooltip-context {
  font-size: 11px;
  color: var(--ink-subtle);
  line-height: 1.4;
  padding-top: 6px;
  border-top: 1px solid var(--hairline);
}
.tooltip-close {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--ink-subtle);
  cursor: pointer;
  border-radius: 4px;
}
.tooltip-close:hover { color: var(--ink); background: var(--surface-3); }

/* Tooltip positioning for items near the top */
.term-tooltip {
  --tooltip-y: calc(100% + 8px);
}

.tooltip-fade-enter-active, .tooltip-fade-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}
.tooltip-fade-enter-from, .tooltip-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(4px);
}
</style>
