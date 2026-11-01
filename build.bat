@echo off
where java >nul 2>&1 || (echo JDK 25 nahi mila. Install karo. & pause & exit /b 1)
where gradle >nul 2>&1 || (echo Gradle 9.5.1 nahi mila. Install karke PATH me daalo. & pause & exit /b 1)
gradle build
echo.
echo JAR yahan milega: build\libs\friendshield-26.2-1.0.0.jar
pause
