package org.aeterts.tensuraunique.mixin;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import org.aeterts.tensuraunique.Claims;
import org.aeterts.tensuraunique.TensuraUniqueRaces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Tensura ignores the result of {@code Races#setRace} when a player confirms a race in the reincarnation menu: it
 * would hand out the unique skills and reset the EP even though the race change was refused. So a taken race has
 * to be refused here, before anything happens, and the menu is opened again for another choice.
 */
@Mixin(value = ReincarnationMenu.class, remap = false)
public abstract class ReincarnationMenuMixin {
    @Shadow(remap = false)
    private Player player;
    @Shadow(remap = false)
    private boolean changeRaceOnly;
    @Shadow(remap = false)
    private float customMaxAP;
    @Shadow(remap = false)
    private float customMaxMP;
    @Shadow(remap = false)
    private int racePool;
    @Shadow(remap = false)
    public DataSlot selectedManasRaceIndex;

    @Shadow(remap = false)
    public abstract List<ManasRace> getRacePool();

    @Shadow(remap = false)
    public abstract List<ManasRace> getRandomRacePool();

    // clickMenuButton overrides a Minecraft method, so its name differs between the dev (Mojang) and the production
    // (intermediary) runtime. Both are listed on purpose: the build produces no refmap for a third-party target.
    @Inject(method = {"clickMenuButton", "method_7604"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void tensuraUnique$checkRace(Player clicker, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(clicker instanceof ServerPlayer sp) || !TensuraUniqueRaces.config().enabled || !TensuraUniqueRaces.config().checkReincarnationMenu) {
            return;
        }
        boolean submit = id == -2 || (id == -1 && !changeRaceOnly);
        if (!submit) {
            return;
        }
        Component denial = null;
        List<ManasRace> pool = getRacePool();
        int index = selectedManasRaceIndex.get();
        if (index >= 0 && index < pool.size()) {
            ResourceLocation raceId = pool.get(index).getRegistryName();
            if (raceId != null) {
                denial = Claims.check(sp, raceId);
            }
        } else if (getRandomRacePool().isEmpty()) {
            denial = org.aeterts.tensuraunique.UniqueConfig.format(TensuraUniqueRaces.config().message("noFreeRace", org.aeterts.tensuraunique.Lang.of(sp)), java.util.Map.of());
        }
        if (denial != null) {
            sp.sendSystemMessage(denial);
            Claims.reopenReincarnation(sp, changeRaceOnly, racePool, customMaxAP, customMaxMP);
            cir.setReturnValue(true);
        }
    }

    /** The random pick must never land on a taken race. Only the server's own copy is filtered, the client one stays as it is. */
    @Inject(method = "getRandomRacePool", at = @At("RETURN"), cancellable = true, remap = false)
    private void tensuraUnique$filterRandom(CallbackInfoReturnable<List<ManasRace>> cir) {
        if (!(player instanceof ServerPlayer sp) || !TensuraUniqueRaces.config().enabled || !TensuraUniqueRaces.config().checkReincarnationMenu) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue().stream().filter(r -> {
            ResourceLocation id = r.getRegistryName();
            return id == null || Claims.check(sp, id) == null;
        }).toList());
    }
}
