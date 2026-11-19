@echo off
rem F2R 학원 PC 작업: 이 폴더에서 Claude Code 원격 제어를 켠다. 휴대폰 Claude 앱 → Code에서 이 PC 세션을 고른다.
rem 처음 한 번: worker.txt 에 ACADEMY_PC (학원 PC) 또는 HOME_LAPTOP (집 노트북)을 적어 둔다.
cd /d %~dp0
if not exist worker.txt echo ACADEMY_PC> worker.txt
if not exist outputs mkdir outputs
claude remote-control
