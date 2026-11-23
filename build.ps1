# PowerShell script để tự động build file APK app-debug.apk
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   ONE UI WIDGET (SAMSUNG GALAXY S26 / ONE UI) BUILD TOOL" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Kiểm tra môi trường Java
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd -and -not $env:JAVA_HOME) {
    Write-Host "[!] Không tìm thấy Java trong biến môi trường PATH hoặc JAVA_HOME." -ForegroundColor Yellow
    Write-Host "    Bạn có thể cài đặt nhanh JDK 17 bằng lệnh Windows sau:" -ForegroundColor White
    Write-Host "    winget install Microsoft.OpenJDK.17" -ForegroundColor Green
    Write-Host "    Hoặc mở trực tiếp dự án này bằng Android Studio Hedgehog / Iguana / Koala / Ladybug." -ForegroundColor White
    Write-Host "==========================================================" -ForegroundColor Cyan
    exit 1
}

# 2. Tải gradle-wrapper.jar nếu chưa có
$wrapperJar = Join-Path $PSScriptRoot "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "[+] Đang tải Gradle Wrapper Jar..." -ForegroundColor Cyan
    $wrapperUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar"
    try {
        Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperJar -UseBasicParsing
        Write-Host "[✓] Tải Gradle Wrapper Jar thành công!" -ForegroundColor Green
    } catch {
        Write-Host "[-] Không thể tự động tải wrapper jar. Dự án sẽ build tự động khi mở bằng Android Studio." -ForegroundColor Yellow
    }
}

# 3. Chạy lệnh assembleDebug
Write-Host "[+] Bắt đầu biên dịch ứng dụng: ./gradlew assembleDebug ..." -ForegroundColor Cyan
& ".\gradlew.bat" assembleDebug

if ($LASTEXITCODE -eq 0) {
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apkPath) {
        Write-Host ""
        Write-Host "==========================================================" -ForegroundColor Green
        Write-Host " [✓] BUILD THÀNH CÔNG! File APK đã sẵn sàng tại:" -ForegroundColor Green
        Write-Host "     $((Get-Item $apkPath).FullName)" -ForegroundColor White
        Write-Host "==========================================================" -ForegroundColor Green
    }
} else {
    Write-Host ""
    Write-Host "[-] Quá trình build thất bại. Vui lòng kiểm tra Android SDK hoặc mở dự án trong Android Studio." -ForegroundColor Red
}
