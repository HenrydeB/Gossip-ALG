@echo off

for %%a in (%*) do (
    start cmd /c "cd /d %cd% && java Gossip %%a"
)