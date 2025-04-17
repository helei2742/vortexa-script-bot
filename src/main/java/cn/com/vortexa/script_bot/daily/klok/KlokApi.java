package cn.com.vortexa.script_bot.daily.klok;

import cn.com.vortexa.captcha.CaptchaResolver;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountBaseInfo;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.web3.EthWalletUtil;
import cn.com.vortexa.web3.dto.WalletInfo;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.Keys;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KlokApi {

    public static final String PEER_ACCOUNT_REFER_KEY = "peer_account_refer";
    public static final String PRIMARY_KEY = "primary_key";
    public static final String ETH_ADDRESS = "eth_address";
    public static final String SESSION_TOKEN = "session_token";
    public static final String DAILY_TIMES = "daily_times";
    public static final String DAILY_LIMIT = "daily_limit";
    public static final String REFER_CODE = "refer_code";

    public static final String LOGIN_SITE_KEY = "6LcZrRMrAAAAAKllb4TLb1CWH2LR7iNOKmT7rt3L";
    public static final String LOGIN_SITE_URL = "https://klokapp.ai";
    public static final String LOGIN_SITE_ACTION = "page_load";

    private static final String VERIFY_API = "https://api1-pp.klokapp.ai/v1/verify";
    private static final String BASE_API = "https://api1-pp.klokapp.ai";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC);

    private final KlokBot klokBot;
    private List<String> questions = null;
    private final Random random = new Random();

    private final String twoCaptchaKey;

    public KlokApi(KlokBot klokBot) {
        this.klokBot = klokBot;
        this.twoCaptchaKey = klokBot.getAutoBotConfig().getConfig(MapConfigKey.TWO_CAPTCHA_API_KEY);
        try {
            questions = Files.readAllLines(Path.of(klokBot.getAutoBotConfig().getResourceDir() + File.separator + "question.txt"));
        } catch (IOException e) {
            log.error("error", e);
        }
    }

    public Result registerOrLogin(AccountContext accountContext, String inviteCode) {
        try {
            String existToken = accountContext.getParam(SESSION_TOKEN);
            if (StrUtil.isNotBlank(existToken)) {
                return Result.ok(existToken);
            }
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

    public CompletableFuture<String> verify(
            AccountContext accountContext,
            String message,
            String signature,
            String inviteCode
    ) {
        klokBot.logger.debug(accountContext.getSimpleInfo() + " start recaptcha verify...");
        return CaptchaResolver.reCaptchaV3EnterpriseResolve(
                accountContext.getProxy(),
                LOGIN_SITE_URL,
                LOGIN_SITE_KEY,
                LOGIN_SITE_ACTION,
                twoCaptchaKey
        ).thenApply(token -> {
            klokBot.logger.debug(accountContext.getSimpleInfo() + " start recaptcha verify success...");
            JSONObject body = new JSONObject();
            body.put("signedMessage", signature);
            body.put("message", message);
            body.put("referral_code", inviteCode);
            body.put("recaptcha_token", token);
            Map<String, String> headers = generateACHeader(accountContext);

            try {
                String response = klokBot.syncRequest(
                        accountContext.getProxy(),
                        VERIFY_API,
                        HttpMethod.POST,
                        headers,
                        null,
                        body,
                        () -> accountContext.getSimpleInfo() + " send verify request"
                ).get();

                JSONObject result = JSONObject.parseObject(response);
                if ("Verification successful".equals(result.getString("message"))) {
                    return result.getString("session_token");
                } else {
                    throw new RuntimeException("Verification failed, " + response);
                }
            } catch (Exception e) {
                throw new RuntimeException("Verification failed, " + e.getMessage());
            }
        });
    }

    public void dailyTask(AccountContext accountContext, String inviteCode) throws ExecutionException, InterruptedException {
        String simpleInfo = accountContext.getSimpleInfo();

//            Result result = registerOrLogin(accountContext, inviteCode);

        klokBot.logger.debug(simpleInfo + " start daily task");

        String token = accountContext.getParam(SESSION_TOKEN);

        if (StrUtil.isBlank(token)) {
            return;
        }

        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("x-session-token", token);

        while (true) {
            int count = 0;
            JSONObject limitCheck = null;
            for (int i = 0; i < 15; i++) {
                limitCheck = accountRequestLimitCheck(accountContext, headers);
                if (limitCheck != null) {
                    count = limitCheck.getInteger("remaining");
                    break;
                }
                klokBot.logger.debug(simpleInfo + " rate limit[%d/%d], sleep 30s...".formatted(i + 1, count));
                TimeUnit.SECONDS.sleep(60);
            }

            if (limitCheck == null || count <= 0) {
                klokBot.logger.warn(simpleInfo + " Daily limited " + count);
                return;
            }


            JSONObject body = new JSONObject();
            body.put("id", UUID.randomUUID().toString());
            JSONArray message = buildChatMessage();
            body.put("messages", message);
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
                        () -> simpleInfo + " send chat request - " + finalCount + " " + message
                ).get();
                klokBot.logger.info("%s daily chat %d finish..question:[%s].%s".formatted(
                        simpleInfo, count, body.get("messages"), chatResult.substring(0, Math.min(20, chatResult.length())))
                );
            } catch (Exception e) {
                klokBot.logger.error("daily chat %d error, %s".formatted(count,
                        e.getCause() == null ? e.getCause().getMessage() : e.getMessage()));
            }

            int i = random.nextInt(10);
            klokBot.logger.debug(simpleInfo + " sleep..." + i);
            TimeUnit.SECONDS.sleep(i);
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
                    BASE_API + "/v1/points",
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
            accountContext.getRewordInfo().setTotalPoints(response.getDouble("total_points"));
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = simpleInfo + " reword query error, " + (e.getCause() == null ? e.getCause().getMessage()
                    : e.getMessage());
            klokBot.logger.error(errorMsg);
            return Result.fail(errorMsg);
        }
    }

    public void autoRefer(AccountContext accountContext, Integer count) throws IOException, ExecutionException, InterruptedException {
        String simpleInfo = accountContext.getSimpleInfo();

        klokBot.logger.info(simpleInfo + " start auto refer");

        Path path = Paths.get(klokBot.getAppConfigDir() + File.separator + "refer"
                + File.separator + accountContext.getId() + "_" + simpleInfo + ".txt");
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        List<Pair<WalletInfo, Boolean>> list = new ArrayList<>();

//            if (Files.exists(path)) {
//                // 1 读钱包
//                try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
//                    StringBuilder sb = new StringBuilder();
//                    String line;
//                    while (((line = reader.readLine()) != null)) {
//                        sb.append(line);
//                    }
//                    list = new ArrayList<>(JSONObject.parseObject(sb.toString(), new TypeReference<>() {}));
//                }
//            }
//
        int newCount = count - list.size();

        if (newCount > 0) {
            // 2 生成钱包
            try {
                for (int i = 0; i < newCount; i++) {
                    WalletInfo walletInfo = EthWalletUtil.generateEthWallet();
                    list.add(new Pair<>(walletInfo, false));
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                    writer.write(JSONObject.toJSONString(list));
                    writer.flush();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        List<Pair<WalletInfo, Boolean>> noReferWallet = list.stream().filter(pair -> !pair.getValue()).toList();

        if (noReferWallet.isEmpty()) {
            klokBot.logger.warn(simpleInfo + " refer limit");
            return;
        }

        String referCode = accountContext.getParam(REFER_CODE);
        if (StrUtil.isBlank(referCode)) {
            registerOrLogin(accountContext, null);
            String token = accountContext.getParam(SESSION_TOKEN);
            if (token == null) {
                klokBot.logger.error(simpleInfo + " token is null");
                return;
            }

            referCode = getAccountReferCode(accountContext);
            accountContext.setParam(REFER_CODE, referCode);
        }

        // 3 邀请
        String finalReferCode = referCode;
        noReferWallet.forEach(pair -> {
            WalletInfo walletInfo = pair.getKey();
            try {
                AccountContext referAc = new AccountContext();
                referAc.setParam(PRIMARY_KEY, walletInfo.getPrivateKey());
                referAc.setParam(ETH_ADDRESS, Keys.toChecksumAddress(walletInfo.getAddress()));
                referAc.setAccountBaseInfo(AccountBaseInfo.builder()
                        .name(accountContext.getName() + "-refer-" + walletInfo.getAddress())
                        .build()
                );
                referAc.setProxy(accountContext.getProxy());
                referAc.setBrowserEnv(accountContext.getBrowserEnv());
                dailyTask(referAc, finalReferCode);
            } catch (Exception e) {
                klokBot.logger.error(simpleInfo + " refer [%s] error".formatted(walletInfo.getAddress()), e);
            }
        });
    }


    private String getAccountReferCode(AccountContext accountContext) throws ExecutionException, InterruptedException {
        Map<String, String> headers = generateACHeader(accountContext);
        headers.put("x-session-token", accountContext.getParam(SESSION_TOKEN));
        String responseStr = klokBot.syncRequest(
                accountContext.getProxy(),
                BASE_API + "/v1/referral/stats",
                HttpMethod.GET,
                headers,
                null,
                null,
                () -> accountContext.getSimpleInfo() + " get refer code request"
        ).get();
        JSONObject jb = JSONObject.parseObject(responseStr);
        return jb.getString("referral_code");
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
