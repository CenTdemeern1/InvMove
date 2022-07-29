package me.pieking1215.invmove.forge;

import me.pieking1215.invmove.InvMove;
import me.pieking1215.invmove.InvMove19;
import me.pieking1215.invmove.InvMoveConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.security.CodeSource;
import java.util.Optional;

public class InvMoveForgeClient {

    static void finishInit() {
        InvMove.instance().finishInit();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGUIDrawPost(ScreenEvent.Render.Post event){
        InvMove.instance().drawDebugOverlay();
    }

    static void clientSetup(final FMLClientSetupEvent event) {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> "", (a, b) -> true));
        MinecraftForge.EVENT_BUS.register(new InvMoveForgeClient());

        InvMove.setInstance(new InvMove19() {
            @Override
            public Optional<String> modidFromClass(Class<?> c) {
                if (c.getPackage().getName().startsWith("net.minecraft.")) {
                    return Optional.of("minecraft");
                }

                return ModList.get().applyForEachModContainer(mod -> {
                    CodeSource src1 = c.getProtectionDomain().getCodeSource();
                    CodeSource src2 = mod.getMod().getClass().getProtectionDomain().getCodeSource();
                    boolean eq = src1 != null && src2 != null && src1.getLocation().equals(src2.getLocation());

                    if (eq) {
                        return Optional.of(mod.getModId());
                    }
                    return Optional.<String>empty();
                }).filter(Optional::isPresent).map(Optional::get).findFirst();
            }

            @Override
            public String modNameFromModid(String modid) {
                return ModList.get().getModContainerById(modid).map(con -> con.getModInfo().getDisplayName()).orElse(modid);
            }

            @Override
            public File configDir() {
                return FMLPaths.CONFIGDIR.get().toFile();
            }

            @Override
            protected void registerKeybind(KeyMapping key) {
                key.setKeyConflictContext(KeyConflictContext.UNIVERSAL);
                // TODO: you're supposed to use RegisterKeyMappingsEvent now but it would probably require
                Minecraft.getInstance().options.keyMappings = ArrayUtils.add(Minecraft.getInstance().options.keyMappings, key);
            }
        });

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> InvMoveConfig.setupCloth(screen)));
    }
}
