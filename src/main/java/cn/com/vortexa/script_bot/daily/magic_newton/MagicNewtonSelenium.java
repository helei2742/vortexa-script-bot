package cn.com.vortexa.script_bot.daily.magic_newton;

import cn.com.vortexa.browser_control.OptSeleniumInstance;
import cn.com.vortexa.browser_control.SeleniumInstance;
import cn.com.vortexa.browser_control.dto.SeleniumParams;
import cn.com.vortexa.browser_control.execute.ExecuteGroup;
import cn.com.vortexa.browser_control.execute.ExecuteItem;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;


/**
 * @author helei
 * @since 2025-04-05
 */
public class MagicNewtonSelenium extends OptSeleniumInstance {

    public MagicNewtonSelenium(
            String instanceId,
            SeleniumParams params
    ) throws IOException {
        super(instanceId, params);
        setAutoClose(false);
    }

    @Override
    protected WebDriver createWebDriver(ChromeOptions chromeOptions) {
        try {
            URL remoteWebDriverUrl = new URL(getParams().getDriverPath());
            chromeOptions.setCapability("timeouts", new HashMap<String, Integer>() {{
                put("script", 30000);  // 设置脚本执行超时
                put("pageLoad", 30000);  // 设置页面加载超时
                put("implicit", 30000);  // 设置隐式等待超时
            }});
            return new RemoteWebDriver(remoteWebDriverUrl, chromeOptions);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
        addExecuteFun(ExecuteGroup
                .builder().name("摇筛子").enterCondition((webDriver, params) -> {
                    return true;
                })
                .executeItems(List.of(
                        ExecuteItem.builder().name("进入摇骰子界面").executeLogic(this::enterDice).build()
                ))
                .build()
        ).addExecuteFun(ExecuteGroup
                .builder().name("扫雷").enterCondition((webDriver, params) -> {
                    webDriver.get(getParams().getTargetWebSite());
                    return true;
                })
                .executeItems(List.of(
                        ExecuteItem.builder().name("进入扫雷界面").executeLogic(this::enterScanBoom).build(),
                        ExecuteItem.builder().name("扫雷。。。").executeLogic(this::scanBoomProcess).build()
                ))
                .build()
        );
    }

    @Override
    protected void addDefaultChromeOptions(ChromeOptions chromeOptions) {

    }


    private void enterDice(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        xPathClick("//p[text()='Roll now']");
    }

    private void enterScanBoom(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        xPathClick("//p[text()='Play now']");
        xPathClick("/html/body/div[2]/div[11]/div[3]/div/div[2]/div[2]/div[1]/div/svg");
    }

    private void scanBoomProcess(WebDriver webDriver, SeleniumInstance seleniumInstance) {

    }
}
