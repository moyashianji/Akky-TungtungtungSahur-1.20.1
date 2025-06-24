// ClientPacketHandler.java - クライアント専用パケット処理
package com.tungsahur.mod.network;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.overlay.TungSahurJumpscareOverlay;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    /**
     * ジャンプスケアパケットのクライアントサイド処理
     */
    public static void handleJumpscarePacket(double playerX, double playerY, double playerZ, float playerYRot) {
        try {
            TungSahurMod.LOGGER.info("クライアントサイドでジャンプスケア処理開始");

            Minecraft minecraft = Minecraft.getInstance();
            Player clientPlayer = minecraft.player;

            if (clientPlayer != null && clientPlayer.level() != null) {
                // TungSahurエンティティをクライアントで一時的に作成
                TungSahurEntity tungSahur = ModEntities.TUNG_SAHUR.get().create(clientPlayer.level());

                if (tungSahur != null) {
                    // エンティティの位置と向きを設定
                    tungSahur.setPos(playerX, playerY, playerZ);
                    tungSahur.setYRot(playerYRot);
                    tungSahur.setXRot(0.0F);

                    TungSahurMod.LOGGER.info("TungSahurエンティティ作成成功: {}", tungSahur);
                    TungSahurMod.LOGGER.info("エンティティ位置: ({}, {}, {})", tungSahur.getX(), tungSahur.getY(), tungSahur.getZ());

                    // ジャンプスケア開始
                    TungSahurJumpscareOverlay.startJumpscare(tungSahur);

                    TungSahurMod.LOGGER.info("ジャンプスケア開始完了");
                } else {
                    TungSahurMod.LOGGER.error("TungSahurエンティティの作成に失敗");
                }
            } else {
                TungSahurMod.LOGGER.error("クライアントプレイヤーまたはワールドがnull");
            }

        } catch (Exception e) {
            TungSahurMod.LOGGER.error("クライアントサイドジャンプスケア処理中にエラー: ", e);
            e.printStackTrace();
        }
    }
}