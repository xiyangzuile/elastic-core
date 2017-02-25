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

package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class BecomeSupernode extends CreateTransaction {

	static final BecomeSupernode instance = new BecomeSupernode();

	private BecomeSupernode() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.CREATE_TRANSACTION }, "name", "description");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final String uris = Convert.nullToEmpty(req.getParameter("uris")).trim();
		String[] uri_filtered = uris.split(";"); // TODO, maybe expect a collection here
		final Account account = ParameterParser.getSenderAccount(req);
		final Attachment attachment = new Attachment.MessagingSupernodeAnnouncement(uri_filtered, 0);
		return this.createTransaction(req, account, attachment);

	}

}
