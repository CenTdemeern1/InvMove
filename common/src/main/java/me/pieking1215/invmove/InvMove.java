package me.pieking1215.invmove;

import com.mojang.blaze3d.platform.InputConstants;

//? if >=1.21 {
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
//?} else
/*import com.mojang.blaze3d.vertex.Tesselator;*/

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import me.pieking1215.invmove.module.CVComponent;
import me.pieking1215.invmove.module.Module;
import me.pieking1215.invmove.module.VanillaModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.player./*$ Input {*/ClientInput/*$}*/;
//? if >=1.21.2
import net.minecraft.world.entity.player.Input;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

//? if <1.19
/*import net.minecraft.network.chat.TextComponent;*/
//? if <1.19
/*import net.minecraft.network.chat.TranslatableComponent;*/

import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

public abstract class InvMove {
    public static final String MOD_ID = "invmove";

    private static InvMove instance;

    public static InvMove instance() {
        if (instance == null) {
            instance = new InvMoveNoOp();
        }

        return instance;
    }

    public static void setInstance(InvMove newInstance) {
        instance = newInstance;
    }

    private static final KeyMapping TOGGLE_MOVEMENT_KEY = new KeyMapping(
            "keybind.invmove.toggleMove",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "keycategory.invmove"
    );

    private static final KeyMapping TOGGLE_MOUSE_KEY = new KeyMapping(
            "keybind.invmove.toggleMouse",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "keycategory.invmove"
    );

    private static final List<Module> addonModules = new ArrayList<>();

    /**
     * Use this function to register addon `Module`s.
     * This should be done during `InterModEnqueueEvent` on forge or the "invmove" entrypoint on fabric/quilt
     */
    @SuppressWarnings("unused")
    public static void registerModule(Module module) {
        System.out.println("[InvMove] Registered Module: " + module);
        (instance != null ? instance.modules : addonModules).add(module);
    }

    // loader compatibility layer

    protected abstract Optional<String> modidFromClassInternal(Class<?> c);
    private final HashMap<Class<?>, Optional<String>> modidFromClassCache = new HashMap<>();
    public Optional<String> modidFromClass(Class<?> c) {
        return modidFromClassCache.computeIfAbsent(c, this::modidFromClassInternal);
    }
    public abstract String modNameFromModid(String modid);
    public abstract boolean hasMod(String modid);
    public abstract File configDir();
    protected abstract void registerKeybind(KeyMapping key);

    // utility

    public MutableComponent translatableComponent(String key) {
        //? if >=1.19 {
        return Component.translatable(key);
        //?} else
        /*return new TranslatableComponent(key);*/
    }
    public MutableComponent literalComponent(String text) {
        //? if >=1.19 {
        return Component.literal(text);
         //?} else
        /*return new TextComponent(text);*/
    }
    public MutableComponent fromCV(CVComponent c) {
        if (c.translate) {
            return translatableComponent(c.text);
        } else {
            return literalComponent(c.text);
        }
    }

    public boolean optionToggleCrouch() {
        //? if >=1.19 {
        return Minecraft.getInstance().options.toggleCrouch().get();
        //?} else
        /*return Minecraft.getInstance().options.toggleCrouch;*/
    }

    public void setOptionToggleCrouch(boolean toggleCrouch) {
        //? if >=1.19 {
        Minecraft.getInstance().options.toggleCrouch().set(toggleCrouch);
        //?} else
        /*Minecraft.getInstance().options.toggleCrouch = toggleCrouch;*/
    }

