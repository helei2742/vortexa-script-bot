package cn.com.vortexa.script_bot.depin.optimai;

import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.anno.BotWSMethodConfig;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;

import java.util.List;
import java.util.Map;

/**
 * @author helei
 * @since 2025/3/24 17:14
 */
@BotApplication(name = "optim_ai", configParams = {OptimAIBot.TWO_CAPTCHA_API_KEY})
public class OptimAIBot extends AutoLaunchBot<OptimAIBot> {

    private static final String WS_CONNECT_URL = "wss://ws.optimai.network/?token=%s";
    public static final String TWO_CAPTCHA_API_KEY = "two_captcha_api_key";
    public static final String PASSWORD_KEY = "password";

    private final int WS_RECONNECT_INTERVAL_SECOND = 60 * 60 * 24;

    private OptimAIAPI optimAIAPI;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        this.optimAIAPI = new OptimAIAPI(this);
    }

    @Override
    protected OptimAIBot getInstance() {
        return this;
    }

    @BotMethod(
            jobType = BotJobType.REGISTER
    )
    public Result registry(AccountContext uniAC, List<AccountContext> sameIdACList, String inviteCode) {
        return optimAIAPI.registry(uniAC, inviteCode);
    }

    @BotMethod(
            jobType = BotJobType.LOGIN
    )
    public Result login(AccountContext accountContext) throws Exception {
        return optimAIAPI.login(accountContext);
    }

    @BotMethod(jobType = BotJobType.QUERY_REWARD, concurrentCount = 10)
    public Result queryReword(AccountContext accountContext, List<AccountContext> sameAC) {
        return optimAIAPI.queryReword(accountContext);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 60, concurrentCount = 5)
    public void tokenRefresh(AccountContext accountContext) {
        Result result = optimAIAPI.refreshAccessToken(accountContext);
        if (result.getSuccess()) {
            logger.info(accountContext.getSimpleInfo() + " refresh token success");
        } else {
            logger.error(accountContext.getSimpleInfo() + " refresh token error, " + result.getErrorMsg());
        }
    }

    @BotMethod(
            jobType = BotJobType.WEB_SOCKET_CONNECT,
            intervalInSecond = WS_RECONNECT_INTERVAL_SECOND,
            bowWsConfig = @BotWSMethodConfig(
                    wsUnlimitedRetry = true,
                    isRefreshWSConnection = true,
                    reconnectLimit = -1,
                    heartBeatIntervalSecond = 15 * 60,
                    nioEventLoopGroupThreads = 1,
                    wsConnectCount = 50
            )
    )
    public OptimAIWSClient buildKeepAliveWSClient(AccountContext accountContext) {
        String simpleInfo = accountContext.getSimpleInfo();

        String wsToken = accountContext.getParam(OptimAIAPI.WS_TOKEN);
        if (wsToken == null) {
            logger.warn(simpleInfo + "ws token is empty... try to generate");
            try {
                wsToken = optimAIAPI.registryNode2GetWSToken(accountContext);
                accountContext.setParam(OptimAIAPI.WS_TOKEN, wsToken);
            } catch (Exception e) {
                logger.error(simpleInfo + " generate ws token error", e);
                throw new RuntimeException("generate ws token error");
            }
        }

        OptimAIWSClient client = new OptimAIWSClient(this, accountContext, WS_CONNECT_URL.formatted(wsToken));

        DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry : accountContext.getBrowserEnv().generateHeaders().entrySet()) {
            httpHeaders.add(entry.getKey(), entry.getValue());
        }

        httpHeaders.add("Accept-Language", "en-US,en;q=0.9,id;q=0.8");
        httpHeaders.add("Cache-Control", "no-cache");
        httpHeaders.add("Connection", "Upgrade");
        httpHeaders.add("Host", "ws.optimai.network");
        httpHeaders.add("Origin", "chrome-extension://njlfcjdojmopagogfpjgcbnpmiknapnd");
        httpHeaders.add("Pragma", "no-cache");
        httpHeaders.add("Sec-WebSocket-Extensions", "permessage-deflate; client_max_window_bits");
        httpHeaders.add("Sec-WebSocket-Key", "YlDqUSX4RQ86eTGWUR1Ynw===");
        httpHeaders.add("Sec-WebSocket-Version", "13");
        httpHeaders.add("Upgrade", "websocket");

        client.setHeaders(httpHeaders);

        return client;
    }
}
