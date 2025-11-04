package com.example.fatcat.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * 音乐播放管理器
 * 负责管理背景音乐的播放、暂停、停止
 */
class MusicPlayer private constructor(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isPaused = false
    private var currentResId: Int = -1
    
    companion object {
        private const val TAG = "MusicPlayer"
        
        @SuppressLint("StaticFieldLeak") // 使用的是 applicationContext，不会造成内存泄漏
        @Volatile
        private var INSTANCE: MusicPlayer? = null
        
        fun getInstance(context: Context): MusicPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlayer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 播放音乐
     * @param resId 音频资源ID
     * @param isLooping 是否循环播放
     */
    fun play(resId: Int, isLooping: Boolean = true) {
        try {
            // 如果正在播放相同的音乐，不做任何操作
            if (isPlaying && currentResId == resId) {
                Log.d(TAG, "Already playing this music")
                return
            }
            
            // 停止当前播放
            stop()
            
            // 创建新的 MediaPlayer
            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.apply {
                this.isLooping = isLooping
                setOnCompletionListener {
                    if (!isLooping) {
                        this@MusicPlayer.isPlaying = false
                        Log.d(TAG, "Music playback completed")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    this@MusicPlayer.isPlaying = false
                    false
                }
                start()
            }
            // 在 apply 块外更新状态变量（避免作用域冲突）
            isPlaying = true
            isPaused = false
            currentResId = resId
            Log.d(TAG, "Music started playing")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing music", e)
            isPlaying = false
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    isPaused = true
                    isPlaying = false
                    Log.d(TAG, "Music paused")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing music", e)
        }
    }
    
    /**
     * 恢复播放
     */
    fun resume() {
        try {
            mediaPlayer?.let {
                if (isPaused) {
                    it.start()
                    isPlaying = true
                    isPaused = false
                    Log.d(TAG, "Music resumed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming music", e)
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
                isPlaying = false
                isPaused = false
                currentResId = -1
                Log.d(TAG, "Music stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music", e)
        }
    }
    
    /**
     * 切换播放/暂停状态
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun toggle() {
        if (isPlaying) {
            pause()
        } else if (isPaused) {
            resume()
        }
    }
    
    /**
     * 检查是否正在播放
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 检查是否已暂停
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun isPaused(): Boolean = isPaused
    
    /**
     * 设置音量
     * @param volume 音量大小 0.0 - 1.0
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun setVolume(volume: Float) {
        try {
            val vol = volume.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(vol, vol)
            Log.d(TAG, "Volume set to $vol")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }
    
    /**
     * 获取当前播放位置（毫秒）
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取音乐总时长（毫秒）
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 跳转到指定位置
     * @param position 位置（毫秒）
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            Log.d(TAG, "Seek to position: $position")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }
    
    /**
     * 释放资源
     */
    @Suppress("unused") // 工具函数，保留供将来使用
    fun release() {
        stop()
        INSTANCE = null
    }
}

