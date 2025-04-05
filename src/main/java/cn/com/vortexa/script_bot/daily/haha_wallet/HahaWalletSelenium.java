package cn.com.vortexa.script_bot.daily.haha_wallet;

import cn.com.vortexa.browser_control.*;
import cn.com.vortexa.browser_control.dto.SeleniumParams;
import cn.com.vortexa.browser_control.dto.SeleniumProxy;
import cn.com.vortexa.browser_control.execute.ExecuteGroup;
import cn.com.vortexa.browser_control.execute.ExecuteItem;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.entity.BrowserEnv;
import cn.com.vortexa.common.entity.ProxyInfo;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static cn.com.vortexa.script_bot.daily.haha_wallet.HaHaWalletBot.*;

public class HahaWalletSelenium extends OptSeleniumInstance {

    public static String HAHA_WALLET_EXTENSION_CRX_PATH = "";
    public static String CHROME_DRIVER_PATH = "";

    private static final Logger log = LoggerFactory.getLogger(HahaWalletSelenium.class);

    private final AccountContext accountContext;

    private int todayCount;


    public static void main(String[] args) throws IOException, InterruptedException {
        AccountContext testAC = new AccountContext();

        testAC.setProxy(ProxyInfo.builder().host("192.46.201.191").port(6705).username("").password("").build());
        testAC.setParam(WALLET_KEY, "23");
        testAC.setParam(USERNAME_KEY, "@qq.com");
        testAC.setParam(PASSWORD_KEY, "23");

        BrowserEnv browserEnv = new BrowserEnv();
        browserEnv.setUserAgent("Mozilla/55.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.116 Safari/537.36");

        testAC.setBrowserEnv(browserEnv);
        HahaWalletSelenium hahaWalletSelenium = new HahaWalletSelenium(new HaHaWalletBot(), testAC);

        hahaWalletSelenium.syncStart();
    }


    public HahaWalletSelenium(HaHaWalletBot bot, AccountContext accountContext) throws IOException {
        super(
                accountContext.getParam(HaHaWalletBot.USERNAME_KEY),
                new SeleniumProxy(
                        accountContext.getProxy().getHost(),
                        accountContext.getProxy().getPort(),
                        accountContext.getProxy().getUsername(),
                        accountContext.getProxy().getPassword()
                ),
                getParams(accountContext)
        );

        String email = accountContext.getParam(HaHaWalletBot.USERNAME_KEY);
        String password = accountContext.getParam(HaHaWalletBot.PASSWORD_KEY);
        String todayCountStr = accountContext.getParam(TODAY_COUNT_KEY);

        String lastRunDay = accountContext.getParam(TODAY_KEY);
        String today = DateTime.now().toString();

        if (StrUtil.isBlank(todayCountStr) || StrUtil.isBlank(lastRunDay) || !today.equals(todayCountStr)) {
            todayCount = getRandom().nextInt(10, 13);
        } else {
            todayCount = Integer.parseInt(todayCountStr);
        }

        accountContext.setParam(TODAY_KEY, today);
        accountContext.setParam(TODAY_COUNT_KEY, todayCount);


        bot.logger.info("[%s] remained [%s] today".formatted(accountContext.getAccountBaseInfoId(), todayCount));

        String wallet = accountContext.getParam(WALLET_KEY);

        if (todayCount == 0) {
            throw new RuntimeException("today total finish");
        }

        if (StrUtil.isBlank(wallet) || StrUtil.isBlank(email) || StrUtil.isBlank(password)) {
            bot.logger.warn("%s no email or password or wallet".formatted(accountContext.getSimpleInfo()));
            throw new IllegalArgumentException("email or password or wallet is empty");
        }

        this.accountContext = accountContext;
    }


