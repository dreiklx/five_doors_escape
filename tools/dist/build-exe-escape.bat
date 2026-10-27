@echo off
REM FiveDoorsEscape.exe para la distribucion completa (CLAUDE.md §2.88): un lanzador nativo de launch4j
REM en modo "launching" (no envuelve los jar), igual que FiveDoorsAtFreddys.exe (§1.23). Usa el runtime
REM JDK 21 que build-distribucion.bat copia en FiveDoorsEscape\runtime y los jar de installDist en
REM FiveDoorsEscape\lwjgl3\lib. Sirve para abrir Escape directamente; desde FDAF se sigue lanzando con
REM lwjgl3.bat (LanzadorEscape), que no cambia.
REM Uso: build-exe-escape.bat <carpeta FiveDoorsEscape de la distribucion> [JDK 8 o superior para correr launch4j]
REM launch4j 3.50 y sus dependencias se reutilizan de la cache de build-exe-launcher.bat
REM (%USERPROFILE%\.launch4j-cache\3.50); si no existe, correr antes build-portable.bat.
setlocal
set DESTINO=%~1
set JDK=%~2
if "%DESTINO%"=="" (
    echo Uso: build-exe-escape.bat ^<carpeta FiveDoorsEscape^> [JDK]
    exit /b 1
)
if "%JDK%"=="" for /d %%D in ("C:\Program Files\Java\jdk1.8*") do set JDK=%%D
set CACHE=%USERPROFILE%\.launch4j-cache\3.50
if not exist "%CACHE%\ready.flag" (
    echo ERROR: no esta la cache de launch4j en %CACHE% ^(correr build-portable.bat primero^).
    exit /b 1
)
if not exist "%DESTINO%\runtime\bin\javaw.exe" (
    echo ERROR: falta "%DESTINO%\runtime\bin\javaw.exe".
    exit /b 1
)
REM launch4j busca bin\windres.exe junto a su jar: el del directorio de trabajo extraido (mismo L4J_CP
REM que build-exe-launcher.bat)
set L4J_CP=%CACHE%\launch4j\launch4j.jar;%CACHE%\xstream-1.4.15.jar;%CACHE%\xmlpull-1.1.3.1.jar;%CACHE%\xpp3_min-1.1.4c.jar
set CONFIG_XML=%TEMP%\launch4j-escape-%RANDOM%.xml
(
echo ^<launch4jConfig^>
echo   ^<headerType^>gui^</headerType^>
echo   ^<dontWrapJar^>true^</dontWrapJar^>
echo   ^<outfile^>%DESTINO%\FiveDoorsEscape.exe^</outfile^>
echo   ^<errTitle^>Five Doors Escape^</errTitle^>
echo   ^<chdir^>.^</chdir^>
echo   ^<priority^>normal^</priority^>
echo   ^<stayAlive^>false^</stayAlive^>
echo   ^<restartOnCrash^>false^</restartOnCrash^>
echo   ^<classPath^>
echo     ^<mainClass^>com.fivedoorsescape.lwjgl3.Lwjgl3Launcher^</mainClass^>
echo     ^<cp^>lwjgl3/lib/*.jar^</cp^>
echo   ^</classPath^>
echo   ^<jre^>
echo     ^<path^>runtime^</path^>
echo     ^<bundledJre64Bit^>true^</bundledJre64Bit^>
echo     ^<bundledJreAsFallback^>false^</bundledJreAsFallback^>
echo     ^<minVersion^>21^</minVersion^>
echo     ^<jdkPreference^>preferJre^</jdkPreference^>
echo     ^<runtimeBits^>64^</runtimeBits^>
echo   ^</jre^>
echo ^</launch4jConfig^>
) > "%CONFIG_XML%"
"%JDK%\bin\java.exe" -cp "%L4J_CP%" net.sf.launch4j.Main "%CONFIG_XML%"
set RC=%ERRORLEVEL%
del "%CONFIG_XML%" >nul 2>&1
if not "%RC%"=="0" (
    echo ERROR: launch4j fallo ^(codigo %RC%^).
    exit /b %RC%
)
echo Generado: %DESTINO%\FiveDoorsEscape.exe
endlocal
