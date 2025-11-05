package com.example.fatcat.utils

/**
 * 应用常量定义
 * 集中管理所有常量，便于维护和修改
 */
object Constants {
    
    // ==================== 数据存储相关 ====================
    object Storage {
        const val PREF_NAME = "pet_prefs"
        const val KEY_PET_NAME = "pet_name"
        const val KEY_PET_GENDER = "pet_gender"
        const val KEY_PET_BIRTHDAY = "pet_birthday"
        const val KEY_PET_PERSONALITY = "pet_personality"
        const val KEY_PET_HOBBY = "pet_hobby"
        const val KEY_PET_STATE = "pet_state"
        const val KEY_PET_HUNGER = "pet_hunger"
        const val KEY_PET_THIRST = "pet_thirst"
        const val KEY_PET_SLEEP = "pet_sleep"
        const val KEY_PET_HAPPINESS = "pet_happiness"
        const val KEY_DANMAKU_ENABLED = "danmaku_enabled"  // 弹幕开关状态
    }
    
    // ==================== 宠物默认值 ====================
    object PetDefaults {
        const val DEFAULT_NAME = "肥波波"
        const val DEFAULT_PERSONALITY = "活泼"
        const val DEFAULT_HOBBY = "睡觉"
        const val DEFAULT_HEALTH_VALUE = 80
        const val MIN_HEALTH_VALUE = 0
        const val MAX_HEALTH_VALUE = 100
    }
    
    // ==================== 健康值阈值 ====================
    object HealthThresholds {
        const val SLEEP_THRESHOLD_LOW = 30      // 低于此值会睡觉
        const val SLEEP_THRESHOLD_CRITICAL = 20 // 低于此值强制睡觉
        const val SLEEP_THRESHOLD_RECOVER = 50  // 高于此值可以恢复活动
        const val MOVE_THRESHOLD = 20           // 精力值大于此值可以移动（新增）
        const val HAPPINESS_THRESHOLD_LOW = 30  // 低于此值会悲伤
        const val HAPPINESS_THRESHOLD_HIGH = 80 // 高于此值会开心
        
        // 低状态提醒阈值（宠物会主动提醒主人）⭐
        const val LOW_STATUS_ALERT_THRESHOLD = 20  // 低于此值会主动提醒主人照顾
    }
    
    // ==================== 更新间隔和衰减值 ====================
    object UpdateConfig {
        const val UPDATE_INTERVAL_MS = 5000L    // 更新间隔（毫秒）= 5秒
        
        // ========== 基础衰减速率（最优配置：8小时安全线）==========
        // 设计理念："8小时安全线" - 上班/上学/睡觉8小时不会造成严重问题
        // 参考业界最佳实践：桌面宠物应该是轻度养成，4-6小时照顾一次
        // 移动时精力值下降最快（4小时），饥饿和口渴第二（5.6小时），快乐第三（7.7小时）
        
        // 移动时的衰减速率（活动状态）- 符合现实的差异化设计 ⭐
        const val SLEEP_DECAY_RATE_MOVING = 0.035f      // 精力值衰减（移动时）- 第一快，4小时降完
        const val THIRST_DECAY_RATE_MOVING = 0.030f     // 口渴值衰减（移动时）- 第二快，4.6小时降完 ⭐ 运动失水快
        const val HUNGER_DECAY_RATE_MOVING = 0.020f     // 饥饿值衰减（移动时）- 第三快，6.9小时降完 ⭐ 运动对饥饿影响小
        const val HAPPINESS_DECAY_RATE_MOVING = 0.018f  // 快乐值衰减（移动时）- 最慢，7.7小时降完
        
        // 预计衰减时间（假设一直移动）：
        // 精力值：100 ÷ 0.035 × 5秒 = 14286秒 ≈ 238分钟 ≈ 4.0小时 ⚡ 运动消耗最快
        // 口渴值：100 ÷ 0.030 × 5秒 = 16667秒 ≈ 278分钟 ≈ 4.6小时 💧 运动散热失水
        // 饥饿值：100 ÷ 0.020 × 5秒 = 25000秒 ≈ 417分钟 ≈ 6.9小时 🍖 猫是短跑动物，运动不显著增加饥饿
        // 快乐值：100 ÷ 0.018 × 5秒 = 27778秒 ≈ 463分钟 ≈ 7.7小时 😊 情绪状态
        // 平均：(238 + 278 + 417 + 463) ÷ 4 = 349分钟 ≈ 5.8小时 ✓
        
        // 静止时的衰减速率（为移动速率的约30%，确保长时间挂机也能坚持）
        const val SLEEP_DECAY_RATE_IDLE = 0.011f        // 精力值衰减（静止时）- 12.6小时降完
        const val THIRST_DECAY_RATE_IDLE = 0.010f       // 口渴值衰减（静止时）- 13.9小时降完 ⭐ 呼吸失水
        const val HUNGER_DECAY_RATE_IDLE = 0.008f       // 饥饿值衰减（静止时）- 17.4小时降完 ⭐ 基础代谢
        const val HAPPINESS_DECAY_RATE_IDLE = 0.005f    // 快乐值衰减（静止时）- 27.8小时降完
        
        // 预计静止时衰减时间：
        // 精力值：100 ÷ 0.011 × 5秒 = 45455秒 ≈ 758分钟 ≈ 12.6小时
        // 口渴值：100 ÷ 0.010 × 5秒 = 50000秒 ≈ 833分钟 ≈ 13.9小时 💧 呼吸持续失水
        // 饥饿值：100 ÷ 0.008 × 5秒 = 62500秒 ≈ 1042分钟 ≈ 17.4小时 🍖 基础代谢消耗
        // 快乐值：100 ÷ 0.005 × 5秒 = 100000秒 ≈ 1667分钟 ≈ 27.8小时 😊 长时间无互动
        // ✅ 8小时上班/睡觉后，精力值剩余约37%，其他状态更健康
        
