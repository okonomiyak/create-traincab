package uk.iwaservice.traincab.sound;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * サーバーの音: VVVF sounds players saved on the server from VvvfEditorScreen's サーバー tab, to load on any inverter
 * later, by anyone. Kept with the world (data/traincab_vvvf_library.dat), by name. Anyone who can build may save,
 * overwriting only their own sounds of the same name; the one who saved a sound and operators may delete it.
 */
public class VvvfLibrary extends SavedData {

    public static final int MAX_PER_PLAYER = 20;
    public static final int MAX_TOTAL = 200;
    private static final String FILE = "traincab_vvvf_library";
    private static final Factory<VvvfLibrary> FACTORY = new Factory<>(VvvfLibrary::new, VvvfLibrary::load);

    private record Entry(String name, String code, UUID owner, String ownerName) {
    }

    private final List<Entry> entries = new ArrayList<>();

    static VvvfLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    /** Every sound, by name, as {@code player} sees them (which they may delete), with what their last action did. */
    VvvfLibraryList list(ServerPlayer player, String message) {
        return new VvvfLibraryList(entries.stream().sorted(Comparator.comparing(Entry::name))
            .map(e -> new VvvfLibraryList.Entry(e.name, e.code, e.ownerName, mayChange(player, e))).toList(), message);
    }

    /** Saves the sound in {@code code} under its name. Returns what happened, for the player. */
    String save(ServerPlayer player, String code) {
        VvvfProfile profile = VvvfProfile.parse(code);
        if (profile == null) {
            return "音のコードを読めませんでした";
        }
        String name = profile.name();
        if (name.isEmpty()) {
            return "全般タブで名前を付けてから保存してください";
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.name.equals(name)) {
                if (!mayChange(player, e)) {
                    return "「" + name + "」はほかの人が保存しています。名前を変えてください";
                }
                entries.set(i, new Entry(name, profile.code(), e.owner, e.ownerName));
                setDirty();
                return "「" + name + "」を上書きしました";
            }
        }
        if (entries.stream().filter(e -> e.owner.equals(player.getUUID())).count() >= MAX_PER_PLAYER) {
            return "保存できるのは 1 人 " + MAX_PER_PLAYER + " 個までです";
        }
        if (entries.size() >= MAX_TOTAL) {
            return "サーバーの音がいっぱいです (" + MAX_TOTAL + " 個)";
        }
        entries.add(new Entry(name, profile.code(), player.getUUID(), player.getGameProfile().getName()));
        setDirty();
        return "「" + name + "」をサーバーに保存しました";
    }

    /** Deletes the sound called {@code name}. Returns what happened, for the player. */
    String delete(ServerPlayer player, String name) {
        for (Entry e : entries) {
            if (e.name.equals(name)) {
                if (!mayChange(player, e)) {
                    return "削除できるのは、保存した人と OP だけです";
                }
                entries.remove(e);
                setDirty();
                return "「" + name + "」を削除しました";
            }
        }
        return "「" + name + "」はもうありません";
    }

    private static boolean mayChange(ServerPlayer player, Entry e) {
        return e.owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", e.name);
            entry.putString("Code", e.code);
            entry.putUUID("Owner", e.owner);
            entry.putString("OwnerName", e.ownerName);
            list.add(entry);
        }
        tag.put("Sounds", list);
        return tag;
    }

    private static VvvfLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
        VvvfLibrary library = new VvvfLibrary();
        for (Tag t : tag.getList("Sounds", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            library.entries.add(new Entry(entry.getString("Name"), entry.getString("Code"), entry.getUUID("Owner"),
                entry.getString("OwnerName")));
        }
        return library;
    }
}
