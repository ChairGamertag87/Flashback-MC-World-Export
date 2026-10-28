package org.scaffoldeditor.worldexport.flashback;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;

// imgui window for exporting the current flashback replay to a .replay file
public final class BlenderExportWindow {
    private BlenderExportWindow() {}

    // flashback renders a preview while capturing, keep it small so its cheap
    private static final int PREVIEW_WIDTH = 960;
    private static final int PREVIEW_HEIGHT = 540;

    private static boolean open;
    private static boolean close;

    private static final int[] radius = {4};
    private static final int[] lowerSection = {0};
    private static final int[] upperSection = {0};
    private static final int[] startTick = {0};
    private static final int[] endTick = {0};
    private static final int[] fps = {20};
    private static final int[] fluidMode = {FluidMode.DYNAMIC.ordinal()};
    private static final ImString outputPath = ImGuiHelper.createResizableImString("");

    // file dialogs complete off thread
    private static volatile String pickedPath;
    private static String errorMessage;

    private static final String[] FLUID_MODE_NAMES;

    static {
        FluidMode[] modes = FluidMode.values();
        FLUID_MODE_NAMES = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            FLUID_MODE_NAMES[i] = modes[i].toString();
        }
    }

    public static void open() {
        open = true;
    }

    public static void render() {
        renderProgress();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        EditorState editorState = EditorStateManager.getCurrent();
        if (world == null || editorState == null) return;

        if (open) {
            open = false;
            close = false;
            errorMessage = null;
            initDefaults(world, editorState);
            ImGui.openPopup("###WorldExport");
        }

        String picked = pickedPath;
        if (picked != null) {
            pickedPath = null;
            outputPath.set(picked);
        }

        if (!ImGuiHelper.beginPopupModalCloseable(I18n.translate("worldexport.gui.export.title") + "###WorldExport", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        if (close) {
            close = false;
            ImGui.closeCurrentPopup();
            ImGuiHelper.endPopupModalCloseable();
            return;
        }

        int minSection = world.getBottomSectionCoord();
        int maxSection = world.getTopSectionCoord();

        ImGui.text(I18n.translate("worldexport.gui.export.bounds"));
        ImGui.sliderInt(I18n.translate("worldexport.gui.export.radius_label"), radius, 1, 32);
        ImGui.sliderInt(I18n.translate("worldexport.gui.export.lower_label"), lowerSection, minSection, maxSection);
        ImGui.sliderInt(I18n.translate("worldexport.gui.export.upper_label"), upperSection, minSection, maxSection);
        if (upperSection[0] < lowerSection[0]) upperSection[0] = lowerSection[0];

        int width = radius[0] * 2 + 1;
        ImGui.textDisabled(I18n.translate("worldexport.gui.export.bounds_info", width, width,
                lowerSection[0] * 16, upperSection[0] * 16 + 15));

        ImGui.separator();

        ImGuiHelper.inputInt(I18n.translate("worldexport.gui.export.start_tick"), startTick);
        ImGuiHelper.inputInt(I18n.translate("worldexport.gui.export.end_tick"), endTick);
        ImGui.sliderInt(I18n.translate("worldexport.gui.export.fps"), fps, 1, 60);
        ImGuiHelper.combo(I18n.translate("worldexport.gui.export.fluid_mode"), fluidMode, FLUID_MODE_NAMES);

        ImGui.separator();

        ImGui.inputText(I18n.translate("worldexport.gui.export.output_file"), outputPath);
        ImGui.sameLine();
        if (ImGui.button(I18n.translate("worldexport.gui.export.browse"))) {
            browse();
        }

        if (errorMessage != null) {
            ImGui.textColored(0xFF5555FF, errorMessage);
        }

        ImGui.separator();

        boolean fullscreen = client.getWindow().isFullscreen();
        if (fullscreen) {
            ImGui.textWrapped(I18n.translate("flashback.export_disable_fullscreen"));
            ImGui.beginDisabled();
        }
        if (ImGui.button(I18n.translate("worldexport.gui.export")) && !fullscreen) {
            errorMessage = startExport(client, world, editorState);
            if (errorMessage == null) {
                close = true;
            }
        }
        if (fullscreen) ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(I18n.translate("gui.cancel"))) {
            close = true;
        }

        ImGuiHelper.endPopupModalCloseable();
    }

    private static void initDefaults(ClientWorld world, EditorState editorState) {
        lowerSection[0] = Math.max(lowerSection[0], world.getBottomSectionCoord());
        if (upperSection[0] <= lowerSection[0] || upperSection[0] > world.getTopSectionCoord()) {
            lowerSection[0] = world.getBottomSectionCoord();
            upperSection[0] = world.getTopSectionCoord();
        }

        // same defaults flashback uses for its own export window
        var range = editorState.getFirstAndLastTicksInTracks();
        int start = Math.max(0, range.start());
        int end = range.end();
        ReplayServer replayServer = Flashback.getReplayServer();
        int total = replayServer != null ? replayServer.getTotalReplayTicks() : start + 100;
        if (end < 0 || end <= start) {
            end = total;
        }
        startTick[0] = Math.min(start, total);
        endTick[0] = Math.min(end, total);

        if (ImGuiHelper.getString(outputPath).isBlank()) {
            outputPath.set(defaultFolder().resolve(generateFileName()).toString());
        }
    }

    private static Path defaultFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("exports");
    }

    private static String generateFileName() {
        return new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".replay";
    }

    private static void browse() {
        File current = new File(ImGuiHelper.getString(outputPath));
        File folder = current.getParentFile() != null ? current.getParentFile() : defaultFolder().toFile();
        folder.mkdirs();

        AsyncFileDialogs.saveFileDialog(folder.getAbsolutePath(), current.getName(), "Replay File", "replay")
                .thenAccept(path -> {
                    if (path != null) pickedPath = path;
                });
    }

    // returns an error message, or null if the export started
    private static String startExport(MinecraftClient client, ClientWorld world, EditorState editorState) {
        if (FlashbackExport.isBusy() || Flashback.EXPORT_JOB != null) {
            return I18n.translate("worldexport.gui.export.busy");
        }

        ClientPlayerEntity player = client.player;
        if (player == null) return I18n.translate("worldexport.gui.export.no_player");

        String pathStr = ImGuiHelper.getString(outputPath).trim();
        if (pathStr.isEmpty()) {
            pathStr = defaultFolder().resolve(generateFileName()).toString();
        }
        if (!pathStr.endsWith(".replay")) {
            pathStr += ".replay";
        }
        File output = new File(pathStr).getAbsoluteFile();
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return I18n.translate("worldexport.gui.export.bad_folder");
        }

        int start = Math.max(0, startTick[0]);
        int end = Math.max(start, endTick[0]);
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            int total = replayServer.getTotalReplayTicks();
            start = Math.min(start, total);
            end = Math.min(end, total);
        }
        if (end <= start) {
            return I18n.translate("worldexport.gui.export.bad_range");
        }

        ChunkPos center = player.getChunkPos();
        int r = radius[0];
        BlockBox bounds = new BlockBox(
                center.x - r, lowerSection[0], center.z - r,
                center.x + r, upperSection[0], center.z + r);

        ReplayExportSettings settings = new ReplayExportSettings()
                .setBounds(bounds)
                .setFluidMode(FluidMode.values()[fluidMode[0]])
                .setFps(fps[0])
                .setOutputFile(output);

        // only the capture fields matter, the video side is swallowed by NoopVideoWriter
        ExportSettings flashbackSettings = new ExportSettings(output.getName(), editorState.copy(),
                player.getPos(), player.getYaw(), player.getPitch(),
                PREVIEW_WIDTH, PREVIEW_HEIGHT, start, end, fps[0], false,
                VideoContainer.PNG_SEQUENCE, null, null, 0, false, false, true,
                false, false, null,
                output.toPath(), null);

        FlashbackExport.start(settings, flashbackSettings);
        outputPath.set(new File(output.getParentFile(), generateFileName()).toString());
        return null;
    }

    // small status window while the file is being written
    private static void renderProgress() {
        FlashbackExport.Session session = FlashbackExport.getSession();
        if (session == null || session.getState() == FlashbackExport.State.CAPTURING) return;

        if (ImGui.begin(I18n.translate("worldexport.gui.exporting.title") + "###WorldExportProgress", ImGuiWindowFlags.AlwaysAutoResize)) {
            ExportInfo info = session.info;
            ImGui.text(I18n.translate(info.getPhase()));
            ImGui.text(I18n.translate("worldexport.gui.exporting.frame_progress", info.getFramesDone(), info.getTotalFrames()));
            ImGui.text(I18n.translate("worldexport.gui.exporting.world_progress", info.getChunksDone(), info.getTotalChunks()));

            switch (session.getState()) {
                case SAVING -> {
                    if (info.getTotalChunks() > 0) {
                        ImGui.progressBar(info.getChunksDone() / (float) info.getTotalChunks());
                    }
                }
                case DONE -> {
                    ImGui.text(session.settings.getOutputFile().getAbsolutePath());
                    if (ImGui.button(I18n.translate("gui.ok"))) {
                        FlashbackExport.dismiss();
                    }
                }
                case FAILED -> {
                    Throwable error = session.getError();
                    ImGui.textColored(0xFF5555FF, I18n.translate("worldexport.gui.exporting.failed"));
                    if (error != null) {
                        ImGui.textWrapped(String.valueOf(error));
                    }
                    if (ImGui.button(I18n.translate("gui.ok"))) {
                        FlashbackExport.dismiss();
                    }
                }
                default -> {}
            }
        }
        ImGui.end();
    }
}
