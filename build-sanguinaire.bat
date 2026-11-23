@echo off
rem Builds Sanguinaire and writes the full output to build.log (Claude reads this file).
cd /d "%~dp0"
echo Building Sanguinaire... (the first run downloads Forge and can take 10+ minutes)
call gradlew.bat build --console=plain > build.log 2>&1
set RESULT=%ERRORLEVEL%
echo EXIT CODE: %RESULT% >> build.log
echo.
powershell -NoProfile -Command "Get-Content build.log -Tail 30"
echo.
if %RESULT%==0 (echo BUILD SUCCESSFUL - the mod jar is in build\libs) else (echo BUILD FAILED - tell Claude to read build.log)
pause
