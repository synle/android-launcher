package com.launcher.nova.repository

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import com.launcher.nova.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val userHandle = Process.myUserHandle()

        launcherApps.getActivityList(null, userHandle)
            .mapNotNull { activityInfo ->
                try {
                    val componentName = activityInfo.componentName
                    AppInfo(
                        packageName = componentName.packageName,
                        label = activityInfo.label.toString(),
                        icon = activityInfo.getBadgedIcon(0),
                        activityName = componentName.className,
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getLaunchIntent(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }
}
