@echo off
chcp 65001 >nul
cd /d E:\Project\mygit\kkFileView
start "kkFileView" java -Dfile.encoding=UTF-8 -Doffice.home=E:\Project\mygit\kkFileView\server\LibreOfficePortable\App\libreoffice -jar server\target\kkFileView-5.0.0.jar