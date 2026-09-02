<template>
  <section class="hero-section">
    <span class="hero-eyebrow">AI-POWERED VIDEO ANALYSIS</span>
    <h1 class="slogan-main">理解视频，不止于看</h1>
    <p class="slogan-sub">上传视频或粘贴链接，AI Agent 按你的目标生成带时间戳证据的结构化产物</p>

    <div class="upload-wrapper">
      <input type="file" id="file-input" @change="s.handleFileChange" accept="video/*" hidden />

      <div
        class="upload-magnet"
        :class="{ 'processing': s.uploading.value, 'is-dragover': s.isDragOver.value }"
        @dragenter.prevent="s.handleDragEnter"
        @dragover.prevent="s.isDragOver.value = true"
        @dragleave.prevent="s.handleDragLeave"
        @drop.prevent="s.handleDrop"
      >
        <div class="upload-panels" v-if="!s.uploading.value">
          <label for="file-input" class="upload-pane pane-local">
            <div class="magnet-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
            </div>
            <span class="magnet-title">本地文件</span>
            <span class="magnet-desc">{{ s.isDragOver.value ? '松手上传' : '点击或拖拽视频文件到此处' }}</span>
          </label>

          <div class="pane-divider"></div>

          <div class="upload-pane pane-url">
            <div class="magnet-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"></path></svg>
            </div>
            <span class="magnet-title">在线链接</span>
            <span class="magnet-desc">支持 B站 / YouTube / 抖音</span>

            <div class="url-input-box" @click.stop>
              <input
                v-model="s.videoUrl.value"
                type="text"
                inputmode="url"
                autocomplete="off"
                spellcheck="false"
                placeholder="粘贴视频链接..."
                aria-label="视频链接"
                :disabled="s.uploading.value"
                @keyup.enter="s.handleUrlUpload"
              />
              <button class="url-go-btn" :disabled="s.uploading.value" @click="s.handleUrlUpload" aria-label="解析视频链接">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
              </button>
            </div>
          </div>
        </div>

        <div class="magnet-content busy" v-else>
          <div class="quantum-loader"></div>
          <span class="busy-text">{{ s.uploadProgress.value.label }}</span>
          <span v-if="s.uploadProgress.value.filename" class="busy-file">{{ s.uploadProgress.value.filename }}</span>
          <div
            v-if="s.uploadProgress.value.percent !== null"
            class="upload-progress"
            role="progressbar"
            :aria-valuenow="s.uploadProgress.value.percent"
          >
            <span :style="{ width: `${s.uploadProgress.value.percent}%` }"></span>
          </div>
          <span v-if="s.uploadProgress.value.detail" class="busy-stat">{{ s.uploadProgress.value.detail }}</span>
          <span v-if="s.uploadProgress.value.warning" class="busy-warning">{{ s.uploadProgress.value.warning }}</span>
          <div v-if="s.uploadAbort.value" class="busy-actions">
            <button type="button" @click="s.cancelUpload">取消上传</button>
          </div>
        </div>
      </div>

      <div v-if="s.resumableFile.value && !s.uploading.value" class="upload-resume">
        <span>{{ s.resumeHint.value }}</span>
        <button type="button" @click="s.resumeUpload">继续上传</button>
        <button type="button" @click="s.discardResumableUpload">重新开始</button>
      </div>
    </div>

    <transition name="toast-pop">
      <div
        v-if="s.message.value"
        class="notification-bar"
        :class="{ 'error': s.messageIsError.value }"
        @click="s.dismissMessage"
      >
        {{ s.message.value }}
      </div>
    </transition>

    <div class="feature-grid">
      <div class="feature-card">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
        </div>
        <h3>智能转写</h3>
        <p>高精度语音识别，自动分段，输出可编辑的全文文本</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" ry="2"></rect><rect x="9" y="9" width="6" height="6"></rect><line x1="9" y1="1" x2="9" y2="4"></line><line x1="15" y1="1" x2="15" y2="4"></line><line x1="9" y1="20" x2="9" y2="23"></line><line x1="15" y1="20" x2="15" y2="23"></line><line x1="20" y1="9" x2="23" y2="9"></line><line x1="20" y1="14" x2="23" y2="14"></line></svg>
        </div>
        <h3>目标驱动分析</h3>
        <p>告诉 Agent 你想得到什么，它会按目标生成结构化产物并附带时间戳证据</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line><line x1="11" y1="8" x2="11" y2="14"></line><line x1="8" y1="11" x2="14" y2="11"></line></svg>
        </div>
        <h3>证据定位</h3>
        <p>点击分析结果中的时间戳，直接跳转到视频对应画面</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { inject } from 'vue'
const s = inject('appState')
</script>
