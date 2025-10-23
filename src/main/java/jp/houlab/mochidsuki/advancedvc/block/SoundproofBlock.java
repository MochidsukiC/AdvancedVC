package jp.houlab.mochidsuki.advancedvc.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 防音材ブロック
 * 音響特性: 吸収率 1.0（音を完全に吸収）
 * クライアントサイドの音響シミュレーションで参照される
 */
public class SoundproofBlock extends Block {

    public static final float ABSORPTION_RATE = 1.0f;
    public static final float REFLECTION_RATE = 0.0f;

    public SoundproofBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
