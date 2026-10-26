@echo off
setlocal
cd /d "%~dp0..\.."
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "tools\demo\node_modules\firebase-admin" (
  call npm.cmd ci --prefix tools/demo --no-audit --no-fund
  if errorlevel 1 exit /b 1
)
call firebase.cmd emulators:exec --project demo-stefanykhub --only auth,firestore,storage --ui "node tools/demo/serve.mjs"
