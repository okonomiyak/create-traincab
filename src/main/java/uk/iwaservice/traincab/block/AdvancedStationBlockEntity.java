package uk.iwaservice.traincab.block;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.iwaservice.traincab.client.CustomSoundClient;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;
import uk.iwaservice.traincab.mascon.DepartureBell;
import uk.iwaservice.traincab.mascon.Mascon;
import uk.iwaservice.traincab.sound.CustomSounds;

/**
 * Create's station plus the departure melody it plays (see AdvancedStationBlock) and a departure signal: a Create
 * signal boundary just past the station, so the track beyond is its own block and trains wait at the station until
 * it is free.
 */
public class AdvancedStationBlockEntity extends StationBlockEntity implements BellSound {

    /** How far past the station, along the track, the departure signal stands. */
    private static final double SIGNAL_OFFSET = 1;
    /** Built-in 接近メロディ: {tick, note-block note (0 = F#3 .. 24 = F#5)}, a rising chime played twice. */
    private static final int[][] CHIME = {
        {0, 10}, {4, 14}, {8, 17}, {12, 22}, {24, 10}, {28, 14}, {32, 17}, {36, 22},
    };
    private static final int CHIME_LENGTH = 48;

    private String soundHash = "";
    private String soundName = "";
    private int soundBytes;
    private boolean melody = true;
    @Nullable
    private UUID signalId;
    /** 双方向: trains may stop here travelling either way (see GlobalStationMixin). */
    private boolean twoWay;
    /** 接近メロディ: an uploaded sound, else the built-in chime if approachChime, else none. */
    @Nullable
    private Clip approach;
    private boolean approachChime = true;
    /** The train the 接近メロディ last played for, so it plays once per approach. */
    @Nullable
    private UUID approachedTrain;
    /** Tick of the built-in chime being played, or -1. */
    private int chimeTick = -1;
    /** Game time the uploaded 接近メロディ ends, so stopping it can't cut off a departure melody played after it. */
    private long approachEnds;

