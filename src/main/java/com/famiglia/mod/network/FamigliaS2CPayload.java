package com.famiglia.mod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Pacchetto Server → Client.
 * Contiene un'azione (stringa breve) e un payload JSON.
 */
public record FamigliaS2CPayload(String action, String jsonData) implements CustomPayload {

    public static final CustomPayload.Id<FamigliaS2CPayload> ID =
            new CustomPayload.Id<>(Identifier.of("famiglia", "s2c"));

    public static final PacketCodec<RegistryByteBuf, FamigliaS2CPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, FamigliaS2CPayload::action,
                    PacketCodecs.STRING, FamigliaS2CPayload::jsonData,
                    FamigliaS2CPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
