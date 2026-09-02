<template>
  <div class="app-stage">
    <header class="navbar">
      <div class="nav-content">
        <div class="brand">
          <span class="brand-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
          </span>
          <router-link to="/" class="brand-name">VideoMind</router-link>
        </div>

        <nav class="nav-links">
          <router-link to="/" class="nav-link" active-class="active" :class="{ active: $route.path === '/' }">上传</router-link>
          <router-link to="/library" class="nav-link" active-class="active">资料库</router-link>
        </nav>

        <div class="nav-controls">
          <button class="theme-toggle" @click="toggleTheme" :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'" :title="isDark ? '亮色模式' : '暗色模式'">
            <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>
          </button>

          <button v-if="!currentUser" class="auth-btn" @click="openAuthModal">
            <span class="btn-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
            </span>
            登录 / 注册
          </button>

          <div v-else class="user-profile">
            <span class="user-name">:: {{ currentUser.nickname }} ::</span>
            <button class="logout-btn" @click="logout" title="退出登录" aria-label="退出登录">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
            </button>
          </div>

          <div class="status-pill" :class="{ 'is-active': uploading }" role="status" aria-live="polite">
            <div class="status-dot"></div>
            <span class="status-text">{{ systemStatusText }}</span>
          </div>
        </div>
      </div>
    </header>

    <main class="main-container">
      <router-view />
      <VideoAnalysisWorkspace
        v-if="showWorkspace"
        :sidebar="sidebar"
        :rendered-markdown="renderedMarkdown"
        @seek-video="seekVideo"
        @set-active-insight="setActiveInsight"
        @back-to-sidebar="exitWorkspace"
        @playback-error="handlePlaybackError"
        @seek-evidence="seekEvidence"
        @copy-result="copyResult"
        @download-result="downloadResult"
      />

      <div class="sidebar-backdrop" v-if="sidebar.visible && !showWorkspace" @click="closeSidebar"></div>
      <div
          ref="sidebarPanel"
          class="sidebar-panel"
          :class="{ 'is-open': sidebar.visible && !showWorkspace }"
          :inert="!sidebar.visible || showWorkspace"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          :aria-label="sidebar.title || '任务详情'"
      >
        <div class="sidebar-header">
          <div class="sidebar-title">
            <span class="icon" v-if="sidebar.type === 'ai'">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12h2"></path><path d="M20 12h2"></path><path d="M12 2v2"></path><path d="M12 20v2"></path><path d="M20.2 6.47l-1.4 1.4"></path><path d="M15.9 5.35l-1.4-1.4"></path><path d="M9 11a3 3 0 1 0 6 0a3 3 0 0 0-6 0"></path></svg>
            </span>
            <span class="icon" v-else>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
            </span>
            {{ sidebar.title }}
          </div>
          <button class="close-btn" @click="closeSidebar" aria-label="关闭分析面板">×</button>
        </div>
        <div ref="sidebarBody" class="sidebar-body">
          <div v-if="sidebar.type === 'ai'" class="video-evidence">
            <video
                v-if="sidebar.playbackUrl"
                ref="videoPlayer"
                :src="sidebar.playbackUrl"
                controls
                playsinline
                preload="metadata"
                @error="handlePlaybackError"
            ></video>
            <div v-else-if="sidebar.playbackLoading" class="video-evidence-loading">正在载入原视频...</div>
            <div v-else-if="sidebar.playbackError" class="video-evidence-error" role="alert">
              <span>{{ sidebar.playbackError }}</span>
              <button type="button" @click="retryPlayback">重新加载</button>
            </div>
            <p v-if="sidebar.playbackUrl">点击分析结果中的时间戳，可跳转到对应画面</p>
          </div>
          <div v-if="sidebar.type === 'ai' && sidebar.mode === 'compose'" class="agent-composer">
            <p class="agent-caption">选择分析模式（决定产物形态）</p>
            <div class="goal-presets agent-mode-row">
              <button
                  v-for="m in analysisModes"
                  :key="m.value"
                  :class="{ active: sidebar.analysisMode === m.value }"
                  @click="sidebar.analysisMode = m.value"
              >
                <strong>{{ m.title }}</strong>
                <span>{{ m.description }}</span>
              </button>
            </div>
            <p class="agent-caption">告诉 Agent 你希望从视频中得到什么产物</p>
            <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
            <textarea
                v-model="sidebar.goal"
                maxlength="500"
                placeholder="例如：梳理核心观点，给出带时间戳的证据和可执行建议（Ctrl / ⌘ + Enter 提交）"
                @keydown.ctrl.enter.prevent="submitAgent"
                @keydown.meta.enter.prevent="submitAgent"
            ></textarea>
            <p v-if="sidebar.goal.length > 400" class="field-counter">已输入 {{ sidebar.goal.length }} / 500 字</p>
            <div class="goal-presets">
              <button
                  v-for="preset in goalPresets"
                  :key="preset.title"
                  :class="{ active: sidebar.goal === preset.prompt }"
                  @click="sidebar.goal = preset.prompt"
              >
                <strong>{{ preset.title }}</strong>
                <span>{{ preset.description }}</span>
              </button>
            </div>
            <button class="agent-run-btn" :disabled="!sidebar.goal.trim()" @click="submitAgent">
              {{ sidebar.error ? '重新分析' : '开始分析' }}
            </button>
          </div>

          <div v-else-if="sidebar.loading" class="agent-running">
            <div class="loading-state">
              <div class="quantum-loader small"></div>
              <p aria-live="polite">{{ loadingHeadline }}</p>
              <p v-if="sidebar.streamOffline" class="stream-offline" role="status">
                连接中断，正在自动重连（第 {{ sidebar.streamRetry }} 次）· 任务仍在服务端继续
              </p>
              <p class="loading-hint">可以关闭本面板，任务会在后台继续，完成后会通知你</p>
            </div>
            <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
              <span class="meta-label">任务计划</span>
              <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
            </div>
            <div v-if="traceStages.length" class="agent-meta-block">
              <span class="meta-label">已完成阶段</span>
              <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
            </div>
          </div>

          <div v-else>
            <div v-if="sidebar.type === 'ai'">
              <div class="result-actions">
                <button type="button" @click="startNewAnalysis">更换产物</button>
                <button type="button" :disabled="!sidebar.content" @click="copyResult">复制结果</button>
                <button type="button" :disabled="!sidebar.content" @click="downloadResult">导出 Markdown</button>
              </div>
              <div class="evidence-search">
                <div class="evidence-search-form">
                  <input
                      v-model="sidebar.evidenceQuery"
                      aria-label="视频证据检索"
                      maxlength="500"
                      placeholder="定位 PPT、字幕、代码或某段讲解"
                      @keyup.enter="searchEvidence"
                  />
                  <button type="button" :disabled="sidebar.evidenceLoading || !sidebar.evidenceQuery.trim()" @click="searchEvidence">
                    {{ sidebar.evidenceLoading ? '检索中' : '定位证据' }}
                  </button>
                </div>
                <p v-if="sidebar.evidenceError" class="evidence-search-error" aria-live="polite">{{ sidebar.evidenceError }}</p>
                <div v-if="sidebar.evidenceResults.length" class="evidence-search-results" aria-live="polite">
                  <button
                      v-for="hit in sidebar.evidenceResults"
                      :key="`${hit.startMs}-${hit.endMs}`"
                      type="button"
                      :title="hit.snippet || '该时间段暂无可展示文本'"
                      @click="seekToEvidence(hit.startMs)"
                  >
                    <strong>{{ formatEvidenceTime(hit.startMs) }}</strong>
                    <small>{{ hit.source || '视频证据' }}</small>
                    <span>{{ hit.snippet || '该时间段暂无可展示文本' }}</span>
                  </button>
                </div>
              </div>
              <div class="markdown-content" v-html="renderedMarkdown" @click="seekEvidence"></div>
              <details v-if="sidebar.plan?.tasks?.length || traceStages.length" class="agent-inspector">
                <summary>分析详情</summary>
                <div class="agent-inspector-content">
                <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
                  <span class="meta-label">Planner 任务</span>
                  <div v-if="sidebar.editingPlan" class="plan-editor">
                    <div v-for="(_, index) in sidebar.planDraft" :key="index" class="plan-editor-row">
                      <input v-model="sidebar.planDraft[index]" maxlength="500" :aria-label="`任务 ${index + 1}`" />
                      <button type="button" title="删除任务" @click="removePlanTask(index)">×</button>
                    </div>
                    <button v-if="sidebar.planDraft.length < 5" type="button" @click="addPlanTask">添加任务</button>
                    <div class="plan-editor-actions">
                      <button type="button" @click="cancelPlanEdit">取消</button>
                      <button type="button" :disabled="sidebar.rerunLoading" @click="rerunWithPlan">
                        {{ sidebar.rerunLoading ? '提交中' : '按新计划重跑' }}
                      </button>
                    </div>
                  </div>
                  <template v-else>
                    <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
                    <button type="button" class="plan-edit-trigger" @click="startPlanEdit">调整计划</button>
                  </template>
                </div>
                <div v-if="traceStages.length" class="agent-meta-block">
                  <span class="meta-label">执行轨迹</span>
                  <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
                </div>
                <div v-if="sidebar.evaluation && Object.keys(sidebar.evaluation).length" class="quality-row">
                  <span>结构完整 {{ sidebar.evaluation.structuredValid ? '通过' : '待完善' }}</span>
                  <span>证据支持 {{ formatPercent(sidebar.evaluation.evidenceSupportRate) }}</span>
                  <span>Critic {{ sidebar.evaluation.criticPassed ? '通过' : '达到轮次上限' }}</span>
                </div>
                </div>
              </details>
              <div class="follow-up-box">
                <textarea
                    v-model="sidebar.followUp"
                    maxlength="500"
                    placeholder="基于视频继续追问...（Ctrl / ⌘ + Enter 发送）"
                    @keydown.ctrl.enter.prevent="submitFollowUp"
                    @keydown.meta.enter.prevent="submitFollowUp"
                ></textarea>
                <button :disabled="sidebar.followUpLoading || !sidebar.followUp.trim()" @click="submitFollowUp">
                  {{ sidebar.followUpLoading ? '分析中' : '追问' }}
                </button>
              </div>
              <div class="feedback-row">
                <span>这个结果有帮助吗？</span>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === 1 }" :aria-pressed="sidebar.feedback === 1" @click="sendFeedback(1)" title="有帮助">赞</button>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === -1 }" :aria-pressed="sidebar.feedback === -1" @click="sendFeedback(-1)" title="需改进">踩</button>
              </div>
            </div>
            <div v-else class="text-content">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <template v-if="sidebar.content">
                <div class="result-actions">
                  <button type="button" @click="copyResult">复制全文</button>
                  <button type="button" @click="downloadResult">导出文本</button>
                </div>
                <p class="text-meta">{{ transcriptMeta }}</p>
                <pre>{{ sidebar.content }}</pre>
              </template>
              <p v-else-if="!sidebar.error" class="text-meta">这个视频还没有可展示的转写文本。</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="showAuthModal" class="auth-backdrop" @click.self="closeAuthModal">
        <div
            ref="authPanel"
            class="auth-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-title"
            @keydown="trapAuthFocus"
        >
          <div class="auth-header">
            <h2 id="auth-title" class="auth-title">{{ authMode === 'login' ? '用户登录' : '新用户注册' }}</h2>
            <button class="close-btn" @click="closeAuthModal" aria-label="关闭登录窗口">×</button>
          </div>
          <form class="auth-body" @submit.prevent="handleAuth">
            <div class="input-group">
              <label for="auth-username">用户名</label>
              <input id="auth-username" v-model="authForm.username" type="text" placeholder="输入账号" autocomplete="username" autofocus />
            </div>
            <div class="input-group">
              <label for="auth-password">密码</label>
              <input id="auth-password" v-model="authForm.password" type="password" placeholder="输入密码" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" />
            </div>
            <div class="input-group" v-if="authMode === 'register'">
              <label for="auth-nickname">昵称</label>
              <input id="auth-nickname" v-model="authForm.nickname" type="text" placeholder="设置一个好听的名字" autocomplete="nickname" />
            </div>
            <div class="auth-action">
              <button type="submit" class="cyber-btn" :disabled="authLoading">
                <span v-if="!authLoading">{{ authMode === 'login' ? '立即登录' : '提交注册' }}</span>
                <span v-else>请求处理中...</span>
              </button>
            </div>
            <div class="auth-toggle">
              <span class="toggle-text">{{ authMode === 'login' ? '没有账号?' : '已有账号?' }}</span>
              <button type="button" class="toggle-link" @click="switchAuthMode()">{{ authMode === 'login' ? '去注册' : '去登录' }}</button>
            </div>
            <p
                v-if="authMessage"
                class="auth-msg"
                :class="{'error': authError}"
                :role="authError ? 'alert' : 'status'"
                aria-live="polite"
            >{{ authMessage }}</p>
          </form>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch, onMounted, onUnmounted, provide, toRaw } from 'vue'
