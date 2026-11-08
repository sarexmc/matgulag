package com.mathgulag;

import com.mathgulag.world.MeatBlock;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Bloques de la arena del gulag (texturas "Gleba" 1 a 8). Todos suenan como bloque de miel
 * (pisadas y golpes blandos, como de organos). Los 5 primeros, rojos y carnosos, sueltan esporas carmesi.
 */
public final class ModContent {
    public static final int GLEBA_COUNT = 8;
    public static final Block[] GLEBA = new Block[GLEBA_COUNT];

    private static final MapColor[] COLORS = {
            MapColor.CRIMSON_NYLIUM, MapColor.CRIMSON_STEM, MapColor.CRIMSON_HYPHAE, MapColor.DIRT,
            MapColor.NETHER, MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_WHITE};

    static {
        for (int i = 0; i < GLEBA_COUNT; i++) {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .mapColor(COLORS[i])
                    .strength(0.9f)
                    .sound(SoundType.HONEY_BLOCK);
            Block b = i < 5 ? new MeatBlock(props) : new Block(props);
            GLEBA[i] = block("gleba_" + (i + 1), b);
        }
    }

    private ModContent() {
    }

    private static Block block(String name, Block block) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GulagMod.MODID, name);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
        return block;
    }

    public static void register() {
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.mathgulag.gulag"))
                .icon(() -> new ItemStack(GLEBA[0]))
                .displayItems((params, output) -> {
                    for (Block b : GLEBA) output.accept(b);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(GulagMod.MODID, "gulag"), tab);
    }
}
