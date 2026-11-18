package com.foglurker.item;

import com.foglurker.brain.LurkerDirector;
import com.foglurker.entity.FogLurkerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Protector of the Fog.
 *
 * <ul>
 *   <li>Against anything else it is a wooden sword (4 damage, 1.6 attacks a second).</li>
 *   <li>Against the Fog Lurker a fully charged hit does 10, a critical hit 15, straight through its armour
 *       (see {@link FogLurkerEntity#hurt}).</li>
 *   <li>Right-click: a scare burst, a sonic boom it is afraid of. 10 of them; its durability bar shows how
 *       many are left. Hitting things never wears it. Recharge it in a crafting table: the sword in the
 *       middle, The Fog of the Day or of the Night around it, one charge each. It cannot be enchanted.</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.foglurker.FogLurkerMod.MOD_ID)
public class ProtectorOfTheFogItem extends SwordItem {
    public static final int CHARGES = 10;
    /** A wooden sword hits for 4 (6 critical): x2.5 makes 10 (15) against the Fog Lurker. */
    public static final float LURKER_MULTIPLIER = 2.5F;
    private static final int COOLDOWN_TICKS = 40;

    public ProtectorOfTheFogItem(Properties properties) {
        super(Tiers.WOOD, 3, -2.4F, properties.durability(CHARGES).rarity(Rarity.RARE));
    }

    public static int charges(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    // ------------------------------------------------------------------ never wears out from use

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true; // the charges are scare bursts, not hits
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false; // only the fog recharges it, not planks
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    /** The anvil ignores isRepairable when merging two of the same item: block that, and books. Renaming is fine. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onAnvil(net.minecraftforge.event.AnvilUpdateEvent event) {
        if (event.getLeft().getItem() instanceof ProtectorOfTheFogItem && !event.getRight().isEmpty()) {
            event.setCanceled(true);
        }
    }

    /** Mending (only possible through commands or NBT edits) must not refill scare bursts from XP. */
    @Override
    public float getXpRepairRatio(ItemStack stack) {
        return 0F;
    }

    // Unenchantable: not at a table, not with books on an anvil.
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    // ------------------------------------------------------------------ the scare burst

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (charges(stack) <= 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("No scare bursts left. Put it in the middle of a crafting table "
                        + "with The Fog of the Day or of the Night around it to recharge.").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            burst(server, sp, stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Fires a scare burst: a sonic boom it is afraid of. It reaches every creature near you or after you,
     * wherever you aim. Uses one charge. Public for the game tests.
     *
     * @return how many creatures it reached
     */
    public static int burst(ServerLevel level, ServerPlayer player, ItemStack stack) {
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
        // The look of it: a Warden's sonic boom along where you look.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        for (int i = 1; i <= 16; i++) {
            Vec3 at = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.3F);
        int reached = 0;
        int fled = 0;
        for (FogLurkerEntity lurker : LurkerDirector.active()) {
            var brain = lurker.brain();
            if (brain == null || brain.isEnded() || lurker.level() != level || lurker.isDeadOrDying()) {
                continue;
            }
            boolean afterYou = brain.targetId().equals(player.getUUID());
            if (!afterYou && lurker.distanceTo(player) > 96) {
                continue;
            }
            reached++;
            boolean scared = level.getRandom().nextFloat() < 0.75F; // 3 in 4 times it runs
            brain.onScareBurst(scared);
            if (scared) {
                fled++;
            }
        }
        LurkerDirector.scare(player); // whatever happens now, it remembers this
        if (reached > 0) {
            player.displayClientMessage(Component.literal(fled == reached ? "It shrieks and flees into the fog."
                    : fled > 0 ? "Some of it fled. The rest is weakened." : "It shrugs it off... but it is weakened.")
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            player.displayClientMessage(Component.literal("The fog swallows the sound. Nothing was near enough to hear it.")
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        }
        return reached;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int left = charges(stack);
        tooltip.add(Component.literal("Scare bursts: " + left + "/" + CHARGES)
                .withStyle(left > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Against the Fog Lurker: 10 damage, 15 on a critical hit").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click to let out what it fears").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
