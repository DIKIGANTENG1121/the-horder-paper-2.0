package net.neon.hordes.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.FinePosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.HordeManager;
import net.neon.hordes.core.HordePlayerState;
import net.neon.hordes.core.ZombiePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Registers /hordes Brigadier command tree via Paper 26.3 LifecycleEvents.COMMANDS (F-05).
 */
public final class HordesCommandRegistrar {

    private final HordesConfig config;
    private final HordeManager hordeManager;
    private final ZombiePlayerManager zombiePlayerManager;

    public HordesCommandRegistrar(
            HordesConfig config,
            HordeManager hordeManager,
            ZombiePlayerManager zombiePlayerManager
    ) {
        this.config = config;
        this.hordeManager = hordeManager;
        this.zombiePlayerManager = zombiePlayerManager;
    }

    public void register(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hordes")
                .then(buildStart())
                .then(buildStop())
                .then(buildReset())
                .then(buildSpawnWave())
                .then(buildDebug())
                .then(buildListEntities())
                .then(buildSpawnZombie())
                .then(buildReload());

        registrar.register(root.build(), "Manage The Hordes events, waves, and plugin configuration");
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildStart() {
        return Commands.literal("start")
                .requires(src -> src.getSender().hasPermission("hordes.command.start"))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> runStart(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)), config.hordeSpawnDuration, null))
                        .then(Commands.argument("length", IntegerArgumentType.integer(20))
                                .executes(ctx -> runStart(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)), IntegerArgumentType.getInteger(ctx, "length"), null))
                                .then(Commands.argument("table", StringArgumentType.string())
                                        .executes(ctx -> runStart(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)), IntegerArgumentType.getInteger(ctx, "length"), StringArgumentType.getString(ctx, "table"))))));
    }

    private int runStart(CommandSourceStack source, List<Player> players, int length, String table) {
        int started = 0;
        for (Player p : players) {
            if (hordeManager.startHorde(p, length, table, false)) {
                started++;
            }
        }
        source.getSender().sendMessage(Component.text("Started horde event for " + started + " player(s) (" + length + " ticks).", NamedTextColor.GREEN));
        return started;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildStop() {
        return Commands.literal("stop")
                .requires(src -> src.getSender().hasPermission("hordes.command.stop"))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player p) {
                        return runStop(ctx.getSource(), List.of(p));
                    }
                    ctx.getSource().getSender().sendMessage(Component.text("Specify a player selector from console.", NamedTextColor.RED));
                    return 0;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> runStop(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)))));
    }

    private int runStop(CommandSourceStack source, List<Player> players) {
        int stopped = 0;
        for (Player p : players) {
            if (hordeManager.stopHorde(p, false)) stopped++;
        }
        source.getSender().sendMessage(Component.text("Stopped active horde event for " + stopped + " player(s).", NamedTextColor.YELLOW));
        return stopped;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildReset() {
        return Commands.literal("reset")
                .requires(src -> src.getSender().hasPermission("hordes.command.reset"))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player p) {
                        hordeManager.resetHorde(p);
                        ctx.getSource().getSender().sendMessage(Component.text("Reset horde data for " + p.getName(), NamedTextColor.GREEN));
                        return 1;
                    }
                    ctx.getSource().getSender().sendMessage(Component.text("Specify a player selector from console.", NamedTextColor.RED));
                    return 0;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> {
                            List<Player> targets = resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class));
                            for (Player p : targets) hordeManager.resetHorde(p);
                            ctx.getSource().getSender().sendMessage(Component.text("Reset horde data for " + targets.size() + " player(s).", NamedTextColor.GREEN));
                            return targets.size();
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSpawnWave() {
        return Commands.literal("spawnWave")
                .requires(src -> src.getSender().hasPermission("hordes.command.spawnwave"))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> runSpawnWave(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)), IntegerArgumentType.getInteger(ctx, "count"), null))
                                .then(Commands.argument("table", StringArgumentType.string())
                                        .executes(ctx -> runSpawnWave(ctx.getSource(), resolvePlayers(ctx.getSource(), ctx.getArgument("players", PlayerSelectorArgumentResolver.class)), IntegerArgumentType.getInteger(ctx, "count"), StringArgumentType.getString(ctx, "table"))))));
    }

    private int runSpawnWave(CommandSourceStack source, List<Player> players, int count, String table) {
        int total = 0;
        for (Player p : players) {
            total += hordeManager.spawnWave(p, count, table, null);
        }
        source.getSender().sendMessage(Component.text("Spawned " + total + " horde mob(s) across " + players.size() + " player(s).", NamedTextColor.GREEN));
        return total;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildDebug() {
        return Commands.literal("debug")
                .requires(src -> src.getSender().hasPermission("hordes.command.debug"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage(Component.text("=== HordesPaper Debug ===", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text("Tracked players: " + hordeManager.getAllStates().size()
                            + " | Tables loaded: " + config.spawnTables.size()
                            + " | Rules loaded: " + config.scriptRules.size(), NamedTextColor.GRAY));
                    for (HordePlayerState st : hordeManager.getAllStates()) {
                        sender.sendMessage(Component.text("- " + st.getLastKnownName()
                                + " active=" + st.isActive()
                                + " nextDay=" + st.getNextHordeDay()
                                + " remTicks=" + st.getRemainingTicks()
                                + " mobs=" + st.getActiveEntities().size(), NamedTextColor.WHITE));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildListEntities() {
        return Commands.literal("listEntities")
                .requires(src -> src.getSender().hasPermission("hordes.command.listentities"))
                .executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Living EntityTypes: ");
                    int count = 0;
                    for (EntityType type : EntityType.values()) {
                        if (type.isAlive()) {
                            if (count > 0) sb.append(", ");
                            sb.append(type.name());
                            count++;
                        }
                    }
                    ctx.getSource().getSender().sendMessage(Component.text("Total alive entity types: " + count, NamedTextColor.AQUA));
                    Bukkit.getLogger().info(sb.toString());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSpawnZombie() {
        return Commands.literal("spawnZombie")
                .requires(src -> src.getSender().hasPermission("hordes.command.spawnzombie"))
                .then(Commands.argument("username", StringArgumentType.word())
                        .then(Commands.argument("pos", ArgumentTypes.finePosition())
                                .executes(ctx -> runSpawnZombie(ctx.getSource(), StringArgumentType.getString(ctx, "username"), ctx.getArgument("pos", FinePositionResolver.class), "ZOMBIE"))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(ctx -> runSpawnZombie(ctx.getSource(), StringArgumentType.getString(ctx, "username"), ctx.getArgument("pos", FinePositionResolver.class), StringArgumentType.getString(ctx, "type"))))));
    }

    private int runSpawnZombie(CommandSourceStack source, String username, FinePositionResolver posResolver, String typeRaw) {
        try {
            FinePosition fp = posResolver.resolve(source);
            World world = source.getLocation().getWorld();
            if (world == null) world = Bukkit.getWorlds().getFirst();
            Location loc = fp.toLocation(world);
            ZombiePlayerManager.Variant variant = switch (typeRaw.toUpperCase(Locale.ROOT)) {
                case "DROWNED" -> ZombiePlayerManager.Variant.DROWNED;
                case "HUSK" -> ZombiePlayerManager.Variant.HUSK;
                default -> ZombiePlayerManager.Variant.ZOMBIE;
            };
            zombiePlayerManager.spawnZombiePlayer(username, null, loc, variant, List.of());
            source.getSender().sendMessage(Component.text("Spawned " + variant + " player for " + username, NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.getSender().sendMessage(Component.text("Failed to spawn zombie player: " + e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildReload() {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("hordes.command.reload"))
                .executes(ctx -> {
                    config.reload();
                    ctx.getSource().getSender().sendMessage(Component.text("HordesPaper configuration reloaded.", NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private List<Player> resolvePlayers(CommandSourceStack source, PlayerSelectorArgumentResolver resolver) {
        try {
            return resolver.resolve(source);
        } catch (Exception e) {
            return List.of();
        }
    }
}
