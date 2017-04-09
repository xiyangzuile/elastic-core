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

package nxt.peer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nxt.Db;

final class PeerDb {

	static class Entry {
		private final String address;
		private final long services;
		private final int lastUpdated;

		Entry(final String address, final long services, final int lastUpdated) {
			this.address = address;
			this.services = services;
			this.lastUpdated = lastUpdated;
		}

		@Override
		public boolean equals(final Object obj) {
			return ((obj != null) && (obj instanceof Entry) && this.address.equals(((Entry) obj).address));
		}

		public String getAddress() {
			return this.address;
		}

		public int getLastUpdated() {
			return this.lastUpdated;
		}

		public long getServices() {
			return this.services;
		}

		@Override
		public int hashCode() {
			return this.address.hashCode();
		}
	}

	static void deletePeers(final Collection<Entry> peers) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("DELETE FROM peer WHERE address = ?")) {
			for (final Entry peer : peers) {
				pstmt.setString(1, peer.getAddress());
				pstmt.executeUpdate();
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static List<Entry> loadPeers() {
		final List<Entry> peers = new ArrayList<>();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM peer");
				ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) {
				peers.add(new Entry(rs.getString("address"), rs.getLong("services"), rs.getInt("last_updated")));
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return peers;
	}

	static void updatePeer(final PeerImpl peer) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"MERGE INTO peer " + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
			pstmt.setString(1, peer.getAnnouncedAddress());
			pstmt.setLong(2, peer.getServices());
			pstmt.setInt(3, peer.getLastUpdated());
			pstmt.executeUpdate();
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static void updatePeers(final Collection<Entry> peers) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"MERGE INTO peer " + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
			for (final Entry peer : peers) {
				pstmt.setString(1, peer.getAddress());
				pstmt.setLong(2, peer.getServices());
				pstmt.setInt(3, peer.getLastUpdated());
				pstmt.executeUpdate();
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
}
