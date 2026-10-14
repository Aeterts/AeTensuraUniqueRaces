package org.aeterts.tensuraunique;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class UniqueCommand {
    private UniqueCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            dispatcher.register(literal("tensura").then(literal("unique").then(literal("devtest").requires(s -> s.hasPermission(4))
                    .executes(ctx -> {
                        var lines = DevTest.run(ctx.getSource().getServer());
                        lines.forEach(l -> TensuraUniqueRaces.LOGGER.info("[devtest] {}", l));
                        // RCON cuts replies at 4 KiB: only failures and the summary go to the sender, everything is in the log
                        long passed = lines.stream().filter(l -> l.startsWith("[ok]")).count();
                        lines.stream().filter(l -> l.startsWith("[FAIL]") || l.startsWith("[EXC]")).forEach(l -> ctx.getSource().sendSuccess(() -> Component.literal(l), false));
                        ctx.getSource().sendSuccess(() -> Component.literal(passed + " checks passed; " + lines.get(lines.size() - 1)), false);
                        return lines.size();
                    }))));
        }
        dispatcher.register(literal("tensura")
                .then(literal("unique")
                        .then(literal("show").requires(s -> s.hasPermission(Claims.cfg().showPermissionLevel)).executes(ctx -> show(ctx.getSource())))
                        .then(literal("release").requires(s -> s.hasPermission(Claims.cfg().adminPermissionLevel))
                                .then(literal("race")
                                        .then(argument("race", ResourceLocationArgument.id())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(RaceGraph.allRaceIds(), b))
                                                .executes(ctx -> releaseRace(ctx.getSource(), ResourceLocationArgument.getId(ctx, "race")))))
                                .then(literal("player")
                                        .then(argument("name", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        ClaimState.get(c.getSource().getServer()).claims.values().stream().map(cl -> cl.name).toList(), b))
                                                .executes(ctx -> releasePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
                        .then(literal("reload").requires(s -> s.hasPermission(Claims.cfg().adminPermissionLevel))
                                .executes(ctx -> reload(ctx.getSource())))));
    }

    private static int show(CommandSourceStack src) {
        UniqueConfig cfg = Claims.cfg();
        ClaimState state = ClaimState.get(src.getServer());
        if (state.claims.isEmpty()) {
            src.sendSuccess(() -> UniqueConfig.format(cfg.message("noneTaken", Lang.of(src.getPlayer())), Map.of()), false);
            return 0;
        }
        Map<ResourceLocation, List<String>> byRace = new TreeMap<>();
        for (ClaimState.Claim c : state.claims.values()) {
            byRace.computeIfAbsent(c.current, k -> new ArrayList<>()).add(c.name == null || c.name.isEmpty() ? "?" : c.name);
        }
        src.sendSuccess(() -> UniqueConfig.format(cfg.message("showHeader", Lang.of(src.getPlayer())),
                Map.of("count", Component.literal(String.valueOf(byRace.size())))), false);
        for (Map.Entry<ResourceLocation, List<String>> e : byRace.entrySet()) {
            int limit = cfg.limitFor(e.getKey());
            String who = cfg.showHolderNames ? String.join(", ", e.getValue()) : String.valueOf(e.getValue().size());
            Component line = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Claims.raceName(e.getKey()).copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": " + who
                            + (limit == Integer.MAX_VALUE ? "" : " (" + e.getValue().size() + "/" + limit + ")")).withStyle(ChatFormatting.WHITE));
            src.sendSuccess(() -> line, false);
        }
        return byRace.size();
    }

    private static int releaseRace(CommandSourceStack src, ResourceLocation race) {
        int n = Claims.releaseRace(src.getServer(), race);
        String lang = Lang.of(src.getPlayer());
        String text = n == 0 ? Lang.format(lang, "command.nobodyHolds", Map.of("race", race.toString()))
                : Lang.format(lang, "command.releasedRace", Map.of("count", String.valueOf(n), "race", race.toString()));
        src.sendSuccess(() -> Component.literal(text).withStyle(n == 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN), true);
        return n;
    }

    private static int releasePlayer(CommandSourceStack src, String name) {
        boolean ok = Claims.releasePlayer(src.getServer(), name);
        String text = Lang.format(Lang.of(src.getPlayer()), ok ? "command.releasedPlayer" : "command.noClaim", Map.of("name", name));
        src.sendSuccess(() -> Component.literal(text).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        return ok ? 1 : 0;
    }

    private static int reload(CommandSourceStack src) {
        TensuraUniqueRaces.reloadConfig();
        String text = Lang.get(Lang.of(src.getPlayer()), "command.reloaded");
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
