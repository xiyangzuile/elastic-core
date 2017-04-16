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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.util.Convert;

public final class SetAccountInfo extends CreateTransaction {

	static final SetAccountInfo instance = new SetAccountInfo();

	private SetAccountInfo() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.CREATE_TRANSACTION }, "name", "description");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final String name = Convert.nullToEmpty(req.getParameter("name")).trim();
		final String description = Convert.nullToEmpty(req.getParameter("description")).trim();

		if (name.length() > Constants.MAX_ACCOUNT_NAME_LENGTH) return JSONResponses.INCORRECT_ACCOUNT_NAME_LENGTH;

		if (description.length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH)
            return JSONResponses.INCORRECT_ACCOUNT_DESCRIPTION_LENGTH;

		final Account account = ParameterParser.getSenderAccount(req);
		final Attachment attachment = new Attachment.MessagingAccountInfo(name, description);
		return this.createTransaction(req, account, attachment);

	}

}
