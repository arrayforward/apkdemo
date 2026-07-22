@echo off
REM Extract all RGB565 .h asset files from goldieos settings + AItalk apps,
REM convert them to PNG, and copy into Android app/src/main/assets/convai/img/.

setlocal
set ROOT=D:\vit\apkdemo
set SETTINGS=%ROOT%\examples\goldieos\apps\settings\assets
set AITALK=%ROOT%\examples\goldieos\apps\AItalk\assets
set OUT=%ROOT%\GoldieSettingsAndroid\app\src\main\assets\convai\img
set PYTHON=node

echo === Output dir: %OUT%
if not exist "%OUT%" mkdir "%OUT%"

echo === Converting settings assets...
%PYTHON% %ROOT%\GoldieSettingsAndroid\tools\img2png.js --all "%SETTINGS%" "%OUT%"
if errorlevel 1 goto :err

echo === Converting AItalk assets (eye / emotion animations)...
%PYTHON% %ROOT%\GoldieSettingsAndroid\tools\img2png.js --all "%AITALK%" "%OUT%"
if errorlevel 1 goto :err

echo === Done.
dir /b "%OUT%" | find /c "png"
goto :eof

:err
echo *** Failed.
exit /b 1
