@echo off
REM Gera sprites PNG dos NPCs usando zrenderer
REM Pré-requisito: zrenderer.exe no PATH ou na pasta atual
REM Input: C:\Users\silve\Documents\Sprites-Projeto\npcs\ (arquivos .spr e .act)
REM Output: C:\Users\silve\Documents\Sprites-Projeto\output-npcs\{jobId}_0_0.png

SET SPRITES_DIR=C:\Users\silve\Documents\Sprites-Projeto\npcs
SET OUTPUT_DIR=C:\Users\silve\Documents\Sprites-Projeto\output-npcs

IF NOT EXIST "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Processar todos os arquivos .spr na pasta npcs/
FOR %%F IN ("%SPRITES_DIR%\*.spr") DO (
    SET "FILENAME=%%~nF"
    echo Processando: %%~nF
    zrenderer -o "%OUTPUT_DIR%\%%~nF_0_0.png" "%%F"
)

echo Sprites gerados em %OUTPUT_DIR%
pause
