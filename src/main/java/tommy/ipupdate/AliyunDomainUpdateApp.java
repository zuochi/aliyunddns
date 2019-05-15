package tommy.ipupdate;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import tommy.ipupdate.utils.HttpUtils;
import tommy.ipupdate.utils.IpUtils;
import tommy.ipupdate.utils.PropertiesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse.*;

@Data
public class AliyunDomainUpdateApp {

    private IAcsClient client;
    private String oldIp;
    private String domainName;
    private String regionId;
    private String accessKeyId;
    private String accessKeySecret;
    private List<String> usefulIpUrls;
    private long ipCheckInterval = Long.valueOf(PropertiesUtils.get("app.update.domain.check.ip.interval"));

    public AliyunDomainUpdateApp(String domainName, String regionId, String accessKeyId, String accessKeySecret) {
        this.domainName = domainName;
        this.regionId = regionId;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;

        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        try {
            this.client = new DefaultAcsClient(profile);
        } catch (Exception e) {
            try {
                DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", "Alidns", "alidns.aliyuncs.com");
                this.client = new DefaultAcsClient(profile);
            } catch (ClientException e1) {
                e1.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws ClientException {

        try {
            AliyunDomainUpdateApp app = new AliyunDomainUpdateApp(
                    PropertiesUtils.get("app.update.domain.name"),
                    PropertiesUtils.get("app.update.domain.regionId"),
                    PropertiesUtils.get("app.update.domain.accessKeyId"),
                    PropertiesUtils.get("app.update.domain.accessKeySecret"));
            app.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute() throws InterruptedException {

        this.updateOldIp();

        int count = 1;
        while(true) {
            String newIp = checkAndReturnNewIp();
            if (StringUtils.isNotBlank(newIp) && IpUtils.checkIpWithPort(String.format("%s:%s", newIp, "8888"))) {
                try {
                    log("检测到IP变更 old：" + oldIp + ", new:" + newIp + ", 开始更新...");
                    this.updateDomainRecord(newIp);
                    this.oldIp = newIp;
                    log("检测到IP变更完毕，新IP：" + newIp);
                } catch (ClientException e) {
                    log("ERROR updateDomainRecord -> " + newIp + " 更新失败: " + e.getMessage());
                }
            }

            // 每进行6次（大约半小时更新一次oldIp，防止人为更新了）
            if (count % 6 == 0) {
                this.updateOldIp();
            }

            count += 1;

            Thread.sleep(ipCheckInterval);
        }
    }

    public void updateOldIp() throws InterruptedException {
        log("从阿里云配置中获取当前域名解析的IP...");
        while(true) {
            try {
                List<Record> domainRecords = this.getDomainRecords();
                if (CollectionUtil.isNotEmpty(domainRecords)) {
                    for (Record domainRecord : domainRecords) {
                        if ("A".equals(domainRecord.getType())) {
                            this.oldIp = domainRecord.getValue();
                            log("获取到阿里云配置中的域名解析IP为：" + this.oldIp);
                            return;
                        }
                    }
                }
                Thread.sleep(3000L);
            } catch (ClientException e) {
                log("ERROR getDomainRecords -> 获取失败: " + e.getMessage());
            }
        }
    }

    public void initUsefulIpUrls() {
        usefulIpUrls = new ArrayList<>();

        String[] getIpUrls = this.getRemoteIpUrls();
        if (getIpUrls == null || getIpUrls.length == 0) {
            String innerIpUrls = PropertiesUtils.get("app.getip.urls");
            log("开始装载内置的 getip.urls：" + innerIpUrls);
            getIpUrls = innerIpUrls.split(",");
        }

        for (String ipUrl : getIpUrls) {
            try {
                if (oldIp.equals(this.getIpByUrl(ipUrl))) {
                    usefulIpUrls.add(ipUrl);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (usefulIpUrls.size() == 0) {
            throw new RuntimeException("找不到有用的IP配置！请重新配置获取IP的地址");
        }
        log("装载有用的IP列表完毕：" + StringUtils.join(this.usefulIpUrls, ","));
    }

    public String getIpByUrl(String ipUrl) {
        // 重试3次
        for (int i=0 ; i<3 ; i++) {
            try {
                String ipStr = HttpRequest.get(ipUrl).addHeaders(ImmutableMap.of(
                        "Accept", "*/*",
                        "Content-Type", "text/plain;charset=UTF-8",
                        "Referer", ipUrl,
                        "User-Agent", HttpUtils.getRandomUA(),
                        "Origin", IpUtils.getRandomIp())).timeout(3000).execute().body();
                return IpUtils.getFirstIp(ipStr);
            } catch (Exception e) {
                log("ERROR getIpByUrl -> " + ipUrl + " 获取数据失败" + (i + 1) + "次: " + e.getMessage());
            }
        }
        return null;
    }

    public void updateDomainRecord(String newIp) throws ClientException {
        List<Record> domainRecords = this.getDomainRecords();
        if (CollectionUtil.isNotEmpty(domainRecords)) {
            for (Record domainRecord : domainRecords) {
                if ("A".equals(domainRecord.getType())) {
                    if (newIp.equals(domainRecord.getValue())) {
                        log("updateDomainRecord 发现阿里解析ip与变更的新ip已经一致，无需更改，更新oldIp -> " + newIp);
                        this.oldIp = newIp;
                    } else {
                        this.updateDomainRecordByDomainRecord(newIp, domainRecord);
                    }
                }
            }
        }
    }

    public String checkAndReturnNewIp() {

        if (CollectionUtil.isEmpty(this.usefulIpUrls)) {
            // 装载有用的IP
            this.initUsefulIpUrls();
        }

        log("开始检查IP是否更新...");
        int min = 0;
        int max = this.usefulIpUrls.size();
        String ipUrl = this.usefulIpUrls.get(new Random().nextInt(max) % (max - min + 1) + min);
        String newIp = this.getIpByUrl(ipUrl);
        if (StringUtils.isBlank(newIp)) {
            log("ip获取失败！移除该获取地址 ipUrl: " + ipUrl + " -> " + newIp);
            usefulIpUrls.remove(ipUrl);
            return null;
        }
        if (!oldIp.equals(newIp)) {
            log("发现IP更新了！！！ ipUrl: " + ipUrl + " -> " + newIp);
            return newIp;
        } else {
            log("IP无需更新 ipUrl: " + ipUrl + " -> " + newIp);
        }
        return null;
    }

    public String[] getRemoteIpUrls() {
        String url = PropertiesUtils.get("app.getip.urls.raw");
        if (StringUtils.isBlank(url)) {
            return null;
        }
        try {
            List<String> list = JSON.parseArray(HttpRequest.get(url).addHeaders(ImmutableMap.of(
                    "Accept", "*/*",
                    "Content-Type", "text/plain;charset=UTF-8",
                    "User-Agent", HttpUtils.getRandomUA(),
                    "Origin", IpUtils.getRandomIp())).timeout(3000).execute().body(), String.class);
            log("发现远程ipUrl配置，加载成功: " + StringUtils.join(list, ","));
            return list.toArray(new String[0]);
        } catch (Exception e) {
            log("ERROR getRemoteIpUrls -> " + url + " 获取数据失败: " + e.getMessage());
        }
        return null;
    }

    public List<Record> getDomainRecords() throws ClientException {
        DescribeDomainRecordsRequest describeDomainRecordsRequest = new DescribeDomainRecordsRequest();
        describeDomainRecordsRequest.setDomainName(this.domainName);
        describeDomainRecordsRequest.setProtocol(ProtocolType.HTTPS); //指定访问协议
        describeDomainRecordsRequest.setAcceptFormat(FormatType.JSON); //指定api返回格式
        describeDomainRecordsRequest.setMethod(MethodType.POST); //指定请求方法
        return this.getClient().getAcsResponse(describeDomainRecordsRequest).getDomainRecords();
    }

    public void updateDomainRecordByDomainRecord(String newIp, Record domainRecord) throws ClientException {
        UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
        updateDomainRecordRequest.setRecordId(domainRecord.getRecordId());
        updateDomainRecordRequest.setRR(domainRecord.getRR());
        updateDomainRecordRequest.setType(domainRecord.getType());
        updateDomainRecordRequest.setValue(newIp);
        updateDomainRecordRequest.setProtocol(ProtocolType.HTTPS); //指定访问协议
        updateDomainRecordRequest.setAcceptFormat(FormatType.JSON); //指定api返回格式
        updateDomainRecordRequest.setMethod(MethodType.POST); //指定请求方法
        UpdateDomainRecordResponse response = this.getClient().getAcsResponse(updateDomainRecordRequest);
        log("updateDomainRecord response:" + JSONObject.toJSONString(response));
    }

    public void log(String msg) {
        System.out.println(new DateTime().toString("yyyy-MM-dd HH:mm:ss") + " " + msg);
    }
}