import { apiRequest, clearAuthToken, hasAuthToken, setAuthToken } from './api'
import {
  forgetUploadProgress,
  formatBytes,
  formatDurationText,
  hasUploadProgress,
  uploadVideoInChunks,
  validateVideoFile
} from './chunkUpload'
import { DEMO_ITEM } from './demoData'
import { createTaskStreams } from './taskEvents'
import { useAnalysisWorkspace } from './useAnalysisWorkspace'
import VideoAnalysisWorkspace from './components/VideoAnalysisWorkspace.vue'

// --- 变量定义 ---
const DEMO_MODE = new URLSearchParams(window.location.search).has('demo')
const MESSAGE_TIMEOUT_MS = 4000
const file = ref(null)
const videoUrl = ref('')
const message = ref('')
const messageIsError = ref(false)
const uploading = ref(false)
const uploadProgress = ref({ label: '准备上传', filename: '', percent: null, detail: '', warning: '' })
const uploadAbort = ref(null)
const resumableFile = ref(null)
const resumableChunks = ref({ done: 0, total: 0 })
const list = ref([])
const searchQuery = ref('')
const videoPlayer = ref(null)
const sidebarPanel = ref(null)
const sidebarBody = ref(null)
const authPanel = ref(null)
const deletingId = ref(null)
const isOffline = ref(typeof navigator !== 'undefined' && navigator.onLine === false)
const activeTasks = ref([])
const elapsedSeconds = ref(0)
const visibleList = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  if (!query) return list.value
  return list.value.filter(item => item.filename?.toLocaleLowerCase().includes(query))
})
const isDragOver = ref(false)
const currentUser = ref(null)
const showAuthModal = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const authMessage = ref('')
const authError = ref(false)
const authForm = ref({ username: '', password: '', nickname: '' })

// --- 主题切换 ---
const isDark = ref(true)
const toggleTheme = () => {
  isDark.value = !isDark.value
  const theme = isDark.value ? 'dark' : 'light'
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('theme', theme)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', isDark.value ? '#010102' : '#ffffff')
}
const initTheme = () => {
  const saved = localStorage.getItem('theme')
  if (saved === 'light') {
    isDark.value = false
    document.documentElement.setAttribute('data-theme', 'light')
  }
}

const taskStreams = createTaskStreams({
  onActiveChange: tasks => { activeTasks.value = tasks }
})
const VIDEO_EXTENSIONS = new Set(['mp4', 'mov', 'mkv', 'avi', 'webm', 'm4v'])
let dragDepth = 0
let messageTimer = null
let elapsedTimer = null
let lastUploadProgress = {}
let focusBeforeAuth = null
let focusBeforeSidebar = null

// --- 状态展示 ---
const activeTaskOf = mediaId => activeTasks.value.find(task => String(task.id) === String(mediaId))

const systemStatusText = computed(() => {
  if (isOffline.value) return '网络已断开'
  if (uploading.value) {
    return uploadProgress.value.percent !== null
      ? `上传 ${uploadProgress.value.percent}%`
      : '处理中'
  }
  if (activeTasks.value.length) return `后台任务 ${activeTasks.value.length}`
  return '系统就绪'
})

const cardStatusClass = item => {
  if (activeTaskOf(item.id)) return 'processing'
  return mediaStatusClass(item.status)
}
const cardStatusLabel = item => {
  const task = activeTaskOf(item.id)
  if (task) return task.type === 'ai' ? 'ANALYZING' : 'TRANSCRIBING'
  return mediaStatusLabel(item.status)
}
const cardStatusTitle = item => {
  const task = activeTaskOf(item.id)
  if (!task) return null
  return task.type === 'ai'
    ? 'AI 分析正在后台执行，完成后会提示你'
    : '文字提取正在后台执行，完成后会提示你'
}
const actionTitle = (item, label) => item.status === 'COMPLETED'
  ? null
  : `视频尚未处理完成，暂时无法${label}`

// 分屏工作台：所有分析模式完成后统一使用分屏视图
const showWorkspace = computed(() =>
  sidebar.value.visible
  && sidebar.value.mode === 'result'
  && !sidebar.value.loading
  && (sidebar.value.content || sidebar.value.deepResult)
  && sidebar.value.viewMode !== 'sidebar'
)

const exitWorkspace = () => {
  sidebar.value.viewMode = 'sidebar'
  sidebar.value.deepResult = null
  closeSidebar()
}

const elapsedLabel = computed(() => {
  const total = elapsedSeconds.value
  if (total < 1) return ''
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  return `${minutes}:${String(total % 60).padStart(2, '0')}`
})

const loadingHeadline = computed(() => {
  const fallback = sidebar.value.type === 'ai'
    ? 'Agent 正在分析视频证据'
    : '正在识别视频语音'
  const headline = sidebar.value.statusMessage || fallback
  // 用“已等待”而不是“已运行”：接管历史任务时计时是从打开面板算起的。
  return elapsedLabel.value ? `${headline} · 已等待 ${elapsedLabel.value}` : headline
})

const transcriptMeta = computed(() => {
  const length = sidebar.value.content?.length || 0
  if (!length) return ''
  return `共 ${length.toLocaleString('zh-CN')} 字`
})

