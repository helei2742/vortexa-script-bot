package cn.com.vortexa.script_bot.daily.haha_wallet;

import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.exception.BotInitException;
import cn.com.vortexa.common.exception.BotStartException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cn.com.vortexa.script_bot.daily.haha_wallet.HahaWalletSelenium.CHROME_DRIVER_PATH;
import static cn.com.vortexa.script_bot.daily.haha_wallet.HahaWalletSelenium.HAHA_WALLET_EXTENSION_CRX_PATH;

@Slf4j
@BotApplication(
        name = "haha_selenium_bot",
        accountParams = {HaHaWalletBot.WALLET_KEY}
)
public class HaHaWalletBot extends AutoLaunchBot<HaHaWalletBot> {

    public static final String WALLET_KEY = "haha_wallet";

    public static final String USERNAME_KEY = "haha_username";

    public static final String PASSWORD_KEY = "haha_password";

    public static final String TODAY_COUNT_KEY = "haha_today_count";

    public static final String TODAY_KEY = "haha_today_count";

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        System.setProperty("java.awt.headless", "false");

        String resourceDir = botConfig.getResourceDir();
        HAHA_WALLET_EXTENSION_CRX_PATH = resourceDir + File.separator + "HaHa-Wallet-Chrome-Web-Store.crx";
        CHROME_DRIVER_PATH = resourceDir + File.separator + "chromedriver";
    }

    @Override
    protected HaHaWalletBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60 * 24,
            dynamicTrigger = false, dynamicTimeWindowMinute = 60, syncExecute = true)
    public void dailyTask(AccountContext accountContext) throws IOException, InterruptedException {
        logger.info("[%s] start daily task".formatted(accountContext.getSimpleInfo()));

        HahaWalletSelenium hahaWalletSelenium = new HahaWalletSelenium(this, accountContext);
        hahaWalletSelenium.syncStart();
        hahaWalletSelenium.close();

        logger.info("[%s] daily task finish".formatted(accountContext.getSimpleInfo()));
    }

    public static void main(String[] args) throws BotStartException, BotInitException {
        List<String> list = new ArrayList<>(List.of(args));
        list.add("--vortexa.botKey=haha_wallet_test");
        list.add("--bot.accountConfig.configFilePath=haha_wallet_google.xlsx");
        list.add("--add-opens java.base/java.lang=ALL-UNNAMED");

//        ScriptAppLauncher.launch(HaHaWalletBot.class, list.toArray(new String[0]));
    }
}

