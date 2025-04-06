package cn.com.vortexa.script_bot.daily.beamable;

import cn.com.vortexa.browser_control.OptSeleniumInstance;
import cn.com.vortexa.browser_control.SeleniumInstance;
import cn.com.vortexa.browser_control.dto.SeleniumParams;
import cn.com.vortexa.browser_control.execute.ExecuteGroup;
import cn.com.vortexa.browser_control.execute.ExecuteItem;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author helei
 * @since 2025-04-05
 */
@Slf4j
public class BeamableSelenium extends OptSeleniumInstance {

    public BeamableSelenium(
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
//        addExecuteFun(ExecuteGroup
//                .builder().name("每日箱子").enterCondition((webDriver, params) -> {
//                    webDriver.get(getParams().getTargetWebSite());
//                    try {
//                        TimeUnit.SECONDS.sleep(5);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                    return true;
//                })
//                .executeItems(List.of(
//                        ExecuteItem.builder().name("领取").executeLogic(this::dailyReword).build()
//                ))
//                .build()
//        ).
        addExecuteFun(ExecuteGroup
                .builder().name("完成任务").enterCondition((webDriver, params) -> {
                    webDriver.get("https://hub.beamable.network/modules/questsold");
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                })
                .executeItems(List.of(
                        ExecuteItem.builder().name("点击任务").executeLogic(this::clickTask).build(),
                        ExecuteItem.builder().name("领取奖励").executeLogic(this::taskClaim).build()
                ))
                .build());
    }


    @Override
    protected void addDefaultChromeOptions(ChromeOptions chromeOptions) {

    }


    private void dailyReword(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        WebElement webElement = xPathFindElement("//div[@id=\"moduleGriddedContainer\"]/div/div/div/div[2]/div//button[./div[text()='Claim']]", 10);
        ((JavascriptExecutor) webDriver).executeScript("arguments[0].click();", webElement);
        randomWait();
        randomWait();
        randomWait();
    }

    private void clickTask(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        String mainHandle = webDriver.getWindowHandle();

        while (true) {
            webDriver.switchTo().window(mainHandle);
            webDriver.navigate().refresh();

            Set<String> handles = webDriver.getWindowHandles();
            xPathClick("//div[@id=\"pageBackground\"]/div[3]/div/div/div[2]/div[\n" +
                    "  div/a/div[2]/div[count(div)=1] \n" +
                    "  and \n" +
                    "  count(div/a/div[2]/div) != 2\n" +
                    "  and \n" +
                    "  not(contains(div/a/div/div[2], 'Connect'))\n" +
                    "]", 60);

            xPathClick("//*[@id=\"moduleGriddedContainer\"]/div/div[2]/div[2]/div[1]/div[2]/div/div/div[2]/a", 60);

            randomWait(3);
            Set<String> after = webDriver.getWindowHandles();
            after.removeAll(handles);

            for (String handle : after) {
                webDriver.switchTo().window(handle);
                webDriver.close();
            }
            webDriver.switchTo().window(mainHandle);

            // 回到主页面
            xPathClick("//*[@id=\"moduleGriddedContainer\"]/div/div[1]", 60);
            randomWait(3);
        }
    }

    private void taskClaim(WebDriver webDriver, SeleniumInstance seleniumInstance) {
    }
}
