package com.khereba1.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.khereba1.app.AppLog

object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val SERVICE_RUN_COMMAND = "com.termux.app.RunCommandService"
    private const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION_RUN_COMMAND) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun runCommand(context: Context, script: String, background: Boolean = true): Result<Unit> {
        return try {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PACKAGE, SERVICE_RUN_COMMAND)
                putExtra("com.termux.RUN_COMMAND_PATH", BASH_PATH)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", if (background) "0" else "2")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            AppLog.log("TERMUX", "Dispatched RUN_COMMAND (background=$background)")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.log("TERMUX", "RUN_COMMAND failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun openTermux(context: Context): Boolean {
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AppLog.log("TERMUX", "Failed to launch Termux", e)
            false
        }
    }

    fun openFdroidTermux(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/en/packages/com.termux/"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.log("TERMUX", "Failed to open F-Droid link", e)
        }
    }
}
