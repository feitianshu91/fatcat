package com.example.fatcat.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.fatcat.model.Danmaku
import com.example.fatcat.model.PetSpeech
import com.example.fatcat.model.SpeechTrigger
import com.example.fatcat.ui.pet.DanmakuOverlay
import com.example.fatcat.ui.pet.FloatingPetView
import com.example.fatcat.ui.pet.SpeechBubbleOverlay
import com.example.fatcat.utils.Constants
import com.example.fatcat.utils.DanmakuGenerator
import com.example.fatcat.utils.MovementHelper
import com.example.fatcat.utils.PetManager
import com.example.fatcat.utils.SpeechGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf

/**
 * 浮动宠物服务
 * 支持拖动和自动移动
 */
class FloatingPetService : Service() {
    
    private lateinit var windowManager: WindowManager
    
    // 宠物窗口（小窗口，可拖动）
    private lateinit var petFloatingView: FrameLayout
    private lateinit var petLayoutParams: WindowManager.LayoutParams
    
    // 弹幕窗口（全屏，不可触摸）
    private lateinit var danmakuFloatingView: FrameLayout
    private lateinit var danmakuLayoutParams: WindowManager.LayoutParams
    private var isDanmakuWindowAdded = false  // 跟踪弹幕窗口是否已添加
    
    // 对话窗口（显示在宠物上方）
    private lateinit var speechFloatingView: FrameLayout
    private lateinit var speechLayoutParams: WindowManager.LayoutParams
    private var isSpeechWindowAdded = false  // 跟踪对话窗口是否已添加
    
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val petManager by lazy { PetManager.getInstance(this) }
    private val movementHelper by lazy { MovementHelper(this) }
    private var petSize: Int = 0  // 宠物大小（像素）
    
    // 移动相关变量
    private var moveJob: Job? = null
    private var targetPosition: Point? = null
    private var isDragging = false
    private var isCurrentlyMoving = false  // 追踪宠物是否正在移动（用于计算衰减）
    
    // 动画相关变量
    private var happyAnimationJob: Job? = null
    private var isPlayingAnimation = false  // 是否正在播放动画
    
    // 触摸事件相关
    private var initialX = 0
    private var initialY = 0
    
    // 弹幕相关变量
    private var danmakuJob: Job? = null
    private val activeDanmakuList = mutableStateListOf<Danmaku>()
    
    // 弹幕爆发标志（用于接收外部触发）
    @Volatile
    private var triggerDanmakuBurst = false
    
    // 对话相关变量
    private val currentSpeech = mutableStateOf<PetSpeech?>(null)
    private var speechJob: Job? = null
    private var lastSpeechTime = 0L
    @Volatile
    private var triggerSpeechAction: SpeechTrigger? = null
    
    // 宠物位置状态（用于对话气泡跟随）
    @Suppress("AutoboxingStateCreation")
    private val petPositionX = mutableStateOf(0)
    @Suppress("AutoboxingStateCreation")
    private val petPositionY = mutableStateOf(0)
    
    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("FloatingPetService", "🟢 服务创建开始")
        
        try {
            // 先启动前台服务（必须在5秒内调用，否则会被系统杀死）
            startForeground(1, createNotification())
            android.util.Log.d("FloatingPetService", "✅ 前台服务已启动")
            
            // 初始化WindowManager
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager 
                ?: throw IllegalStateException("无法获取 WindowManager")
            
            // 创建窗口（按顺序，便于错误回滚）
            try {
                createPetWindow()
                android.util.Log.d("FloatingPetService", "✅ 宠物窗口创建成功")
                
                createDanmakuWindow()
                android.util.Log.d("FloatingPetService", "✅ 弹幕窗口创建成功")
                
                createSpeechWindow()
                android.util.Log.d("FloatingPetService", "✅ 对话窗口创建成功")
                
                // 启动所有后台任务
                startPetUpdates()
                startAutoMovement()
                startDanmakuListener()
                startSpeechMonitor()
                
                android.util.Log.d("FloatingPetService", "🟢 服务创建完成")
                
            } catch (e: SecurityException) {
                android.util.Log.e("FloatingPetService", "❌ 权限错误：$e", e)
                // 清理已创建的资源
                cleanupOnCreateError()
                throw e
                
            } catch (e: Exception) {
                android.util.Log.e("FloatingPetService", "❌ 创建窗口失败：$e", e)
                // 清理已创建的资源
                cleanupOnCreateError()
                throw e
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FloatingPetService", "❌ 服务初始化失败，服务将停止", e)
            // 停止服务
            stopSelf()
        }
    }
    