    protected void drawShadow(Font font, PoseStack poseStack, String string, float x, float y, int col){
        //? if >=1.19 {
        //? if >=1.21 {
        var builder = new ByteBufferBuilder(786432);
        //?} else
        /*var builder = Tesselator.getInstance().getBuilder();*/
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(builder);
        font.drawInBatch(string, x, y, col, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        buffer.endBatch();
        //?} else
        /*font.draw(poseStack, string, x, y, col);*/
    }

    public ResourceLocation parseResource(String path){
        //? if >=1.21 {
        return ResourceLocation.parse(path);
        //?} else
        /*return new ResourceLocation(path);*/
    }

    // implementation

    protected boolean wasSneaking = false;
    protected boolean wasMovementDisallowed = false;
    protected boolean wasToggleMovementPressed = false;
    protected boolean wasToggleMousePressed = false;

    protected Map<ToggleKeyMapping, Boolean> wasToggleKeyDown = new HashMap<>();

    protected boolean forceRawKeyDown = false;

    // Read in MouseHandlerMixin
    protected boolean overrideMouseGrabbed = false;
    protected double mouseLockedAtX = 0;
    protected double mouseLockedAtY = 0;
    // Read and written to in MouseHandlerMixin
    public double fakeMousePositionX = 0;
    public double fakeMousePositionY = 0;

    public final List<Module> modules = new ArrayList<>();

    public InvMove() {
        this.modules.addAll(addonModules);
        addonModules.clear();
        this.modules.add(0, this.getVanillaModule());

        this.registerKeybind(TOGGLE_MOVEMENT_KEY);
        this.registerKeybind(TOGGLE_MOUSE_KEY);
    }

    public Module getVanillaModule() {
        return new VanillaModule();
    }

    public void finishInit(){
        InvMoveConfig.load();
    }

    /**
     * Handles updating and applying effects of the toggle movement key.
     * This is more complicated than you might expect because we want to toggle the config value
     *   only if it actually has an effect.
     * So for example, pressing it while in a text field won't do anything
     * @param screen The current screen
     * @param couldMove Whether we could move in this Screen before
     * @return Whether we can move in this Screen now
     */
    private boolean handleToggleMovementKey(Screen screen, boolean couldMove) {
        if (TOGGLE_MOVEMENT_KEY.isUnbound()) return couldMove;

        // .key here is accessWidened
        TOGGLE_MOVEMENT_KEY.setDown(InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), TOGGLE_MOVEMENT_KEY.key.getValue()));
        boolean before = wasToggleMovementPressed;
        wasToggleMovementPressed = TOGGLE_MOVEMENT_KEY.isDown;

        // if button pressed
        if (TOGGLE_MOVEMENT_KEY.isDown && !before) {

            if (screen == null) {
                InvMoveConfig.MOVEMENT.ENABLED.set(!InvMoveConfig.MOVEMENT.ENABLED.get());
                return couldMove;
            }

            // if we could move before
            if (couldMove && InvMoveConfig.MOVEMENT.ENABLED.get()) {
                // toggle movement off
                InvMoveConfig.MOVEMENT.ENABLED.set(false);
                return false;
            }

            // if we couldn't move before
            if (!couldMove && !InvMoveConfig.MOVEMENT.ENABLED.get()) {
                // try turning movement on and see if that makes us able to move
                InvMoveConfig.MOVEMENT.ENABLED.set(true);
                if (allowMovementInScreen(screen)) {
                    // if we are allowed to move now, keep the change
                    return true;
                } else{
                    // if we are still not allowed to move, revert the change
                    InvMoveConfig.MOVEMENT.ENABLED.set(false);
                    return false;
                }
            }
        }

