package cn.com.vortexa.script_bot.depin.depin_3_dos;

import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.common.exception.BotInitException;
import cn.com.vortexa.common.exception.BotStartException;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@BotApplication(
        name = "three_dos_bot"
)
public class ThreeDosBot extends AutoLaunchBot<ThreeDosBot> {

    private ThreeDosApi threeDosApi;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        this.threeDosApi = new ThreeDosApi(this);
    }

    @Override
    protected ThreeDosBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.REGISTER, jobName = "自动注册")
    public Result autoRegister(AccountContext exampleAC, List<AccountContext> sameABIIdList, String inviteCode) {
        return threeDosApi.register(exampleAC, sameABIIdList, inviteCode);
    }

    @BotMethod(jobType = BotJobType.LOGIN, jobName = "自动获取token")
    public Result login(AccountContext accountContext) {
        return threeDosApi.login(accountContext);
    }

    @BotMethod(jobType = BotJobType.QUERY_REWARD, jobName = "奖励查询", intervalInSecond = 300, uniqueAccount = true)
    public Result queryReward(AccountContext exampleAC, List<AccountContext> sameABIIdList) {
        return threeDosApi.updateAccount(exampleAC, sameABIIdList);
    }

    @BotMethod(jobType = BotJobType.ONCE_TASK, jobName = "重发验证邮件", uniqueAccount = true)
    public void resendEmail(AccountContext exampleAC, List<AccountContext> sameABIIdList) {
        threeDosApi.resendEmail(exampleAC, sameABIIdList);
    }

    @BotMethod(jobType = BotJobType.ONCE_TASK, jobName = "验证邮箱")
    public void checkEmail(AccountContext accountContext) {
        threeDosApi.checkEmail(accountContext);
    }

    @BotMethod(jobType = BotJobType.ONCE_TASK, jobName = "生成秘钥")
    public void generateSecretKey(AccountContext accountContext) {
        threeDosApi.generateSecretKey(accountContext);
    }

    @BotMethod(
            jobType = BotJobType.TIMED_TASK,
            jobName = "每日登录",
            intervalInSecond = 60 * 60 * 12,
            uniqueAccount = true
    )
    public void dailyCheckIn(AccountContext exampleAC, List<AccountContext> sameABIIdList) {
        threeDosApi.dailyCheckIn(exampleAC, sameABIIdList);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, jobName = "自动Ping", intervalInSecond = 60, dynamicTrigger = true, dynamicTimeWindowMinute = 300)
    public void keepAlivePing(AccountContext accountContext) {
        threeDosApi.keepLive(accountContext);
    }


    @BotMethod(jobType = BotJobType.ONCE_TASK)
    public void registerWhiteList(AccountContext accountContext) throws ExecutionException, InterruptedException {
        String ethAddress = accountContext.getParam("eth_address");
        if (StrUtil.isBlank(ethAddress)) return;

        JSONObject body = new JSONObject();
        body.put("email", accountContext.getAccountBaseInfo().getEmail());
        body.put("project_identifier", "dev-inflectiv-ai");
        body.put("wallet_address", ethAddress);
        Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
        headers.put("origin", "https://whitelist.inflectiv.ai");
        headers.put("referer", "https://whitelist.inflectiv.ai/");


       syncRequest(
                accountContext.getProxy(),
                "https://ssrks0qeqf.execute-api.eu-west-2.amazonaws.com/production/whitelist/create",
                HttpMethod.POST,
                headers,
                null,
                body
        ).whenComplete((response, throwable) -> {
            if (throwable != null) {
                logger.error("register white list error", throwable);
            }
            log.info("register white list success, " + response);
       });
    }


    public static void main(String[] args) throws BotStartException, BotInitException {
        List<String> list = new ArrayList<>(List.of(args));

        list.add("--bot.botKey=3Mods-Google");
        list.add("--bot.customConfig.invite_code=WSJQRJD5CB");
        list.add("--bot.accountConfig.configFilePath=3dos/3dos_google.xlsx");
        list.add("--add-opens java.base/java.lang=ALL-UNNAMED");

//        ScriptAppLauncher.launch(ThreeDosBot.class, list.toArray(new String[0]));
    }
}
