/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.util.*;

import nxt.util.*;
import org.json.simple.JSONObject;

import nxt.addons.AddOns;
import nxt.crypto.Crypto;
import nxt.env.DirProvider;
import nxt.env.RuntimeEnvironment;
import nxt.env.RuntimeMode;
import nxt.env.ServerStatus;
import nxt.http.API;
import nxt.http.APIProxy;
import nxt.peer.Peers;
import nxt.user.Users;

public final class Nxt {

    public static boolean becomeSupernodeNow = false;
    public static boolean isSupernode = false;
    public static String supernodePass = "";
    public static String externalIPSN = "";
    public static boolean connectToSupernodes = false;
    public static Account getSnAccount(){ return Account.getAccount(Crypto.getPublicKey(Nxt.supernodePass)); };
	public static boolean snrenew = false;

    private static class Init {

		private static volatile boolean initialized = false;


		static {
			try {
				final long startTime = System.currentTimeMillis();
				Logger.init();
				Nxt.setSystemProperties();
				Nxt.logSystemProperties();
				Nxt.runtimeMode.init();
				final Thread secureRandomInitThread = Nxt.initSecureRandom();
				Nxt.setServerStatus(ServerStatus.BEFORE_DATABASE, null);
				Db.init();
				Nxt.setServerStatus(ServerStatus.AFTER_DATABASE, null);
				//noinspection ResultOfMethodCallIgnored
				TransactionProcessorImpl.getInstance();
				//noinspection ResultOfMethodCallIgnored
				BlockchainProcessorImpl.getInstance();
				Account.init();
				AccountLedger.init();
				PrunableSourceCode.init();
				Redeem.init();
				Work.init();
				nxt.Fork.init();
				PowAndBounty.init();
				PowAndBountyAnnouncements.init();
				Peers.init();
				APIProxy.init();
				Generator.init();
				AddOns.init();
				API.init();
				Users.init();

				// Now initialize the fork manager. This will throw an Exception if features that are not implemented are being enabled
				SoftForkManager.init();

				// Do a supernode check
				String snpass=Nxt.getStringProperty("nxt.superNodePassphrase","");
				snrenew=Nxt.getBooleanProperty("nxt.autoRenewSupernode");

				if(snpass.length()!=0){
					Nxt.externalIPSN = Convert.emptyToNull(Nxt.getStringProperty("nxt.myAddress", "").trim());
					if(Nxt.externalIPSN  == null){
						final InetAddress extAddr = UPnP.getExternalAddress();
						if (extAddr == null){
							Logger.logErrorMessage("Cannot determine your external IP, please set it in nxt.properties!");
							System.exit(1);
						}

						// in this case UPNP port must be active, check

						boolean check = Nxt.getBooleanProperty("nxt.enablePeerUPnP");
						if(!check){
							Logger.logErrorMessage("Without a statically configured IP, you need to set nxt.enablePeerUPnP = true");
							System.exit(1);
						}
						Nxt.externalIPSN = extAddr.getHostAddress();
					}
					Nxt.connectToSupernodes=Nxt.getBooleanProperty("nxt.connectToSupernodes");
					Logger.logInfoMessage("Own IP at early stage: " + Nxt.externalIPSN);

					if(!IPValidator.getInstance().validate(Nxt.externalIPSN)){
						Logger.logErrorMessage("You are trying to become a supernode, but your external IP is not a correct IP.");
						System.exit(1);
					}

					Account sn = Account.getAccount(Crypto.getPublicKey(snpass));

					if(sn==null || (!sn.isSuperNode() && !sn.canBecomeSupernode())) if (!snrenew) {
                        Logger.logErrorMessage("You are not a supernode yet, make sure you activate nxt.autoRenewSupernode = true or renew supernode status via API.");
                    } else
                        Logger.logErrorMessage("You are trying to become a supernode, but you do not have enough funds for the deposit. Fund your account as quick as possible. Retrying soon.");
                    else // Check if autorenew is on if the node is not yet a supernode
                        if((!sn.isSuperNode() && sn.canBecomeSupernode())) if (!snrenew) {
                            Logger.logErrorMessage("You are not a supernode yet, make sure you activate nxt.autoRenewSupernode = true.");
                            System.exit(1);
                        }

					if(sn != null && sn.isSuperNode()){
						Nxt.isSupernode = true;
						Nxt.supernodePass = snpass;
						Nxt.becomeSupernodeNow = false;
						Logger.logInfoMessage("YOU ARE A SUPERNODE RIGHT NOW!");
						SupernodeMagicManager.getInstance().initialized();
					} else{
                        Nxt.isSupernode = true;
                        Nxt.supernodePass = snpass;
                        Nxt.becomeSupernodeNow = true;
                        Logger.logInfoMessage("YOU WILL BECOME A SUPERNODE SHORTLY!");
                        SupernodeMagicManager.getInstance().initialized();
                    }



				}




				int configuredMultiplier = Nxt.getIntProperty("nxt.timeMultiplier");
				if (configuredMultiplier < 1) configuredMultiplier = 1;
				
				final int timeMultiplier = (Constants.isTestnet && Constants.isOffline)
						? configuredMultiplier : 1;
				ThreadPool.start(timeMultiplier);
				if (timeMultiplier > 1) {
					Nxt.setTime(new Time.FasterTime(
							Math.max(Nxt.getEpochTime(), Nxt.getBlockchain().getLastBlock().getTimestamp()),
							timeMultiplier));
					Logger.logMessage("TIME WILL FLOW " + timeMultiplier + " TIMES FASTER!");
				}
				try {
					secureRandomInitThread.join(10000);
				} catch (final InterruptedException ignore) {
				}
				Nxt.testSecureRandom();
				final long currentTime = System.currentTimeMillis();
				Logger.logMessage("Initialization took " + ((currentTime - startTime) / 1000) + " seconds");
				Logger.logMessage("Nxt server " + Nxt.VERSION + " started successfully.");
				Logger.logMessage("Copyright © 2013-2016 The XEL Core Developers.");
				Logger.logMessage("Distributed under GPLv2, with ABSOLUTELY NO WARRANTY.");
				if (API.getWelcomePageUri() != null) Logger.logMessage("Client UI is at " + API.getWelcomePageUri());
				Nxt.setServerStatus(ServerStatus.STARTED, API.getWelcomePageUri());
				if (Nxt.isDesktopApplicationEnabled()) Nxt.launchDesktopApplication();
				if (Constants.isTestnet) Logger.logMessage("RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!");
			} catch (final Exception e) {
				Logger.logErrorMessage(e.getMessage(), e);
				System.err.println("[!!] " + e.getMessage());
				Nxt.runtimeMode.alert(e.getMessage() + "\n" + "See additional information in "
						+ Nxt.dirProvider.getLogFileDir() + System.getProperty("file.separator") + "nxt.log");
				System.exit(1);
			}
		}

