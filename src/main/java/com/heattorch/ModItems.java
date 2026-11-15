package com.heattorch;

import java.util.function.Function;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class ModItems {
	private ModItems() {
	}

	/** Tag rong: khong co vat lieu nao sua duoc bang anvil (chi ghep 2 cay duoc voi nhau). */
	public static final TagKey<Item> REPAIRS_HEAT_TORCH =
			TagKey.create(Registries.ITEM, HeatTorchMod.id("repairs_heat_torch"));

	// Do ben 32, toc do dao 1.0, khong cong them sat thuong, enchantability 15
	public static final ToolMaterial HEAT_TORCH_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_WOODEN_TOOL,
			32,
			1.0F,
			0.0F,
			15,
			REPAIRS_HEAT_TORCH);

	// Tong sat thuong hien thi = 7  (1 co ban cua nguoi choi + 6)
	// Tong toc do danh hien thi = 1.5 (4.0 co ban + (-2.5))
	public static final float ATTACK_DAMAGE_MODIFIER = 6.0F;
	public static final float ATTACK_SPEED_MODIFIER = -2.5F;

	public static final Item HEAT_TORCH = register(
			"heat_torch",
			Item::new,
			new Item.Properties().sword(HEAT_TORCH_MATERIAL, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HeatTorchMod.id(name));
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
				.register(entries -> entries.accept(HEAT_TORCH));
	}
}
