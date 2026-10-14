package org.aeterts.tensuraunique;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development-only scenario run ({@code /tensura unique devtest}, registered only in a dev environment and not part of the jar).
 * Two fake players go through the real reincarnation menu and the real evolution call.
 */
public final class DevTest {
    private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-00000000aa01");
    private static final UUID ID_B = UUID.fromString("00000000-0000-0000-0000-00000000bb02");
    private static final UUID ID_C = UUID.fromString("00000000-0000-0000-0000-00000000cc03");

    private final List<String> out = new ArrayList<>();
    private int failed;

    private void check(String what, boolean ok) {
        out.add((ok ? "[ok]   " : "[FAIL] ") + what);
        if (!ok) {
            failed++;
        }
    }

    public static List<String> run(MinecraftServer server) {
        DevTest t = new DevTest();
        try {
            t.scenario(server);
        } catch (Throwable e) {
            t.out.add("[EXC]  " + e);
            TensuraUniqueRaces.LOGGER.error("devtest crashed", e);
            t.failed++;
        } finally {
            ClaimState state = ClaimState.get(server);
            state.claims.remove(ID_A);
            state.claims.remove(ID_B);
            state.claims.remove(ID_C);
            state.setDirty();
        }
        t.out.add(t.failed == 0 ? "ALL OK" : t.failed + " FAILED");
        return t.out;
    }

    private static ResourceLocation raceOf(ServerPlayer p) {
        return Claims.actualRace(p);
    }

    private static void confirm(ServerPlayer p, int index) {
        ReincarnationMenu menu = new ReincarnationMenu(1, p.getInventory(), p);
        menu.selectedManasRaceIndex.set(index);
        menu.clickMenuButton(p, -1);
    }