        // 睡眠时的速率 ⭐ 关键优化：睡眠时口渴和饥饿也会缓慢下降，符合现实
        const val SLEEP_RECOVERY_RATE = 1.0f            // 精力恢复速率（每5秒+1点）
        const val THIRST_DECAY_RATE_SLEEP = 0.005f      // 口渴值衰减（睡眠时）⭐ 呼吸失水
        const val HUNGER_DECAY_RATE_SLEEP = 0.004f      // 饥饿值衰减（睡眠时）⭐ 基础代谢
        
        // 睡眠8小时后的状态变化：
        // 精力值：恢复到100% ✅
        // 口渴值：100 - (0.005 × 576次) = 71.2% ✅ 睡醒后会口渴
        // 饥饿值：100 - (0.004 × 576次) = 77.0% ✅ 睡醒后会饿
        // 快乐值：不变 ✅ 睡眠不影响情绪
    }
    
    // ==================== 互动奖励值 ====================
    object InteractionRewards {
        const val PAT_HEAD_HAPPINESS = 10       // 摸头增加的快乐值
        const val HUG_HAPPINESS = 15           // 拥抱增加的快乐值
        const val FEED_HUNGER = 20             // 喂食增加的饥饿值
        const val FEED_WATER_THIRST = 20       // 喂水增加的口渴值
    }
    
    // ==================== 浮动窗口配置 ====================
    object FloatingWindow {
        const val WINDOW_WIDTH = 200           // 窗口宽度（像素）
        const val WINDOW_HEIGHT = 200          // 窗口高度（像素）
        const val INITIAL_X = 100             // 初始X坐标
        const val INITIAL_Y = 100             // 初始Y坐标
    }
    
    // ==================== 宠物大小配置 ====================
    object PetSize {
        const val SIZE_SMALL = 100            // 小（100像素）
        const val SIZE_MEDIUM = 150           // 中（150像素）
        const val SIZE_LARGE = 200            // 大（200像素）
        const val SIZE_XLARGE = 250           // 超大（250像素）
        const val DEFAULT_SIZE = SIZE_MEDIUM  // 默认大小
        
        const val KEY_PET_SIZE = "pet_size"   // 存储键
    }
    
    // ==================== 移动配置 ====================
    object Movement {
        const val AUTO_MOVE_ENABLED = true     // 是否启用自动移动
        const val MOVE_INTERVAL_MS = 1000L     // 移动间隔（毫秒）- 从3秒改为1秒，提高频率
        const val MOVE_SPEED = 8               // 移动速度（像素/步）- 从5改为8，移动更快
        const val MIN_MOVE_DISTANCE = 50       // 最小移动距离（像素）
        const val MAX_MOVE_DISTANCE = 200      // 最大移动距离（像素）
        const val BOUNDARY_MARGIN = 20         // 边界边距（像素）
        const val ENABLE_DRAG = true           // 是否允许拖动
    }
    
    // ==================== 通知配置 ====================
    object Notification {
        const val CHANNEL_ID = "pet_service_channel"
        const val CHANNEL_NAME = "宠物服务"
        const val NOTIFICATION_ID = 1
        const val TITLE = "肥波波正在运行"
        const val CONTENT = "桌面宠物正在显示"
    }
    
    // ==================== 弹幕配置 ====================
    object Danmaku {
        const val ENABLED = true                    // 是否启用弹幕
        const val AUTO_GENERATE = false             // 是否自动生成（false=手动点击按钮触发）
        const val BURST_COUNT = 35                  // 每次涌现的弹幕数量 ✨
        const val BURST_DELAY_MS = 100L             // 每条弹幕之间的延迟（毫秒）
    }
    
    // ==================== 等级系统配置 ====================
    object LevelSystem {
        const val MIN_LEVEL = 1                     // 最低等级
        const val MAX_LEVEL = 99                    // 最高等级
        const val EXP_BASE = 100                    // 基础经验值（Lv.1→Lv.2需要100exp）
        
        // 计算升级所需经验值：当前等级 × 基础经验值
        // Lv.1→Lv.2: 100, Lv.2→Lv.3: 200, Lv.3→Lv.4: 300...
        fun getExpForNextLevel(currentLevel: Int): Int {
            return currentLevel * EXP_BASE
        }
        
        const val KEY_PET_LEVEL = "pet_level"       // 存储键
        const val KEY_PET_EXP = "pet_exp"           // 存储键
    }
    
    // ==================== 猜拳游戏配置 ====================
    object RockPaperScissorsGame {
        // 经验值奖励
        const val EXP_WIN = 50                      // 胜利奖励经验值
        const val EXP_DRAW = 20                     // 平局奖励经验值
        const val EXP_LOSE = 10                     // 失败奖励经验值（参与奖）
        
        // 精力消耗
        const val ENERGY_COST_WIN = 5               // 胜利消耗精力值
        const val ENERGY_COST_DRAW = 3              // 平局消耗精力值
        const val ENERGY_COST_LOSE = 3              // 失败消耗精力值
        
        // 游戏统计存储键
        const val KEY_TOTAL_GAMES = "rps_total_games"
        const val KEY_WIN_COUNT = "rps_win_count"
        const val KEY_DRAW_COUNT = "rps_draw_count"
        const val KEY_LOSE_COUNT = "rps_lose_count"
    }
}

