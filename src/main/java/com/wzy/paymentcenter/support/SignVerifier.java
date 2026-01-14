package com.wzy.paymentcenter.support;

import com.wzy.paymentcenter.domain.req.PayNotifyReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
public class SignVerifier {
    public boolean verify(PayNotifyReq req) {
        if (req == null) return false;
        if (isBlank(hmacSecret)) {
            log.error("HMAC secret is empty! please config pay.callback.hmac-secret");
            return false;
        }
        if (isBlank(req.getSign())) {
            log.warn("sign is empty, payNo={}", req.getPayNo());
            return false;
        }

        // 1) 组装待签名字符串（必须与支付端一致）
        String data = buildSignPlain(req);

        // 2) 计算 HMAC
        String expected = hmacSha256Hex(hmacSecret, data);

        // 3) 常规比较（这里用 equalsIgnoreCase，避免大小写问题）
        boolean ok = expected.equalsIgnoreCase(req.getSign());
        if (!ok) {
            log.warn("verify failed, expected={}, actual={}, data={}", expected, req.getSign(), data);
        }
        return ok;
    }

    /**
     * 固定顺序拼接（非常重要）
     */
    public String buildSignPlain(PayNotifyReq req) {
        // 注意：null 统一转成空字符串，避免 NPE
        return "orderNo=" + nvl(req.getOrderNo())
                + "&payNo=" + nvl(req.getPayNo())
                + "&channelTradeNo=" + nvl(req.getChannelTradeNo())
                + "&status=" + nvl(req.getStatus())
                + "&amount=" + Objects.toString(req.getAmount(), "")
                + "&timestamp=" + Objects.toString(req.getTimestamp(), "")
                + "&nonce=" + nvl(req.getNonce());
    }

    /**
     * HmacSHA256 输出 hex 小写
     */
    public String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("hmac sha256 error", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
