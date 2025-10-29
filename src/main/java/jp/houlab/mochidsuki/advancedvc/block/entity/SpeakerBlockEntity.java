package jp.houlab.mochidsuki.advancedvc.block.entity;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import jp.houlab.mochidsuki.advancedvc.server.ServerAudioRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * スピーカーブロックエンティティ
 * 接続されたマイクの音声を増幅して再生
 */
public class SpeakerBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlockPos connectedMicrophone = null;
    private VolumeLevel amplification = VolumeLevel.LOUD; // デフォルト増幅レベル

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    /**
     * マイクと接続
     */
    public void connectToMicrophone(BlockPos microphonePos) {
        this.connectedMicrophone = microphonePos;
        setChanged();

        // サーバーに登録
        if (!level.isClientSide) {
            ServerAudioRouter router = ServerAudioRouter.getInstance();
            router.registerSpeaker(worldPosition, microphonePos, amplification);
            LOGGER.info("Speaker at {} connected to microphone at {}", worldPosition, microphonePos);
        }
    }

    /**
     * マイクとの接続を解除
     */
    public void disconnect() {
        if (connectedMicrophone != null && !level.isClientSide) {
            ServerAudioRouter router = ServerAudioRouter.getInstance();
            router.unregisterSpeaker(worldPosition);
            LOGGER.info("Speaker at {} disconnected from microphone", worldPosition);
        }

        this.connectedMicrophone = null;
        setChanged();
    }

    /**
     * 増幅レベルを設定
     */
    public void setAmplification(VolumeLevel amplificationLevel) {
        this.amplification = amplificationLevel;
        setChanged();

        // サーバーに更新通知
        if (connectedMicrophone != null && !this.level.isClientSide()) {
            ServerAudioRouter router = ServerAudioRouter.getInstance();
            router.updateSpeakerAmplification(worldPosition, amplificationLevel);
        }
    }

    public BlockPos getConnectedMicrophone() {
        return connectedMicrophone;
    }

    public VolumeLevel getAmplification() {
        return amplification;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (connectedMicrophone != null) {
            tag.putLong("ConnectedMicrophone", connectedMicrophone.asLong());
        }
        tag.putInt("Amplification", amplification.ordinal());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains("ConnectedMicrophone")) {
            connectedMicrophone = BlockPos.of(tag.getLong("ConnectedMicrophone"));
        }
        amplification = VolumeLevel.values()[tag.getInt("Amplification")];
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        disconnect();
    }
}
