package cn.com.vortexa.script_bot.depin.optimai;

import cn.com.vortexa.captcha.CloudFlareResolver;
import cn.com.vortexa.common.constants.ProxyProtocol;
import cn.com.vortexa.common.entity.ProxyInfo;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class PKCEGenerator {

    // 生成随机的 code_verifier（32 字节，转换为十六进制字符串）
    public static String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32]; // 32 字节随机数据（与 JS 代码一致）
        secureRandom.nextBytes(codeVerifier);

        // 转换为十六进制字符串
        StringBuilder hexString = new StringBuilder();
        for (byte b : codeVerifier) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // 计算 code_challenge（SHA-256 + Base64 URL 编码）
    public static String generateCodeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));

        // Base64 URL 编码（去掉填充 =）
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed)
                .replace("+", "-")
                .replace("/", "_");
    }

    public static void main(String[] args) {
        try {
            // 生成 PKCE 参数
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            // 输出结果
            System.out.println("Code Verifier: " + codeVerifier);
            System.out.println("Code Challenge: " + codeChallenge);

            ProxyInfo proxy = new ProxyInfo();
            proxy.setProxyProtocol(ProxyProtocol.HTTP);
            proxy.setHost("46.203.137.111");
            proxy.setPort(6108);
            proxy.setUsername("hldjmuos");
            proxy.setPassword("545n41b7z20x");

            CloudFlareResolver.cloudFlareResolve(
                    proxy,
                    "https://node.optimai.network/login",
                    "0x4AAAAAAA-NTN9roDHAsPQe",
                    "c03504065d26827ca9e5b2b9a6147ec3"
            ).thenAcceptAsync(jsonObject -> {
                System.out.println(jsonObject);
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
