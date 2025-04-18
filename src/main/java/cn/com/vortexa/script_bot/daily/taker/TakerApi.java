package cn.com.vortexa.script_bot.daily.taker;

import com.alibaba.fastjson.JSONObject;

import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.entity.AccountContext;
import cn.com.vortexa.script_node.constants.MapConfigKey;
import cn.com.vortexa.web3.EthWalletUtil;
import cn.hutool.core.util.StrUtil;

import java.util.Map;
import java.util.concurrent.ExecutionException;


/**
 * @author helei
 * @since 2025/4/18 9:26
 */
public class TakerApi {
    private static final String BASE_URL = "https://sowing-api.taker.xyz";
    private static final String LOGIN_MESSAGE = "Taker quest needs to verify your identity to prevent unauthorized access. Please confirm your sign-in details below:\\n\\naddress: %s\\n\\nNonce: %s";
    private final TakerBot takerBot;

    public TakerApi(TakerBot takerBot) {
        this.takerBot = takerBot;
    }

    /**
     * 自动领
     *
     * @param accountContext    accountContext
     * @throws Exception    Exception
     */
    public void autoClaim(AccountContext accountContext) throws Exception {
        Map<String, String> headers = buildHaader(accountContext);

        String simpleInfo = accountContext.getSimpleInfo();
        String token = login(accountContext);

        if (StrUtil.isBlank(token)) {
            throw new Exception("token is empty");
        }

        headers.put("authorization", "Bearer " + token);
        try {
            String response = takerBot.syncRequest(
                    accountContext.getProxy(),
                    BASE_URL + "/task/signIn?status=true",
                    HttpMethod.GET,
                    headers,
                    null,
                    null,
                    () -> simpleInfo + " send auto claim request.."
            ).get();
            takerBot.logger.info(simpleInfo + " claim success.." + response);
        } catch (InterruptedException | ExecutionException e) {
            throw new Exception("auto claim error", e);
        }
    }

    /**
     * 登录获取token
     *
     * @param accountContext    accountContext
     * @return  token
     * @throws Exception    Exception
     */
    public String login(AccountContext accountContext) throws Exception {
        String primaryKey = accountContext.getParam(MapConfigKey.WALLET_PRIMARY_KEY_KEY);
        if (StrUtil.isBlank(primaryKey)) {
            throw new Exception(accountContext.getId() + " primary key is empty");
        }
        String address = accountContext.getParam(MapConfigKey.WALLET_ADDRESS_KEY, () -> EthWalletUtil.getETHAddress(primaryKey));
        String nonce = generateNonce(accountContext, address);

        String message = LOGIN_MESSAGE.formatted(address, nonce);
        String signature = EthWalletUtil.signatureMessage2String(
                primaryKey,
                message
        );

        JSONObject body = new JSONObject();
        body.put("address", address);
        body.put("signature", signature);
        body.put("message", message);

        JSONObject response = takerBot.syncJSONRequest(
                accountContext.getProxy(),
                BASE_URL + "/wallet/login",
                HttpMethod.POST,
                buildHaader(accountContext),
                null,
                body,
                () -> accountContext.getSimpleInfo() + ": " + address + " send login request.."
        ).get();

        String token = response.getJSONObject("result").getString("token");
        accountContext.setParam(MapConfigKey.TOKEN_KEY, token);

        return token;
    }

    private String generateNonce(AccountContext accountContext, String address) throws Exception {
        JSONObject body = new JSONObject();
        body.put("walletAddress", address);
        try {
            JSONObject response = takerBot.syncJSONRequest(
                    accountContext.getProxy(),
                    BASE_URL + "/wallet/generateNonce",
                    HttpMethod.POST,
                    buildHaader(accountContext),
                    null,
                    body,
                    () -> accountContext.getSimpleInfo() + ": " + address + " send get nonce request.."
            ).get();

            if (response.getInteger("code") == 200) {
                takerBot.logger.info("request nonce success");
                return response.getJSONObject("result").getString("nonce");
            } else {
                throw new Exception("request nonce result exception");
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new Exception("request nonce unknown error", e);
        }
    }

    private Map<String, String> buildHaader(AccountContext ac) {
        Map<String, String> headers = ac.getBrowserEnv().generateHeaders();
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("Referer", "https://sowing.taker.xyz/");
        headers.put("Referrer-Policy", "strict-origin-when-cross-origin");
        return headers;
    }
}
