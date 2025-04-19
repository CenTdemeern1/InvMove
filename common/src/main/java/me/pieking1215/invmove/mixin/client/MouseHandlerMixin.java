package me.pieking1215.invmove.mixin.client;

import me.pieking1215.invmove.InvMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
    public void isMouseGrabbedMixin(CallbackInfoReturnable<Boolean> cir) {
        if (InvMove.instance().getOverrideMouseGrabbed()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    public void onMoveHEAD(long l, double d, double e, CallbackInfo ci) {
        InvMove invMove = InvMove.instance();
        if (invMove.getOverrideMouseGrabbed()) {
            this.xpos = invMove.fakeMousePosition.x;
            this.ypos = invMove.fakeMousePosition.y;
        }
    }

    @Inject(method = "onMove", at = @At("TAIL"))
    public void onMoveTail(long l, double d, double e, CallbackInfo ci) {
        InvMove invMove = InvMove.instance();
        if (invMove.getOverrideMouseGrabbed()) {
            Vector2d mouseLockedAt = invMove.getMouseLockedAt();
            invMove.fakeMousePosition.x = this.xpos;
            invMove.fakeMousePosition.y = this.ypos;
            this.xpos = mouseLockedAt.x;
            this.ypos = mouseLockedAt.y;
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    public void onPressMixin(long window, int i, int j, int k, CallbackInfo ci) {
        InvMove invMove = InvMove.instance();
        if (invMove.getOverrideMouseGrabbed() && window == this.minecraft.getWindow().getWindow()) {
            // Prevents the game from thinking you're AFK
            this.minecraft.getFramerateLimitTracker().onInputReceived();

            ci.cancel();
        }
    }
}
