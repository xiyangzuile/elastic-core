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

import org.json.simple.JSONStreamAware;

import nxt.NxtException;

public final class ParameterException extends NxtException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 411553011935598645L;
	private final JSONStreamAware errorResponse;

	ParameterException(final JSONStreamAware errorResponse) {
		this.errorResponse = errorResponse;
	}

	JSONStreamAware getErrorResponse() {
		return this.errorResponse;
	}

}
