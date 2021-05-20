package cn.imzjw.tieba;

import cn.imzjw.tieba.entity.Cookie;
import cn.imzjw.tieba.util.Encryption;
import cn.imzjw.tieba.util.Request;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author https://blog.imzjw.cn
 * @date 2021/1/9 16:22
 */
public class Run {
    /**
     * 获取日志记录器对象
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);
    /**
     * 获取用户所有关注贴吧
     */
    private static final String LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex";
    /**
     * 获取用户的tbs
     */
    private static final String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    /**
     * 贴吧签到接口
     */
    private static final String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";
    /**
     * 存储用户所关注的贴吧
     */
    private static List<String> follow = new ArrayList<>();
    /**
     * 签到成功的贴吧列表
     */
    private static List<String> success = new ArrayList<>();

    private static List<String> all = new ArrayList<>();
    /**
     * 用户的tbs
     */
    private String tbs = "";
    /**
     * 用户所关注的贴吧数量，最大不能超过 201
     */
    private static Integer followNum = 201;

    public static void main(String[] args) {
        Cookie cookie = Cookie.getInstance();
        // 存入Cookie，以备使用
        if (args.length == 0) {
            LOGGER.warn("请在 Secrets 中填写 BDUSS");
        }
        cookie.setBduss(args[0]);
        Run run = new Run();
        run.getTbs();
        run.getFollow();
        run.runSign();
        // LOGGER.info("共 {} 个贴吧 - 成功: {} - 失败: {}", followNum, success.size(), followNum - success.size());
        if (args.length == 3)
            run.sendTg(args[1], args[2]);
    }

    /**
     * 进行登录，获得 tbs ，签到的时候需要用到这个参数
     */
    private void getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                LOGGER.info("获取tbs成功");
                tbs = jsonObject.getString("tbs");
            } else {
                LOGGER.warn("获取tbs失败: " + jsonObject);
            }
        } catch (Exception e) {
            LOGGER.error("获取tbs部分出现错误: " + e);
        }
    }

    /**
     * 获取用户所关注的贴吧列表
     */
    private void getFollow() {
        try {
            JSONObject jsonObject = Request.get(LIKE_URL);
            LOGGER.info("获取贴吧列表成功");
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
            followNum = jsonArray.size();
            // 获取用户所有关注的贴吧
            for (Object array : jsonArray) {
                all.add(((JSONObject) array).getString("forum_name"));
                if ("0".equals(((JSONObject) array).getString("is_sign"))) {
                    // 将未签到的贴吧加入到 follow 中，待签到
                    follow.add(((JSONObject) array).getString("forum_name"));
                } else {
                    // 将已经成功签到的贴吧，加入到 success
                    success.add(((JSONObject) array).getString("forum_name"));
                }
            }
        } catch (Exception e) {
            LOGGER.error("获取贴吧列表部分出现错误: " + e);
        }
    }

    /**
     * 开始进行签到，每一轮性将所有未签到的贴吧进行签到，一共进行 5 轮，如果还未签到完就立即结束
     * 一般一次只会有少数的贴吧未能完成签到，为了减少接口访问次数，每一轮签到完等待 1 分钟，如果在过程中所有贴吧签到完则结束。
     */
    private void runSign() {
        // 当执行 5 轮所有贴吧还未签到成功就结束操作
        Integer flag = 5;
        try {
            while (success.size() < followNum && flag > 0) {
                LOGGER.info("-----第 {} 轮签到开始-----", 5 - flag + 1);
                LOGGER.info("还剩 {} 贴吧需要签到", followNum - success.size());
                Iterator<String> iterator = follow.iterator();
                while (iterator.hasNext()) {
                    String s = iterator.next();
                    String body = "kw=" + s + "&tbs=" + tbs + "&sign=" + Encryption.enCodeMd5("kw=" + s + "tbs=" + tbs + "tiebaclient!!!");
                    JSONObject post = Request.post(SIGN_URL, body);
                    if ("0".equals(post.getString("error_code"))) {
                        iterator.remove();
                        success.add(s);
                        LOGGER.info(s + ": " + "签到成功");
                    } else {
                        LOGGER.warn(s + ": " + "签到失败");
                    }
                }
                if (success.size() != followNum) {
                    // 为防止短时间内多次请求接口，触发风控，设置每一轮签到完等待 5 分钟
                    Thread.sleep(1000 * 60 * 5);
                    // 重新获取 tbs
                    // 尝试解决以前第 1 次签到失败，剩余 4 次循环都会失败的错误。
                    getTbs();
                }
                flag--;
            }
        } catch (Exception e) {
            LOGGER.error("签到部分出现错误: " + e);
        }
    }

    /**
     * 发送签到结果到 Telegram
     *
     * @param botToken tg 机器人 token
     * @param chat_id  聊天 id
     */
    private void sendTg(String botToken, String chat_id) {
        LOGGER.info("开始进行 Telegram 推送");
        String msg = "--------获取贴吧列表 BEGIN--------\n" + all.stream().collect(Collectors.joining("\n"))
                + "\n--------获取贴吧列表 END--------\n";
        msg += "贴吧总计：【" + followNum + "】";
        msg += "\n成功签到：【" + success.size() + "】   失败：【" + (followNum - success.size()) + "】";
        try {
            msg = URLEncoder.encode(msg, "UTF-8");
            Request.sendGet("https://api.telegram.org/bot" + botToken + "/sendMessage", "chat_id=" + chat_id + "&text=" + msg);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("tg 发送失败 -> " + e);
        }
    }

    /**
     * 发送结果到 QQ
     *
     * @param qmsgKey qmsg 酱的 key https://qmsg.zendee.cn
     */
    // private void sendQmsg(String qmsgKey) {
    //     LOGGER.info("开始进行 Qmsg 酱通知");
    //     String msg = "--------获取贴吧列表 BEGIN--------\n" + all.stream().collect(Collectors.joining("\n"))
    //             + "\n--------获取贴吧列表 END--------\n";
    //     msg += "贴吧总计：【" + followNum + "】";
    //     msg += "\n成功签到：【" + success.size() + "】   失败：【" + (followNum - success.size()) + "】";
    //     try {
    //         msg = URLEncoder.encode(msg, "UTF-8");
    //         Request.sendGet("https://qmsg.zendee.cn/send/" + qmsgKey, "msg=" + msg);
    //     } catch (Exception e) {
    //         LOGGER.error("qmsg 酱发送失败 -> " + e);
    //     }
    // }
}
