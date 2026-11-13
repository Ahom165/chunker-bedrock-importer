package com.zai.chunkerimporter.mixin;

import com.zai.chunkerimporter.gui.BedrockImportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an "Import Bedrock" button to the singleplayer world selection screen.
 *
 * Injected at the tail of {@code createFooterButtons} (which runs before
 * {@code visitWidgets} and {@code repositionElements} in {@link SelectWorldScreen#init()}),
 * so the layout system registers, adds and positions our button exactly like the
 * vanilla ones. It is placed in the header, right-aligned, so it never collides
 * with the centered vanilla footer buttons.
 */
@Mixin(SelectWorldScreen.class)
public class SelectWorldScreenMixin {
	@Shadow
	private HeaderAndFooterLayout layout;

	@Inject(method = "createFooterButtons", at = @At("TAIL"))
	private void chunker_bedrock_importer$addImportButton(CallbackInfo ci) {
		SelectWorldScreen self = (SelectWorldScreen) (Object) this;
		Button importButton = Button.builder(
						Component.translatable("chunker_bedrock_importer.button"),
						button -> Minecraft.getInstance().setScreenAndShow(new BedrockImportScreen(self)))
				.size(110, 20)
				.build();
		this.layout.addToHeader(importButton, settings -> settings.alignHorizontallyRight());
	}
}
