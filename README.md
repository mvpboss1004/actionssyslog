# actionssyslog
actionssyslog is a plugin that serve as an action of Watcher plugin in X-Pack, just like Jira, Webhook, Slack and other actions.
We offer an IntelliJ IDEA projcet. To use SyslogAction (using version 5.2.0 as example):
 1. Extract X-Pack-5.2.0.jar from X-Pack-5.2.0.zip;
 2. Add this jar as dependency in IntelliJ IDEA;
 3. Build the project;
 4. Open X-Pack-5.2.0.jar with ArchieveManager like WinRAR, 7zip;
 5. Copy the generated *.class files into X-Pack-5.2.0.jar according to the path, overwrite the existing ones if needed;
 6. Replace the original jar in ES_HOME/plugins/x-pack with the new X-Pack-5.2.0.jar;
 7. Download [java-syslog-client-1.0.8.jar](http:maven.aliyun.comnexus#nexus-search;quick~java-syslog-client) and copy it to ES_HOME/plugins/x-pack;
 8. Add the following lien to ES_HOME/plugins/x-pack/plugin-security.policy:
   `permission java.net.SocketPermission "localhost:0", "listen,resolve";`
 9. All the master nodes need to do these steps, data nodes don't need to;
 10. Restart your cluseter and enjoy it!
 
Here is a simple example:
```
 PUT _xpackwatcherwatchsyslog_example
{
  "trigger" : {
    "schedule" : { "interval" : "10s" } 
  },
  "actions" : {
    "hello_syslog" : {  
      "syslog" : {
        "app" : "elastic"
        "host" : "127.0.0.1",
        "port" : 514,
        "facility" : "local7",
        "level" : "warning",
        "text" : "executed at {{ctx.execution_time}}" 
      }
    }
  }
```

P.S:
 1. The params of "syslog" used here are right the default value;
 2. "level" can be: EMERGENCY/ALERT/CRITICAL/ERROR/NOTICE/INFORMATIONAL/DEBUG;
 3. "text" is necessary.
 
 If you reads Chinese, please visit http://blog.csdn.net/mvpboss1004/article/details/70158864
