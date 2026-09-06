2026-09-07 task-1 host-contract gate

Summary:
- libxposed API 102 packaging is proven by the public source and Maven Central artifact. The package-info.java file explicitly documents META-INF/xposed/java_init.list, META-INF/xposed/module.prop, required minApiVersion and targetApiVersion, and META-INF/xposed/scope.list semantics; the artifact is published at https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/ and returned HTTP 200.
- The actual device is rooted Android 16 / ColorOS 16: 'adb -s 3B661M01NH500000 shell getprop ro.build.version.sdk' returned 36, 'ro.build.version.release' returned 16, 'ro.build.version.oplusrom' returned V16.1.0, 'pm path com.android.systemui' returned /system_ext/priv-app/SystemUI/SystemUI.apk, and 'which su' returned /system/bin/su.
- The LSP-IT host identity, host version, host API, disable command, and restart command are not proven. No official public LSP-IT repo or manager package metadata was found, and the device has no matching package names under 'lsposed|lsp|xposed|zygisk'.
- Result: task 1 is NO_GO. The libxposed module contract is supported; the LSP-IT host contract is not proven, so no implementation path can be accepted until the missing proof is provided.

Sources recorded in task-1-host-contract.json:
- https://raw.githubusercontent.com/libxposed/api/45e7c5cfe54725b6d828d8b7be65e22ce60c67e4/api/src/main/java/io/github/libxposed/api/package-info.java
- https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/
- adb commands listed above
- GitHub repository search for 'LSP-IT LSPosed IT' returned no official public LSP-IT repository
