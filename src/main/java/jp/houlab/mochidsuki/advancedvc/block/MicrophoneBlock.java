package jp.houlab.mochidsuki.advancedvc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * マイクロフォンブロック
 * 範囲内のプレイヤーの音声を拾う
 * TODO: BlockEntityで範囲内プレイヤー検知とストリーム登録
 */
public class MicrophoneBlock extends Block {

    public static final float DETECTION_RANGE = 10.0f; // 検知範囲（ブロック）

    public MicrophoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // TODO: BlockEntity実装
    // - 範囲内のプレイヤーを検知
    // - 音声ストリームIDをサーバーに登録
}
