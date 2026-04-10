package com.famiglia.mod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Pacchetto Client → Server.
 * Contiene un'azione (stringa breve) e un payload JSON opzionale.
 */
public record FamigliaC2SPayload(String action, String jsonData) implements CustomPayload {

    public static final CustomPayload.Id<FamigliaC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of("famiglia", "c2s"));

    public static final PacketCodec<RegistryByteBuf, FamigliaC2SPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, FamigliaC2SPayload::action,
                    PacketCodecs.STRING, FamigliaC2SPayload::jsonData,
                    FamigliaC2SPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
