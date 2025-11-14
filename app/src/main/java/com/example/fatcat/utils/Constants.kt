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
        const val KEY_PET_HEALTH = "pet_health"        // 健康值（精力值）
        const val KEY_PET_SATIETY = "pet_satiety"      // 饱腹值（v4.1新增）
        const val KEY_PET_THIRST = "pet_thirst"        // 口渴值（v4.1新增）
        const val KEY_PET_HAPPINESS = "pet_happiness"   // 开心值（v4.1新增）
        const val KEY_PET_SLEEP = "pet_sleep"          // 睡眠值（v4.1新增）
        const val KEY_FORCED_SLEEP_START = "forced_sleep_start" // 强制睡眠开始时间
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
    
    // ==================== 睡眠相关配置 ====================
    object Sleep {
        const val FORCED_SLEEP_MIN_DURATION_MS = 30 * 60 * 1000L // 强制睡眠至少持续30分钟
    }
    
    // ==================== 健康值阈值（v4.0简化版）===================
    object HealthThresholds {
        const val SLEEP_THRESHOLD = 20      // 低于此值强制睡觉
        const val RECOVER_THRESHOLD = 50    // 高于此值可以恢复活动
        const val MOVE_THRESHOLD = 20       // 健康值大于此值可以移动
        const val TIRED_THRESHOLD = 50      // 低于此值进入疲惫状态
    }
    
    // ==================== 更新间隔和衰减值（v4.1更新）===================
    object UpdateConfig {
        const val UPDATE_INTERVAL_MS = 5000L    // 更新间隔（毫秒）= 5秒
        
        // ========== v4.1 状态值系统 ==========
        // 设计理念：健康值（精力）+ 饱腹值 + 口渴值 + 开心值
        
        // 健康值（精力值）衰减速率
        const val HEALTH_DECAY_RATE = 1        // 健康值衰减速率（每5秒-1点）
        const val HEALTH_RECOVERY_RATE = 1     // 健康值恢复速率（睡觉时每5秒+1点）
        
        // 饱腹值衰减速率（100→0需要2小时）
        // 2小时 = 120分钟 = 7200秒 = 1440次更新 → 每次减 100/1440 = 0.0694
        const val SATIETY_DECAY_RATE = 0.0694f  // 饱腹值衰减速率（每5秒-0.0694点）
        
        // 口渴值衰减速率（100→0需要1.5小时）
        // 1.5小时 = 90分钟 = 5400秒 = 1080次更新 → 每次减 100/1080 = 0.0926
        const val THIRST_DECAY_RATE = 0.0926f   // 口渴值衰减速率（每5秒-0.0926点）
        
        // 开心值衰减速率（100→0需要1小时）
        // 1小时 = 60分钟 = 3600秒 = 720次更新 → 每次减 100/720 = 0.1389
        const val HAPPINESS_DECAY_RATE = 0.1389f // 开心值衰减速率（每5秒-0.1389点）
        
        // 睡眠值衰减速率（100→0需要4小时）
        // 4小时 = 240分钟 = 14400秒 = 2880次更新 → 每次减 100/2880 = 0.0347
        const val SLEEP_DECAY_RATE = 0.0347f     // 睡眠值衰减速率（每5秒-0.0347点）
        
        // 睡眠值恢复速率（目标：1小时回满）
        // 1小时 = 60分钟 = 3600秒 = 720次更新 → 每次加 100/720 = 0.1389
        const val SLEEP_RECOVERY_RATE = 0.1389f  // 睡眠中恢复速率（每5秒+0.1389点，约1小时回满）
        
        // 实际耗尽时间验证：
        // 饱腹值：100 ÷ 0.0694 × 5秒 = 7205秒 ≈ 120.1分钟 ≈ 2小时 ✅
        // 口渴值：100 ÷ 0.0926 × 5秒 = 5399秒 ≈ 90.0分钟 = 1.5小时 ✅
        // 开心值：100 ÷ 0.1389 × 5秒 = 3600秒 = 60分钟 = 1小时 ✅
        // 睡眠值：100 ÷ 0.0347 × 5秒 = 14409秒 ≈ 240.2分钟 ≈ 4小时 ✅
    }
    
    // ==================== 互动奖励值（v4.1更新）===================
    object InteractionRewards {
        const val PAT_HEAD_HAPPINESS = 10      // 摸头增加的开心值
        const val HUG_HAPPINESS = 15          // 拥抱增加的开心值
        const val FEED_SATIETY = 20            // 喂食增加的饱腹值
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
        
        // 状态提醒通知配置
        const val CHANNEL_ID_STATUS_ALERT = "pet_status_alert_channel"
        const val CHANNEL_NAME_STATUS_ALERT = "宠物状态提醒"
        const val NOTIFICATION_ID_STATUS_ALERT = 2
        const val STATUS_ALERT_THRESHOLD = 20  // 状态值低于此值时提醒
        const val NOTIFICATION_INTERVAL_MS = 5 * 60 * 1000L  // 通知间隔：5分钟
    }
    
    // ==================== 弹幕配置 ====================
    object Danmaku {
        const val ENABLED = true                    // 是否启用弹幕
        const val AUTO_GENERATE = false             // 是否自动生成（false=手动点击按钮触发）
        const val BURST_COUNT = 35                  // 每次涌现的弹幕数量 ✨
        const val BURST_DELAY_MS = 100L             // 每条弹幕之间的延迟（毫秒）
        const val CHECK_INTERVAL_MS = 100L          // 弹幕检查间隔（毫秒）
    }
    
    // ==================== 宠物说话配置 ====================
    object Speech {
        const val MONITOR_INTERVAL_MS = 5000L       // 说话监控间隔（毫秒）
        const val URGENT_INTERVAL_MS = 30000L       // 紧急状态提醒间隔（30秒）
        const val NORMAL_INTERVAL_MS = 60000L       // 一般状态提醒间隔（60秒）
        const val LOW_HEALTH_THRESHOLD = 30         // 低健康值阈值
    }
    
    // ==================== 动画配置 ====================
    object Animation {
        const val JUMP_HEIGHT = 40                  // 跳跃高度（像素）
        const val JUMP_COUNT = 3                    // 跳跃次数
        const val JUMP_DURATION_MS = 150L           // 每次跳跃的上升/下降时间（毫秒）
        const val JUMP_PAUSE_MS = 50L               // 跳跃之间的停顿（毫秒）
        const val JUMP_STEPS = 8                    // 跳跃动画步数
    }
    
    // ==================== 更新配置 ====================
    object Update {
        const val CONNECT_TIMEOUT_MS = 10000        // 连接超时（毫秒）
        const val READ_TIMEOUT_MS = 10000           // 读取超时（毫秒）
        const val DOWNLOAD_PROGRESS_INTERVAL_MS = 500L  // 下载进度更新间隔（毫秒）
    }
    
    // ==================== 移动配置扩展 ====================
    object MovementExtended {
        const val CENTER_TARGET_RANGE = 200         // 中心目标范围（像素）
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

