package org.elasticsearch.xpack.watcher.actions.syslog;
import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.Severity;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.common.text.TextTemplate;
import org.elasticsearch.xpack.watcher.actions.*;
import java.io.IOException;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;

/**
 * Created by mvpboss1004 on 2017/4/3.
 */
public class SyslogAction implements Action{
    public static final String TYPE = "logging";
    final String app;
    final String host;
    final int port;
    final String facility;
    final String level;
    final TextTemplate text;

    public SyslogAction(final String app, final String host, final int port, final String facility, final String level, final TextTemplate text) {
        this.app = app;
        this.host = host;
        this.port = port;
        this.facility = facility;
        this.level = level;
        this.text = text;
    }

    public void send(final String loggedText) throws Exception {
        UdpSyslogMessageSender sender = AccessController.doPrivileged(new PrivilegedExceptionAction<UdpSyslogMessageSender>() {
            public UdpSyslogMessageSender run() throws Exception {
                return new UdpSyslogMessageSender();
            }
        });
        sender.setDefaultAppName(app);
        sender.setSyslogServerHostname(host);
        sender.setSyslogServerPort(port);
        sender.setDefaultFacility(Facility.fromLabel(facility.toUpperCase()));
        sender.setDefaultSeverity(Severity.fromLabel(level.toUpperCase()));
        sender.sendMessage(loggedText);
    }

    @Override
    public String type() {
        return "syslog";
    }

    @Override
    public boolean equals(final Object o){
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SyslogAction that = (SyslogAction)o;
        return app==that.app && host==that.host && port==that.port && facility==that.facility && level==that.level && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.app, this.host, this.port, this.facility, this.level, this.text);
    }

    public XContentBuilder toXContent(final XContentBuilder builder, final ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(Field.APP.getPreferredName(), app);
        builder.field(Field.HOST.getPreferredName(), host);
        builder.field(Field.PORT.getPreferredName(), port);
        builder.field(Field.FACILITY.getPreferredName(), facility);
        builder.field(Field.LEVEL.getPreferredName(), level);
        builder.field(Field.TEXT.getPreferredName(), text, params);
        return builder.endObject();
    }

    public static Builder builder(final String app, final String host, final int port, final String facility, final String level, final TextTemplate text) {
        return new Builder(new SyslogAction(app, host, port ,facility, level, text));
    }

    public static SyslogAction parse(final String watchId, final String actionId, final XContentParser parser) throws IOException {
        String app = "elastic";
        String host = "localhost";
        int port = 514;
        String facility = "local7";
        String level = "info";
        String currentFieldName = null;
        XContentParser.Token token;
        TextTemplate text = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (Field.APP.match(currentFieldName)) {
                if (token != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "syslog", watchId, actionId, Field.APP.getPreferredName(), token });
                }
                host = parser.text();
            } else if (Field.HOST.match(currentFieldName)) {
                if (token != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "syslog", watchId, actionId, Field.HOST.getPreferredName(), token });
                }
                host = parser.text();
            } else if (Field.PORT.match(currentFieldName)) {
                if (token != XContentParser.Token.VALUE_NUMBER) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type int, but found [{}] instead", new Object[] { "syslog", watchId, actionId, Field.PORT.getPreferredName(), token });
                }
                port = parser.intValue();
            } else if (Field.FACILITY.match(currentFieldName)) {
                if (token != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "syslog", watchId, actionId, Field.FACILITY.getPreferredName(), token });
                }
                facility = parser.text();
            } else if (Field.LEVEL.match(currentFieldName)) {
                if (token != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "syslog", watchId, actionId, Field.LEVEL.getPreferredName(), token });
                }
                level = parser.text();
            } else if (Field.TEXT.match(currentFieldName)) {
                try {
                    text = TextTemplate.parse(parser);
                } catch (ElasticsearchParseException pe) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. failed to parse [{}] field", pe, new Object[]{"syslog", watchId, actionId, Field.TEXT.getPreferredName()});
                }
            }
        }
        return new SyslogAction(app, host, port, facility, level, text);
    }

    interface Field extends Action.Field {
        ParseField APP = new ParseField("app", new String[0]);
        ParseField HOST = new ParseField("host", new String[0]);
        ParseField PORT = new ParseField("port", new String[0]);
        ParseField FACILITY = new ParseField("facility", new String[0]);
        ParseField LEVEL = new ParseField("level", new String[0]);
        ParseField TEXT = new ParseField("text", new String[0]);
        ParseField LOGGED_TEXT = new ParseField("logged_text", new String[0]);
    }

    public static class Builder implements Action.Builder<SyslogAction> {
        final SyslogAction action;
        public Builder(final SyslogAction action) {
            this.action = action;
        }

        @Override
        public SyslogAction build() {
            return action;
        }
    }

    public interface Result {
        class Success extends Action.Result implements SyslogAction.Result {
            private final String loggedText;

            public Success(final String loggedText) {
                super("syslog", Status.SUCCESS);
                this.loggedText = loggedText;
            }

            public String getLoggedText() {
                return loggedText;
            }

            public XContentBuilder toXContent(final XContentBuilder builder, final ToXContent.Params params) throws IOException {
                return builder.startObject(type).field(SyslogAction.Field.LOGGED_TEXT.getPreferredName(), loggedText).endObject();
            }
        }

        class Simulated extends Action.Result implements SyslogAction.Result {
            private final String loggedText;

            public Simulated(final String loggedText) {
                super("syslog", Status.SUCCESS);
                this.loggedText = loggedText;
            }

            public String getLoggedText() {
                return loggedText;
            }

            public XContentBuilder toXContent(final XContentBuilder builder, final ToXContent.Params params) throws IOException {
                return builder.startObject(type).field(SyslogAction.Field.LOGGED_TEXT.getPreferredName(), loggedText).endObject();
            }
        }
    }
}
