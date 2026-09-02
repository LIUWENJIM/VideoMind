<template>
  <div class="workspace-container">
    <!-- 顶部工具栏 -->
    <div class="workspace-toolbar">
      <div class="toolbar-left">
        <button class="back-btn" @click="$emit('back-to-sidebar')" title="返回">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
        </button>
        <span class="workspace-title">{{ sidebar.title }}</span>
      </div>
      <div class="toolbar-right">
        <div class="result-actions">
          <button type="button" @click="$emit('copy-result')" title="复制结果">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
          </button>
          <button type="button" @click="$emit('download-result')" title="导出 Markdown">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 分屏主体 -->
    <div class="workspace-main">
      <!-- 左侧：视频播放器 -->
      <div class="workspace-video" :style="{ width: splitPosition + '%' }">
        <video
          v-if="sidebar.playbackUrl"
          ref="videoEl"
          :src="sidebar.playbackUrl"
          controls
          playsinline
          preload="metadata"
          @timeupdate="onTimeUpdate"
          @loadedmetadata="onVideoLoaded"
          @error="$emit('playback-error')"
        ></video>
        <div v-else-if="sidebar.playbackLoading" class="video-loading">
          <div class="quantum-loader small"></div>
          <span>正在载入视频</span>
        </div>
        <div v-else class="video-placeholder">
          <span>视频不可用</span>
        </div>

        <!-- 时间轴 -->
        <EvidenceTimeline
          v-if="hasEvidenceMarkers && videoDuration > 0"
          :insights="sidebar.deepResult?.keyInsights || []"
          :takeaways="sidebar.deepResult?.actionableTakeaways || []"
          :video-duration="videoDuration"
          :current-time="sidebar.videoCurrentTime"
          :active-index="sidebar.activeInsightIndex"
          @seek="onSeek"
          @select="onMarkerSelect"
        />
      </div>

      <!-- 可拖拽分隔条 -->
      <div
        class="splitter"
        @mousedown="onSplitterMouseDown"
        @dblclick="resetSplit"
      >
        <div class="splitter-handle"><span></span><span></span></div>
      </div>

      <!-- 右侧：导航 + 分析面板 -->
      <div class="workspace-right" :style="{ width: (100 - splitPosition) + '%' }">
        <!-- 左侧导航 -->
        <nav class="workspace-toc" :class="{ collapsed: tocCollapsed }" v-if="tocItems.length > 0">
          <div class="toc-header">
            <span v-if="!tocCollapsed">目录</span>
            <button class="toc-toggle" @click="tocCollapsed = !tocCollapsed" :title="tocCollapsed ? '展开目录' : '收起目录'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline :points="tocCollapsed ? '9 18 15 12 9 6' : '15 18 9 12 15 6'"></polyline>
              </svg>
            </button>
          </div>
          <ul class="toc-list" v-show="!tocCollapsed">
            <li
              v-for="item in tocItems"
              :key="item.id"
              :class="{ active: activeSection === item.id, ['level-' + (item.level || 2)]: true }"
              class="toc-item"
            >
              <a
                :href="'#' + item.id"
                @click.prevent="scrollToSection(item.id)"
                :title="item.title"
              >
                <span class="toc-number" v-if="item.number">{{ item.number }}</span>
                <span class="toc-text">{{ item.title }}</span>
              </a>
            </li>
          </ul>
        </nav>

        <!-- 主内容区 -->
        <div class="workspace-analysis" ref="analysisPanel">
          <div class="analysis-scroll" ref="scrollContainer" @scroll="onScroll">
            <!-- DEEP 模式：结构化展示 -->
            <template v-if="sidebar.deepResult">
              <div class="analysis-content">
                <!-- 执行摘要 -->
                <section v-if="sidebar.deepResult.executiveSummary" class="analysis-section" id="section-summary" data-section="summary">
                  <h3 class="section-title">核心洞察</h3>
                  <p class="executive-summary"><AnnotatedText :text="sidebar.deepResult.executiveSummary" :terms="keyTerms" /></p>
                </section>

                <!-- 背景 -->
                <section v-if="sidebar.deepResult.context" class="analysis-section" id="section-context" data-section="context">
                  <h3 class="section-title">背景</h3>
                  <p class="context-text"><AnnotatedText :text="sidebar.deepResult.context" :terms="keyTerms" /></p>
                </section>

                <!-- 洞察卡片 -->
                <section v-if="sidebar.deepResult.keyInsights?.length" class="analysis-section" id="section-insights" data-section="insights">
                  <h3 class="section-title">洞察</h3>
                  <div class="insights-list">
                    <InsightCard
                      v-for="(insight, i) in sidebar.deepResult.keyInsights"
                      :key="i"
                      :ref="el => setInsightRef(i, el)"
                      :insight="insight"
                      :index="i"
                      :is-active="sidebar.activeInsightIndex === i"
                      :terms="keyTerms"
                      @play-evidence="onSeek"
                      @select="(ins, idx) => onInsightSelect(idx)"
                    />
                  </div>
                </section>

                <!-- 批判性分析 -->
                <section
                  v-if="sidebar.deepResult.criticalAnalysis && hasCriticalAnalysis"
                  class="analysis-section"
                  id="section-critical"
                  data-section="critical"
                >
                  <CriticalAnalysisPanel :critical-analysis="sidebar.deepResult.criticalAnalysis" />
                </section>

                <!-- 可操作收获 -->
                <section v-if="sidebar.deepResult.actionableTakeaways?.length" class="analysis-section" id="section-takeaways" data-section="takeaways">
                  <h3 class="section-title">收获</h3>
                  <div class="takeaways-list">
                    <ActionableTakeawayCard
                      v-for="(takeaway, i) in sidebar.deepResult.actionableTakeaways"
                      :key="i"
                      :takeaway="takeaway"
                      :index="i"
                      :terms="keyTerms"
                      @play-evidence="onSeek"
                    />
                  </div>
                </section>

                <!-- 术语表 -->
                <section v-if="keyTerms.length" class="analysis-section" id="section-terms" data-section="terms">
                  <h3 class="section-title">术语表</h3>
                  <div class="terms-glossary">
                    <div v-for="term in keyTerms" :key="term.term" class="glossary-item">
                      <dt class="glossary-term">{{ term.term }}</dt>
                      <dd class="glossary-def">{{ term.definition }}<span v-if="term.context" class="glossary-context">{{ term.context }}</span></dd>
                    </div>
                  </div>
                </section>
              </div>
            </template>

            <!-- 非 DEEP 模式：Markdown 渲染 -->
            <template v-else-if="renderedMarkdown">
              <div class="markdown-content" ref="markdownEl" v-html="renderedMarkdown" @click="$emit('seek-evidence', $event)"></div>
            </template>

            <!-- 无数据 -->
            <div v-else class="no-data">
              <p>暂无分析数据</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import InsightCard from './InsightCard.vue'