    public AdvancedStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            return;
        }
        tickChime();
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        SignalBoundary signal = departureSignal();
        GlobalStation station = getStation();
        // The station on the track is recreated whenever the track changes, so keep handing it the setting.
        if (station instanceof TwoWayStation twoWayStation && twoWayStation.traincab$twoWay() != twoWay) {
            twoWayStation.traincab$setTwoWay(twoWay);
        }
        Train train = station == null ? null : station.getPresentTrain();
        // The lamp turns green once the block ahead is free and the melody has finished.
        boolean clear = (signal == null || signal.cachedStates.getFirst() != SignalState.RED)
            && (train == null || !DepartureBell.isRinging(train));
        // The train it played for has stopped here: the 接近メロディ has done its job (another train already
        // standing at a 双方向 station doesn't cut it off).
        if (train != null && train.id.equals(approachedTrain)) {
            stopApproach();
        }
        Train approaching = station == null ? null : approachingTrain(station);
        if (approaching == null) {
            approachedTrain = null;
        } else if (!approaching.id.equals(approachedTrain)) {
            approachedTrain = approaching.id;
            playApproach();
        }
        BlockState state = getBlockState();
        if (state.hasProperty(AdvancedStationBlock.CLEAR) && state.getValue(AdvancedStationBlock.CLEAR) != clear) {
            level.setBlock(worldPosition, state.setValue(AdvancedStationBlock.CLEAR, clear), Block.UPDATE_CLIENTS);
        }
    }

    /** A train on its way to stop here and close enough for the 接近メロディ: scheduled, or driven with a mascon. */
    @Nullable
    private static Train approachingTrain(GlobalStation station) {
        double range = TrainCabServerConfig.approachDistance;
        Train train = station.getNearestTrain();
        if (train != null && train.navigation.destination == station && train.navigation.distanceToDestination < range) {
            return train;
        }
        return Mascon.approaching(station, range);
    }

    private void stopApproach() {
        chimeTick = -1;
        if (level.getGameTime() < approachEnds && level instanceof ServerLevel server) {
            CustomSounds.play(server, worldPosition, "");
        }
        approachEnds = 0;
    }

    private void playApproach() {
        if (approach != null && level instanceof ServerLevel server
            && CustomSounds.exists(server.getServer(), approach.hash())) {
            CustomSounds.play(server, worldPosition, approach.hash());
            approachEnds = level.getGameTime() + CustomSounds.ticks(approach.bytes());
        } else if (approach == null && approachChime) {
            chimeTick = 0;
        }
    }

    private void tickChime() {
        if (chimeTick < 0) {
            return;
        }
        for (int[] note : CHIME) {
            if (note[0] == chimeTick) {
                float pitch = (float) Math.pow(2, (note[1] - 12) / 12.0);
                // Volume above 1 only widens the range: heard up to ~32 blocks away, like the departure bell.
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2f, pitch);
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, pitch);
            }
        }
        if (++chimeTick >= CHIME_LENGTH) {
            chimeTick = -1;
        }
    }

    /** Finds the departure signal, putting it (back) on the track if it is missing or the station has moved. */
    @Nullable
    private SignalBoundary departureSignal() {
        GlobalStation station = getStation();
        if (station == null) {
            return null;
        }
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            if (graph.getPoint(EdgePointType.STATION, station.id) == null) {
                continue;
            }
            SignalBoundary signal = signalId == null ? null : graph.getPoint(EdgePointType.SIGNAL, signalId);
            if (signal != null && signal.blockEntities.getFirst().containsKey(worldPosition)
                && signal.edgeLocation.equals(station.edgeLocation)
                && Math.abs(signal.position - station.position - SIGNAL_OFFSET) < 1e-3) {
                return signal;
            }
            removeSignal();
            return placeSignal(graph, station);
        }
        return null;
    }

    @Nullable
    private SignalBoundary placeSignal(TrackGraph graph, GlobalStation station) {
        // Trains stop travelling from the first towards the second node, so they leave towards the second.
        TrackNode back = graph.locateNode(station.edgeLocation.getFirst());
        TrackNode front = graph.locateNode(station.edgeLocation.getSecond());
        TrackEdge edge = back == null || front == null ? null : graph.getConnectionsFrom(back).get(front);
        double position = station.position + SIGNAL_OFFSET;
        // ponytail: no signal when a junction or curve starts right after the station; Create's own signal works there
        if (edge == null || position > edge.getLength() - 0.25) {
            return null;
        }
        EdgeData data = edge.getEdgeData();
        for (EdgePointType<?> type : EdgePointType.TYPES.values()) {
            if (data.get(type, position) != null) {
                return null;
            }
        }
        SignalBoundary signal = EdgePointType.SIGNAL.create();
        signalId = UUID.randomUUID();
        signal.setId(signalId);
        signal.setLocation(station.edgeLocation, position);
        // Listed on both sides: a side without a block entity would be one-way.
        signal.blockEntityAdded(this, true);
        signal.blockEntityAdded(this, false);
        graph.addPoint(EdgePointType.SIGNAL, signal);
        setChanged();
        return signal;
    }

    private void removeSignal() {
        if (signalId == null) {
            return;
        }
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            SignalBoundary signal = graph.getPoint(EdgePointType.SIGNAL, signalId);
            if (signal != null && signal.blockEntities.getFirst().containsKey(worldPosition)) {
                // Leaves no block entity on either side, so Create takes the signal off the track.
                signal.blockEntityRemoved(worldPosition, true);
            }
        }
        signalId = null;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (!level.isClientSide) {
            removeSignal();
        }
    }

    public boolean twoWay() {
        return twoWay;
    }

    @Nullable
    public Clip approach() {
        return approach;
    }

    public boolean approachChime() {
        return approachChime;
    }

    public void setApproach(@Nullable Clip clip, boolean chime) {
        approach = clip;
        approachChime = chime;
        notifyUpdate();
    }

    public void toggleTwoWay() {
        twoWay = !twoWay;
        notifyUpdate();
    }

    @Override
    public boolean hasSound() {
        return !soundHash.isEmpty();
    }

    @Override
    public String soundHash() {
        return soundHash;
    }

    @Override
    public String soundName() {
        return soundName;
    }

    @Override
    public int soundBytes() {
        return soundBytes;
    }

    @Override
    public boolean melody() {
        return melody;
    }

    @Override
    public void setSound(String hash, String name, int bytes) {
        soundHash = hash;
        soundName = name;
        soundBytes = bytes;
        notifyUpdate();
    }

    @Override
    public void setBuiltin(boolean melody) {
        soundHash = "";
        soundName = "";
        soundBytes = 0;
        this.melody = melody;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("Melody", melody);
        tag.putBoolean("TwoWay", twoWay);
        tag.putBoolean("ApproachChime", approachChime);
        if (approach != null) {
            CompoundTag c = new CompoundTag();
            c.putString("Hash", approach.hash());
            c.putString("Name", approach.name());
            c.putInt("Bytes", approach.bytes());
            tag.put("Approach", c);
        }
        if (signalId != null && !clientPacket) {
            tag.putUUID("DepartureSignal", signalId);
        }
        if (hasSound()) {
            tag.putString("SoundHash", soundHash);
            tag.putString("SoundName", soundName);
            tag.putInt("SoundBytes", soundBytes);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        melody = !tag.contains("Melody") || tag.getBoolean("Melody");
        soundHash = tag.getString("SoundHash");
        soundName = tag.getString("SoundName");
        soundBytes = tag.getInt("SoundBytes");
        twoWay = tag.getBoolean("TwoWay");
        approachChime = !tag.contains("ApproachChime") || tag.getBoolean("ApproachChime");
        CompoundTag c = tag.getCompound("Approach");
        approach = c.contains("Hash") ? new Clip(c.getString("Hash"), c.getString("Name"), c.getInt("Bytes")) : null;
        if (clientPacket && approach != null) {
            CustomSoundClient.prefetch(approach.hash());
        }
        if (!clientPacket) {
            signalId = tag.hasUUID("DepartureSignal") ? tag.getUUID("DepartureSignal") : null;
        }
        if (clientPacket && hasSound()) {
            CustomSoundClient.prefetch(soundHash);
        }
    }
}