    /**
     * onCreate 失败时清理资源
     */
    private fun cleanupOnCreateError() {
        android.util.Log.d("FloatingPetService", "🧹 清理失败的初始化...")
        try {
            // 停止所有任务
            moveJob?.cancel()
            happyAnimationJob?.cancel()
            danmakuJob?.cancel()
            speechJob?.cancel()
            
            // 移除已创建的窗口
            removeAllWindows()
            
        } catch (e: Exception) {
            android.util.Log.w("FloatingPetService", "清理时出错", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 检查是否是触发弹幕的Intent
        if (intent?.action == ACTION_TRIGGER_DANMAKU) {
            android.util.Log.d("FloatingPetService", "收到触发弹幕指令")
            triggerDanmakuBurst()
        }
        // 检查是否是触发说话的Intent
        if (intent?.action == ACTION_TRIGGER_SPEECH) {
            android.util.Log.d("FloatingPetService", "收到触发说话指令")
            val trigger = intent.getStringExtra("trigger")?.let { 
                try {
                    SpeechTrigger.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }
            triggerSpeech(trigger)
        }
        // 检查是否是触发开心动画的Intent
        if (intent?.action == ACTION_TRIGGER_HAPPY_ANIMATION) {
            android.util.Log.d("FloatingPetService", "收到触发开心动画指令")
            playHappyJumpAnimation()
        }
        return START_STICKY
    }
    
    companion object {
        const val ACTION_TRIGGER_DANMAKU = "com.example.fatcat.TRIGGER_DANMAKU"
        const val ACTION_TRIGGER_SPEECH = "com.example.fatcat.TRIGGER_SPEECH"
        const val ACTION_TRIGGER_HAPPY_ANIMATION = "com.example.fatcat.TRIGGER_HAPPY_ANIMATION"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * 创建宠物窗口（小窗口，可拖动）
     */
    private fun createPetWindow() {
        // 检查是否有悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.e("FloatingPetService", "没有悬浮窗权限")
                throw SecurityException("没有悬浮窗权限，无法创建浮动窗口")
            }
        }
        
        // 获取保存的宠物大小
        petSize = petManager.getPetSize()
        
        petFloatingView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // 设置透明背景
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // 创建 LifecycleOwner 和 SavedStateRegistryOwner 用于 ComposeView
        val petLifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)
            
            init {
                // 按正确顺序初始化
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
            
            override val lifecycle: Lifecycle
                get() = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry
        }
        
        val petComposeView = ComposeView(this).apply {
            // 设置ComposeView透明背景
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 使用反射设置 ViewTreeLifecycleOwner
            try {
                val setMethod = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                    .getMethod("set", View::class.java, LifecycleOwner::class.java)
                setMethod.invoke(null, petFloatingView, petLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置 ViewTreeLifecycleOwner", e)
            }
            
            // 使用反射设置 ViewTreeSavedStateRegistryOwner
            try {
                val setMethod = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                    .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                setMethod.invoke(null, petFloatingView, petLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置 ViewTreeSavedStateRegistryOwner", e)
            }
            
            setContent {
                // 只显示宠物，并在 Compose 层面处理触摸
                FloatingPetView(
                    petManager = petManager,
                    petSize = petSize,
                    onDragStart = {
                        // 开始拖动
                        android.util.Log.d("FloatingPetService", "Compose: 开始拖动")
                        isDragging = false
                        initialX = petLayoutParams.x
                        initialY = petLayoutParams.y
                        stopAutoMovement()
                    },
                    onDrag = { deltaX, deltaY ->
                        // 拖动中
                        isDragging = true
                        val newX = petLayoutParams.x + deltaX.toInt()
                        val newY = petLayoutParams.y + deltaY.toInt()
                        
                        // 应用边界限制
                        val constrained = movementHelper.constrainToBounds(
                            newX, newY,
                            petSize, petSize
                        )
                        
                        petLayoutParams.x = constrained.x
                        petLayoutParams.y = constrained.y
                        
                        // 更新宠物位置状态
                        petPositionX.value = constrained.x
                        petPositionY.value = constrained.y
                        
                        windowManager.updateViewLayout(petFloatingView, petLayoutParams)
                    },
                    onDragEnd = {
                        // 结束拖动
                        android.util.Log.d("FloatingPetService", "Compose: 结束拖动")
                        if (isDragging) {
                            // 如果是拖动，恢复自动移动
                            startAutoMovement()
                        }
                        isDragging = false
                    },
                    onDoubleTap = {
                        // 双击
                        android.util.Log.d("FloatingPetService", "Compose: 双击宠物")
                        playHappyJumpAnimation()
                    }
                )
            }
        }
        
        petFloatingView.addView(petComposeView)
        
        // 创建窗口参数（宠物窗口大小）
        // ⚠️ 使用精确的宠物大小，而不是 WRAP_CONTENT
        petLayoutParams = WindowManager.LayoutParams(
            petSize,  // 精确的宽度
            petSize,  // 精确的高度
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or  // ⭐ 关键！让窗口外的触摸传递给下层
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  // 允许超出屏幕边界
            PixelFormat.TRANSLUCENT  // 使用半透明格式支持透明背景
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Constants.FloatingWindow.INITIAL_X
            y = Constants.FloatingWindow.INITIAL_Y
            // 设置窗口格式为透明
            format = PixelFormat.TRANSPARENT
        }
        
        // 初始化宠物位置状态
        petPositionX.value = Constants.FloatingWindow.INITIAL_X
        petPositionY.value = Constants.FloatingWindow.INITIAL_Y
        
        // ⚠️ 不再在 FrameLayout 上设置 OnTouchListener！
        // 改为在 Compose 层面处理触摸，这样只有实际的图片区域会接收触摸事件
        // 透明区域不会拦截触摸，用户可以正常点击桌面其他内容
        
        try {
            windowManager.addView(petFloatingView, petLayoutParams)
            android.util.Log.d("FloatingPetService", "宠物窗口创建成功")
        } catch (e: Exception) {
            android.util.Log.e("FloatingPetService", "添加宠物窗口到屏幕失败", e)
            throw e
        }
    }
    
    /**
     * 创建弹幕窗口（全屏，不可触摸）
     */
    private fun createDanmakuWindow() {
        danmakuFloatingView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // 设置透明背景
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // 创建独立的 LifecycleOwner
        val danmakuLifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)
            
            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
            
            override val lifecycle: Lifecycle
                get() = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry
        }
        
        val danmakuComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 使用反射设置 ViewTreeLifecycleOwner
            try {
                val setMethod = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                    .getMethod("set", View::class.java, LifecycleOwner::class.java)
                setMethod.invoke(null, danmakuFloatingView, danmakuLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置弹幕 ViewTreeLifecycleOwner", e)
            }
            
            // 使用反射设置 ViewTreeSavedStateRegistryOwner
            try {
                val setMethod = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                    .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                setMethod.invoke(null, danmakuFloatingView, danmakuLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置弹幕 ViewTreeSavedStateRegistryOwner", e)
            }
            
            setContent {
                // 只显示弹幕覆盖层
                DanmakuOverlay(
                    danmakuList = activeDanmakuList,
                    onDanmakuComplete = { danmakuId ->
                        // 移除完成的弹幕
                        activeDanmakuList.removeAll { it.id == danmakuId }
                        // 检查是否需要移除弹幕窗口
                        removeDanmakuWindowIfEmpty()
                    }
                )
            }
        }
        
        danmakuFloatingView.addView(danmakuComposeView)
        
        // 创建全屏窗口参数
        danmakuLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,  // 全屏宽度
            WindowManager.LayoutParams.MATCH_PARENT,  // 全屏高度
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // ⭐ 不响应触摸，让触摸事件穿透
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            format = PixelFormat.TRANSPARENT
        }
        
        // 不在启动时添加弹幕窗口，而是在需要显示弹幕时才添加
        android.util.Log.d("FloatingPetService", "弹幕窗口创建完成，等待需要时才添加到屏幕")
    }
    
    /**
     * 创建对话窗口（显示在宠物上方）
     */
    private fun createSpeechWindow() {
        speechFloatingView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // 创建独立的 LifecycleOwner
        val speechLifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)
            
            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
            
            override val lifecycle: Lifecycle
                get() = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry
        }
        