const resumeHint = computed(() => {
  const target = resumableFile.value
  if (!target) return ''
  const { done, total } = resumableChunks.value
  const progress = total ? `已完成 ${Math.round((done / total) * 100)}%` : '已保留上传进度'
  return `${target.name} ${progress}，可继续未完成的上传`
})

// --- 核心业务逻辑 ---

const handleDragEnter = () => {
  dragDepth += 1
  isDragOver.value = true
}

// 拖过子元素也会触发 dragleave，用进出计数避免提示文案反复闪烁。
const handleDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (!dragDepth) isDragOver.value = false
}

const resetDragState = () => {
  dragDepth = 0
  isDragOver.value = false
}

/** 统一入口：登录、格式、体积三道校验全部在进入上传态之前完成。 */
const startUpload = async (selectedFile, extraFileCount = 0) => {
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }
  if (!selectedFile) return
  if (!isSupportedVideo(selectedFile)) {
    showMsg(`⚠️ ${selectedFile.name} 不是受支持的视频格式`, true)
    return
  }
  const invalid = validateVideoFile(selectedFile)
  if (invalid) {
    showMsg(`⚠️ ${invalid}`, true)
    return
  }
  if (extraFileCount > 0) {
    showMsg(`一次只处理一个视频，已选择 ${selectedFile.name}，其余 ${extraFileCount} 个已忽略`)
  }
  file.value = selectedFile
  videoUrl.value = ''
  await uploadFile()
}

const handleFileChange = async (e) => {
  const selected = e.target.files
  await startUpload(selected?.[0], Math.max(0, (selected?.length || 0) - 1))
  e.target.value = ''
}

const handleDrop = async (e) => {
  resetDragState()
  const dropped = e.dataTransfer?.files
  if (!dropped?.length) return
  await startUpload(dropped[0], dropped.length - 1)
}

const buildUploadWarning = progress => {
  if (progress.retryingCount) {
    return `网络不稳定，正在重试 ${progress.retryingCount} 个分片（第 ${progress.retryAttempt}/${progress.retryMaxAttempts} 次）`
  }
  if (progress.resumedChunks) {
    return `已续传：跳过 ${progress.resumedChunks} 个此前完成的分片`
  }
  return ''
}

const applyUploadProgress = progress => {
  lastUploadProgress = progress
  const merging = progress.phase === 'merging'
  const detail = [`${formatBytes(progress.uploadedBytes)} / ${formatBytes(progress.totalBytes)}`]
  detail.push(`分片 ${progress.completedChunks}/${progress.totalChunks}`)
  if (!merging && progress.bytesPerSecond) {
    detail.push(`${formatBytes(progress.bytesPerSecond)}/s`)
    const eta = formatDurationText(progress.etaSeconds)
    if (eta) detail.push(`剩余约 ${eta}`)
  }
  uploadProgress.value = {
    label: merging ? '分片已全部送达，正在服务端合并' : '正在安全上传',
    filename: file.value?.name || uploadProgress.value.filename,
    percent: progress.percent,
    detail: detail.join(' · '),
    warning: buildUploadWarning(progress)
  }
}

const rememberResumableUpload = target => {
  if (!target || !hasUploadProgress(target)) {
    resumableFile.value = null
    return
  }
  resumableFile.value = target
  resumableChunks.value = {
    done: lastUploadProgress.completedChunks || 0,
    total: lastUploadProgress.totalChunks || 0
  }
}

const uploadFile = async () => {
  const target = file.value
  if (!target) return
  if (DEMO_MODE) {
    showMsg('演示模式：已模拟完成分片上传')
    return
  }

  const controller = new AbortController()
  uploadAbort.value = controller
  uploading.value = true
  resumableFile.value = null
  lastUploadProgress = {}
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: hasUploadProgress(target) ? '正在核对已上传分片' : '准备分片上传',
    filename: target.name,
    percent: 0,
    detail: `0 B / ${formatBytes(target.size)}`,
    warning: ''
  }

  try {
    const uploadedMedia = await uploadVideoInChunks(target, applyUploadProgress, controller.signal)
    if (currentUser.value?.id !== uploadUserId) return
    resumableFile.value = null
    showMsg(`✅ ${target.name} 上传完成`)
    await fetchList({ notify: true })
    openAgent(uploadedMedia)
  } catch (error) {
    if (currentUser.value?.id !== uploadUserId) return
    rememberResumableUpload(target)
    if (error?.aborted) {
      showMsg('上传已取消，进度已保留，可点“继续上传”接着传')
      return
    }
    console.error(error)
    showMsg(
      resumableFile.value
        ? `❌ 上传中断：${error.message}（进度已保留，可继续上传）`
        : `❌ 上传失败：${error.message}`,
      true
    )
  } finally {
    uploading.value = false
    uploadAbort.value = null
    file.value = null
  }
}

const cancelUpload = () => {
  if (!uploadAbort.value) return
  uploadProgress.value = { ...uploadProgress.value, label: '正在取消上传', warning: '' }
  uploadAbort.value.abort()
}

const resumeUpload = async () => {
  const target = resumableFile.value
  if (!target || uploading.value) return
  file.value = target
  await uploadFile()
}

const discardResumableUpload = () => {
  forgetUploadProgress(resumableFile.value)
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  showMsg('已清除保留的上传进度，下次将从头开始')
}

const handleUrlUpload = async () => {
  const normalizedUrl = videoUrl.value.trim()
  if (!normalizedUrl) return
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (DEMO_MODE) {
    videoUrl.value = ''
    showMsg('演示模式：已模拟完成链接解析')
    return
  }

  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }

  let parsedUrl
  try {
    parsedUrl = new URL(normalizedUrl)
  } catch {
    parsedUrl = null
  }
  if (!parsedUrl || !['http:', 'https:'].includes(parsedUrl.protocol)) {
    showMsg('⚠️ 请输入合法的 http/https 链接', true)
    return
  }

  uploading.value = true
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: '正在解析视频链接',
    filename: parsedUrl.hostname,
    percent: null,
    detail: '服务端正在拉取源视频，时长取决于源站速度',
    warning: ''
  }
  messageIsError.value = false
  message.value = '正在解析链接并极速下载 (低码率模式)...'

  const formData = new FormData()
  formData.append('url', normalizedUrl)

  try {
    const res = await apiRequest('/media/upload-url', {
      method: 'POST',
      body: formData
    })
    if (!res.ok) throw new Error(await res.text())
    const uploadedMedia = await res.json()
    if (currentUser.value?.id !== uploadUserId) return

    showMsg('✅ 链接资源已入库')
    videoUrl.value = ''
    await fetchList({ notify: true })
    openAgent(uploadedMedia)
  } catch (error) {
    console.error(error)
    if (currentUser.value?.id !== uploadUserId) return
    let errMsg = error.message
    if (errMsg.includes("Unsupported URL")) errMsg = "不支持该平台链接"
    showMsg('❌ 解析失败: ' + errMsg, true)
  } finally {
    uploading.value = false
  }
}

/** 成功提示自动消失；错误提示保留到用户点掉，避免关键失败原因 4 秒后就没了。 */
const showMsg = (msg, isError = false) => {
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = msg
  messageIsError.value = isError
  if (isError) return
  messageTimer = setTimeout(() => {
    if (message.value !== msg) return
    message.value = ''
    messageIsError.value = false
  }, MESSAGE_TIMEOUT_MS)
}

const dismissMessage = () => {
  if (!messageIsError.value) return
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = ''
  messageIsError.value = false
}

const fetchList = async ({ notify = false } = {}) => {
  if (DEMO_MODE) return list.value
  if (!currentUser.value) {
    list.value = []
    return list.value
  }
  try {
    // 带时间戳绕开浏览器缓存，避免删除/新增之后列表还是旧的。
    const res = await apiRequest(`/media/list?_t=${Date.now()}`)
    if (res.status === 401) return null
    if (!res.ok) throw new Error('加载视频列表失败')
    list.value = await res.json()
  } catch (error) {
    console.error(error)
    if (notify) showMsg('视频资料库加载失败，请稍后刷新', true)
    return null
  }
  return list.value
}

const isSupportedVideo = selectedFile => {
  if (selectedFile.type?.startsWith('video/')) return true
  const extension = selectedFile.name?.split('.').pop()?.toLowerCase()
  return VIDEO_EXTENSIONS.has(extension)
}

const mediaStatusClass = status => ['COMPLETED', 'PROCESSING', 'FAILED'].includes(status)
  ? status.toLowerCase()
  : 'unknown'
const mediaStatusLabel = status => ({
  COMPLETED: 'READY',
  PROCESSING: 'PROCESSING',
  FAILED: 'FAILED'
})[status] || 'PENDING'

const {
  sidebar,
  goalPresets,
  analysisModes,
  traceStages,
  renderedMarkdown,
  transcribe,
  closeSidebar,
  openAgent,
  submitAgent,
  startNewAnalysis,
  showDemoResult,
  startPlanEdit,
  cancelPlanEdit,
  addPlanTask,
  removePlanTask,
  rerunWithPlan,
  submitFollowUp,
  searchEvidence,
  sendFeedback,
  retryPlayback,
  handlePlaybackError,
  resetWorkspace,
  discardMediaWorkspace,
  fetchDeepResult,
  setActiveInsight,
  setWorkspaceView,
  formatPercent
} = useAnalysisWorkspace({
  demoMode: DEMO_MODE,
  taskStreams,
  showMessage: showMsg,
  refreshMediaList: fetchList,
  findMediaItem: id => list.value.find(item => item.id === id),
  onAnswerAppended: () => scrollToLatestAnswer()
})

