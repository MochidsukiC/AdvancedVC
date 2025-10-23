package jp.houlab.mochidsuki.advancedvc.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 吸音材ブロック
 * 音響特性: 反射率 0.0（音を反射しない）
 * クライアントサイドの音響シミュレーションで参照される
 */
public class AbsorbentBlock extends Block {

    public static final float ABSORPTION_RATE = 0.5f;
    public static final float REFLECTION_RATE = 0.0f;

    public AbsorbentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
