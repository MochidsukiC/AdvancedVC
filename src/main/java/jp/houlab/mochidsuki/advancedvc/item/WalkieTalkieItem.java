package jp.houlab.mochidsuki.advancedvc.item;

import jp.houlab.mochidsuki.advancedvc.client.gui.WalkieTalkieScreen;
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
 * ウォーキートーキーアイテム
 * 周波数設定GUIを開き、プッシュトゥートーク通信を可能にする
 */
public class WalkieTalkieItem extends Item {

    public static final String NBT_FREQUENCY = "Frequency";

    public WalkieTalkieItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // クライアントサイドでGUIを開く
            Minecraft.getInstance().setScreen(new WalkieTalkieScreen(stack));
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int frequency = getFrequency(stack);
        if (frequency > 0) {
            tooltip.add(Component.literal("周波数: " + frequency + " Hz"));
        } else {
            tooltip.add(Component.literal("右クリックで周波数を設定"));
        }

        tooltip.add(Component.literal("PTTキー(V)で通信"));
    }

    /**
     * 周波数を取得
     */
    public static int getFrequency(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(NBT_FREQUENCY)) {
            return stack.getTag().getInt(NBT_FREQUENCY);
        }
        return 0;
    }

    /**
     * 周波数を設定
     */
    public static void setFrequency(ItemStack stack, int frequency) {
        stack.getOrCreateTag().putInt(NBT_FREQUENCY, frequency);
    }
}