/** 追问的答案追加在长文末尾，主动滚过去，否则用户会以为“点了没反应”。 */
const scrollToLatestAnswer = async () => {
  await nextTick()
  const container = sidebarBody.value?.querySelector('.markdown-content')
  if (!container) return
  const headings = container.querySelectorAll('h2, h3')
  const anchor = headings.length ? headings[headings.length - 1] : container.lastElementChild
  anchor?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const seekVideo = seconds => {
  if (!Number.isFinite(seconds)) return
  const player = videoPlayer.value
  if (!player) {
    if (sidebar.value.playbackError) {
      showMsg('原视频加载失败，无法跳转，可先点“重新加载”', true)
    } else if (sidebar.value.playbackLoading) {
      showMsg('原视频还在载入，稍等一下再点这个时间戳')
    } else {
      showMsg('这个视频暂时没有可播放的原片，无法跳转', true)
    }
    return
  }
  if (player.readyState === 0) {
    player.addEventListener('loadedmetadata', () => seekVideo(seconds), { once: true })
    return
  }
  const duration = player.duration
  const maxTime = Number.isFinite(duration) ? Math.max(0, duration - 0.1) : Number.MAX_SAFE_INTEGER
  player.currentTime = Math.min(Math.max(0, seconds), maxTime)
  player.play().catch(() => {})
  player.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

const seekEvidence = event => {
  const link = event.target.closest('a[href^="#video-t="]')
  if (!link) return
  event.preventDefault()
  seekVideo(Number(link.getAttribute('href').split('=')[1]))
}

const seekToEvidence = timestampMs => seekVideo(Number(timestampMs) / 1000)
const formatEvidenceTime = timestampMs => {
  const seconds = Math.max(0, Math.floor(Number(timestampMs) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const time = `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${time}` : time
}

/** Clipboard API 在非 HTTPS 环境不可用，这里保留一条降级路径，避免“复制失败”变成死路。 */
const copyToClipboard = async text => {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 继续走下面的降级方案。
  }
  try {
    const scratch = document.createElement('textarea')
    scratch.value = text
    scratch.setAttribute('readonly', '')
    scratch.style.position = 'fixed'
    scratch.style.top = '0'
    scratch.style.opacity = '0'
    document.body.appendChild(scratch)
    scratch.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(scratch)
    return copied
  } catch {
    return false
  }
}

const copyResult = async () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可复制的内容', true)
    return
  }
  const label = sidebar.value.type === 'ai' ? '分析结果' : '转写全文'
  if (await copyToClipboard(content)) showMsg(`${label}已复制`)
  else showMsg('复制失败，请手动选中内容后复制', true)
}

const resultFileBaseName = () => {
  const title = sidebar.value.title || ''
  const raw = title.split(' · ').slice(1).join(' · ') || title
  const cleaned = raw.replace(/\.[^/.]+$/, '').replace(/[\\/:*?"<>|]/g, '_').trim()
  return cleaned || (sidebar.value.type === 'ai' ? 'analysis' : 'transcript')
}

const downloadResult = () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可导出的内容', true)
    return
  }
  const isMarkdown = sidebar.value.type === 'ai'
  const blob = new Blob([content], {
    type: isMarkdown ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${resultFileBaseName()}.${isMarkdown ? 'md' : 'txt'}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 立刻 revoke 在部分浏览器会导致下载拿不到内容，延后一拍更稳。
  setTimeout(() => URL.revokeObjectURL(url), 0)
  showMsg(`已导出 ${link.download}`)
}

const deleteItem = async (item) => {
  if (DEMO_MODE) {
    list.value = list.value.filter(i => i.id !== item.id)
    discardMediaWorkspace(item.id)
    showMsg('演示任务已移除')
    return
  }
  if (deletingId.value) return
  const runningTask = activeTaskOf(item.id)
  const warning = runningTask
    ? '\n\n注意：该视频还有任务正在后台执行，删除后这次的结果会丢失。'
    : ''
  if (!confirm(`确认要永久删除 "${item.filename}" 吗？${warning}`)) return
  deletingId.value = item.id
  try {
    const res = await apiRequest(`/media/delete?id=${item.id}`, { method: 'DELETE' })
    const text = await res.text()
    if (res.ok) {
      showMsg(`已删除 ${item.filename}`)
      list.value = list.value.filter(i => i.id !== item.id)
      discardMediaWorkspace(item.id)
    } else {
      showMsg('❌ ' + text, true)
    }
  } catch (e) {
    showMsg('❌ 删除请求失败', true)
  } finally {
    deletingId.value = null
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return '--'
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const downloadAudio = async (item) => {
  if (DEMO_MODE) {
    showMsg(`演示模式：${item.filename} 音频已准备`)
    return
  }
  let fileName = item.filename || 'audio.mp3';
  fileName = fileName.replace(/\.[^/.]+$/, "") + ".mp3";
  try {
    showMsg('正在转码并下载...')
    const res = await apiRequest(`/analysis/download?id=${item.id}`)
    // 失败时后端返回的是 JSON 信封，api.js 会把 message 解包给 text()，
    // 这里读出来向上抛，避免把“视频不存在 / 无权访问 / 转码失败”统一显示成同一句话。
    if (!res.ok) throw new Error((await res.text()) || '请稍后重试')
    const blob = await res.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    showMsg('✅ 下载完成')
  } catch (e) {
    showMsg('音频下载失败：' + (e?.message || '请稍后重试'), true)
  }
}

const restoreFocus = element => {
  if (element?.isConnected && typeof element.focus === 'function') element.focus()
}

const openAuthModal = () => {
  if (showAuthModal.value) return
  focusBeforeAuth = document.activeElement
  showAuthModal.value = true
  authMessage.value = ''
  authForm.value = { username: '', password: '', nickname: '' }
}
const closeAuthModal = () => {
  showAuthModal.value = false
  restoreFocus(focusBeforeAuth)
  focusBeforeAuth = null
}
const closeActiveOverlay = () => {
  if (showAuthModal.value) closeAuthModal()
  else if (sidebar.value.visible) closeSidebar()
}
const handleKeydown = event => {
  if (event.key === 'Escape') closeActiveOverlay()
}

/** 弹窗内循环 Tab，键盘用户不会一路跳到被遮住的背景里。 */
const trapAuthFocus = event => {
  if (event.key !== 'Tab' || !authPanel.value) return
  const focusable = [...authPanel.value.querySelectorAll('button, input, [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.disabled && element.offsetParent !== null)
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || !authPanel.value.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

const switchAuthMode = ({ keepMessage = false } = {}) => {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  if (!keepMessage) authMessage.value = ''
}
const handleAuth = async () => {
  if (!authForm.value.username || !authForm.value.password) {
    authMessage.value = '请输入完整的账号和密码'
    authError.value = true
    return
  }
  authLoading.value = true
  authMessage.value = ''
  const endpoint = authMode.value === 'login' ? '/user/login' : '/user/register'
  try {
    const res = await apiRequest(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(authForm.value)
    })
    if (!res.ok) {
      authMessage.value = (await res.text()) || `请求失败（HTTP ${res.status}）`
      authError.value = true
      return
    }
    const data = await res.json().catch(() => null)
    if (!data?.userInfo) {
      authMessage.value = '服务端返回异常，请稍后重试'
      authError.value = true
      return
    }
    if (authMode.value === 'login') {
      currentUser.value = data.userInfo
      localStorage.setItem('user', JSON.stringify(data.userInfo))
      setAuthToken(data.token)
      closeAuthModal()
      showMsg(`欢迎回来，${data.userInfo.nickname}`)
      fetchList({ notify: true })
    } else {
      authMessage.value = '注册成功，账号密码已保留，直接点“立即登录”即可'
      authError.value = false
      setTimeout(() => switchAuthMode({ keepMessage: true }), 900)
    }
  } catch (e) {
    console.error(e)
    authMessage.value = e?.message || '网络连接错误'
    authError.value = true
  } finally {
    authLoading.value = false
  }
}
/** 退出与登录失效走同一套清理，避免两处漏掉不同的字段。 */
const resetSessionState = () => {
  uploadAbort.value?.abort()
  uploadAbort.value = null
  taskStreams.stopAll()
  resetWorkspace()
  currentUser.value = null
  list.value = []
  searchQuery.value = ''
  videoUrl.value = ''
  file.value = null
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  uploading.value = false
  localStorage.removeItem('user')
}

const logout = () => {
  if (hasAuthToken()) {
    apiRequest('/user/logout', { method: 'POST' }).catch(() => {})
  }
  resetSessionState()
  clearAuthToken()
  showMsg('已退出系统')
}

const handleAuthExpired = () => {
  resetSessionState()
  showMsg('登录状态已失效，请重新登录', true)
  openAuthModal()
}

const handleOnline = () => {
  isOffline.value = false
  showMsg('网络已恢复，正在同步最新状态')
  if (currentUser.value) fetchList()
}

const handleOffline = () => {
  isOffline.value = true
  showMsg('网络已断开：上传会自动重试，后台任务会在恢复后继续', true)
}

// 上传中误关标签页会白丢已传分片，这里让浏览器先问一句。
const handleBeforeUnload = event => {
  if (!uploading.value) return
  event.preventDefault()
  event.returnValue = ''
}

// 打开弹层时挂上标记类，锁背景滚动的规则只在窄屏生效（见样式里的说明）。
const overlayOpen = computed(() => (sidebar.value.visible && !showWorkspace.value) || showAuthModal.value)
watch(overlayOpen, open => {
  document.body.classList.toggle('overlay-open', open)
})

watch(() => sidebar.value.visible, async visible => {
  if (visible) {
    focusBeforeSidebar = document.activeElement
    await nextTick()
    sidebarPanel.value?.focus()
    return
  }
  restoreFocus(focusBeforeSidebar)
  focusBeforeSidebar = null
})

// 向子页面组件提供共享状态
provide('appState', {
  // Upload state
  videoUrl, uploading, uploadProgress, uploadAbort, isDragOver, resumableFile,
  resumeHint, message, messageIsError,
  // Upload handlers
  handleFileChange, handleDragEnter, handleDragLeave, handleDrop, handleUrlUpload,
  cancelUpload, resumeUpload, discardResumableUpload, dismissMessage,
  // Library state
  list, searchQuery, deletingId,
  // Library actions
  openAgent, deleteItem, transcribe, downloadAudio, formatTime,
  // Helpers
  activeTaskOf, mediaStatusClass, mediaStatusLabel, cardStatusClass, cardStatusLabel,
})

// 长任务给一个时间锚点，用户才不会怀疑是不是卡死了。
watch(() => sidebar.value.loading, loading => {
  clearInterval(elapsedTimer)
  elapsedTimer = null
  elapsedSeconds.value = 0
  if (!loading) return
  const startedAt = Date.now()
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 1000)
})

onMounted(() => {
  initTheme()
  window.addEventListener('auth-expired', handleAuthExpired)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('beforeunload', handleBeforeUnload)
  if (DEMO_MODE) {
    currentUser.value = { id: 1, nickname: 'Agent Demo' }
    list.value = [DEMO_ITEM]
    openAgent(DEMO_ITEM)
    showDemoResult()
    return
  }
  const savedUser = localStorage.getItem('user')
  if (savedUser && hasAuthToken()) {
    try {
      currentUser.value = JSON.parse(savedUser)
    } catch(e) {}
  }
  fetchList({ notify: Boolean(currentUser.value) })
})
onUnmounted(() => {
  window.removeEventListener('auth-expired', handleAuthExpired)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearTimeout(messageTimer)
  clearInterval(elapsedTimer)
  uploadAbort.value?.abort()
  document.body.classList.remove('overlay-open')
  taskStreams.stopAll()
})
</script>

<style>
/* 字体已改为 index.html 里的非阻塞 <link> 加载，此处不再用 @import 阻塞首屏 CSSOM */

/* Linear Design System — https://linear.app */

:root {
  /* Surface */
  --canvas: #010102;
  --surface-1: #0f1011;
  --surface-2: #141516;
  --surface-3: #18191a;
  --surface-4: #191a1b;
  /* Border */
  --hairline: #23252a;
  --hairline-strong: #34343a;
  --hairline-tertiary: #3e3e44;
  /* Text */
  --ink: #f7f8f8;
  --ink-muted: #d0d6e0;
  --ink-subtle: #8a8f98;
  --ink-tertiary: #62666d;
  /* Brand */
  --primary: #5e6ad2;
  --primary-hover: #828fff;
  --primary-focus: #5e69d1;
  --on-primary: #ffffff;
  --brand-secure: #7a7fad;
  /* Semantic */
  --semantic-success: #27a644;
  --semantic-error: #e5484d;
  /* Motion tokens — spring-feeling easing */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --duration-fast: 150ms;
  --duration-normal: 250ms;
  --duration-slow: 400ms;
  --duration-exit: 180ms;
}

/* 亮色主题 — Linear inverse surface */
[data-theme="light"] {
  --canvas: #ffffff;
  --surface-1: #f5f6f6;
  --surface-2: #f0f1f1;
  --surface-3: #ebedee;
  --surface-4: #e8e9ea;
  --hairline: #e0e2e4;
  --hairline-strong: #c8cacd;
  --hairline-tertiary: #b0b3b6;
  --ink: #000000;
  --ink-muted: #3c3f44;
  --ink-subtle: #62666d;
  --ink-tertiary: #8a8f98;
  --primary: #5e6ad2;
  --primary-hover: #4957b8;
  --primary-focus: #5e69d1;
  --on-primary: #ffffff;
  --brand-secure: #5a5f8a;
  --semantic-success: #1a7f37;
  --semantic-error: #cf222e;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

html, body, #app {
  margin: 0 !important; padding: 0 !important; width: 100vw !important;
  max-width: 100vw !important; min-height: 100vh !important;
  overflow-x: hidden; background-color: var(--canvas);
  transition: background-color 0.5s var(--ease-out), color 0.5s var(--ease-out);
}

.app-stage { position: relative; z-index: 1; width: 100%; min-height: 100vh; color: var(--ink); font-family: 'DM Sans', 'Noto Sans SC', -apple-system, system-ui, sans-serif; }

/* 导航 — Linear top-nav */
.navbar { position: sticky; top: 0; z-index: 100; width: 100%; height: 56px; background: color-mix(in srgb, var(--canvas) 85%, transparent); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border-bottom: 1px solid var(--hairline); }
.nav-content { max-width: 1280px; margin: 0 auto; padding: 0 24px; display: flex; justify-content: space-between; align-items: center; height: 100%; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand-icon { color: var(--primary); display: flex; align-items: center; transition: transform var(--duration-normal) var(--ease-spring); }
.brand:hover .brand-icon { transform: scale(1.1) rotate(-5deg); }
.brand-name { font-size: 18px; font-weight: 600; color: var(--ink); letter-spacing: -0.3px; text-decoration: none; }

.nav-links { display: flex; gap: 4px; margin-left: 24px; }
.nav-link {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-subtle);
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.15s;
}
.nav-link:hover { color: var(--ink); background: var(--surface-1); }
.nav-link.active, .nav-link.router-link-exact-active { color: var(--ink); background: var(--surface-2); }

.nav-controls { display: flex; align-items: center; gap: 10px; }
.theme-toggle { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: transparent; border: 1px solid var(--hairline); border-radius: 8px; color: var(--ink-subtle); cursor: pointer; transition: all var(--duration-normal) var(--ease-out); }
.theme-toggle:hover { background: var(--surface-1); color: var(--ink); border-color: var(--hairline-strong); transform: rotate(15deg); }
.theme-toggle:active { transform: rotate(15deg) scale(0.92); }
.auth-btn { background: var(--surface-1); border: 1px solid var(--hairline); color: var(--ink); padding: 8px 14px; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; display: flex; align-items: center; gap: 8px; transition: all var(--duration-normal) var(--ease-out); }
.auth-btn:hover { background: var(--surface-2); border-color: var(--hairline-strong); transform: translateY(-1px); }
.auth-btn:active { transform: translateY(0) scale(0.98); }
.user-profile { display: flex; align-items: center; gap: 10px; font-size: 14px; color: var(--ink-muted); }
.user-name { color: var(--primary); font-weight: 500; }
.logout-btn { background: none; border: none; color: var(--ink-subtle); cursor: pointer; padding: 4px; display: flex; align-items: center; transition: color 0.15s; }
.logout-btn:hover { color: var(--semantic-error); }

.status-pill { display: flex; align-items: center; gap: 8px; background: var(--surface-2); padding: 2px 10px; border-radius: 9999px; border: none; font-size: 12px; color: var(--ink-muted); }
.status-dot { width: 6px; height: 6px; background: var(--semantic-success); border-radius: 50%; }
.status-pill.is-active .status-dot { animation: pulse-primary 1.5s infinite alternate; }

/* Hero — Linear display-lg */
.main-container { max-width: 1280px; margin: 0 auto; padding: 48px 24px; }
.hero-section { text-align: center; margin-bottom: 96px; }
.hero-eyebrow { display: inline-block; font-size: 13px; font-weight: 500; letter-spacing: 0.4px; color: var(--primary); background: rgba(94, 106, 210, 0.08); padding: 4px 12px; border-radius: 9999px; margin-bottom: 20px; opacity: 0; animation: fadeSlideDown 0.6s var(--ease-out) 0.1s forwards; }
.slogan-main { font-size: clamp(36px, 5vw, 56px); font-weight: 600; letter-spacing: -1.8px; line-height: 1.1; margin-bottom: 12px; color: var(--ink); opacity: 0; animation: fadeSlideDown 0.6s var(--ease-out) 0.2s forwards; }
.slogan-sub { font-size: 18px; color: var(--ink-subtle); letter-spacing: 0; margin-bottom: 48px; line-height: 1.5; max-width: 760px; margin-left: auto; margin-right: auto; white-space: nowrap; opacity: 0; animation: fadeSlideDown 0.6s var(--ease-out) 0.35s forwards; }

/* Feature grid — Linear feature-card */
.feature-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 64px; max-width: 880px; margin-left: auto; margin-right: auto; }
.feature-card { background: var(--surface-1); border: 1px solid var(--hairline); border-radius: 12px; padding: 24px; text-align: left; transition: all var(--duration-normal) var(--ease-out); opacity: 0; animation: fadeSlideUp 0.5s var(--ease-out) forwards; }
.feature-card:nth-child(1) { animation-delay: 0.5s; }
.feature-card:nth-child(2) { animation-delay: 0.6s; }
.feature-card:nth-child(3) { animation-delay: 0.7s; }
.feature-card:hover { border-color: var(--hairline-strong); transform: translateY(-4px); box-shadow: 0 8px 24px -8px rgba(0, 0, 0, 0.3); }
.feature-icon { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; background: rgba(94, 106, 210, 0.08); border-radius: 8px; color: var(--primary); margin-bottom: 16px; transition: all var(--duration-normal) var(--ease-spring); }
.feature-card:hover .feature-icon { transform: scale(1.1); background: rgba(94, 106, 210, 0.15); }
.feature-card h3 { font-size: 16px; font-weight: 500; color: var(--ink); margin-bottom: 8px; letter-spacing: -0.1px; }
.feature-card p { font-size: 14px; color: var(--ink-subtle); line-height: 1.5; }

/* Upload — Linear feature-card */
.upload-wrapper { max-width: 720px; margin: 0 auto; opacity: 0; animation: fadeSlideUp 0.6s var(--ease-out) 0.45s forwards; }

.upload-magnet {
  position: relative;
  background: var(--surface-1);
  border-radius: 12px;
  border: 1px solid var(--hairline);
  overflow: hidden;
  transition: all var(--duration-normal) var(--ease-out);
}
.upload-magnet:hover { border-color: var(--hairline-strong); box-shadow: 0 4px 16px -4px rgba(0, 0, 0, 0.2); }
.upload-magnet.is-dragover { border-color: var(--primary); transform: scale(1.01); box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.15); }

/* 双面板布局 */
.upload-panels {
  display: flex; height: 240px;
}
.upload-pane {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24px; cursor: pointer; transition: all var(--duration-normal) var(--ease-out);
}
.upload-pane:hover { background: var(--surface-2); }
.upload-pane:hover .magnet-icon { transform: translateY(-4px) scale(1.08); color: var(--primary-hover); }
.pane-divider { width: 1px; background: var(--hairline); transition: background var(--duration-normal) var(--ease-out); }

.magnet-icon { color: var(--primary); margin-bottom: 12px; transition: all var(--duration-normal) var(--ease-spring); }
.magnet-title { font-size: 16px; font-weight: 500; color: var(--ink); margin-bottom: 4px; }
.magnet-desc { font-size: 13px; color: var(--ink-subtle); transition: color var(--duration-fast) var(--ease-out); }
.upload-pane:hover .magnet-desc { color: var(--ink-muted); }

/* URL 输入框 */
.url-input-box {
  display: flex; margin-top: 14px; border-bottom: 1px solid var(--hairline);
  transition: border-color 0.15s; width: 100%; max-width: 220px;
}
.url-input-box:focus-within { border-color: var(--primary); }
.url-input-box input {
  background: transparent; border: none; outline: none; color: var(--ink);
  padding: 8px 4px; width: 100%; font-size: 14px;
}
.url-input-box input::placeholder { color: var(--ink-tertiary); }
.url-go-btn {
  background: transparent; border: none; color: var(--primary); cursor: pointer;
  padding: 0 8px; opacity: 0.7; transition: opacity 0.15s;
}
.url-go-btn:hover { opacity: 1; }

/* 处理中状态 */
.magnet-content.busy {
  height: 240px; width: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--surface-1); position: relative;
}
.busy-text { margin-top: 12px; color: var(--primary); font-size: 14px; font-weight: 500; }
.busy-file { max-width: 80%; margin-top: 6px; color: var(--ink-subtle); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.upload-progress { width: min(320px, 72%); height: 4px; margin-top: 16px; background: var(--surface-3); overflow: hidden; border-radius: 2px; }
.upload-progress span { display: block; height: 100%; background: var(--primary); transition: width 0.25s ease; border-radius: 2px; }
.busy-stat { max-width: 82%; margin-top: 8px; color: var(--ink-subtle); font-size: 12px; text-align: center; }
.busy-warning { max-width: 82%; margin-top: 6px; color: var(--semantic-error); font-size: 12px; text-align: center; }
.busy-actions { margin-top: 16px; }
.busy-actions button { border: 1px solid var(--hairline); border-radius: 8px; background: transparent; color: var(--ink-subtle); padding: 8px 14px; font-size: 14px; cursor: pointer; transition: border-color 0.15s, color 0.15s; }
.busy-actions button:hover { border-color: var(--semantic-error); color: var(--semantic-error); }

.upload-resume {
  margin-top: 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 10px;
  padding: 12px 16px; background: var(--surface-1); border: 1px solid var(--hairline);
  border-left: 2px solid var(--primary); color: var(--ink-subtle); font-size: 14px; text-align: left;
  border-radius: 8px;
}
.upload-resume button { border: 1px solid var(--hairline); border-radius: 8px; background: transparent; color: var(--primary); padding: 6px 12px; cursor: pointer; font-size: 14px; }
.upload-resume button:hover { border-color: var(--primary); background: rgba(94, 106, 210, 0.08); }

.notification-bar { margin-top: 2rem; display: inline-block; background: var(--surface-2); color: var(--ink); padding: 10px 24px; font-weight: 500; border-radius: 8px; border: 1px solid var(--hairline); font-size: 14px; animation: toastEnter var(--duration-slow) var(--ease-spring); }
.notification-bar.error { background: rgba(229, 72, 77, 0.1); color: var(--semantic-error); border-color: rgba(229, 72, 77, 0.3); cursor: pointer; }

.quantum-loader { width: 40px; height: 40px; border: 3px solid var(--surface-3); border-top-color: var(--primary); border-radius: 50%; animation: spin 1s var(--ease-in-out) infinite; margin-bottom: 1rem; }
.quantum-loader.small { width: 24px; height: 24px; margin: 0 auto; }

/* Workspace — Linear card grid */
.workspace-section { opacity: 0; animation: fadeSlideUp 0.6s var(--ease-out) 0.6s forwards; }
.section-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 24px; border-bottom: 1px solid var(--hairline); padding-bottom: 12px; }
.library-title { display: flex; align-items: center; gap: 12px; }
.section-header h3 { font-size: 20px; font-weight: 600; letter-spacing: -0.2px; color: var(--ink); }
.count-chip { background: var(--surface-2); padding: 2px 10px; border-radius: 9999px; font-size: 12px; color: var(--ink-muted); }
.library-search { width: min(320px, 42vw); display: flex; align-items: center; gap: 9px; padding: 8px 12px; border: 1px solid var(--hairline); background: var(--surface-1); color: var(--ink-subtle); border-radius: 8px; transition: all var(--duration-normal) var(--ease-out); }
.library-search:focus-within { border-color: var(--primary); color: var(--primary); box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.1); }
.library-search input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 14px; }
.library-empty { padding: 52px 20px; text-align: center; color: var(--ink-subtle); border: 1px dashed var(--hairline); border-radius: 12px; }
.library-empty button { margin-top: 12px; border: 0; background: transparent; color: var(--primary); cursor: pointer; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.project-card { background: var(--surface-1); border-radius: 12px; border: 1px solid var(--hairline); overflow: hidden; transition: all var(--duration-normal) var(--ease-out); position: relative; }
.project-card:hover { border-color: var(--hairline-strong); transform: translateY(-2px); box-shadow: 0 8px 24px -8px rgba(0, 0, 0, 0.25); }
.card-meta { display: flex; gap: 16px; padding: 24px; align-items: center; border-bottom: 1px solid var(--hairline); }
.meta-icon { width: 48px; height: 48px; background: rgba(94, 106, 210, 0.08); border: 1px solid var(--hairline); border-radius: 8px; display: flex; align-items: center; justify-content: center; color: var(--primary); flex-shrink: 0; transition: all var(--duration-normal) var(--ease-spring); }
.project-card:hover .meta-icon { transform: scale(1.05); }
.filename-mask { font-size: 16px; font-weight: 500; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 180px; }
.meta-tags { display: flex; gap: 10px; font-size: 12px; margin-top: 4px; }
.time-tag { color: var(--ink-subtle); }
.status-indicator { font-weight: 500; padding: 2px 8px; border-radius: 9999px; font-size: 12px; }
.status-indicator.completed { color: var(--semantic-success); background: rgba(39, 166, 68, 0.1); }
.status-indicator.processing { color: var(--primary); background: rgba(94, 106, 210, 0.1); animation: pulse-primary 1.5s infinite alternate; }
.status-indicator.failed { color: var(--semantic-error); background: rgba(229, 72, 77, 0.1); }
.status-indicator.unknown { color: var(--ink-subtle); background: var(--surface-2); }

.action-dock { display: grid; grid-template-columns: 1fr 1fr 1.5fr; gap: 8px; padding: 12px; }
.dock-item { position: relative; border: 1px solid var(--hairline); background: var(--surface-2); border-radius: 8px; padding: 14px; display: flex; align-items: center; justify-content: center; gap: 8px; cursor: pointer; transition: all var(--duration-normal) var(--ease-out); color: var(--ink-subtle); font-size: 13px; overflow: hidden; }
.dock-item:hover:not(:disabled) { color: var(--primary); border-color: var(--primary); transform: translateY(-1px); }
.dock-item:active:not(:disabled) { transform: translateY(0) scale(0.97); }
.dock-item:disabled { opacity: 0.3; cursor: not-allowed; }
.dock-item.ai-core { border-color: var(--primary); color: var(--primary); }
.dock-item.ai-core .label-group { display: flex; flex-direction: column; align-items: flex-start; z-index: 1; }
.dock-item.ai-core .item-sub { font-size: 12px; color: var(--primary); opacity: 0.8; }
.dock-item.ai-core:hover:not(:disabled) { background: var(--primary); color: var(--on-primary); transform: translateY(-1px); }
.dock-item.ai-core:active:not(:disabled) { transform: translateY(0) scale(0.97); }
.dock-item.ai-core:hover:not(:disabled) .item-sub { color: var(--on-primary); }

/* Sidebar — Linear panel */
.sidebar-backdrop { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.6); z-index: 998; opacity: 0; transition: opacity var(--duration-normal) var(--ease-out); }
.sidebar-backdrop + .sidebar-panel.is-open ~ .sidebar-backdrop, .sidebar-backdrop { opacity: 1; }
.sidebar-panel { position: fixed; top: 0; right: -920px; width: 880px; max-width: calc(100vw - 24px); height: 100%; background: var(--surface-1); border-left: 1px solid var(--hairline); z-index: 999; transition: right var(--duration-slow) var(--ease-out), box-shadow var(--duration-slow) var(--ease-out); display: flex; flex-direction: column; }
.sidebar-panel.is-open { right: 0; box-shadow: -16px 0 48px -12px rgba(0, 0, 0, 0.4); }
.sidebar-header { padding: 20px 24px; border-bottom: 1px solid var(--hairline); display: flex; justify-content: space-between; align-items: center; }
.sidebar-title { font-size: 20px; font-weight: 600; color: var(--ink); display: flex; align-items: center; gap: 10px; letter-spacing: -0.2px; }
.icon { color: var(--primary); display: flex; align-items: center; }
.close-btn { width: 36px; height: 36px; background: none; border: none; color: var(--ink-subtle); cursor: pointer; transition: all var(--duration-normal) var(--ease-out); font-size: 1.35rem; border-radius: 8px; }
.close-btn:hover { color: var(--ink); background: var(--surface-2); }
.close-btn:active { transform: scale(0.9); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 24px; }
.loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; color: var(--ink-subtle); gap: 16px; }
.markdown-content, .text-content { line-height: 1.7; color: var(--ink); font-size: 15px; }
.text-content pre { white-space: pre-wrap; background: var(--canvas); padding: 16px; border-radius: 8px; border: 1px solid var(--hairline); color: var(--ink-muted); font-size: 13px; }
.text-meta { margin-bottom: 10px; color: var(--ink-subtle); font-size: 13px; }
.markdown-content h1, .markdown-content h2, .markdown-content h3 { color: var(--ink); margin-top: 1.5em; margin-bottom: 0.5em; font-weight: 600; }
.markdown-content h1 { border-bottom: 1px solid var(--hairline); padding-bottom: 10px; }
.markdown-content ul { padding-left: 20px; }
.markdown-content li { margin-bottom: 8px; color: var(--ink-muted); }
.markdown-content strong { color: var(--primary); font-weight: 600; }
.markdown-content p { margin-bottom: 1em; }
.markdown-content a[href^="#video-t="] { display: inline-block; padding: 1px 6px; border: 1px solid rgba(94, 106, 210, 0.4); color: var(--primary); text-decoration: none; border-radius: 4px; font-size: 13px; }
.markdown-content a[href^="#video-t="]:hover { background: var(--primary); color: var(--on-primary); border-color: var(--primary); }
.result-actions { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 18px; }
.result-actions button { border: 1px solid var(--hairline); background: transparent; color: var(--ink-subtle); padding: 8px 12px; cursor: pointer; border-radius: 8px; font-size: 14px; transition: all var(--duration-normal) var(--ease-out); }
.result-actions button:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); transform: translateY(-1px); }
.result-actions button:active:not(:disabled) { transform: translateY(0) scale(0.97); }
.result-actions button:disabled { opacity: 0.4; cursor: not-allowed; }

