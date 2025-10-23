package jp.houlab.mochidsuki.advancedvc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * スピーカーブロック
 * 接続されたマイクの音声を増幅して再生
 * TODO: BlockEntityで音声ルーティング
 */
public class SpeakerBlock extends Block {

    public SpeakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // TODO: BlockEntity実装
    // - 接続されたマイクのストリームIDを登録
    // - 自身の座標をサーバーに登録
    // - サーバーはこのブロックを「仮想音源」として扱う
}
