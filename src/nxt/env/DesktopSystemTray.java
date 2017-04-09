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

package nxt.env;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import nxt.Block;
import nxt.Constants;
import nxt.Db;
import nxt.Generator;
import nxt.Nxt;
import nxt.http.API;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.Logger;

public class DesktopSystemTray {

	public static final int DELAY = 1000;

	public static String humanReadableByteCount(final long bytes) {
		final int unit = 1000;
		if (bytes < unit) {
			return bytes + " B";
		}
		final int exp = (int) (Math.log(bytes) / Math.log(unit));
		final String pre = "" + ("KMGTPE").charAt(exp - 1);
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	private SystemTray tray;
	private final JFrame wrapper = new JFrame();
	private JDialog statusDialog;
	private JPanel statusPanel;
	private ImageIcon imageIcon;
	private TrayIcon trayIcon;
	private MenuItem openWalletInBrowser;
	private MenuItem viewLog;
	private SystemTrayDataProvider dataProvider;

	private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM,
			Locale.getDefault());

	private void addDataRow(final JPanel parent, String text, final String value) {
		final JPanel rowPanel = new JPanel();
		if (!"".equals(value)) {
			rowPanel.add(Box.createRigidArea(new Dimension(20, 0)));
		}
		rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
		if (!"".equals(text) && !"".equals(value)) {
			text += ':';
		}
		final JLabel textLabel = new JLabel(text);
		// textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
		rowPanel.add(textLabel);
		rowPanel.add(Box.createRigidArea(new Dimension(140 - textLabel.getPreferredSize().width, 0)));
		final JTextField valueField = new JTextField(value);
		valueField.setEditable(false);
		valueField.setBorder(BorderFactory.createEmptyBorder());
		rowPanel.add(valueField);
		rowPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		parent.add(rowPanel);
		parent.add(Box.createRigidArea(new Dimension(0, 4)));
	}

	private void addEmptyRow(final JPanel parent) {
		this.addLabelRow(parent, "");
	}

	private void addLabelRow(final JPanel parent, final String text) {
		this.addDataRow(parent, text, "");
	}

	void alert(final String message) {
		JOptionPane.showMessageDialog(null, message, "Initialization Error", JOptionPane.ERROR_MESSAGE);
	}

