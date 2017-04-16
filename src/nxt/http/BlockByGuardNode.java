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

package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class BlockByGuardNode extends CreateTransaction {

	static final BlockByGuardNode instance = new BlockByGuardNode();

	private BlockByGuardNode() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.CREATE_TRANSACTION }, "name", "description");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final String accstr = Convert.nullToEmpty(req.getParameter("shenanigan")).trim();
		try {
			long acc = Long.parseLong(accstr);
			final Account account = ParameterParser.getSenderAccount(req);
			final Account shenanigan = Account.getAccount(acc);
			if (account == null) return JSONResponses.INCORRECT_ACCOUNT;
			final Attachment attachment = new Attachment.MessagingSupernodeAnnouncement(new String[] {}, shenanigan.getId());
			return this.createTransaction(req, account, attachment);
		}
		catch(Exception e){
			return JSONResponses.INCORRECT_ACCOUNT;
		}

	}

}
