package com.heattorch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Toan bo co che Melt / chay / dong bang cua Heat Torch.
 *
 * Quy tac trang thai (moi don danh ap mot hieu ung len muc tieu):
 *  - Che do thuong (Heat): ap CHAY. Neu muc tieu dang FREEZE -> melt (x2 neu het hoi chieu),
 *    xoa freeze (Chay + Freeze => 0). Co Fire Aspect thi ap them chay lai.
 *  - Che do Reverse: ap FREEZE. Neu muc tieu dang CHAY -> melt (x2 neu het hoi chieu),
 *    xoa chay (Freeze + Chay => 0). Sinh vat chay san (tag naturally_burning) khong mat chay.
 */
public final class HeatTorchCombat {
	private HeatTorchCombat() {
	}

	// ====== Hang so co the chinh ======
	/** Chay 6s */
	public static final int BURN_TICKS = 120;
	/** Moi cap Fire Aspect cong them 2s (lv1 = 8s, lv2 = 10s) */
	public static final int FIRE_ASPECT_EXTRA_TICKS_PER_LEVEL = 40;
	/** Freeze ~6s: ticksFrozen giam 2/tick khi khong o tuyet lun => 240 tick giam het sau 120 tick */
	public static final int FREEZE_TICKS = 240;
	/** Hoi chieu melt: goc 2.5s, Internal Cooldown I = 2.25s, II = 2s */
	public static final int MELT_COOLDOWN_TICKS = 50;
	public static final int MELT_COOLDOWN_TICKS_IC1 = 45;
	public static final int MELT_COOLDOWN_TICKS_IC2 = 40;
	public static final float MELT_MULTIPLIER = 2.0F;
	/** He so chi mang goc cua Minecraft */
	public static final float VANILLA_CRIT = 1.5F;
	public static final float CRIT_BONUS_PER_LEVEL = 0.4F;

	// ====== Khoa enchant ======
	public static final ResourceKey<Enchantment> REVERSE = enchantKey("reverse");
	public static final ResourceKey<Enchantment> CRITICAL_MELT = enchantKey("critical_melt");
	public static final ResourceKey<Enchantment> INTERNAL_COOLDOWN = enchantKey("internal_cooldown");

	public static final TagKey<EntityType<?>> NATURALLY_BURNING =
			TagKey.create(Registries.ENTITY_TYPE, HeatTorchMod.id("naturally_burning"));

	private static ResourceKey<Enchantment> enchantKey(String name) {
		return ResourceKey.create(Registries.ENCHANTMENT, HeatTorchMod.id(name));
	}

	// ====== Trang thai ======
	private record AttackContext(ServerPlayer attacker, ItemStack weapon, boolean crit) {
	}

	/** Thong tin mot don danh da tinh san, dung de ap hieu ung sau khi don danh trung. */
	public record Hit(float damage, boolean reverse, boolean melt, int fireAspectLevel) {
	}

	private static final ThreadLocal<AttackContext> CONTEXT = new ThreadLocal<>();
	private static final Map<UUID, Long> LAST_MELT = new HashMap<>();

	public static void clear() {
		LAST_MELT.clear();
		CONTEXT.remove();
	}

	// ====== Duoc goi tu PlayerMixin (bao quanh Player.attack) ======
	public static void beginAttack(Player player, Entity target) {
		CONTEXT.remove();
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		ItemStack weapon = sp.getMainHandItem();
		if (!weapon.is(ModItems.HEAT_TORCH)) {
			return;
		}
		// Lap lai dieu kien chi mang cua Minecraft goc
		boolean crit = sp.getAttackStrengthScale(0.5F) > 0.9F
				&& sp.fallDistance > 0.0F
				&& !sp.onGround()
				&& !sp.onClimbable()
				&& !sp.isInWater()
				&& !sp.hasEffect(MobEffects.BLINDNESS)
				&& !sp.isPassenger()
				&& target instanceof LivingEntity
				&& !sp.isSprinting();
		CONTEXT.set(new AttackContext(sp, weapon, crit));
	}

	public static void endAttack() {
		CONTEXT.remove();
	}

	/** Huy knockback cho moi sinh vat (tru nguoi danh) trong luc Heat Torch dang tan cong. */
	public static boolean shouldCancelKnockback(LivingEntity victim) {
		AttackContext ctx = CONTEXT.get();
		return ctx != null && victim != ctx.attacker();
	}

