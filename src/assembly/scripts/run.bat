@echo off
setlocal
set "DIR=%~dp0"
if "%DIR:~-1%"=="\" set "DIR=%DIR:~0,-1%"

start "" javaw ^
  --module-path "%DIR%\lib" ^
  --add-modules javafx.controls ^
  -Djava.library.path="%DIR%" ^
  -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager ^
  -cp "%DIR%\config;%DIR%\app.jar" ^
  cz.bliksoft.meshcorecompanion.AppLauncher %*

endlocal
