package tommy.ipupdate.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author liuzhuofeng tommylau
 * @version 2019/5/15
 */
public class PropertiesUtils {

    private static Properties props = new Properties();;

    static {
        InputStream inStream = PropertiesUtils.class.getClassLoader().getResourceAsStream("conf.properties");
        try {
            props.load(inStream);
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