/* Agent workspace */
.video-evidence { margin: -24px -24px 24px; background: var(--canvas); border-bottom: 1px solid var(--hairline); }
.video-evidence video { display: block; width: 100%; max-height: 420px; aspect-ratio: 16 / 9; background: var(--canvas); object-fit: contain; }
.video-evidence p, .video-evidence-loading { padding: 10px 24px; color: var(--ink-subtle); font-size: 13px; }
.video-evidence-loading { min-height: 110px; display: grid; place-items: center; }
.video-evidence-error { min-height: 110px; padding: 18px 24px; display: flex; align-items: center; justify-content: center; gap: 12px; color: var(--semantic-error); }
.video-evidence-error button { border: 1px solid var(--semantic-error); background: transparent; color: var(--semantic-error); padding: 6px 10px; border-radius: 8px; cursor: pointer; }
.agent-composer { display: flex; flex-direction: column; gap: 16px; }
.agent-caption { color: var(--ink-subtle); line-height: 1.7; font-size: 14px; }
.inline-error { padding: 11px 12px; border-left: 2px solid var(--semantic-error); background: rgba(229, 72, 77, 0.08); color: var(--semantic-error); line-height: 1.5; border-radius: 0 8px 8px 0; }
.agent-composer textarea, .follow-up-box textarea {
  width: 100%; min-height: 130px; resize: vertical; background: var(--surface-2); color: var(--ink);
  border: 1px solid var(--hairline); border-radius: 8px; padding: 12px; line-height: 1.6; outline: none; font-size: 14px;
}
.agent-composer textarea:focus, .follow-up-box textarea:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.1); }
.field-counter { color: var(--ink-subtle); font-size: 12px; text-align: right; }
.goal-presets { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.goal-presets button, .feedback-row button {
  border: 1px solid var(--hairline); border-radius: 8px; background: transparent; color: var(--ink-subtle);
  padding: 8px 12px; cursor: pointer; font-size: 14px; transition: border-color 0.15s, color 0.15s;
}
.goal-presets button { min-height: 82px; padding: 12px; text-align: left; }
.goal-presets strong, .goal-presets span { display: block; }
.goal-presets strong { margin-bottom: 6px; color: var(--ink); font-weight: 500; }
.goal-presets span { font-size: 12px; line-height: 1.45; color: var(--ink-subtle); }
.goal-presets button:hover, .goal-presets button.active, .feedback-row button:hover, .feedback-row button.active {
  color: var(--primary); border-color: var(--primary); background: rgba(94, 106, 210, 0.08);
}
.goal-presets button:hover strong, .goal-presets button.active strong { color: var(--primary); }
.agent-run-btn {
  border: 0; border-radius: 8px; padding: 12px 18px; background: var(--primary); color: var(--on-primary);
  font-weight: 500; cursor: pointer; font-size: 14px; transition: all var(--duration-normal) var(--ease-out);
}
.agent-run-btn:hover:not(:disabled) { background: var(--primary-hover); transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(94, 106, 210, 0.4); }
.agent-run-btn:active:not(:disabled) { transform: translateY(0) scale(0.98); }
.agent-run-btn:disabled, .follow-up-box button:disabled { opacity: 0.4; cursor: not-allowed; }
.evidence-search { margin: 18px 0 24px; }
.evidence-search-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.evidence-search-form input {
  min-width: 0; border: 1px solid var(--hairline); border-radius: 8px; background: var(--surface-2);
  color: var(--ink); padding: 8px 12px; outline: none; font-size: 14px;
}
.evidence-search-form input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.1); }
.evidence-search-form button, .evidence-search-results button {
  border: 1px solid var(--hairline); border-radius: 8px; background: transparent;
  color: var(--ink-subtle); padding: 8px 12px; cursor: pointer; font-size: 14px;
  transition: all var(--duration-normal) var(--ease-out);
}
.evidence-search-form button:hover, .evidence-search-results button:hover { border-color: var(--primary); color: var(--primary); transform: translateY(-1px); }
.evidence-search-form button:active, .evidence-search-results button:active { transform: translateY(0) scale(0.97); }
.evidence-search-form button:disabled { opacity: 0.4; cursor: not-allowed; }
.evidence-search-error { margin-top: 8px; color: var(--semantic-error); font-size: 13px; }
.evidence-search-results { display: grid; gap: 6px; margin-top: 8px; }
.evidence-search-results button { display: grid; grid-template-columns: 58px 70px minmax(0, 1fr); gap: 10px; text-align: left; }
.evidence-search-results strong { color: var(--primary); }
.evidence-search-results small { color: var(--ink-subtle); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-search-results span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); }
.agent-running { display: flex; flex-direction: column; gap: 20px; }
.agent-running .loading-state { min-height: 210px; height: auto; }
.loading-state p { max-width: 34rem; text-align: center; }
.stream-offline { color: var(--semantic-error); font-size: 13px; }
.loading-hint { color: var(--ink-subtle); font-size: 13px; }
.agent-inspector { margin-top: 24px; border-top: 1px solid var(--hairline); padding-top: 16px; }
.agent-inspector summary { color: var(--ink-subtle); cursor: pointer; font-weight: 500; padding: 8px 0; font-size: 14px; }
.agent-inspector summary:hover { color: var(--primary); }
.agent-inspector-content { padding-top: 14px; }
.agent-meta-block { margin-bottom: 16px; padding: 14px; background: var(--surface-2); border-left: 2px solid var(--primary); border-radius: 0 8px 8px 0; }
.meta-label { display: block; color: var(--primary); font-size: 12px; font-weight: 500; margin-bottom: 10px; }
.agent-meta-block ol { padding-left: 20px; color: var(--ink-muted); }
.agent-meta-block li { margin: 7px 0; font-size: 14px; }
.plan-editor { display: grid; gap: 8px; margin-top: 12px; }
.plan-editor-row { display: grid; grid-template-columns: minmax(0, 1fr) 34px; gap: 8px; }
.plan-editor input { min-width: 0; border: 1px solid var(--hairline); background: var(--surface-2); color: var(--ink); padding: 8px 10px; border-radius: 8px; font-size: 14px; outline: none; }
.plan-editor input:focus { border-color: var(--primary); }
.plan-editor button, .plan-edit-trigger { border: 1px solid var(--hairline); background: transparent; color: var(--ink-subtle); padding: 7px 10px; cursor: pointer; border-radius: 8px; font-size: 14px; }
.plan-editor button:hover, .plan-edit-trigger:hover { color: var(--primary); border-color: var(--primary); }
.plan-editor-actions { display: flex; justify-content: flex-end; gap: 8px; }
.plan-edit-trigger { margin-top: 8px; }
.stage-list { display: flex; flex-wrap: wrap; gap: 8px; }
.stage-list span, .quality-row span {
  border: 1px solid var(--hairline); border-radius: 9999px; padding: 4px 10px; color: var(--ink-subtle); font-size: 12px;
}
.quality-row { display: flex; flex-wrap: wrap; gap: 8px; }
.follow-up-box { display: grid; grid-template-columns: 1fr auto; gap: 10px; margin-top: 24px; }
.follow-up-box textarea { min-height: 76px; }
.follow-up-box button {
  align-self: stretch; min-width: 76px; border: 1px solid var(--primary); border-radius: 8px;
  background: rgba(94, 106, 210, 0.08); color: var(--primary); cursor: pointer; font-size: 14px;
}
.feedback-row { display: flex; align-items: center; gap: 8px; margin-top: 18px; color: var(--ink-subtle); font-size: 14px; }

