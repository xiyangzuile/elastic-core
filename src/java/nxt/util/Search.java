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

package nxt.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.tika.Tika;

public final class Search {

	private static final Analyzer analyzer = new StandardAnalyzer();

	public static String detectMimeType(final byte[] data) {
		final Tika tika = new Tika();
		return tika.detect(data);
	}

	public static String detectMimeType(final byte[] data, final String filename) {
		final Tika tika = new Tika();
		return tika.detect(data, filename);
	}

	public static String[] parseTags(final String tags, final int minTagLength, final int maxTagLength, final int maxTagCount) {
		if (tags.trim().length() == 0) {
			return Convert.EMPTY_STRING;
		}
		final List<String> list = new ArrayList<>();
		try (TokenStream stream = Search.analyzer.tokenStream(null, tags)) {
			final CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
			String tag;
			stream.reset();
			while (stream.incrementToken() && (list.size() < maxTagCount) &&
					((tag = attribute.toString()).length() <= maxTagLength) && (tag.length() >= minTagLength)) {
				if (!list.contains(tag)) {
					list.add(tag);
				}
			}
			stream.end();
		} catch (final IOException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return list.toArray(new String[list.size()]);
	}

	private Search() {}

}
