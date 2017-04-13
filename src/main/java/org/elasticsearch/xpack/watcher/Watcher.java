package org.elasticsearch.xpack.watcher;

import org.elasticsearch.common.Strings;
import org.elasticsearch.plugins.*;
import org.elasticsearch.common.unit.*;
import org.apache.logging.log4j.*;
import org.elasticsearch.common.settings.*;
import org.elasticsearch.common.io.stream.*;
import org.elasticsearch.cluster.metadata.*;
import org.elasticsearch.cluster.*;
import org.elasticsearch.client.*;
import org.elasticsearch.xpack.support.clock.*;
import org.elasticsearch.xpack.security.*;
import org.elasticsearch.search.*;
import org.elasticsearch.license.*;
import org.elasticsearch.script.*;
import org.elasticsearch.xpack.watcher.transform.script.*;
import org.elasticsearch.xpack.watcher.transform.search.*;
import org.elasticsearch.xpack.watcher.transform.*;
import org.elasticsearch.xpack.common.text.*;
import org.elasticsearch.xpack.notification.email.*;
import org.elasticsearch.xpack.notification.email.attachment.*;
import org.elasticsearch.xpack.watcher.actions.email.*;
import org.elasticsearch.xpack.common.http.*;
import org.elasticsearch.xpack.watcher.actions.webhook.*;
import org.elasticsearch.xpack.watcher.actions.index.*;
import org.elasticsearch.xpack.watcher.actions.logging.*;
import org.elasticsearch.xpack.notification.hipchat.*;
import org.elasticsearch.xpack.watcher.actions.hipchat.*;
import org.elasticsearch.xpack.notification.jira.*;
import org.elasticsearch.xpack.watcher.actions.jira.*;
import org.elasticsearch.xpack.notification.slack.*;
import org.elasticsearch.xpack.watcher.actions.slack.*;
import org.elasticsearch.xpack.notification.pagerduty.*;
import org.elasticsearch.xpack.watcher.actions.pagerduty.*;
import org.elasticsearch.xpack.watcher.actions.syslog.*;
import org.elasticsearch.xpack.watcher.actions.*;
import java.util.stream.*;
import org.elasticsearch.common.inject.*;
import org.elasticsearch.xpack.watcher.watch.*;
import org.elasticsearch.xpack.watcher.client.*;
import org.elasticsearch.xpack.watcher.trigger.*;
import org.elasticsearch.xpack.watcher.trigger.schedule.*;
import org.elasticsearch.xpack.watcher.input.*;
import org.elasticsearch.xpack.watcher.support.*;
import org.elasticsearch.xpack.watcher.execution.*;
import org.elasticsearch.common.util.concurrent.*;
import org.elasticsearch.threadpool.*;
import org.elasticsearch.xpack.watcher.transport.actions.put.*;
import org.elasticsearch.action.*;
import org.elasticsearch.xpack.watcher.transport.actions.delete.*;
import org.elasticsearch.xpack.watcher.transport.actions.get.*;
import org.elasticsearch.xpack.watcher.transport.actions.stats.*;
import org.elasticsearch.xpack.watcher.transport.actions.ack.*;
import org.elasticsearch.xpack.watcher.transport.actions.activate.*;
import org.elasticsearch.xpack.watcher.transport.actions.service.*;
import org.elasticsearch.xpack.watcher.transport.actions.execute.*;
import org.elasticsearch.rest.*;
import org.elasticsearch.xpack.watcher.rest.action.*;
import org.elasticsearch.common.*;
import org.joda.time.*;
import org.elasticsearch.xpack.watcher.history.*;
import org.elasticsearch.common.regex.*;
import java.util.*;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.xpack.watcher.condition.*;
import java.util.function.*;
import org.elasticsearch.xpack.*;
import org.elasticsearch.common.logging.*;

public class Watcher implements ActionPlugin, ScriptPlugin
{
    public static final Setting<String> INDEX_WATCHER_VERSION_SETTING;
    public static final Setting<String> INDEX_WATCHER_TEMPLATE_VERSION_SETTING;
    public static final Setting<Boolean> ENCRYPT_SENSITIVE_DATA_SETTING;
    public static final Setting<TimeValue> MAX_STOP_TIMEOUT_SETTING;
    private static final ScriptContext.Plugin SCRIPT_PLUGIN;
    public static final ScriptContext SCRIPT_CONTEXT;
    private static final Logger logger;
    protected final Settings settings;
    protected final boolean transportClient;
    protected final boolean enabled;
    
