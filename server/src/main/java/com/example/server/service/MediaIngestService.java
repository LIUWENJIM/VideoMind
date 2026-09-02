package com.example.server.service;

import com.example.server.entity.MediaFile;
import com.example.server.utils.MinioUtils;
import com.example.server.utils.YtDlpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class MediaIngestService {

    private static final Logger log = LoggerFactory.getLogger(MediaIngestService.class);

    private final MinioUtils minioUtils;
    private final YtDlpUtils ytDlpUtils;
    private final MediaService mediaService;

    public MediaIngestService(MinioUtils minioUtils,
                              YtDlpUtils ytDlpUtils,
                              MediaService mediaService) {
        this.minioUtils = minioUtils;
        this.ytDlpUtils = ytDlpUtils;
        this.mediaService = mediaService;
    }

    public MediaFile ingestFile(MultipartFile file, Long userId) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");

        String filename = mediaService.normalizeVideoFilename(file.getOriginalFilename());
        String md5 = mediaService.calculateMd5(file);
        String fileUrl = minioUtils.uploadFile(file);

        // 提取封面：保存到临时文件后用 FFmpeg 取帧
        String coverUrl = null;
        Path tempVideo = null;
        File coverFile = null;
        try {
            tempVideo = Files.createTempFile("ingest-", "-" + filename);
            file.transferTo(tempVideo.toFile());
            coverFile = ytDlpUtils.extractCoverFromVideo(tempVideo.toFile());
            if (coverFile != null) {
                coverUrl = minioUtils.uploadLocalFile(coverFile, "cover.jpg", "covers/");
            }
        } catch (Exception e) {
            log.warn("cover_extraction_failed_for_upload", e);
        } finally {
            Files.deleteIfExists(tempVideo);
            if (coverFile != null) Files.deleteIfExists(coverFile.toPath());
        }

        MediaFile media = mediaService.saveUploadedMedia(filename, fileUrl, userId, md5);
        if (coverUrl != null) {
            mediaService.updateCoverUrl(media.getId(), coverUrl);
        }
        return media;
    }

    public MediaFile ingestUrl(String url, Long userId) throws Exception {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("视频链接不能为空");

        // 先提取视频标题，用作显示名称
        String title = ytDlpUtils.extractTitle(url);

        File tempFile = null;
        File coverFile = null;
        try {
            tempFile = ytDlpUtils.downloadVideo(url);
            String md5 = mediaService.calculateMd5(tempFile);
            String fileUrl = minioUtils.uploadLocalFile(tempFile);
            // 优先用视频标题，降级到 "WEB_" + 临时文件名
            String displayName = (title != null && !title.isBlank())
                    ? title + ".mp4"
                    : "WEB_" + tempFile.getName();

            // 提取封面：优先用 yt-dlp 下载的缩略图，降级用 FFmpeg 取帧
            String coverUrl = null;
            coverFile = ytDlpUtils.findDownloadedThumbnail(tempFile);
            if (coverFile == null) {
                coverFile = ytDlpUtils.extractCoverFromVideo(tempFile);
            }
            if (coverFile != null) {
                coverUrl = minioUtils.uploadLocalFile(coverFile, "cover.jpg", "covers/");
            }

            MediaFile media = mediaService.saveUploadedMedia(displayName, fileUrl, userId, md5);
            if (coverUrl != null) {
                mediaService.updateCoverUrl(media.getId(), coverUrl);
            }
            return media;
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("temporary_video_cleanup_failed path={}", tempFile.getAbsolutePath());
            }
            if (coverFile != null && coverFile.exists() && !coverFile.delete()) {
                log.warn("temporary_cover_cleanup_failed path={}", coverFile.getAbsolutePath());
            }
        }
    }
}
