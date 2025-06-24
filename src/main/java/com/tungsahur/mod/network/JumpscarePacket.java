// JumpscarePacket.java - ジャンプスケア実行パケット（dist分離対応版）
package com.tungsahur.mod.network;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.network.FriendlyByteBuf;
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
     * パケット受信時の処理（クライアントサイド） - static メソッドで dist分離対応
     */
    public static void handle(JumpscarePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        // メインスレッドで実行
        ctx.enqueueWork(() -> {
            try {
                TungSahurMod.LOGGER.info("=== JumpscarePacket受信 ===");
                TungSahurMod.LOGGER.info("プレイヤー位置: ({}, {}, {})", packet.playerX, packet.playerY, packet.playerZ);

                // クライアントサイドの処理を別クラスに分離
                ClientPacketHandler.handleJumpscarePacket(packet.playerX, packet.playerY, packet.playerZ, packet.playerYRot);

            } catch (Exception e) {
                TungSahurMod.LOGGER.error("JumpscarePacket処理中にエラー: ", e);
                e.printStackTrace();
            }
        });

        ctx.setPacketHandled(true);
    }
}