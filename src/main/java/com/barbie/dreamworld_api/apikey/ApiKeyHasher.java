package com.barbie.dreamworld_api.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Utilitário para nunca guardar nem logar a chave de API em texto claro. */
public final class ApiKeyHasher {

    private ApiKeyHasher() {}

    public static String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível nesta JVM", e);
        }
    }

    /** Prefixo seguro para logs — nunca devolve a chave completa. */
    public static String mask(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "(vazia)";
        }
        String curto = rawKey.strip();
        return (curto.length() <= 4 ? curto : curto.substring(0, 4)) + "…";
    }
}