    private void scenario(MinecraftServer server) {
        var level = server.overworld();
        ServerPlayer a = FakePlayer.get(level, new GameProfile(ID_A, "DevA"));
        ServerPlayer b = FakePlayer.get(level, new GameProfile(ID_B, "DevB"));
        ClaimState state = ClaimState.get(server);
        state.claims.remove(ID_A);
        state.claims.remove(ID_B);
        Claims.settle(server);

        List<ManasRace> pool = new ReincarnationMenu(0, a.getInventory(), a).getRacePool();
        out.add("pool: " + pool.stream().map(r -> String.valueOf(r.getRegistryName())).toList());
        for (ManasRace r : pool) {
            out.add("tree " + r.getRegistryName() + " -> " + RaceGraph.closure(r.getRegistryName(), a));
        }
        for (int x = 0; x < pool.size(); x++) {
            for (int y = x + 1; y < pool.size(); y++) {
                var tx = new java.util.HashSet<>(RaceGraph.closure(pool.get(x).getRegistryName(), a));
                tx.retainAll(RaceGraph.closure(pool.get(y).getRegistryName(), a));
                if (!tx.isEmpty()) {
                    out.add("OVERLAP " + pool.get(x).getRegistryName() + " / " + pool.get(y).getRegistryName() + " share " + tx);
                }
            }
        }
        int i1 = -1, i2 = -1;
        for (int i = 0; i < pool.size() && i2 < 0; i++) {
            ResourceLocation id = pool.get(i).getRegistryName();
            if (id == null || Claims.isExempt(id, a)) {
                continue;
            }
            if (i1 < 0) {
                i1 = i;
            } else if (!RaceGraph.closure(pool.get(i1).getRegistryName(), a).contains(id)
                    && !RaceGraph.closure(id, a).contains(pool.get(i1).getRegistryName())) {
                i2 = i;
            }
        }
        check("found two independent non-exempt starting races", i1 >= 0 && i2 >= 0);
        if (i1 < 0 || i2 < 0) {
            return;
        }
        ResourceLocation r1 = pool.get(i1).getRegistryName();
        ResourceLocation r2 = pool.get(i2).getRegistryName();
        out.add("r1=" + r1 + " r2=" + r2 + " tree(r1)=" + RaceGraph.closure(r1, a));

        confirm(a, i1);
        Claims.settle(server);
        check("A got r1", r1.equals(raceOf(a)));
        check("A holds a claim on r1", state.claims.containsKey(ID_A) && state.claims.get(ID_A).current.equals(r1));

        confirm(b, i1);
        Claims.settle(server);
        check("B did NOT get r1", !r1.equals(raceOf(b)));
        check("B holds no claim", !state.claims.containsKey(ID_B));
        check("B's menu is queued to reopen", Claims.hasPendingReopen(ID_B));

        confirm(b, i2);
        Claims.settle(server);
        check("B got r2", r2.equals(raceOf(b)));

        ManasRace race1 = RaceGraph.race(r1);
        List<ManasRace> next = race1.getNextEvolutions(race1.createDefaultInstance(), a);
        out.add("next evolutions of r1: " + next.stream().map(r -> String.valueOf(r.getRegistryName())).toList());
        if (!next.isEmpty()) {
            ManasRace evo = next.get(0);
            ResourceLocation evoId = evo.getRegistryName();
            check("B is refused a race of A's tree (check)", Claims.check(b, evoId) != null);
            check("A can evolve inside its own tree", RaceAPI.getRaceFrom(a).evolveRace(evo));
            Claims.settle(server);
            check("A's claim now points to the evolution, root stays r1",
                    state.claims.get(ID_A) != null && state.claims.get(ID_A).current.equals(evoId) && state.claims.get(ID_A).root.equals(r1));
            confirm(b, i1);
            check("B still can not take r1 after A evolved", !r1.equals(raceOf(b)));
        }

        ManasRace outsider = RaceGraph.race(ResourceLocation.parse("tensura:vampire"));
        boolean forced = RaceAPI.getRaceFrom(a).evolveRace(outsider.createDefaultInstance());
        Claims.settle(server);
        check("forced evolution is allowed", forced);
        check("forced evolution keeps A's root r1 (tree stays reserved)",
                state.claims.get(ID_A) != null && state.claims.get(ID_A).root.equals(r1));
        confirm(b, i1);
        check("B still can not take r1 after A's forced evolution", !r1.equals(raceOf(b)));

        var dispatcher = server.getCommands().getDispatcher();
        var asB = b.createCommandSourceStack();
        try {
            int shown = dispatcher.execute("tensura unique show", asB);
            check("non-op can run /tensura unique show (" + shown + " races listed)", shown >= 1);
        } catch (Exception e) {
            check("non-op can run /tensura unique show: " + e.getMessage(), false);
        }
        for (String cmd : new String[]{"tensura unique release race tensura:elf", "tensura unique release player DevA", "tensura unique reload"}) {
            boolean refused;
            try {
                dispatcher.execute(cmd, asB);
                refused = false;
            } catch (Exception e) {
                refused = true;
            }
            check("non-op is refused: /" + cmd, refused);
        }

        // 4d. refused menu choice -> the menu really is opened again. Fabric's FakePlayer cannot open menus, so a plain
        // ServerPlayer with a (netty embedded) connection plays B here.
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        channel.pipeline().addLast(conn);
        ServerPlayer b2 = new ServerPlayer(server, level, new GameProfile(ID_B, "DevB"), net.minecraft.server.level.ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, conn, b2, CommonListenerCookie.createInitial(b2.getGameProfile(), false));
        Claims.lookup = (srv, id) -> id.equals(ID_B) ? b2 : srv.getPlayerList().getPlayer(id);
        confirm(b2, i1);
        check("refused choice queues the reopen", Claims.hasPendingReopen(ID_B));
        b2.doCloseContainer();
        for (int tick = 0; tick < 4; tick++) {
            Claims.settle(server);
        }
        check("menu is not reopened before the delay", !(b2.containerMenu instanceof ReincarnationMenu));
        Claims.settle(server);
        Claims.settle(server);
        check("menu is reopened after the delay (menu=" + b2.containerMenu.getClass().getSimpleName() + ")", b2.containerMenu instanceof ReincarnationMenu);
        b2.doCloseContainer();
        channel.finishAndReleaseAll();
        Claims.lookup = (srv, id) -> srv.getPlayerList().getPlayer(id);

        check("release player A", Claims.releasePlayer(server, "DevA"));
        confirm(b, i1);
        Claims.settle(server);
        check("after release B can take r1", r1.equals(raceOf(b)));
        check("B's old claim moved from r2 to r1", state.claims.get(ID_B) != null && state.claims.get(ID_B).root.equals(r1));

        CompoundTag saved = state.save(new CompoundTag(), server.registryAccess());
        int before = state.claims.size();
        ClaimState loaded = ClaimState.fromNbt(saved, server.registryAccess());
        check("claims survive a save/load round trip", loaded.claims.size() == before
                && loaded.claims.get(ID_B) != null && loaded.claims.get(ID_B).root.equals(r1));

        ResourceLocation human = ResourceLocation.parse("tensura:human");
        boolean aHuman = RaceAPI.getRaceFrom(a).setRace(human, false);
        boolean bHuman = RaceAPI.getRaceFrom(b).setRace(human, false);
        Claims.settle(server);
        check("Human is unlimited (both can be Human)", aHuman && bHuman && human.equals(raceOf(a)) && human.equals(raceOf(b)));
        check("Human holds no claim", !state.claims.containsKey(ID_A) && !state.claims.containsKey(ID_B));
        ResourceLocation enlightened = ResourceLocation.parse("tensura:enlightened_human");
        check("enlightened human is exempt", Claims.isExempt(enlightened, a));
        check("vampire is NOT exempt", !Claims.isExempt(ResourceLocation.parse("tensura:vampire"), a));

        configScenarios(server, a, b, pool);
    }

