package net.tiny.boot;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import net.tiny.config.Configuration;
import net.tiny.config.ConfigurationHandler;
import net.tiny.config.ContextHandler;

/**
 * <p>
 * Help: java net.tiny.boot.Main --help
 * <code>
 *      -p --profile The profile name.
 *      -f --file    Configuration file.
 *                   Default: 'application-{profile}.yml(Support YAML, JSON, HSON and Properties)'
 *      -i --pid     Process id file (/var/run/pid).
 *      -v --verbose On debug mode.
 *      -h --help    This help message.
 * </code>
 * </p>
 * <br/>
 * <p>
 * Configuration file : application-{profile}.[yml, json, conf, properties]
 * <code>
 * main = ${server}
 * shutdown = ${hook}
 * daemon = true
 * callback.class = x.y.ServiceContext
 * executor.class = x.y.ExecutorService
 * server.class = x.y.Launcher
 * hook.class = x.y.ServerShutdown
 * </code>
 * </p>
 */
public class Main {

    protected static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String BOOT_CONFIG_FILENAME = "application";

    private static final String CONFIG_MAIN              = "main";
    private static final String CONFIG_SHUTDOWN          = "shutdown";
    private static final String CONFIG_SHUTDOWN_TIMEOUT  = "shutdownTimeout";
    private static final String CONFIG_DAEMON            = "daemon";
    private static final String CONFIG_EXECUTOR          = "executor";
    private static final String CONFIG_CONSUMER_CALLBACK = "callback";
    private static final String ENV_PROFILE = "profile";
    private static final String MAIN_METHOD = "main";
    private static final String LOGGER_PROPERTIES = "logging.properties";

    private static final long DEFAULT_SHUTDOWN_TIMEOUT = 3L; //3s

    protected static Integer processId = -1;
    private static Map<Class<?>, List<Object>> bootCache = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        LogManager.getLogManager()
            .readConfiguration(Thread.currentThread().getContextClassLoader().getResourceAsStream(LOGGER_PROPERTIES));

