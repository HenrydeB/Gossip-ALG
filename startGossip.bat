@echo off

::set "directory=C:\Users\henry\OneDrive\Desktop\Distributed Systems I\DesktopGS"

for %%a in (%*) do (
    start cmd /c "cd /d %cd% && java Gossip %%a"
)