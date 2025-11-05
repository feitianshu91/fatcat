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
     * 更新宠物健康值
     * @param hunger 饥饿值（0-100）
     * @param thirst 口渴值（0-100）
     * @param sleep 睡眠值（0-100）
     * @param happiness 快乐值（0-100）
     */
    fun updateHealth(
        hunger: Int? = null, 
        thirst: Int? = null, 
        sleep: Int? = null, 
        happiness: Int? = null
    ) {
        val currentPet = _pet.value
        savePet(currentPet.copy(
            hunger = hunger?.coerceIn(
                Constants.PetDefaults.MIN_HEALTH_VALUE, 
                Constants.PetDefaults.MAX_HEALTH_VALUE
            ) ?: currentPet.hunger,
            thirst = thirst?.coerceIn(
                Constants.PetDefaults.MIN_HEALTH_VALUE, 
                Constants.PetDefaults.MAX_HEALTH_VALUE
            ) ?: currentPet.thirst,
            sleep = sleep?.coerceIn(
                Constants.PetDefaults.MIN_HEALTH_VALUE, 
                Constants.PetDefaults.MAX_HEALTH_VALUE
            ) ?: currentPet.sleep,
            happiness = happiness?.coerceIn(
                Constants.PetDefaults.MIN_HEALTH_VALUE, 
                Constants.PetDefaults.MAX_HEALTH_VALUE
            ) ?: currentPet.happiness
        ))
    }
    
    /**
     * 摸头互动
     * 如果宠物正在用户强制睡眠中，摸头会唤醒宠物
     */
    fun patHead() {
        val currentPet = _pet.value
        
        // 如果是用户强制睡眠，摸头会唤醒宠物
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            savePet(currentPet.copy(
                isUserForcedSleep = false,
                state = PetState.NORMAL,
                happiness = (currentPet.happiness + Constants.InteractionRewards.PAT_HEAD_HAPPINESS)
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE)
            ))
        } else {
            // 正常摸头增加快乐值
            updateHealth(happiness = currentPet.happiness + Constants.InteractionRewards.PAT_HEAD_HAPPINESS)
            updateState(PetState.HAPPY)
        }
    }
    
    /**
     * 拥抱互动
     */
    fun hug() {
        val currentPet = _pet.value
        updateHealth(happiness = currentPet.happiness + Constants.InteractionRewards.HUG_HAPPINESS)
        updateState(PetState.HAPPY)
    }
    
    /**
     * 喂食
     */
    fun feed() {
        val currentPet = _pet.value
        updateHealth(hunger = currentPet.hunger + Constants.InteractionRewards.FEED_HUNGER)
    }
    
    /**
     * 喂水
     */
    fun feedWater() {
        val currentPet = _pet.value
        updateHealth(thirst = currentPet.thirst + Constants.InteractionRewards.FEED_WATER_THIRST)
    }
    
    /**
     * 用户强制宠物睡觉
     * 宠物会一直睡到精力值满（100）才起床
     */
    fun forceSleep() {
        val currentPet = _pet.value
        savePet(currentPet.copy(
            state = PetState.SLEEP,
            isUserForcedSleep = true
        ))
    }
    
    /**
     * 自动更新宠物状态（根据健康值）
     */
    fun autoUpdateState() {
        val currentPet = _pet.value
        
        // ⭐ 用户强制睡眠：宠物会一直睡到精力值满（100）才起床
        if (currentPet.isUserForcedSleep && currentPet.state == PetState.SLEEP) {
            // 检查是否精力值已满
            if (currentPet.sleep >= Constants.PetDefaults.MAX_HEALTH_VALUE) {
                // 精力值满了，可以醒来
                savePet(currentPet.copy(
                    isUserForcedSleep = false,
                    state = PetState.NORMAL
                ))
            }
            // 否则继续睡觉，不做任何状态改变
            return
        }
        
        // 强制睡觉：精力值低于临界值时，强制进入睡眠状态
        if (currentPet.sleep < Constants.HealthThresholds.SLEEP_THRESHOLD_CRITICAL) {
            if (currentPet.state != PetState.SLEEP) {
                updateState(PetState.SLEEP)
            }
            return
        }
        
        // 睡眠恢复中：精力值在临界值和恢复值之间，保持睡眠直到恢复
        if (currentPet.sleep < Constants.HealthThresholds.SLEEP_THRESHOLD_RECOVER 
            && currentPet.state == PetState.SLEEP) {
            // 继续睡觉，不改变状态
            return
        }
        
        // 普通睡眠：精力值较低时进入睡眠
        if (currentPet.sleep < Constants.HealthThresholds.SLEEP_THRESHOLD_LOW 
            && currentPet.state != PetState.SLEEP) {
            updateState(PetState.SLEEP)
            return
        }
        
        // 已经睡够了，可以醒来
        if (currentPet.sleep >= Constants.HealthThresholds.SLEEP_THRESHOLD_RECOVER 
            && currentPet.state == PetState.SLEEP) {
            updateState(PetState.NORMAL)
            return
        }
        
        // 根据快乐值决定表情
        if (currentPet.happiness < Constants.HealthThresholds.HAPPINESS_THRESHOLD_LOW 
            && currentPet.state != PetState.SAD) {
            updateState(PetState.SAD)
            return
        }
        
        if (currentPet.happiness > Constants.HealthThresholds.HAPPINESS_THRESHOLD_HIGH 
            && currentPet.state != PetState.HAPPY) {
            updateState(PetState.HAPPY)
            return
        }
        
        // 默认状态（随机切换常态和发呆）
        // 只有在精力值充足时才会在常态和发呆之间切换
        if (currentPet.sleep >= Constants.HealthThresholds.SLEEP_THRESHOLD_RECOVER 
            && currentPet.happiness in Constants.HealthThresholds.HAPPINESS_THRESHOLD_LOW..Constants.HealthThresholds.HAPPINESS_THRESHOLD_HIGH) {
            val random = (System.currentTimeMillis() / Constants.UpdateConfig.UPDATE_INTERVAL_MS) % 2
            if (random == 0L && currentPet.state != PetState.DAZE) {
                updateState(PetState.DAZE)
            } else if (random != 0L && currentPet.state != PetState.NORMAL) {
                updateState(PetState.NORMAL)
            }
        }
    }
    
    /**
     * 应用健康值衰减
     * @param isMoving 是否正在移动（移动时衰减更快）
     */
    fun applyHealthDecay(isMoving: Boolean = false) {
        val pet = _pet.value
        
        // 睡眠状态：恢复精力值，但口渴和饥饿会缓慢下降（符合现实）⭐
        if (pet.state == PetState.SLEEP) {
            updateHealth(
                sleep = (pet.sleep + Constants.UpdateConfig.SLEEP_RECOVERY_RATE).toInt()
                    .coerceAtMost(Constants.PetDefaults.MAX_HEALTH_VALUE),  // 睡觉时精力恢复
                thirst = (pet.thirst - Constants.UpdateConfig.THIRST_DECAY_RATE_SLEEP).toInt()  // ⭐ 睡眠时呼吸失水
                    .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                hunger = (pet.hunger - Constants.UpdateConfig.HUNGER_DECAY_RATE_SLEEP).toInt()  // ⭐ 睡眠时基础代谢
                    .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
                // 快乐值不变（睡眠不影响情绪）
            )
        } else {
            // 根据是否移动选择不同的衰减速率
            if (isMoving) {
                // 移动时：精力值下降最快，饥饿和口渴第二，快乐第三
                updateHealth(
                    hunger = (pet.hunger - Constants.UpdateConfig.HUNGER_DECAY_RATE_MOVING).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    thirst = (pet.thirst - Constants.UpdateConfig.THIRST_DECAY_RATE_MOVING).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    sleep = (pet.sleep - Constants.UpdateConfig.SLEEP_DECAY_RATE_MOVING).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    happiness = (pet.happiness - Constants.UpdateConfig.HAPPINESS_DECAY_RATE_MOVING).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
                )
            } else {
                // 静止时：缓慢衰减（为移动速率的30%）
                // 这样即使用户息屏一天，宠物也会有明显的状态变化
                updateHealth(
                    hunger = (pet.hunger - Constants.UpdateConfig.HUNGER_DECAY_RATE_IDLE).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    thirst = (pet.thirst - Constants.UpdateConfig.THIRST_DECAY_RATE_IDLE).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    sleep = (pet.sleep - Constants.UpdateConfig.SLEEP_DECAY_RATE_IDLE).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE),
                    happiness = (pet.happiness - Constants.UpdateConfig.HAPPINESS_DECAY_RATE_IDLE).toInt()
                        .coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
                )
            }
        }
    }
    
    /**
     * 检查宠物是否可以移动
     * 只有在常态时且精力值足够时才能移动
     */
    fun canMove(): Boolean {
        val pet = _pet.value
        return pet.state == PetState.NORMAL && pet.sleep >= Constants.HealthThresholds.MOVE_THRESHOLD
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
            
            // 升级时恢复所有属性到最大值
            _pet.value = currentPet.copy(
                hunger = Constants.PetDefaults.MAX_HEALTH_VALUE,
                thirst = Constants.PetDefaults.MAX_HEALTH_VALUE,
                sleep = Constants.PetDefaults.MAX_HEALTH_VALUE,
                happiness = Constants.PetDefaults.MAX_HEALTH_VALUE,
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
        
        // 消耗精力值
        val newSleep = (currentPet.sleep - energyCost).coerceAtLeast(Constants.PetDefaults.MIN_HEALTH_VALUE)
        _pet.value = currentPet.copy(sleep = newSleep)
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

