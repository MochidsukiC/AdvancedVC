package jp.houlab.mochidsuki.advancedvc.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import jp.houlab.mochidsuki.advancedvc.client.ClientConfig;
import jp.houlab.mochidsuki.advancedvc.client.audio.RayEnvironmentScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * レイ可視化（デバッグ表示）
 */
@Mod.EventBusSubscriber(modid = AdvancedvcMain.MODID, value = Dist.CLIENT)
public class DebugRayRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ClientConfig.get().visualizeRays) return;

        RayEnvironmentScanner.DebugScanData data = RayEnvironmentScanner.getLastDebugScan();
        if (data == null) return;
        // 2秒以上前のデータは破棄
        if (System.currentTimeMillis() - data.timestamp > 2000) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // PoseStackの操作をtry-finallyで保護
        pose.pushPose();
        try {
            pose.translate(-camPos.x, -camPos.y, -camPos.z);

            VertexConsumer vc = buffers.getBuffer(RenderType.lines());

            List<RayEnvironmentScanner.DebugRay> rays = data.rays;
            int max = Math.min(160, rays.size());
            for (int i = 0; i < max; i++) {
                RayEnvironmentScanner.DebugRay r = rays.get(i);
                // ヒットあり: 赤、ヒットなし: 緑
                float cr = r.hit ? 1.0f : 0.0f;
                float cg = r.hit ? 0.0f : 1.0f;
                float cb = 0.0f;
                float a = 0.9f;

                // ライン描画（法線は適当にZ+）
                vc.vertex(pose.last().pose(), (float) r.start.x, (float) r.start.y, (float) r.start.z)
                        .color(cr, cg, cb, a).normal(pose.last().normal(), 0, 0, 1).endVertex();
                vc.vertex(pose.last().pose(), (float) r.end.x, (float) r.end.y, (float) r.end.z)
                        .color(cr, cg, cb, a).normal(pose.last().normal(), 0, 0, 1).endVertex();
            }
        } finally {
            // 必ずpopPoseを実行
            pose.popPose();
        }

        // バッファをフラッシュ（PoseStack操作の後）
        buffers.endBatch(RenderType.lines());
    }
}

