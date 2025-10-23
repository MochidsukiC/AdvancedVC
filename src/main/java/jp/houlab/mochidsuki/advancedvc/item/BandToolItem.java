package jp.houlab.mochidsuki.advancedvc.item;

import jp.houlab.mochidsuki.advancedvc.client.gui.BandToolScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * バンドツール（万能PAミキサー）
 */
public class BandToolItem extends Item {

    public BandToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // クライアントサイドでミキサーGUIを開く
            Minecraft.getInstance().setScreen(new BandToolScreen());
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.literal("万能PAミキサー"));
        tooltip.add(Component.literal("右クリックでミキサー画面を開く"));
        tooltip.add(Component.literal("- コンプレッサー"));
        tooltip.add(Component.literal("- イコライザー"));
        tooltip.add(Component.literal("- ディレイ/リバーブ"));
    }
}