import CriticalAnalysisPanel from './CriticalAnalysisPanel.vue'
import ActionableTakeawayCard from './ActionableTakeawayCard.vue'
import EvidenceTimeline from './EvidenceTimeline.vue'
import AnnotatedText from './AnnotatedText.vue'

const props = defineProps({
  sidebar: { type: Object, required: true },
  renderedMarkdown: { type: String, default: '' }
})

const emit = defineEmits([
  'seek-video',
  'set-active-insight',
  'back-to-sidebar',
  'playback-error',
  'seek-evidence',
  'copy-result',
  'download-result'
])

const videoEl = ref(null)
const analysisPanel = ref(null)
const scrollContainer = ref(null)
const markdownEl = ref(null)
const videoDuration = ref(0)
const insightRefs = ref({})
const splitPosition = ref(50)
const isDragging = ref(false)
const activeSection = ref('')
const tocCollapsed = ref(false)

// 根据 deepResult 动态生成目录
const tocItems = computed(() => {
  const items = []
  const dr = props.sidebar.deepResult

  // DEEP 模式：从结构化数据生成目录
  if (dr) {
    if (dr.executiveSummary) items.push({ id: 'section-summary', title: '核心洞察', number: null })
    if (dr.context) items.push({ id: 'section-context', title: '背景', number: null })
    if (dr.keyInsights?.length) items.push({ id: 'section-insights', title: '洞察', number: String(dr.keyInsights.length) })
    if (dr.criticalAnalysis && hasCriticalAnalysis.value) items.push({ id: 'section-critical', title: '批判性分析', number: null })
    if (dr.actionableTakeaways?.length) items.push({ id: 'section-takeaways', title: '收获', number: String(dr.actionableTakeaways.length) })
    if (dr.keyTerms?.length) items.push({ id: 'section-terms', title: '术语表', number: String(dr.keyTerms.length) })
    return items
  }

  // 非 DEEP 模式：从 markdown 提取 h2/h3 标题
  const md = props.sidebar.content || ''
  if (!md) return items
  const headingRegex = /^(#{2,3})\s+(.+)$/gm
  let match
  let idx = 0
  while ((match = headingRegex.exec(md)) !== null) {
    const level = match[1].length
    const title = match[2].replace(/[*_`\[\]]/g, '').trim()
    if (title) {
      items.push({
        id: `md-heading-${idx}`,
        title,
        number: null,
        level
      })
      idx++
    }
  }
  return items
})

const hasCriticalAnalysis = computed(() => {
  const ca = props.sidebar.deepResult?.criticalAnalysis
  if (!ca) return false
  return (ca.strengths?.length || 0) + (ca.limitations?.length || 0) + (ca.alternativePerspectives?.length || 0) > 0
})

const hasEvidenceMarkers = computed(() => {
  const dr = props.sidebar.deepResult
  if (!dr) return false
  return dr.keyInsights?.some(i => i.evidence?.length) || dr.actionableTakeaways?.some(t => t.evidence?.length)
})

const keyTerms = computed(() => props.sidebar.deepResult?.keyTerms || [])

// 非 DEEP 模式：给 markdown 渲染后的 h2/h3 注入 ID，支持 TOC 跳转
watch(() => props.renderedMarkdown, async () => {
  await nextTick()
  if (!markdownEl.value) return
  const headings = markdownEl.value.querySelectorAll('h2, h3')
  headings.forEach((el, idx) => {
    el.id = `md-heading-${idx}`
    el.style.scrollMarginTop = '20px'
  })
}, { immediate: true })

function setInsightRef(index, el) {
  if (el) insightRefs.value[index] = el
}

// --- 目录滚动追踪 ---
function onScroll() {
  const container = scrollContainer.value
  if (!container) return
  const scrollTop = container.scrollTop + 100
  let current = ''

  // DEEP 模式：按 data-section 属性追踪
  const deepSections = container.querySelectorAll('[data-section]')
  if (deepSections.length) {
    for (const section of deepSections) {
      if (section.offsetTop <= scrollTop) current = section.id
    }
  }

  // 非 DEEP 模式：按 markdown heading ID 追踪
  if (!current) {
    const headings = container.querySelectorAll('[id^="md-heading-"]')
    for (const h of headings) {
      if (h.offsetTop <= scrollTop) current = h.id
    }
  }

  activeSection.value = current
}

function scrollToSection(sectionId) {
  const container = scrollContainer.value
  if (!container) return
  const el = container.querySelector(`#${sectionId}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

// --- 分隔条拖拽 ---
function onSplitterMouseDown(e) {
  e.preventDefault()
  isDragging.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onSplitterMouseMove)
  document.addEventListener('mouseup', onSplitterMouseUp)
}

function onSplitterMouseMove(e) {
  if (!isDragging.value) return
  const container = document.querySelector('.workspace-main')
  if (!container) return
  const rect = container.getBoundingClientRect()
  const x = e.clientX - rect.left
  splitPosition.value = Math.max(20, Math.min(80, (x / rect.width) * 100))
}

function onSplitterMouseUp() {
  isDragging.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', onSplitterMouseMove)
  document.removeEventListener('mouseup', onSplitterMouseUp)
}

function resetSplit() { splitPosition.value = 50 }

onUnmounted(() => {
  document.removeEventListener('mousemove', onSplitterMouseMove)
  document.removeEventListener('mouseup', onSplitterMouseUp)
})

// --- 视频交互 ---
function onVideoLoaded() {
  if (videoEl.value) videoDuration.value = videoEl.value.duration || 0
}

function onTimeUpdate(event) {
  const time = event.target.currentTime
  props.sidebar.videoCurrentTime = time
  const insights = props.sidebar.deepResult?.keyInsights || []
  let activeIndex = null
  for (let i = 0; i < insights.length; i++) {
    for (const ev of (insights[i].evidence || [])) {
      if (Math.abs(ev.timestampMs / 1000 - time) < 15) { activeIndex = i; break }
    }
    if (activeIndex !== null) break
  }
  if (activeIndex !== props.sidebar.activeInsightIndex) {
    emit('set-active-insight', activeIndex)
    if (activeIndex !== null) scrollToInsightCard(activeIndex)
  }
}

function onSeek(timestampMs) { emit('seek-video', timestampMs / 1000) }

function onInsightSelect(index) {
  emit('set-active-insight', index)
  const insight = props.sidebar.deepResult?.keyInsights?.[index]
  if (insight?.evidence?.length) emit('seek-video', insight.evidence[0].timestampMs / 1000)
}

function onMarkerSelect(index) {
  emit('set-active-insight', index)
  scrollToInsightCard(index)
}

async function scrollToInsightCard(index) {
  await nextTick()
  const cardRef = insightRefs.value[index]
  if (cardRef?.$el) cardRef.$el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}
</script>

<style scoped>
.workspace-container {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  flex-direction: column;
  background: var(--canvas);
  z-index: 900;
}

/* 工具栏 */
.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: var(--surface-1);
  border-bottom: 1px solid var(--hairline);
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.back-btn {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  background: transparent; border: 1px solid var(--hairline);
  border-radius: 6px; cursor: pointer; color: var(--ink-subtle);
  transition: all 0.15s;
}
.back-btn:hover { color: var(--ink); border-color: var(--hairline-strong); background: var(--surface-2); }
.workspace-title {
  font-size: 14px; font-weight: 500; color: var(--ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.toolbar-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.result-actions { display: flex; gap: 4px; }
.result-actions button {
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  background: transparent; border: 1px solid var(--hairline);
  border-radius: 6px; cursor: pointer; color: var(--ink-subtle);
  transition: all 0.15s;
}
.result-actions button:hover { color: var(--ink); border-color: var(--hairline-strong); background: var(--surface-2); }

/* 分屏主体 */
.workspace-main { display: flex; flex: 1; min-height: 0; }

/* 视频面板 */
.workspace-video {
  display: flex; flex-direction: column;
  background: #000; min-width: 0; overflow: hidden;
}
.workspace-video video { flex: 1; width: 100%; object-fit: contain; min-height: 0; }
.video-loading {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  color: rgba(255,255,255,0.5); font-size: 13px;
}
.video-placeholder {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.3); font-size: 13px;
}

/* 分隔条 */
.splitter {
  width: 6px; background: var(--hairline); cursor: col-resize;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: background 0.15s; z-index: 10;
}
.splitter:hover, .splitter:active { background: var(--primary); }
.splitter-handle { display: flex; flex-direction: column; gap: 3px; }
.splitter-handle span {
  display: block; width: 2px; height: 2px;
  background: var(--ink-tertiary); border-radius: 50%;
  transition: background 0.15s;
}
.splitter:hover .splitter-handle span { background: var(--on-primary); }

/* 右侧容器（导航+内容） */
.workspace-right {
  display: flex; min-width: 0; background: var(--canvas);
}

/* 左侧导航 TOC */
.workspace-toc {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid var(--hairline);
  background: var(--surface-1);
  overflow-y: auto;
  padding: 16px 0;
  transition: width 0.2s var(--ease-out);
}
.workspace-toc.collapsed {
  width: 36px;
  padding: 12px 0;
}
.toc-header {
  font-size: 10px;
  font-weight: 600;
  color: var(--ink-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  padding: 0 12px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.toc-toggle {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  background: transparent; border: none; border-radius: 4px;
  cursor: pointer; color: var(--ink-subtle);
  transition: all 0.15s;
}
.toc-toggle:hover { color: var(--ink); background: var(--surface-2); }
.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.toc-item {
  margin: 0;
}
.toc-item a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  color: var(--ink-subtle);
  text-decoration: none;
  font-size: 12px;
  line-height: 1.4;
  transition: all 0.15s;
  border-left: 2px solid transparent;
}
.toc-item.level-3 a { padding-left: 28px; font-size: 11px; }
.toc-item a:hover {
  color: var(--ink);
  background: var(--surface-2);
}
.toc-item.active a {
  color: var(--primary);
  border-left-color: var(--primary);
  background: rgba(94, 106, 210, 0.05);
}
.toc-number {
  font-size: 10px;
  font-weight: 600;
  color: var(--ink-tertiary);
  background: var(--surface-3);
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}
.toc-item.active .toc-number {
  color: var(--primary);
  background: rgba(94, 106, 210, 0.1);
}
.toc-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 主内容区 */
.workspace-analysis {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.analysis-scroll {
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.analysis-content {
  padding: 24px 28px;
  display: grid;
  gap: 28px;
  align-content: start;
  max-width: 680px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-subtle);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 12px;
  scroll-margin-top: 20px;
}
.executive-summary {
  font-size: 15px; color: var(--ink); line-height: 1.7;
  padding: 16px 20px;
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 8px;
}
.context-text { font-size: 14px; color: var(--ink-muted); line-height: 1.7; }

.insights-list, .takeaways-list { display: grid; gap: 8px; }

/* 术语表 */
.terms-glossary {
  display: grid;
  gap: 2px;
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 8px;
  overflow: hidden;
}
.glossary-item {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--hairline);
}
.glossary-item:last-child { border-bottom: none; }
.glossary-term {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
}
.glossary-def {
  font-size: 13px;
  color: var(--ink-muted);
  line-height: 1.5;
  margin: 0;
}
.glossary-context {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-subtle);
  font-style: italic;
}

/* Markdown 渲染 */
.markdown-content {
  padding: 24px 28px;
  max-width: 680px;
  line-height: 1.7;
  color: var(--ink);
  font-size: 15px;
}
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  color: var(--ink); margin-top: 1.5em; margin-bottom: 0.5em; font-weight: 600;
}
.markdown-content :deep(h2) { font-size: 18px; border-bottom: 1px solid var(--hairline); padding-bottom: 8px; }
.markdown-content :deep(h3) { font-size: 16px; }
.markdown-content :deep(ul) { padding-left: 20px; }
.markdown-content :deep(li) { margin-bottom: 8px; color: var(--ink-muted); }
.markdown-content :deep(strong) { color: var(--ink); font-weight: 600; }
.markdown-content :deep(p) { margin-bottom: 1em; }
.markdown-content :deep(a[href^="#video-t="]) {
  display: inline-block; padding: 1px 6px;
  border: 1px solid rgba(94, 106, 210, 0.4);
  color: var(--primary); text-decoration: none; border-radius: 4px; font-size: 13px;
}
.markdown-content :deep(a[href^="#video-t="]:hover) {
  background: var(--primary); color: var(--on-primary); border-color: var(--primary);
}

.no-data {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--ink-subtle); font-size: 14px;
}

/* 响应式 */
@media (max-width: 900px) {
  .workspace-toc { display: none; }
}
@media (max-width: 768px) {
  .workspace-main { flex-direction: column; }
  .workspace-video { width: 100% !important; height: 40vh; }
  .splitter { width: 100%; height: 6px; cursor: row-resize; }
  .workspace-right { width: 100% !important; flex: 1; }
  .analysis-content { padding: 16px; }
}
</style>