        return couldMove;
    }

    private boolean shouldSneak(InvMoveConfig.Movement.SneakMode sneakMode, boolean shiftIsDown) {
        switch (sneakMode) {
            case Off:
                return false;
            case MaintainWhilePressed:
                if (!shiftIsDown) wasSneaking = false;
                // Fall-through
            case Maintain:
                return wasSneaking;
            case Pressed:
                return shiftIsDown;
        }
        return false;
    }

    public void onInputUpdate(/*$ Input {*/ClientInput/*$}*/ input
            //? if >=1.21.4 {
            //?} else if >=1.19 {
            /*, boolean sneaking, float sneakSpeed
             *///?} else
            /*, boolean sneaking*/
    ){
        if(Minecraft.getInstance().player == null) {
            wasMovementDisallowed = false;
            return;
        }

        // don't continue if the input is a non-vanilla type or if it isn't the local player's input
        // this fixes Freecam/Tweakeroo where while a screen is open the player would also move
        if(input.getClass() != KeyboardInput.class || input != Minecraft.getInstance().player.input) {
            wasMovementDisallowed = false;
            return;
        }

        if(Minecraft.getInstance().screen == null) {
            //? if >=1.21.2 {
            wasSneaking = input.keyPresses.shift();
            //?} else
            /*wasSneaking = input.shiftKeyDown;*/
        }

        boolean canMove = allowMovementInScreen(Minecraft.getInstance().screen);
        canMove = handleToggleMovementKey(Minecraft.getInstance().screen, canMove);
        handleHeadMovement(canMove);

        if(canMove){
            wasMovementDisallowed = false;

            // tick keybinds (since opening the ui unpresses all keys)
            tickKeybinds();

            boolean shiftIsDown = Minecraft.getInstance().options.keyShift.isDown;

//            Minecraft.getInstance().screen.passEvents = true;

            // this is needed for compatibility with ItemPhysic
            Minecraft.getInstance().options.keyDrop.setDown(false);

            if (!optionToggleCrouch()) {
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isPassenger()) {
                    Minecraft.getInstance().options.keyShift.setDown(InvMoveConfig.MOVEMENT.DISMOUNT.get() && Minecraft.getInstance().options.keyShift.isDown);
                } else {
                    //? if >=1.17 {
                    boolean isCreativeFlying = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().flying;
                    //?} else {
                    /*boolean isCreativeFlying = Minecraft.getInstance().player != null && Minecraft.getInstance().player.abilities.flying;
                    *///?}
                    InvMoveConfig.Movement.SneakMode mode = isCreativeFlying
                            ? InvMoveConfig.MOVEMENT.SNEAK_FLYING.get()
                            : InvMoveConfig.MOVEMENT.SNEAK.get();
                    boolean sneakKey = shouldSneak(mode, shiftIsDown);
                    Minecraft.getInstance().options.keyShift.setDown(sneakKey);
                }
            }

            // tick movement

            //? if >=1.21.4 {
            inputTickRaw(input);
            //?} else if >=1.19 {
            /*inputTickRaw(input, sneaking, sneakSpeed);
            *///?} else {
            /*inputTickRaw(input, sneaking);
            *///?}

        }else if(Minecraft.getInstance().screen != null){
            // we are in a screen that we can't move in

            // this used to be KeyMapping.releaseAll() but it caused issues with other mods (ItemSwapper + Amecs)
            if (!wasMovementDisallowed) {
                for (KeyMapping key : KeyMapping.ALL.values()) {
                    if (allowKey(key)) {
                        // .release() is accessWidened
                        key.release();
                    }
                }
            }

            wasMovementDisallowed = true;

            // special handling for sneaking
            if (InvMoveConfig.GENERAL.ENABLED.get() && !optionToggleCrouch()) {
                if (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.isPassenger()) {
                    // need to tick the sneak key in order to do maintain sneak logic
                    // since normally we don't tick keys if movement is disabled
                    tickKeybind(Minecraft.getInstance().options.keyShift);
                    boolean shiftIsDown = Minecraft.getInstance().options.keyShift.isDown;
                    //? if >=1.17 {
                    boolean isCreativeFlying = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().flying;
                    //?} else {
                    /*boolean isCreativeFlying = Minecraft.getInstance().player != null && Minecraft.getInstance().player.abilities.flying;
                    *///?}
                    InvMoveConfig.Movement.SneakMode mode = isCreativeFlying
                            ? InvMoveConfig.Movement.SneakMode.Off
                            : InvMoveConfig.MOVEMENT.SNEAK_DISALLOWED.get();
                    boolean sneakKey = shouldSneak(mode, shiftIsDown);
                    Minecraft.getInstance().options.keyShift.setDown(sneakKey);

                    //? if >=1.21.2 {
                    input.keyPresses = new Input(
                            input.keyPresses.forward(),
                            input.keyPresses.backward(),
                            input.keyPresses.left(),
                            input.keyPresses.right(),
                            input.keyPresses.jump(),
                            sneakKey,
                            input.keyPresses.sprint());
                    //?} else
                    /*input.shiftKeyDown = sneakKey;*/
                }
            }
        } else {
            wasMovementDisallowed = false;
        }
    }

    private void handleHeadMovement(boolean canMove) {
        if (!canMove) {
            this.overrideMouseGrabbed = false;
            this.wasToggleMousePressed = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (TOGGLE_MOUSE_KEY.isUnbound()) return;

        TOGGLE_MOUSE_KEY.setDown(InputConstants.isKeyDown(minecraft.getWindow().getWindow(), TOGGLE_MOUSE_KEY.key.getValue()));

        if (TOGGLE_MOUSE_KEY.isDown && !this.wasToggleMousePressed) {
            if (this.overrideMouseGrabbed) {
                releaseMouse();
            } else {
                grabMouse();
            }
        }

        this.wasToggleMousePressed = TOGGLE_MOUSE_KEY.isDown;
    }

    public boolean getOverrideMouseGrabbed() {
        return this.overrideMouseGrabbed;
    }

    public double getMouseLockedAtX() {
        return this.mouseLockedAtX;
    }
    public double getMouseLockedAtY() {
        return this.mouseLockedAtY;
    }

    private void grabMouse() {
        Window window = Minecraft.getInstance().getWindow();
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        this.mouseLockedAtX = mouseHandler.xpos();
        this.mouseLockedAtY = mouseHandler.ypos();
        int xpos = window.getScreenWidth() / 2;
        int ypos = window.getScreenHeight() / 2;
        this.fakeMousePositionX = xpos;
        this.fakeMousePositionY = ypos;
        InputConstants.grabOrReleaseMouse(window.getWindow(), GLFW.GLFW_CURSOR_DISABLED, xpos, ypos);
        // This might be causing weird jumps in facing angles when toggling
        // Might be specific to macOS? Like the vanilla issue when moving your mouse while closing the inventory
        // It's inconsistent, so I can't really tell definitively, but this seems to be worsening it?
        // Might be a race condition?
//        mouseHandler.setIgnoreFirstMove();
        this.overrideMouseGrabbed = true;
    }

    private void releaseMouse() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        // InputConstants.grabOrReleaseMouse, but in the opposite order (otherwise the position doesn't change due to being locked)
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(window, this.mouseLockedAtX, this.mouseLockedAtY);
        this.overrideMouseGrabbed = false;
    }

    private void tickKeybinds() {
        // edited implementation of KeyMapping.setAll()
        // using normal setAll breaks toggle keys so we have to do it manually
        // TODO: maybe it would be better to modify KeyboardHandler::keyPress to hook key presses instead of doing it this way
        for (KeyMapping k : KeyMapping.ALL.values()) {
            if (!allowKey(k))
                continue;

            tickKeybind(k);
        }
    }

    private void tickKeybind(KeyMapping k) {
        if (k.key.getType() == InputConstants.Type.KEYSYM && k.key.getValue() != InputConstants.UNKNOWN.getValue()) {

            boolean raw = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), k.key.getValue());

            // if is a toggle key in toggle mode
            if (k instanceof ToggleKeyMapping && ((ToggleKeyMapping)k).needsToggle.getAsBoolean()) {
                // special handling for toggle keys

                // manually handle toggling
                if (wasToggleKeyDown.containsKey(k)) {
                    if (!wasToggleKeyDown.get(k) && raw) {
                        // TODO: add a "boolean allowKey(KeyBinding);" method to Module instead of hardcoding only for sneak
                        if (k == Minecraft.getInstance().options.keyShift) {
                            if (InvMoveConfig.MOVEMENT.SNEAK.get() == InvMoveConfig.Movement.SneakMode.Pressed) {
                                k.setDown(true);
                            }
                        } else {
                            k.setDown(true);
                        }
                    }
                }

                wasToggleKeyDown.put((ToggleKeyMapping) k, raw);

            } else {
                // normal setAll behavior
                k.setDown(raw);
            }
        }
    }

    /**
     * Returns `true` if the given key should be handled by the mod
     */
    public boolean allowKey(KeyMapping key) {
        String k = key.getName();

        if (InvMoveConfig.MOVEMENT.allowedKeys.containsKey(k)) {
            return InvMoveConfig.MOVEMENT.allowedKeys.get(k);
        }

        boolean allow = allowKeyDefault(key);
        InvMoveConfig.MOVEMENT.allowedKeys.put(k, allow);
        return allow;
    }

    public boolean allowKeyDefault(KeyMapping key) {
        for (Module module : modules) {
            Optional<Boolean> def = module.allowKeyDefault(key);
            if (def.isPresent()) {
                return def.get();
            }
        }

        return false;
    }

    /**
     * Returns `true` if the local player is allowed to move in this `Screen`, `false` otherwise.
     */
    public boolean allowMovementInScreen(Screen screen) {
        if(screen == null) return false;

        if(Minecraft.getInstance().player == null) return false;

        if(!InvMoveConfig.GENERAL.ENABLED.get()) return false;
        if(!InvMoveConfig.MOVEMENT.ENABLED.get()) return false;

        // checking this way instead of screen.isPauseScreen() makes it work with ItemSwapper
        // (their overlay uses a mixin instead of overriding isPauseScreen)
        if(Minecraft.getInstance().isPaused()) return false;

        Optional<Boolean> movement = Optional.empty();
        modules: for (Module mod : this.modules) {
            Module.Movement res = mod.shouldAllowMovement(screen);
            switch (res) {
                case PASS:
                    break;
                case FORCE_ENABLE:
                    movement = Optional.of(true);
                    break modules;
                case FORCE_DISABLE:
                    movement = Optional.of(false);
                    break modules;
                case SUGGEST_ENABLE:
                    movement = Optional.of(true);
                    break;
                case SUGGEST_DISABLE:
                    movement = Optional.of(false);
                    break;
            }
        }

        if (!movement.isPresent()) {
            Class<? extends Screen> cl = screen.getClass();
            String modid = modidFromClass(cl).orElse("?unknown");
            InvMoveConfig.MOVEMENT.unrecognizedScreensAllowMovement.putIfAbsent(modid, new HashMap<>());
            HashMap<Class<? extends Screen>, Boolean> hm = InvMoveConfig.MOVEMENT.unrecognizedScreensAllowMovement.get(modid);

            if (!hm.containsKey(cl)) {
                hm.put(cl, InvMoveConfig.MOVEMENT.UNRECOGNIZED_SCREEN_DEFAULT.get());
                InvMoveConfig.save();
            }

            return hm.get(cl);
        } else {
            return movement.get();
        }
    }

    public static Field[] getDeclaredFieldsSuper(Class<?> aClass) {
        List<Field> fs = new ArrayList<>();

        do{
            fs.addAll(Arrays.asList(aClass.getDeclaredFields()));
        }while((aClass = aClass.getSuperclass()) != null);

        return fs.toArray(new Field[0]);
    }

    /**
     * Calls ClientInput.tick but forces using raw keybind data
     */
    public void inputTickRaw(/*$ Input {*/ClientInput/*$}*/ input
            //? if >=1.21.4 {
            //?} else if >=1.19 {
            /*, boolean sneaking, float sneakSpeed
             *///?} else
            /*, boolean sneaking*/
    ) {
        forceRawKeyDown = true;
        //? if >=1.21.4 {
        input.tick();
        //?} else if >=1.19 {
        /*input.tick(sneaking, sneakSpeed);
        *///?} else {
        /*input.tick(sneaking);
        *///?}
        forceRawKeyDown = false;
    }

    public boolean shouldForceRawKeyDown() {
        return forceRawKeyDown;
    }

    public <T> T withRawKeyDown(Supplier<T> r) {
        boolean was = forceRawKeyDown;
        forceRawKeyDown = true;
        T v = r.get();
        forceRawKeyDown = was;
        return v;
    }

    /**
     * Returns `true` if this `Screen` should have its background tint hidden, `false` otherwise.
     */
    public boolean shouldDisableScreenBackground(Screen screen) {

        if(Minecraft.getInstance().player == null) return false;

        if(!InvMoveConfig.GENERAL.ENABLED.get()) return false;

        if(!InvMoveConfig.BACKGROUND.BACKGROUND_HIDE.get()) return false;

        if(screen == null) return false;

        if(screen.isPauseScreen()){
            switch (InvMoveConfig.BACKGROUND.HIDE_ON_PAUSE.get()) {
                case Show:
                    return false;
                case AllowHide:
                    break;
                case ShowSP:
                    if (Minecraft.getInstance().hasSingleplayerServer()) {
                        if(Minecraft.getInstance().getSingleplayerServer() != null) {
                            if (!Minecraft.getInstance().getSingleplayerServer().isPublished()) return false;
                        } else {
                            return false;
                        }
                    }
                    break;
            }
        }

        Optional<Boolean> show = Optional.empty();
        modules: for (Module mod : this.modules) {
            Module.Background res = mod.shouldHideBackground(screen);
            switch (res) {
                case PASS:
                    break;
                case FORCE_SHOW:
                    show = Optional.of(true);
                    break modules;
                case FORCE_HIDE:
                    show = Optional.of(false);
                    break modules;
                case SUGGEST_SHOW:
                    show = Optional.of(true);
                    break;
                case SUGGEST_HIDE:
                    show = Optional.of(false);
                    break;
            }
        }

        if (!show.isPresent()) {
            Class<? extends Screen> cl = screen.getClass();
            String modid = modidFromClass(cl).orElse("?unknown");
            InvMoveConfig.BACKGROUND.unrecognizedScreensHideBG.putIfAbsent(modid, new HashMap<>());
            HashMap<Class<? extends Screen>, Boolean> hm = InvMoveConfig.BACKGROUND.unrecognizedScreensHideBG.get(modid);

            if (!hm.containsKey(cl)) {
                hm.put(cl, InvMoveConfig.BACKGROUND.UNRECOGNIZED_SCREEN_DEFAULT.get());
                InvMoveConfig.save();
            }

            return hm.get(cl);
        } else {
            return !show.get();
        }
    }

    /**
     * Draws the class name of the current `Screen` and its superclasses, along with their
     *   modid and their movement and background state.
     */
    public void drawDebugOverlay() {
        if(InvMoveConfig.GENERAL.DEBUG_DISPLAY.get()) {
            Screen screen = Minecraft.getInstance().screen;
            if(screen == null) return;

            int i = 0;
            Class<?> cl = screen.getClass();
            while (cl.getSuperclass() != null) {
                String className = cl.getName();
                if (className.startsWith("net.minecraft.")) {
                    className = className.substring("net.minecraft.".length());
                }

                Optional<String> modid = this.modidFromClass(cl);
                if (modid.isPresent()) {
                    className = "[" + modid.get() + "] " + className;
                }
                if (shouldDisableScreenBackground(screen)) {
                    className = "B" + className;
                }
                if (allowMovementInScreen(screen)) {
                    className = "M" + className;
                }
                drawShadow(Minecraft.getInstance().font, new PoseStack(), className, 4, 4 + 10 * i, 0xffffffff);

                i++;
                cl = cl.getSuperclass();
            }
        }
    }
}
