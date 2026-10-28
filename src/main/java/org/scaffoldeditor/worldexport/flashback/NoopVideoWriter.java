package org.scaffoldeditor.worldexport.flashback;

import java.nio.FloatBuffer;

import com.moulberry.flashback.exporting.VideoWriter;

import net.minecraft.client.texture.NativeImage;

// swallows frames, vcap exports only need the game state
public class NoopVideoWriter implements VideoWriter {

    @Override
    public void encode(NativeImage src, FloatBuffer audioBuffer) {}

    @Override
    public void finish() {}
}