        Main main = new Main(args);
        main.run();
        System.exit(main.exit());
    }

    private ApplicationContext.Listener listener = null;
    private boolean verbose = false;
    private boolean daemon = false;
    private boolean preparation = false;
    private long shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
    private int exitCode = 0;
    private String profile;
    private String[] arguments;
    private String configFile;
    private String pidFile;
    private Configuration configuration;
    private ExecutorService executor;
    private Consumer<Callable<Properties>> consumer;
    private Throwable lastError;
    private ServiceCollector collector;
    private Class<?> bootClass;

    public Main(String[] args) {
        this.arguments = args;
        this.preparation = init(this.arguments);
        if(!this.preparation) {
            usage();
            this.exitCode = -1;
            return;
        }

        List<?> hooks = this.configuration.getAs(CONFIG_SHUTDOWN, List.class);
        if(null != hooks) {
            for(Object hook : hooks) {
                if(isRunnable(hook)) {
                    ShutdownManager.getInstance().addListener((Runnable)hook);
                }
            }
        }
        try {
            Long timeout =this.configuration.getLong(CONFIG_SHUTDOWN_TIMEOUT);
            if(null != timeout) {
                this.shutdownTimeout = timeout;
            }
        } catch (Exception e) {
            //Not found
        }
        ShutdownManager.getInstance().addListener(new ExecutorShutdownHook(this.executor, this.shutdownTimeout));
    }

    public Main(Class<?> bootClass, String[] args) {
        this.bootClass = bootClass;
        this.arguments = args;
        this.preparation = init(this.arguments);
        if(!this.preparation) {
            usage();
            this.exitCode = -1;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean init(String[] args) {
        processId = getProcessId();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-h") || args[i].equals("--help")) {
                return false;
            } else
            if (args[i].equals("-v") || args[i].equals("-verbose")) {
                this.verbose = true;
                listener = new DebugMonitor();
            } else
            if (args[i].equals("-p") || args[i].equals("--profile")) {
                if(args.length < (i+1)) {
                    return false;
                }
                this.profile = args[i+1];
            } else
            if (args[i].equals("-i") || args[i].equals("--pid")) {
                if(args.length < (i+1)) {
                    return false;
                }
                this.pidFile = args[i+1];
            } else
            if (args[i].equals("-f") || args[i].equals("--file")) {
                if(args.length < (i+1)) {
                    return false;
                }
                this.configFile = args[i+1];
            }
        }

        // Set Profile
        if(this.profile == null) {
            this.profile = System.getenv(ENV_PROFILE);
            if(this.profile == null) {
                this.profile = System.getProperty(ENV_PROFILE);
            }
        } else {
            System.setProperty("activeProfile", this.profile);
        }

        // Set Pidfile
        if(this.pidFile == null) {
            this.pidFile = System.getProperty("pidfile");
        }

        //If not on CL arguments find configuration file on current path with profile
        this.configFile = matcheConfigFile(this.configFile);

        if(null == this.configFile) {
            System.err.println("Not found configuration file.");
            return false;
        }

        this.collector = new ServiceCollector();

        try {
            ConfigurationHandler handler = new ConfigurationHandler();
            handler.setListener(collector);
            handler.setResource(this.configFile);
            handler.parse();
            this.configuration = handler.getConfiguration();
            List<Object> list = this.configuration.getAs(CONFIG_MAIN, List.class);
            for(Object boot : list) {
                if(isBootstrap(boot)) {
                    if(listener != null) {
                        listener.trace(String.format("[BOOT] Register a boot '%1$s'", boot.getClass().getName()));
                    }
                    List<Object> boots = bootCache.get(boot.getClass());
                    if(null == boots) {
                        boots = new ArrayList<>();
                        bootCache.put(boot.getClass(), boots);
                    }
                    boots.add(boot);
                } else {
                    if(listener != null) {
                        listener.warn(String.format("'[BOOT] %1$s' is not bootstrap class.", boot.getClass().getName()));
                    }
                }
            }
            if(bootCache.isEmpty()) {
                System.err.println(
                        String.format("Could not find 'main' property to boot. See '%1$s'", this.configFile));
                return false;
            }
            this.daemon = this.configuration.getBoolean(CONFIG_DAEMON);
            this.executor = this.configuration.getAs(CONFIG_EXECUTOR, ExecutorService.class);
            if(null == this.executor) {
                this.executor = Executors.newCachedThreadPool();
            }
            // See 'callback.class = xxx'
            this.consumer = this.configuration.getAs(CONFIG_CONSUMER_CALLBACK, Consumer.class);

            if(listener != null) {
                listener.trace(String.format("[BOOT] Load configuration form '%1$s'", this.configFile));
            }
            return true;
        } catch (Throwable error) {
            this.lastError = error;
            this.lastError.printStackTrace(System.err);
            LOGGER.log(Level.SEVERE, error.getMessage(), error);
            if(listener != null) {
                listener.error("[BOOT] Configuration failed.", error);
            }
            return false;
        }
    }

    protected void usage() {
        System.out.println();
        System.out.println("Usage:");
        System.out.println("java " + getClass().getName());
        System.out.println("     -p --profile The profile name.");
        System.out.println("     -f --file    Config file.");
        System.out.println("                  Default: 'config-profile.yml(or conf, json, properties)'");
        System.out.println("     -i --pid     Process id file (/var/run/pid).");
        System.out.println("     -v --verbose On debug mode.");
        System.out.println("     -h --help    This help message.");
        System.out.println();
    }

    protected Configuration getConfig() {
        return this.configuration;
    }

    protected int execute() {
        try {
            if(listener != null) {
                listener.trace(String.format("[BOOT] Main(%d) executed.", processId));
            }
            Set<Class<?>> keys = bootCache.keySet();
            String[] args = new String[this.arguments.length + 2];
            System.arraycopy(this.arguments, 0, args, 0, this.arguments.length);
            for(Class<?> key : keys) {
                Object[] boots = bootCache.get(key).toArray();
                for(Object boot : boots) {
                    if(listener != null) {
                        listener.trace(String.format("[BOOT] %s executed.", boot.getClass().getName()));
                    }
                    if(isRunnable(boot)) {
                        this.executor.execute((Runnable)boot);
                    } else if(isThread(boot)) {
                        Thread thread = (Thread)boot;
                        thread.setDaemon(this.daemon);
                        thread.start();
                    } else {
                        final Method method = boot.getClass().getMethod(MAIN_METHOD, String[].class);
                        if(this.daemon) {
                            this.executor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // static method doesn't have an instance
                                        method.invoke(null, (Object)args);
                                    } catch (Throwable ex) {
                                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                                    }
                                }
                            });
                        } else {
                            // static method doesn't have an instance
                            method.invoke(null, (Object)this.arguments);
                        }
                    }
                }
            }

            return 0;
        } catch (Throwable error) {
            this.lastError = error;
            LOGGER.log(Level.SEVERE, error.getMessage(), error);
            return 1;
        }
    }

    public ApplicationContext run() {
        bootCache = Collections.unmodifiableMap(bootCache);
        Set<String> unimps = this.configuration.remains();
        if (!unimps.isEmpty()) {
            LOGGER.warning(String.format("[BOOT] Found %d parameter(s) '%s' that have not been referenced.", unimps.size(), unimps.toString()));
        }
        // Run service consumer callback to register all singleton services in container.
        // See 'callback.class = x.y.ServiceContext'
        if (consumer != null) {
            consumer.accept(new Callable<Properties>() {
                @Override
                public Properties call() {
                    // Setup service locator properties;
                    Properties services = new Properties();
                    services.put("config", configuration);
                    services.put("main", this);
                    services.put("PID", processId);
                    for (String key : collector.keys()) {
                        services.put(key, collector.get(key));
                    }
                    LOGGER.info(String.format("[BOOT] The service properties(%d) is forwarded.", services.size()));
                    collector.collection.clear();
                    return services;
                }
            });
        } else {
            LOGGER.info(String.format("[BOOT] The application context is saved on JVM properties by PID:%d", processId));
            System.getProperties().put(getProcessId(), this);
        }

        if(this.bootClass == null) {
            // Run base on configuration
            this.exitCode = execute();
        } else {
            //TODO
        }
        return new ApplicationContextWrapper(this);
    }

    protected int exit() {
        this.configuration.destroy();
        new ExecutorShutdownHook(this.executor, this.shutdownTimeout).run();
        bootCache = null;
        processId = -1;
        System.gc();
        return this.exitCode;
    }

    public Integer getProcessId() {
        if( -1 == processId) {
            // Get proccess id from JMX
            RuntimeMXBean mxRuntime = ManagementFactory.getRuntimeMXBean();
            String name = mxRuntime.getName();
            // Get pid by command line 'jps -lv'
            processId = Integer.valueOf(name.substring(0, name.indexOf('@'))); // pid@pcname
        }
        return processId;
    }

    public String getProfile() {
        return this.profile;
    }

    @Override
    public String toString() {
        return String.format("%s@%d-%s", getClass().getName(), getProcessId(), getProfile());
    }

    private String matcheConfigFile(String config) {
        final StringBuilder regex;
        final File path;
        if(null == config) {
            path = new File(System.getProperty("user.dir"));
            regex = new StringBuilder(BOOT_CONFIG_FILENAME);
        } else {
            File file = new File(config);
            path = file.getParentFile();
            regex = new StringBuilder(file.getName());
        }
        if(this.profile != null) {
            regex.append("-").append(this.profile);
        }
        //Default: 'config-profile.yml(conf, json, properties)'
        regex.append("[.](yml|properties|json|conf)");

        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.isFile() && Pattern.matches(regex.toString(), file.getName()));
            }
        };
        File[] files = path.listFiles(filter);
        if(null == files || files.length == 0) {
            if(verbose)
                LOGGER.warning(String.format("Can not matched '%1$s' file on '%2$s'", regex.toString(), path.getAbsolutePath()));
            return null;
        }
        try {
            return files[0].getCanonicalPath();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }

    private boolean isBootstrap(Object boot) {
        return (hasMainMethod(boot) ||
                isRunnable(boot) ||
                isThread(boot));
    }

    private boolean hasMainMethod(Object boot) {
        try {
            // Find public static 'main(String[] args)' method
            Method method = boot.getClass().getMethod(MAIN_METHOD, String[].class);
            return Modifier.isStatic(method.getModifiers());
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private boolean isRunnable(Object boot) {
        return (boot instanceof Runnable);
    }

    private boolean isThread(Object boot) {
        return (boot instanceof Thread);
    }

    class ExecutorShutdownHook implements Runnable {
        private ExecutorService pool;
        private long timeout;
        public ExecutorShutdownHook(ExecutorService pool, long timeout) {
            this.pool = pool;
            this.timeout = timeout;
        }

        @Override
        public void run() {
            pool.shutdown(); // Disable new tasks from being submitted
            try {
              // Wait a while for existing tasks to terminate
              if (!pool.awaitTermination(timeout, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(timeout, TimeUnit.SECONDS)) {
                    if(listener != null) {
                        listener.warn("[BOOT] Pool did not terminate.", null);
                    }
                }
              }
            } catch (InterruptedException ie) {
              // (Re-)Cancel if current thread also interrupted
              pool.shutdownNow();
              // Preserve interrupt status
              Thread.currentThread().interrupt();
            }
            if(listener != null) {
                listener.info("[BOOT] Main shutdowned.");
            }
        }
    }

    class ApplicationContextWrapper implements ApplicationContext {

        final Main delgate;
        private ApplicationContextWrapper(Main main) {
            delgate = main;
        }
        @Override
        public Integer getProcessId() {
            return processId;
        }

        @Override
        public String getProfile() {
            return delgate.getProfile();
        }

        @Override
        public <T> T getBean(String key, Class<T> type) {
            return delgate.configuration.getAs(key, type);
        }

        @Override
        public List<Object> getBootBeans() {
            List<Object> list = new ArrayList<>();
            for (Class<?> type : bootCache.keySet()) {
                for (Object b : bootCache.get(type)) {
                    if (!list.contains(b)) {
                        list.add(b);
                    }
                }
            }
            return list;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> getBootBeans(Class<T> type) {
            if(bootCache.containsKey(type)) {
                return (List<T>) bootCache.get(type);
            }
            return null;
        }

        @Override
        public <T> T getBootBean(Class<T> type) {
            if(bootCache.containsKey(type)) {
                return type.cast(bootCache.get(type).get(0));
            }
            return null;
        }

        @Override
        public Throwable getLastError() {
            return delgate.lastError;
        }
    }

    class ServiceCollector implements ContextHandler.Listener {
        Map<String, Object> collection = new HashMap<>();
        @Override
        public void created(Object bean, Class<?> beanClass) {
            LOGGER.fine(String.format("[BOOT] '%s'#%d was created.", beanClass.getSimpleName(), bean.hashCode()));
        }

        @Override
        public void parsed(String type, String resource, int size) {
            LOGGER.info(String.format("[BOOT] %s '%s'(%d) was parsed.", type, String.valueOf(resource), size));
        }

        @Override
        public void cached(String name, Object value, boolean config) {
            if (config) {
                LOGGER.fine(String.format("[BOOT] Cached Configuration#%d' by '%s'", value.hashCode(), name));
            } else {
                LOGGER.fine(String.format("[BOOT] Cached '%s' = '%s'", name, value.toString()));
                collection.put(name, value);
            }
        }
        public Set<String> keys() {
            return collection.keySet();
        }
        public Object get(String name) {
            return collection.get(name);
        }
    }

    static class DebugMonitor implements ApplicationContext.Listener {
        @Override
        public void trace(String msg) {
            LOGGER.fine(msg);
        }

        @Override
        public void info(String msg) {
            LOGGER.info(msg);
        }

        @Override
        public void warn(String msg) {
            warn(msg, null);
        }

        @Override
        public void warn(String msg, Throwable exception) {
            if(null != exception) {
                System.err.println(msg + " " + exception.getMessage());
                LOGGER.log(Level.WARNING, msg, exception);
            } else {
                System.err.println(msg);
                LOGGER.log(Level.WARNING, msg);
            }
        }

        @Override
        public void error(String msg) {
            error(msg, null);
        }
        @Override
        public void error(String msg, Throwable exception) {
            if(null != exception) {
                System.err.println(msg + " " + exception.getMessage());
                LOGGER.log(Level.SEVERE, msg, exception);
            } else {
                System.err.println(msg);
                LOGGER.log(Level.SEVERE, msg);
            }
        }
    }
}
