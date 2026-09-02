<template>
  <div class="critical-analysis">
    <h3 class="panel-title">批判性分析</h3>
    <div class="analysis-grid">
      <div v-if="criticalAnalysis.strengths?.length" class="analysis-column strengths">
        <div class="column-header">
          <span>优点</span>
        </div>
        <ul>
          <li v-for="(item, i) in criticalAnalysis.strengths" :key="i">{{ item }}</li>
        </ul>
      </div>
      <div v-if="criticalAnalysis.limitations?.length" class="analysis-column limitations">
        <div class="column-header">
          <span>局限</span>
        </div>
        <ul>
          <li v-for="(item, i) in criticalAnalysis.limitations" :key="i">{{ item }}</li>
        </ul>
      </div>
      <div v-if="criticalAnalysis.alternativePerspectives?.length" class="analysis-column alternatives">
        <div class="column-header">
          <span>其他视角</span>
        </div>
        <ul>
          <li v-for="(item, i) in criticalAnalysis.alternativePerspectives" :key="i">{{ item }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  criticalAnalysis: { type: Object, required: true }
})
</script>

<style scoped>
.critical-analysis {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  padding: 16px;
}
.panel-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-subtle);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 14px;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.analysis-column {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--hairline);
}
.analysis-column.strengths {
  background: rgba(39, 166, 68, 0.04);
  border-color: rgba(39, 166, 68, 0.2);
}
.analysis-column.limitations {
  background: rgba(237, 137, 54, 0.04);
  border-color: rgba(237, 137, 54, 0.2);
}
.analysis-column.alternatives {
  background: rgba(94, 106, 210, 0.04);
  border-color: rgba(94, 106, 210, 0.2);
}
.column-header {
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 10px;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}
li {
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-muted);
  padding-left: 14px;
  position: relative;
}
li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: var(--ink-subtle);
}
.strengths li::before { color: rgba(39, 166, 68, 0.6); }
.limitations li::before { color: rgba(237, 137, 54, 0.6); }
.alternatives li::before { color: rgba(94, 106, 210, 0.6); }
</style>