		private static void init() {
			if (Init.initialized) throw new RuntimeException("Nxt.init has already been called");
			Init.initialized = true;
		}

		private Init() {
		} // never

	}

	public static final String VERSION = "1.0.8";

	public static final String APPLICATION = "Elastic";

	private static volatile Time time = new Time.EpochTime();
	private static final String NXT_DEFAULT_PROPERTIES = "nxt-default.properties";
	private static final String NXT_PROPERTIES = "nxt.properties";

	private static final String CONFIG_DIR = "conf";
	private static final RuntimeMode runtimeMode;

	private static final DirProvider dirProvider;
	private static final Properties defaultProperties = new Properties();

	static {
		Nxt.redirectSystemStreams("out");
		Nxt.redirectSystemStreams("err");
		System.out.println("Initializing XEL server version " + Nxt.VERSION);
		Nxt.printCommandLineArguments();
		runtimeMode = RuntimeEnvironment.getRuntimeMode();
		System.out.printf("Runtime mode %s\n", Nxt.runtimeMode.getClass().getName());
		dirProvider = RuntimeEnvironment.getDirProvider();
		System.out.println("User home folder " + Nxt.dirProvider.getUserHomeDir());
		Nxt.loadProperties(Nxt.defaultProperties, Nxt.NXT_DEFAULT_PROPERTIES, true);
		if (!Objects.equals(Nxt.VERSION, Nxt.defaultProperties.getProperty("nxt.version")))
            throw new RuntimeException("Using an nxt-default.properties file from a version other than " + Nxt.VERSION
                    + " is not supported!!! You provided " + Nxt.defaultProperties.getProperty("nxt.version") + ".");
	}

