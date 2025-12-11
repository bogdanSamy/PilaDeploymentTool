@echo off
echo Rebuilding portable app with proper JavaFX setup...

set JAVA_HOME=C:\jdk_21
set JAVAFX_SDK=C:\javafx-sdk-21
set OUTPUT=C:\LaPilaSiLaCiocan-Portable

cd C:\Intelij_Projects\LaPilaSiLaCiocan

REM Clean old
if exist "%OUTPUT%" rmdir /s /q "%OUTPUT%"

REM Rebuild
call mvn package -DskipTests

REM Create structure
mkdir "%OUTPUT%\app"
mkdir "%OUTPUT%\runtime\bin"
mkdir "%OUTPUT%\javafx"

REM Copy JAR
copy target\LaPilaSiLaCiocan-1.0-SNAPSHOT-uber.jar "%OUTPUT%\app\"

REM Copy JavaFX JARs

copy "%JAVAFX_SDK%\lib\*.jar" "%OUTPUT%\javafx\"

REM Copy JavaFX DLLs to runtime bin
copy "%JAVAFX_SDK%\bin\*.dll" "%OUTPUT%\runtime\bin\"

REM Copy minimal Java
xcopy /E /I /Q "%JAVA_HOME%\bin" "%OUTPUT%\runtime\bin"
xcopy /E /I /Q "%JAVA_HOME%\conf" "%OUTPUT%\runtime\conf"
xcopy /E /I /Q "%JAVA_HOME%\lib" "%OUTPUT%\runtime\lib"

REM Create launcher
(
    echo @echo off
    echo cd /d "%%~dp0"
    echo set PATH=%%CD%%\runtime\bin;%%PATH%%
    echo runtime\bin\javaw.exe -Djava.library.path=runtime\bin --module-path javafx --add-modules javafx.controls,javafx.fxml,javafx.graphics -jar app\LaPilaSiLaCiocan-1.0-SNAPSHOT-uber.jar
) > "%OUTPUT%\LaPilaSiLaCiocan.bat"

echo Done! Testing...
cd "%OUTPUT%"
call LaPilaSiLaCiocan.bat