    private ServerPlayer c;
    private final List<ResourceLocation> poolIds = new ArrayList<>();

    private static final ResourceLocation ELF = ResourceLocation.parse("tensura:elf");
    private static final ResourceLocation DWARF = ResourceLocation.parse("tensura:dwarf");
    private static final ResourceLocation ORC = ResourceLocation.parse("tensura:orc");
    private static final ResourceLocation GOBLIN = ResourceLocation.parse("tensura:goblin");
    private static final ResourceLocation OGRE = ResourceLocation.parse("tensura:ogre");
    private static final ResourceLocation HUMAN = ResourceLocation.parse("tensura:human");

    private void cfg(String json) {
        UniqueConfig parsed = UniqueConfig.parseJson(json, TensuraUniqueRaces.LOGGER);
        if (parsed == null) {
            check("test config is valid JSON: " + json, false);
            parsed = new UniqueConfig();
        }
        TensuraUniqueRaces.useConfig(parsed);
    }

    private void reset(MinecraftServer server, ServerPlayer... players) {
        cfg("{}");
        ClaimState state = ClaimState.get(server);
        for (ServerPlayer p : players) {
            RaceAPI.getRaceFrom(p).setRace(HUMAN, false);
        }
        Claims.settle(server);
        state.claims.remove(ID_A);
        state.claims.remove(ID_B);
        state.claims.remove(ID_C);
    }

    private boolean pick(MinecraftServer server, ServerPlayer p, ResourceLocation race) {
        int index = poolIds.indexOf(race);
        if (index < 0) {
            check("race " + race + " is in the reincarnation pool", false);
            return false;
        }
        confirm(p, index);
        Claims.settle(server);
        return race.equals(raceOf(p));
    }

    /** Russian text is recognised by its Cyrillic letters (this file stays free of them). */
    private static boolean isRussian(String text) {
        return text != null && text.chars().anyMatch(ch -> ch >= 0x0400 && ch <= 0x04FF);
    }

