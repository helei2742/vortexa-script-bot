package cn.com.vortexa.script_bot.daily.klok;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.script_node.service.BotApi;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static cn.com.vortexa.script_bot.daily.klok.KlokApi.*;

@Slf4j
@BotApplication(
        name = "klok_bot",
        accountParams = {PRIMARY_KEY},
        configParams = {MapConfigKey.TWO_CAPTCHA_API_KEY}
)
public class KlokBot extends AutoLaunchBot<KlokBot> {

    private KlokApi klokApi;

    private String inviteCode;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        klokApi = new KlokApi(this);
        inviteCode = (String) botConfig.getCustomConfig().get(REFER_CODE);
    }

    @Override
    protected KlokBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.REGISTER, concurrentCount = 5)
    public Result register(AccountContext exampleAC, List<AccountContext> sameBAIDList, String inviteCode) {
        return klokApi.registerOrLogin(exampleAC, inviteCode);
    }

    @BotMethod(jobType = BotJobType.LOGIN, concurrentCount = 5)
    public Result login(AccountContext accountContext) {
        if (accountContext.getId() != 11) return Result.fail("test");
        return klokApi.registerOrLogin(accountContext, inviteCode);
    }

    @BotMethod(jobType = BotJobType.QUERY_REWARD, intervalInSecond = 24 * 60 * 60, uniqueAccount = true)
    public Result rewordQuery(AccountContext exampleAC, List<AccountContext> sameBAIDList) {
        return klokApi.rewordQuery(exampleAC);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 6 * 60 * 60, concurrentCount = 10)
    public void dailyTask(AccountContext accountContext) throws ExecutionException, InterruptedException {
        klokApi.dailyTask(accountContext, inviteCode);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60, concurrentCount = 50)
    public void autoRefer_v2(AccountContext accountContext) throws ExecutionException, InterruptedException {
        AutoBotConfig autoBotConfig = getAutoBotConfig();
        Integer count = (Integer) autoBotConfig.getCustomConfig().get(PEER_ACCOUNT_REFER_KEY);
        try {
            klokApi.autoRefer(accountContext, 1);
        } catch (IOException e) {
            logger.error(accountContext.getSimpleInfo() + " auto refer error", e);
        }
    }
}
