@echo off
chcp 65001 nul
echo 正在安装 FFmpeg 和 Tesseract，请稍候...

winget install --id Gyan.FFmpeg -e --accept-source-agreements --accept-package-agreements
winget install --id UB-Mannheim.TesseractOCR -e --accept-source-agreements --accept-package-agreements

echo.
echo 安装完成！请关闭此窗口，重新打开 CMD 后运行：
echo   ffmpeg -version
echo   tesseract --version
echo 验证是否成功。
echo.
pause