/* 登录框 — Linear modal */
.auth-backdrop { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.7); z-index: 2000; display: flex; justify-content: center; align-items: center; backdrop-filter: blur(4px); }
.auth-panel { width: 400px; max-width: 90vw; background: var(--surface-1); border: 1px solid var(--hairline); border-radius: 12px; display: flex; flex-direction: column; animation: modalEnter var(--duration-slow) var(--ease-spring) forwards; }
.auth-header { padding: 20px 24px; border-bottom: 1px solid var(--hairline); display: flex; justify-content: space-between; align-items: center; }
.auth-title { font-size: 20px; color: var(--ink); font-weight: 600; letter-spacing: -0.2px; }
.auth-body { padding: 24px; }
.input-group { margin-bottom: 20px; }
.input-group label { display: block; color: var(--ink-subtle); font-size: 13px; margin-bottom: 8px; font-weight: 500; }
.input-group input { width: 100%; background: var(--surface-2); border: 1px solid var(--hairline); border-radius: 8px; padding: 8px 12px; color: var(--ink); font-size: 16px; outline: none; transition: all var(--duration-normal) var(--ease-out); }
.input-group input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(94, 106, 210, 0.1); }
.cyber-btn { width: 100%; background: var(--primary); color: var(--on-primary); border: none; padding: 12px; font-weight: 500; font-size: 14px; cursor: pointer; transition: all var(--duration-normal) var(--ease-out); border-radius: 8px; margin-bottom: 20px; }
.cyber-btn:hover:not(:disabled) { background: var(--primary-hover); transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(94, 106, 210, 0.4); }
.cyber-btn:active:not(:disabled) { transform: translateY(0) scale(0.98); }
.cyber-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.auth-toggle { text-align: center; font-size: 14px; color: var(--ink-subtle); }
.toggle-link { background: none; border: none; color: var(--primary); cursor: pointer; font-weight: 500; margin-left: 5px; }
.toggle-link:hover { color: var(--primary-hover); }
.auth-msg { margin-top: 15px; text-align: center; font-size: 13px; color: var(--primary); }
.auth-msg.error { color: var(--semantic-error); }

