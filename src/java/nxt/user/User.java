/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.user;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Generator;
import nxt.crypto.Crypto;
import nxt.util.JSON;
import nxt.util.Logger;

final class User {

	private final class UserAsyncListener implements AsyncListener {

		@Override
		public void onComplete(final AsyncEvent asyncEvent) throws IOException { }

		@Override
		public void onError(final AsyncEvent asyncEvent) throws IOException {

			synchronized (User.this) {
				User.this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

				try (Writer writer = User.this.asyncContext.getResponse().getWriter()) {
					JSON.emptyJSON.writeJSONString(writer);
				}

				User.this.asyncContext.complete();
				User.this.asyncContext = null;
			}

		}

		@Override
		public void onStartAsync(final AsyncEvent asyncEvent) throws IOException { }

		@Override
		public void onTimeout(final AsyncEvent asyncEvent) throws IOException {

			synchronized (User.this) {
				User.this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

				try (Writer writer = User.this.asyncContext.getResponse().getWriter()) {
					JSON.emptyJSON.writeJSONString(writer);
				}

				User.this.asyncContext.complete();
				User.this.asyncContext = null;
			}

		}

	}
	private volatile String secretPhrase;
	private volatile byte[] publicKey;
	private volatile boolean isInactive;
	private final String userId;
	private final ConcurrentLinkedQueue<JSONStreamAware> pendingResponses = new ConcurrentLinkedQueue<>();

	private AsyncContext asyncContext;

	User(final String userId) {
		this.userId = userId;
	}

	synchronized void enqueue(final JSONStreamAware response) {
		this.pendingResponses.offer(response);
	}

	byte[] getPublicKey() {
		return this.publicKey;
	}

	String getSecretPhrase() {
		return this.secretPhrase;
	}

	String getUserId() {
		return this.userId;
	}

	boolean isInactive() {
		return this.isInactive;
	}

	void lockAccount() {
		Generator.stopForging(this.secretPhrase);
		this.secretPhrase = null;
	}

	synchronized void processPendingResponses(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final JSONArray responses = new JSONArray();
		JSONStreamAware pendingResponse;
		while ((pendingResponse = this.pendingResponses.poll()) != null) {
			responses.add(pendingResponse);
		}
		if (responses.size() > 0) {
			final JSONObject combinedResponse = new JSONObject();
			combinedResponse.put("responses", responses);
			if (this.asyncContext != null) {
				this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
				try (Writer writer = this.asyncContext.getResponse().getWriter()) {
					combinedResponse.writeJSONString(writer);
				}
				this.asyncContext.complete();
				this.asyncContext = req.startAsync();
				this.asyncContext.addListener(new UserAsyncListener());
				this.asyncContext.setTimeout(5000);
			} else {
				resp.setContentType("text/plain; charset=UTF-8");
				try (Writer writer = resp.getWriter()) {
					combinedResponse.writeJSONString(writer);
				}
			}
		} else {
			if (this.asyncContext != null) {
				this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
				try (Writer writer = this.asyncContext.getResponse().getWriter()) {
					JSON.emptyJSON.writeJSONString(writer);
				}
				this.asyncContext.complete();
			}
			this.asyncContext = req.startAsync();
			this.asyncContext.addListener(new UserAsyncListener());
			this.asyncContext.setTimeout(5000);
		}
	}

	synchronized void send(final JSONStreamAware response) {
		if (this.asyncContext == null) {

			if (this.isInactive) {
				// user not seen recently, no responses should be collected
				return;
			}
			if (this.pendingResponses.size() > 1000) {
				this.pendingResponses.clear();
				// stop collecting responses for this user
				this.isInactive = true;
				if (this.secretPhrase == null) {
					// but only completely remove users that don't have unlocked accounts
					Users.remove(this);
				}
				return;
			}

			this.pendingResponses.offer(response);

		} else {

			final JSONArray responses = new JSONArray();
			JSONStreamAware pendingResponse;
			while ((pendingResponse = this.pendingResponses.poll()) != null) {

				responses.add(pendingResponse);

			}
			responses.add(response);

			final JSONObject combinedResponse = new JSONObject();
			combinedResponse.put("responses", responses);

			this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

			try (Writer writer = this.asyncContext.getResponse().getWriter()) {
				combinedResponse.writeJSONString(writer);
			} catch (final IOException e) {
				Logger.logMessage("Error sending response to user", e);
			}

			this.asyncContext.complete();
			this.asyncContext = null;

		}

	}

	void setInactive(final boolean inactive) {
		this.isInactive = inactive;
	}


	long unlockAccount(final String secretPhrase) {
		this.publicKey = Crypto.getPublicKey(secretPhrase);
		this.secretPhrase = secretPhrase;
		return Generator.startForging(secretPhrase).getAccountId();
	}

}
