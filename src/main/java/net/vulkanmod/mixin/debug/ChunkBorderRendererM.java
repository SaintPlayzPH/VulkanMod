package net.vulkanmod.mixin.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.class_1297;
import net.minecraft.class_1923;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_757;
import net.minecraft.class_862;
import net.minecraft.class_293.class_5596;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({class_862.class})
public class ChunkBorderRendererM {
   @Shadow
   @Final
   private class_310 field_4516;
   @Shadow
   @Final
   private static int field_35557;
   @Shadow
   @Final
   private static int field_35558;

   @Overwrite
   public void method_23109(class_4587 poseStack, class_4597 multiBufferSource, double d, double e, double f) {
      Matrix4fStack poseStack2 = RenderSystem.getModelViewStack();
      poseStack2.pushMatrix();
      poseStack2.mul(poseStack.method_23760().method_23761());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableDepthTest();
      RenderSystem.setShader(class_757::method_34540);
      class_1297 entity = this.field_4516.field_1773.method_19418().method_19331();
      class_289 tesselator = class_289.method_1348();
      class_287 bufferBuilder = tesselator.method_1349();
      double g = (double)this.field_4516.field_1687.method_31607() - e;
      double h = (double)this.field_4516.field_1687.method_31600() - e;
      class_1923 chunkPos = entity.method_31476();
      double i = (double)chunkPos.method_8326() - d;
      double j = (double)chunkPos.method_8328() - f;
      RenderSystem.lineWidth(1.0F);
      bufferBuilder.method_1328(class_5596.field_27382, class_290.field_1576);
      RenderSystem.disableCull();
      float lw = 0.1F;
      double h0 = (double)((int)e / 16 * 16 + this.field_4516.field_1687.method_31607() % 16) - e;

      for(int k = -16; k <= 16; k += 16) {
         for(int l = -16; l <= 16; l += 16) {
            bufferBuilder.method_22912(i + (double)k, g, j + (double)l).method_22915(1.0F, 0.0F, 0.0F, 0.0F).method_1344();
            bufferBuilder.method_22912(i + (double)k, h, j + (double)l).method_22915(1.0F, 0.0F, 0.0F, 0.5F).method_1344();
            bufferBuilder.method_22912(i + (double)k + (double)lw, g, j + (double)l).method_22915(1.0F, 0.0F, 0.0F, 0.5F).method_1344();
            bufferBuilder.method_22912(i + (double)k + (double)lw, h, j + (double)l).method_22915(1.0F, 0.0F, 0.0F, 0.0F).method_1344();
         }
      }

      for(int i1 = -2; i1 < 3; ++i1) {
         double hr = h0 + (double)(16 * i1);
         bufferBuilder.method_22912(i, hr, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr + (double)lw, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr + (double)lw, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr + (double)lw, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr + (double)lw, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr + (double)lw, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i, hr + (double)lw, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr + (double)lw, j + 16.0D).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
         bufferBuilder.method_22912(i + 16.0D, hr + (double)lw, j).method_22915(0.25F, 0.25F, 1.0F, 1.0F).method_1344();
      }

      RenderSystem.depthMask(false);
      RenderSystem.enableBlend();
      float a = 0.3F;
      bufferBuilder.method_22912(i, h0, j).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0, j).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0 + 16.0D, j).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0 + 16.0D, j).method_22915(0.25F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0 + 16.0D, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0 + 16.0D, j + 16.0D).method_22915(0.25F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0, j).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0 + 16.0D, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i, h0 + 16.0D, j).method_22915(0.25F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0, j).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0 + 16.0D, j + 16.0D).method_22915(0.3F, 0.25F, 0.1F, a).method_1344();
      bufferBuilder.method_22912(i + 16.0D, h0 + 16.0D, j).method_22915(0.25F, 0.25F, 0.1F, a).method_1344();
      tesselator.method_1350();
      RenderSystem.enableCull();
      RenderSystem.lineWidth(2.0F);
      RenderSystem.depthMask(true);
      poseStack2.popMatrix();
      RenderSystem.applyModelViewMatrix();
   }
}
