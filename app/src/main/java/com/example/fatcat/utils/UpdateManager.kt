package com.example.fatcat.utils

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.fatcat.R
import com.example.fatcat.MainActivity
import com.example.fatcat.model.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * 应用更新管理器
 * 支持手动检查和自动检查更新
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        // 版本信息URL（使用Gitee仓库，国内访问更快）
        private const val VERSION_CHECK_URL = "https://gitee.com/long-anxiang/fatcat/raw/main/version.json"
        
        // 如果需要可以换回GitHub
        // private const val VERSION_CHECK_URL = "https://raw.githubusercontent.com/feitianshu91/fatcat/main/version.json"
        
        // 自动检查配置
        private const val PREF_NAME = "update_settings"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_IGNORED_VERSION = "ignored_version"
        
        // 检查间隔（默认24小时）
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        
        // 通知相关
        private const val NOTIFICATION_CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "应用更新"
        private const val NOTIFICATION_ID_UPDATE = 1001
        private const val NOTIFICATION_ID_DOWNLOAD = 1002
    }
    
    private var downloadId: Long = -1L
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // 下载进度状态
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress
    
    // 下载状态
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus
    
    // 通知管理器
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * 检查更新
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
            
            android.util.Log.d("UpdateManager", "📱 当前版本: versionCode=$currentVersionCode, versionName=${packageInfo.versionName}")
            android.util.Log.d("UpdateManager", "🌐 检查更新URL: $VERSION_CHECK_URL")
            
            // 从服务器获取最新版本信息（添加缓存控制，避免获取到缓存的旧版本）
            val url = URL(VERSION_CHECK_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("Expires", "0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val versionJson = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("UpdateManager", "📥 获取到的version.json: $versionJson")
            
            val jsonObject = JSONObject(versionJson)
            
            val latestVersion = AppVersion(
                versionName = jsonObject.getString("versionName"),
                versionCode = jsonObject.getInt("versionCode"),
                downloadUrl = jsonObject.getString("downloadUrl"),
                updateMessage = jsonObject.getString("updateMessage"),
                forceUpdate = jsonObject.optBoolean("forceUpdate", false),
                fileSize = jsonObject.optLong("fileSize", 0L)
            )
            
            android.util.Log.d("UpdateManager", "🔍 远程版本: versionCode=${latestVersion.versionCode}, versionName=${latestVersion.versionName}")
            android.util.Log.d("UpdateManager", "📊 版本比较: 远程(${latestVersion.versionCode}) > 当前($currentVersionCode) = ${latestVersion.versionCode > currentVersionCode}")
            
            // 如果有新版本，返回版本信息
            if (latestVersion.versionCode > currentVersionCode) {
                android.util.Log.d("UpdateManager", "✅ 发现新版本！")
                latestVersion
            } else {
                android.util.Log.d("UpdateManager", "✅ 已是最新版本")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "❌ 检查更新失败", e)
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
     * 下载并安装更新
     */
    suspend fun downloadAndInstall(appVersion: AppVersion, onProgress: (Int) -> Unit = {}) {
        try {
            val fileName = "FatCat_${appVersion.versionName}.apk"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            // 如果文件已存在，删除
            if (file.exists()) {
                file.delete()
            }
            
            // 重置下载状态
            _downloadProgress.value = 0
            _downloadStatus.value = DownloadStatus.Idle
            
            // 使用系统下载管理器下载
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
            
            android.util.Log.d("UpdateManager", "📥 开始下载更新，ID: $downloadId")
            
            // 注册下载完成监听
            registerDownloadReceiver(file)
            
            // 开始监控下载进度
            startMonitoringDownload(downloadId)
            
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "下载失败", e)
            _downloadStatus.value = DownloadStatus.Failed(e.message ?: "未知错误")
        }
    }
    
    /**
     * 注册下载完成广播接收器
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
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    // ============ 自动更新功能 ============
    
    /**
     * 检查是否应该自动检查更新
     * @return true 如果应该检查
     */
    fun shouldAutoCheck(): Boolean {
        if (!isAutoCheckEnabled()) {
            return false
        }
        
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        
        return (currentTime - lastCheckTime) >= CHECK_INTERVAL_MS
    }
    
    /**
     * 自动检查更新（带忽略版本逻辑）
     * @return AppVersion 如果有新版本且未被忽略，否则返回null
     */
    suspend fun autoCheckForUpdate(): AppVersion? {
        val newVersion = checkForUpdate()
        
        if (newVersion != null) {
            // 更新最后检查时间
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
            
            // 检查是否被忽略
            val ignoredVersion = prefs.getInt(KEY_IGNORED_VERSION, -1)
            if (ignoredVersion == newVersion.versionCode) {
                android.util.Log.d("UpdateManager", "版本 ${newVersion.versionName} 已被忽略")
                return null
            }
        } else {
            // 即使没有更新也更新检查时间
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
        }
        
        return newVersion
    }
    
    /**
     * 忽略某个版本
     */
    fun ignoreVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_IGNORED_VERSION, versionCode).apply()
        android.util.Log.d("UpdateManager", "已忽略版本: $versionCode")
    }
    
    /**
     * 清除忽略的版本
     */
    fun clearIgnoredVersion() {
        prefs.edit().remove(KEY_IGNORED_VERSION).apply()
    }
    
    /**
     * 设置自动检查更新
     */
    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply()
        android.util.Log.d("UpdateManager", "自动检查更新: ${if (enabled) "已开启" else "已关闭"}")
    }
    
    /**
     * 获取自动检查更新状态
     */
    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true) // 默认开启
    }
    
    /**
     * 获取上次检查时间
     */
    fun getLastCheckTime(): Long {
        return prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    }
    
    /**
     * 获取距离下次检查的剩余时间（毫秒）
     */
    fun getTimeUntilNextCheck(): Long {
        val lastCheckTime = getLastCheckTime()
        val currentTime = System.currentTimeMillis()
        val nextCheckTime = lastCheckTime + CHECK_INTERVAL_MS
        return maxOf(0L, nextCheckTime - currentTime)
    }
    
    /**
     * 格式化剩余时间
     */
    fun formatTimeUntilNextCheck(): String {
        val ms = getTimeUntilNextCheck()
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "即将检查"
        }
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用更新通知"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 显示更新通知
     */
    fun showUpdateNotification(appVersion: AppVersion) {
        createNotificationChannel()
        
        // 创建点击通知后打开应用的Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎉 发现新版本 v${appVersion.versionName}")
            .setContentText(appVersion.updateMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(appVersion.updateMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
        android.util.Log.d("UpdateManager", "📢 已显示更新通知")
    }
    
    /**
     * 显示下载进度通知
     */
    private fun showDownloadProgressNotification(progress: Int) {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载更新")
            .setContentText("已完成 $progress%")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_DOWNLOAD, notification)
    }
    
    /**
     * 取消下载进度通知
     */
    private fun cancelDownloadNotification() {
        notificationManager.cancel(NOTIFICATION_ID_DOWNLOAD)
    }
    
    /**
     * 开始监控下载进度
     */
    suspend fun startMonitoringDownload(downloadId: Long) {
        withContext(Dispatchers.IO) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var downloading = true
            
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    val status = cursor.getInt(statusIndex)
                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val bytesTotal = cursor.getLong(bytesTotalIndex)
                    
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                                _downloadProgress.value = progress
                                _downloadStatus.value = DownloadStatus.Downloading(progress)
                                showDownloadProgressNotification(progress)
                                android.util.Log.d("UpdateManager", "📥 下载进度: $progress% ($bytesDownloaded/$bytesTotal)")
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _downloadProgress.value = 100
                            _downloadStatus.value = DownloadStatus.Success
                            cancelDownloadNotification()
                            downloading = false
                            android.util.Log.d("UpdateManager", "✅ 下载完成！")
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                            _downloadStatus.value = DownloadStatus.Failed("下载失败，错误码: $reason")
                            cancelDownloadNotification()
                            downloading = false
                            android.util.Log.e("UpdateManager", "❌ 下载失败，错误码: $reason")
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            _downloadStatus.value = DownloadStatus.Paused
                            android.util.Log.d("UpdateManager", "⏸️ 下载已暂停")
                        }
                    }
                }
                cursor.close()
                
                if (downloading) {
                    delay(500) // 每500毫秒更新一次进度
                }
            }
        }
    }
}

/**
 * 下载状态
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    object Success : DownloadStatus()
    object Paused : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}

