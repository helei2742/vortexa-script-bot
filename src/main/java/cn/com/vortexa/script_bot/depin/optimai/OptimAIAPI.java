package cn.com.vortexa.script_bot.depin.optimai;

import cn.com.vortexa.captcha.CloudFlareResolver;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.entity.ProxyInfo;
import cn.com.vortexa.common.entity.RewordInfo;
import cn.hutool.core.util.StrUtil;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author helei
 * @since 2025/3/24 17:15
 */
public class OptimAIAPI {
    private static final List<String> BROWSER_LIST = List.of("chrome", "firefox", "edge", "opera", "brave");
    private static final String CLIENT_SECRET = "D1A167BD1346DDF2357DA5A2F2F2F";

    private static final String LOGIN_PAGE_URL = "https://node.optimai.network/login";
    private static final String LOGIN_WEBSITE_KEY = "0x4AAAAAAA-NTN9roDHAsPQe";

    private static final String SIGN_IN_API = "https://api.optimai.network/auth/signin";
    private static final String GET_TOKEN_API = "https://api.optimai.network/auth/token";
    private static final String REFRESH_TOKEN_API = "https://api.optimai.network/auth/refresh";
    private static final String BASE_API = "https://api.optimai.network";
    private static final String NODE_REGISTER_API = "https://api.optimai.network/devices/register";
    private static final String REWORD_QUERY_API = "/dashboard/stats";

    public static final String ACCESS_TOKEN_KEY = "access_token";
    public static final String REFRESH_TOKEN_KEY = "refresh_token";
    public static final String BROWSER_KEY = "optimai_browser";
    public static final String TIMEZONE_KEY = "timezone";
    public static final String USER_ID_KEY = "user_id";
    public static final String DEVICE_ID_KEY = "device_id";
    public static final String ONLINE_BODY_KEY = "online_body";

