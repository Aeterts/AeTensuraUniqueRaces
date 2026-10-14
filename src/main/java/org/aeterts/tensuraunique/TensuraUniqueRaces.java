package org.aeterts.tensuraunique;

import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.race.api.RaceEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TensuraUniqueRaces implements ModInitializer {
    public static final String MOD_ID = "tensura_unique_races_fabric";
    public static final Logger LOGGER = LoggerFactory.getLogger("TensuraUniqueRaces");

    private static UniqueConfig config = new UniqueConfig();

    public static UniqueConfig config() {
        return config;
    }

    /** Replaces the active config without touching the file; for the development test. */
    static void useConfig(UniqueConfig cfg) {
        config = cfg;
        RaceGraph.clearCache();
    }

    public static void reloadConfig() {
        config = UniqueConfig.load(LOGGER);
        RaceGraph.clearCache();
    }

    @Override
    public void onInitialize() {
        reloadConfig();

        RaceEvents.SET_RACE.register((previous, entity, next, evolution, teleport, message) -> {
            if (!config.enabled || !(entity instanceof ServerPlayer player) || player.getServer() == null) {
                return EventResult.pass();
            }
            ResourceLocation nextId = next.getRaceId();
            if (previous != null && previous.getRaceId().equals(nextId)) {
                return EventResult.pass();
            }
            // Only plain evolutions are refused here (Tensura handles a refused evolution cleanly). Picking a race in the
            // reincarnation menu is checked earlier (ReincarnationMenuMixin). Everything else - awakening and harvest
            // festival (Tensura would apply its rewards even if the race change failed), admin commands, curses - is
            // never stopped, only recorded.
            if (config.checkEvolutions && evolution && previous != null && previous.getNextEvolutions(player).contains(next.getRace())) {
                var denial = Claims.check(player, nextId);
                if (denial != null) {
                    player.sendSystemMessage(denial);
                    return EventResult.interruptFalse();
                }
            }
            Claims.reserve(player, nextId, evolution);
            return EventResult.pass();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (config.enabled) {
                Claims.onJoin(handler.getPlayer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (config.enabled) {
                Claims.onLeave(handler.getPlayer());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RaceGraph.clearCache(); // trees may differ between worlds (config of Tensura, add-ons)
            Claims.expireInactive(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Claims.reset());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config.enabled) {
                Claims.settle(server);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> UniqueCommand.register(dispatcher));

        LOGGER.info("Ae's Tensura: Unique Races loaded");
    }
}
