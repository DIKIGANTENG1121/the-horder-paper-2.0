package net.neon.testkit;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.neon.hordes.HordesPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Dev-only test harness plugin (NOT for release).
 * Exposes /neontest run <id|all> and supports automated headless execution via -Dneon.autorun=true.
 */
public final class NeonTestkitPlugin extends JavaPlugin {

    private AcceptanceTests tests;

    @Override
    public void onEnable() {
        Plugin target = Bukkit.getPluginManager().getPlugin("HordesPaper");
        if (!(target instanceof HordesPaperPlugin hordesPlugin)) {
            getLogger().severe("HordesPaper plugin not found! NeonTestkit cannot run.");
            return;
        }
        this.tests = new AcceptanceTests(this, hordesPlugin);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("neontest")
                            .requires(src -> src.getSender().hasPermission("neontest.run"))
                            .then(Commands.literal("run")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                executeAndReport(ctx.getSource().getSender(), id, false);
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .build(),
                    "Run HordesPaper acceptance tests (AT-01..AT-23 or all)"
            );
        });

        if (Boolean.getBoolean("neon.autorun")) {
            getLogger().info("neon.autorun=true detected — scheduling AT-01..AT-23 suite in 40 ticks.");
            Bukkit.getScheduler().runTaskLater(this, () ->
                    executeAndReport(Bukkit.getConsoleSender(), "all", Boolean.getBoolean("neon.autoshutdown")), 40L);
        }
    }

    private void executeAndReport(CommandSender sender, String targetId, boolean shutdownAfter) {
        CompletableFuture<List<AcceptanceTests.TestResult>> future = "all".equalsIgnoreCase(targetId)
                ? tests.runAllAsync()
                : tests.runSingleAsync(targetId).thenApply(List::of);

        future.whenComplete((results, error) -> Bukkit.getScheduler().runTask(this, () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== NEON TESTKIT RESULTS (").append(Instant.now()).append(") ===\n");
            sb.append("Server: ").append(Bukkit.getVersion()).append(" | Bukkit: ").append(Bukkit.getBukkitVersion()).append("\n");
            sb.append("Java: ").append(System.getProperty("java.version")).append("\n");

            int passed = 0;
            int failed = 0;
            int info = 0;
            int skipped = 0;
            if (error != null || results == null) {
                failed = 1;
                String msg = "[FAIL] SUITE — Unhandled exception: " + (error != null ? error.getMessage() : "null results");
                sb.append(msg).append("\n");
                sender.sendMessage(Component.text(msg, NamedTextColor.RED));
            } else {
                for (AcceptanceTests.TestResult r : results) {
                    String tag;
                    NamedTextColor color;
                    if (r.informational()) {
                        tag = "INFO";
                        color = r.skipped() ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
                        info++;
                        if (r.skipped()) skipped++;
                    } else if (r.passed()) {
                        tag = "PASS";
                        color = NamedTextColor.GREEN;
                        passed++;
                    } else {
                        tag = "FAIL";
                        color = NamedTextColor.RED;
                        failed++;
                    }
                    String line = String.format("[%s] %s — %s", tag, r.id(), r.detail());
                    sb.append(line).append("\n");
                    sender.sendMessage(Component.text(line, color));
                }
            }
            sb.append(String.format("SUMMARY: %d PASS, %d FAIL, %d INFO (%d SKIPPED) (total %d)\n",
                    passed, failed, info, skipped, results != null ? results.size() : 0));

            Path outFile = Path.of("neon-testkit-results.txt");
            try {
                Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8);
                getLogger().info("Wrote structured test results to " + outFile.toAbsolutePath());
            } catch (IOException e) {
                getLogger().severe("Failed to write neon-testkit-results.txt: " + e.getMessage());
            }

            if (shutdownAfter) {
                getLogger().info("neon.autoshutdown=true — stopping test server cleanly.");
                Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
            }
        }));
    }
}
