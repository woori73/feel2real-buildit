@echo off
rem F2R academy PC worker: starts Claude Code Remote Control in this folder.
rem On your phone: Claude app - Code - pick this PC session and ask it to process the queue.
rem First time: write ACADEMY_PC (academy PC) or HOME_LAPTOP (home laptop) in worker.txt.
cd /d %~dp0
if not exist worker.txt echo ACADEMY_PC> worker.txt
if not exist outputs mkdir outputs
claude remote-control
