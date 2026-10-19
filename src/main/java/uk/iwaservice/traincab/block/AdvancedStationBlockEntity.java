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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import uk.iwaservice.traincab.client.CustomSoundClient;
import uk.iwaservice.traincab.mascon.DepartureBell;

/**
 * Create's station plus the departure melody it plays (see AdvancedStationBlock) and a departure signal: a Create
 * signal boundary just past the station, so the track beyond is its own block and trains wait at the station until
 * it is free.
 */
public class AdvancedStationBlockEntity extends StationBlockEntity implements BellSound {

    /** How far past the station, along the track, the departure signal stands. */
    private static final double SIGNAL_OFFSET = 1;

    private String soundHash = "";
    private String soundName = "";
    private int soundBytes;
    private boolean melody = true;
    @Nullable
    private UUID signalId;

    public AdvancedStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Create registers the station's schedule depot as an item handler for its own block entity type only. */
    public IItemHandler depotItems() {
        return depotBehaviour.itemHandler;
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide || level.getGameTime() % 10 != 0) {
            return;
        }
        SignalBoundary signal = departureSignal();
        GlobalStation station = getStation();
        Train train = station == null ? null : station.getPresentTrain();
        // The lamp turns green once the block ahead is free and the melody has finished.
        boolean clear = (signal == null || signal.cachedStates.getFirst() != SignalState.RED)
            && (train == null || !DepartureBell.isRinging(train));
        BlockState state = getBlockState();
        if (state.hasProperty(AdvancedStationBlock.CLEAR) && state.getValue(AdvancedStationBlock.CLEAR) != clear) {
            level.setBlock(worldPosition, state.setValue(AdvancedStationBlock.CLEAR, clear), Block.UPDATE_CLIENTS);
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
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Melody", melody);
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
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        melody = !tag.contains("Melody") || tag.getBoolean("Melody");
        soundHash = tag.getString("SoundHash");
        soundName = tag.getString("SoundName");
        soundBytes = tag.getInt("SoundBytes");
        if (!clientPacket) {
            signalId = tag.hasUUID("DepartureSignal") ? tag.getUUID("DepartureSignal") : null;
        }
        if (clientPacket && hasSound()) {
            CustomSoundClient.prefetch(soundHash);
        }
    }
}