	// ====== Duoc goi tu LivingEntityMixin.hurtServer ======
	/** Tra ve null neu day khong phai don danh can chien cua Heat Torch. */
	public static Hit prepare(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		AttackContext ctx = CONTEXT.get();
		if (ctx == null || source.getEntity() != ctx.attacker() || !source.is(DamageTypes.PLAYER_ATTACK)) {
			return null;
		}
		ItemStack weapon = ctx.weapon();
		boolean reverse = enchantLevel(level, REVERSE, weapon) > 0;
		int fireAspect = enchantLevel(level, Enchantments.FIRE_ASPECT, weapon);

		boolean hasOpposite = reverse ? hasBurn(target) : hasFreeze(target);
		boolean melt = hasOpposite && meltReady(level, target, weapon);

		float damage = amount;
		if (melt) {
			int critMelt = enchantLevel(level, CRITICAL_MELT, weapon);
			if (ctx.crit() && critMelt > 0) {
				float newCrit = VANILLA_CRIT + CRIT_BONUS_PER_LEVEL * critMelt;
				damage *= newCrit / VANILLA_CRIT;
			}
			damage *= MELT_MULTIPLIER;
		}
		return new Hit(damage, reverse, melt, fireAspect);
	}

	/** Chi goi khi don danh da that su gay sat thuong. */
	public static void apply(ServerLevel level, LivingEntity target, Hit hit) {
		if (hit.melt()) {
			recordMelt(level, target);
		}
		if (!hit.reverse()) {
			// Che do Heat: ap CHAY
			if (hasFreeze(target)) {
				target.setTicksFrozen(0); // Chay + Freeze => 0
				if (hit.fireAspectLevel() > 0) {
					ignite(target, burnTicks(hit.fireAspectLevel())); // Fire Aspect: ap chay lai
				}
			} else {
				ignite(target, burnTicks(hit.fireAspectLevel()));
			}
		} else {
			// Che do Reverse: ap FREEZE
			if (hasBurn(target)) {
				if (!naturallyBurning(target)) {
					target.clearFire(); // Freeze + Chay => 0
				}
			} else {
				freeze(target);
			}
		}
	}

	// ====== Ham phu ======
	private static int enchantLevel(ServerLevel level, ResourceKey<Enchantment> key, ItemStack stack) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
				.orElse(0);
	}

	private static boolean naturallyBurning(LivingEntity e) {
		return e.getType().is(NATURALLY_BURNING);
	}

	private static boolean hasBurn(LivingEntity e) {
		return e.isOnFire() || e.getRemainingFireTicks() > 0 || naturallyBurning(e);
	}

	private static boolean hasFreeze(LivingEntity e) {
		return e.getTicksFrozen() > 0;
	}

	private static int burnTicks(int fireAspectLevel) {
		return BURN_TICKS + FIRE_ASPECT_EXTRA_TICKS_PER_LEVEL * fireAspectLevel;
	}

	/** Khong cong don: chi lam moi len toi da thoi gian, khong keo dai them. */
	private static void ignite(LivingEntity e, int ticks) {
		if (e.getRemainingFireTicks() < ticks) {
			e.setRemainingFireTicks(ticks);
		}
	}

	/** Muc tieu mac do da (hoac mien nhiem) thi canFreeze() = false va khong bi freeze. */
	private static void freeze(LivingEntity e) {
		if (e.canFreeze()) {
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), FREEZE_TICKS));
		}
	}

	private static int cooldownTicks(ServerLevel level, ItemStack weapon) {
		int ic = enchantLevel(level, INTERNAL_COOLDOWN, weapon);
		if (ic >= 2) {
			return MELT_COOLDOWN_TICKS_IC2;
		}
		if (ic == 1) {
			return MELT_COOLDOWN_TICKS_IC1;
		}
		return MELT_COOLDOWN_TICKS;
	}

	private static boolean meltReady(ServerLevel level, LivingEntity target, ItemStack weapon) {
		Long last = LAST_MELT.get(target.getUUID());
		if (last == null) {
			return true;
		}
		long now = level.getGameTime();
		return now < last || now - last >= cooldownTicks(level, weapon);
	}

	private static void recordMelt(ServerLevel level, LivingEntity target) {
		long now = level.getGameTime();
		LAST_MELT.put(target.getUUID(), now);
		if (LAST_MELT.size() > 512) {
			LAST_MELT.values().removeIf(t -> now < t || now - t > 1200);
		}
	}
}
