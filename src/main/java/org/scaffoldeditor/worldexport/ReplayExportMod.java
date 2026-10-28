package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModels;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshotManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;

public class ReplayExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static ReplayExportMod instance;

    public static ReplayExportMod getInstance() {
        return instance;
    }

    private Set<ClientBlockPlaceCallback> blockUpdateListeners = new HashSet<>();

    private final Version modVersion = FabricLoader.getInstance().getModContainer("worldexport").get().getMetadata().getVersion();
    
    private WorldSnapshotManager worldSnapshotManager;
    
    public Version getModVersion() {
        return modVersion;
    }

    public void onBlockUpdated(ClientBlockPlaceCallback listener) {
        blockUpdateListeners.add(listener);
    }

    public boolean removeOnBlockUpdated(ClientBlockPlaceCallback listener) {
        return blockUpdateListeners.remove(listener);
    }

    public WorldSnapshotManager getWorldSnapshotManager() {
        return worldSnapshotManager;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        ClientBlockPlaceCallback.EVENT.register((pos, oldState, state, world) -> {
            blockUpdateListeners.forEach(listener -> listener.place(pos, oldState, state, world));
        });

        worldSnapshotManager = new WorldSnapshotManager();

        ReplayModels.registerDefaults();
    }

}
