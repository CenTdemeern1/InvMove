package me.pieking1215.invmove;

import me.pieking1215.invmove.module.Module;
import me.pieking1215.invmove.module.VanillaModule18;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

public abstract class InvMove18 extends InvMove {

    public InvMove18() {
        super();
    }

    @Override
    public Module getVanillaModule() {
        return new VanillaModule18();
    }

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
}
