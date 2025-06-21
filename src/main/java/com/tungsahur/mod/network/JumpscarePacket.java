// JumpscarePacket.java - ジャンプスケア実行パケット
package com.tungsahur.mod.network;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.overlay.TungSahurJumpscareOverlay;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class JumpscarePacket {

    // パケットで送信するデータ
    private final double playerX;
    private final double playerY;
    private final double playerZ;
    private final float playerYRot;

    public JumpscarePacket(double playerX, double playerY, double playerZ, float playerYRot) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerZ = playerZ;
        this.playerYRot = playerYRot;
    }

    /**
     * パケットをバッファに書き込み
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.playerX);
        buffer.writeDouble(this.playerY);
        buffer.writeDouble(this.playerZ);
        buffer.writeFloat(this.playerYRot);

        TungSahurMod.LOGGER.debug("JumpscarePacket エンコード: pos=({},{},{}), yRot={}",
                playerX, playerY, playerZ, playerYRot);
    }

    /**
     * バッファからパケットを読み込み
     */
    public static JumpscarePacket decode(FriendlyByteBuf buffer) {
        double playerX = buffer.readDouble();
        double playerY = buffer.readDouble();
        double playerZ = buffer.readDouble();
        float playerYRot = buffer.readFloat();

        TungSahurMod.LOGGER.debug("JumpscarePacket デコード: pos=({},{},{}), yRot={}",
                playerX, playerY, playerZ, playerYRot);

        return new JumpscarePacket(playerX, playerY, playerZ, playerYRot);
    }

    /**
     * パケット受信時の処理（クライアントサイド）
     */
    @OnlyIn(Dist.CLIENT)
    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        // メインスレッドで実行
        ctx.enqueueWork(() -> {
            try {
                TungSahurMod.LOGGER.info("=== JumpscarePacket受信 ===");
                TungSahurMod.LOGGER.info("プレイヤー位置: ({}, {}, {})", playerX, playerY, playerZ);

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
                TungSahurMod.LOGGER.error("JumpscarePacket処理中にエラー: ", e);
                e.printStackTrace();
            }
        });

        ctx.setPacketHandled(true);
    }
}