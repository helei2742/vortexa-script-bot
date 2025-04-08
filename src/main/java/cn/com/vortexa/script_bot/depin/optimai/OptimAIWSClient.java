package cn.com.vortexa.script_bot.depin.optimai;

import com.alibaba.fastjson.JSONObject;

import cn.com.vortexa.script_node.websocket.BotJsonWSClient;
import cn.com.vortexa.common.entity.AccountContext;

/**
 * @author helei
 * @since 2025/3/24 17:04
 */
public class OptimAIWSClient extends BotJsonWSClient {

    private final OptimAIBot optimAIBot;

    public OptimAIWSClient(OptimAIBot optimAIBot, AccountContext accountContext, String connectUrl) {
        super(accountContext, connectUrl);
        this.optimAIBot = optimAIBot;
    }

    @Override
    public JSONObject getHeartbeatMessage() {
        return new JSONObject();
    }

    @Override
    public void whenAccountReceiveResponse(Object id, JSONObject response) {

    }

    @Override
    public void whenAccountReceiveMessage(JSONObject message) {

    }
}
