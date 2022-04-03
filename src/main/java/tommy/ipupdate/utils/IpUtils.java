package tommy.ipupdate.utils;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @version 2018/5/17.
 */
public class IpUtils {

    private static final Pattern ipPatternA = Pattern.compile("^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\:\\d{1,5}$");

    private static final Pattern ipPatternB = Pattern.compile("((2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\.){3}(2[0-4]\\d|25[0-5]|[01]?\\d\\d?)");

    /**
     * 校验是否合法的IP带端口的格式
     * @param ip 要校验的IP带端口
     * @return 合法返回 true, 否则返回 false
     */
    public static boolean checkIpWithPort(String ip) {
        return ipPatternA.matcher(ip).matches();
    }

    /**
     * 获取字符串中的第一个IP
     * @param ipStr
     * @return
     */
    public static String getFirstIp(String ipStr) {
        Matcher m = ipPatternB.matcher(ipStr);
        while (m.find()) {
            return m.group();
        }
        return null;
    }

    /*
     * 随机生成国内IP地址
     */
    public static String getRandomIp() {

        // ip范围
        int[][] range = { { 607649792, 608174079 },// 36.56.0.0-36.63.255.255
                { 1038614528, 1039007743 },// 61.232.0.0-61.237.255.255
                { 1783627776, 1784676351 },// 106.80.0.0-106.95.255.255
                { 2035023872, 2035154943 },// 121.76.0.0-121.77.255.255
                { 2078801920, 2079064063 },// 123.232.0.0-123.235.255.255
                { -1950089216, -1948778497 },// 139.196.0.0-139.215.255.255
                { -1425539072, -1425014785 },// 171.8.0.0-171.15.255.255
                { -1236271104, -1235419137 },// 182.80.0.0-182.92.255.255
                { -770113536, -768606209 },// 210.25.0.0-210.47.255.255
                { -569376768, -564133889 }, // 222.16.0.0-222.95.255.255
        };

        Random rdint = new Random();
        int index = rdint.nextInt(10);
        return num2ip(range[index][0] + new Random().nextInt(range[index][1] - range[index][0]));
    }

    /*
     * 将十进制转换成ip地址
     */
    public static String num2ip(int ip) {
        int[] b = new int[4];
        b[0] = (ip >> 24) & 0xff;
        b[1] = (ip >> 16) & 0xff;
        b[2] = (ip >> 8) & 0xff;
        b[3] = ip & 0xff;
        return b[0] + "." + b[1] + "." + b[2] + "." + b[3];
    }
}
