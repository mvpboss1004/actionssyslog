package org.elasticsearch.xpack.watcher.actions.syslog;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.elasticsearch.xpack.watcher.execution.WatchExecutionContext;
import org.elasticsearch.xpack.watcher.support.Variables;
import org.elasticsearch.xpack.watcher.watch.Payload;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.actions.ExecutableAction;
import org.apache.logging.log4j.*;
import java.util.Map;

/**
 * Created by mvpboss1004 on 2017/4/3.
 */
public class ExecutableSyslogAction extends ExecutableAction<SyslogAction> {
    private final TextTemplateEngine engine;

    public ExecutableSyslogAction(final SyslogAction action, final Logger logger, final TextTemplateEngine templateEngine) {
        super(action, logger);
        this.engine = templateEngine;
    }

    @Override
    public Action.Result execute(final String actionId, final WatchExecutionContext ctx, final Payload payload) throws Exception {
        final Map<String, Object> model = Variables.createCtxModel(ctx, payload);
        final String loggedText = engine.render(action.text, model);
        if (ctx.simulateAction(actionId)) {
            return new SyslogAction.Result.Simulated(loggedText);
        }
        action.send(loggedText);
        return new SyslogAction.Result.Success(loggedText);
    }
}

