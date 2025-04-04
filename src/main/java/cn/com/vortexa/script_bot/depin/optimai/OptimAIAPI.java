package cn.com.vortexa.script_bot.depin.optimai;

import cn.com.vortexa.captcha.CloudFlareResolver;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.entity.ProxyInfo;
import com.alibaba.fastjson.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author helei
 * @since 2025/3/24 17:15
 */
public class OptimAIAPI {

    private static final String LOGIN_PAGE_URL = "https://node.optimai.network/login";
    private static final String LOGIN_WEBSITE_KEY = "0x4AAAAAAA-NTN9roDHAsPQe";

    private static final String SIGN_IN_API = "https://api.optimai.network/auth/signin";
    private static final String GET_TOKEN_API = "https://api.optimai.network/auth/token";

    private static final String ACCESS_TOKEN_KEY = "access_token";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";

    private final OptimAIBot optimAIBot;

    public OptimAIAPI(OptimAIBot optimAIBot) {
        this.optimAIBot = optimAIBot;
    }


    public Result registry(AccountContext uniAC, String inviteCode) {


        return null;
    }

    /**
     * 登录
     *
     * @param accountContext accountContext
     * @return Result
     * @throws Exception Exception
     */
    public Result login(AccountContext accountContext) throws Exception {
        if (accountContext.getId() != 1) return Result.fail("");
        ProxyInfo proxy = accountContext.getProxy();
        String simpleInfo = accountContext.getSimpleInfo();

        optimAIBot.logger.info(simpleInfo + " start cf resolve...");
        CompletableFuture<Result> future = CloudFlareResolver.cloudFlareResolve(
                proxy,
                LOGIN_PAGE_URL,
                LOGIN_WEBSITE_KEY,
                optimAIBot.getAutoBotConfig().getConfig(OptimAIBot.TWO_CAPTCHA_API_KEY)
        ).thenApplyAsync(twoCaptchaResult -> {
            try {
                optimAIBot.logger.info(simpleInfo + " cf resolve success");
                String userAgent = twoCaptchaResult.getString("userAgent");
                String token = twoCaptchaResult.getString("token");

                JSONObject body = new JSONObject();
                String codeVerifier = generateCodeVerifier();

                body.put("email", accountContext.getAccountBaseInfo().getEmail());
                body.put("password", accountContext.getParam(OptimAIBot.PASSWORD_KEY));
                body.put("code_challenge_method", "S256");
                body.put("code_challenge", generateCodeChallenge(codeVerifier));
                body.put("turnstile_token", token);

                Map<String, String> signInHeaders = buildSignInHeader(accountContext, userAgent);

                String signInStr = optimAIBot.syncRequest(
                        proxy,
                        SIGN_IN_API,
                        HttpMethod.POST,
                        signInHeaders,
                        null,
                        body,
                        () -> simpleInfo + " start login"
                ).get();

                JSONObject signIn = JSONObject.parseObject(signInStr);
                String authorizationCode = signIn.getString("authorization_code");

                optimAIBot.logger.info(simpleInfo + " code get success");

                JSONObject getTokenBody = new JSONObject();
                getTokenBody.put("code", authorizationCode);
                getTokenBody.put("code_verifier", codeVerifier);
                getTokenBody.put("grant_type", "authorization_code");

                Map<String, String> tokenHeader = buildTokenHeader(accountContext, userAgent);

                String getTokenStr = optimAIBot.syncRequest(
                        proxy,
                        GET_TOKEN_API,
                        HttpMethod.POST,
                        tokenHeader,
                        null,
                        getTokenBody,
                        () -> simpleInfo + " start get token"
                ).get();
                JSONObject tokenResult = JSONObject.parseObject(getTokenStr);

                optimAIBot.logger.info(simpleInfo + " get token success, " + tokenResult);
                accountContext.setParam(ACCESS_TOKEN_KEY, tokenResult.getString(ACCESS_TOKEN_KEY));
                accountContext.setParam(REFRESH_TOKEN_KEY, tokenResult.getString(REFRESH_TOKEN_KEY));
                return Result.ok();
            } catch (Exception e) {
                optimAIBot.logger.error("login error, " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                return Result.fail("");
            }
        });

        return future.get();
    }


    public JSONObject registryWSClient(AccountContext accountContext) throws Exception {
        HashMap<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("cpu_cores", 1);
        deviceInfo.put("memory_gb", 0);
        deviceInfo.put("screen_width_px", 375);
        deviceInfo.put("screen_height_px", 600);
        deviceInfo.put("color_depth", 30);
        deviceInfo.put("scale_factor", 1);
        deviceInfo.put("browser_name", "chrome");
        deviceInfo.put("device_type", "extension");
        deviceInfo.put("language", "zh-CN");
        deviceInfo.put("timezone", "Asia/Shanghai");


        return null;
    }

    public String generateClientId(HashMap<String, Object> deviceInfo) {

        return null;
    }

    @NotNull
    private static Map<String, String> buildSignInHeader(AccountContext accountContext, String userAgent) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("userAgent", userAgent);
        headers.put("server", "cloudflare");
        headers.put("cf-cache-status", "DYNAMIC");
        headers.put("cf-ray", "925e96ab3e43e2e3-HKG");
        headers.put("content-type", "application/json; charset=utf-8");
        return headers;
    }

    @NotNull
    private static Map<String, String> buildTokenHeader(AccountContext accountContext, String userAgent) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("userAgent", userAgent);
        headers.put("server", "cloudflare");
        headers.put("origin", "https://node.optimai.network");
        headers.put("referer", "https://node.optimai.network/");
        headers.put("content-type", "application/json;");
        return headers;
    }


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
}
