package cn.com.vortexa.script_bot.daily.beamable;


import cn.com.vortexa.browser_control.SeleniumInstance;
import cn.com.vortexa.browser_control.constants.BrowserDriverType;
import cn.com.vortexa.browser_control.execute.ExecuteGroup;
import cn.com.vortexa.browser_control.execute.ExecuteItem;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.bot.selenium.FingerBrowserBot;
import cn.com.vortexa.script_node.dto.selenium.ACBotTypedSeleniumExecuteInfo;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author helei
 * @since 2025-04-06
 */
@Slf4j
@BotApplication(
        name = "beamable_bot"
)
public class BeamableBot extends FingerBrowserBot {
    public static final String TARGET_SITE_URL = "https://hub.beamable.network/modules/aprildailies";

    @Override
    protected BeamableBot getInstance() {
        return this;
    }

    @Override
    protected BrowserDriverType browserDriverType() {
        return BrowserDriverType.BIT_BROWSER;
    }

    @Override
    protected ACBotTypedSeleniumExecuteInfo buildExecuteGroupChain() {
        ArrayList<ExecuteGroup> list = new ArrayList<>();

        list.add(ExecuteGroup
                .builder().name("每日箱子").enterCondition((webDriver, params) -> {
                    webDriver.get(TARGET_SITE_URL);
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                })
                .executeItems(List.of(
                        ExecuteItem.builder().name("领取").executeLogic(this::dailyReword).build()
                ))
                .build()
        );
//        addExecuteFun(ExecuteGroup
//                .builder().name("完成任务").enterCondition((webDriver, params) -> {
//                    webDriver.get("https://hub.beamable.network/modules/questsold");
//                    try {
//                        TimeUnit.SECONDS.sleep(10);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                    return true;
//                })
//                .executeItems(List.of(
////                        ExecuteItem.builder().name("点击任务").executeLogic(this::clickTask).build()
//                        ExecuteItem.builder().name("领取奖励").executeLogic(this::taskClaim).build()
//                ))
//                .build());
        return new ACBotTypedSeleniumExecuteInfo(getBotKey(), list);
    }


    private void dailyReword(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        WebElement webElement = seleniumInstance.xPathFindElement("//div[@id=\"moduleGriddedContainer\"]/div/div/div/div[2]/div//button[./div[text()='Claim']]", 40);
        ((JavascriptExecutor) webDriver).executeScript("arguments[0].click();", webElement);
        seleniumInstance.randomWait(8);
    }

    private void clickTask(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        String mainHandle = webDriver.getWindowHandle();

        while (true) {
            try {
                webDriver.switchTo().window(mainHandle);
                webDriver.navigate().refresh();
                seleniumInstance.randomWait(3);
                Set<String> handles = webDriver.getWindowHandles();
                WebElement webElement = seleniumInstance.xPathFindElement("//div[@id=\"pageBackground\"]/div[2]/div/div/div[2]/div[\n" +
                        "  div/a/div[2]/div[count(div)=1] \n" +
                        "  and \n" +
                        "  count(div/a/div[2]/div) != 2\n" +
                        "  and \n" +
                        "  not(contains(div/a/div/div[2], 'Connect'))\n" +
                        "  and \n" +
                        "  not(contains(div/a/div/div[2], 'Youtube to learn'))\n" +
                        "]", 60);

                seleniumInstance.scrollTo(webElement);
                seleniumInstance.randomWait(2);
                webElement.click();

                seleniumInstance.xPathClick("//*[@id=\"moduleGriddedContainer\"]/div/div[2]/div[2]/div[1]/div[2]/div/div/div[2]/a", 60);

                seleniumInstance.randomWait(8);
                Set<String> after = webDriver.getWindowHandles();
                after.removeAll(handles);

                for (String handle : after) {
                    webDriver.switchTo().window(handle);
                    webDriver.close();
                }
                webDriver.switchTo().window(mainHandle);

                // 回到主页面
                seleniumInstance.xPathClick("//*[@id=\"moduleGriddedContainer\"]/div/div[1]", 60);
                seleniumInstance.randomWait(8);
            }catch (Exception e) {
                log.error(e.getMessage(), e);
                break;
            }
        }
        webDriver.switchTo().window(mainHandle);
        webDriver.get("https://hub.beamable.network/modules/questsold");
    }


    private void taskClaim(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        webDriver.get("https://hub.beamable.network/modules/questsold");
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        while (true) {
            try {
                WebElement webElement = seleniumInstance.xPathFindElement("//div[@id=\"pageBackground\"]/div[2]/div/div/div[2]/div[contains(div/a/div[2]/div[2], 'Claimable')]", 60);

                seleniumInstance.scrollTo(webElement);
                seleniumInstance.randomWait(3);
                webElement.click();

                seleniumInstance.xPathClick("//button[text()='claim reward']", 60);
                seleniumInstance.xPathClick("//button[text()='Close']", 60);

                seleniumInstance.randomWait();
                // 回到主页面
                seleniumInstance.xPathClick("//*[@id=\"moduleGriddedContainer\"]/div/div[1]", 60);
                seleniumInstance.randomWait();
            }catch (Exception e) {
                log.error(e.getMessage(), e);
                break;
            }
        }
    }
}
