package com.eu.habbo;

import com.eu.habbo.core.CleanerThread;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.Logging;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.core.consolecommands.ConsoleCommand;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.camera.CameraClient;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public final class Emulator {

    public final static int MAJOR = 2;


    public final static int MINOR = 1;


    public final static int BUILD = 0;


    public final static String PREVIEW = "RC-1";

    public static final String version = "Arcturus Morningstar" + " " + MAJOR + "." + MINOR + "." + BUILD + " " + PREVIEW;
    private static final String logo =

            "							                    				    \n" +
                    "    __  ___                 _  A R C T U R U S __            		\n" +
                    "   /  |/  /___  _________  (_)___  ____ ______/ /_____ ______		\n" +
                    "  / /|_/ / __ \\/ ___/ __ \\/ / __ \\/ __ `/ ___/ __/ __ `/ ___/	\n" +
                    " / /  / / /_/ / /  / / / / / / / / /_/ (__  ) /_/ /_/ / /    		\n" +
                    "/_/  /_/\\____/_/  /_/ /_/_/_/ /_/\\__, /____/\\__/\\__,_/_/     	\n" +
                    "                                /____/                             \n";
    public static String build = "";
    public static boolean isReady = false;
    public static boolean isShuttingDown = false;
    public static boolean stopped = false;
    public static boolean debugging = false;
    private static int timeStarted = 0;
    private static Runtime runtime;
    private static ConfigurationManager config;
    private static TextsManager texts;
    private static GameServer gameServer;
    private static RCONServer rconServer;
    private static CameraClient cameraClient;
    private static Database database;
    private static Logging logging;
    private static ThreadPooling threading;
    private static GameEnvironment gameEnvironment;
    private static PluginManager pluginManager;
    private static Random random;
    private static BadgeImager badgeImager;

    static {
        Thread hook = new Thread(new Runnable() {
            public synchronized void run() {
                Emulator.dispose();
            }
        });
        hook.setPriority(10);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public static void main(String[] args) throws Exception {
        try {
            Locale.setDefault(new Locale("en"));

            setBuild();
            Emulator.stopped = false;
            ConsoleCommand.load();
            Emulator.logging = new Logging();
            Emulator.getLogging().logStart("\r" + Emulator.logo +
                    "       Build: " + build + "\n");
            random = new Random();
            long startTime = System.nanoTime();

            Emulator.runtime = Runtime.getRuntime();
            Emulator.config = new ConfigurationManager("config.ini");
            Emulator.database = new Database(Emulator.getConfig());
            Emulator.config.loaded = true;
            Emulator.config.loadFromDatabase();
            Emulator.threading = new ThreadPooling(Emulator.getConfig().getInt("runtime.threads"));
            Emulator.getDatabase().getDataSource().setMaximumPoolSize(Emulator.getConfig().getInt("runtime.threads") * 2);
            Emulator.getDatabase().getDataSource().setMinimumIdle(10);
            Emulator.pluginManager = new PluginManager();
            Emulator.pluginManager.reload();
            Emulator.getPluginManager().fireEvent(new EmulatorConfigUpdatedEvent());
            Emulator.texts = new TextsManager();
            new CleanerThread();
            Emulator.gameServer = new GameServer(getConfig().getValue("game.host", "127.0.0.1"), getConfig().getInt("game.port", 30000));
            Emulator.rconServer = new RCONServer(getConfig().getValue("rcon.host", "127.0.0.1"), getConfig().getInt("rcon.port", 30001));
            Emulator.gameEnvironment = new GameEnvironment();
            Emulator.gameEnvironment.load();
            Emulator.gameServer.initializePipeline();
            Emulator.gameServer.connect();
            Emulator.rconServer.initializePipeline();
            Emulator.rconServer.connect();
            Emulator.badgeImager = new BadgeImager();
            Emulator.getLogging().logStart("Habbo Hotel Emulator has succesfully loaded.");
            Emulator.getLogging().logStart("You're running: " + Emulator.version);
            Emulator.getLogging().logStart("System launched in: " + (System.nanoTime() - startTime) / 1e6 + "ms. Using: " + (Runtime.getRuntime().availableProcessors() * 2) + " threads!");
            Emulator.getLogging().logStart("Memory: " + (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + "/" + (runtime.freeMemory()) / (1024 * 1024) + "MB");

            Emulator.debugging = Emulator.getConfig().getBoolean("debug.mode");

            if (debugging) {
                Emulator.getLogging().logDebugLine("Debugging Enabled!");
            }

            Emulator.getPluginManager().fireEvent(new EmulatorLoadedEvent());
            Emulator.isReady = true;
            Emulator.timeStarted = getIntUnixTimestamp();

            if (Emulator.getConfig().getInt("runtime.threads") < (Runtime.getRuntime().availableProcessors() * 2)) {
                Emulator.getLogging().logStart("Emulator settings runtime.threads (" + Emulator.getConfig().getInt("runtime.threads") + ") can be increased to " + (Runtime.getRuntime().availableProcessors() * 2) + " to possibly increase performance.");
            }


            Emulator.getThreading().run(new Runnable() {
                @Override
                public void run() {
                    Emulator.getLogging().logStart("Thankyou for downloading Arcturus Morningstar! This is a Release Candidate for 2.1.0, if you find any bugs please place them on our git repository.");
                    Emulator.getLogging().logStart("Please note, Arcturus Emulator is a project by TheGeneral, we take no credit for the original work, and only the work we have continued. If you'd like to support the project, join our discord at: ");
                    Emulator.getLogging().logStart("https://discord.gg/syuqgN");
                    System.out.println("Waiting for commands: ");
                }
            }, 3500);

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (!isShuttingDown && isReady) {
                try {

                    String line = reader.readLine();

                    if (line != null) {
                        ConsoleCommand.handle(line);
                    }
                    System.out.println("Waiting for command: ");
                } catch (Exception e) {
                    Emulator.getLogging().logErrorLine(e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setBuild() {
        if (Emulator.class.getProtectionDomain().getCodeSource() == null) {
            build = "UNKNOWN";
            return;
        }

        StringBuilder sb = new StringBuilder();
        try {
            String filepath = new File(Emulator.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();
            MessageDigest md = MessageDigest.getInstance("MD5");// MD5
            FileInputStream fis = new FileInputStream(filepath);
            byte[] dataBytes = new byte[1024];
            int nread = 0;

            while ((nread = fis.read(dataBytes)) != -1)
                md.update(dataBytes, 0, nread);

            byte[] mdbytes = md.digest();

            for (int i = 0; i < mdbytes.length; i++)
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
        } catch (Exception e) {
            build = "UNKNOWN";
            return;
        }

        build = sb.toString();
    }

    private static void dispose() {
        Emulator.getThreading().setCanAdd(false);
        Emulator.isShuttingDown = true;
        Emulator.isReady = false;
        Emulator.getLogging().logShutdownLine("Stopping Arcturus Emulator " + version + "...");

        try {
            if (Emulator.getPluginManager() != null)
                Emulator.getPluginManager().fireEvent(new EmulatorStartShutdownEvent());
        } catch (Exception e) {
        }

        try {
            if (Emulator.cameraClient != null)
                Emulator.cameraClient.disconnect();
        } catch (Exception e) {
        }

        try {
            if (Emulator.rconServer != null)
                Emulator.rconServer.stop();
        } catch (Exception e) {
        }


        try {
            if (Emulator.gameEnvironment != null)
                Emulator.gameEnvironment.dispose();
        } catch (Exception e) {
        }

        try {
            if (Emulator.getPluginManager() != null)
                Emulator.getPluginManager().fireEvent(new EmulatorStoppedEvent());
        } catch (Exception e) {
        }

        try {
            if (Emulator.pluginManager != null)
                Emulator.pluginManager.dispose();
        } catch (Exception e) {
        }

        Emulator.getLogging().saveLogs();

        try {
            if (Emulator.config != null) {
                Emulator.config.saveToDatabase();
            }
        } catch (Exception e) {
        }

        try {
            if (Emulator.gameServer != null)
                Emulator.gameServer.stop();
        } catch (Exception e) {
        }

        Emulator.getLogging().logShutdownLine("Stopped Arcturus Emulator " + version + "...");

        if (Emulator.database != null) {
            Emulator.getDatabase().dispose();
        }
        Emulator.stopped = true;

        try {
            if (Emulator.threading != null)
                Emulator.threading.shutDown();
        } catch (Exception e) {
        }
    }

    public static ConfigurationManager getConfig() {
        return config;
    }

    public static TextsManager getTexts() {
        return texts;
    }

    public static Database getDatabase() {
        return database;
    }

    public static Runtime getRuntime() {
        return runtime;
    }

    public static GameServer getGameServer() {
        return gameServer;
    }

    public static RCONServer getRconServer() {
        return rconServer;
    }

    public static Logging getLogging() {
        return logging;
    }

    public static ThreadPooling getThreading() {
        return threading;
    }

    public static GameEnvironment getGameEnvironment() {
        return gameEnvironment;
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    public static Random getRandom() {
        return random;
    }

    public static BadgeImager getBadgeImager() {
        return badgeImager;
    }

    public static CameraClient getCameraClient() {
        return cameraClient;
    }

    public static synchronized void setCameraClient(CameraClient client) {
        cameraClient = client;
    }

    public static int getTimeStarted() {
        return timeStarted;
    }

    public static int getOnlineTime() {
        return getIntUnixTimestamp() - timeStarted;
    }

    public static void prepareShutdown() {
        System.exit(0);
    }

    private static String dateToUnixTimestamp(Date date) {
        String res = "";
        Date aux = stringToDate("1970-01-01 00:00:00");
        Timestamp aux1 = dateToTimeStamp(aux);
        Timestamp aux2 = dateToTimeStamp(date);
        long difference = aux2.getTime() - aux1.getTime();
        long seconds = difference / 1000L;
        return res + seconds;
    }

    private static Date stringToDate(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date res = null;
        try {
            res = format.parse(date);
        } catch (Exception e) {
            Emulator.getLogging().logErrorLine(e);
        }
        return res;
    }

    public static Timestamp dateToTimeStamp(Date date) {
        return new Timestamp(date.getTime());
    }

    public static Date getDate() {
        return new Date(System.currentTimeMillis());
    }

    public static String getUnixTimestamp() {
        return dateToUnixTimestamp(getDate());
    }

    public static int getIntUnixTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public static boolean isNumeric(String string)
            throws IllegalArgumentException {
        boolean isnumeric = false;
        if ((string != null) && (!string.equals(""))) {
            isnumeric = true;
            char[] chars = string.toCharArray();
            for (char aChar : chars) {
                isnumeric = Character.isDigit(aChar);
                if (!isnumeric) {
                    break;
                }
            }
        }
        return isnumeric;
    }

    public int getUserCount() {
        return gameEnvironment.getHabboManager().getOnlineCount();
    }

    public int getRoomCount() {
        return gameEnvironment.getRoomManager().getActiveRooms().size();
    }
}