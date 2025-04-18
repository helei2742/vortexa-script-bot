package cn.com.vortexa.script_bot.daily.taker;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.script_node.service.BotApi;

/**
 * @author helei
 * @since 2025/4/18 9:02
 */
@BotApplication(name = "taker_auto_bot", accountParams = MapConfigKey.WALLET_PRIMARY_KEY_KEY)
public class TakerBot extends AutoLaunchBot<TakerBot> {

    private TakerApi takerApi;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        this.takerApi = new TakerApi(this);
    }

    @Override
    protected TakerBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60 * 3 + 120)
    public void autoClaim(AccountContext accountContext) {
        try {
            takerApi.autoClaim(accountContext);
        } catch (Exception e) {
            logger.error(accountContext.getSimpleInfo() + " auto claim fail.."
                    + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
    }
}
