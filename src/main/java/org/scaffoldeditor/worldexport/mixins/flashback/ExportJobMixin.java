package org.scaffoldeditor.worldexport.mixins.flashback;

import org.scaffoldeditor.worldexport.flashback.FlashbackExport;
import org.scaffoldeditor.worldexport.flashback.NoopVideoWriter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.exporting.VideoWriter;

@Mixin(value = ExportJob.class, remap = false)
public class ExportJobMixin {

    @Shadow @Final
    private ExportSettings settings;

    // vcap exports dont need a video file
    @Inject(method = "createVideoWriter", at = @At("HEAD"), cancellable = true)
    private static void worldexport$skipVideoWriter(ExportSettings settings, String tempFileName, CallbackInfoReturnable<VideoWriter> cir) {
        if (FlashbackExport.isVcapJob(settings)) {
            cir.setReturnValue(new NoopVideoWriter());
        }
    }

    // runs right after the frame is rendered with keyframes applied
    @Inject(method = "doExport", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;startDownload"))
    private void worldexport$captureFrame(CallbackInfo ci) {
        if (FlashbackExport.isVcapJob(settings)) {
            FlashbackExport.onFrame();
        }
    }

    @Inject(method = "doExport", at = @At("RETURN"))
    private void worldexport$finishCapture(CallbackInfo ci) {
        if (FlashbackExport.isVcapJob(settings)) {
            FlashbackExport.onCaptureFinished();
        }
    }

    // keep the video "export done" popup out of it
    @WrapWithCondition(method = "run", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/editor/ui/windows/ExportDoneWindow;addFinishedExportEntry"))
    private boolean worldexport$skipDoneEntry(ExportDoneWindow.FinishedExportEntry entry) {
        return !FlashbackExport.isVcapJob(settings);
    }
}
