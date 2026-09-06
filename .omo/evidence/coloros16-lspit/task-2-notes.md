# Task 2 notes

Observed at: 2026-09-07T07:51:44.1826042+08:00
Device rows: 3B661M01NH500000:device
Status: READY
SDK: 36; release: 16; oplusrom: V16.1.0
Launcher: priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.android.launcher/.Launcher
Privilege branch: True via /system/bin/su
Recovery: proven using adb shell which su; adb shell pm path com.android.systemui; adb shell settings get global device_provisioned; adb shell ls /data/adb 2>&1

