package com.digital.service.aiinterview.impl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 腾讯云 API 3.0 TC3-HMAC-SHA256 签名，用于绕过 SDK 的 setSkipSign 兼容问题，直接 HTTP 调用。
 */
final class TencentTc3Signer {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String HOST_ASR = "asr.tencentcloudapi.com";
    private static final String SERVICE_ASR = "asr";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneOffset.UTC);

    static String authorization(String secretId, String secretKey, long timestamp, String payload) {
        String date = DATE_FMT.format(Instant.ofEpochSecond(timestamp));
        String contentType = "application/json; charset=utf-8";
        String canonicalHeaders = "content-type:" + contentType + "\nhost:" + HOST_ASR + "\n";
        String signedHeaders = "content-type;host";
        String hashedPayload = sha256Hex(payload);
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;
        String credentialScope = date + "/" + SERVICE_ASR + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE_ASR);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

        return ALGORITHM + " Credential=" + secretId + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    static long currentTimestampSeconds() {
        return Instant.now().getEpochSecond();
    }

    static String hostAsr() {
        return HOST_ASR;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TencentTc3Signer() {}
}
