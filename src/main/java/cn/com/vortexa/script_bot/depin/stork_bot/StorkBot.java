package cn.com.vortexa.script_bot.depin.stork_bot;

import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.util.aws.AWSAuthSRPFLOWClient;
import cn.com.vortexa.common.util.aws.AwsToken;
import cn.com.vortexa.mail.constants.MailProtocolType;
import cn.com.vortexa.mail.factory.MailReaderFactory;
import cn.com.vortexa.mail.reader.MailReader;
import cn.com.vortexa.script_node.anno.BotApplication;
import cn.com.vortexa.script_node.anno.BotMethod;
import cn.com.vortexa.script_node.bot.AutoLaunchBot;
import cn.com.vortexa.common.dto.config.AutoBotConfig;
import cn.com.vortexa.script_node.service.BotApi;
import cn.com.vortexa.common.constants.BotJobType;
import cn.com.vortexa.common.dto.Result;
import cn.com.vortexa.common.entity.AccountContext;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.com.vortexa.script_bot.depin.stork_bot.StorkBot.StorkBotAPI.PASSWORD_KEY;


@BotApplication(name = "stork_bot", accountParams = PASSWORD_KEY)
public class StorkBot extends AutoLaunchBot<StorkBot> {


    private StorkBotAPI storkBotAPI;

    @Override
    protected void botInitialized(AutoBotConfig botConfig, BotApi botApi) {
        storkBotAPI = new StorkBotAPI(this);
    }

    @Override
    protected StorkBot getInstance() {
        return this;
    }

    @BotMethod(jobType = BotJobType.REGISTER)
    public Result signUp(AccountContext exampleAC, List<AccountContext> sameABIList, String inviteCode) {
        return storkBotAPI.signup(exampleAC, sameABIList, inviteCode);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 20)
    public void tokenRefresh(AccountContext accountContext) {
        storkBotAPI.refreshToken(accountContext);
    }

    @BotMethod(jobType = BotJobType.TIMED_TASK, intervalInSecond = 60 * 5)
    public void keepAlive(AccountContext accountContext) {
        storkBotAPI.keepAlive(accountContext);
    }

    @Slf4j
    static class StorkBotAPI {

        private static final String AWS_CLIENT_ID = "5msns4n49hmg3dftp2tp1t2iuh";

        private static final String AWS_URL = "https://cognito-idp.ap-northeast-1.amazonaws.com/";

        private static final String AWS_REFRESH_URL = "https://stork-prod-apps.auth.ap-northeast-1.amazoncognito.com/oauth2/token";

        private static final String AWS_USER_POOL_ID = "ap-northeast-1_M22I44OpC";

        private static final String STORK_SIGNED_PRICE_API = "https://app-api.jp.stork-oracle.network/v1/stork_signed_prices";

        private static final String VALIDATE_SIGNED_PRICE_API = "https://app-api.jp.stork-oracle.network/v1/stork_signed_prices/validations";

        private static final String MAIL_FROM = "noreply@stork.network";

        private static final Pattern V_CODE_PATTERN = Pattern.compile("\\b\\d{6}\\b");

        public static final String PASSWORD_KEY = "stork_password";

        public static final String IMAP_PASSWORD_KEY = "imap_password";

        public static final String AWS_TOKEN_KEY = "aws_token";

        private static final MailReader mailReader = MailReaderFactory.getMailReader(MailProtocolType.imap,
                "imap.gmail.com", "993", true);

        private static final ConcurrentHashMap<AccountContext, AWSAuthSRPFLOWClient> acAWSMap = new ConcurrentHashMap<>();

        private final StorkBot bot;

        public StorkBotAPI(StorkBot bot) {
            this.bot = bot;
        }

