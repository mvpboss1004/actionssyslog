package org.elasticsearch.xpack.watcher.actions.syslog;

import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.actions.ActionFactory;
import java.io.IOException;

/**
 * Created by mvpboss1004 on 2017/4/3.
 */

public class SyslogActionFactory extends ActionFactory {
    private final TextTemplateEngine templateEngine;

    public SyslogActionFactory(final Settings settings, final TextTemplateEngine templateEngine) {
        super(Loggers.getLogger(ExecutableSyslogAction.class, settings, new String[0]));
        this.templateEngine = templateEngine;
    }

    @Override
    public ExecutableSyslogAction parseExecutable(final String watchId, final String actionId, final XContentParser parser) throws IOException {
        return new ExecutableSyslogAction(SyslogAction.parse(watchId, actionId, parser), this.actionLogger, this.templateEngine);
    }
}
