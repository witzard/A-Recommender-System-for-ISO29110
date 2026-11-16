@echo off
rem Runs every test of the prototype in one go: reasoner, reasoning test cases, Fuseki load, backend, run_checks.py,
rem knowledge-search test cases, npm test. Options: see tests\run_all.py (e.g. --restart-backend, --skip npm-test).
cd /d "%~dp0.."
python tests\run_all.py %*
exit /b %ERRORLEVEL%
