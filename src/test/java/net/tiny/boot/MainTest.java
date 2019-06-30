package net.tiny.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.LogManager;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tiny.config.Config;

public class MainTest {

    @BeforeAll
    public static void beforeAll() throws Exception {
    LogManager.getLogManager()
        .readConfiguration(Thread.currentThread().getContextClassLoader().getResourceAsStream("logging.properties"));
    //Comment out SLF4JBridgeHandler to show exception trace when tomcat start failed
    //Bridge the output of java.util.logging.Logger
//    org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
//    org.slf4j.bridge.SLF4JBridgeHandler.install();
//    LOGGER.log(Level.INFO, String.format("[REST] %s() SLF4J Bridge the output of JUL",
//            Bootstrap.class.getSimpleName()));
    }

    @BeforeEach
    public void setUp() throws Exception {
        ConsoleCapture.out.enable(true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ConsoleCapture.out.enable(false);
        ConsoleCapture.out.clear();
    }

    @Test
    public void testUsageHelp() throws Exception {
        String[] args = new String[] {"-h"};
        Main main = new Main(args);
        assertNotNull(main);
        assertNotNull(main);

        String out = ConsoleCapture.out.getHistory();
        assertTrue(ConsoleCapture.out.contains("Usage"));
        assertTrue(out.contains("Usage"));
    }

    @Test
    public void testNotFoundProfile() throws Exception {
        String[] args = new String[] {"-v", "-p", "dumy"};
        Main main = new Main(args);
        assertNotNull(main);


        String out = ConsoleCapture.out.getHistory();
        assertTrue(ConsoleCapture.out.contains("Usage"));
        assertTrue(out.contains("Usage"));
    }

    @Test
    public void testUnitProfile() throws Exception {
        String regex = "application-unit[.](properties|json|conf|yml)";
        assertTrue(Pattern.matches(regex, "application-unit.properties"));

        String[] args = new String[] {"-v", "-p", "unit"};

        ApplicationContext context = new Main(args).run();
        Thread.sleep(3500L);
        assertNull(context.getLastError());
    }

    @Test
    public void testOnlyOne() throws Exception {
        String[] args = new String[] {"-v", "-p", "unit"};
        One.main(args);
    }

    public static class One {
        private String name = "one1";

        public String getName() {
            return name;
        }

        public static void main(String[] args) throws Exception {
            One one = new One();
            one.exec();
        }

        public void exec() {
            System.out.println(String.format("Task1 '%s' start", name));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            System.out.println(String.format("Task1 '%s' end", name));
        }
    }

    public static class Two implements Runnable {
        private String name;
        private Object config;

        public String getName() {
            return name;
        }

        @Override
        public void run() {
            System.out.println(String.format("Task2 '%s' start", name));
            try {
                System.out.println(String.format("Task2 '%s' Configuration#%d", name, config.hashCode()));
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
            System.out.println(String.format("Task2 '%s' end.", name));
        }
    }

    public static class Three extends Thread {
        @Override
        public void run() {
            System.out.println("Task3 'Three' start");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
            System.out.println("Task3 'Three' end.");
        }
    }

    @Config("") //TODO
    public static class Four {
        private String name;

        public String getName() {
            return name;
        }

        public static void main(String[] args) throws Exception {
            ApplicationContext context = new Main(Four.class, args).run();
            Four four = context.getBootBean(Four.class);
            four.exec();
        }

        public void exec() {
            System.out.println(String.format("Task1 '%s' start", name));
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }
            System.out.println(String.format("Task1 '%s' end", name));
        }
    }

    public static class DummyBoot {
        private String name;

        public String getName() {
            return name;
        }

        public static void main(String[] args) throws Exception {
            ApplicationContext context = new Main(DummyBoot.class, args).run();
            DummyBoot boot = context.getBootBean(DummyBoot.class);
            boot.exec();
        }

        public void exec() {
            System.out.println(String.format("Task1 '%1$s' start", name));
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
            System.out.println(String.format("Task1 '%1$s' end", name));
        }
    }

    public static class Hook implements Runnable {
        private String name;

        public String getName() {
            return name;
        }

        @Override
        public void run() {
            System.out.println(name + " shutdown start");
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
            System.out.println(name + " shutdown end");
        }
    }

    public static class DummyCallback implements Consumer<Callable<Properties>> {
        ////////////////////////////////////////
        // Service consumer callback  method, will be called by main process.
        @Override
        public void accept(Callable<Properties> callable) {
            try {
                Properties services = callable.call();
                System.out.println("## Called by main booter, Found " + services.size() + " service(s).");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
