// NetworkHandler.java - ネットワークパケット管理
package com.tungsahur.mod.network;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TungSahurMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * パケットを登録
     */
    public static void registerPackets() {
        TungSahurMod.LOGGER.info("TungSahur ネットワークパケット登録開始");

        // JumpscarePacketを登録
        INSTANCE.messageBuilder(JumpscarePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(JumpscarePacket::decode)
                .encoder(JumpscarePacket::encode)
                .consumerMainThread(JumpscarePacket::handle)
                .add();

        TungSahurMod.LOGGER.info("JumpscarePacket登録完了 (ID: {})", packetId - 1);
        TungSahurMod.LOGGER.info("TungSahur ネットワークパケット登録完了");
    }

    /**
     * 特定のプレイヤーにジャンプスケアパケットを送信
     */
    public static void sendJumpscareToPlayer(ServerPlayer player) {
        try {
            JumpscarePacket packet = new JumpscarePacket(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot()
            );

            TungSahurMod.LOGGER.info("プレイヤー {} にジャンプスケアパケット送信: pos=({},{},{})",
                    player.getName().getString(), player.getX(), player.getY(), player.getZ());

            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);

            TungSahurMod.LOGGER.info("ジャンプスケアパケット送信完了");

        } catch (Exception e) {
            TungSahurMod.LOGGER.error("ジャンプスケアパケット送信エラー: ", e);
            e.printStackTrace();
        }
    }

    /**
     * 範囲内の全プレイヤーにジャンプスケアパケットを送信
     */
    public static void sendJumpscareToPlayersNear(ServerPlayer sourcePlayer, double range) {
        try {
            sourcePlayer.serverLevel().players().forEach(player -> {
                if (player.distanceTo(sourcePlayer) <= range) {
                    sendJumpscareToPlayer(player);
                }
            });
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("範囲内ジャンプスケアパケット送信エラー: ", e);
        }
    }
}