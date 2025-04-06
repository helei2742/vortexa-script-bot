package cn.com.vortexa.script_bot.daily.magic_newton;

import cn.com.vortexa.browser_control.OptSeleniumInstance;
import cn.com.vortexa.browser_control.SeleniumInstance;
import cn.com.vortexa.browser_control.dto.SeleniumParams;
import cn.com.vortexa.browser_control.execute.ExecuteGroup;
import cn.com.vortexa.browser_control.execute.ExecuteItem;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author helei
 * @since 2025-04-05
 */
@Slf4j
public class MagicNewtonSelenium extends OptSeleniumInstance {

    private static final Pattern countPattern = Pattern.compile("(\\d+)/(\\d+)");

    private static final int targetBoom = 10;

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
//        addExecuteFun(ExecuteGroup
//                .builder().name("摇筛子").enterCondition((webDriver, params) -> {
//                    try {
//                        TimeUnit.SECONDS.sleep(5);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                    return true;
//                })
//                .executeItems(List.of(
//                        ExecuteItem.builder().name("进入摇骰子界面").executeLogic(this::enterDice).build()
//                ))
//                .build()
//        ).
        addExecuteFun(ExecuteGroup
                .builder().name("扫雷").enterCondition((webDriver, params) -> {
                    webDriver.get(getParams().getTargetWebSite());
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
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

        try {
            xPathClick("//p[text()='Let's roll']", 10);
            xPathClick("//p[text()='Throw Dice']");
        } catch (Exception e) {
            log.warn(getInstanceId() + " cannot dice");
        }
    }

    private void enterScanBoom(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        randomWait();
        randomWait();
        xPathClick("//p[text()='Play now']", 10);
        xPathClick("//div[text()='Continue']", 10);
    }

    private void scanBoomProcess(WebDriver webDriver, SeleniumInstance seleniumInstance) {
        String msInfo = xPathFindElement("//div[@class=\"ms-info\"]").getText();
        Matcher matcher = countPattern.matcher(msInfo);
        int current = 0;
        int total = 0;
        if (matcher.find()) {
            current = Integer.parseInt(matcher.group(1));
            total = Integer.parseInt(matcher.group(2));
        }
        log.info(getInstanceId() + " scan boom [%s/%s]".formatted(current, total));
        if (total == current) {
            log.warn(getInstanceId() + " count limit");
            return;
        }
        // 扫雷
        playGame();
    }

    private void playGame() {
        Actions actions = new Actions(getWebDriver());

        int playLimit = 3;
        int playCount = 0;
        int scanCount = 0;
        Set<Integer> excludeIndex = new HashSet<>() {};
        while (true) {
            Map<Integer, WebElement> index2ElementMap = new HashMap<>();
            List<WebElement> rowsElement = xPathFindElements("//div[@class=\"fPSBzf bYPztT dKLBtz cMGtQw gamecol\"]");
            List<List<Integer>> map = new ArrayList<>(rowsElement.size());

            Set<Integer> knownIndex = new HashSet<>() {};

            for (int x = 0; x < rowsElement.size(); x++) {
                List<WebElement> col = rowsElement.get(x).findElements(
                        By.xpath("./div/div")
                );
                List<Integer> line = new ArrayList<>(col.size());
                for (int y = 0; y < col.size(); y++) {
                    WebElement item = col.get(y);
                    int index = x * col.size() + y;
                    index2ElementMap.put(index, item);
                    String text = item.getText().trim();
                    String style = item.getDomAttribute("style");
                    String divClass = item.getDomAttribute("class");

                    if(excludeIndex.contains(index)){
                        line.add(-1);
                    } else if (!text.isEmpty() && text.matches("\\d+")) {
                        line.add(Integer.parseInt(text));
                    } else if (style != null && style.contains("background-color: transparent")
                            && style.contains("border: none")
                            && style.contains("box-shadow: none")
                            && style.contains("color: white")) {
                        line.add(0);
                    } else if (divClass != null && divClass.contains("tile-flagged")) {
                        line.add(-1);
                    } else {
                        knownIndex.add(index);
                        line.add(null);
                    }
                }
                map.add(line);
            }

            Map<String, Set<MinesweeperSolver.Pos>> result = MinesweeperSolver.solve(map);
            Set<MinesweeperSolver.Pos> toClick = result.get("click");
            Set<MinesweeperSolver.Pos> boom = result.get("boom");

            log.info(getInstanceId() + " [%s] scan count[%s]. map resolve finish :\n click[%s] boom[%s]\n%s".formatted(
                    playCount, scanCount, toClick.size(), boom.size(), printMap(map)
            ));

            // 右击炸弹
            for (MinesweeperSolver.Pos pos : boom) {
                int index = pos.row * map.getFirst().size() + pos.col;
                excludeIndex.add(index);
                actions.contextClick(index2ElementMap.get(index)).perform();
            }

            // 点击可点击区域
            for (MinesweeperSolver.Pos pos : toClick) {
                int index = pos.row * map.getFirst().size() + pos.col;
                index2ElementMap.get(index).click();
            }

            if (toClick.isEmpty() && boom.isEmpty()) {
                List<Integer> list = knownIndex.stream().filter(i->!excludeIndex.contains(i)).toList();
                index2ElementMap.get(list.get(getRandom().nextInt(0, list.size()))).click();
            }

            try {
                WebElement returnHome = getWebDriver().findElement(By.xpath("//div[text()='Play Again']"));
                returnHome.click();
                playCount++;
                scanCount = 0;
                excludeIndex.clear();
                if (playCount > playLimit) {
                    return;
                }
            } catch (Exception e) {
                try {
                    WebElement returnHome = getWebDriver().findElement(By.xpath("//div[text()='Return Home']"));
                    returnHome.click();
                    // 没有再来一次，只有返回
                    return;
                } catch (Exception e2) {
                    scanCount++;
                    log.warn(getInstanceId() + " [%s] scan count[%s] next epoch......".formatted(
                            playCount, scanCount
                    ));
                }
            }
        }
    }

    private String printMap(List<List<Integer>> map) {
        StringBuilder sb = new StringBuilder();
        for (List<Integer> integers : map) {
            sb.append(integers).append('\n');
        }
        return sb.toString();
    }
}
