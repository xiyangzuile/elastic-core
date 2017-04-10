@ECHO OFF
cd %cd%
SET /p ask=Is JAVA SDK installed? Did you set Java SDK Enviroments? (Y/n)
if %ask%==n goto No
if %ask%==N goto No
SET /p ask2=Is MAVEN installed? Did you set Maven Enviroments? (Y/n)
if %ask2%==n goto No
if %ask2%==N goto No
SET /p ask3=Do you want to clean compiling? Like: mvn clean compile package.. (Y/n)
if %ask3%==Y goto Clean
if %ask3%==y goto Clean
if %ask3%==n goto NotClean
if %ask3%==N goto NotClean



:Run
echo .
SET /p ask4=Install finished. Do you want to run? (y/N)
if %ask4%==n goto No
if %ask4%==N goto No
call windows_run.bat

:Clean
cls
echo.
echo package compiling started with clean parameter...
call windows_build_clean.bat
goto Run

:NotClean
cls
echo.
echo package compiling started...
call windows_build_withoutclean.bat
goto Run

:No
cls
echo.
echo Okay, let's exit...
echo.
set /p pause=Press any key to continue!... 
exit