    @Override
    public void init() {
        super.addExecuteFun(ExecuteGroup.builder()
                        .name("初始化")
                        .enterCondition((webDriver, params) -> {
                            return true;
                        })
                        .executeItems(List.of(
                                ExecuteItem.builder().name("代理验证").executeLogic(this::proxyVerify).build()
                        ))
                        .build()
                )
                .addExecuteFun(ExecuteGroup.builder()
                        .name("登录HaHa")
                        .enterCondition((webDriver, params) -> {
                            return !xPathExist("//*[@id=\"app-content\"]/div/div[2]/div[2]/div/button[2]");
                        })
                        .executeItems(List.of(
                                ExecuteItem.builder().name("切换到目标页面").executeLogic(this::changeToTargetPage).build(),
                                ExecuteItem.builder().name("登录账号").executeLogic(this::loginAccount).build(),
                                ExecuteItem.builder().name("导入钱包").executeLogic(this::importWallet).build()
                        ))
                        .build()
                )
                .addExecuteFun(ExecuteGroup.builder()
                        .name("解锁钱包")
                        .enterCondition((webDriver, params) -> {
                            return xPathExist("//*[@id=\"app-content\"]/div/div[2]/div[2]/div/button[2]");
                        })
                        .executeItems(List.of(
                                ExecuteItem.builder().name("输入Pin Code").executeLogic(((webDriver, seleniumInstance) -> {
                                    xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div/div/input").sendKeys("123456789");

                                    xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div/button[1]");
                                })).build()
                        ))
                        .build()
                )
                .addExecuteFun(ExecuteGroup.builder()
                        .name("每日任务")
                        .enterCondition((webDriver, params) -> {
                            return true;
                        })
                        .executeItems(List.of(
                                ExecuteItem.builder().name("进入monad Swap页面").executeLogic(this::enterMonadSwapPage).build(),
                                ExecuteItem.builder().name("交换Monad").executeLogic(this::monadSwap).build()
//                                ExecuteItem.builder().name("trans sepolia Eth").executeLogic(this::sepoliaSwapPage).build()
                        ))
                        .build()
                );

    }


    private void sepoliaSwapPage(WebDriver webDriver, SeleniumInstance seleniumInstance) {

        // 点击进入legacyPage
        xPathClick("//p[text()='Legacy Wallet']");

        // 点击网络选择按钮
        xPathClick("//div[text()='Sepolia' or text()='Monad Testnet']");

        // 点击选择测试网
        xPathClick("//li[text()='Testnet']");

        // 点击选择Sepolia
        xPathClick("//p[text()='Sepolia (ETH)']");

        randomWait();

        int count = seleniumInstance.getRandom().nextInt(5, 10);

        for (int i = 0; i < count; i++) {
            try {
                sepoliaSwap(webDriver, seleniumInstance);

            } catch (Exception e) {
                log.error("{} sepolia swap error", getInstanceId(), e);
            }
        }
    }

    private void sepoliaSwap(WebDriver webDriver, SeleniumInstance seleniumInstance) {

        // 点击send页面
        xPathClick("//p[text()='Send']");

        randomWait();

        // 选择代币
        List<WebElement> selectionBtnList = xPathFindElements("//*[@id=\"app-content\"]/div[1]/div[2]/div[2]/div[2]/div[1]/div[1]/div[1]//button");
        int select = seleniumInstance.getRandom().nextInt(selectionBtnList.size());
        selectionBtnList.get(select).click();

        // 选择自己的地址
        xPathClick("//div[contains(text(), 'Account 1')]", 60);
        randomWait();

        WebElement countP = xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[1]/div[2]/div[2]/div[3]/p");
        if (countP.getText().isEmpty()) {
            countP = xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[1]/div[2]/div[2]/div[3]/p");
        }
        double total = Double.parseDouble(countP.getText());
        double count = seleniumInstance.getRandom().nextDouble(0.01, 0.07) * total;

        // 输入数量
        WebElement countInput = xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[1]/div[2]/div[5]/div/div[2]/input");
        countInput.sendKeys("%.6f".formatted(count));

        // 点击下一步
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[2]/div[2]/button", 120);

        // 点击确认
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[2]/div[2]/button", 120);
    }


