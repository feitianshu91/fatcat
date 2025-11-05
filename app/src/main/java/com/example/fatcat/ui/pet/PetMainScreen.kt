package com.example.fatcat.ui.pet

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fatcat.R
import com.example.fatcat.model.PetGender
import com.example.fatcat.model.PetState
import com.example.fatcat.utils.Constants
import com.example.fatcat.utils.MusicPlayer
import com.example.fatcat.utils.PetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetMainScreen(
    modifier: Modifier = Modifier,
    petManager: PetManager? = null,
    hasOverlayPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartService: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 在 Composable 内部初始化 PetManager
    // PetManager.getInstance 内部已有错误处理，不会抛出异常
    val actualPetManager = remember {
        petManager ?: PetManager.getInstance(context)
    }
    
    // 如果 PetManager 为空，显示错误界面
    if (actualPetManager == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "初始化失败，请重启应用",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }
    
    val musicPlayer = remember { 
        MusicPlayer.getInstance(context)
    }
    
    val pet by actualPetManager.pet.collectAsState()
    
    var showEditDialog by remember { mutableStateOf(false) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var currentPetSize by remember { mutableStateOf(actualPetManager.getPetSize()) }
    var showGameDialog by remember { mutableStateOf(false) }
    var showLevelUpDialog by remember { mutableStateOf(false) }
    var levelUpInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (oldLevel, newLevel)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "肥波波 - 桌面宠物",
            style = MaterialTheme.typography.headlineLarge
        )
        
        // 等级和经验值显示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ 等级 ${pet.level}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    
                    if (pet.level < Constants.LevelSystem.MAX_LEVEL) {
                        Text(
                            text = "${pet.exp} / ${Constants.LevelSystem.getExpForNextLevel(pet.level)} EXP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "已满级 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                
                if (pet.level < Constants.LevelSystem.MAX_LEVEL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 经验条
                    LinearProgressIndicator(
                        progress = actualPetManager.getLevelProgress(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
        
        // 权限检查
        if (!hasOverlayPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要悬浮窗权限才能显示桌面宠物",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("授予权限")
                    }
                }
            }
        } else {
            // 宠物控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("启动宠物")
                }
                
                Button(
                    onClick = { 
                        // 关闭桌面宠物服务
                        val intent = android.content.Intent(context, com.example.fatcat.service.FloatingPetService::class.java)
                        context.stopService(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("关闭宠物")
                }
            }
        }
        
        HorizontalDivider()
        
        // 音乐播放控制
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎵 背景音乐",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                if (musicPlayer == null) {
                                    Log.w("PetMainScreen", "MusicPlayer 未初始化")
                                    return@Button
                                }
                                
                                if (isMusicPlaying) {
                                    musicPlayer.pause()
                                    isMusicPlaying = false
                                } else {
                                    // 注意：需要将 hajimi.mo3 转换为 mp3 格式
                                    // 并重命名为 hajimi.mp3 放在 res/raw 文件夹中
                                    // 文件名必须全小写：hajimi.mp3（不能有大写字母）
                                    
                                    // 使用动态方式获取资源ID（避免编译时检查，文件不存在也不会报错）
                                    val resId = try {
                                        context.resources.getIdentifier(
                                            "hajimi", 
                                            "raw", 
                                            context.packageName
                                        )
                                    } catch (e: Exception) {
                                        Log.e("PetMainScreen", "获取资源ID失败", e)
                                        0
                                    }
                                    
                                    if (resId != 0) {
                                        musicPlayer.play(resId, isLooping = true)
                                        isMusicPlaying = true
                                    } else {
                                        // 文件不存在，显示提示
                                        Log.w("PetMainScreen", "音频文件 hajimi.mp3 未找到，请添加到 res/raw 文件夹")
                                        // 暂时禁用播放功能，文件添加后会自动可用
                                    }
                                }
                            } catch (e: Exception) {
                                // 处理音频文件不存在的情况
                                Log.e("PetMainScreen", "音频播放错误: ${e.message}", e)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isMusicPlaying) "⏸️ 暂停音乐" else "▶️ 播放音乐")
                    }
                    
                    Button(
                        onClick = {
                            try {
                                musicPlayer?.stop()
                                isMusicPlaying = false
                            } catch (e: Exception) {
                                Log.e("PetMainScreen", "停止音乐失败", e)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = (isMusicPlaying || (musicPlayer?.isPaused() == true))
                    ) {
                        Text("⏹️ 停止")
                    }
                }
            }
        }
        
        HorizontalDivider()
        
        // 宠物信息显示
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "宠物信息",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("名称", pet.name)
                InfoRow("性别", if (pet.gender == PetGender.MALE) "雄性" else "雌性")
                InfoRow("性格", pet.personality)
                InfoRow("爱好", pet.hobby)
                InfoRow("状态", getStateText(pet.state))
            }
        }
        
        // 健康值显示
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "健康状态",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                HealthBar("饥饿", pet.hunger)
                HealthBar("口渴", pet.thirst)
                HealthBar("睡眠", pet.sleep)
                HealthBar("快乐", pet.happiness)
            }
        }
        
        // 编辑按钮
        Button(
            onClick = { showEditDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("编辑宠物信息")
        }
        
        // 宠物大小调整
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "宠物大小",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 大小选项按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizeOptions = listOf(
                        "小" to Constants.PetSize.SIZE_SMALL,
                        "中" to Constants.PetSize.SIZE_MEDIUM,
                        "大" to Constants.PetSize.SIZE_LARGE,
                        "超大" to Constants.PetSize.SIZE_XLARGE
                    )
                    
                    sizeOptions.forEach { (label, size) ->
                        Button(
                            onClick = {
                                currentPetSize = size
                                actualPetManager.setPetSize(size)
                                // 重启服务以应用新大小
                                val intent = android.content.Intent(context, com.example.fatcat.service.FloatingPetService::class.java)
                                context.stopService(intent)
                                context.startForegroundService(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = if (currentPetSize == size) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        ) {
                            Text(label)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "当前大小: ${currentPetSize}像素",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 互动按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { 
                    actualPetManager.patHead()
                    triggerHappyAnimation(context)  // 触发开心动画
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (pet.isUserForcedSleep && pet.state == PetState.SLEEP) "唤醒" else "摸头")
            }
            Button(
                onClick = { 
                    actualPetManager.hug()
                    triggerHappyAnimation(context)  // 触发开心动画
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("拥抱")
            }
        }
        
        // 喂食按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { 
                    actualPetManager.feed()
                    triggerHappyAnimation(context)  // 触发开心动画
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("喂食")
            }
            Button(
                onClick = { 
                    actualPetManager.feedWater()
                    triggerHappyAnimation(context)  // 触发开心动画
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("喂水")
            }
        }
        
        // 睡眠按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { actualPetManager.forceSleep() },
                modifier = Modifier.weight(1f),
                enabled = pet.state != PetState.SLEEP
            ) {
                Text("😴 哄睡")
            }
        }
        
        // 小游戏
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎮 小游戏",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 游戏说明
                Text(
                    text = "玩游戏可以获得经验值升级！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 猜拳游戏按钮
                Button(
                    onClick = {
                        Log.d("PetMainScreen", "🎮 猜拳对战按钮被点击")
                        showGameDialog = true
                        Log.d("PetMainScreen", "showGameDialog 设置为: $showGameDialog")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🥊 猜拳对战", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "精力 -3~5",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 游戏统计
                val gameStats = actualPetManager.getGameStats()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${gameStats.totalGames}",
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "总场次",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${gameStats.winCount}",
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "胜利",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${gameStats.drawCount}",
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFFFFC107)
                        )
                        Text(
                            text = "平局",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${gameStats.loseCount}",
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFFF44336)
                        )
                        Text(
                            text = "失败",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        // 宠物说话控制
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💬 宠物说话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "宠物会根据状态自动说话，也可以双击或手动触发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 手动触发说话按钮
                Button(
                    onClick = {
                        // 发送触发说话的Intent
                        val intent = android.content.Intent(context, com.example.fatcat.service.FloatingPetService::class.java)
                        intent.action = "com.example.fatcat.TRIGGER_SPEECH"
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("🗨️ 让宠物说话", fontSize = 16.sp)
                }
            }
        }
        
        // 弹幕控制
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💕 哄人弹幕",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (actualPetManager.getDanmakuEnabled()) {
                                "点击按钮让宠物哄你 ✨"
                            } else {
                                "弹幕已关闭"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    
                    Switch(
                        checked = actualPetManager.getDanmakuEnabled(),
                        onCheckedChange = { enabled ->
                            actualPetManager.setDanmakuEnabled(enabled)
                        }
                    )
                }
                
                if (actualPetManager.getDanmakuEnabled()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 触发弹幕按钮
                    Button(
                        onClick = {
                            // 发送触发弹幕的Intent
                            val intent = android.content.Intent(context, com.example.fatcat.service.FloatingPetService::class.java)
                            intent.action = "com.example.fatcat.TRIGGER_DANMAKU"
                            context.startService(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("💕 让宠物哄我！", fontSize = 16.sp)
                    }
                }
            }
        }
        
        // 关于和更新
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📱 关于应用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // 显示当前版本
                val updateManager = remember { com.example.fatcat.utils.UpdateManager(context) }
                InfoRow("当前版本", updateManager.getCurrentVersion())
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 检查更新按钮
                var isChecking by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<com.example.fatcat.model.AppVersion?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var checkMessage by remember { mutableStateOf("") }
                
                val coroutineScope = rememberCoroutineScope()
                
                Button(
                    onClick = {
                        isChecking = true
                        checkMessage = ""
                        coroutineScope.launch {
                            try {
                                val newVersion = updateManager.checkForUpdate()
                                isChecking = false
                                if (newVersion != null) {
                                    updateInfo = newVersion
                                    showUpdateDialog = true
                                } else {
                                    checkMessage = "已是最新版本 ✅"
                                }
                            } catch (e: Exception) {
                                isChecking = false
                                checkMessage = "检查失败，请检查网络"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("检查中...")
                    } else {
                        Text("🔄 检查更新")
                    }
                }
                
                if (checkMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = checkMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                // 更新对话框
                if (showUpdateDialog && updateInfo != null) {
                    UpdateDialog(
                        appVersion = updateInfo!!,
                        updateManager = updateManager,
                        onDismiss = { 
                            showUpdateDialog = false
                            updateInfo = null
                        }
                    )
                }
            }
        }
    }
    
    // 猜拳游戏对话框
    if (showGameDialog) {
        Log.d("PetMainScreen", "🎮 正在显示游戏对话框")
        com.example.fatcat.ui.game.RockPaperScissorsGameDialog(
            onDismiss = {
                Log.d("PetMainScreen", "🚫 游戏对话框被关闭")
                showGameDialog = false
            },
            onGameFinished = { result ->
                Log.d("PetMainScreen", "✅ 游戏完成，结果: $result")
                
                // 转换游戏结果类型
                val repoResult = when (result) {
                    com.example.fatcat.ui.game.GameResult.WIN -> com.example.fatcat.data.GameResult.WIN
                    com.example.fatcat.ui.game.GameResult.DRAW -> com.example.fatcat.data.GameResult.DRAW
                    com.example.fatcat.ui.game.GameResult.LOSE -> com.example.fatcat.data.GameResult.LOSE
                }
                
                // 玩游戏并获取奖励
                val reward = actualPetManager.playRockPaperScissorsGame(repoResult)
                Log.d("PetMainScreen", "🎁 奖励: EXP+${reward.expGained}, 精力-${reward.energyCost}, 升级: ${reward.leveledUp}")
                
                // 如果升级了，显示升级提示
                if (reward.leveledUp) {
                    levelUpInfo = Pair(reward.newLevel - 1, reward.newLevel)
                    showLevelUpDialog = true
                    Log.d("PetMainScreen", "⭐ 等级提升: ${reward.newLevel - 1} → ${reward.newLevel}")
                }
            }
        )
    }
    
    // 升级提示对话框
    if (showLevelUpDialog && levelUpInfo != null) {
        LevelUpDialog(
            oldLevel = levelUpInfo!!.first,
            newLevel = levelUpInfo!!.second,
            onDismiss = {
                showLevelUpDialog = false
                levelUpInfo = null
            }
        )
    }
    
    // 编辑对话框
    if (showEditDialog) {
        EditPetDialog(
            pet = pet,
            onDismiss = { showEditDialog = false },
            onSave = { updatedPet ->
                actualPetManager.savePet(updatedPet)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun HealthBar(label: String, value: Int) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun getStateText(state: PetState): String {
    return when (state) {
        PetState.NORMAL -> "常态"
        PetState.DAZE -> "发呆"
        PetState.SLEEP -> "睡觉"
        PetState.HAPPY -> "开心"
        PetState.ANGRY -> "生气"
        PetState.SAD -> "悲伤"
        PetState.SURPRISED -> "惊讶"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPetDialog(
    pet: com.example.fatcat.model.Pet,
    onDismiss: () -> Unit,
    onSave: (com.example.fatcat.model.Pet) -> Unit
) {
    var name by remember { mutableStateOf(pet.name) }
    var personality by remember { mutableStateOf(pet.personality) }
    var hobby by remember { mutableStateOf(pet.hobby) }
    var gender by remember { mutableStateOf(pet.gender) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑宠物信息") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("性格") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hobby,
                    onValueChange = { hobby = it },
                    label = { Text("爱好") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = gender == PetGender.MALE,
                        onClick = { gender = PetGender.MALE }
                    )
                    Text("雄性", modifier = Modifier.padding(top = 12.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = gender == PetGender.FEMALE,
                        onClick = { gender = PetGender.FEMALE }
                    )
                    Text("雌性", modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(pet.copy(
                        name = name,
                        personality = personality,
                        hobby = hobby,
                        gender = gender
                    ))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 更新对话框
 */
@Composable
fun UpdateDialog(
    appVersion: com.example.fatcat.model.AppVersion,
    updateManager: com.example.fatcat.utils.UpdateManager,
    onDismiss: () -> Unit
) {
    val downloadProgress by updateManager.downloadProgress.collectAsState()
    val downloadStatus by updateManager.downloadStatus.collectAsState()
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = { 
            if (downloadStatus !is com.example.fatcat.utils.DownloadStatus.Downloading) {
                onDismiss() 
            }
        },
        title = {
            Text("🎉 发现新版本")
        },
        text = {
            Column {
                Text(
                    text = "版本：${appVersion.versionName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                if (appVersion.fileSize > 0) {
                    Text(
                        text = "大小：${updateManager.formatFileSize(appVersion.fileSize)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "更新内容：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = appVersion.updateMessage,
                    style = MaterialTheme.typography.bodySmall
                )
                
                if (appVersion.forceUpdate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ 此更新为强制更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // 显示下载进度
                when (val status = downloadStatus) {
                    is com.example.fatcat.utils.DownloadStatus.Downloading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在下载：${status.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { status.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    is com.example.fatcat.utils.DownloadStatus.Success -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "✅ 下载完成！正在安装...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is com.example.fatcat.utils.DownloadStatus.Failed -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "❌ ${status.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        updateManager.downloadAndInstall(appVersion)
                    }
                },
                enabled = downloadStatus !is com.example.fatcat.utils.DownloadStatus.Downloading &&
                         downloadStatus !is com.example.fatcat.utils.DownloadStatus.Success
            ) {
                when (downloadStatus) {
                    is com.example.fatcat.utils.DownloadStatus.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载中...")
                    }
                    is com.example.fatcat.utils.DownloadStatus.Success -> {
                        Text("安装中...")
                    }
                    is com.example.fatcat.utils.DownloadStatus.Failed -> {
                        Text("重试")
                    }
                    else -> {
                        Text("立即更新")
                    }
                }
            }
        },
        dismissButton = {
            if (!appVersion.forceUpdate && 
                downloadStatus !is com.example.fatcat.utils.DownloadStatus.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    )
}

/**
 * 升级提示对话框
 */
@Composable
fun LevelUpDialog(
    oldLevel: Int,
    newLevel: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎉",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "恭喜升级！",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "等级提升",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lv.$oldLevel",
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "→",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Lv.$newLevel",
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✨ 升级奖励",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "所有属性已恢复到最大值！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("太棒了！")
            }
        }
    )
}

/**
 * 触发开心跳跃动画
 * 当用户与宠物互动（摸头、拥抱、喂食等）时调用
 */
private fun triggerHappyAnimation(context: android.content.Context) {
    val intent = android.content.Intent(context, com.example.fatcat.service.FloatingPetService::class.java).apply {
        action = com.example.fatcat.service.FloatingPetService.ACTION_TRIGGER_HAPPY_ANIMATION
    }
    context.startService(intent)
    android.util.Log.d("PetMainScreen", "💕 触发宠物开心动画")
}

