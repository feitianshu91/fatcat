package com.example.fatcat.data

import com.example.fatcat.model.Pet
import com.example.fatcat.model.PetState
import com.example.fatcat.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 宠物数据仓库
 * 职责：管理宠物数据和业务逻辑
 * 
 * 优点：
 * 1. 分离数据持久化和业务逻辑
 * 2. 提供统一的数据访问接口
 * 3. 便于单元测试
 */
class PetRepository private constructor(
    private val dataSource: PetLocalDataSource
) {
    
    private val _pet = MutableStateFlow(dataSource.loadPet())
    val pet: StateFlow<Pet> = _pet.asStateFlow()
    
    // 累积小数，用于精确计算衰减（v4.1修复）
    private var satietyAccumulator: Float = 0f    // 饱腹值累积小数
    private var thirstAccumulator: Float = 0f     // 口渴值累积小数
    private var happinessAccumulator: Float = 0f  // 开心值累积小数
    private var sleepAccumulator: Float = 0f      // 睡眠值衰减累积小数
    private var sleepRecoveryAccumulator: Float = 0f // 睡眠值恢复累积小数
    
    companion object {
        @Volatile
        private var INSTANCE: PetRepository? = null
        
        fun getInstance(dataSource: PetLocalDataSource): PetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PetRepository(dataSource).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 保存宠物数据
     */
    fun savePet(pet: Pet) {
        dataSource.savePet(pet)
        _pet.value = pet
    }
    
    /**
     * 更新宠物状态
     */
    fun updateState(state: PetState) {
        val currentPet = _pet.value
        savePet(currentPet.copy(state = state))
    }
    
    /**
     * 更新宠物健康值（v4.0简化版）
     * @param health 健康值（0-100）
     */
    fun updateHealth(health: Int? = null) {
        val currentPet = _pet.value
        if (health != null) {
            savePet(currentPet.copy(
                health = health.coerceIn(
                    Constants.PetDefaults.MIN_HEALTH_VALUE, 
                    Constants.PetDefaults.MAX_HEALTH_VALUE
                )
            ))
        }
    }
    
    private fun wakeFromForcedSleepIfNeeded(pet: Pet, newState: PetState = PetState.NORMAL): Pet {
        return pet.copy(
            isUserForcedSleep = false,
            forcedSleepStart = 0L,
            state = newState
        )
    }
    
    /**
     * 摸头互动（v4.1更新）
     * 如果宠物正在用户强制睡眠中，摸头会唤醒宠物
     * 摸头会增加开心值
     */
    fun patHead() {
        val currentPet = _pet.value
        
        // 如果是用户强制睡眠，摸头会唤醒宠物
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            val awakenedPet = wakeFromForcedSleepIfNeeded(currentPet).copy(
                happiness = (currentPet.happiness + Constants.InteractionRewards.PAT_HEAD_HAPPINESS)
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            )
            savePet(awakenedPet)
            updateState(PetState.HAPPY)
        } else {
            // 正常摸头增加开心值
            val newHappiness = (currentPet.happiness + Constants.InteractionRewards.PAT_HEAD_HAPPINESS)
                .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            savePet(currentPet.copy(happiness = newHappiness))
            updateState(PetState.HAPPY)
        }
    }
    
    /**
     * 拥抱互动（v4.1更新）
     * 拥抱会增加开心值
     */
    fun hug() {
        val currentPet = _pet.value
        
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            val awakenedPet = wakeFromForcedSleepIfNeeded(currentPet).copy(
                happiness = (currentPet.happiness + Constants.InteractionRewards.HUG_HAPPINESS)
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            )
            savePet(awakenedPet)
            updateState(PetState.HAPPY)
            return
        }
        
        val newHappiness = (currentPet.happiness + Constants.InteractionRewards.HUG_HAPPINESS)
            .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
        savePet(currentPet.copy(happiness = newHappiness))
        updateState(PetState.HAPPY)
    }
    
    /**
     * 喂食（v4.1更新）
     * 喂食会增加饱腹值
     */
    fun feed() {
        val currentPet = _pet.value
        
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            val awakenedPet = wakeFromForcedSleepIfNeeded(currentPet).copy(
                satiety = (currentPet.satiety + Constants.InteractionRewards.FEED_SATIETY)
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            )
            savePet(awakenedPet)
            return
        }
        
        val newSatiety = (currentPet.satiety + Constants.InteractionRewards.FEED_SATIETY)
            .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
        savePet(currentPet.copy(satiety = newSatiety))
    }
    
    /**
     * 喂水（v4.1更新）
     * 喂水会增加口渴值
     */
    fun feedWater() {
        val currentPet = _pet.value
        
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            val awakenedPet = wakeFromForcedSleepIfNeeded(currentPet).copy(
                thirst = (currentPet.thirst + Constants.InteractionRewards.FEED_WATER_THIRST)
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            )
            savePet(awakenedPet)
            return
        }
        
        val newThirst = (currentPet.thirst + Constants.InteractionRewards.FEED_WATER_THIRST)
            .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
        savePet(currentPet.copy(thirst = newThirst))
    }
    
    /**
     * 用户强制宠物睡觉（v4.0简化版）
     * 宠物会一直睡到健康值满（100）才起床
     */
    fun forceSleep() {
        val currentPet = _pet.value
        savePet(currentPet.copy(
            state = PetState.SLEEP,
            isUserForcedSleep = true,
            forcedSleepStart = System.currentTimeMillis()
        ))
    }
    
    /**
     * 自动更新宠物状态（根据健康值）（v4.0简化版）
     */
    fun autoUpdateState() {
        val currentPet = _pet.value
        
        // ⭐ 用户强制睡眠：宠物会一直睡到健康值满（100）才起床
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            val forcedStart = currentPet.forcedSleepStart
            val elapsed = if (forcedStart > 0L) {
                System.currentTimeMillis() - forcedStart
            } else {
                Long.MAX_VALUE
            }
            val hasSleptLongEnough = elapsed >= Constants.Sleep.FORCED_SLEEP_MIN_DURATION_MS
            
            if (currentPet.health >= Constants.PetDefaults.MAX_HEALTH_VALUE && hasSleptLongEnough) {
                val awakenedPet = wakeFromForcedSleepIfNeeded(currentPet)
                savePet(awakenedPet)
            }
            // 否则继续睡觉，不做任何状态改变
            return
        }
        
        // 强制睡觉：健康值低于临界值时，强制进入睡眠状态
        if (currentPet.health < Constants.HealthThresholds.SLEEP_THRESHOLD) {
            if (currentPet.state != PetState.SLEEP) {
                updateState(PetState.SLEEP)
            }
            return
        }
        
        // 睡眠恢复中：健康值在临界值和恢复值之间，保持睡眠直到恢复
        if (currentPet.health < Constants.HealthThresholds.RECOVER_THRESHOLD 
            && currentPet.state == PetState.SLEEP) {
            // 继续睡觉，不改变状态
            return
        }
        
        // 已经睡够了，可以醒来
        if (currentPet.health >= Constants.HealthThresholds.RECOVER_THRESHOLD 
            && currentPet.state == PetState.SLEEP) {
            updateState(PetState.NORMAL)
            return
        }
        
        // 疲惫状态：健康值在20-50之间，进入疲惫状态
        if (currentPet.health < Constants.HealthThresholds.TIRED_THRESHOLD 
            && currentPet.health >= Constants.HealthThresholds.SLEEP_THRESHOLD
            && currentPet.state != PetState.DAZE) {
            updateState(PetState.DAZE)
            return
        }
        
        // 默认状态（随机切换常态和发呆）
        // 只有在健康值充足时才会在常态和发呆之间切换
        if (currentPet.health >= Constants.HealthThresholds.RECOVER_THRESHOLD) {
            val random = (System.currentTimeMillis() / Constants.UpdateConfig.UPDATE_INTERVAL_MS) % 2
            if (random == 0L && currentPet.state != PetState.DAZE) {
                updateState(PetState.DAZE)
            } else if (random != 0L && currentPet.state != PetState.NORMAL) {
                updateState(PetState.NORMAL)
            }
        }
    }
    
    /**
     * 应用健康值衰减（v4.1更新）
     * 规则：
     * - 健康值：每5秒减少，移动时减少更多，睡觉时恢复
     * - 饱腹值：每5秒减少0.0694点（100→0需要2小时）
     * - 口渴值：每5秒减少0.0926点（100→0需要1.5小时）
     * - 开心值：每5秒减少0.1389点（100→0需要1小时）
     * - 睡眠值：每5秒减少0.0347点（100→0需要4小时）
     * - 睡眠时：健康值和睡眠值都会逐步恢复
     */
    fun applyHealthDecay(isMoving: Boolean = false) {
        val pet = _pet.value
        
        android.util.Log.d("PetRepository", "=== 状态值衰减 ===")
        android.util.Log.d("PetRepository", "当前状态: ${pet.state}, 健康值: ${pet.health}, 饱腹值: ${pet.satiety}, 口渴值: ${pet.thirst}, 开心值: ${pet.happiness}, 睡眠值: ${pet.sleep}, 是否移动: $isMoving")
        
        // 睡眠状态：恢复健康值，其他状态值不变
        if (pet.state == PetState.SLEEP) {
            val newHealth = (pet.health + Constants.UpdateConfig.HEALTH_RECOVERY_RATE)
                .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            
            // 睡眠中恢复睡眠值（带小数累积）
            sleepAccumulator = 0f // 清零衰减累积，避免苏醒后突降
            sleepRecoveryAccumulator += Constants.UpdateConfig.SLEEP_RECOVERY_RATE
            val sleepIncrease = sleepRecoveryAccumulator.toInt()
            sleepRecoveryAccumulator -= sleepIncrease.toFloat()
            val newSleep = (pet.sleep + sleepIncrease)
                .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            
            android.util.Log.d(
                "PetRepository",
                "睡眠中 - 健康值恢复: ${pet.health} → $newHealth，睡眠值恢复: ${pet.sleep} → $newSleep"
            )
            savePet(pet.copy(
                health = newHealth,
                sleep = newSleep
            ))
        } else {
            // 非睡眠状态：所有状态值都衰减
            val newHealth = calculateHealthDecay(pet.health, isMoving)
            
            // 饱腹值：持续衰减（100→0需要2小时）- 使用累积小数机制
            val (newSatiety, newSatietyAccumulator) = calculateValueDecay(
                currentValue = pet.satiety,
                accumulator = satietyAccumulator,
                decayRate = Constants.UpdateConfig.SATIETY_DECAY_RATE
            )
            satietyAccumulator = newSatietyAccumulator
            
            // 口渴值：持续衰减（100→0需要1.5小时）- 使用累积小数机制
            val (newThirst, newThirstAccumulator) = calculateValueDecay(
                currentValue = pet.thirst,
                accumulator = thirstAccumulator,
                decayRate = Constants.UpdateConfig.THIRST_DECAY_RATE
            )
            thirstAccumulator = newThirstAccumulator
            
            // 开心值：持续衰减（100→0需要1小时）- 使用累积小数机制
            val (newHappiness, newHappinessAccumulator) = calculateValueDecay(
                currentValue = pet.happiness,
                accumulator = happinessAccumulator,
                decayRate = Constants.UpdateConfig.HAPPINESS_DECAY_RATE
            )
            happinessAccumulator = newHappinessAccumulator
            
            // 睡眠值：持续衰减（100→0需要4小时）- 使用累积小数机制
            sleepRecoveryAccumulator = 0f // 清零恢复累积，避免醒来后突增
            val (newSleep, newSleepAccumulator) = calculateValueDecay(
                currentValue = pet.sleep,
                accumulator = sleepAccumulator,
                decayRate = Constants.UpdateConfig.SLEEP_DECAY_RATE
            )
            sleepAccumulator = newSleepAccumulator
            
            android.util.Log.d("PetRepository", "非睡眠状态 - 状态值减少: 健康值 ${pet.health} → $newHealth, 饱腹值 ${pet.satiety} → $newSatiety, 口渴值 ${pet.thirst} → $newThirst, 开心值 ${pet.happiness} → $newHappiness, 睡眠值 ${pet.sleep} → $newSleep")
            
            savePet(pet.copy(
                health = newHealth,
                satiety = newSatiety,
                thirst = newThirst,
                happiness = newHappiness,
                sleep = newSleep
            ))
        }
    }
    
    /**
     * 计算健康值衰减
     * @param currentHealth 当前健康值
     * @param isMoving 是否正在移动
     * @return 衰减后的健康值
     */
    private fun calculateHealthDecay(currentHealth: Int, isMoving: Boolean): Int {
        val healthDecayRate = if (isMoving) {
            (Constants.UpdateConfig.HEALTH_DECAY_RATE * 1.5f).toInt()  // 移动时减少更多（1.5倍）
        } else {
            Constants.UpdateConfig.HEALTH_DECAY_RATE  // 静止时正常减少
        }
        return (currentHealth - healthDecayRate)
            .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
    }
    
    /**
     * 计算状态值衰减（带累积小数机制）
     * @param currentValue 当前值
     * @param accumulator 累积小数
     * @param decayRate 衰减速率
     * @return Pair<新值, 新累积小数>
     */
    private fun calculateValueDecay(
        currentValue: Int,
        accumulator: Float,
        decayRate: Float
    ): Pair<Int, Float> {
        var newAccumulator = accumulator + decayRate
        val decrease = newAccumulator.toInt()  // 取出整数部分
        newAccumulator -= decrease.toFloat()   // 保留小数部分
        val newValue = (currentValue - decrease)
            .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
        return Pair(newValue, newAccumulator)
    }
    
    /**
     * 检查宠物是否可以移动（v4.0简化版）
     * 只有在常态时且健康值足够时才能移动
     */
    fun canMove(): Boolean {
        val pet = _pet.value
        return pet.state == PetState.NORMAL && pet.health >= Constants.HealthThresholds.MOVE_THRESHOLD
    }
    
    /**
     * 重置宠物数据
     */
    fun resetPet() {
        dataSource.clearPetData()
        _pet.value = dataSource.loadPet()
    }
    
    /**
     * 获取宠物大小
     */
    fun getPetSize(): Int {
        return dataSource.getPetSize()
    }
    
    /**
     * 设置宠物大小
     */
    fun setPetSize(size: Int) {
        dataSource.savePetSize(size)
    }
    
    /**
     * 获取弹幕开关状态
     */
    fun getDanmakuEnabled(): Boolean {
        return dataSource.getDanmakuEnabled()
    }
    
    /**
     * 设置弹幕开关状态
     */
    fun setDanmakuEnabled(enabled: Boolean) {
        dataSource.saveDanmakuEnabled(enabled)
    }
    
    // ==================== 等级系统 ====================
    
    /**
     * 添加经验值并检查是否升级
     * @param expAmount 经验值数量
     * @return 是否升级了
     */
    fun addExp(expAmount: Int): Boolean {
        val currentPet = _pet.value
        
        // 已达到最高等级，不再获得经验
        if (currentPet.level >= Constants.LevelSystem.MAX_LEVEL) {
            return false
        }
        
        var newExp = currentPet.exp + expAmount
        var newLevel = currentPet.level
        var leveledUp = false
        
        // 循环检查是否可以升级（可能一次获得足够多经验升多级）
        while (newExp >= Constants.LevelSystem.getExpForNextLevel(newLevel) 
               && newLevel < Constants.LevelSystem.MAX_LEVEL) {
            
            newExp -= Constants.LevelSystem.getExpForNextLevel(newLevel)
            newLevel++
            leveledUp = true
            
            // 升级时恢复健康值到最大值
            _pet.value = currentPet.copy(
                health = Constants.PetDefaults.MAX_HEALTH_VALUE,
                level = newLevel,
                exp = newExp
            )
            
            dataSource.savePet(_pet.value)
        }
        
        // 如果没有升级，只是增加了经验值
        if (!leveledUp) {
            _pet.value = currentPet.copy(exp = newExp)
            dataSource.savePet(_pet.value)
        }
        
        return leveledUp
    }
    
    /**
     * 获取升级所需经验值
     */
    fun getExpForNextLevel(): Int {
        return Constants.LevelSystem.getExpForNextLevel(_pet.value.level)
    }
    
    /**
     * 获取升级进度（0.0 - 1.0）
     */
    fun getLevelProgress(): Float {
        val currentPet = _pet.value
        if (currentPet.level >= Constants.LevelSystem.MAX_LEVEL) {
            return 1.0f
        }
        val requiredExp = Constants.LevelSystem.getExpForNextLevel(currentPet.level)
        return currentPet.exp.toFloat() / requiredExp.toFloat()
    }
    
    // ==================== 猜拳游戏 ====================
    
    /**
     * 玩猜拳游戏
     * @param result 游戏结果：WIN, DRAW, LOSE
     */
    fun playRockPaperScissorsGame(result: GameResult): GameReward {
        val currentPet = _pet.value
        
        // 根据结果获取奖励和消耗
        val (expReward, energyCost) = when (result) {
            GameResult.WIN -> Pair(
                Constants.RockPaperScissorsGame.EXP_WIN,
                Constants.RockPaperScissorsGame.ENERGY_COST_WIN
            )
            GameResult.DRAW -> Pair(
                Constants.RockPaperScissorsGame.EXP_DRAW,
                Constants.RockPaperScissorsGame.ENERGY_COST_DRAW
            )
            GameResult.LOSE -> Pair(
                Constants.RockPaperScissorsGame.EXP_LOSE,
                Constants.RockPaperScissorsGame.ENERGY_COST_LOSE
            )
        }
        
        // 消耗健康值
        val newHealth = (currentPet.health - energyCost).coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
        _pet.value = currentPet.copy(health = newHealth)
        dataSource.savePet(_pet.value)
        
        // 添加经验值并检查是否升级
        val leveledUp = addExp(expReward)
        
        // 更新游戏统计
        val stats = dataSource.getGameStats()
        val newStats = when (result) {
            GameResult.WIN -> stats.copy(
                totalGames = stats.totalGames + 1,
                winCount = stats.winCount + 1
            )
            GameResult.DRAW -> stats.copy(
                totalGames = stats.totalGames + 1,
                drawCount = stats.drawCount + 1
            )
            GameResult.LOSE -> stats.copy(
                totalGames = stats.totalGames + 1,
                loseCount = stats.loseCount + 1
            )
        }
        dataSource.saveGameStats(newStats)
        
        return GameReward(
            expGained = expReward,
            energyCost = energyCost,
            leveledUp = leveledUp,
            newLevel = _pet.value.level
        )
    }
    
    /**
     * 获取游戏统计
     */
    fun getGameStats(): GameStats {
        return dataSource.getGameStats()
    }
}

/**
 * 游戏结果枚举
 */
enum class GameResult {
    WIN,    // 胜利
    DRAW,   // 平局
    LOSE    // 失败
}

/**
 * 游戏奖励数据类
 */
data class GameReward(
    val expGained: Int,      // 获得的经验值
    val energyCost: Int,     // 消耗的精力值
    val leveledUp: Boolean,  // 是否升级了
    val newLevel: Int        // 新等级
)

