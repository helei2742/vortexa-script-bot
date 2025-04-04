package cn.com.vortexa.script_bot.daily.parasail;

import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.web3.EthWalletUtil;
import com.alibaba.fastjson.JSONObject;

import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author helei
 * @since 2025/3/28 11:13
 */
@BotApplication(
        name = "parasail_bot",
        configParams = {MapConfigKey.INVITE_CODE_KEY},
        accountParams = {ParasailBot.PRIMARY_KEY}
)
public class ParasailBot extends AutoLaunchBot<ParasailBot> {
    public static final String BASE_URL = "https://www.parasail.network/api";
    public static final String PRIMARY_KEY = "primary_key";
    private static final Logger log = LoggerFactory.getLogger(ParasailBot.class);

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {

    }

    @Override
    protected ParasailBot getInstance() {
        return null;
    }

    @BotMethod(jobType = BotJobType.LOGIN)
    public Result signIn(AccountContext accountContext) {
        try {
            logger.info(accountContext.getSimpleInfo() + " start sign in...");
            String token = verifyUser(accountContext).get();
            accountContext.setParam(MapConfigKey.TOKEN_KEY, token);

            logger.info(accountContext.getSimpleInfo() + " sign in success ,token" + token);
            return Result.ok();
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = accountContext.getSimpleInfo() + " sign in error, "
                    + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            logger.error(errorMsg);

            return Result.fail(errorMsg);
        }
    }

    public CompletableFuture<String> verifyUser(AccountContext accountContext) {
        logger.debug("start signature verify message");
        return generateSignature(accountContext.getParam(PRIMARY_KEY))
                .thenApply(signatureData -> {
                    logger.debug("signature verify message success, send verify request");

                    try {
                        String verifyResponseStr = syncRequest(
                                accountContext.getProxy(),
                                BASE_URL + "/user/verify",
                                HttpMethod.POST,
                                accountContext.getBrowserEnv().generateHeaders(),
                                null,
                                signatureData
                        ).get();

                        JSONObject response = JSONObject.parseObject(verifyResponseStr);
                        return response.getJSONObject("data").getString("token");
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("verifyUserError", e);
                    }
                });
    }

    public CompletableFuture<JSONObject> generateSignature(String primaryKey) {
        String message = """
            By signing this message, you confirm that you agree to the Parasail Terms of Service.

            Parasail (including the Website and Parasail Smart Contracts) is not intended for:
            (a) access and/or use by Excluded Persons;
            (b) access and/or use by any person or entity in, or accessing or using the Website from, an Excluded Jurisdiction.
            
            Excluded Persons are prohibited from accessing and/or using Parasail (including the Website and Parasail Smart Contracts).
            
            For full terms, refer to: https://parasail.network/Parasail_User_Terms.pdf
            """;
        return CompletableFuture.supplyAsync(
                () -> EthWalletUtil.signatureMessage2String(primaryKey, message), getExecutorService()
        ).thenApply(signatureData -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("", signatureData);
            return jsonObject;
        });
    }
}
