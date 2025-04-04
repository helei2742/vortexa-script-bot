package cn.com.vortexa.script_bot.daily.klok;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.exception.BotInitException;
import cn.com.vortexa.common.exception.BotStartException;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.web3.EthWalletUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@BotApplication(
        name = "klok_bot",
        accountParams = {KlokBot.PRIMARY_KEY}
)
public class KlokBot extends AutoLaunchBot<KlokBot> {

    public static final String PRIMARY_KEY = "primary_key";
    public static final String ETH_ADDRESS = "eth_address";
    public static final String SESSION_TOKEN = "session_token";
    public static final String DAILY_TIMES = "daily_times";
    public static final String DAILY_LIMIT = "daily_limit";

    private KlokApi klokApi;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        klokApi = new KlokApi(this);
    }

    @Override
    protected KlokBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.REGISTER, concurrentCount = 5)
    public Result register(AccountContext exampleAC, List<AccountContext> sameBAIDList, String inviteCode) {
        return klokApi.register(exampleAC, inviteCode);
    }

    @BotMethod(jobType = BotJobType.LOGIN, concurrentCount = 5)
    public Result login(AccountContext accountContext) {
        if (accountContext.getAccountBaseInfoId() != 3) return Result.fail("");
        return klokApi.login(accountContext);
    }

    @BotMethod(jobType = BotJobType.QUERY_REWARD, intervalInSecond = 24 * 60 * 60)
    public Result rewordQuery(AccountContext exampleAC, List<AccountContext> sameBAIDList) {
        return klokApi.rewordQuery(exampleAC);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 6 * 60 * 60, concurrentCount = 10)
    public void dailyTask(AccountContext accountContext) throws ExecutionException, InterruptedException {
        klokApi.dailyTask(accountContext);
    }

    public static void main(String[] args) throws BotStartException, BotInitException {
        List<String> list = new ArrayList<>(java.util.List.of(args));
        list.add("--vortexa.botKey=klok_test");
        list.add("--vortexa.customConfig.invite_code=TJXGVPJT");
        list.add("--vortexa.accountConfig.configFilePath=klok_google.xlsx");
        list.add("--add-opens java.base/java.lang=ALL-UNNAMED");

//        ScriptAppLauncher.launch(KlokBot.class, list.toArray(new String[0]));
    }

    @Slf4j
    static class KlokApi {

        private static final String VERIFY_API = "https://api1-pp.klokapp.ai/v1/verify";
        private static final String BASE_API = "https://api1-pp.klokapp.ai";
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC);

        private final KlokBot klokBot;
        private List<String> questions = null;
        private final Random random = new Random();

        public KlokApi(KlokBot klokBot) {
            this.klokBot = klokBot;
            try {
                questions = Files.readAllLines(Path.of(klokBot.getAutoBotConfig().getResourceDir() + File.separator + "question.txt"));
            } catch (IOException e) {
                log.error("error", e);
            }
        }

        public Result register(AccountContext accountContext, String inviteCode) {
            try {
                Pair<String, String> signature = generateSignature(accountContext);
                klokBot.logger.debug(accountContext.getSimpleInfo() + " signature success");

                CompletableFuture<String> tokenFuture = verify(accountContext, signature.getKey(), signature.getValue(), inviteCode);
                String token = tokenFuture.get();
                klokBot.logger.info(accountContext.getSimpleInfo() + " register success, inviteCode: " + inviteCode);
                accountContext.setParam(SESSION_TOKEN, token);

                return Result.ok(token);
            } catch (Exception e) {
                String errorMsg = accountContext.getSimpleInfo() + " register error, " +
                        (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                klokBot.logger.error(
                        errorMsg
                );
                return Result.fail(errorMsg);
            }
        }

        public Result login(AccountContext accountContext) {
            try {
                Pair<String, String> signature = generateSignature(accountContext);
                klokBot.logger.debug(accountContext.getSimpleInfo() + " signature success");

                CompletableFuture<String> tokenFuture = verify(accountContext, signature.getKey(), signature.getValue(),
                        null);
                String token = tokenFuture.get();
                klokBot.logger.info(accountContext.getSimpleInfo() + " login success, token: " + token);
                accountContext.setParam(SESSION_TOKEN, token);

                return Result.ok(token);
            } catch (Exception e) {
                String errorMsg = accountContext.getSimpleInfo() + " login success, token: " +
                        (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                klokBot.logger.error(
                        errorMsg
                );
                return Result.fail(errorMsg);
            }
        }

        public CompletableFuture<String> verify(
                AccountContext accountContext,
                String message,
                String signature,
                String inviteCode
        ) {
            JSONObject body = new JSONObject();
            body.put("signedMessage", signature);
            body.put("message", message);
            body.put("referral_code", inviteCode);

            Map<String, String> headers = generateACHeader(accountContext);

            return klokBot.syncRequest(
                    accountContext.getProxy(),
                    VERIFY_API,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> accountContext.getSimpleInfo() + " send verify request"
            ).thenApply(response -> {
                JSONObject result = JSONObject.parseObject(response);

                if ("Verification successful".equals(result.getString("message"))) {
                    return result.getString("session_token");
                } else {
                    throw new RuntimeException("Verification failed, " + response);
                }
            });
        }

        public void dailyTask(AccountContext accountContext) throws ExecutionException, InterruptedException {
            String simpleInfo = accountContext.getSimpleInfo();
            int count = (int) accountContext.getParams().getOrDefault(DAILY_TIMES, 10);
            Result result = login(accountContext);

            klokBot.logger.debug(simpleInfo + " start daily task, remaining: " + count);

            if (result.getSuccess()) {
                String token = accountContext.getParam(SESSION_TOKEN);

                Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
                headers.put("x-session-token", token);

                int errorCount = 0;

                JSONObject limitCheck = accountRequestLimitCheck(accountContext, headers);
                Integer remaining = limitCheck.getInteger("remaining");
                count = Math.min(count, remaining);

                if (count <= 0) {
                    klokBot.logger.warn(simpleInfo + " Daily limit reached " + count);
                    return;
                }

                while (count > 0) {
                    JSONObject body = new JSONObject();
                    body.put("id", UUID.randomUUID().toString());
                    body.put("messages", buildChatMessage());
                    body.put("model", "llama-3.3-70b-instruct");
                    body.put("created_at", currentISOTime());
                    body.put("language", "english");

                    try {
                        int finalCount = count;
                        String chatResult = klokBot.syncRequest(
                                accountContext.getProxy(),
                                BASE_API + "/v1/chat",
                                HttpMethod.POST,
                                headers,
                                null,
                                body,
                                () -> simpleInfo + " send chat request - " + finalCount
                        ).get();
                        klokBot.logger.info("%s daily chat %d finish...".formatted(simpleInfo, count));
                    } catch (Exception e) {
                        klokBot.logger.error("daily chat %d error, %s".formatted(count,
                                e.getCause() == null ? e.getCause().getMessage() : e.getMessage()));
                        errorCount++;
                    }
                    count--;
                }

                if (errorCount > 0) {
                    accountContext.setParam(DAILY_TIMES, errorCount);
                }
            }
        }

        public JSONObject accountRequestLimitCheck(AccountContext accountContext, Map<String, String> headers)
                throws ExecutionException, InterruptedException {

            String limitResponse = klokBot.syncRequest(
                    accountContext.getProxy(),
                    BASE_API + "/v1/rate-limit",
                    HttpMethod.GET,
                    headers,
                    null,
                    null,
                    () -> accountContext.getSimpleInfo() + " check request limit"
            ).get();

            return JSONObject.parseObject(limitResponse);
        }

        public Result rewordQuery(AccountContext accountContext) {
            String token = accountContext.getParam(SESSION_TOKEN);
            String simpleInfo = accountContext.getSimpleInfo();
            if (token == null) {
                return Result.fail(simpleInfo + " rewordQuery failed, token not found");
            }

            Map<String, String> headers = generateACHeader(accountContext);

            try {
                String responseStr = klokBot.syncRequest(
                        accountContext.getProxy(),
                        BASE_API + "/v1/chat/stats",
                        HttpMethod.GET,
                        headers,
                        null,
                        null,
                        () -> simpleInfo + " send reword query request"
                ).get();

                JSONObject response = JSONObject.parseObject(responseStr);
                response.put("message", "Reword query success");
                if (!accountContext.getParams().containsKey(DAILY_LIMIT)) {
                    accountContext.setParam(DAILY_LIMIT, response.getInteger("remaining"));
                }
                accountContext.getRewordInfo().setTotalPoints(response.getDouble("points_earned"));
                return Result.ok();
            } catch (InterruptedException | ExecutionException e) {
                String errorMsg = simpleInfo + " reword query error, " + (e.getCause() == null ? e.getCause().getMessage()
                        : e.getMessage());
                klokBot.logger.error(errorMsg);
                return Result.fail(errorMsg);
            }
        }

        private static @NotNull Map<String, String> generateACHeader(AccountContext accountContext) {
            Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
            headers.put("origin", "https://klokapp.ai");
            headers.put("referer", "https://klokapp.ai/");
            headers.put("content-type", "application/json");
            headers.put("accept", "*/*");
            return headers;
        }

        private JSONArray buildChatMessage() {
            JSONArray array = new JSONArray();
            JSONObject jb = new JSONObject();
            jb.put("role", "user");
            jb.put("content", questions.get(random.nextInt(questions.size())));
            array.add(jb);
            return array;
        }

        private String buildMessage(String address) {
            String template
                    = "klokapp.ai wants you to sign in with your Ethereum account:\n%s\n\n\nURI: https://klokapp.ai/\nVersion: 1\nChain ID: 1\nNonce: %s\nIssued At: %s";

            // 使用自定义格式化器，确保毫秒部分保留 3 位
            String issuedAt = currentISOTime();

            return template.formatted(address, EthWalletUtil.getRandomNonce(), issuedAt);
        }

        private static @NotNull String currentISOTime() {
            // 获取当前时间的 Instant 对象（UTC 时间）
            Instant now = Instant.now();

            return formatter.format(now);
        }

        private Pair<String, String> generateSignature(AccountContext accountContext) {
            String primaryKey = accountContext.getParam(PRIMARY_KEY);
            String address = accountContext.getParam(ETH_ADDRESS);
            if (StrUtil.isBlank(address)) {
                address = EthWalletUtil.getETHAddress(primaryKey);
                accountContext.setParam(ETH_ADDRESS, address);
            }
            String message = buildMessage(address);

            return new Pair<>(
                    message,
                    EthWalletUtil.signatureMessage2String(primaryKey, message)
            );
        }
    }
}
