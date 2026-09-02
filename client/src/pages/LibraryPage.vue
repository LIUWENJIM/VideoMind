<template>
  <section class="library-page">
    <div class="library-header">
      <div class="library-title-row">
        <h2>视频资料库</h2>
        <div class="count-chip">{{ s.list.value.length }} 个视频</div>
      </div>
      <div class="library-controls">
        <label class="library-search">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          <input v-model="s.searchQuery.value" type="search" placeholder="搜索视频名称" />
        </label>
        <div class="sort-group">
          <button
            v-for="opt in sortOptions"
            :key="opt.value"
            :class="{ active: sortBy === opt.value }"
            @click="sortBy = opt.value"
          >{{ opt.label }}</button>
        </div>
      </div>
    </div>

    <div v-if="sortedList.length" class="card-grid">
      <div
        v-for="item in sortedList"
        :key="item.id"
        class="project-card"
        @click="s.openAgent(item)"
      >
        <button
          class="delete-btn"
          :disabled="s.deletingId.value === item.id"
          @click.stop="s.deleteItem(item)"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14H6L5 6"></path><path d="M8 6V4h8v2"></path>
          </svg>
        </button>

        <div class="card-preview">
          <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.filename" class="cover-image" loading="lazy" />
          <div v-else class="preview-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
          </div>
          <div class="card-status-badge" :class="cardStatusClass(item)">
            {{ cardStatusLabel(item) }}
          </div>
        </div>

        <div class="card-info">
          <div class="card-filename" :title="item.filename">{{ item.filename }}</div>
          <div class="card-meta">
            <span class="meta-time">{{ s.formatTime(item.uploadTime) }}</span>
            <span class="meta-mode" v-if="getAnalysisMode(item)">{{ getAnalysisMode(item) }}</span>
          </div>
        </div>

        <div class="card-actions">
          <button
            class="action-btn"
            :disabled="item.status !== 'COMPLETED'"
            @click.stop="s.transcribe(item.id)"
            title="提取文字"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
            <span>转写</span>
          </button>
          <button
            class="action-btn primary"
            :disabled="item.status !== 'COMPLETED'"
            @click.stop="s.openAgent(item)"
            title="AI 分析"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" ry="2"></rect><rect x="9" y="9" width="6" height="6"></rect></svg>
            <span>分析</span>
          </button>
          <button
            class="action-btn"
            :disabled="item.status !== 'COMPLETED'"
            @click.stop="s.downloadAudio(item)"
            title="下载音频"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
            <span>音频</span>
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="s.searchQuery.value" class="library-empty">
      <p>没有找到"{{ s.searchQuery.value }}"</p>
      <button type="button" @click="s.searchQuery.value = ''">清除搜索</button>
    </div>
    <div v-else class="library-empty">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
      </div>
      <p>还没有视频</p>
      <span>返回首页上传你的第一个视频</span>
      <button type="button" @click="$router.push('/')">去上传</button>
    </div>
  </section>
</template>

<script setup>
import { inject, computed, ref } from 'vue'
const s = inject('appState')

const sortBy = ref('recent')
const sortOptions = [
  { value: 'recent', label: '最近上传' },
  { value: 'name', label: '名称' },
  { value: 'status', label: '状态' }
]

const sortedList = computed(() => {
  const list = [...s.list.value]
  switch (sortBy.value) {
    case 'name': return list.sort((a, b) => (a.filename || '').localeCompare(b.filename || ''))
    case 'status': return list.sort((a, b) => (a.status || '').localeCompare(b.status || ''))
    default: return list.sort((a, b) => new Date(b.uploadTime) - new Date(a.uploadTime))
  }
})

function cardStatusClass(item) {
  const task = s.activeTaskOf(item.id)
  if (task) return 'processing'
  return s.mediaStatusClass(item.status)
}

function cardStatusLabel(item) {
  const task = s.activeTaskOf(item.id)
  if (task) return task.type === 'ai' ? '分析中' : '转写中'
  return s.mediaStatusLabel(item.status)
}