        val speechComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 使用反射设置 ViewTreeLifecycleOwner
            try {
                val setMethod = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                    .getMethod("set", View::class.java, LifecycleOwner::class.java)
                setMethod.invoke(null, speechFloatingView, speechLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置对话 ViewTreeLifecycleOwner", e)
            }
            
            // 使用反射设置 ViewTreeSavedStateRegistryOwner
            try {
                val setMethod = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                    .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                setMethod.invoke(null, speechFloatingView, speechLifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "无法设置对话 ViewTreeSavedStateRegistryOwner", e)
            }
            
            setContent {
                // 显示对话气泡（跟随宠物位置）
                SpeechBubbleOverlay(
                    speech = currentSpeech.value,
                    petX = petPositionX.value,
                    petY = petPositionY.value,
                    petSize = petSize
                )
            }
        }
        
        speechFloatingView.addView(speechComposeView)
        
        // 创建全屏窗口参数（不可触摸）
        speechLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // 不响应触摸
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            format = PixelFormat.TRANSPARENT
        }
        
        // 不在启动时添加对话窗口，而是在需要显示对话时才添加
        android.util.Log.d("FloatingPetService", "对话窗口创建完成，等待需要时才添加到屏幕")
    }
    
    /**
     * 确保弹幕窗口已添加到屏幕（线程安全）
     */
    private fun ensureDanmakuWindowAdded() {
        synchronized(this) {
            if (!isDanmakuWindowAdded && ::danmakuFloatingView.isInitialized) {
                try {
                    windowManager.addView(danmakuFloatingView, danmakuLayoutParams)
                    isDanmakuWindowAdded = true
                    android.util.Log.d("FloatingPetService", "✅ 弹幕窗口已添加到屏幕")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingPetService", "❌ 添加弹幕窗口失败", e)
                    isDanmakuWindowAdded = false  // 确保状态一致
                }
            }
        }
    }
    
    /**
     * 移除弹幕窗口（当没有活动弹幕时）（线程安全）
     */
    private fun removeDanmakuWindowIfEmpty() {
        synchronized(this) {
            if (isDanmakuWindowAdded && activeDanmakuList.isEmpty()) {
                try {
                    windowManager.removeView(danmakuFloatingView)
                    isDanmakuWindowAdded = false
                    android.util.Log.d("FloatingPetService", "✅ 弹幕窗口已移除（无活动弹幕）")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingPetService", "❌ 移除弹幕窗口失败", e)
                    // 即使移除失败，也标记为未添加，避免重复尝试
                    isDanmakuWindowAdded = false
                }
            }
        }
    }
    
    /**
     * 确保对话窗口已添加到屏幕（线程安全）
     */
    private fun ensureSpeechWindowAdded() {
        synchronized(this) {
            if (!isSpeechWindowAdded && ::speechFloatingView.isInitialized) {
                try {
                    windowManager.addView(speechFloatingView, speechLayoutParams)
                    isSpeechWindowAdded = true
                    android.util.Log.d("FloatingPetService", "✅ 对话窗口已添加到屏幕")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingPetService", "❌ 添加对话窗口失败", e)
                    isSpeechWindowAdded = false  // 确保状态一致
                }
            }
        }
    }
    
    /**
     * 移除对话窗口（当没有对话时）（线程安全）
     */
    private fun removeSpeechWindowIfEmpty() {
        synchronized(this) {
            if (isSpeechWindowAdded && currentSpeech.value == null) {
                try {
                    windowManager.removeView(speechFloatingView)
                    isSpeechWindowAdded = false
                    android.util.Log.d("FloatingPetService", "✅ 对话窗口已移除（无对话）")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingPetService", "❌ 移除对话窗口失败", e)
                    // 即使移除失败，也标记为未添加，避免重复尝试
                    isSpeechWindowAdded = false
                }
            }
        }
    }
    
    /**
     * 启动自动移动
     */
    private fun startAutoMovement() {
        android.util.Log.d("FloatingPetService", "启动自动移动")
        
        stopAutoMovement()
        
        moveJob = serviceScope.launch {
            while (true) {
                delay(Constants.Movement.MOVE_INTERVAL_MS)
                
                // 如果正在拖动，跳过
                if (isDragging) {
                    android.util.Log.d("FloatingPetService", "正在拖动，跳过移动")
                    continue
                }
                
                // 检查宠物是否可以移动（只有常态时才移动）
                val canMove = petManager.canMove()
                val pet = petManager.pet.value
                android.util.Log.d("FloatingPetService", "检查移动条件 - 状态:${pet.state}, 睡眠:${pet.sleep}, 可移动:$canMove")
                
                if (!canMove) {
                    // 不能移动时，清除目标位置
                    targetPosition = null
                    isCurrentlyMoving = false  // 不能移动时设置为静止状态
                    continue
                }
                
                // 如果没有目标或已到达目标，生成新目标
                if (targetPosition == null) {
                    targetPosition = movementHelper.generateRandomTarget(
                        petLayoutParams.x,
                        petLayoutParams.y,
                        petSize,  // 使用 petSize 而不是 petLayoutParams.width（WRAP_CONTENT）
                        petSize   // 使用 petSize 而不是 petLayoutParams.height（WRAP_CONTENT）
                    )
                    android.util.Log.d("FloatingPetService", "生成新目标位置: (${targetPosition?.x}, ${targetPosition?.y})")
                }
                
                // 移动一步
                targetPosition?.let { target ->
                    val nextStep = movementHelper.calculateNextStep(
                        petLayoutParams.x,
                        petLayoutParams.y,
                        target.x,
                        target.y
                    )
                    
                    if (nextStep != null) {
                        // 移动到下一步
                        android.util.Log.d("FloatingPetService", "移动到: (${nextStep.x}, ${nextStep.y})")
                        petLayoutParams.x = nextStep.x
                        petLayoutParams.y = nextStep.y
                        
                        // 更新宠物位置状态（用于对话气泡跟随）
                        petPositionX.value = nextStep.x
                        petPositionY.value = nextStep.y
                        
                        windowManager.updateViewLayout(petFloatingView, petLayoutParams)
                        isCurrentlyMoving = true  // 正在移动状态
                    } else {
                        // 已到达目标，清除目标
                        android.util.Log.d("FloatingPetService", "到达目标位置")
                        targetPosition = null
                        isCurrentlyMoving = false  // 到达目标后静止
                    }
                }
            }
        }
    }
    
    /**
     * 停止自动移动
     */
    private fun stopAutoMovement() {
        moveJob?.cancel()
        moveJob = null
        targetPosition = null
    }
    
    /**
     * 播放开心跳跃动画
     * 宠物会快速上下跳动，表达开心的情绪
     */
    private fun playHappyJumpAnimation() {
        // 如果已经在播放动画，不重复播放
        if (isPlayingAnimation) {
            android.util.Log.d("FloatingPetService", "动画正在播放中，跳过")
            return
        }
        
        // 取消任何正在进行的动画
        happyAnimationJob?.cancel()
        
        happyAnimationJob = serviceScope.launch {
            try {
                isPlayingAnimation = true
                android.util.Log.d("FloatingPetService", "🎉 开始播放开心跳跃动画！")
                
                // 保存原始位置
                val originalY = petLayoutParams.y
                
                // 跳跃参数
                val jumpHeight = 40  // 跳跃高度（像素）
                val jumpCount = 3    // 跳跃次数
                val jumpDuration = 150L  // 每次跳跃的上升/下降时间（毫秒）
                val jumpPause = 50L      // 跳跃之间的停顿
                
                // 执行跳跃动画
                repeat(jumpCount) { index ->
                    android.util.Log.d("FloatingPetService", "跳跃 ${index + 1}/$jumpCount")
                    
                    // 向上跳（使用缓动效果）
                    val upSteps = 8
                    repeat(upSteps) { step ->
                        if (!isPlayingAnimation) return@launch  // 如果被取消，提前退出
                        
                        // 使用抛物线缓动（先快后慢）
                        val progress = (step + 1).toFloat() / upSteps
                        val easedProgress = 1f - (1f - progress) * (1f - progress)
                        val offsetY = (jumpHeight * easedProgress).toInt()
                        
                        petLayoutParams.y = originalY - offsetY
                        petPositionY.value = petLayoutParams.y
                        windowManager.updateViewLayout(petFloatingView, petLayoutParams)
                        
                        delay(jumpDuration / upSteps)
                    }
                    
                    // 向下落（使用缓动效果）
                    val downSteps = 8
                    repeat(downSteps) { step ->
                        if (!isPlayingAnimation) return@launch  // 如果被取消，提前退出
                        
                        // 使用抛物线缓动（先慢后快）
                        val progress = (step + 1).toFloat() / downSteps
                        val easedProgress = progress * progress
                        val offsetY = jumpHeight - (jumpHeight * easedProgress).toInt()
                        
                        petLayoutParams.y = originalY - offsetY
                        petPositionY.value = petLayoutParams.y
                        windowManager.updateViewLayout(petFloatingView, petLayoutParams)
                        
                        delay(jumpDuration / downSteps)
                    }
                    
                    // 确保回到原始位置
                    petLayoutParams.y = originalY
                    petPositionY.value = originalY
                    windowManager.updateViewLayout(petFloatingView, petLayoutParams)
                    
                    // 跳跃之间的停顿（最后一次跳跃后不停顿）
                    if (index < jumpCount - 1) {
                        delay(jumpPause)
                    }
                }
                
                android.util.Log.d("FloatingPetService", "✅ 开心跳跃动画完成！")
                
            } catch (e: Exception) {
                android.util.Log.e("FloatingPetService", "播放开心动画失败", e)
            } finally {
                isPlayingAnimation = false
            }
        }
    }
    
    /**
     * 停止开心跳跃动画
     */
    private fun stopHappyJumpAnimation() {
        happyAnimationJob?.cancel()
        happyAnimationJob = null
        isPlayingAnimation = false
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                Constants.Notification.CHANNEL_ID,
                Constants.Notification.CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, Constants.Notification.CHANNEL_ID)
                .setContentTitle(Constants.Notification.TITLE)
                .setContentText(Constants.Notification.CONTENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle(Constants.Notification.TITLE)
                .setContentText(Constants.Notification.CONTENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }
    
    /**
     * 启动宠物状态更新
     */
    private fun startPetUpdates() {
        serviceScope.launch {
            while (true) {
                delay(Constants.UpdateConfig.UPDATE_INTERVAL_MS)
                
                // 自动更新状态
                petManager.autoUpdateState()
                
                // 应用健康值衰减（移动时衰减更快）
                petManager.applyHealthDecay(isMoving = isCurrentlyMoving)
            }
        }
    }
    
    /**
     * 启动弹幕生成器（自动模式）
     */
    private fun startDanmakuListener() {
        // 检查用户设置
        val userEnabled = petManager.getDanmakuEnabled()
        if (!userEnabled) {
            android.util.Log.d("FloatingPetService", "弹幕已被用户禁用")
            return
        }
        
        android.util.Log.d("FloatingPetService", "弹幕生成器启动（手动触发模式）")
        
        stopDanmakuGenerator()
        
        danmakuJob = serviceScope.launch {
            while (true) {
                delay(100) // 每100ms检查一次
                
                // 检查是否需要触发弹幕爆发
                if (triggerDanmakuBurst) {
                    triggerDanmakuBurst = false
                    burstDanmaku()
                }
            }
        }
    }
    
    /**
     * 弹幕爆发！一次性涌现多条弹幕
     */
    private fun burstDanmaku() {
        android.util.Log.d("FloatingPetService", "💬 弹幕爆发！一股脑涌现 ${Constants.Danmaku.BURST_COUNT} 条")
        
        // 确保弹幕窗口已添加到屏幕
        ensureDanmakuWindowAdded()
        
        serviceScope.launch {
            // 获取屏幕尺寸（px转换为dp）
            val screenSize = movementHelper.getScreenSize()
            val density = resources.displayMetrics.density
            val screenWidth = (screenSize.x / density).dp
            val screenHeight = (screenSize.y / density).dp
            
            android.util.Log.d("FloatingPetService", "屏幕尺寸: $screenWidth x $screenHeight (密度: $density)")
            
            // 一次性批量生成所有弹幕（不重复文本，随机位置）✨
            @Suppress("SpellCheckingInspection")
            val danmakus = DanmakuGenerator.generateBatch(
                Constants.Danmaku.BURST_COUNT,
                screenWidth,
                screenHeight
            )
            
            android.util.Log.d("FloatingPetService", "批量生成 ${danmakus.size} 条弹幕")
            
            // 逐条添加到列表，形成"涌现"的效果
            danmakus.forEachIndexed { index, danmaku ->
                activeDanmakuList.add(danmaku)
                android.util.Log.d("FloatingPetService", "添加弹幕 ${index + 1}: ${danmaku.text} at (${danmaku.x}, ${danmaku.y})")
                
                // 每条弹幕之间稍微延迟一点
                if (index < danmakus.size - 1) {
                    delay(Constants.Danmaku.BURST_DELAY_MS)
                }
            }
        }
    }
    
    /**
     * 触发弹幕爆发（供外部调用）
     */
    fun triggerDanmakuBurst() {
        triggerDanmakuBurst = true
    }
    
    
    /**
     * 启动对话监听器
     */
    private fun startSpeechMonitor() {
        android.util.Log.d("FloatingPetService", "启动对话监听器（智能模式）")
        
        stopSpeechMonitor()
        
        speechJob = serviceScope.launch {
            while (true) {
                delay(5000) // 每5秒检查一次
                
                // 检查手动触发的说话
                if (triggerSpeechAction != null) {
                    val trigger = triggerSpeechAction
                    triggerSpeechAction = null
                    showSpeech(trigger)
                    continue
                }
                
                // 智能自动说话：优先处理紧急状态（低于20），然后处理一般低状态（低于30）
                val pet = petManager.pet.value
                
                // ⭐ 紧急状态检查（低于20）- 更频繁提醒（30秒间隔）
                val isUrgent = pet.hunger < Constants.HealthThresholds.LOW_STATUS_ALERT_THRESHOLD ||
                              pet.thirst < Constants.HealthThresholds.LOW_STATUS_ALERT_THRESHOLD ||
                              pet.sleep < Constants.HealthThresholds.LOW_STATUS_ALERT_THRESHOLD ||
                              pet.happiness < Constants.HealthThresholds.LOW_STATUS_ALERT_THRESHOLD
                
                if (isUrgent && SpeechGenerator.shouldSpeak(lastSpeechTime, minInterval = 30000L)) {
                    // 紧急状态：30秒提醒一次
                    showSpeech(null)
                    continue
                }
                
                // 一般低状态检查（低于30）- 正常频率（60秒间隔）
                if (SpeechGenerator.shouldSpeak(lastSpeechTime, minInterval = 60000L)) {
                    // 只在以下状态下自动说话：
                    val shouldAutoSpeak = when {
                        pet.hunger < 30 -> true  // 饥饿
                        pet.thirst < 30 -> true  // 口渴
                        pet.sleep < 30 -> true   // 疲劳
                        pet.happiness < 30 -> true  // 不开心
                        else -> false
                    }
                    
                    if (shouldAutoSpeak) {
                        val speech = SpeechGenerator.generateSpeech(pet)
                        if (speech != null) {
                            ensureSpeechWindowAdded()  // 确保窗口已添加
                            currentSpeech.value = speech
                            lastSpeechTime = System.currentTimeMillis()
                            android.util.Log.d("FloatingPetService", "💬 宠物主动说话（状态不佳）: ${speech.text}")
                            
                            // 在对话持续时间后自动清空对话并检查是否需要移除窗口
                            serviceScope.launch {
                                delay(speech.duration)
                                if (currentSpeech.value == speech) {
                                    currentSpeech.value = null
                                    removeSpeechWindowIfEmpty()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 显示对话（根据触发原因）
     */
    private fun showSpeech(trigger: SpeechTrigger?) {
        val pet = petManager.pet.value
        val speech = SpeechGenerator.generateSpeech(pet, trigger)
        if (speech != null) {
            ensureSpeechWindowAdded()  // 确保窗口已添加
            currentSpeech.value = speech
            lastSpeechTime = System.currentTimeMillis()
            android.util.Log.d("FloatingPetService", "💬 宠物说话: ${speech.text}")
            
            // 在对话持续时间后自动清空对话并检查是否需要移除窗口
            serviceScope.launch {
                delay(speech.duration)
                if (currentSpeech.value == speech) {
                    currentSpeech.value = null
                    removeSpeechWindowIfEmpty()
                }
            }
        }
    }
    
    /**
     * 触发说话（供外部调用）
     */
    fun triggerSpeech(trigger: SpeechTrigger? = null) {
        triggerSpeechAction = trigger ?: SpeechTrigger.RANDOM
    }
    
    /**
     * 停止对话监听器
     */
    private fun stopSpeechMonitor() {
        speechJob?.cancel()
        speechJob = null
        currentSpeech.value = null
    }
    
    /**
     * 停止弹幕生成器
     */
    private fun stopDanmakuGenerator() {
        danmakuJob?.cancel()
        danmakuJob = null
        activeDanmakuList.clear()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        android.util.Log.d("FloatingPetService", "🔴 服务销毁，开始清理资源")
        
        try {
            // 1. 停止所有协程任务
            stopAutoMovement()
            stopHappyJumpAnimation()
            stopDanmakuGenerator()
            stopSpeechMonitor()
            
            // 2. 取消协程作用域（重要！防止内存泄漏）
            serviceScope.cancel()
            android.util.Log.d("FloatingPetService", "✅ 协程作用域已取消")
            
            // 3. 移除所有窗口
            removeAllWindows()
            
            android.util.Log.d("FloatingPetService", "✅ 资源清理完成")
        } catch (e: Exception) {
            android.util.Log.e("FloatingPetService", "❌ 清理资源时出错", e)
        }
    }
    
    /**
     * 移除所有窗口
     */
    private fun removeAllWindows() {
        // 移除宠物窗口
        if (::petFloatingView.isInitialized) {
            try {
                windowManager.removeView(petFloatingView)
                android.util.Log.d("FloatingPetService", "✅ 宠物窗口已移除")
            } catch (e: Exception) {
                android.util.Log.w("FloatingPetService", "移除宠物窗口失败", e)
            }
        }
        
        // 移除弹幕窗口（只有在已添加时才移除）
        synchronized(this) {
            if (::danmakuFloatingView.isInitialized && isDanmakuWindowAdded) {
                try {
                    windowManager.removeView(danmakuFloatingView)
                    isDanmakuWindowAdded = false
                    android.util.Log.d("FloatingPetService", "✅ 弹幕窗口已移除")
                } catch (e: Exception) {
                    android.util.Log.w("FloatingPetService", "移除弹幕窗口失败", e)
                }
            }
        }
        
        // 移除对话窗口（只有在已添加时才移除）
        synchronized(this) {
            if (::speechFloatingView.isInitialized && isSpeechWindowAdded) {
                try {
                    windowManager.removeView(speechFloatingView)
                    isSpeechWindowAdded = false
                    android.util.Log.d("FloatingPetService", "✅ 对话窗口已移除")
                } catch (e: Exception) {
                    android.util.Log.w("FloatingPetService", "移除对话窗口失败", e)
                }
            }
        }
    }
}