    private void monadSwap(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        WebElement swapCountInput = xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[1]/div[2]/div/div[3]/input");

        Random random = new Random();
        int monCount = random.nextInt(10, 13);
        int successTimes = 0;
        int errorTimes = 0;
        while (successTimes < monCount) {
            try {
                // 点击进入交换代币选择界面
                xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[3]/div[1]/button");

                // 随机选择代币
                List<WebElement> token2List = xPathFindElements("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[3]/div[2]/div[3]/div//button");
                token2List.removeFirst();

                randomWait();
                token2List.get(random.nextInt(token2List.size())).click();

                double count = random.nextDouble(0.0001, 0.001);
                swapCountInput.clear();
                swapCountInput.sendKeys("%.4f".formatted(count));

                randomWait();
                // 点击确认
                xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[6]/button");

                randomWait();
                xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[3]/div[2]/div/button", 120);
                successTimes++;

                todayCount--;
                accountContext.setParam(TODAY_COUNT_KEY, todayCount);
            } catch (Exception e) {
                log.error("{} monad swap error", getInstanceId(), e);
                if (errorTimes++ > 3) {
                    break;
                }
            }
        }

        // 点击返回按钮
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[1]/button");
    }

    private void enterMonadSwapPage(WebDriver webDriver, SeleniumInstance seleniumInstance) {

        // 点击进入legacyPage
        xPathClick("//p[text()='Legacy Wallet']");

        // 点击网络选择按钮
        xPathClick("//div[text()='Sepolia' or text()='Monad Testnet']");

        // 点击选择测试网
        xPathClick("//li[text()='Testnet']");

        // 点击选择Monad
        xPathClick("//p[text()='Monad Testnet']");

        randomWait();

        // 点击swap页面
        xPathClick("//p[text()='Swap']");

        randomWait();
    }

    private void importWallet(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        // 点击导入按钮
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[5]/div[2]/button");

        // 等待输入框出现， 输入钱包
        List<WebElement> inputs = xPathFindElements("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[3]/div//input");
        String[] split = accountContext.getParam(WALLET_KEY).split(" ");
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).sendKeys(split[i]);
        }

        // 点击导入按钮
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[4]/button");

        // 点击导入成功的确认按钮
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/button");

        // 点击跳过按钮
//            xPathClick("//*[@id=\"app-content\"]/div[2]/div[2]/div[3]/button[2]");


        randomWait();
    }


    private void loginAccount(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[3]/button[2]");

        // 找到 email 输入框并输入邮箱
        webDriver.findElement(By.cssSelector("input[type='email']")).sendKeys(accountContext.getParam(USERNAME_KEY));
        // 找到 password 输入框并输入密码
        webDriver.findElement(By.cssSelector("input[type='password']")).sendKeys(accountContext.getParam(PASSWORD_KEY));
        // 点击登录按钮
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[3]/div[3]/button[1]");


        // 输入解锁密码
        xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[3]/div[1]/input").sendKeys("123456789");
        // 输入解锁密码
        xPathFindElement("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[3]/div[2]/input").sendKeys("123456789");
        // 确认
        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/div[2]/div[4]/button");


        xPathClick("//*[@id=\"app-content\"]/div/div[2]/div[2]/label/input");
    }


    private void changeToTargetPage(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        // 获取所有窗口句柄
        Set<String> handles = webDriver.getWindowHandles();
        for (String handle : handles) {
            webDriver.switchTo().window(handle);
            if (Objects.equals(webDriver.getCurrentUrl(), "data:,")) {
                webDriver.close(); // 关闭 data:, 页面
                break;
            }
        }

        // 切换到第二个标签页（索引 1）
        List<String> windowList = new ArrayList<>(webDriver.getWindowHandles());
        webDriver.switchTo().window(windowList.getFirst());
    }

    private void proxyVerify(WebDriver webDriver, SeleniumInstance instance) {
        // 使用 Robot 模拟输入用户名和密码
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
        robot.delay(10000); // 等待弹框出现

        // 输入用户名
        for (char c : instance.getProxy().getUsername().toCharArray()) {
            robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(c));
            robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(c));
        }

        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);

        // 输入密码
        for (char c : instance.getProxy().getPassword().toCharArray()) {
            robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(c));
            robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(c));
        }
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }

    @NotNull
    private static SeleniumParams getParams(AccountContext accountContext) {
        return SeleniumParams
                .builder()
                .driverPath(CHROME_DRIVER_PATH)
                .targetWebSite("chrome-extension://andhndehpcjpmneneealacgnmealilal/popup.html")
                .extensionPaths(List.of(HAHA_WALLET_EXTENSION_CRX_PATH))
                .chromeOptions(List.of("user-agent=" + accountContext.getBrowserEnv().getUserAgent()))
                .build();
    }
}
