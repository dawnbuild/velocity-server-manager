package de.gunnablescum.velocityservermanager.utils;

public enum ServerFlag {
    EMPTY((byte) 0, "<red>No Flags</red>"),
    LOBBY((byte) 1, "<yellow>Lobby</yellow>"),
    RESTRICTED((byte) 2, "<yellow>Restricted</yellow>"),
    DISABLED((byte) 4, "<yellow>Disabled</yellow>"),
    LIMBO((byte) 5,"<yellow>Limbo Server</yellow>"),
    PROXY_MANAGED((byte) 9, "<yellow>Proxy Managed</yellow>"),// Yes 9 because 8 (proxy_managed) + 1 (forced lobby);
    PROXY_MANAGED_LIMBO((byte)13, "<yellow>Proxy Managed Limbo</yellow>"),;

    public final byte bit;
    private final String name;

    ServerFlag(byte bit, String name) {
        this.bit = bit;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static ServerFlag valueOf(byte bit) {
        return switch (bit) {
            case 0 -> EMPTY;
            case 1 -> LOBBY;
            case 2 -> RESTRICTED;
            case 4 -> DISABLED;
            case 5 -> LIMBO;
            case 9 -> PROXY_MANAGED;
            case 13-> PROXY_MANAGED_LIMBO;
            default -> throw new IllegalArgumentException("No enum constant for bit value: " + bit);
        };
    }

    public static String miniMessageFormatted(int bit) {
        if(bit == ServerFlag.EMPTY.bit) return ServerFlag.EMPTY.toString();
        StringBuilder sb = new StringBuilder();
        for(ServerFlag flag : values()) {
            if(flag == EMPTY) continue;
            if ((bit & flag.bit) != flag.bit) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(flag);
        }
        return sb.toString();
    }
}
