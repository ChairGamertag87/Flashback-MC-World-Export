package org.scaffoldeditor.worldexport.flashback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.joml.Vector3i;
import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.scaffoldeditor.worldexport.ReplayExportMod;
import org.scaffoldeditor.worldexport.replay.ReplayEntity;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter.ModelNotFoundException;
import org.scaffoldeditor.worldexport.vcap.IFrame;
import org.scaffoldeditor.worldexport.vcap.BlockExporter.CaptureCallback;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ReplayFrameCapturer {

    protected int fps = 20;

    protected int framesDone;
    protected ReplayExportSettings settings;
    protected ReplayFile exporter;

    protected Map<BlockPos, BlockState> blockUpdateCache = new HashMap<>();
    protected Map<Entity, ReplayEntity<?>> entityCache = new HashMap<>();
    protected Set<Entity> skippedEnts = new HashSet<>();

    protected CompletableFuture<IFrame> initialWorldCapture;

    private MinecraftClient client = MinecraftClient.getInstance();

    private ExecutorService worldCaptureService;

    public ReplayFrameCapturer(ReplayExportSettings settings) {
        this.settings = settings;
        this.fps = settings.getFps();
    }

    protected ClientBlockPlaceCallback blockUpdateListener = new ClientBlockPlaceCallback() {

        @Override
        public void place(BlockPos pos, @Nullable BlockState oldState, BlockState state, World world) {
            blockUpdateCache.put(pos, state);
        }
        
    };

    public ReplayFile getExporter() {
        return exporter;
    }

    public boolean isSetup() {
        return exporter != null;
    }

    public int getFramesDone() {
        return framesDone;
    }

    /**
     * Setup the exporter and capture the initial world.
     * @param callback A callback to use for the initial world capture.
     * @return A future that completes once the initial world capture has finished.
     */
    public CompletableFuture<IFrame> setup(@Nullable CaptureCallback callback) {
        if (worldCaptureService == null || worldCaptureService.isShutdown()) {
            worldCaptureService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Replay Exporter");
                // thread.setPriority(Thread.MAX_PRIORITY);
                return thread;
            });
        }

        if (exporter == null) {
            // int viewDistance = client.options.viewDistance;
            // int viewDistance = settings.getViewDistance();
            // int viewDistanceBlocks = viewDistance * 16;
            // // ChunkPos centerPos = client.getCameraEntity().getChunkPos();
            // // exporter = new ReplayFile(client.world, 
            // //         new ChunkPos(centerPos.x - viewDistance, centerPos.z - viewDistance),
            // //         new ChunkPos(centerPos.x + viewDistance, centerPos.z + viewDistance));
            // BlockPos centerPos = client.getCameraEntity().getBlockPos();
            // exporter = new ReplayFile(client.world, new BlockBox(
            //     centerPos.getX() - viewDistanceBlocks, Integer.MIN_VALUE, centerPos.getZ() - viewDistanceBlocks, 
            //     centerPos.getX() + viewDistanceBlocks, Integer.MAX_VALUE, centerPos.getZ() + viewDistanceBlocks));

            exporter = new ReplayFile(client.world, settings.getBounds());

            BlockPos centerBlock = client.getCameraEntity().getBlockPos();
            exporter.meta.offset = new Vector3i(-centerBlock.getX(), -centerBlock.getY(), -centerBlock.getZ());
        }
        
        exporter.setFps(fps);
        exporter.getWorldExporter()
                .getSettings()
                .setFluidMode(settings.getFluidMode());

        initialWorldCapture = exporter.getWorldExporter().captureIFrameAsync(0, Util.getMainWorkerExecutor(), callback);
        ReplayExportMod.getInstance().onBlockUpdated(blockUpdateListener);
        return initialWorldCapture;
    }

    /**
     * Capture a frame of the replay.
     */
    public void captureFrame(float tickDelta) {
        if (exporter == null) {
            throw new IllegalStateException("Frame capture has not been setup!");
        }

        double time = framesDone / (double) fps;
        if (!blockUpdateCache.isEmpty()) {
            exporter.getWorldExporter().capturePFrame(time, blockUpdateCache.keySet(), client.world);
            blockUpdateCache.clear();
        }
        client.world.getEntities().forEach(ent -> captureEntity(ent, tickDelta));
        framesDone++;
    }

    protected void captureEntity(Entity ent, float tickDelta) {
        // the local player is flashback's free camera, not a recorded entity
        if (ent == client.player || skippedEnts.contains(ent)) {
            return;
        }

        ReplayEntity<?> rEnt = entityCache.get(ent);
        if (rEnt == null) {
            rEnt = new ReplayEntity<>(ent, exporter);
            try {
                rEnt.genAdapter();
            } catch (ModelNotFoundException e) {
                LogManager.getLogger().error("Entity " + ent.getName() + " (" + EntityType.getId(ent.getType())
                        + ") does not have a model adapter. Will be missing from export!");
                skippedEnts.add(ent);
                return;
            }
            
            rEnt.setStartTime(framesDone / (float) fps);
            exporter.entities.add(rEnt);
            entityCache.put(ent, rEnt);
        }

        rEnt.capture(tickDelta);
    }

    public ExecutorService getWorldCaptureService() {
        return worldCaptureService;
    }

    /**
     * Close the resources associated with this capturer.
     */
    public void close() throws IOException {
        ReplayExportMod.getInstance().removeOnBlockUpdated(blockUpdateListener);
        if (worldCaptureService != null) {
            worldCaptureService.shutdown();
            worldCaptureService = null;
        }
    }

    /**
     * Save this replay out to file.
     */
    public void save(@Nullable Consumer<String> phaseConsumer) throws IOException {
        File output = settings.getOutputFile();
        Path folder = output.getParentFile().toPath();
        File target = folder.resolve(FilenameUtils.getBaseName(output.getName())+".replay").normalize().toFile();

        LogManager.getLogger().info("Saving replay file to "+target);

        // Wait for initial world capture to finish.
        initialWorldCapture.join();
        
        FileOutputStream out = new FileOutputStream(target);
        exporter.save(out, phaseConsumer);
        out.close();
    }
    
    public void save() throws IOException {
        save(null);
    }
    
}
