package cn.com.vortexa.script_bot.daily.monadscore;

import cn.com.vortexa.common.exception.BotInitException;
import cn.com.vortexa.common.exception.BotStartException;
import cn.com.vortexa.common.util.http.RestApiClient;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.script_node.service.BotApi;
import com.alibaba.fastjson.JSONObject;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.hutool.core.util.StrUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author helei
 * @since 2025/3/28 9:51
 */
@BotApplication(
        name = "monad_score",
        configParams = {MapConfigKey.INVITE_CODE_KEY},
        accountParams = {MonadScoreBot.WALLET_ADDRESS}
)
public class MonadScoreBot extends AutoLaunchBot<MonadScoreBot> {
    public static final String WALLET_ADDRESS = "wallet_address";
    public static final String TOKEN = "token";

    public static final String BASE_URL = "https://mscore.onrender.com";

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        RestApiClient.readTimeout = 300;
        RestApiClient.writeTimeout = 300;
        RestApiClient.connectTimeout = 300;
        setRequestConcurrentCount(25);
    }

    @BotMethod(jobType = BotJobType.REGISTER, concurrentCount = 25)
    public Result register(AccountContext uniqueAC, List<AccountContext> sameBIdACList, String inviteCode) {
        return verify(uniqueAC, inviteCode);
    }

    @NotNull
    private Result verify(AccountContext uniqueAC, String inviteCode) {
        String simpleInfo = uniqueAC.getSimpleInfo();

        if (StrUtil.isBlank(inviteCode)) {
            logger.warn(simpleInfo + " register cancel, invite code is empty");
        }

        logger.debug(simpleInfo + " start register, invite code: " + inviteCode);

        String walletAddress = uniqueAC.getParam(WALLET_ADDRESS);
        JSONObject body = new JSONObject();
        body.put("wallet", walletAddress);
        body.put("invite", inviteCode);

        try {
            String responseStr = syncRequest(
                    uniqueAC.getProxy(),
                    BASE_URL + "/user",
                    HttpMethod.POST,
                    generateHeader(uniqueAC),
                    null,
                    body,
                    ()->simpleInfo + " send registry request",
                    2
            ).get();

            logger.info("%s active node response: %s".formatted(simpleInfo, responseStr));

            JSONObject result = JSONObject.parseObject(responseStr);
            String token = result.getString(TOKEN);

            uniqueAC.setParam(TOKEN, token);
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("%s active node error, %s".formatted(
                    simpleInfo, e.getCause() == null ? e.getCause().getMessage() : e.getMessage())
            );
            return Result.fail(e.getMessage());
        }
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60 * 24)
    public void activeNode(AccountContext accountContext) {
        String simpleInfo = accountContext.getSimpleInfo();
        String walletAddress = accountContext.getParam(WALLET_ADDRESS);

        if (StrUtil.isBlank(walletAddress)) {
            logger.warn("%s didn't have wallet address, skip it".formatted(simpleInfo));
            return;
        }
        logger.debug("%s start active node...".formatted(simpleInfo));

        verify(accountContext, null);
        String token = accountContext.getParam(TOKEN);

        if (token == null) {
            logger.warn("%s didn't have token, skip it".formatted(simpleInfo));
            return;
        }

        Map<String, String> headers = generateHeader(accountContext);
        headers.put("authorization", "Bearer " + token);

        JSONObject body = new JSONObject();
        body.put("wallet", walletAddress);
        body.put("startTime", System.currentTimeMillis());

        try {
            String responseStr = syncRequest(
                    accountContext.getProxy(),
                    BASE_URL + "/user/update-start-time",
                    HttpMethod.PUT,
                    headers,
                    null,
                    body
            ).get();
            logger.info("%s active node response: %s".formatted(simpleInfo, responseStr));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("%s active node error, %s".formatted(
                    simpleInfo, e.getCause() == null ? e.getCause().getMessage() : e.getMessage())
            );
        }
    }

    @Override
    protected MonadScoreBot getInstance() {
        return this;
    }

    private Map<String, String> generateHeader(AccountContext accountContext) {
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("origin", "https://monadscore.xyz");
        headers.put("referer", "https://monadscore.xyz/");
        return headers;
    }

    public static void main(String[] args) throws BotStartException, BotInitException {
        List<String> list = new ArrayList<>(List.of(args));

        list.add("--vortexa.botKey=monad_score_google");

        list.add("--vortexa.accountConfig.configFilePath=monad_score_google.xlsx");
        list.add("--add-opens java.base/java.lang=ALL-UNNAMED");

//        ScriptAppLauncher.launch(MonadScoreBot.class, list.toArray(new String[0]));
    }
}
