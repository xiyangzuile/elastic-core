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

package nxt.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public final class JSON {

	public final static JSONStreamAware emptyJSON = JSON.prepare(new JSONObject());

	/** String escape pattern */
	private static final Pattern pattern = Pattern
			.compile("[\"\\\\\\u0008\\f\\n\\r\\t/\\u0000-\\u001f\\u007f-\\u009f\\u2000-\\u20ff\\ud800-\\udbff]");

	/**
	 * Create a formatted string from a list
	 *
	 * @param list
	 *            List
	 * @param sb
	 *            String builder
	 */
	private static void encodeArray(final List<?> list, final StringBuilder sb) {
		if (list == null) {
			sb.append("null");
			return;
		}
		boolean firstElement = true;
		sb.append('[');
		for (final Object obj : list) {
			if (firstElement) firstElement = false;
			else sb.append(',');
			JSON.encodeValue(obj, sb);
		}
		sb.append(']');
	}

	/**
	 * Create a formatted string from a map
	 *
	 * @param map
	 *            Map
	 * @param sb
	 *            String builder
	 */
	private static void encodeObject(final Map<?, ?> map, final StringBuilder sb) {
		if (map == null) {
			sb.append("null");
			return;
		}
		final Set<Map.Entry<Object, Object>> entries = (Set) map.entrySet();
		final Iterator<Map.Entry<Object, Object>> it = entries.iterator();
		boolean firstElement = true;
		sb.append('{');
		while (it.hasNext()) {
			final Map.Entry<Object, Object> entry = it.next();
			final Object key = entry.getKey();
			final Object value = entry.getValue();
			if (key == null) continue;
			if (firstElement) firstElement = false;
			else sb.append(',');
			sb.append('\"').append(key.toString()).append("\":");
			JSON.encodeValue(value, sb);
		}
		sb.append('}');
	}

	/**
	 * Encode a JSON value
	 *
	 * @param value
	 *            JSON value
	 * @param sb
	 *            String builder
	 */
	private static void encodeValue(final Object value, final StringBuilder sb) {
		if (value == null) sb.append("null");
		else if (value instanceof Double)
			if (((Double) value).isInfinite() || ((Double) value).isNaN()) sb.append("null");
			else sb.append(value.toString());
		else if (value instanceof Float)
			if (((Float) value).isInfinite() || ((Float) value).isNaN()) sb.append("null");
			else sb.append(value.toString());
		else if (value instanceof Number) sb.append(value.toString());
		else if (value instanceof Boolean) sb.append(value.toString());
		else if (value instanceof Map) JSON.encodeObject((Map<Object, Object>) value, sb);
		else if (value instanceof List) JSON.encodeArray((List<Object>) value, sb);
		else {
			sb.append('\"');
			JSON.escapeString(value.toString(), sb);
			sb.append('\"');
		}
	}

	/**
	 * Escape control characters in a string and append them to the string
	 * buffer
	 *
	 * @param string
	 *            String to be written
	 * @param sb
	 *            String builder
	 */
	private static void escapeString(final String string, final StringBuilder sb) {
		if (string.length() == 0) return;
		//
		// Find the next special character in the string
		//
		int start = 0;
		final Matcher matcher = JSON.pattern.matcher(string);
		while (matcher.find(start)) {
			final int pos = matcher.start();
			if (pos > start) sb.append(string.substring(start, pos));
			start = pos + 1;
			//
			// Escape control characters
			//
			final char c = string.charAt(pos);
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '/':
				sb.append("\\/");
				break;
			default:
				//noinspection ConstantConditions,ConstantConditions
				if (((c >= '\u0000') && (c <= '\u001F')) || ((c >= '\u007F') && (c <= '\u009F'))
						|| ((c >= '\u2000') && (c <= '\u20FF')))
					sb.append("\\u").append(String.format("%04X", (int) c));
				else sb.append(c);
			}
		}
		//
		// Append the remainder of the string
		//
		if (start == 0) sb.append(string);
		else if (start < string.length()) sb.append(string.substring(start));
	}

	public static JSONStreamAware prepare(final JSONObject json) {
		return new JSONStreamAware() {
			private final char[] jsonChars = JSON.toJSONString(json).toCharArray();

			@Override
			public void writeJSONString(final Writer out) throws IOException {
				out.write(this.jsonChars);
			}
		};
	}

	public static JSONStreamAware prepareRequest(final JSONObject json) {
		json.put("protocol", 1);
		return JSON.prepare(json);
	}

	/**
	 * Create a formatted JSON string
	 *
	 * @param json
	 *            JSON list or map
	 * @return Formatted string
	 */
	private static String toJSONString(final JSONAware json) {
		if (json == null) return "null";
		if (json instanceof Map) {
			final StringBuilder sb = new StringBuilder(1024);
			JSON.encodeObject((Map) json, sb);
			return sb.toString();
		}
		if (json instanceof List) {
			final StringBuilder sb = new StringBuilder(1024);
			JSON.encodeArray((List) json, sb);
			return sb.toString();
		}
		return json.toJSONString();
	}

	public static String toString(final JSONStreamAware jsonStreamAware) {
		final StringWriter stringWriter = new StringWriter();
		try {
			JSON.writeJSONString(jsonStreamAware, stringWriter);
		} catch (final IOException ignore) {
		}
		return stringWriter.toString();
	}

	/**
	 * Write a formatted JSON string
	 *
	 * @param json
	 *            JSON list or map
	 * @param writer
	 *            Writer
	 * @throws IOException
	 *             I/O error occurred
	 */
	public static void writeJSONString(final JSONStreamAware json, final Writer writer) throws IOException {
		if (json == null) {
			writer.write("null");
			return;
		}
		if (json instanceof Map) {
			final StringBuilder sb = new StringBuilder(1024);
			JSON.encodeObject((Map) json, sb);
			writer.write(sb.toString());
			return;
		}
		if (json instanceof List) {
			final StringBuilder sb = new StringBuilder(1024);
			JSON.encodeArray((List) json, sb);
			writer.write(sb.toString());
			return;
		}
		json.writeJSONString(writer);
	}

	private JSON() {
	} // never
}
