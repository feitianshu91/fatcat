package com.example.fatcat.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.fatcat.model.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * 应用更新管理�?
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        // 版本信息URL（需要替换为实际的URL�?
        private const val VERSION_CHECK_URL = "https://raw.githubusercontent.com/feitianshu91/FatCat/main/version.json"
        
        // 或者使用自己的服务�?
        // private const val VERSION_CHECK_URL = "https://your-domain.com/api/version"
    }
    
    private var downloadId: Long = -1L
    
    /**
     * 检查更�?
     * @return AppVersion 如果有新版本，否则返回null
     */
    suspend fun checkForUpdate(): AppVersion? = withContext(Dispatchers.IO) {
        try {
            // 获取当前版本
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            
            // 从服务器获取最新版本信�?
            val versionJson = URL(VERSION_CHECK_URL).readText()
            val jsonObject = JSONObject(versionJson)
            
            val latestVersion = AppVersion(
                versionName = jsonObject.getString("versionName"),
                versionCode = jsonObject.getInt("versionCode"),
                downloadUrl = jsonObject.getString("downloadUrl"),
                updateMessage = jsonObject.getString("updateMessage"),
                forceUpdate = jsonObject.optBoolean("forceUpdate", false),
                fileSize = jsonObject.optLong("fileSize", 0L)
            )
            
            // 如果有新版本，返回版本信�?
            if (latestVersion.versionCode > currentVersionCode) {
                latestVersion
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "检查更新失�?, e)
            null
        }
    }
    
    /**
     * 获取当前版本信息
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * 下载并安装更�?
     */
    fun downloadAndInstall(appVersion: AppVersion, onProgress: (Int) -> Unit = {}) {
        try {
            val fileName = "FatCat_${appVersion.versionName}.apk"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            // 如果文件已存在，删除
            if (file.exists()) {
                file.delete()
            }
            
            // 使用系统下载管理器下�?
            val request = DownloadManager.Request(Uri.parse(appVersion.downloadUrl))
                .setTitle("肥猫桌宠更新")
                .setDescription("正在下载 v${appVersion.versionName}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            // 注册下载完成监听
            registerDownloadReceiver(file)
            
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "下载失败", e)
        }
    }
    
    /**
     * 注册下载完成广播接收�?
     */
    private fun registerDownloadReceiver(apkFile: File) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    // 下载完成，安装APK
                    installApk(apkFile)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
    
    /**
     * 安装APK
     */
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用FileProvider
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            context.startActivity(intent)
            
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "安装APK失败", e)
        }
    }
    
    /**
     * 格式化文件大�?
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