/* 删除按钮 */
.delete-btn {
  position: absolute; top: 10px; right: 10px; background: transparent; border: none;
  width: 36px; height: 36px; color: var(--ink-subtle); cursor: pointer; opacity: 0; transition: color 0.15s ease, opacity 0.15s ease; z-index: 10;
  border-radius: 8px;
}
.project-card:hover .delete-btn, .delete-btn:focus-visible { opacity: 1; }
.delete-btn:hover:not(:disabled) { color: var(--semantic-error); }
.delete-btn:disabled { cursor: progress; opacity: 0.5; }
.url-input-box input:disabled, .url-go-btn:disabled { opacity: 0.5; cursor: not-allowed; }

@media (max-width: 720px) {
  .navbar { height: 48px; }
  .nav-content { padding: 0 16px; }
  .brand-name { font-size: 15px; }
  .status-pill { display: none; }
  .auth-btn { padding: 6px 10px; font-size: 13px; }
  .main-container { padding: 24px 16px; }
  .hero-section { margin-bottom: 48px; }
  .slogan-main { font-size: 32px; letter-spacing: -1px; }
  .slogan-sub { margin-bottom: 24px; font-size: 16px; white-space: normal; }
  .feature-grid { grid-template-columns: 1fr; margin-top: 40px; }
  .upload-magnet { border-radius: 8px; }
  .upload-panels { flex-direction: column; height: auto; }
  .upload-pane { min-height: 180px; }
  .pane-divider { width: 100%; height: 1px; }
  .card-grid { grid-template-columns: 1fr; }
  .section-header { align-items: stretch; flex-direction: column; }
  .library-search { width: 100%; }
  .action-dock { grid-template-columns: 1fr; }
  .filename-mask { max-width: 55vw; }
  .sidebar-panel { width: 100%; max-width: 100vw; right: -100vw; }
  .sidebar-header { padding: 16px; }
  .sidebar-title { font-size: 16px; max-width: calc(100vw - 60px); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .sidebar-body { padding: 16px; }
  .video-evidence { margin: -16px -16px 18px; }
  .video-evidence p, .video-evidence-loading { padding-left: 16px; padding-right: 16px; }
  .goal-presets { grid-template-columns: 1fr; }
  .goal-presets button { min-height: 68px; }
  .result-actions { justify-content: stretch; }
  .result-actions button { flex: 1; padding: 9px 4px; font-size: 12px; }
  .evidence-search-form { grid-template-columns: 1fr; }
  .evidence-search-results button { grid-template-columns: 56px minmax(0, 1fr); }
  .evidence-search-results small { display: none; }
  .follow-up-box { grid-template-columns: 1fr; }
  .follow-up-box button { min-height: 44px; }
  .delete-btn { opacity: 1; }
  .upload-resume { flex-direction: column; align-items: stretch; text-align: center; }
  .upload-resume button { min-height: 40px; }
  .busy-stat, .busy-warning { max-width: 92%; }
  /*
    移动端侧栏铺满整屏，背景还能滚动会非常割裂，这里锁住。
    只在窄屏生效：移动端滚动条是浮层式的，隐藏溢出不会让内容横向跳动；
    桌面端滚动条占布局宽度，锁滚动会带来 15px 左右的位移，所以不动。
  */
  body.overlay-open { overflow: hidden; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes slideUpFade { from { opacity: 0; transform: translateY(40px); } to { opacity: 1; transform: translateY(0); } }
@keyframes fadeSlideDown { from { opacity: 0; transform: translateY(-12px); } to { opacity: 1; transform: translateY(0); } }
@keyframes fadeSlideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
@keyframes modalEnter { from { opacity: 0; transform: scale(0.96) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }
@keyframes toastEnter { from { opacity: 0; transform: translateY(8px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes pulse-primary { 0% { opacity: 0.5; } 100% { opacity: 1; } }
</style>
