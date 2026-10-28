package org.scaffoldeditor.worldexport.mixins.flashback;

import org.scaffoldeditor.worldexport.flashback.BlenderExportWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.moulberry.flashback.editor.ui.windows.MainMenuBar;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resource.language.I18n;

@Mixin(value = MainMenuBar.class, remap = false)
public class MainMenuBarMixin {

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void worldexport$addMenuItem(CallbackInfo ci) {
        if (ImGui.menuItem(I18n.translate("worldexport.gui.export.menu") + "##WorldExport")) {
            BlenderExportWindow.open();
        }
    }
}
