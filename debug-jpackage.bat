@echo off
echo Testing jpackage with minimal options...

set JAVA_HOME=C:\jdk_21

echo Test 1: Check jpackage version
"%JAVA_HOME%\bin\jpackage" --version
echo.

echo Test 2: List jpackage types
"%JAVA_HOME%\bin\jpackage" --type app-image --help
echo.

echo Test 3: Try creating simple app-image (NOT installer)
echo This should work quickly...

"%JAVA_HOME%\bin\jpackage" ^
    --type app-image ^
    --name "TestApp" ^
    --input target ^
    --main-jar LaPilaSiLaCiocan-1.0-SNAPSHOT-uber.jar ^
    --main-class com.autodeploy.Main ^
    --dest C:\Temp\test-app ^
    --verbose

echo.
echo Did it finish? Check C:\Temp\test-app
pause