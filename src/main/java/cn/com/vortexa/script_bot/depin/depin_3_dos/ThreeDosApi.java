package cn.com.vortexa.script_bot.depin.depin_3_dos;

import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountBaseInfo;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.entity.RewordInfo;
import cn.com.vortexa.common.exception.LoginException;
import cn.com.vortexa.common.exception.RegisterException;
import cn.com.vortexa.mail.constants.MailProtocolType;
import cn.com.vortexa.mail.factory.MailReaderFactory;
import cn.com.vortexa.mail.reader.MailReader;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.mail.Message;
import javax.mail.internet.MimeMultipart;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ThreeDosApi {

    private static final String REGISTER_API = "https://api.dashboard.3dos.io/api/auth/register";

    private static final String LOGIN_API = "https://api.dashboard.3dos.io/api/auth/login";

    private static final String KEEP_ALIVE_API = "https://m8k9mykqqj.us-east-1.awsapprunner.com/api/harvest-data";

    private static final String REWORD_QUERY_API = "https://api.dashboard.3dos.io/api/profile/me";

    private static final String RESEND_EMAIL_API = "https://api.dashboard.3dos.io/api/email/resend";

    private static final String GENERATE_KEY_API = "https://api.dashboard.3dos.io/api/profile/generate-api-key";

    private static final String DAILY_CHECK_IN_API = "https://api.dashboard.3dos.io/api/claim-reward";

    public static final String HARVESTED_DATA_KEY = "harvested_data";

    public static final String HARVESTED_URL_KEY = "harvested_url";

    private static final String PASSWORD_KEY = "password";

    private static final String SECRET_KEY = "api_secret";

    private static final String TOKEN_KEY = "token";

    private static final String TOKEN_EXPIRE = "token_expire";

    private static final String MAIL_FROM = "3DOS <noreply@3dos.io>";

    private static final String MAIL_CSS_SELECTOR = "body > div > div.content > div > a";

    private static final MailReader mailReader = MailReaderFactory.getMailReader(MailProtocolType.imap,
            "imap.gmail.com", "993", true);

    private final ThreeDosBot bot;

    private final String harvestedData;

    private final String harvestedUrl;

    public ThreeDosApi(ThreeDosBot bot) {
        this.bot = bot;
        this.harvestedData = bot.getAutoBotConfig().getConfig(HARVESTED_DATA_KEY);
        this.harvestedUrl = bot.getAutoBotConfig().getConfig(HARVESTED_URL_KEY);
    }

    public Result register(AccountContext exampleAC, List<AccountContext> sameABIIdList, String inviteCode) {

        JSONObject body = new JSONObject();
        body.put("country_id", "233");
        body.put("acceptTerms", true);
        body.put("referred_by", inviteCode);

        AccountBaseInfo accountBaseInfo = exampleAC.getAccountBaseInfo();
        body.put("email", accountBaseInfo.getEmail());
        body.put("password", exampleAC.getParam(PASSWORD_KEY));

        Map<String, String> headers = exampleAC.getBrowserEnv().generateHeaders();
        headers.put("Origin", "https://dashboard.3dos.io");
        headers.put("Referer", "https://dashboard.3dos.io/");


        try {
            CompletableFuture<Boolean> future = bot.syncRequest(
                    exampleAC.getProxy(),
                    REGISTER_API,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> exampleAC.getSimpleInfo() + " 开始注册"
            ).thenApplyAsync(responseStr -> {
                JSONObject result = JSONObject.parseObject(responseStr);

                if (BooleanUtil.isTrue(result.getBoolean("flag"))) {
                    bot.logger.info(exampleAC.getSimpleInfo() + "注册成功");
                    return true;
                } else {
                    JSONArray data = result.getJSONArray("data");
                    for (int i = 0; i < data.size(); i++) {
                        if ("The email has already been taken.".equals(data.getString(i))) {
                            bot.logger.warn(exampleAC.getSimpleInfo() + "邮箱已被注册");
                            return true;
                        }
                    }
                    throw new RegisterException(exampleAC.getSimpleInfo() + "注册失败, " + responseStr);
                }
            }, bot.getExecutorService());

            future.get();
            return Result.ok();
        } catch (Exception e) {
            bot.logger.error(exampleAC.getSimpleInfo() + "注册失败，", e.getCause());
            return Result.fail("注册失败, " + e.getMessage());
        }
    }

    public void resendEmail(AccountContext exampleAC, List<AccountContext> accountContexts) {
        String token = exampleAC.getParam(TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            return;
        }

        Map<String, String> headers = createAuthHeader(exampleAC, token);

        CompletableFuture<String> query = bot.syncRequest(
                exampleAC.getProxy(),
                RESEND_EMAIL_API,
                HttpMethod.GET,
                headers,
                null,
                null,
                () -> exampleAC.getSimpleInfo() + " 请求重发验证邮件"
        );

        try {
            String responseStr = query.get();

            bot.logger.info("%s 验证邮件重发成功,  %s".formatted(exampleAC.getSimpleInfo(), responseStr));
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            bot.logger.error("%s 验证邮件重发失败, %s".formatted(exampleAC.getSimpleInfo(), errorMsg));
        }
    }

    public void checkEmail(AccountContext accountContext) {
        AccountBaseInfo accountBaseInfo = accountContext.getAccountBaseInfo();

        bot.logger.info("开始验证邮箱 " + accountBaseInfo.getEmail());
        String emailPassword = (String) accountBaseInfo.getParams().get("imap_password");
        emailPassword = emailPassword.replace(" ", "").replace(" ", "");

        AtomicReference<String> link = new AtomicReference<>("");
        mailReader.stoppableReadMessage(
                accountBaseInfo.getEmail(),
                emailPassword,
                3,
                message -> {
                    String newValue = resolveLinkFromMessage(message);
                    link.set(newValue);
                    return StrUtil.isNotBlank(newValue);
                }
        );

        if (StrUtil.isNotBlank(link.get())) {
            bot.logger.info("邮箱[%s]验证链接获取成功, %s".formatted(accountBaseInfo.getEmail(), link));
            try {
                bot.syncRequest(
                        accountContext.getProxy(),
                        link.get(),
                        HttpMethod.GET,
                        accountContext.getBrowserEnv().generateHeaders(),
                        null,
                        null,
                        () -> "点击邮箱[%s]验证链接[%s]".formatted(accountBaseInfo.getEmail(), link),
                        3
                ).get();

            } catch (InterruptedException | ExecutionException e) {
                bot.logger.error("[%s]邮箱验证失败".formatted(accountBaseInfo.getEmail()), e);
            }
        } else {
            bot.logger.error("[%s]验证链接提取失败".formatted(accountBaseInfo.getEmail()));
        }
    }


    private String resolveLinkFromMessage(Message message) {
        try {
            boolean b = Arrays.stream(message.getFrom())
                    .anyMatch(address -> MAIL_FROM.equals(address.toString()));
            if (!b) return null;

            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();

            String htmlStr = mimeMultipart.getBodyPart(1).getContent().toString();

            Document document = Jsoup.parse(htmlStr);
            // 使用 CSS 选择器提取 a 标签内容
            Elements linkElement = document.select(MAIL_CSS_SELECTOR);
            return linkElement.attr("href");
        } catch (Exception e) {
            throw new RuntimeException("从邮件提取链接出错", e);
        }
    }

    public Result login(AccountContext accountContext) {
        String existToken = accountContext.getParam(TOKEN_KEY);
        String expireStr = accountContext.getParam(TOKEN_EXPIRE);
        if (StrUtil.isNotBlank(existToken)
                && StrUtil.isNotBlank(expireStr)
                && System.currentTimeMillis() - Long.parseLong(expireStr) < 0
        ) {
            bot.logger.warn("%s 已有可用token".formatted(accountContext.getSimpleInfo()));
            return Result.ok();
        }

        JSONObject body = new JSONObject();
        body.put("email", accountContext.getAccountBaseInfo().getEmail());
        body.put("password", accountContext.getParam(PASSWORD_KEY));

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("Origin", "https://dashboard.3dos.io");
        headers.put("Referer", "https://dashboard.3dos.io/");

        CompletableFuture<String> tokenFuture = bot.syncRequest(
                accountContext.getProxy(),
                LOGIN_API,
                HttpMethod.POST,
                headers,
                null,
                body,
                () -> accountContext.getSimpleInfo() + " 开始登录"
        ).thenApplyAsync(responseStr -> {
            JSONObject result = JSONObject.parseObject(responseStr);

            if (BooleanUtil.isTrue(result.getBoolean("flag"))) {
                bot.logger.info(accountContext.getSimpleInfo() + "登录成功");
                JSONObject data = result.getJSONObject("data");

                accountContext.setParam(TOKEN_EXPIRE, System.currentTimeMillis() + data.getInteger("expires_in") * 1000);
                return data.getString("access_token");
            }
            throw new LoginException("登录获取token失败," + responseStr);
        });

        try {
            String token = tokenFuture.get();
            bot.logger.info("%s 登录成功, token: %s".formatted(accountContext.getSimpleInfo(), token));
            return Result.ok(token);
        } catch (InterruptedException | ExecutionException e) {
            bot.logger.error("%s 登录失败".formatted(accountContext.getSimpleInfo()), e);
            return Result.fail("登录失败, " + e.getMessage());
        }
    }

    public void keepLive(AccountContext accountContext) {
        String secretKey = accountContext.getParam(SECRET_KEY);
        if (StrUtil.isBlank(secretKey)) {
            bot.logger.warn(accountContext.getSimpleInfo() + " 没有secret key");
            return;
        }
        JSONObject body = new JSONObject();
        body.put("apiSecret", secretKey);
        body.put("harvestedData", harvestedData);
        body.put("url", harvestedUrl);

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();

        CompletableFuture<String> request = bot.syncRequest(
                accountContext.getProxy(),
                KEEP_ALIVE_API,
                HttpMethod.POST,
                headers,
                null,
                body,
                () -> accountContext.getSimpleInfo() + "发送心跳..."
        );

        try {
            String responseStr = request.get();
            bot.logger.info("%s 发送心跳成功, %s".formatted(accountContext.getSimpleInfo(), responseStr));
        } catch (InterruptedException | ExecutionException e) {
            bot.logger.error("%s 发送心跳失败, %s".formatted(accountContext.getSimpleInfo(),
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
    }


    public Result updateAccount(AccountContext exampleAC, List<AccountContext> sameABIIdList) {
        String token = exampleAC.getParam(TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            bot.logger.warn("%s token为空".formatted(exampleAC.getSimpleInfo()));
            return Result.fail("token为空");
        }

        Map<String, String> headers = createAuthHeader(exampleAC, token);

        CompletableFuture<String> query = bot.syncRequest(
                exampleAC.getProxy(),
                REWORD_QUERY_API,
                HttpMethod.POST,
                headers,
                null,
                new JSONObject(),
                () -> exampleAC.getSimpleInfo() + " 开始更新账户信息"
        );

        try {
            String responseStr = query.get();

            JSONObject result = JSONObject.parseObject(responseStr);
            JSONObject data = result.getJSONObject("data");

            String apiSecret = data.getString("api_secret");
            for (AccountContext ac : sameABIIdList) {
                RewordInfo rewordInfo = ac.getRewordInfo();
                rewordInfo.setDailyPoints(data.getDouble("todays_earning"));
                rewordInfo.setTotalPoints(data.getDouble("loyalty_points"));
                ac.setParam(SECRET_KEY, apiSecret);
            }

            bot.logger.info("%s 更新账户信息成功, api_secret -> %s".formatted(exampleAC.getSimpleInfo(), apiSecret));
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            bot.logger.info("%s 更新账户信息失败, %s".formatted(exampleAC.getSimpleInfo(), errorMsg));
            return Result.fail("更新账户信息失败, " + errorMsg);
        }
    }

    public void generateSecretKey(AccountContext accountContext) {
        String token = accountContext.getParam(TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            bot.logger.warn("%s token为空".formatted(accountContext.getSimpleInfo()));
            return;
        }

        Map<String, String> headers = createAuthHeader(accountContext, token);

        CompletableFuture<String> query = bot.syncRequest(
                accountContext.getProxy(),
                GENERATE_KEY_API,
                HttpMethod.POST,
                headers,
                null,
                new JSONObject(),
                () -> accountContext.getSimpleInfo() + " 开始获取secret key"
        );

        try {
            String responseStr = query.get();

            JSONObject result = JSONObject.parseObject(responseStr);
            JSONObject data = result.getJSONObject("data");
            if (data == null) {
                bot.logger.warn("%s secret key获取失败, %s".formatted(accountContext.getSimpleInfo(), responseStr));
            } else {
                String key = data.getString(SECRET_KEY);
                accountContext.setParam(SECRET_KEY, key);
                bot.logger.info("%s secret key获取成功, api_secret -> %s".formatted(accountContext.getSimpleInfo(), key));
            }
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            bot.logger.error("%s secret key获取失败, %s".formatted(accountContext.getSimpleInfo(), errorMsg));
        }
    }


    public void dailyCheckIn(AccountContext exampleAC, List<AccountContext> sameABIIdList) {
        String token = exampleAC.getParam(TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            bot.logger.warn("%s token为空".formatted(exampleAC.getSimpleInfo()));
            return;
        }

        Map<String, String> headers = createAuthHeader(exampleAC, token);

        JSONObject body = new JSONObject();
        body.put("id", "daily-reward-api");

        CompletableFuture<String> query = bot.syncRequest(
                exampleAC.getProxy(),
                DAILY_CHECK_IN_API,
                HttpMethod.POST,
                headers,
                null,
                body,
                () -> exampleAC.getSimpleInfo() + " 领取每日奖励"
        );

        try {
            String responseStr = query.get();

            JSONObject result = JSONObject.parseObject(responseStr);
            if (result.getBoolean("flag")) {
                bot.logger.info("%s 领取每日奖励成功，%s".formatted(exampleAC.getSimpleInfo(), result.getJSONObject("data")));
            } else {
                bot.logger.warn("%s 领取每日奖励失败, %s".formatted(exampleAC.getSimpleInfo(), responseStr));
            }
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            bot.logger.info("%s 领取每日奖励失败, %s".formatted(exampleAC.getSimpleInfo(), errorMsg));
        }
    }

    @NotNull
    private static Map<String, String> createAuthHeader(AccountContext accountContext, String token) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Origin", "https://dashboard.3dos.io");
        headers.put("Referer", "https://dashboard.3dos.io/");
        return headers;
    }
}
