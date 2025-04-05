package cn.com.vortexa.script_bot.daily.magic_newton;


import cn.com.vortexa.browser_control.driver.BitBrowserDriver;
import cn.com.vortexa.browser_control.dto.SeleniumParams;
import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.script_node.service.BotApi;
import cn.hutool.core.lang.Pair;

import java.io.IOException;
import java.util.List;


/**
 * @author helei
 * @since 2025-04-05
 */
@BotApplication(
        name = "magic_newton",
        configParams = {MagicNewtonBot.BIT_BROWSER_API_URL, MagicNewtonBot.CHROME_DRIVER_URL},
        accountParams = MagicNewtonBot.FINGER_BROWSER_SEQ
)
public class MagicNewtonBot extends AutoLaunchBot<MagicNewtonBot> {

    public static final String BIT_BROWSER_API_URL = "bit_browser_api_url";
    public static final String CHROME_DRIVER_URL = "chrome_driver_url";
    public static final String FINGER_BROWSER_SEQ = "finger_browser_seq";

    public static final String TARGET_SITE_URL = "https://www.magicnewton.com/portal/rewards";

    private BitBrowserDriver browserDriver;

    private String chromeDriverUrl;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        String connectUrl = (String) botConfig.getCustomConfig().get(BIT_BROWSER_API_URL);
        chromeDriverUrl = (String) botConfig.getCustomConfig().get(CHROME_DRIVER_URL);
        browserDriver = new BitBrowserDriver(connectUrl);
    }

    @Override
    protected MagicNewtonBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60 * 12, concurrentCount = 10)
    public void dailyTask(AccountContext accountContext) throws IOException {
        if (accountContext.getId() != 1) return;

        Integer fingerSeq = Integer.parseInt(accountContext.getParam(FINGER_BROWSER_SEQ));
        String debuggerAddress = browserDriver.startWebDriverBySeq(fingerSeq);

        String simpleInfo = accountContext.getSimpleInfo();
        MagicNewtonSelenium magicNewtonSelenium = new MagicNewtonSelenium(simpleInfo,
                SeleniumParams
                        .builder()
                        .driverPath(chromeDriverUrl)
                        .experimentalOptions(List.of(new Pair<>("debuggerAddress", debuggerAddress)))
                        .targetWebSite(TARGET_SITE_URL)
                        .build()
        );

        try {
            browserDriver.flexAbleWindowBounds(List.of(fingerSeq));
            magicNewtonSelenium.syncStart();
        } catch (InterruptedException e) {
            logger.error(simpleInfo + " rpa execute error", e);
        }
    }
}
