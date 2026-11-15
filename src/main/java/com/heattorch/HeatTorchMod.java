package com.heattorch;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

public class HeatTorchMod implements ModInitializer {
	public static final String MOD_ID = "heattorch";

	/** Loot table phu (JSON) chua quyen sach Internal Cooldown I, duoc chen vao ruong End City. */
	private static final ResourceKey<LootTable> END_CITY_BONUS =
			ResourceKey.create(Registries.LOOT_TABLE, id("chests/end_city_internal_cooldown"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.init();

		// Xoa du lieu cooldown khi server tat
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HeatTorchCombat.clear());

		// Chen sach Internal Cooldown I vao ruong tau End City
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (source.isBuiltin() && BuiltInLootTables.END_CITY_TREASURE.equals(key)) {
				tableBuilder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(END_CITY_BONUS)));
			}
		});
	}
}
