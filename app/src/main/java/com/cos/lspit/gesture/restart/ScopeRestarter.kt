package com.cos.lspit.gesture.restart

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

sealed class Capability {
    object Root : Capability()
    object Shizuku : Capability()
    object None : Capability()
}

sealed class RestartResult {
    object Success : RestartResult()
    data class Failure(val reason: String) : RestartResult()
    object Manual : RestartResult()
}

/**
 * Root-first SystemUI restart with a granted-Shizuku fallback and a manual
 * dead end. Every path is wrapped so the UI can never crash from here.
 */
object ScopeRestarter {

    /** Root probe first, then granted Shizuku, else None. Never throws. */
    suspend fun detect(context: Context): Capability = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(RestartCommands.rootProbe())
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (output.contains("uid=0")) return@withContext Capability.Root
        } catch (_: Throwable) {
            // No root or su missing -> fall through.
        }
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext Capability.Shizuku
            }
        } catch (_: Throwable) {
            // Shizuku not installed or binder dead -> None.
        }
        Capability.None
    }

    /** Kills SystemUI via the detected capability; the system restarts it automatically. */
    suspend fun restart(context: Context, capability: Capability): RestartResult =
        withContext(Dispatchers.IO) {
            when (capability) {
                Capability.Root -> try {
                    val process = Runtime.getRuntime().exec(RestartCommands.rootKill())
                    val exit = process.waitFor()
                    if (exit == 0) RestartResult.Success else RestartResult.Failure("root-exit-$exit")
                } catch (failure: Throwable) {
                    RestartResult.Failure("root-error: ${failure.message}")
                }
                Capability.Shizuku -> try {
                    val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    val process = service.newProcess(RestartCommands.shizukuKill(), null, null)
                    val exit = process.waitFor()
                    if (exit == 0) RestartResult.Success else RestartResult.Failure("shizuku-no-perm")
                } catch (failure: Throwable) {
                    RestartResult.Failure("shizuku-error: ${failure.message}")
                }
                Capability.None -> RestartResult.Manual
            }
        }
}
