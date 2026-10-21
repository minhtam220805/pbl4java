@echo off
cd /d "%~dp0"
echo ==========================================
echo    KHOI CHAY PBL4 JAVAFX CLIENT
echo ==========================================
set "MVN_BIN=D:\IntelliJ IDEA Community Edition 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
if not exist "%MVN_BIN%" (
    set "MVN_BIN=mvn"
)
call "%MVN_BIN%" javafx:run
pause

