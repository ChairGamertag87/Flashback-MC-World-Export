package org.scaffoldeditor.worldexport.flashback;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import org.scaffoldeditor.worldexport.ReplayExportMod;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;

import net.minecraft.client.MinecraftClient;

// runs a vcap capture on top of a flashback export job
public final class FlashbackExport {
    private FlashbackExport() {}

    public enum State { CAPTURING, SAVING, DONE, FAILED }

    private static volatile Session session;

    public static final class Session {
        public final ReplayExportSettings settings;
        public final ExportSettings flashbackSettings;
        public final ReplayFrameCapturer capturer;
        public final ExportInfo.Mutable info = new ExportInfo.Mutable();

        private volatile State state = State.CAPTURING;
        private volatile Throwable error;

        private Session(ReplayExportSettings settings, ExportSettings flashbackSettings) {
            this.settings = settings;
            this.flashbackSettings = flashbackSettings;
            this.capturer = new ReplayFrameCapturer(settings);
        }

        public State getState() {
            return state;
        }

        public Throwable getError() {
            return error;
        }
    }

    public static Session getSession() {
        return session;
    }

    public static boolean isBusy() {
        Session s = session;
        return s != null && (s.state == State.CAPTURING || s.state == State.SAVING);
    }

    public static boolean isVcapJob(ExportSettings settings) {
        Session s = session;
        return s != null && s.flashbackSettings == settings;
    }

    // queue the export, flashback picks the job up on the next client tick
    public static void start(ReplayExportSettings settings, ExportSettings flashbackSettings) {
        if (isBusy() || Flashback.EXPORT_JOB != null) {
            throw new IllegalStateException("An export is already running");
        }
        Session s = new Session(settings, flashbackSettings);
        s.info.setTotalFrames((int) Math.ceil(
                (flashbackSettings.endTick() - flashbackSettings.startTick()) / 20d * settings.getFps()));
        session = s;
        Flashback.EXPORT_JOB = new ExportJob(flashbackSettings);
    }

    public static void dismiss() {
        if (!isBusy()) session = null;
    }

    // called once per rendered frame from the export job
    public static void onFrame() {
        Session s = session;
        if (s == null || s.state != State.CAPTURING) return;
        try {
            captureFrame(s);
        } catch (Throwable e) {
            // dont take the whole game down, just stop capturing
            ReplayExportMod.LOGGER.error("Error capturing replay frame", e);
            s.error = e;
            s.state = State.FAILED;
            try {
                s.capturer.close();
            } catch (Exception ignored) {}
        }
    }

    private static void captureFrame(Session s) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!s.capturer.isSetup()) {
            s.info.setPhase(ExportPhase.CAPTURE);
            s.capturer.setup((x, y, index, total) -> {
                s.info.setChunksDone(index + 1);
                s.info.setTotalChunks(total);
            });

            // give player skins a moment to download before they get captured
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        s.capturer.captureFrame(client.getRenderTickCounter().getTickDelta(true));
        s.info.setFramesDone(s.capturer.getFramesDone());
    }

    // called when the export job loop ends, saving happens off thread
    public static void onCaptureFinished() {
        Session s = session;
        if (s == null || s.state != State.CAPTURING) return;
        s.state = State.SAVING;

        if (!s.capturer.isSetup()) {
            s.error = new IllegalStateException("No frames were captured");
            s.state = State.FAILED;
            return;
        }

        s.capturer.initialWorldCapture.thenRunAsync(() -> {
            try {
                s.capturer.save(s.info::setPhase);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, s.capturer.getWorldCaptureService()).whenComplete((v, e) -> {
            try {
                s.capturer.close();
            } catch (IOException ignored) {}

            if (e != null) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                ReplayExportMod.LOGGER.error("Error exporting replay", cause);
                s.error = cause;
                s.state = State.FAILED;
            } else {
                s.info.setPhase(ExportPhase.FINISHED);
                s.state = State.DONE;
            }
        });
    }
}
