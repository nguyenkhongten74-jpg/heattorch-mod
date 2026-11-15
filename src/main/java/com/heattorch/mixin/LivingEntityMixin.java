package com.heattorch.mixin;

import com.heattorch.HeatTorchCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Nhan sat thuong (melt / crit melt) truoc, ap chay/freeze sau khi don danh trung. */
	@WrapMethod(method = "hurtServer")
	private boolean heattorch$wrapHurtServer(ServerLevel level, DamageSource source, float amount,
			Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		HeatTorchCombat.Hit hit = HeatTorchCombat.prepare(level, self, source, amount);
		if (hit == null) {
			return original.call(level, source, amount);
		}
		boolean result = original.call(level, source, hit.damage());
		if (result) {
			HeatTorchCombat.apply(level, self, hit);
		}
		return result;
	}

	/** Heat Torch khong gay day lui. */
	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void heattorch$noKnockback(double power, double x, double z, CallbackInfo ci) {
		if (HeatTorchCombat.shouldCancelKnockback((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