    private static java.util.Set<String> placeholders(String text) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[a-zA-Z]+}").matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    private String refusal(ServerPlayer p, ResourceLocation race) {
        Component msg = Claims.check(p, race);
        return msg == null ? null : msg.getString();
    }

    private static boolean runs(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String command,
                                net.minecraft.commands.CommandSourceStack source) {
        try {
            dispatcher.execute(command, source);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void configScenarios(MinecraftServer server, ServerPlayer a, ServerPlayer b, List<ManasRace> pool) {
        UniqueConfig original = TensuraUniqueRaces.config();
        c = FakePlayer.get(server.overworld(), new GameProfile(ID_C, "DevC"));
        for (ManasRace r : pool) {
            poolIds.add(r.getRegistryName());
        }
        ClaimState state = ClaimState.get(server);
        out.add("--- config scenarios ---");
        try {
            reset(server, a, b, c);
            cfg("{\"maxPlayersPerRace\": 2}");
            check("[max=2] A takes elf", pick(server, a, ELF));
            check("[max=2] B takes elf as well", pick(server, b, ELF));
            String full = refusal(c, ELF);
            check("[max=2] C is refused, with the 'full' text", full != null && full.contains("full"));
            check("[max=2] both holders recorded", state.claims.size() == 2);

            reset(server, a, b, c);
            cfg("{\"maxPlayersPerRace\": 0}");
            check("[max=0 unlimited] A, B, C all take dwarf", pick(server, a, DWARF) && pick(server, b, DWARF) && pick(server, c, DWARF));

            reset(server, a, b, c);
            cfg("{\"maxPlayersPerRace\": 1, \"perRaceLimits\": {\"tensura:dwarf\": 2, \"tensura:orc\": 0}}");
            check("[perRace] dwarf: A and B", pick(server, a, DWARF) && pick(server, b, DWARF));
            check("[perRace] dwarf: C refused", refusal(c, DWARF) != null);
            check("[perRace] orc unlimited: A,B,C", pick(server, a, ORC) && pick(server, b, ORC) && pick(server, c, ORC));
            check("[perRace] elf still limited to 1", pick(server, a, ELF) && refusal(b, ELF) != null);

            reset(server, a, b, c);
            cfg("{\"maxPlayersPerRace\": 0, \"strictlyUniqueRaces\": [\"tensura:dwarf\"]}");
            check("[strict] dwarf is single", pick(server, a, DWARF) && refusal(b, DWARF) != null);
            check("[strict] other races are free", pick(server, a, ELF) && pick(server, b, ELF) && pick(server, c, ELF));

            reset(server, a, b, c);
            cfg("{\"exemptRaces\": [\"tensura:human\", \"tensura:elf\"]}");
            check("[exempt] elf is free for A and B, no claims", pick(server, a, ELF) && pick(server, b, ELF) && state.claims.isEmpty());
            check("[exempt] dwarf is still limited", pick(server, a, DWARF) && refusal(b, DWARF) != null);
            reset(server, a, b, c);
            cfg("{\"exemptRaces\": [\"tensura:elf\"], \"exemptIncludesEvolutions\": true}");
            check("[exemptIncludesEvolutions] whole elf tree is exempt", Claims.isExempt(ResourceLocation.parse("tensura:divine_elf"), a));
            cfg("{\"exemptRaces\": [\"tensura:elf\"]}");
            check("[exempt] without it only the listed race", !Claims.isExempt(ResourceLocation.parse("tensura:divine_elf"), a));
            cfg("{\"exemptRaces\": [\"tensura:human\", \"Bad Id!\"]}");
            check("[exempt] an invalid id is ignored, the valid one works", Claims.isExempt(HUMAN, a));

            reset(server, a, b, c);
            cfg("{\"bypassPlayers\": [\"devb\", \"" + ID_C + "\"]}");
            check("[bypass] A takes dwarf", pick(server, a, DWARF));
            check("[bypass] B (by name, any case) takes it too", pick(server, b, DWARF));
            check("[bypass] C (by UUID) takes it too", pick(server, c, DWARF));
            check("[bypass] bypassed players hold no claim", state.claims.size() == 1 && state.claims.containsKey(ID_A));
            reset(server, a, b, c);
            cfg("{\"bypassPermissionLevel\": 0}");
            check("[bypassLevel=0] everybody is bypassed", pick(server, a, DWARF) && pick(server, b, DWARF));
            reset(server, a, b, c);
            pick(server, a, DWARF);
            cfg("{\"bypassPermissionLevel\": 4}");
            check("[bypassLevel=4] a player without op rights is not bypassed", refusal(b, DWARF) != null);

            reset(server, a, b, c);
            cfg("{\"checkReincarnationMenu\": false}");
            check("[checkMenu=false] B gets a held race", pick(server, a, DWARF) && pick(server, b, DWARF));
            check("[checkMenu=false] both are recorded (over the limit)", state.claims.size() == 2);
            reset(server, a, b, c);
            cfg("{\"enabled\": false}");
            check("[enabled=false] B gets a held race, nothing recorded", pick(server, a, DWARF) && pick(server, b, DWARF) && state.claims.isEmpty());
            reset(server, a, b, c);
            cfg("{\"reopenMenuAfterDenial\": false}");
            pick(server, a, DWARF);
            pick(server, b, DWARF);
            check("[reopen=false] a refused choice does not queue a reopen", !Claims.hasPendingReopen(ID_B));
            cfg("{}");
            pick(server, b, DWARF);
            check("[reopen=true] it does", Claims.hasPendingReopen(ID_B));

            reset(server, a, b, c);
            cfg("{\"perRaceLimits\": {\"tensura:elf\": 2, \"tensura:enlightened_elf\": 1}}");
            ManasRace enlightened = RaceGraph.race(ResourceLocation.parse("tensura:enlightened_elf"));
            pick(server, a, ELF);
            pick(server, b, ELF);
            check("[evolution] A evolves", RaceAPI.getRaceFrom(a).evolveRace(enlightened));
            Claims.settle(server);
            check("[evolution] B's evolution into the held form is refused", !RaceAPI.getRaceFrom(b).evolveRace(enlightened));
            cfg("{\"perRaceLimits\": {\"tensura:elf\": 2, \"tensura:enlightened_elf\": 1}, \"checkEvolutions\": false}");
            check("[checkEvolutions=false] B's evolution goes through", RaceAPI.getRaceFrom(b).evolveRace(enlightened));
            Claims.settle(server);

            reset(server, a, b, c);
            ResourceLocation divineElf = ResourceLocation.parse("tensura:divine_elf");
            pick(server, a, ELF);
            check("[tree] elf holder reserves the elf tree", refusal(b, divineElf) != null);
            cfg("{\"lockEvolutionPaths\": false}");
            check("[tree] lockEvolutionPaths=false: only the current race", refusal(b, divineElf) == null && refusal(b, ELF) != null);
            cfg("{\"noTreeLockRaces\": [\"tensura:elf\"]}");
            check("[tree] noTreeLockRaces", refusal(b, divineElf) == null);
            cfg("{\"excludeFromTree\": {\"tensura:elf\": [\"tensura:divine_elf\"]}}");
            check("[tree] excludeFromTree frees one form, keeps the others",
                    refusal(b, divineElf) == null && refusal(b, ResourceLocation.parse("tensura:elf_saint")) != null);
            cfg("{\"extraTreeRaces\": {\"tensura:elf\": [\"tensura:vampire\"]}}");
            check("[tree] extraTreeRaces adds a race", refusal(b, ResourceLocation.parse("tensura:vampire")) != null);
            cfg("{}");
            check("[tree] default: vampire is not part of the elf tree", refusal(b, ResourceLocation.parse("tensura:vampire")) == null);
            reset(server, a, b, c);
            pick(server, a, GOBLIN);
            check("[tree] treeStopsAtBaseRaces=true: goblin does not swallow ogre", refusal(b, OGRE) == null);
            cfg("{\"treeStopsAtBaseRaces\": false}");
            check("[tree] treeStopsAtBaseRaces=false: it does", refusal(b, OGRE) != null);

            reset(server, a, b, c);
            pick(server, a, DWARF);
            String def = refusal(b, DWARF);
            check("[messages] default text names the holder", def != null && def.contains("DevA") && def.contains("already taken"));
            cfg("{\"showHolderNames\": false}");
            String hidden = refusal(b, DWARF);
            check("[showHolderNames=false] holder is hidden", hidden != null && !hidden.contains("DevA") && hidden.contains("another player"));
            cfg("{\"messages\": {\"taken\": \"&aTAKEN {race} by {holders} limit {limit}\"}}");
            String custom = refusal(b, DWARF);
            check("[messages] custom template with placeholders", custom != null && custom.startsWith("TAKEN ") && custom.contains("by DevA limit 1"));
            var formatted = UniqueConfig.format("&cA&lB&rC {x}", java.util.Map.of("x", Component.literal("X")));
            check("[messages] colour codes and placeholders in format()", formatted.getString().equals("ABC X")
                    && formatted.getSiblings().get(0).getStyle().getColor() != null);
            cfg("{\"logDenials\": true}");
            check("[logDenials] a refusal with logging on still works", refusal(b, DWARF) != null);

            reset(server, a, b, c);
            cfg("{\"inactiveReleaseDays\": 1}");
            pick(server, a, DWARF);
            pick(server, b, ELF);
            state.claims.get(ID_A).lastSeen = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000;
            Claims.expireInactive(server);
            check("[inactive] a claim away for 2 days is released", !state.claims.containsKey(ID_A));
            check("[inactive] a recent claim stays", state.claims.containsKey(ID_B));
            reset(server, a, b, c);
            cfg("{\"inactiveReleaseDays\": 0}");
            pick(server, a, DWARF);
            state.claims.get(ID_A).lastSeen = 0;
            Claims.expireInactive(server);
            check("[inactive=0] never releases", state.claims.containsKey(ID_A));

            reset(server, a, b, c);
            pick(server, a, DWARF);
            var dispatcher = server.getCommands().getDispatcher();
            var asC = c.createCommandSourceStack();
            check("[commands] show works for everybody by default", runs(dispatcher, "tensura unique show", asC));
            cfg("{\"showPermissionLevel\": 2}");
            check("[showPermissionLevel=2] show is refused for a non-op", !runs(dispatcher, "tensura unique show", asC));
            cfg("{}");
            check("[adminLevel=2] release is refused for a non-op", !runs(dispatcher, "tensura unique release player DevA", asC));
            cfg("{\"adminPermissionLevel\": 0}");
            check("[adminLevel=0] release player works for a non-op",
                    runs(dispatcher, "tensura unique release player DevA", asC) && !state.claims.containsKey(ID_A));
            pick(server, a, DWARF);
            check("[adminLevel=0] release race works",
                    runs(dispatcher, "tensura unique release race tensura:dwarf", asC) && !state.claims.containsKey(ID_A));

            reset(server, a, b, c);
            pick(server, a, DWARF);
            String english = refusal(b, DWARF);
            check("[lang] auto: a player whose game is English gets English", english != null && english.contains("already taken"));
            cfg("{\"language\": \"ru\"}");
            String russian = refusal(b, DWARF);
            check("[lang] language=ru gives Russian, with the holder", russian != null && isRussian(russian) && russian.contains("DevA"));
            cfg("{\"language\": \"ru_ru\"}");
            check("[lang] ru_ru is the same as ru", isRussian(refusal(b, DWARF)));
            cfg("{\"language\": \"xx_yy\"}");
            check("[lang] an unknown language falls back to English", refusal(b, DWARF).contains("already taken"));
            cfg("{}");
            ServerPlayer ruClient = new ServerPlayer(server, server.overworld(), new GameProfile(ID_B, "DevB"),
                    new net.minecraft.server.level.ClientInformation("ru_ru", 10, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0,
                            net.minecraft.world.entity.HumanoidArm.RIGHT, false, true));
            check("[lang] auto: a Russian game gets Russian", isRussian(Claims.check(ruClient, DWARF).getString()));
            ServerPlayer noFileClient = new ServerPlayer(server, server.overworld(), new GameProfile(ID_B, "DevB"),
                    new net.minecraft.server.level.ClientInformation("eo_uy", 10, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0,
                            net.minecraft.world.entity.HumanoidArm.RIGHT, false, true));
            check("[lang] auto: a language without a file gets English", Claims.check(noFileClient, DWARF).getString().contains("already taken"));
            cfg("{\"language\": \"ru\", \"messages\": {\"taken\": \"MINE {race} {holders}\"}}");
            String mine = refusal(b, DWARF);
            check("[lang] a text changed in the config is used as written, in every language", mine != null && mine.startsWith("MINE ") && mine.contains("DevA"));
            cfg("{\"language\": \"ru\", \"messages\": {\"taken\": \"&cThe race &e{race}&c is already taken by &f{holders}&c. Choose another one.\"}}");
            check("[lang] an old config that still holds the English default is localized", isRussian(refusal(b, DWARF)));
            cfg("{\"language\": \"ru\", \"showHolderNames\": false}");
            check("[lang] the 'another player' text is localized", refusal(b, DWARF).contains(Lang.get("ru_ru", "message.someone")));
            check("[lang] normalize: ru, RU-ru and ru_ru", Lang.normalize("ru").equals("ru_ru") && Lang.normalize("RU-ru").equals("ru_ru") && Lang.normalize("ru_ru").equals("ru_ru"));
            StringBuilder badLanguages = new StringBuilder();
            for (String code : Lang.languages()) {
                if (!Lang.has(code) || !Lang.keys(code).equals(Lang.keys("en_us"))) {
                    badLanguages.append(code).append(" (keys) ");
                    continue;
                }
                for (String key : Lang.keys("en_us")) {
                    if (!placeholders(Lang.get("en_us", key)).equals(placeholders(Lang.get(code, key)))) {
                        badLanguages.append(code).append(':').append(key).append(' ');
                    }
                }
            }
            check("[lang] all " + Lang.languages().size() + " languages have every key and the same placeholders" + (badLanguages.length() == 0 ? "" : ": " + badLanguages),
                    Lang.languages().size() >= 33 && badLanguages.length() == 0);
            check("[lang] regional variants use the file of their language",
                    Lang.normalize("de_at").equals("de_de") && Lang.normalize("es_mx").equals("es_es") && Lang.normalize("pt_pt").equals("pt_br")
                            && Lang.normalize("fr_ca").equals("fr_fr") && Lang.normalize("zh_hk").equals("zh_tw") && Lang.normalize("zh_sg").equals("zh_cn")
                            && Lang.normalize("nb_no").equals("no_no") && Lang.normalize("en_gb").equals("en_us") && Lang.normalize("ja").equals("ja_jp"));
            check("[lang] a language without a file keeps its code and falls back to English",
                    Lang.normalize("xx_yy").equals("xx_yy") && Lang.get("xx_yy", "message.someone").equals(Lang.get("en_us", "message.someone")));
            UniqueConfig.Messages defaults = new UniqueConfig.Messages();
            check("[lang] the defaults of the config equal the English texts",
                    defaults.taken.equals(Lang.get("en_us", "message.taken")) && defaults.full.equals(Lang.get("en_us", "message.full"))
                            && defaults.someone.equals(Lang.get("en_us", "message.someone")) && defaults.noFreeRace.equals(Lang.get("en_us", "message.noFreeRace"))
                            && defaults.noneTaken.equals(Lang.get("en_us", "message.noneTaken")) && defaults.showHeader.equals(Lang.get("en_us", "message.showHeader")));
            check("[lang] a command text with arguments", Lang.format("ru_ru", "command.releasedPlayer", java.util.Map.of("name", "Steve")).equals(Lang.get("ru_ru", "command.releasedPlayer").replace("{name}", "Steve")) && isRussian(Lang.get("ru_ru", "command.releasedPlayer")));

            check("[json] garbage is rejected", UniqueConfig.parseJson("{ not json", TensuraUniqueRaces.LOGGER) == null);
            UniqueConfig empty = UniqueConfig.parseJson("{}", TensuraUniqueRaces.LOGGER);
            check("[json] {} gives the defaults", empty != null && empty.maxPlayersPerRace == 1 && empty.lockEvolutionPaths
                    && empty.isExempt(HUMAN) && empty.limitFor(DWARF) == 1);
        } finally {
            reset(server, a, b, c);
            TensuraUniqueRaces.useConfig(original);
        }
    }
}
