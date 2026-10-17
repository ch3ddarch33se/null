package com.ch3ddarch33se.thenull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import com.ch3ddarch33se.thenull.worldgen.WorldProvider;

/** Persistent, wall-clock block regeneration and corrosion timers. */
public final class NullBlockTimers {
    private static final long TERRAIN_MILLIS = 60_000L;
    private static final long FOREIGN_MILLIS = 90_000L;
    private static final long NATIVE_PLACED_MILLIS = 3_600_000L;
    private static final long FLICKER_START_MILLIS = 5_000L;
    private static final long FLICKER_STEP_MILLIS = 200L;

    private enum Kind { RESTORE_TERRAIN, CORRODE_FOREIGN, EXPIRE_NATIVE }
    private enum Phase { FLICKER, EXPIRE }

    private record TimerEntry(long position, long deadline, long token, Kind kind, String expectedBlock, int faceMask) {
        private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);
        private static final Codec<TimerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(TimerEntry::position),
            Codec.LONG.fieldOf("deadline").forGetter(TimerEntry::deadline),
            Codec.LONG.fieldOf("token").forGetter(TimerEntry::token),
            KIND_CODEC.fieldOf("kind").forGetter(TimerEntry::kind),
            Codec.STRING.fieldOf("expected_block").forGetter(TimerEntry::expectedBlock),
            Codec.INT.optionalFieldOf("face_mask", 0).forGetter(TimerEntry::faceMask)
        ).apply(instance, TimerEntry::new));
    }

    private record Key(ResourceKey<Level> dimension, long position) { }
    private record Pending(long at, Key key, long token, Phase phase) { }
    private static final class Active {
        final TimerEntry entry;
        long nextAt;
        Phase phase;
        boolean flickerOn;
        Active(TimerEntry entry, long nextAt, Phase phase) {
            this.entry = entry;
            this.nextAt = nextAt;
            this.phase = phase;
        }
    }

    private static final AttachmentType<List<TimerEntry>> CHUNK_TIMERS = AttachmentRegistry.<List<TimerEntry>>builder()
        .persistent(TimerEntry.CODEC.listOf())
        .buildAndRegister(Identifier.parse("thenull:block_timers"));
    private static final AtomicLong TOKENS = new AtomicLong(System.currentTimeMillis());
    private static final Map<Key, Active> ACTIVE = new HashMap<>();
    private static final PriorityQueue<Pending> QUEUE = new PriorityQueue<>(Comparator.comparingLong(Pending::at));

    private NullBlockTimers() { }

    public static void initialize() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level) || !isNullPosition(level, pos)) return;
            if (state.is(Blocks.SCULK) || state.is(Blocks.GLOW_LICHEN)) onNativeBlockBroken(level, pos, state);
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, unused) -> loadChunkTimers(level, chunk));
    }

    public static void onNativeBlockBroken(ServerLevel level, BlockPos pos, BlockState original) {
        TimerEntry previous = removeAt(level, pos);
        if (previous != null) return; // player-placed native blocks keep their own expiry rule
        schedule(level, pos, Kind.RESTORE_TERRAIN, original, TERRAIN_MILLIS);
    }

    public static void onPlayerPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isNullPosition(level, pos) || state.isAir()) return;
        boolean nativeBlock = state.is(Blocks.SCULK) || state.is(Blocks.GLOW_LICHEN);
        schedule(level, pos, nativeBlock ? Kind.EXPIRE_NATIVE : Kind.CORRODE_FOREIGN,
            state, nativeBlock ? NATIVE_PLACED_MILLIS : FOREIGN_MILLIS);
    }

    private static void schedule(ServerLevel level, BlockPos pos, Kind kind, BlockState expected, long duration) {
        ChunkAccess chunk = level.getChunkAt(pos);
        List<TimerEntry> entries = chunk.getAttached(CHUNK_TIMERS);
        ArrayList<TimerEntry> updated = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        long packed = pos.asLong();
        updated.removeIf(entry -> entry.position() == packed);
        long deadline = System.currentTimeMillis() + duration;
        long token = TOKENS.incrementAndGet();
        TimerEntry entry = new TimerEntry(packed, deadline, token, kind,
            BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString(), faceMask(expected));
        updated.add(entry);
        chunk.setAttached(CHUNK_TIMERS, List.copyOf(updated));
        activate(level, entry, System.currentTimeMillis());
    }

    private static TimerEntry removeAt(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkAt(pos);
        List<TimerEntry> entries = chunk.getAttached(CHUNK_TIMERS);
        if (entries == null || entries.isEmpty()) return null;
        long packed = pos.asLong();
        TimerEntry removed = null;
        ArrayList<TimerEntry> updated = new ArrayList<>(entries.size());
        for (TimerEntry entry : entries) {
            if (entry.position() == packed) removed = entry;
            else updated.add(entry);
        }
        if (removed != null) {
            if (updated.isEmpty()) chunk.removeAttached(CHUNK_TIMERS);
            else chunk.setAttached(CHUNK_TIMERS, List.copyOf(updated));
            ACTIVE.remove(new Key(level.dimension(), packed));
        }
        return removed;
    }

    private static void loadChunkTimers(ServerLevel level, LevelChunk chunk) {
        List<TimerEntry> entries = chunk.getAttached(CHUNK_TIMERS);
        if (entries == null) return;
        long now = System.currentTimeMillis();
        for (TimerEntry entry : entries) activate(level, entry, now);
    }

    private static void activate(ServerLevel level, TimerEntry entry, long now) {
        Key key = new Key(level.dimension(), entry.position());
        Active old = ACTIVE.get(key);
        if (old != null && old.entry.token() == entry.token()) return;
        long flickerAt = entry.deadline() - FLICKER_START_MILLIS;
        Phase phase = entry.kind() == Kind.RESTORE_TERRAIN && now >= flickerAt && now < entry.deadline()
            ? Phase.FLICKER : Phase.EXPIRE;
        long at = entry.deadline() <= now ? now : phase == Phase.FLICKER ? now : entry.deadline();
        if (phase == Phase.EXPIRE && entry.kind() == Kind.RESTORE_TERRAIN && now < flickerAt) {
            phase = Phase.FLICKER;
            at = flickerAt;
        }
        Active active = new Active(entry, at, phase);
        if (phase == Phase.FLICKER) {
            active.flickerOn = level.getBlockState(BlockPos.of(entry.position())).equals(restoreState(entry));
        }
        ACTIVE.put(key, active);
        QUEUE.add(new Pending(active.nextAt, key, entry.token(), phase));
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        while (!QUEUE.isEmpty() && QUEUE.peek().at() <= now) {
            Pending pending = QUEUE.poll();
            Active active = ACTIVE.get(pending.key());
            if (active == null || active.entry.token() != pending.token()
                || active.nextAt != pending.at() || active.phase != pending.phase()) continue;
            ServerLevel level = server.getLevel(pending.key().dimension());
            if (level == null) {
                ACTIVE.remove(pending.key());
                continue;
            }
            BlockPos pos = BlockPos.of(pending.key().position());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                ACTIVE.remove(pending.key()); // persistent chunk entry resumes on its next load
                continue;
            }
            List<TimerEntry> attached = chunk.getAttached(CHUNK_TIMERS);
            if (attached == null || !attached.contains(active.entry)) {
                ACTIVE.remove(pending.key());
                continue;
            }

            if (active.phase == Phase.FLICKER) {
                BlockState current = level.getBlockState(pos);
                BlockState restore = restoreState(active.entry);
                if (!current.isAir() && !(active.flickerOn && current.equals(restore))) {
                    clearTimer(chunk, active.entry, pending.key());
                    continue;
                }
                active.flickerOn = !active.flickerOn;
                level.setBlock(pos, active.flickerOn ? restore : Blocks.AIR.defaultBlockState(), 3);
                active.nextAt = Math.min(now + FLICKER_STEP_MILLIS, active.entry.deadline());
                active.phase = active.nextAt >= active.entry.deadline() ? Phase.EXPIRE : Phase.FLICKER;
                QUEUE.add(new Pending(active.nextAt, pending.key(), active.entry.token(), active.phase));
                continue;
            }

            expire(level, chunk, pos, active, pending.key());
        }
    }

    private static boolean isNullPosition(ServerLevel level, BlockPos pos) {
        return level.dimension().equals(WorldProvider.WORLD) && pos.getY() < -4;
    }

    private static void expire(ServerLevel level, LevelChunk chunk, BlockPos pos, Active active, Key key) {
        BlockState current = level.getBlockState(pos);
        if (active.entry.kind() == Kind.RESTORE_TERRAIN) {
            BlockState restore = restoreState(active.entry);
            boolean ownFlickerBlock = active.flickerOn && current.equals(restore);
            if (!current.isAir() && !ownFlickerBlock) {
                clearTimer(chunk, active.entry, key);
                return;
            }
            level.setBlock(pos, restore, 3);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator())) {
                entity.hurt(level.damageSources().inWall(), 2.0F);
            }
        } else {
            String currentId = BuiltInRegistries.BLOCK.getKey(current.getBlock()).toString();
            if (!currentId.equals(active.entry.expectedBlock()) || current.isAir()) {
                clearTimer(chunk, active.entry, key);
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) Containers.dropContents(level, pos, container);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        clearTimer(chunk, active.entry, key);
    }

    private static void clearTimer(ChunkAccess chunk, TimerEntry entry, Key key) {
        List<TimerEntry> attached = chunk.getAttached(CHUNK_TIMERS);
        if (attached != null) {
            ArrayList<TimerEntry> updated = new ArrayList<>(attached);
            updated.remove(entry);
            if (updated.isEmpty()) chunk.removeAttached(CHUNK_TIMERS);
            else chunk.setAttached(CHUNK_TIMERS, List.copyOf(updated));
        }
        ACTIVE.remove(key);
    }

    static long remainingMillis(ServerLevel level, BlockPos pos) {
        List<TimerEntry> entries = level.getChunkAt(pos).getAttached(CHUNK_TIMERS);
        if (entries == null) return -1;
        long packed = pos.asLong();
        return entries.stream().filter(entry -> entry.position() == packed)
            .mapToLong(entry -> entry.deadline() - System.currentTimeMillis()).findFirst().orElse(-1);
    }

    static boolean timersArePersistent() { return CHUNK_TIMERS.isPersistent(); }

    private static int faceMask(BlockState state) {
        if (!state.is(Blocks.GLOW_LICHEN)) return 0;
        int mask = 0;
        var directions = net.minecraft.core.Direction.values();
        for (int i = 0; i < directions.length; i++) {
            var property = MultifaceBlock.getFaceProperty(directions[i]);
            if (state.hasProperty(property) && state.getValue(property)) mask |= 1 << i;
        }
        return mask;
    }

    private static BlockState restoreState(TimerEntry entry) {
        if (entry.expectedBlock().equals("minecraft:glow_lichen")) {
            BlockState state = Blocks.GLOW_LICHEN.defaultBlockState();
            var directions = net.minecraft.core.Direction.values();
            for (int i = 0; i < directions.length; i++) {
                var property = MultifaceBlock.getFaceProperty(directions[i]);
                if (entry.faceMask() != 0 && (entry.faceMask() & 1 << i) != 0) state = state.setValue(property, true);
            }
            return state;
        }
        return Blocks.SCULK.defaultBlockState();
    }
}
