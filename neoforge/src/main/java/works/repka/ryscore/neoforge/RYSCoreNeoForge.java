package works.repka.ryscore.neoforge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.perm.Perms;
import works.repka.ryscore.perm.Roles;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import works.repka.ryscore.migrate.Migrations;

/**
 * Общая часть кора на обеих сторонах (1.11): знает запущенный сервер и запускает серверные шаги переезда с FTB.
 * Клиентская часть живёт в {@link RYSCoreNeoForgeClient}.
 */
@Mod(RYSCore.MOD_ID)
public final class RYSCoreNeoForge {
    public RYSCoreNeoForge() {
        Migrations.loader(id -> ModList.get().isLoaded(id),
            id -> id.isEmpty() ? "" : ModList.get().getModContainerById(id).map(c -> c.getModInfo().getVersion().toString()).orElse(""));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> RYSCore.server(e.getServer()));
        // после всех модов: их хранилища уже подняты в этом же событии; игроков сервер ещё не принимает
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStartedEvent e) -> {
            try {
                Migrations.runServer(e.getServer());
            } catch (Throwable t) {
                RYSCore.LOG.error("{} migration did not start", Migrations.TAG, t);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            RYSCore.server(null);
            Roles.invalidateAll();
        });
        // роль в памяти ядра забывается при /op, /deop и выходе игрока (1.15)
        NeoForge.EVENT_BUS.addListener((PermissionsChangedEvent e) -> Roles.invalidate(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Roles.invalidate(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
        permissions();
    }

    /** Узлы прав NeoForge для {@link Perms}: имя узла {@code modid.имя}, умолчание по таблице ролей RYSHelm или роли узла. */
    private static final Map<String, PermissionNode<Boolean>> NODES = new ConcurrentHashMap<>();

    private static void permissions() {
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes e) -> {
            NODES.clear();
            for (Perms.Node node : Perms.nodes()) {
                PermissionNode<Boolean> pn = new PermissionNode<>(node.modid(), node.name(), PermissionTypes.BOOLEAN, (player, id, ctx) -> {
                    MinecraftServer server = player != null ? player.server : RYSCore.server();
                    return server != null && Perms.resolveDefault(server, id, node);
                });
                pn.setInformation(node.label(), Component.literal(node.defaultRole()));
                NODES.put(node.id(), pn);
                e.addNodes(pn);
            }
        });
        Perms.checker((player, id, node) -> {
            PermissionNode<Boolean> pn = NODES.get(node.id());
            if (pn == null || !PermissionAPI.getRegisteredNodes().contains(pn)) return null;
            try {
                return player != null ? PermissionAPI.getPermission(player, pn) : PermissionAPI.getOfflinePermission(id, pn);
            } catch (RuntimeException ex) {
                return null;
            }
        });
    }

    private static final SuggestionProvider<CommandSourceStack> STEPS = (c, b) ->
        SharedSuggestionProvider.suggest(Migrations.steps().stream().filter(s -> s.side() == Migrations.Side.SERVER).map(Migrations.Step::id), b);

    /** {@code /rys migrate status | run <шаг> | undo <шаг>}: уровень 4, отчёт в чат и лог. */
    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rys")
            .then(Commands.literal("migrate").requires(s -> s.hasPermission(4))
                .then(Commands.literal("status").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    for (Component line : Migrations.describeText(server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT).normalize())) {
                        c.getSource().sendSuccess(() -> line, false);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("run").then(Commands.argument("step", StringArgumentType.word()).suggests(STEPS)
                    .executes(c -> reply(c, Migrations.runManual(StringArgumentType.getString(c, "step"), c.getSource().getServer())))))
                .then(Commands.literal("undo").then(Commands.argument("step", StringArgumentType.word()).suggests(STEPS)
                    .executes(c -> reply(c, Migrations.undo(StringArgumentType.getString(c, "step"), c.getSource().getServer())))))));
    }

    private static int reply(CommandContext<CommandSourceStack> c, Migrations.Outcome o) {
        Component text = o.text();
        if (o.status() == Migrations.Status.FAILED || o.status() == Migrations.Status.NOT_NEEDED || o.status() == Migrations.Status.ALREADY_DONE) {
            c.getSource().sendFailure(text);
            return 0;
        }
        c.getSource().sendSuccess(() -> text, true);
        return Command.SINGLE_SUCCESS;
    }
}
