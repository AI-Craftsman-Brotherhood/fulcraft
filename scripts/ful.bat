@echo off
REM FUL CLI wrapper script for Windows
REM Usage: ful [options] [command]

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_DIR=%SCRIPT_DIR%..\app\build\libs"
set "JAR_PATH=%JAR_DIR%\ful.jar"

if not exist "%JAR_PATH%" (
    for %%F in ("%JAR_DIR%\ful-*.jar") do (
        set "JAR_PATH=%%F"
        goto :jar_found
    )
)

:jar_found
if not exist "%JAR_PATH%" (
    echo ❌ Error: ful.jar not found in %JAR_DIR% >&2
    echo    Run 'gradlew :app:shadowJar' to build the JAR first. >&2
    exit /b 1
)

REM Default JVM options (can be overridden via FUL_JAVA_OPTS)
if not defined FUL_JAVA_OPTS set "FUL_JAVA_OPTS="

java %FUL_JAVA_OPTS% -jar "%JAR_PATH%" %*
