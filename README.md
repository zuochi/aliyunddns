# 阿里云简单的DDNS

## 特点：
* 跟别人简单的脚本对比，逻辑比较多，而且健壮性较强。
* 支持远程文件获取获取公网IP的网站。
* 抓取IP的时候会有重试机制，若抓取3次仍然失败，则会把失效的网站会从临时列表中移除。
* 若发现目前使用的抓取网站均失效，会对抓取网站重新加载（内置+远程）。
* 每对IP检查6次后会重新到阿里云加载目前的解析IP。
* 若检查到公网IP变更，更新的时候会检查阿里云当前的解析IP，若相同则不更新。
* 只对 Type 为 ipv4 的记录生效。

## 使用方法：
* 修改 conf.properties 文件
* 运行命令 mvn clean assembly:assembly
* 找到target目录下的 aliyunddns-jar-with-dependencies.jar
* 执行命令 nohup java -Xmx5m -Xms5m -jar aliyunddns-jar-with-dependencies.jar > aliyunddns.log &

## conf.properties说明
* app.update.domain.name 域名名称，如 baidu.com
* app.update.domain.regionId  对应阿里云的访问机房节点ID 
* app.update.domain.accessKeyId  阿里云accessKey 
* app.update.domain.accessKeySecret  阿里云accessKeySecret 
* app.update.domain.check.ip.interval 检查公网ip间隔 
* app.getip.urls 内置抓取公网IP的网站，多个用英文都好隔开（但会优先使用 app.getip.urls.raw 中的网站
* app.getip.urls.raw 远程接口/文件（支持读github文件），抓取公网IP的网站，需要的返回格式为字符串数组，如： ["http://123","https://abc"] 