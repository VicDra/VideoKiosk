@echo off
cd /d C:\ClaudeCodes\VideoKiosk\signaling-server
start "SignalingServer" /B node server.js
timeout /T 3 /NOBREAK >nul
cd /d C:\ClaudeCodes\VideoKiosk
node cdp-test.js
pause
