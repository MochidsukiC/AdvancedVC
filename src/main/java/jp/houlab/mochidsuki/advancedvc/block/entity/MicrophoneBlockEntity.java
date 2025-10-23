package jp.houlab.mochidsuki.advancedvc.block.entity;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.block.MicrophoneBlock;
import jp.houlab.mochidsuki.advancedvc.server.ServerAudioRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * マイクロフォンブロックエンティティ
 * 範囲内のプレイヤーを検知し、その音声ストリームIDをサーバーに登録
 */
public class MicrophoneBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<UUID> detectedPlayers = new HashSet<>();
    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 20; // 1秒ごとに更新

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROPHONE.get(), pos, state);
    }

    /**
     * Tick処理
     */
    public static void tick(Level level, BlockPos pos, BlockState state, MicrophoneBlockEntity blockEntity) {
        if (level.isClientSide) {
            return;
        }

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= UPDATE_INTERVAL) {
            blockEntity.tickCounter = 0;
            blockEntity.detectPlayers((ServerLevel) level);
        }
    }

    /**
     * 範囲内のプレイヤーを検知
     */
    private void detectPlayers(ServerLevel level) {
        AABB detectionBox = new AABB(worldPosition)
                .inflate(MicrophoneBlock.DETECTION_RANGE);

        List<ServerPlayer> players = level.getEntitiesOfClass(
                ServerPlayer.class,
                detectionBox
        );

        Set<UUID> currentPlayers = new HashSet<>();
        for (ServerPlayer player : players) {
            currentPlayers.add(player.getUUID());
        }

        // 新しく検知されたプレイヤー
        Set<UUID> newPlayers = new HashSet<>(currentPlayers);
        newPlayers.removeAll(detectedPlayers);

        // 範囲外に出たプレイヤー
        Set<UUID> leftPlayers = new HashSet<>(detectedPlayers);
        leftPlayers.removeAll(currentPlayers);

        // サーバーに通知
        ServerAudioRouter router = ServerAudioRouter.getInstance();
        for (UUID playerId : newPlayers) {
            // マイクに登録
            router.registerMicrophone(worldPosition, playerId);
            LOGGER.debug("Microphone detected player: {} at {}", playerId, worldPosition);
        }

        for (UUID playerId : leftPlayers) {
            // マイクから登録解除
            router.unregisterMicrophone(worldPosition, playerId);
            LOGGER.debug("Microphone lost player: {} at {}", playerId, worldPosition);
        }

        detectedPlayers.clear();
        detectedPlayers.addAll(currentPlayers);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        // ブロック削除時にすべての登録を解除
        if (!level.isClientSide) {
            ServerAudioRouter router = ServerAudioRouter.getInstance();
            for (UUID playerId : detectedPlayers) {
                router.unregisterMicrophone(worldPosition, playerId);
            }
            detectedPlayers.clear();
        }
    }
}