function getAnalysisMode(item) {
  return null
}
</script>

<style scoped>
.library-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
}

.library-header {
  margin-bottom: 24px;
}
.library-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.library-title-row h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--ink);
  letter-spacing: -0.3px;
}
.count-chip {
  background: var(--surface-2);
  padding: 2px 10px;
  border-radius: 9999px;
  font-size: 12px;
  color: var(--ink-muted);
}

.library-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.library-search {
  flex: 1;
  max-width: 320px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--hairline);
  background: var(--surface-1);
  border-radius: 8px;
  color: var(--ink-subtle);
  transition: border-color 0.15s;
}
.library-search:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.1);
}
.library-search input {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
}

.sort-group {
  display: flex;
  gap: 2px;
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 8px;
  padding: 2px;
}
.sort-group button {
  background: transparent;
  border: none;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: var(--ink-subtle);
  transition: all 0.15s;
}
.sort-group button:hover { color: var(--ink); }
.sort-group button.active {
  background: var(--surface-3);
  color: var(--ink);
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.project-card {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
  position: relative;
}
.project-card:hover {
  border-color: var(--hairline-strong);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px -8px rgba(0, 0, 0, 0.25);
}

.delete-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  background: var(--surface-2);
  border: 1px solid var(--hairline);
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-subtle);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
  z-index: 2;
}
.project-card:hover .delete-btn { opacity: 1; }
.delete-btn:hover:not(:disabled) { color: var(--semantic-error); border-color: var(--semantic-error); }
.delete-btn:disabled { cursor: progress; }

.card-preview {
  height: 120px;
  background: var(--surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border-bottom: 1px solid var(--hairline);
  overflow: hidden;
}
.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-icon { color: var(--ink-tertiary); }
.card-status-badge {
  position: absolute;
  bottom: 8px;
  left: 8px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.card-status-badge.completed { background: rgba(39, 166, 68, 0.15); color: var(--semantic-success); }
.card-status-badge.processing { background: rgba(94, 106, 210, 0.15); color: var(--primary); }
.card-status-badge.failed { background: rgba(229, 72, 77, 0.15); color: var(--semantic-error); }
.card-status-badge.unknown { background: var(--surface-3); color: var(--ink-subtle); }

.card-info {
  padding: 14px 16px 10px;
}
.card-filename {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.card-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--ink-subtle);
}
.meta-mode {
  color: var(--primary);
  font-weight: 500;
}

.card-actions {
  display: flex;
  gap: 1px;
  padding: 0 12px 12px;
}
.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 7px 0;
  background: var(--surface-2);
  border: 1px solid var(--hairline);
  border-radius: 6px;
  color: var(--ink-subtle);
  cursor: pointer;
  font-size: 11px;
  transition: all 0.15s;
}
.action-btn:hover:not(:disabled) {
  color: var(--primary);
  border-color: var(--primary);
}
.action-btn.primary {
  color: var(--primary);
  border-color: rgba(94, 106, 210, 0.3);
  background: rgba(94, 106, 210, 0.05);
}
.action-btn.primary:hover:not(:disabled) {
  background: var(--primary);
  color: var(--on-primary);
  border-color: var(--primary);
}
.action-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.library-empty {
  padding: 80px 20px;
  text-align: center;
  color: var(--ink-subtle);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.empty-icon { color: var(--ink-tertiary); margin-bottom: 8px; }
.library-empty p { font-size: 16px; color: var(--ink-muted); }
.library-empty span { font-size: 13px; }
.library-empty button {
  margin-top: 12px;
  background: var(--primary);
  color: var(--on-primary);
  border: none;
  padding: 8px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 720px) {
  .library-controls { flex-direction: column; align-items: stretch; }
  .library-search { max-width: none; }
  .card-grid { grid-template-columns: 1fr; }
}
</style>
