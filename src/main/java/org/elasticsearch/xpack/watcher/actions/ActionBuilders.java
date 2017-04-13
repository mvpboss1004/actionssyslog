package org.elasticsearch.xpack.watcher.actions;

import org.elasticsearch.xpack.notification.email.*;
import org.elasticsearch.xpack.watcher.actions.email.*;
import org.elasticsearch.xpack.watcher.actions.index.*;
import org.elasticsearch.common.collect.*;
import org.elasticsearch.xpack.watcher.actions.jira.*;
import java.util.*;
import org.elasticsearch.xpack.common.http.*;
import org.elasticsearch.xpack.watcher.actions.webhook.*;
import org.elasticsearch.xpack.watcher.actions.logging.*;
import org.elasticsearch.xpack.common.text.*;
import org.elasticsearch.xpack.watcher.actions.hipchat.*;
import org.elasticsearch.xpack.notification.slack.message.*;
import org.elasticsearch.xpack.watcher.actions.slack.*;
import org.elasticsearch.xpack.watcher.actions.pagerduty.*;
import org.elasticsearch.xpack.notification.pagerduty.*;
import org.elasticsearch.xpack.watcher.actions.syslog.*;

public final class ActionBuilders
{
    public static EmailAction.Builder emailAction(final EmailTemplate.Builder email) {
        return emailAction(email.build());
    }
    
    public static EmailAction.Builder emailAction(final EmailTemplate email) {
        return EmailAction.builder(email);
    }
    
    public static IndexAction.Builder indexAction(final String index, final String type) {
        return IndexAction.builder(index, type);
    }
    
    public static JiraAction.Builder jiraAction(final String account, final MapBuilder<String, Object> fields) {
        return jiraAction(account, fields.immutableMap());
    }
    
    public static JiraAction.Builder jiraAction(final String account, final Map<String, Object> fields) {
        return JiraAction.builder(account, fields);
    }
    
    public static WebhookAction.Builder webhookAction(final HttpRequestTemplate.Builder httpRequest) {
        return webhookAction(httpRequest.build());
    }
    
    public static WebhookAction.Builder webhookAction(final HttpRequestTemplate httpRequest) {
        return WebhookAction.builder(httpRequest);
    }
    
    public static LoggingAction.Builder loggingAction(final String text) {
        return loggingAction(new TextTemplate(text));
    }
    
    public static LoggingAction.Builder loggingAction(final TextTemplate text) {
        return LoggingAction.builder(text);
    }
    
    public static HipChatAction.Builder hipchatAction(final String message) {
        return hipchatAction(new TextTemplate(message));
    }
    
    public static HipChatAction.Builder hipchatAction(final String account, final String body) {
        return hipchatAction(account, new TextTemplate(body));
    }
    
    public static HipChatAction.Builder hipchatAction(final TextTemplate body) {
        return hipchatAction(null, body);
    }
    
    public static HipChatAction.Builder hipchatAction(final String account, final TextTemplate body) {
        return HipChatAction.builder(account, body);
    }
    
    public static SlackAction.Builder slackAction(final String account, final SlackMessage.Template.Builder message) {
        return slackAction(account, message.build());
    }
    
    public static SlackAction.Builder slackAction(final String account, final SlackMessage.Template message) {
        return SlackAction.builder(account, message);
    }
    
    public static PagerDutyAction.Builder triggerPagerDutyAction(final String account, final String description) {
        return pagerDutyAction(IncidentEvent.templateBuilder(description).setAccount(account));
    }
    
    public static PagerDutyAction.Builder pagerDutyAction(final IncidentEvent.Template.Builder event) {
        return PagerDutyAction.builder(event.build());
    }
    
    public static PagerDutyAction.Builder pagerDutyAction(final IncidentEvent.Template event) {
        return PagerDutyAction.builder(event);
    }

    public static SyslogAction.Builder syslogAction(final String app, final String host, final int port , final String facility, final String level, final TextTemplate text) {
        return SyslogAction.builder(app, host, port, facility, level, text);
    }
}
