package cn.com.vortexa.script_bot.depin.flow3;


import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.script_node.service.BotApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


/**
 * @author helei
 * @since 2025-04-10
 */
@Slf4j
@BotApplication(
        name = "flow3_bot",
        configParams = {
                MapConfigKey.TWO_CAPTCHA_API_KEY, MapConfigKey.INVITE_CODE_KEY
        },
        accountParams = {
                MapConfigKey.PASSWORD_KEY
        }
)
public class Flow3Bot extends AutoLaunchBot<Flow3Bot> {

    private Flow3Api flow3Api;

    private String inviteCode;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        flow3Api = new Flow3Api(this);
        inviteCode = (String) botConfig.getCustomConfig().get(MapConfigKey.INVITE_CODE_KEY);
    }

    @Override
    protected Flow3Bot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.ONCE_TASK, uniqueAccount = true, concurrentCount = 5)
    public Result register(AccountContext accountContext, List<AccountContext> sameAccount) {
        Result result = flow3Api.register(accountContext, inviteCode);
        if (result.getSuccess()) {
            logger.info(accountContext.getSimpleInfo() + " register success, invite code: " + inviteCode);
        } else {
            logger.error(accountContext.getSimpleInfo() + " register fail, " + result.getErrorMsg());
        }
        return result;
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, concurrentCount = 50, intervalInSecond = 30)
    public void keepAlive(AccountContext accountContext) {
        Result result = flow3Api.keepAlive(accountContext);
        if (result.getSuccess()) {
            logger.info(accountContext.getSimpleInfo() + " keepalive ping success, " + result.getData());
        } else {
            logger.error(accountContext.getSimpleInfo() + " keepalive ping fail, " + result.getErrorMsg());
        }
    }
}