    public List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        final List<NamedWriteableRegistry.Entry> entries = new ArrayList<NamedWriteableRegistry.Entry>();
        entries.add(new NamedWriteableRegistry.Entry((Class)MetaData.Custom.class, "watcher", WatcherMetaData::new));
        entries.add(new NamedWriteableRegistry.Entry((Class)NamedDiff.class, "watcher", WatcherMetaData::readDiffFrom));
        return entries;
    }
    
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        final List<NamedXContentRegistry.Entry> entries = new ArrayList<NamedXContentRegistry.Entry>();
        entries.add(new NamedXContentRegistry.Entry((Class)MetaData.Custom.class, new ParseField("watcher", new String[0]), WatcherMetaData::fromXContent));
        return entries;
    }
    
    public Watcher(final Settings settings) {
        this.settings = settings;
        this.transportClient = "transport".equals(settings.get(Client.CLIENT_TYPE_SETTING_S.getKey()));
        this.enabled = (boolean)XPackSettings.WATCHER_ENABLED.get(settings);
        validAutoCreateIndex(settings);
    }
    
    public Collection<Object> createComponents(final Clock clock, final ScriptService scriptService, final InternalClient internalClient, final SearchRequestParsers searchRequestParsers, final XPackLicenseState licenseState, final HttpClient httpClient, final NamedXContentRegistry xContentRegistry, final Collection<Object> components) {
        final Map<String, ConditionFactory> parsers = new HashMap<String, ConditionFactory>();
        parsers.put("always", (c, id, p, upgrade) -> AlwaysCondition.parse(id, p));
        parsers.put("never", (c, id, p, upgrade) -> NeverCondition.parse(id, p));
        parsers.put("array_compare", (c, id, p, upgrade) -> ArrayCompareCondition.parse(c, id, p));
        parsers.put("compare", (c, id, p, upgrade) -> CompareCondition.parse(c, id, p));
        final String defaultLegacyScriptLanguage = ScriptSettings.getLegacyDefaultLang(this.settings);
        parsers.put("script", (c, id, p, upgrade) -> ScriptCondition.parse(scriptService, id, p, upgrade, defaultLegacyScriptLanguage));
        final ConditionRegistry conditionRegistry = new ConditionRegistry(Collections.unmodifiableMap((Map<? extends String, ? extends ConditionFactory>)parsers), clock);
        final Map<String, TransformFactory> transformFactories = new HashMap<String, TransformFactory>();
        transformFactories.put("script", new ScriptTransformFactory(this.settings, scriptService));
        transformFactories.put("search", new SearchTransformFactory(this.settings, internalClient, searchRequestParsers, xContentRegistry, scriptService));
        final TransformRegistry transformRegistry = new TransformRegistry(this.settings, (Map<String, TransformFactory>)Collections.unmodifiableMap((Map<? extends String, ? extends TransformFactory>)transformFactories));
        final Map<String, ActionFactory> actionFactoryMap = new HashMap<String, ActionFactory>();
        final TextTemplateEngine templateEngine = this.getService(TextTemplateEngine.class, components);
        actionFactoryMap.put("email", new EmailActionFactory(this.settings, this.getService(EmailService.class, components), templateEngine, this.getService(EmailAttachmentsParser.class, components)));
        actionFactoryMap.put("webhook", new WebhookActionFactory(this.settings, httpClient, this.getService(HttpRequestTemplate.Parser.class, components), templateEngine));
        actionFactoryMap.put("index", new IndexActionFactory(this.settings, internalClient));
        actionFactoryMap.put("logging", new LoggingActionFactory(this.settings, templateEngine));
        actionFactoryMap.put("hipchat", new HipChatActionFactory(this.settings, templateEngine, this.getService(HipChatService.class, components)));
        actionFactoryMap.put("jira", new JiraActionFactory(this.settings, templateEngine, this.getService(JiraService.class, components)));
        actionFactoryMap.put("slack", new SlackActionFactory(this.settings, templateEngine, this.getService(SlackService.class, components)));
        actionFactoryMap.put("pagerduty", new PagerDutyActionFactory(this.settings, templateEngine, this.getService(PagerDutyService.class, components)));
        actionFactoryMap.put("syslog", new SyslogActionFactory(this.settings, templateEngine));
        final ActionRegistry registry = new ActionRegistry(actionFactoryMap, conditionRegistry, transformRegistry, clock, licenseState);
        return (Collection<Object>)Collections.singleton((Object)registry);
    }
    
    private <T> T getService(final Class<T> serviceClass, final Collection<Object> services) {
        final List<Object> collect = services.stream().filter(o -> o.getClass() == serviceClass).collect((Collector<? super Object, ?, List<Object>>)Collectors.toList());
        if (collect.isEmpty()) {
            throw new IllegalArgumentException("no service for class " + serviceClass.getName());
        }
        if (collect.size() > 1) {
            throw new IllegalArgumentException("more than one service for class " + serviceClass.getName());
        }
        return (T)collect.get(0);
    }
    
    public Collection<Module> nodeModules() {
        final List<Module> modules = new ArrayList<Module>();
        modules.add((Module)new WatcherModule(this.enabled, this.transportClient));
        if (this.enabled && !this.transportClient) {
            modules.add((Module)new WatchModule());
            modules.add((Module)new WatcherClientModule());
            modules.add((Module)new TriggerModule(this.settings));
            modules.add((Module)new ScheduleModule());
            modules.add((Module)new InputModule());
            modules.add((Module)new HistoryModule());
            modules.add((Module)new ExecutionModule());
        }
        return modules;
    }
    
    public Settings additionalSettings() {
        return Settings.EMPTY;
    }
    
    public List<Setting<?>> getSettings() {
        final List<Setting<?>> settings = new ArrayList<Setting<?>>();
        for (final WatcherIndexTemplateRegistry.TemplateConfig templateConfig : WatcherIndexTemplateRegistry.TEMPLATE_CONFIGS) {
            settings.add(templateConfig.getSetting());
        }
        settings.add(Watcher.INDEX_WATCHER_VERSION_SETTING);
        settings.add(Watcher.INDEX_WATCHER_TEMPLATE_VERSION_SETTING);
        settings.add(Watcher.MAX_STOP_TIMEOUT_SETTING);
        settings.add(ExecutionService.DEFAULT_THROTTLE_PERIOD_SETTING);
        settings.add((Setting<?>)Setting.intSetting("xpack.watcher.execution.scroll.size", 0, new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.intSetting("xpack.watcher.watch.scroll.size", 0, new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add(Watcher.ENCRYPT_SENSITIVE_DATA_SETTING);
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.internal.ops.search.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.internal.ops.bulk.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.internal.ops.index.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.actions.index.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.index.rest.direct_access", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.trigger.schedule.engine", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.input.search.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.transform.search.default_timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.trigger.schedule.ticker.tick_interval", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.execution.scroll.timeout", new Setting.Property[] { Setting.Property.NodeScope }));
        settings.add((Setting<?>)Setting.simpleString("xpack.watcher.start_immediately", new Setting.Property[] { Setting.Property.NodeScope }));
        return settings;
    }
    
    public List<ExecutorBuilder<?>> getExecutorBuilders(final Settings settings) {
        if (this.enabled) {
            final FixedExecutorBuilder builder = new FixedExecutorBuilder(settings, "watcher", 5 * EsExecutors.boundedNumberOfProcessors(settings), 1000, "xpack.watcher.thread_pool");
            return Collections.singletonList((ExecutorBuilder<?>)builder);
        }
        return Collections.emptyList();
    }
    
    public List<ActionPlugin.ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        if (!this.enabled) {
            return Collections.emptyList();
        }
        return Arrays.asList(new ActionPlugin.ActionHandler((GenericAction)PutWatchAction.INSTANCE, (Class)TransportPutWatchAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)DeleteWatchAction.INSTANCE, (Class)TransportDeleteWatchAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)GetWatchAction.INSTANCE, (Class)TransportGetWatchAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)WatcherStatsAction.INSTANCE, (Class)TransportWatcherStatsAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)AckWatchAction.INSTANCE, (Class)TransportAckWatchAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)ActivateWatchAction.INSTANCE, (Class)TransportActivateWatchAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)WatcherServiceAction.INSTANCE, (Class)TransportWatcherServiceAction.class, new Class[0]), new ActionPlugin.ActionHandler((GenericAction)ExecuteWatchAction.INSTANCE, (Class)TransportExecuteWatchAction.class, new Class[0]));
    }
    
    public List<Class<? extends RestHandler>> getRestHandlers() {
        if (!this.enabled) {
            return Collections.emptyList();
        }
        return Arrays.asList(RestPutWatchAction.class, RestDeleteWatchAction.class, RestWatcherStatsAction.class, RestGetWatchAction.class, RestWatchServiceAction.class, RestAckWatchAction.class, RestActivateWatchAction.class, RestExecuteWatchAction.class, RestHijackOperationAction.class);
    }
    
    public ScriptContext.Plugin getCustomScriptContexts() {
        return Watcher.SCRIPT_PLUGIN;
    }
    
    static void validAutoCreateIndex(final Settings settings) {
        final String value = settings.get("action.auto_create_index");
        if (value == null) {
            return;
        }
        final String errorMessage = LoggerMessageFormat.format("the [action.auto_create_index] setting value [{}] is too restrictive. disable [action.auto_create_index] or set it to [{}, {}, {}*]", new Object[] { value, ".watches", ".triggered_watches", ".watcher-history-" });
        if (Booleans.isExplicitFalse(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (Booleans.isExplicitTrue(value)) {
            return;
        }
        final String[] matches = Strings.commaDelimitedListToStringArray(value);
        final List<String> indices = new ArrayList<String>();
        indices.add(".watches");
        indices.add(".triggered_watches");
        final DateTime now = new DateTime(DateTimeZone.UTC);
        indices.add(HistoryStore.getHistoryIndexNameForTime(now));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusDays(1)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(1)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(2)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(3)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(4)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(5)));
        indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(6)));
        for (final String index : indices) {
            boolean matched = false;
            for (final String match : matches) {
                final char c = match.charAt(0);
                if (c == '-') {
                    if (Regex.simpleMatch(match.substring(1), index)) {
                        throw new IllegalArgumentException(errorMessage);
                    }
                }
                else if (c == '+') {
                    if (Regex.simpleMatch(match.substring(1), index)) {
                        matched = true;
                        break;
                    }
                }
                else if (Regex.simpleMatch(match, index)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new IllegalArgumentException(errorMessage);
            }
        }
        Watcher.logger.warn("the [action.auto_create_index] setting is configured to be restrictive [{}].  for the next 6 months daily history indices are allowed to be created, but please make sure that any future history indices after 6 months with the pattern [.watcher-history-YYYY.MM.dd] are allowed to be created", (Object)value);
    }
    
    static {
        INDEX_WATCHER_VERSION_SETTING = new Setting("index.xpack.watcher.plugin.version", "", (Function)Function.identity(), new Setting.Property[] { Setting.Property.IndexScope });
        INDEX_WATCHER_TEMPLATE_VERSION_SETTING = new Setting("index.xpack.watcher.template.version", "", (Function)Function.identity(), new Setting.Property[] { Setting.Property.IndexScope });
        ENCRYPT_SENSITIVE_DATA_SETTING = Setting.boolSetting("xpack.watcher.encrypt_sensitive_data", false, new Setting.Property[] { Setting.Property.NodeScope });
        MAX_STOP_TIMEOUT_SETTING = Setting.timeSetting("xpack.watcher.stop.timeout", TimeValue.timeValueSeconds(30L), new Setting.Property[] { Setting.Property.NodeScope });
        SCRIPT_PLUGIN = new ScriptContext.Plugin("xpack", "watch");
        SCRIPT_CONTEXT = Watcher.SCRIPT_PLUGIN::getKey;
        logger = Loggers.getLogger((Class)XPackPlugin.class);
    }
}