	private static final Properties properties = new Properties(Nxt.defaultProperties);

	static {
		Nxt.loadProperties(Nxt.properties, Nxt.NXT_PROPERTIES, false);
	}

	public static Blockchain getBlockchain() {
		return BlockchainImpl.getInstance();
	}

	public static BlockchainProcessor getBlockchainProcessor() {
		return BlockchainProcessorImpl.getInstance();
	}

	public static Boolean getBooleanProperty(final String name) {
		final String value = Nxt.properties.getProperty(name);
		if (Objects.equals(Boolean.TRUE.toString(), value)) {
			Logger.logMessage(name + " = \"true\"");
			return true;
		} else if (Objects.equals(Boolean.FALSE.toString(), value)) {
			Logger.logMessage(name + " = \"false\"");
			return false;
		}
		Logger.logMessage(name + " not defined, assuming false");
		return false;
	}

	public static Boolean getBooleanPropertySilent(final String name) {
		final String value = Nxt.properties.getProperty(name);
		if (Objects.equals(Boolean.TRUE.toString(), value)) {
			return true;
		} else if (Objects.equals(Boolean.FALSE.toString(), value)) {
			return false;
		}
		return false;
	}

	public static File getConfDir() {
		return Nxt.dirProvider.getConfDir();
	}

	public static String getDbDir(final String dbDir) {
		return Nxt.dirProvider.getDbDir(dbDir);
	}

	public static int getEpochTime() {
		return Nxt.time.getTime();
	}

	public static int getIntProperty(final String name) {
		return Nxt.getIntProperty(name, 0);
	}

	public static int getIntProperty(final String name, final int defaultValue) {
		try {
			final int result = Integer.parseInt(Nxt.properties.getProperty(name));
			Logger.logMessage(name + " = \"" + result + "\"");
			return result;
		} catch (final NumberFormatException e) {
			Logger.logMessage(name + " not defined or not numeric, using default value " + defaultValue);
			return defaultValue;
		}
	}

	public static String getProcessId() {
		final String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
		if (runtimeName == null) return "";
		final String[] tokens = runtimeName.split("@");
		if (tokens.length == 2) return tokens[0];
		return "";
	}

	public static List<String> getStringListProperty(final String name) {
		final String value = Nxt.getStringProperty(name);
		if ((value == null) || (value.length() == 0)) return Collections.emptyList();
		final List<String> result = new ArrayList<>();
		for (String s : value.split(";")) {
			s = s.trim();
			if (s.length() > 0) result.add(s);
		}
		return result;
	}

	public static String getStringProperty(final String name) {
		return Nxt.getStringProperty(name, null, false);
	}

	public static String getStringProperty(final String name, final String defaultValue) {
		return Nxt.getStringProperty(name, defaultValue, false);
	}

	public static String getStringProperty(final String name, final String defaultValue, final boolean doNotLog) {
		final String value = Nxt.properties.getProperty(name);
		if ((value != null) && !Objects.equals("", value)) {
			Logger.logMessage(name + " = \"" + (doNotLog ? "{not logged}" : value) + "\"");
			return value;
		} else {
			Logger.logMessage(name + " not defined");
			return defaultValue;
		}
	}

	public static TransactionProcessor getTransactionProcessor() {
		return TransactionProcessorImpl.getInstance();
	}

	public static String getUserHomeDir() {
		return Nxt.dirProvider.getUserHomeDir();
	}

	private static void init() {
		Init.init();
	}

	public static void init(final Properties customProperties) {
		Nxt.properties.putAll(customProperties);
		Nxt.init();
	}

	private static Thread initSecureRandom() {
		final Thread secureRandomInitThread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
		secureRandomInitThread.setDaemon(true);
		secureRandomInitThread.start();
		return secureRandomInitThread;
	}

