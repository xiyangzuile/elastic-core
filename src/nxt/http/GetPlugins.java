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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public final class GetPlugins extends APIServlet.APIRequestHandler {

	private static class PluginDirListing extends SimpleFileVisitor<Path> {

		private final List<Path> directories = new ArrayList<>();

		public List<Path> getDirectories() {
			return this.directories;
		}

		@Override
		public FileVisitResult postVisitDirectory(final Path dir, final IOException e) {
			if (!Objects.equals(GetPlugins.PLUGINS_HOME, dir)) this.directories.add(dir);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(final Path file, final BasicFileAttributes attr) {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(final Path file, final IOException e) {
			return FileVisitResult.CONTINUE;
		}
	}

	static final GetPlugins instance = new GetPlugins();

	private static final Path PLUGINS_HOME = Paths.get("./html/ui/plugins");

	private GetPlugins() {
		super(new APITag[] { APITag.INFO });
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final JSONObject response = new JSONObject();
		if (!Files.isReadable(GetPlugins.PLUGINS_HOME))
            return JSONResponses.fileNotFound(GetPlugins.PLUGINS_HOME.toString());
		final PluginDirListing pluginDirListing = new PluginDirListing();
		try {
			Files.walkFileTree(GetPlugins.PLUGINS_HOME, EnumSet.noneOf(FileVisitOption.class), 2, pluginDirListing);
		} catch (final IOException e) {
			return JSONResponses.fileNotFound(e.getMessage());
		}
		final JSONArray plugins = new JSONArray();
		pluginDirListing.getDirectories()
				.forEach(dir -> plugins.add(Paths.get(dir.toString()).getFileName().toString()));
		response.put("plugins", plugins);
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}