    public static final Random random = new Random();
    private static final Logger log = LoggerFactory.getLogger(OptimAIAPI.class);

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
        if (accountContext.getId() != 1) {
            return Result.fail("");
        }
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
                optimAIBot.logger.error(
                        "login error, " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                return Result.fail("");
            }
        });

        return future.get();
    }


    public Result refreshAccessToken(AccountContext accountContext) {
        String refreshToken = accountContext.getParam(REFRESH_TOKEN_KEY);
        if (StrUtil.isBlank(refreshToken)) {
            try {
                return login(accountContext);
            } catch (Exception e) {
                return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        }

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);

        try {
            String responseStr = optimAIBot.syncRequest(
                    accountContext.getProxy(),
                    REFRESH_TOKEN_API,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> accountContext.getSimpleInfo() + " start refresh token..."
            ).get();

            JSONObject result = JSONObject.parseObject(responseStr);
            accountContext.setParam(ACCESS_TOKEN_KEY, result.getJSONObject("data").getString(ACCESS_TOKEN_KEY));
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    public Result queryUserId(AccountContext accountContext) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        String token = accountContext.getParam(ACCESS_TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            try {
                refreshAccessToken(accountContext);
                token = accountContext.getParam(ACCESS_TOKEN_KEY);
            } catch (Exception e) {
                return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        }

        headers.put("authorization", "Bearer " + token);

        try {
            String responseStr = optimAIBot.syncRequest(
                    accountContext.getProxy(),
                    BASE_API + "/auth/me?platforms=all",
                    HttpMethod.GET,
                    headers,
                    null,
                    null,
                    () -> accountContext.getSimpleInfo() + " start get user id"
            ).get();
            JSONObject result = JSONObject.parseObject(responseStr);
            String userId = result.getJSONObject("user").getString("id");
            accountContext.setParam(USER_ID_KEY, userId);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    public Result queryDeviceId(AccountContext accountContext) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        String token = accountContext.getParam(ACCESS_TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            try {
                refreshAccessToken(accountContext);
                token = accountContext.getParam(ACCESS_TOKEN_KEY);
            } catch (Exception e) {
                return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        }

        headers.put("authorization", "Bearer " + token);

        try {
            String responseStr = optimAIBot.syncRequest(
                    accountContext.getProxy(),
                    BASE_API + "/devices?limit=10&sort_by=last_used_at",
                    HttpMethod.GET,
                    headers,
                    null,
                    null,
                    () -> accountContext.getSimpleInfo() + " start get device id"
            ).get();

            JSONObject result = JSONObject.parseObject(responseStr);
            JSONArray items = result.getJSONArray("items");

            String deviceId = null;
            for (int i = 0; i < items.size(); i++) {
                JSONObject jb = items.getJSONObject(i);
                if (!"telegram".equals(jb.getString("device_type"))) {
                    deviceId = jb.getString("id");
                }
            }

            if (StrUtil.isBlank(deviceId)) {
                Result.fail("please register node first");
            }
            accountContext.setParam(DEVICE_ID_KEY, deviceId);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    public Result keepAlive(AccountContext accountContext) {
        Result result = generateOnlineBody(accountContext);
        if (result.getSuccess()) {
            String data = (String) result.getData();

            String token = accountContext.getParam(ACCESS_TOKEN_KEY);

            Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
            headers.put("authorization", "Bearer " + token);

            JSONObject body = new JSONObject();
            body.put("data", data);

            try {
                String responseStr = optimAIBot.syncRequest(
                        accountContext.getProxy(),
                        BASE_API + "/uptime/online",
                        HttpMethod.POST,
                        headers,
                        null,
                        body,
                        () -> accountContext.getSimpleInfo() + " send keepalive request"
                ).get();

                return Result.ok(responseStr);
            } catch (InterruptedException | ExecutionException e) {
                return Result.fail("keepalive request error, " + result.getErrorMsg());
            }
        } else {
            return Result.fail("online body generate error, " + result.getErrorMsg());
        }
    }

    public Result generateOnlineBody(AccountContext accountContext) {
        String userId = accountContext.getParam(USER_ID_KEY);
        if (StrUtil.isBlank(userId)) {
            Result result = queryUserId(accountContext);
            if (result.getSuccess()) {
                userId = accountContext.getParam(USER_ID_KEY);
            } else {
                return Result.fail("userId request error, " + result.getErrorMsg());
            }
        }

        String deviceId = accountContext.getParam(DEVICE_ID_KEY);
        if (StrUtil.isBlank(deviceId)) {
            Result result = queryDeviceId(accountContext);
            if (result.getSuccess()) {
                deviceId = accountContext.getParam(DEVICE_ID_KEY);
            } else {
                return Result.fail("deviceId request error, " + result.getErrorMsg());

            }
        }
        JSONObject body = new JSONObject();
        body.put("duration", 600000);
        body.put("user_id", userId);
        body.put("device_id", deviceId);
        body.put("device_type", "telegram");
        body.put("timestamp", System.currentTimeMillis());

        return Result.ok(Ur(body.toJSONString()));
    }

    public Result queryReword(AccountContext accountContext) {
        String accessToken = accountContext.getParam(ACCESS_TOKEN_KEY);
        if (accessToken == null) {
            Result result = refreshAccessToken(accountContext);
            if (!result.getSuccess()) {
                return Result.fail("get access token error, " + result.getErrorMsg());
            } else {
                accessToken = accountContext.getParam(ACCESS_TOKEN_KEY);
            }
        }

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("Authorization", "Bearer " + accessToken);

        try {
            String responseStr = optimAIBot.syncRequest(
                    accountContext.getProxy(),
                    REWORD_QUERY_API,
                    HttpMethod.GET,
                    headers,
                    null,
                    null
            ).get();
            RewordInfo rewordInfo = accountContext.getRewordInfo();

            JSONObject result = JSONObject.parseObject(responseStr);
            JSONObject state = result.getJSONObject("data").getJSONObject("stats");
            Double totalRewards = state.getDouble("total_rewards");
            Object totalUptime = state.get("total_uptime");

            rewordInfo.setTotalPoints(totalRewards);
            rewordInfo.setSession(String.valueOf(totalUptime));
            optimAIBot.logger.info(accountContext.getSimpleInfo()
                    + " reword query success, total[%s] uptime[%s]".formatted(totalRewards, totalUptime));
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            optimAIBot.logger.error(
                    "query reword error, " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
            return Result.fail(e.getMessage());
        }
    }


    public String getNetworkTimezone(AccountContext accountContext) throws ExecutionException, InterruptedException {
        String responseStr = optimAIBot.syncRequest(
                accountContext.getProxy(),
                "http://ip-api.com/json/",
                HttpMethod.GET,
                new HashMap<>(),
                null,
                null,
                () -> accountContext.getSimpleInfo() + " get network detail"
        ).get();
        return JSONObject.parseObject(responseStr).getJSONObject("data").getString("timezone");
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

    public static String generateXClientAuthentication(String browser, String timezone) throws NoSuchAlgorithmException, InvalidKeyException {
        JSONObject body = new JSONObject();
        body.put("client_app_id", "TLG_MINI_APP_V1");
        body.put("timestamp", new Date());
        JSONObject deviceInfo = generateDeviceInfo(browser, timezone);
        body.put("device_info", deviceInfo);

        String bodyStr = JSONObject.toJSONString(body);

        // 创建HMAC-SHA256签名
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(CLIENT_SECRET.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signatureBytes = mac.doFinal(bodyStr.getBytes());
        // 计算签名
        String signature = bytesToHex(signatureBytes);
        body.put("signature", signature);
        // 对tokenPayload进行Base64编码
        String base64Token = Base64.getEncoder().encodeToString(JSONObject.toJSONString(body).getBytes());
        // 将Base64字符串进行替换
        base64Token = base64Token.replace("+", "-")
                .replace("/", "_")
                .replaceAll("=+$", "");
        return base64Token;
    }

    private static @NotNull JSONObject generateDeviceInfo(String browser, String timezone) {
        JSONObject deviceInfo = new JSONObject();
        deviceInfo.put("cpu_cores", 1);
        deviceInfo.put("memory_gb", 0);
        deviceInfo.put("screen_width_px", 375);
        deviceInfo.put("screen_height_px", 600);
        deviceInfo.put("color_depth", 30);
        deviceInfo.put("scale_factor", 1);
        deviceInfo.put("browser_name", browser);
        deviceInfo.put("device_type", "extension");
        deviceInfo.put("language", "zh-CN");
        deviceInfo.put("timezone", timezone);
        return deviceInfo;
    }

    // 将字节数组转换为Hex字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // Fibonacci transformation function
    private static int Ts(int e) {
        int t = 0, i = 1;
        for (int s = 0; s < e; s++) {
            int temp = t;
            t = i;
            i = temp + i;
        }
        return t % 20;
    }

    // String transformation function Bs
    private static String Bs(String e) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.length(); i++) {
            sb.append((char) (e.charAt(i) + Ts(i)));
        }
        return sb.toString();
    }

    // XOR transformation function Rs
    private static String Rs(String e) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.length(); i++) {
            sb.append((char) ((e.charAt(i) ^ (i % 256)) & 255));
        }
        return sb.toString();
    }

    // Swap transformation function Ss
    private static String Ss(String e) {
        char[] arr = e.toCharArray();
        for (int i = 0; i < arr.length - 1; i += 2) {
            char temp = arr[i];
            arr[i] = arr[i + 1];
            arr[i + 1] = temp;
        }
        return new String(arr);
    }

    // Final transformation function Ur
    public static String Ur(Object e) {
        return encodeToBase64(Ss(Rs(Bs(JSONObject.toJSONString(e)))));
    }

    // Helper method to encode a string to Base64
    private static String encodeToBase64(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes());
    }


    public static void main(String[] args) {
        JSONObject body = new JSONObject();
        body.put("duration", 600000);
        body.put("user_id", "c17c9b6a-c261-4870-aac6-7a580e330d58");
        body.put("device_id", "0195c17f-661d-70eb-8726-5c56303832b5");
        body.put("device_type", "telegram");
        body.put("timestamp", 1744264244445L);

        String result = Ur(body);
        System.out.println(result);
    }
}
