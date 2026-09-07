@echo off
setlocal
set "GRADLE_VERSION=8.13"
set "DIST=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "INSTALL=%DIST%\gradle-%GRADLE_VERSION%"
if not exist "%INSTALL%\bin\gradle.bat" (
  if not exist "%DIST%" mkdir "%DIST%"
  if not exist "%DIST%\gradle-%GRADLE_VERSION%-bin.zip" powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST%\gradle-%GRADLE_VERSION%-bin.zip'"
  powershell -NoProfile -Command "Expand-Archive -Force '%DIST%\gradle-%GRADLE_VERSION%-bin.zip' '%DIST%'"
)
call "%INSTALL%\bin\gradle.bat" %*
