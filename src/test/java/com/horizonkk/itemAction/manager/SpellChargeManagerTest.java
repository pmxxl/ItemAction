package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpellChargeManagerTest {
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final ItemAction plugin = mock(ItemAction.class);
        final List<Runnable> ticks = new ArrayList<>();
        final SpellChargeManager manager = new SpellChargeManager(plugin);
        final Player player = player();

        Fixture() {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
                ticks.add(call.getArgument(1));
                return mock(BukkitTask.class);
            });
        }

        Player player() {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            return player;
        }

        @Override public void close() { manager.cleanup(); bukkit.close(); }
    }

    @Test void fullDurationCompletesExactlyOnceWithoutCancellation() {
        try (var f = new Fixture()) {
            Runnable done = mock(Runnable.class), cancel = mock(Runnable.class);
            f.manager.startChargingMillis(f.player, "breakthrough", 100, done, cancel);
            assertEquals(0, f.manager.getChargeProgressPercent(f.player.getUniqueId()));
            f.ticks.get(0).run();
            assertEquals(50, f.manager.getChargeProgressPercent(f.player.getUniqueId()));
            verifyNoInteractions(done, cancel);
            f.ticks.get(0).run();
            f.ticks.get(0).run();
            f.manager.cancelCharging(f.player.getUniqueId());
            verify(done, times(1)).run();
            verifyNoInteractions(cancel);
            assertFalse(f.manager.isPlayerCharging(f.player.getUniqueId()));
        }
    }

    @Test void replacementCancelsOldAndOldTickCannotCompleteNewCharge() {
        try (var f = new Fixture()) {
            Runnable oldDone = mock(Runnable.class), oldCancel = mock(Runnable.class), newDone = mock(Runnable.class);
            f.manager.startChargingMillis(f.player, "old", 50, oldDone, oldCancel);
            f.manager.startChargingMillis(f.player, "new", 100, newDone);
            verify(oldCancel).run();
            f.ticks.get(0).run();
            assertEquals(0, f.manager.getChargeProgressPercent(f.player.getUniqueId()));
            verifyNoInteractions(oldDone, newDone);
            f.ticks.get(1).run();
            f.ticks.get(1).run();
            verify(newDone).run();
        }
    }

    @Test void logoutAndCleanupCancelAllWithoutCompletion() {
        try (var f = new Fixture()) {
            Runnable done = mock(Runnable.class), cancel = mock(Runnable.class);
            Player other = f.player();
            f.manager.startChargingMillis(f.player, "one", 100, done, cancel);
            f.manager.startChargingMillis(other, "two", 100, done, cancel);
            when(f.player.isOnline()).thenReturn(false);
            f.ticks.get(0).run();
            f.manager.cleanup();
            f.manager.cleanup();
            verify(cancel, times(2)).run();
            verifyNoInteractions(done);
            assertFalse(f.manager.isPlayerCharging(other.getUniqueId()));
        }
    }

    @Test void cancellationCallbackStartingANewChargeCannotBeOverwritten() {
        try (var f = new Fixture()) {
            Runnable done = mock(Runnable.class);
            f.manager.startChargingMillis(f.player, "old", 100, done,
                    () -> f.manager.startChargingMillis(f.player, "from-callback", 100, done));
            assertThrows(IllegalStateException.class,
                    () -> f.manager.startChargingMillis(f.player, "outer", 100, done));
            assertEquals("from-callback", f.manager.getCurrentSpellId(f.player.getUniqueId()));
            assertEquals(2, f.ticks.size());
        }
    }
}
