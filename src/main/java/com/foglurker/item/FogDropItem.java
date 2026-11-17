package com.foglurker.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Something the Fog Lurker left behind, with a line of flavour text. */
public class FogDropItem extends Item {
    private final String lore;

    public FogDropItem(Properties properties, String lore) {
        super(properties);
        this.lore = lore;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(lore).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
