package io.github.lucaargolo.seasonsextras;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.github.lucaargolo.seasons.FabricSeasons;
import io.github.lucaargolo.seasonsextras.block.AirConditioningBlock;
import io.github.lucaargolo.seasonsextras.block.GreenhouseGlassBlock;
import io.github.lucaargolo.seasonsextras.block.SeasonCalendarBlock;
import io.github.lucaargolo.seasonsextras.block.SeasonDetectorBlock;
import io.github.lucaargolo.seasonsextras.blockentities.AirConditioningBlockEntity;
import io.github.lucaargolo.seasonsextras.blockentities.AirConditioningBlockEntity.Conditioning;
import io.github.lucaargolo.seasonsextras.blockentities.GreenhouseGlassBlockEntity;
import io.github.lucaargolo.seasonsextras.blockentities.SeasonCalendarBlockEntity;
import io.github.lucaargolo.seasonsextras.blockentities.SeasonDetectorBlockEntity;
import io.github.lucaargolo.seasonsextras.item.*;
import io.github.lucaargolo.seasonsextras.patchouli.PatchouliMultiblockCreator;
import io.github.lucaargolo.seasonsextras.screenhandlers.AirConditioningScreenHandler;
import io.github.lucaargolo.seasonsextras.utils.ModIdentifier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.TreeFeature;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class FabricSeasonsExtras implements ModInitializer {

    private static final HashMap<Identifier, JsonObject> multiblockCache = new HashMap<>();
    public static final String MOD_ID = "seasonsextras";

    //Block Entities
    public static BlockEntityType<SeasonDetectorBlockEntity> SEASON_DETECTOR_TYPE = null;
    public static BlockEntityType<SeasonCalendarBlockEntity> SEASON_CALENDAR_TYPE = null;
    public static BlockEntityType<GreenhouseGlassBlockEntity> GREENHOUSE_GLASS_TYPE = null;
    public static BlockEntityType<AirConditioningBlockEntity> AIR_CONDITIONING_TYPE = null;

    //Blocks
    public static SeasonCalendarBlock SEASON_CALENDAR_BLOCK;
    public static GreenhouseGlassBlock[] GREENHOUSE_GLASS_BLOCKS = new GreenhouseGlassBlock[17];

    //Items
    public static ModIdentifier SEASONAL_COMPENDIUM_ITEM_ID = new ModIdentifier("seasonal_compendium");
    public static Item SEASON_CALENDAR_ITEM;
    
    //Screen Handlers
    public static ScreenHandlerType<AirConditioningScreenHandler> AIR_CONDITIONING_SCREEN_HANDLER;

    //Packets
    public static ModIdentifier SEND_VALID_BIOMES_S2C = new ModIdentifier("send_valid_biomes_s2c");
    public static ModIdentifier SEND_BIOME_MULTIBLOCKS_S2C = new ModIdentifier("send_biome_multiblocks_s2c");
    public static ModIdentifier SEND_MULTIBLOCKS_S2C = new ModIdentifier("send_multiblocks_s2c");
    public static ModIdentifier SEND_TESTED_SEASON_S2C = new ModIdentifier("send_tested_season_s2c");
    public static ModIdentifier SEND_MODULE_PRESS_C2S = new ModIdentifier("send_module_press_c2s");

    //Creative Tab

    private static final List<Pair<Predicate<Item>, Item>> creativeTabItems = new ArrayList<>();
    private static final ItemGroup CREATIVE_TAB = FabricItemGroup.builder(new ModIdentifier("creative_tab"))
            .icon(() -> SEASON_CALENDAR_ITEM.getDefaultStack())
            .entries((enabledFeatures, entries, operatorEnabled) -> {
                creativeTabItems.forEach(pair -> {
                    Item item = pair.getRight();
                    if(pair.getLeft().test(item)) {
                        entries.add(item.getDefaultStack());
                    }
                });
            })
            .build();

    private static void addToTab(Predicate<Item> condition, Item item) {
        creativeTabItems.add(new Pair<>(condition, item));
    }
    
    private static void addToTab(Item item) {
        addToTab(i -> true, item);
    }
    

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onInitialize() {

        for (DyeColor value : DyeColor.values()) {
            GreenhouseGlassBlock greenhouseGlass = Registry.register(Registries.BLOCK, new ModIdentifier(value.getName()+"_greenhouse_glass"), new GreenhouseGlassBlock(false, FabricBlockSettings.copyOf(Blocks.GREEN_STAINED_GLASS)));
            addToTab(i -> FabricSeasons.CONFIG.isSeasonMessingCrops(), Registry.register(Registries.ITEM, new ModIdentifier(value.getName()+"_greenhouse_glass"), new GreenHouseGlassItem(greenhouseGlass, new Item.Settings())));
            GREENHOUSE_GLASS_BLOCKS[value.ordinal()] = greenhouseGlass;
        }
        GreenhouseGlassBlock tintedGreenhouseGlass = Registry.register(Registries.BLOCK, new ModIdentifier("tinted_greenhouse_glass"), new GreenhouseGlassBlock(true, FabricBlockSettings.copyOf(Blocks.TINTED_GLASS)));
        addToTab(i -> FabricSeasons.CONFIG.isSeasonMessingCrops(), Registry.register(Registries.ITEM, new ModIdentifier("tinted_greenhouse_glass"), new GreenHouseGlassItem(tintedGreenhouseGlass, new Item.Settings())));
        GREENHOUSE_GLASS_BLOCKS[16] = tintedGreenhouseGlass;
        GREENHOUSE_GLASS_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new ModIdentifier("greenhouse_glass"), FabricBlockEntityTypeBuilder.create(GreenhouseGlassBlockEntity::new, GREENHOUSE_GLASS_BLOCKS).build(null));

        AirConditioningBlock heaterBlock = Registry.register(Registries.BLOCK, new ModIdentifier("heater"), new AirConditioningBlock(Conditioning.HEATER, FabricBlockSettings.copyOf(Blocks.COBBLESTONE).luminance(state -> state.get(AirConditioningBlock.LEVEL) * 5)));
        addToTab(i -> FabricSeasons.CONFIG.isSeasonMessingCrops(), Registry.register(Registries.ITEM, new ModIdentifier("heater"), new AirConditioningItem(heaterBlock, new Item.Settings())));
        AirConditioningBlock chillerBlock = Registry.register(Registries.BLOCK, new ModIdentifier("chiller"), new AirConditioningBlock(Conditioning.CHILLER, FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).luminance(state -> state.get(AirConditioningBlock.LEVEL) * 5)));
        addToTab(i -> FabricSeasons.CONFIG.isSeasonMessingCrops(), Registry.register(Registries.ITEM, new ModIdentifier("chiller"), new AirConditioningItem(chillerBlock, new Item.Settings())));
        AIR_CONDITIONING_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new ModIdentifier("air_conditioning"), FabricBlockEntityTypeBuilder.create(AirConditioningBlockEntity::new, heaterBlock, chillerBlock).build(null));
        AIR_CONDITIONING_SCREEN_HANDLER = Registry.register(Registries.SCREEN_HANDLER, new ModIdentifier("air_conditioning_screen"), new ExtendedScreenHandlerType<>((syncId, playerInventory, buf) -> {
            return new AirConditioningScreenHandler(syncId, playerInventory, ScreenHandlerContext.create(playerInventory.player.world, buf.readBlockPos()), buf.readRegistryValue(Registries.BLOCK));
        }));

        SeasonDetectorBlock seasonDetector = Registry.register(Registries.BLOCK, new ModIdentifier("season_detector"), new SeasonDetectorBlock(FabricBlockSettings.copyOf(Blocks.DAYLIGHT_DETECTOR)));
        SEASON_DETECTOR_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new ModIdentifier("season_detector"), FabricBlockEntityTypeBuilder.create(seasonDetector::createBlockEntity, seasonDetector).build(null));
        addToTab(Registry.register(Registries.ITEM, new ModIdentifier("season_detector"), new SeasonDetectorItem(seasonDetector, new Item.Settings())));

        SEASON_CALENDAR_BLOCK = Registry.register(Registries.BLOCK, new ModIdentifier("season_calendar"), new SeasonCalendarBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)));
        SEASON_CALENDAR_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new ModIdentifier("season_calendar"), FabricBlockEntityTypeBuilder.create(SEASON_CALENDAR_BLOCK::createBlockEntity, SEASON_CALENDAR_BLOCK).build(null));
        SEASON_CALENDAR_ITEM = Registry.register(Registries.ITEM, new ModIdentifier("season_calendar"), new SeasonCalendarItem(SEASON_CALENDAR_BLOCK, (new Item.Settings())));
        addToTab(SEASON_CALENDAR_ITEM);

        addToTab(i -> FabricLoader.getInstance().isModLoaded("patchouli"), Registry.register(Registries.ITEM, SEASONAL_COMPENDIUM_ITEM_ID, new SeasonalCompendiumItem(new Item.Settings())));
        addToTab(i -> FabricSeasons.CONFIG.isSeasonMessingCrops(), Registry.register(Registries.ITEM, new ModIdentifier("crop_season_tester"), new CropSeasonTesterItem(new Item.Settings())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendValidBiomes(server, handler.player);
        });

        ServerPlayNetworking.registerGlobalReceiver(SEND_MODULE_PRESS_C2S, (server, player, handler, buf, sender) -> {
            int button = buf.readInt();
            server.execute(() -> {
                if(player.currentScreenHandler instanceof AirConditioningScreenHandler screenHandler) {
                    screenHandler.cycleButton(button);
                }
            });
        });

        ItemStorage.SIDED.registerForBlockEntity((entity, direction) -> {
            Storage<ItemVariant> inputStorage = InventoryStorage.of(entity.getInputInventory(), direction);
            Storage<ItemVariant> moduleStorage = FilteringStorage.extractOnlyOf(InventoryStorage.of(entity.getModuleInventory(), direction));
            return new CombinedStorage<>(List.of(inputStorage, moduleStorage));
        }, AIR_CONDITIONING_TYPE);

        ResourceConditions.register(new ModIdentifier("is_season_messing_crops"), json -> FabricSeasons.CONFIG.isSeasonMessingCrops());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static void sendValidBiomes(MinecraftServer server, @Nullable ServerPlayerEntity player) {
        boolean generateMultiblocks = player == null;
        if(generateMultiblocks) {
            multiblockCache.clear();
        }
        server.getWorlds().forEach(serverWorld -> {
            if(FabricSeasons.CONFIG.isValidInDimension(serverWorld.getRegistryKey())) {
                Set<RegistryEntry<Biome>> validBiomes = new HashSet<>();
                serverWorld.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes().forEach(entry -> {
                    entry.getKey().ifPresent(key -> validBiomes.add(entry));
                });
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeIdentifier(serverWorld.getRegistryKey().getValue());
                buf.writeInt(validBiomes.size());
                validBiomes.stream().map(r -> r.getKey().get().getValue()).forEach(buf::writeIdentifier);
                if(player != null) {
                    ServerPlayNetworking.send(player, SEND_VALID_BIOMES_S2C, buf);
                }else{
                    server.getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, SEND_VALID_BIOMES_S2C, buf));
                }
                sendBiomeMultiblocks(server, player, serverWorld, validBiomes);
            }
        });
        sendMultiblocks(server, player);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static void sendBiomeMultiblocks(MinecraftServer server, @Nullable ServerPlayerEntity player, ServerWorld serverWorld, Set<RegistryEntry<Biome>> validBiomes) {
        Identifier worldId = serverWorld.getRegistryKey().getValue();
        HashMap<Identifier, HashSet<Identifier>> biomeToMultiblocks = new HashMap<>();
        validBiomes.forEach(entry -> {
            Identifier biomeId = entry.getKey().get().getValue();
            List<ConfiguredFeature<?, ?>> validFeatures = entry.value().getGenerationSettings().getFeatures().stream()
                    .flatMap(RegistryEntryList::stream)
                    .map(RegistryEntry::value)
                    .flatMap(PlacedFeature::getDecoratedFeatures)
                    .filter((c) -> c.feature() instanceof TreeFeature)
                    .collect(ImmutableList.toImmutableList());

            for(ConfiguredFeature<?, ?> cf : validFeatures) {
                Identifier cfId = server.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).getId(cf);
                if(cfId != null) {
                    if (!multiblockCache.containsKey(cfId)) {
                        PatchouliMultiblockCreator creator = new PatchouliMultiblockCreator(serverWorld, Blocks.GRASS_BLOCK.getDefaultState(), Blocks.GRASS.getDefaultState(), new BlockPos(-100, -100, -100), (c) -> {
                            cf.generate(c.getFakeWorld(), serverWorld.getChunkManager().getChunkGenerator(), Random.create(0L), new BlockPos(100, 100, 100));
                        });
                        Optional<JsonObject> optional = creator.getMultiblock((set) -> {
                            boolean foundLeave = false;
                            boolean foundLog = false;
                            Iterator<BlockState> iterator = set.iterator();
                            while(iterator.hasNext() && (!foundLeave || !foundLog)) {
                                BlockState state = iterator.next();
                                if(state.isIn(BlockTags.LEAVES)) {
                                    foundLeave = true;
                                }
                                if(state.isIn(BlockTags.LOGS)) {
                                    foundLog = true;
                                }
                            }
                            return foundLeave && foundLog;
                        });
                        optional.ifPresent((o) -> {
                            multiblockCache.put(cfId, o);
                            biomeToMultiblocks.computeIfAbsent(biomeId, b -> new HashSet<>()).add(cfId);
                        });
                    }else{
                        biomeToMultiblocks.computeIfAbsent(biomeId, b -> new HashSet<>()).add(cfId);
                    }
                }
            };
            Identifier empty = new ModIdentifier("empty");
            if(multiblockCache.containsKey(empty)) {
                biomeToMultiblocks.computeIfAbsent(biomeId, b -> new HashSet<>(Collections.singleton(empty)));
            }else{
                PatchouliMultiblockCreator creator = new PatchouliMultiblockCreator(serverWorld, Blocks.SAND.getDefaultState(), Blocks.DEAD_BUSH.getDefaultState(), new BlockPos(0, 0, 0), (c) -> {});
                JsonObject emptyMultiblock = creator.getMultiblock((set) -> true).get();
                multiblockCache.put(empty, emptyMultiblock);
                biomeToMultiblocks.computeIfAbsent(biomeId, b -> new HashSet<>(Collections.singleton(empty)));
            }
        });

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(worldId);
        buf.writeInt(biomeToMultiblocks.size());
        biomeToMultiblocks.forEach((identifier, set) -> {
            buf.writeIdentifier(identifier);
            buf.writeInt(set.size());
            set.forEach(buf::writeIdentifier);
        });
        if(player != null) {
            ServerPlayNetworking.send(player, SEND_BIOME_MULTIBLOCKS_S2C, buf);
        }else{
            server.getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, SEND_BIOME_MULTIBLOCKS_S2C, buf));
        }

    }

    private static void sendMultiblocks(MinecraftServer server, @Nullable ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(multiblockCache.size());
        multiblockCache.forEach((identifier, jsonObject) -> {
            buf.writeIdentifier(identifier);
            var string = jsonObject.toString();
            if(string.length() > PacketByteBuf.DEFAULT_MAX_STRING_LENGTH) {
                buf.writeString("{}");
            }else{
                buf.writeString(string);
            };

        });
        if(player != null) {
            ServerPlayNetworking.send(player, SEND_MULTIBLOCKS_S2C, buf);
        }else{
            server.getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, SEND_MULTIBLOCKS_S2C, buf));
        }
    }


}
