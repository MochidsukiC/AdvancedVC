package jp.houlab.mochidsuki.advancedvc.block;

import jp.houlab.mochidsuki.advancedvc.block.entity.ModBlockEntities;
import jp.houlab.mochidsuki.advancedvc.block.entity.SpeakerBlockEntity;
import jp.houlab.mochidsuki.advancedvc.client.gui.SpeakerConnectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * スピーカーブロック
 * 接続されたマイクの音声を増幅して再生
 */
public class SpeakerBlock extends BaseEntityBlock {

    public SpeakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            // クライアントサイドでGUIを開く
            Minecraft.getInstance().setScreen(new SpeakerConnectionScreen(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
