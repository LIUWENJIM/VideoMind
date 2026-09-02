package com.example.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class YtDlpUtils {

    private static final Logger log = LoggerFactory.getLogger(YtDlpUtils.class);

    private final String ytDlpPath;
    private final String ffmpegDir;

    public YtDlpUtils(@Value("${tool.ytdlp.path}") String ytDlpPath,
                      @Value("${tool.ffmpeg.dir}") String ffmpegDir) {
        this.ytDlpPath = ytDlpPath;
        this.ffmpegDir = ffmpegDir;
    }

    /**
     * 使用 yt-dlp 提取视频标题（不下载视频本身）。
     * 失败时返回 null，调用方降级到默认文件名。
     */
    public String extractTitle(String url) {
        try {
            validatePublicHttpUrl(url);
            List<String> command = List.of(
                    ytDlpPath,
                    "--no-playlist",
                    "--print", "title",
                    "--no-download",
                    "--socket-timeout", "15",
                    url
            );
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start();
            String title;
            try (var reader = process.inputReader()) {
                title = reader.readLine();
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || title == null || title.isBlank()) {
                return null;
            }
            // 清理标题中不合法的文件名字符
            return title.trim()
                    .replaceAll("[\\\\/:*?\"<>|]", "")
                    .replaceAll("\\s+", " ")
                    .strip();
        } catch (Exception e) {
            log.warn("yt_dlp_title_extraction失败 url={}", url, e);
            return null;
        }
    }

    public File downloadVideo(String url) throws Exception {
        validatePublicHttpUrl(url);
        Path outputPath = Path.of(System.getProperty("java.io.tmpdir"), UUID.randomUUID() + ".mp4");
        Path logPath = Files.createTempFile("yt-dlp-", ".log");
        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);
        command.add("--no-playlist");
        command.add("--socket-timeout");
        command.add("30");
        command.add("--retries");
        command.add("3");
        command.add("--max-filesize");
        command.add("2048M");
        // Prefer the broadly supported H.264/AVC + AAC combination for imported
        // videos. Merely changing an AV1 file's container to MP4 does not make it
        // playable in Safari on every macOS and hardware combination.
        command.add("-f");
        command.add("bv*[vcodec^=avc1][ext=mp4]+ba[acodec^=mp4a][ext=m4a]/b[vcodec^=avc1][ext=mp4]/bv*[vcodec^=avc1]+ba[acodec^=mp4a]");
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("--recode-video");
        command.add("mp4");
        // 同时下载封面缩略图
        command.add("--write-thumbnail");
        command.add("--convert-thumbnails");
        command.add("jpg");
        if (ffmpegDir != null && !ffmpegDir.isBlank()) {
            command.add("--ffmpeg-location");
            command.add(ffmpegDir);
        }
        command.add("-o");
        command.add(outputPath.toString());
        command.add(url);

        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logPath.toFile())
                    .start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("视频链接下载超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputPath)) {
                String logs = Files.readString(logPath);
                throw new IllegalStateException("yt-dlp 下载失败: " + tail(logs, 2_000));
            }
            log.info("url_video_downloaded host={} bytes={}", URI.create(url).getHost(), Files.size(outputPath));
            return outputPath.toFile();
        } catch (Exception e) {
            Files.deleteIfExists(outputPath);
            throw e;
        } finally {
            Files.deleteIfExists(logPath);
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    /**
     * 查找 yt-dlp 下载到本地的封面缩略图文件。
     * yt-dlp 会把缩略图保存到与视频同目录、同名但扩展名为 .jpg/.webp/.png 的文件。
     */
    public File findDownloadedThumbnail(File videoFile) {
        if (videoFile == null) return null;
        Path parent = videoFile.toPath().getParent();
        String baseName = videoFile.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);
        for (String ext : List.of(".jpg", ".webp", ".png")) {
            Path candidate = parent.resolve(baseName + ext);
            if (Files.isRegularFile(candidate)) return candidate.toFile();
        }
        return null;
    }

    /**
     * 用 FFmpeg 从视频文件提取一帧作为封面。
     * 取视频 10% 处的帧，避免片头黑屏/标题卡。失败返回 null。
     */
    public File extractCoverFromVideo(File videoFile) {
        if (videoFile == null || !videoFile.exists()) return null;
        try {
            // 先获取视频时长
            Path durationLog = Files.createTempFile("ffmpeg-duration-", ".log");
            List<String> probeCmd = List.of(
                    resolveFfmpeg(), "-i", videoFile.getAbsolutePath(),
                    "-f", "null", "-"
            );
            Process probe = new ProcessBuilder(probeCmd)
                    .redirectErrorStream(true)
                    .redirectOutput(durationLog.toFile())
                    .start();
            String probeOutput = "";
            if (probe.waitFor(30, TimeUnit.SECONDS)) {
                probeOutput = Files.readString(durationLog);
            } else {
                probe.destroyForcibly();
            }
            Files.deleteIfExists(durationLog);

            // 解析时长
            double duration = 10.0; // 默认取第 10 秒
            var matcher = java.util.regex.Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+\\.\\d+)").matcher(probeOutput);
            if (matcher.find()) {
                int h = Integer.parseInt(matcher.group(1));
                int m = Integer.parseInt(matcher.group(2));
                double s = Double.parseDouble(matcher.group(3));
                duration = Math.max(1.0, (h * 3600 + m * 60 + s) * 0.1);
            }

            // 提取封面帧
            Path coverPath = Files.createTempFile("cover-", ".jpg");
            List<String> cmd = List.of(
                    resolveFfmpeg(), "-y",
                    "-ss", String.format("%.1f", duration),
                    "-i", videoFile.getAbsolutePath(),
                    "-vframes", "1",
                    "-q:v", "2",
                    coverPath.toString()
            );
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(Path.of(System.getProperty("java.io.tmpdir"), "ffmpeg-cover.log").toFile())
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Files.deleteIfExists(coverPath);
                return null;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(coverPath) || Files.size(coverPath) < 100) {
                Files.deleteIfExists(coverPath);
                return null;
            }
            return coverPath.toFile();
        } catch (Exception e) {
            log.warn("ffmpeg_cover_extraction_failed", e);
            return null;
        }
    }

    private String resolveFfmpeg() {
        if (ffmpegDir != null && !ffmpegDir.isBlank()) {
            return Path.of(ffmpegDir, "ffmpeg").toString();
        }
        return "ffmpeg";
    }

    private void validatePublicHttpUrl(String value) throws Exception {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅支持合法的公网 HTTP/HTTPS 视频链接");
        }
        InetAddress[] resolved = InetAddress.getAllByName(host);
        if (resolved.length == 0) {
            throw new IllegalArgumentException("无法解析视频链接的主机地址");
        }
        for (InetAddress address : resolved) {
            if (isDisallowedAddress(address)) {
                throw new IllegalArgumentException("不允许访问本机、内网或保留网段地址");
            }
        }
        // 注意：这里只是应用层的尽力校验（defense-in-depth）。yt-dlp 子进程会对 host
        // 重新做一次 DNS 解析并可能跟随 302 重定向，存在 DNS rebinding / 跳转到内网的
        // TOCTOU 风险，单靠应用层字符串/首次解析校验无法彻底封堵。生产环境必须叠加网络层
        // 出口管控（egress 白名单、独立网络命名空间或出口代理），才能真正杜绝 SSRF。
    }

    /**
     * 拦截不应从服务端访问的地址：回环、任意本地、链路本地（含云元数据端点
     * 169.254.169.254）、站点内网（RFC1918）、IPv6 ULA、运营商级 NAT、组播与保留网段。
     */
    private boolean isDisallowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()          // 0.0.0.0 / ::
                || address.isLoopbackAddress()   // 127.0.0.0/8 / ::1
                || address.isLinkLocalAddress()  // 169.254.0.0/16（含云元数据端点）/ fe80::/10
                || address.isSiteLocalAddress()  // 10/8、172.16/12、192.168/16
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0) return true;                                     // 0.0.0.0/8 “本网络”
            if (first == 100 && second >= 64 && second <= 127) return true;  // 100.64.0.0/10 运营商级 NAT
            if (first == 169 && second == 254) return true;                  // 169.254.0.0/16 兜底
            return first >= 240;                                             // 240.0.0.0/4 保留段
        }
        if (bytes.length == 16) {
            return (bytes[0] & 0xFE) == 0xFC;                                // fc00::/7 IPv6 唯一本地地址(ULA)
        }
        return false;
    }

    private String tail(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(value.length() - maxLength);
    }
}
