package org.scaffoldeditor.worldexport.mixins.flashback;

import org.scaffoldeditor.worldexport.flashback.BlenderExportWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.moulberry.flashback.editor.ui.ReplayUI;

@Mixin(value = ReplayUI.class, remap = false)
public class ReplayUIMixin {

    @Inject(method = "drawOverlayInternal", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/editor/ui/windows/StartExportWindow;render()V", shift = At.Shift.AFTER))
    private static void worldexport$renderWindows(CallbackInfo ci) {
        BlenderExportWindow.render();
    }
}
