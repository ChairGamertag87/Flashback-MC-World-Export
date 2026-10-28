package org.scaffoldeditor.worldexport.flashback;

import java.io.File;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import net.minecraft.util.math.BlockBox;

public final class ReplayExportSettings {

    private BlockBox bounds;

    private FluidMode fluidMode = FluidMode.DYNAMIC;

    private File outputFile = new File("output.replay");

    private int fps = 20;

    public BlockBox getBounds() {
        return bounds;
    }

    public ReplayExportSettings setBounds(BlockBox bounds) {
        this.bounds = bounds;
        return this;
    }

    public FluidMode getFluidMode() {
        return fluidMode;
    }

    public ReplayExportSettings setFluidMode(FluidMode fluidMode) {
        this.fluidMode = fluidMode;
        return this;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public ReplayExportSettings setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    public int getFps() {
        return fps;
    }

    public ReplayExportSettings setFps(int fps) {
        this.fps = fps;
        return this;
    }
}
