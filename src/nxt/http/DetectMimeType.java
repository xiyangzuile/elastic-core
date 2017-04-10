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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.Search;

public final class DetectMimeType extends APIServlet.APIRequestHandler {

	static final DetectMimeType instance = new DetectMimeType();

	private DetectMimeType() {
		super("file", new APITag[] { APITag.DATA, APITag.UTILS }, "data", "filename", "isText");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
		String filename = Convert.nullToEmpty(req.getParameter("filename")).trim();
		final String dataValue = Convert.emptyToNull(req.getParameter("data"));
		byte[] data;
		if (dataValue == null) {
			try {
				final Part part = req.getPart("file");
				if (part == null) {
					throw new ParameterException(JSONResponses.INCORRECT_TAGGED_DATA_FILE);
				}
				final ParameterParser.FileData fileData = new ParameterParser.FileData(part).invoke();
				data = fileData.getData();
				// Depending on how the client submits the form, the filename,
				// can be a regular parameter
				// or encoded in the multipart form. If its not a parameter we
				// take from the form
				if (filename.isEmpty() && (fileData.getFilename() != null)) {
					filename = fileData.getFilename();
				}
			} catch (IOException | ServletException e) {
				Logger.logDebugMessage("error in reading file data", e);
				throw new ParameterException(JSONResponses.INCORRECT_TAGGED_DATA_FILE);
			}
		} else {
			final boolean isText = !"false".equalsIgnoreCase(req.getParameter("isText"));
			data = isText ? Convert.toBytes(dataValue) : Convert.parseHexString(dataValue);
		}

		final JSONObject response = new JSONObject();
		response.put("type", Search.detectMimeType(data, filename));
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	@Override
	protected boolean requirePost() {
		return true;
	}

}