	void createAndShowGUI() {
		if (!SystemTray.isSupported()) {
			Logger.logInfoMessage("SystemTray is not supported");
			return;
		}
		final PopupMenu popup = new PopupMenu();
		this.imageIcon = new ImageIcon("html/ui/img/nxt-icon-32x32.png", "tray icon");
		this.trayIcon = new TrayIcon(this.imageIcon.getImage());
		this.trayIcon.setImageAutoSize(true);
		this.tray = SystemTray.getSystemTray();

		final MenuItem shutdown = new MenuItem("Shutdown");
		this.openWalletInBrowser = new MenuItem("Open Wallet in Browser");
		if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			this.openWalletInBrowser.setEnabled(false);
		}
		final MenuItem showDesktopApplication = new MenuItem("Show Desktop Application");
		final MenuItem refreshDesktopApplication = new MenuItem("Refresh Wallet");
		if (!Nxt.isDesktopApplicationEnabled()) {
			showDesktopApplication.setEnabled(false);
			refreshDesktopApplication.setEnabled(false);
		}
		this.viewLog = new MenuItem("View Log File");
		if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			this.viewLog.setEnabled(false);
		}
		final MenuItem status = new MenuItem("Status");

		popup.add(status);
		popup.add(this.viewLog);
		popup.addSeparator();
		popup.add(this.openWalletInBrowser);
		popup.add(showDesktopApplication);
		popup.add(refreshDesktopApplication);
		popup.addSeparator();
		popup.add(shutdown);
		this.trayIcon.setPopupMenu(popup);
		this.trayIcon.setToolTip("Initializing");
		try {
			this.tray.add(this.trayIcon);
		} catch (final AWTException e) {
			Logger.logInfoMessage("TrayIcon could not be added", e);
			return;
		}

		this.trayIcon.addActionListener(e -> this.displayStatus());

		this.openWalletInBrowser.addActionListener(e -> {
			try {
				Desktop.getDesktop().browse(this.dataProvider.getWallet());
			} catch (final IOException ex) {
				Logger.logInfoMessage("Cannot open wallet in browser", ex);
			}
		});

		showDesktopApplication.addActionListener(e -> {
			try {
				Class.forName("nxtdesktop.DesktopApplication").getMethod("launch").invoke(null);
			} catch (final ReflectiveOperationException exception) {
				Logger.logInfoMessage("nxtdesktop.DesktopApplication failed to launch", exception);
			}
		});

		refreshDesktopApplication.addActionListener(e -> {
			try {
				Class.forName("nxtdesktop.DesktopApplication").getMethod("refresh").invoke(null);
			} catch (final ReflectiveOperationException exception) {
				Logger.logInfoMessage("nxtdesktop.DesktopApplication failed to refresh", exception);
			}
		});

		this.viewLog.addActionListener(e -> {
			try {
				Desktop.getDesktop().open(this.dataProvider.getLogFile());
			} catch (final IOException ex) {
				Logger.logInfoMessage("Cannot view log", ex);
			}
		});

		status.addActionListener(e -> this.displayStatus());

		shutdown.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(null,
					"Sure you want to shutdown XEL?\n\nIf you do, this will stop forging, shufflers and account monitors.\n\n",
					"Shutdown", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				Logger.logInfoMessage("Shutdown requested by System Tray");
				System.exit(0); // Implicitly invokes shutdown using the
								// shutdown hook
			}
		});

		final ActionListener statusUpdater = evt -> {
			if ((this.statusDialog == null) || !this.statusDialog.isVisible()) {
				return;
			}
			this.displayStatus();
		};
		new Timer(DesktopSystemTray.DELAY, statusUpdater).start();
	}

	private void displayStatus() {
		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		final Collection<Generator> allGenerators = Generator.getAllGenerators();

		final StringBuilder generators = new StringBuilder();
		for (final Generator generator : allGenerators) {
			generators.append(Convert.rsAccount(generator.getAccountId())).append(' ');
		}
		final Object optionPaneBackground = UIManager.get("OptionPane.background");
		UIManager.put("OptionPane.background", Color.WHITE);
		final Object panelBackground = UIManager.get("Panel.background");
		UIManager.put("Panel.background", Color.WHITE);
		final Object textFieldBackground = UIManager.get("TextField.background");
		UIManager.put("TextField.background", Color.WHITE);
		Container statusPanelParent = null;
		if ((this.statusDialog != null) && (this.statusPanel != null)) {
			statusPanelParent = this.statusPanel.getParent();
			statusPanelParent.remove(this.statusPanel);
		}
		this.statusPanel = new JPanel();
		this.statusPanel.setLayout(new BoxLayout(this.statusPanel, BoxLayout.Y_AXIS));

		this.addLabelRow(this.statusPanel, "Installation");
		this.addDataRow(this.statusPanel, "Application", Nxt.APPLICATION);
		this.addDataRow(this.statusPanel, "Version", Nxt.VERSION);
		this.addDataRow(this.statusPanel, "Network", (Constants.isTestnet) ? "TestNet" : "MainNet");
		this.addDataRow(this.statusPanel, "Working offline", "" + Constants.isOffline);
		this.addDataRow(this.statusPanel, "Wallet", String.valueOf(API.getWelcomePageUri()));
		this.addDataRow(this.statusPanel, "Peer port", String.valueOf(Peers.getDefaultPeerPort()));
		this.addDataRow(this.statusPanel, "Program folder",
				String.valueOf(Paths.get(".").toAbsolutePath().getParent()));
		this.addDataRow(this.statusPanel, "User folder",
				String.valueOf(Paths.get(Nxt.getUserHomeDir()).toAbsolutePath()));
		this.addDataRow(this.statusPanel, "Database URL", Db.db == null ? "unavailable" : Db.db.getUrl());
		this.addEmptyRow(this.statusPanel);

		if (lastBlock != null) {
			this.addLabelRow(this.statusPanel, "Last Block");
			this.addDataRow(this.statusPanel, "Height", String.valueOf(lastBlock.getHeight()));
			this.addDataRow(this.statusPanel, "Timestamp", String.valueOf(lastBlock.getTimestamp()));
			this.addDataRow(this.statusPanel, "Time",
					String.valueOf(new Date(Convert.fromEpochTime(lastBlock.getTimestamp()))));
			this.addDataRow(this.statusPanel, "Seconds passed",
					String.valueOf(Nxt.getEpochTime() - lastBlock.getTimestamp()));
			this.addDataRow(this.statusPanel, "Forging", String.valueOf(allGenerators.size() > 0));
			if (allGenerators.size() > 0) {
				this.addDataRow(this.statusPanel, "Forging accounts", generators.toString());
			}
		}

		this.addEmptyRow(this.statusPanel);
		this.addLabelRow(this.statusPanel, "Environment");
		this.addDataRow(this.statusPanel, "Number of peers", String.valueOf(Peers.getAllPeers().size()));
		this.addDataRow(this.statusPanel, "Available processors",
				String.valueOf(Runtime.getRuntime().availableProcessors()));
		this.addDataRow(this.statusPanel, "Max memory",
				DesktopSystemTray.humanReadableByteCount(Runtime.getRuntime().maxMemory()));
		this.addDataRow(this.statusPanel, "Total memory",
				DesktopSystemTray.humanReadableByteCount(Runtime.getRuntime().totalMemory()));
		this.addDataRow(this.statusPanel, "Free memory",
				DesktopSystemTray.humanReadableByteCount(Runtime.getRuntime().freeMemory()));
		this.addDataRow(this.statusPanel, "Process id", Nxt.getProcessId());
		this.addEmptyRow(this.statusPanel);
		this.addDataRow(this.statusPanel, "Updated", this.dateFormat.format(new Date()));
		if ((this.statusDialog == null) || !this.statusDialog.isVisible()) {
			final JOptionPane pane = new JOptionPane(this.statusPanel, JOptionPane.PLAIN_MESSAGE,
					JOptionPane.DEFAULT_OPTION, this.imageIcon);
			this.statusDialog = pane.createDialog(this.wrapper, "XEL Server Status");
			this.statusDialog.setVisible(true);
			this.statusDialog.dispose();
		} else {
			if (statusPanelParent != null) {
				statusPanelParent.add(this.statusPanel);
				statusPanelParent.revalidate();
			}
			this.statusDialog.getContentPane().validate();
			this.statusDialog.getContentPane().repaint();
			EventQueue.invokeLater(this.statusDialog::toFront);
		}
		UIManager.put("OptionPane.background", optionPaneBackground);
		UIManager.put("Panel.background", panelBackground);
		UIManager.put("TextField.background", textFieldBackground);
	}

	void setToolTip(final SystemTrayDataProvider dataProvider) {
		SwingUtilities.invokeLater(() -> {
			this.trayIcon.setToolTip(dataProvider.getToolTip());
			this.openWalletInBrowser.setEnabled(
					(dataProvider.getWallet() != null) && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE));
			this.viewLog.setEnabled(dataProvider.getWallet() != null);
			DesktopSystemTray.this.dataProvider = dataProvider;
		});
	}

	void shutdown() {
		SwingUtilities.invokeLater(() -> this.tray.remove(this.trayIcon));
	}
}
