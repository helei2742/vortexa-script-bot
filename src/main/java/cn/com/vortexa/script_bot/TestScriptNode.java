package cn.com.vortexa.script_bot;


import cn.com.vortexa.common.util.BannerUtil;
import cn.com.vortexa.script_node.ScriptNodeApplication;
import org.springframework.boot.SpringApplication;

/**
 * @author helei
 * @since 2025-04-04
 */
public class TestScriptNode {

    public static void main(String[] args) {
        BannerUtil.closeOtherBanner();
        SpringApplication.run(ScriptNodeApplication.class, args);
    }
}
