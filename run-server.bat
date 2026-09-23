@echo off
echo ==========================================
echo    KHOI CHAY PBL4 CHAT SERVER (PORT 8888)
echo ==========================================
"D:\IntelliJ IDEA Community Edition 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" compile exec:java -Dexec.mainClass="com.pbl4.app.network.ChatServer"
pause
