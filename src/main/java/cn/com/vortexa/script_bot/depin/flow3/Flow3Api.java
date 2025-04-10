package cn.com.vortexa.script_bot.depin.flow3;


import cn.com.vortexa.captcha.CloudFlareResolver;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author helei
 * @since 2025-04-10
 */
@Slf4j
public class Flow3Api {

    private static final String REGISTRY_WEB_SITE_KEY = "0x4AAAAAABDpOwOAt5nJkp9b";
    private static final String REGISTRY_WEB_SITE_URL = "https://app.flow3.tech/sign-up";
    private static final String LOGIN_WEB_SITE_URL = "https://app.flow3.tech/login";

    private static final String BASE_URL = "https://api2.flow3.tech/api";

    private final Flow3Bot flow3Bot;

    private final String twoCapKey;

    public Flow3Api(Flow3Bot flow3Bot) {
        this.flow3Bot = flow3Bot;
        this.twoCapKey = (String) flow3Bot.getAutoBotConfig().getCustomConfig().get(MapConfigKey.TWO_CAPTCHA_API_KEY);
    }

    /**
     * 注册
     *
     * @param accountContext accountContext
     * @param inviteCode     inviteCode
     * @return Result
     */
    public Result register(AccountContext accountContext, String inviteCode) {
        if (StrUtil.isEmpty(inviteCode)) {
            flow3Bot.logger.warn(accountContext.getSimpleInfo() + " no invite code");
        }
        if (StrUtil.isNotBlank(accountContext.getParam(MapConfigKey.ACCESS_TOKEN_KEY_V2))) {
            return Result.ok("registry finish");
        }

        flow3Bot.logger.debug(accountContext.getSimpleInfo() + " start registry");

        String password = accountContext.getParam(MapConfigKey.PASSWORD_KEY);
        String email = accountContext.getAccountBaseInfo().getEmail();
        if (StrUtil.isBlank(password) || StrUtil.isBlank(email)) {
            return Result.fail("email or password is empty");
        }

        try {
            return CloudFlareResolver.cloudFlareResolve(
                    accountContext.getProxy(),
                    REGISTRY_WEB_SITE_URL,
                    REGISTRY_WEB_SITE_KEY,
                    twoCapKey
            ).thenApply(twoCaptchaResult -> {
                String userAgent = twoCaptchaResult.getString("userAgent");
                String token = twoCaptchaResult.getString("token");

                JSONObject body = new JSONObject();
                body.put("referralCode", inviteCode);
                body.put("password", password);
                body.put("email", email);
                body.put("captchaToken", token);

                Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
                headers.put("userAgent", userAgent);

                try {
                    String responseStr = flow3Bot.syncRequest(
                            accountContext.getProxy(),
                            BASE_URL + "/user/register",
                            HttpMethod.POST,
                            headers,
                            null,
                            body,
                            () -> accountContext.getSimpleInfo() + " send register request"
                    ).get();
                    JSONObject result = JSONObject.parseObject(responseStr);
                    if (!"success".equals(result.getString("result"))) {
                        throw new RuntimeException("register failed, " + responseStr);
                    } else {
                        JSONObject data = result.getJSONObject("data");
                        String accessToken = data.getString(MapConfigKey.ACCESS_TOKEN_KEY_V2);
                        String refreshToken = data.getString(MapConfigKey.REFRESH_TOKEN_KEY_V2);
                        accountContext.setParam(MapConfigKey.ACCESS_TOKEN_KEY_V2, accessToken);
                        accountContext.setParam(MapConfigKey.REFRESH_TOKEN_KEY_V2, refreshToken);
                    }
                    return Result.ok();
                } catch (InterruptedException | ExecutionException e) {
                    return Result.fail(e.getMessage());
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            return Result.fail(e.getMessage());
        }
    }

    public Result login(AccountContext accountContext) {
        String password = accountContext.getParam(MapConfigKey.PASSWORD_KEY);
        String email = accountContext.getAccountBaseInfo().getEmail();
        if (StrUtil.isBlank(password) || StrUtil.isBlank(email)) {
            return Result.fail("email or password is empty");
        }

        try {
            return CloudFlareResolver.cloudFlareResolve(
                    accountContext.getProxy(),
                    REGISTRY_WEB_SITE_URL,
                    REGISTRY_WEB_SITE_KEY,
                    twoCapKey
            ).thenApply(twoCaptchaResult -> {
                String userAgent = twoCaptchaResult.getString("userAgent");
                String token = twoCaptchaResult.getString("token");

                JSONObject body = new JSONObject();
                body.put("password", password);
                body.put("email", email);
                body.put("captchaToken", token);

                Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
                headers.put("userAgent", userAgent);

                try {
                    String responseStr = flow3Bot.syncRequest(
                            accountContext.getProxy(),
                            BASE_URL + "/user/login",
                            HttpMethod.POST,
                            headers,
                            null,
                            body,
                            () -> accountContext.getSimpleInfo() + " send login request"
                    ).get();

                    JSONObject result = JSONObject.parseObject(responseStr);
                    if (!"success".equals(result.getString("result"))) {
                        throw new RuntimeException("register failed, " + responseStr);
                    } else {
                        JSONObject data = result.getJSONObject("data");
                        String accessToken = data.getString(MapConfigKey.ACCESS_TOKEN_KEY_V2);
                        String refreshToken = data.getString(MapConfigKey.REFRESH_TOKEN_KEY_V2);
                        accountContext.setParam(MapConfigKey.ACCESS_TOKEN_KEY_V2, accessToken);
                        accountContext.setParam(MapConfigKey.REFRESH_TOKEN_KEY_V2, refreshToken);
                    }
                    return Result.ok();
                } catch (InterruptedException | ExecutionException e) {
                    return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            return Result.fail(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    public Result keepAlive(AccountContext accountContext) {
        String token = accountContext.getParam(MapConfigKey.ACCESS_TOKEN_KEY_V2, () -> {
            Result login = login(accountContext);
            if (login.getSuccess()) {
                return accountContext.getParam(MapConfigKey.ACCESS_TOKEN_KEY_V2);
            }
            return null;
        });

        if (StrUtil.isBlank(token)) {
            return Result.fail("access token is empty");
        }

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("authorization", "Bearer " + token);
        headers.put("origin", "https://app.flow3.tech");
        headers.put("referer", "https://app.flow3.tech/");

        try {
            String responseStr = flow3Bot.syncRequest(
                    accountContext.getProxy(),
                    BASE_URL + "/user/get-connection-quality",
                    HttpMethod.GET,
                    headers,
                    null,
                    null,
                    () -> accountContext.getSimpleInfo() + " send keepalive ping"
            ).get();

            return Result.ok(responseStr);
        } catch (InterruptedException | ExecutionException e) {
            return Result.ok(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }
}
