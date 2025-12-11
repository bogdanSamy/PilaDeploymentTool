@echo off
setlocal EnableDelayedExpansion

echo Rebuilding portable app with proper JavaFX setup...

REM --- CONFIGURARE ---
set "JAVA_HOME=C:\jdk_21"
set "JAVAFX_SDK=C:\javafx-sdk-21"
set "OUTPUT=C:\LaPilaSiLaCiocan-Portable"
set "PROJECT_DIR=C:\Intelij_Projects\LaPilaSiLaCiocan"
REM -------------------

REM Verificari initiale
if not exist "%JAVA_HOME%\bin\javaw.exe" (
    echo [EROARE] Nu am gasit Java in: %JAVA_HOME%
    echo Verifica daca folderul C:\jdk_21 exista si contine bin\javaw.exe
    pause
    exit /b 1
)

if not exist "%JAVAFX_SDK%" (
    echo [EROARE] Nu am gasit JavaFX SDK in: %JAVAFX_SDK%
    pause
    exit /b 1
)

cd /d "%PROJECT_DIR%"

REM Clean old
if exist "%OUTPUT%" (
    echo Stergere versiune veche...
    rmdir /s /q "%OUTPUT%"
)

REM Rebuild Maven
echo Building project...
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [EROARE] Maven build failed!
    pause
    exit /b 1
)

REM Create structure
echo Creating directories...
mkdir "%OUTPUT%\app"
mkdir "%OUTPUT%\runtime\bin"
mkdir "%OUTPUT%\runtime\conf"
mkdir "%OUTPUT%\runtime\lib"
mkdir "%OUTPUT%\javafx"

REM Copy JAR
echo Copying App JAR...
copy "target\LaPilaSiLaCiocan-1.0-SNAPSHOT-uber.jar" "%OUTPUT%\app\" >nul

REM Copy JavaFX JARs
echo Copying JavaFX libs...
copy "%JAVAFX_SDK%\lib\*.jar" "%OUTPUT%\javafx\" >nul

REM Copy JavaFX DLLs (crucial for runtime)
echo Copying JavaFX DLLs...
copy "%JAVAFX_SDK%\bin\*.dll" "%OUTPUT%\runtime\bin\" >nul

REM Copy Java Runtime using Robocopy (exclude errors 1-3 which are just success logs)
echo Copying Java Runtime...
robocopy "%JAVA_HOME%\bin" "%OUTPUT%\runtime\bin" /E /NFL /NDL /NJH /NJS
if %ERRORLEVEL% GEQ 8 (
    echo [EROARE] Robocopy a esuat la copierea BIN!
    pause
    exit /b 1
)

robocopy "%JAVA_HOME%\conf" "%OUTPUT%\runtime\conf" /E /NFL /NDL /NJH /NJS
robocopy "%JAVA_HOME%\lib" "%OUTPUT%\runtime\lib" /E /NFL /NDL /NJH /NJS

REM Create launcher
echo Creating launcher...
(
    echo @echo off
    echo cd /d "%%~dp0"
    echo set "PATH=%%CD%%\runtime\bin;%%PATH%%"
    echo start "LaPilaSiLaCiocan" "runtime\bin\javaw.exe" -Djava.library.path="runtime\bin" --module-path "javafx" --add-modules javafx.controls,javafx.fxml,javafx.graphics -jar "app\LaPilaSiLaCiocan-1.0-SNAPSHOT-uber.jar"
) > "%OUTPUT%\LaPilaSiLaCiocan.bat"

echo Done! Testing...
if exist "%OUTPUT%\runtime\bin\javaw.exe" (
    echo Java executable found successfully.
    cd /d "%OUTPUT%"
    call LaPilaSiLaCiocan.bat
) else (
    echo [FATAL] javaw.exe lipseste din folderul destinatie! Copierea a esuat.
    pause
)