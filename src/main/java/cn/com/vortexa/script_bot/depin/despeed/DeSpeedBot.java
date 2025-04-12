package cn.com.vortexa.script_bot.depin.despeed;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.script_node.service.BotApi;

import java.util.List;

import static cn.com.vortexa.script_node.constants.MapConfigKey.PASSWORD_KEY;


@BotApplication(name = "stork_bot", accountParams = PASSWORD_KEY)
public class DeSpeedBot extends AutoLaunchBot<DeSpeedBot> {


    private DeSpeedApi deSpeedApi;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        deSpeedApi = new DeSpeedApi(this);
    }

    @Override
    protected DeSpeedBot getInstance() {
        return this;
    }

//    @BotMethod(jobType = BotJobType.REGISTER)
//    public Result signUp(AccountContext exampleAC, List<AccountContext> sameABIList, String inviteCode) {
//        return deSpeedApi.signup(exampleAC, sameABIList, inviteCode);
//    }
//
//    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 20)
//    public void tokenRefresh(AccountContext accountContext) {
//        deSpeedApi.refreshToken(accountContext);
//    }
//
//    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 5)
//    public void keepAlive(AccountContext accountContext) {
//        deSpeedApi.keepAlive(accountContext);
//    }

}
