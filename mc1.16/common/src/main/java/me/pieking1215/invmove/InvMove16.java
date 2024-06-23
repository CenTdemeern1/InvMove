package me.pieking1215.invmove;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

public abstract class InvMove16 extends InvMove {
    @Override
    public MutableComponent translatableComponent(String key) {
        return new TranslatableComponent(key);
    }

    @Override
    public MutableComponent literalComponent(String text) {
        return new TextComponent(text);
    }

    @Override
    public boolean optionToggleCrouch() {
        return Minecraft.getInstance().options.toggleCrouch;
    }

    @Override
    public void setOptionToggleCrouch(boolean toggleCrouch) {
        Minecraft.getInstance().options.toggleCrouch = toggleCrouch;
    }

    @Override
    protected void drawShadow(Font font, PoseStack poseStack, String string, float x, float y, int col){
        font.draw(poseStack, string, x, y, col);
    }

    @Override
    public ResourceLocation parseResource(String path){
        return new ResourceLocation(path);
    }
}