	public static boolean isDesktopApplicationEnabled() {
		return RuntimeEnvironment.isDesktopApplicationEnabled()
				&& Nxt.getBooleanProperty("nxt.launchDesktopApplication");
	}

	private static void launchDesktopApplication() {
		Nxt.runtimeMode.launchDesktopApplication();
	}

	public static Properties loadProperties(final Properties properties, final String propertiesFile,
			final boolean isDefault) {
		try {
			// Load properties from location specified as command line parameter
			final String configFile = System.getProperty(propertiesFile);
			if (configFile != null) {
				System.out.printf("Loading %s from %s\n", propertiesFile, configFile);
				try (InputStream fis = new FileInputStream(configFile)) {
					properties.load(fis);
					return properties;
				} catch (final IOException e) {
					throw new IllegalArgumentException(
							String.format("Error loading %s from %s", propertiesFile, configFile));
				}
			} else try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(propertiesFile)) {
                // When running nxt.exe from a Windows installation we
                // always have nxt.properties in the classpath but this is
                // not the nxt properties file
                // Therefore we first load it from the classpath and then
                // look for the real nxt.properties in the user folder.
                if (is != null) {
                    System.out.println("Loading default properties from resources file.");

                    properties.load(is);
                    if (isDefault) return properties;
                }
                // load non-default properties files from the user folder
                if (!Nxt.dirProvider.isLoadPropertyFileFromUserDir()) {
                    System.out.println("Skipping to load properties from home folder.");

                    return properties;
                }
                final String homeDir = Nxt.dirProvider.getUserHomeDir();
                if (!Files.isReadable(Paths.get(homeDir))) {
                    System.out.println("Creating home directory: " + homeDir);

                    try {
                        Files.createDirectory(Paths.get(homeDir));
                    } catch (final Exception e) {
                        if (!(e instanceof NoSuchFileException)) throw e;
                        // Fix for WinXP and 2003 which does have a roaming
                        // sub folder
                        Files.createDirectory(Paths.get(homeDir).getParent());
                        Files.createDirectory(Paths.get(homeDir));
                    }
                }
                final Path confDir = Paths.get(homeDir, Nxt.CONFIG_DIR);
                if (!Files.isReadable(confDir)) {
                    System.out.println("Creating config directory: " + confDir);

                    Files.createDirectory(confDir);
                }
                final Path propPath = Paths.get(confDir.toString()).resolve(Paths.get(propertiesFile));
                if (Files.isReadable(propPath)) {
                    System.out.println("Loading customized properties " + propertiesFile + " from " + confDir);
                    try (InputStream istream = Files.newInputStream(propPath)) {
                        properties.load(istream);
                    } catch (final Exception e) {
                        System.err.println("Failed loading customized properties " + propertiesFile + " from "
                                + confDir + ": " + e.getMessage());
                    }

                } else {
                    System.out.println("Creating property file in:" + propPath);

                    Files.createFile(propPath);
                    Files.write(propPath,
                            Convert.toBytes("# use this file for workstation specific " + propertiesFile));
                }
                return properties;
            } catch (final IOException e) {
                throw new IllegalArgumentException("Error loading " + propertiesFile, e);
            }
		} catch (final IllegalArgumentException e) {
			e.printStackTrace(); // make sure we log this exception
			throw e;
		}
	}

	private static void logSystemProperties() {
		final String[] loggedProperties = new String[] { "java.version", "java.vm.version", "java.vm.name",
				"java.vendor", "java.vm.vendor", "java.home", "java.library.path", "java.class.path", "os.arch",
				"sun.arch.data.model", "os.name", "file.encoding", "java.security.policy", "java.security.manager",
				RuntimeEnvironment.RUNTIME_MODE_ARG, RuntimeEnvironment.DIRPROVIDER_ARG };
		for (final String property : loggedProperties)
            Logger.logDebugMessage(String.format("%s = %s", property, System.getProperty(property)));
		Logger.logDebugMessage(String.format("availableProcessors = %s", Runtime.getRuntime().availableProcessors()));
		Logger.logDebugMessage(String.format("maxMemory = %s", Runtime.getRuntime().maxMemory()));
		Logger.logDebugMessage(String.format("processId = %s", Nxt.getProcessId()));
	}

	public static void main(final String[] args) {
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(Nxt::shutdown));
			Nxt.init();
		} catch (final Throwable t) {
			System.out.println("Fatal error: " + t.toString());
			t.printStackTrace();
		}
	}

	public static Transaction.Builder newTransactionBuilder(final byte[] transactionBytes)
			throws NxtException.NotValidException {
		return TransactionImpl.newTransactionBuilder(transactionBytes);
	}

	public static Transaction.Builder newTransactionBuilder(final byte[] transactionBytes,
			final JSONObject prunableAttachments) throws NxtException.NotValidException {
		return TransactionImpl.newTransactionBuilder(transactionBytes, prunableAttachments);
	}

	public static Transaction.Builder newTransactionBuilder(final byte[] senderPublicKey, final long amountNQT,
			final long feeNQT, final short deadline, final Attachment attachment) {
		return new TransactionImpl.BuilderImpl((byte) 1, senderPublicKey, amountNQT, feeNQT, deadline,
				(Attachment.AbstractAttachment) attachment);
	}

	public static Transaction.Builder newTransactionBuilder(final JSONObject transactionJSON)
			throws NxtException.NotValidException {
		return TransactionImpl.newTransactionBuilder(transactionJSON);
	}

	private static void printCommandLineArguments() {
		try {
			final List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
			if ((inputArguments != null) && (inputArguments.size() > 0)) System.out.println("Command line arguments");
            else return;
			inputArguments.forEach(System.out::println);
		} catch (final AccessControlException e) {
			System.out.println("Cannot read input arguments " + e.getMessage());
		}
	}

	private static void redirectSystemStreams(final String streamName) {
		final String isStandardRedirect = System.getProperty("nxt.redirect.system." + streamName);
		Path path = null;
		if (isStandardRedirect != null) try {
            path = Files.createTempFile("nxt.system." + streamName + ".", ".log");
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }
        else {
			final String explicitFileName = System.getProperty("nxt.system." + streamName);
			if (explicitFileName != null) path = Paths.get(explicitFileName);
		}
		if (path != null) try {
            final PrintStream stream = new PrintStream(Files.newOutputStream(path));
            if (Objects.equals(streamName, "out")) System.setOut(new PrintStream(stream));
            else System.setErr(new PrintStream(stream));
        } catch (final IOException e) {
            e.printStackTrace();
        }
	}

	private static void setServerStatus(final ServerStatus status, final URI wallet) {
		Nxt.runtimeMode.setServerStatus(status, wallet, Nxt.dirProvider.getLogFileDir());
	}

	private static void setSystemProperties() {
		// Override system settings that the user has define in nxt.properties
		// file.
		final String[] systemProperties = new String[] { "socksProxyHost", "socksProxyPort", };

		for (final String propertyName : systemProperties) {
			String propertyValue;
			if ((propertyValue = Nxt.getStringProperty(propertyName)) != null)
                System.setProperty(propertyName, propertyValue);
		}
	}

	private static void setTime(final Time time) {
		Nxt.time = time;
	}

	private static void shutdown() {
		Logger.logShutdownMessage("Shutting down...");
		AddOns.shutdown();
		API.shutdown();
		Users.shutdown();
		ThreadPool.shutdown();
		Peers.shutdown();
		Db.shutdown();
		Logger.logShutdownMessage("Elastic server " + Nxt.VERSION + " stopped.");
		Logger.shutdown();
		Nxt.runtimeMode.shutdown();
	}

	private static void testSecureRandom() {
		final Thread thread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
		thread.setDaemon(true);
		thread.start();
		try {
			thread.join(2000);
			if (thread.isAlive()) throw new RuntimeException("SecureRandom implementation too slow!!! "
                    + "Install haveged if on linux, or set nxt.useStrongSecureRandom=false.");
		} catch (final InterruptedException ignore) {
		}
	}

	public static void updateLogFileHandler(final Properties loggingProperties) {
		Nxt.dirProvider.updateLogFileHandler(loggingProperties);
	}

	private Nxt() {
	} // never

}