        /**
         * 注册
         *
         * @param exampleAC     exampleAC
         * @param sameABIACList sameABIACList
         * @param inviteCode    inviteCode
         * @return Result
         */
        public Result signup(AccountContext exampleAC, List<AccountContext> sameABIACList, String inviteCode) {
            bot.logger.info("%s start signup".formatted(exampleAC.getSimpleInfo()));

            CompletableFuture<String> signupFuture = sendSignUpRequest(exampleAC, inviteCode)
                    .thenApplyAsync(responseStr -> {
                        bot.logger.info("%s signup request sent, %s".formatted(exampleAC.getSimpleInfo(), responseStr));
                        try {
                            TimeUnit.SECONDS.sleep(0);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return queryCheckCode(exampleAC);
                    })
                    .thenApplyAsync(checkCode -> confirmSignup(exampleAC, checkCode));

            try {
                String confirmResponse = signupFuture.get();
                bot.logger.info("%s sign up finish, %s".formatted(exampleAC.getSimpleInfo(), confirmResponse));
                for (AccountContext accountContext : sameABIACList) {
                    AccountContext.signUpSuccess(accountContext);
                }
                return Result.ok();
            } catch (InterruptedException | ExecutionException e) {
                String errorMsg = "%s signup error, %s".formatted(exampleAC.getSimpleInfo(),
                        e.getCause() == null ? e.getCause().getMessage() : e.getMessage());
                bot.logger.error(errorMsg, e);
                return Result.fail(errorMsg);
            }
        }


        /**
         * 刷新token
         *
         * @param accountContext accountContext
         */
        public void refreshToken(AccountContext accountContext) {
            acAWSMap.compute(accountContext, (k, client) -> {
                if (client == null) {
                    client = new AWSAuthSRPFLOWClient(AWS_URL, AWS_REFRESH_URL, AWS_USER_POOL_ID, AWS_CLIENT_ID);
                }

                Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
                headers.put("origin", "https://app.stork.network");
                headers.put("referer", "https://app.stork.network/");

                AwsToken awsToken = JSONObject.parseObject(accountContext.getParam(AWS_TOKEN_KEY), AwsToken.class);
                if (awsToken == null) {
                    // 登录获取refresh token
                    bot.logger.info(accountContext.getSimpleInfo() + " first login, query refresh token...");


                    awsToken = client.userSrpLogin(
                            accountContext.getProxy(),
                            accountContext.getAccountBaseInfo().getEmail(),
                            accountContext.getParam(PASSWORD_KEY),
                            headers
                    );
                } else {
                    // 刷新token
                    awsToken = client.refreshToken(
                            accountContext.getProxy(),
                            awsToken,
                            headers
                    );
                }

                accountContext.setParam(AWS_TOKEN_KEY, JSONObject.toJSONString(awsToken));

                bot.logger.info(accountContext.getSimpleInfo() + " token refresh finish...");

                return client;
            });
        }


        /**
         * keepAlive
         *
         * @param accountContext accountContext
         */
        public void keepAlive(AccountContext accountContext) {
            try {
                AwsToken token = JSONObject.parseObject(accountContext.getParam(AWS_TOKEN_KEY), AwsToken.class);

                if (token == null) {
                    bot.logger.warn(accountContext.getSimpleInfo() + " token is null, skip it");
                    return;
                }

                String msgHash = getSignedPrice(accountContext, token).get();

                String response = validateSignedPrice(accountContext, token, msgHash).get();

                bot.logger.info(accountContext + "%s keep alive success, " + response);
            } catch (InterruptedException | ExecutionException e) {
                bot.logger.error(accountContext + " keep alive error " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
            }
        }


        private CompletableFuture<String> validateSignedPrice(AccountContext accountContext, AwsToken token, String msgHash) {
            bot.logger.debug(accountContext.getSimpleInfo() + " start validate signed price ");

            Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
            headers.put("Authorization", token.getAuthorization());

            JSONObject body = new JSONObject();
            body.put("msg_hash", msgHash);
            body.put("valid", true);

            return bot.syncRequest(
                    accountContext.getProxy(),
                    VALIDATE_SIGNED_PRICE_API,
                    HttpMethod.POST,
                    headers,
                    null,
                    body
            );
        }


        private CompletableFuture<String> getSignedPrice(AccountContext accountContext, AwsToken token) {
            bot.logger.debug(accountContext.getSimpleInfo() + " start get signed price ");


            Map<String, String> headers = accountContext.getBrowserEnv().generateHeaders();
            headers.put("Authorization", token.getAuthorization());

            return bot.syncRequest(
                    accountContext.getProxy(),
                    STORK_SIGNED_PRICE_API,
                    HttpMethod.GET,
                    headers,
                    null,
                    null
            ).thenApplyAsync(responseStr -> {
                JSONObject signedPrices = JSONObject.parseObject(responseStr);
                bot.logger.debug(accountContext.getSimpleInfo() + " signed price get success");

                JSONObject prices = signedPrices.getJSONObject("data");
                for (String symbol : prices.keySet()) {
                    JSONObject price = prices.getJSONObject(symbol);
                    JSONObject timestampedSignature = price.getJSONObject("timestamped_signature");
                    if (timestampedSignature != null) {
                        return timestampedSignature.getString("msg_hash");
                    }
                }

                throw new RuntimeException("signed price is empty");
            });
        }


        private String confirmSignup(AccountContext exampleAC, String checkCode) {
            bot.logger.info(exampleAC.getSimpleInfo() + " check code is " + checkCode + " start confirm sign up");

            Map<String, String> headers = exampleAC.getBrowserEnv().generateHeaders();
            headers.put("X-Amz-Target", "AWSCognitoIdentityProviderService.ConfirmSignUp");

            headers.put("accept", "*/*");
            headers.put("x-amz-user-agent", "aws-amplify/6.12.1 auth/3 framework/2 Authenticator ui-react/6.9.0");
            headers.put("content-type", "x-amz-json-1.1");
            headers.put("origin", "https://app.stork.network");
            headers.put("referer", "https://app.stork.network/");

            JSONObject body = new JSONObject();
            body.put("Username", exampleAC.getAccountBaseInfo().getEmail());
            body.put("ConfirmationCode", checkCode);
            body.put("ClientId", AWS_CLIENT_ID);

            CompletableFuture<String> future = bot.syncRequest(
                    exampleAC.getProxy(),
                    AWS_URL,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> exampleAC.getSimpleInfo() + " confirm sign up"
            );
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("confirm sign up error, %s".formatted(e.getCause() == null ? e.getCause().getMessage() : e.getMessage()));
            }
        }


        /**
         * Step 2 从邮箱获取验证码
         *
         * @param exampleAC exampleAC
         * @return String
         */
        private @NotNull String queryCheckCode(AccountContext exampleAC) {
            bot.logger.info(exampleAC.getSimpleInfo() + " start query check code");

            String email = exampleAC.getAccountBaseInfo().getEmail();
            String imapPassword = (String) exampleAC.getAccountBaseInfo().getParams().get(IMAP_PASSWORD_KEY);

            AtomicReference<String> checkCode = new AtomicReference<>();
            mailReader.stoppableReadMessage(email, imapPassword, 3, message -> {
                try {
                    String newValue = resolveVerifierCodeFromMessage(message);
                    checkCode.set(newValue);
                    return StrUtil.isNotBlank(newValue);
                } catch (MessagingException e) {
                    throw new RuntimeException("email check code query error", e);
                }
            });

            if (StrUtil.isBlank(checkCode.get())) {
                throw new RuntimeException("check code is empty");
            }

            return checkCode.get();
        }


        private CompletableFuture<String> sendSignUpRequest(AccountContext exampleAC, String inviteCode) {
            Map<String, String> headers = exampleAC.getBrowserEnv().generateHeaders();
            headers.put("x-Amz-Target", "AWSCognitoIdentityProviderService.SignUp");
            headers.put("accept", "*/*");
            headers.put("x-amz-user-agent", "aws-amplify/6.12.1 auth/3 framework/2 Authenticator ui-react/6.9.0");
            headers.put("content-type", "x-amz-json-1.1");
            headers.put("origin", "https://app.stork.network");
            headers.put("referer", "https://app.stork.network/");

            JSONObject body = generateSignupBody(exampleAC, inviteCode);

            // Step 1 注册请求
            return bot.syncRequest(
                    exampleAC.getProxy(),
                    AWS_URL,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> exampleAC.getSimpleInfo() + " send sign up request"
            ).exceptionallyAsync(throwable -> {
                bot.logger.warn(exampleAC.getSimpleInfo() + " send sign up request error " +
                        throwable.getMessage() + " try resend");
                try {
                    return resendSignUpCode(exampleAC).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException("resend sign up error, %s".formatted(throwable.getMessage()));
                }
            });
        }

        private CompletableFuture<String> resendSignUpCode(AccountContext exampleAC) {
            Map<String, String> headers = exampleAC.getBrowserEnv().generateHeaders();
            headers.put("x-Amz-Target", "AWSCognitoIdentityProviderService.ResendConfirmationCode");
            headers.put("accept", "*/*");
            headers.put("x-amz-user-agent", "aws-amplify/6.12.1 auth/3 framework/2 Authenticator ui-react/6.9.0");
            headers.put("content-type", "x-amz-json-1.1");
            headers.put("origin", "https://app.stork.network");
            headers.put("referer", "https://app.stork.network/");

            JSONObject body = new JSONObject();
            body.put("ClientId", AWS_CLIENT_ID);
            body.put("Username", exampleAC.getAccountBaseInfo().getEmail());

            // Step 1 注册请求
            return bot.syncRequest(
                    exampleAC.getProxy(),
                    AWS_URL,
                    HttpMethod.POST,
                    headers,
                    null,
                    body,
                    () -> exampleAC.getSimpleInfo() + " resend sign up request"
            );
        }

        private String resolveVerifierCodeFromMessage(Message message) throws MessagingException {
            boolean b = Arrays.stream(message.getFrom())
                    .anyMatch(address -> address.toString().contains(MAIL_FROM));
            if (!b) return null;

            String context = MailReader.getTextFromMessage(message);
            Matcher matcher = V_CODE_PATTERN.matcher(context);

            if (matcher.find()) {
                return matcher.group();
            }
            return null;
        }

        private static @NotNull JSONObject generateSignupBody(AccountContext exampleAC, String inviteCode) {
            JSONObject body = new JSONObject();

            String email = exampleAC.getAccountBaseInfo().getEmail();
            body.put("Username", email);
            body.put("Password", exampleAC.getParam(PASSWORD_KEY));
            body.put("ClientId", AWS_CLIENT_ID);

            JSONArray userAtributes = new JSONArray();
            JSONObject ua1 = new JSONObject();
            ua1.put("Name", "email");
            ua1.put("Value", email);
            JSONObject ua2 = new JSONObject();
            ua2.put("Name", "custom:referral_code");
            ua2.put("Value", inviteCode);
            userAtributes.add(ua1);
            userAtributes.add(ua2);
            body.put("UserAttributes", userAtributes);
            return body;
        }
    }
}
