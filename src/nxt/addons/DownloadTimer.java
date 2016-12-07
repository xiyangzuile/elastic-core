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

package nxt.addons;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Nxt;
import nxt.util.Listener;
import nxt.util.Logger;

public final class DownloadTimer implements AddOn {

	private PrintWriter writer = null;

	@Override
	public void init() {

		try {

			this.writer = new PrintWriter(
					(new BufferedWriter(new OutputStreamWriter(new FileOutputStream("downloadtime.csv")))), true);
			this.writer.println("height,time,dtime,bps,transations,dtransactions,tps");
			Nxt.getBlockchainProcessor().addListener(new Listener<Block>() {

				final int interval = 10000;
				final long startTime = System.currentTimeMillis();
				long previousTime = 0;
				long transactions = 0;
				long dtransactions = 0;

				@Override
				public void notify(final Block block) {
					final int n = block.getTransactions().size();
					this.transactions += n;
					this.dtransactions += n;
					final int height = block.getHeight();
					if ((height % this.interval) == 0) {
						final long time = System.currentTimeMillis() - this.startTime;
						DownloadTimer.this.writer.print(height);
						DownloadTimer.this.writer.print(',');
						DownloadTimer.this.writer.print(time / 1000);
						DownloadTimer.this.writer.print(',');
						final long dtime = (time - this.previousTime) / 1000;
						DownloadTimer.this.writer.print(dtime);
						DownloadTimer.this.writer.print(',');
						DownloadTimer.this.writer.print(this.interval / dtime);
						DownloadTimer.this.writer.print(',');
						DownloadTimer.this.writer.print(this.transactions);
						DownloadTimer.this.writer.print(',');
						DownloadTimer.this.writer.print(this.dtransactions);
						DownloadTimer.this.writer.print(',');
						final long tps = this.dtransactions / dtime;
						DownloadTimer.this.writer.println(tps);
						this.previousTime = time;
						this.dtransactions = 0;
					}
				}

			}, BlockchainProcessor.Event.BLOCK_PUSHED);

		} catch (final IOException e) {
			Logger.logErrorMessage(e.getMessage(), e);
		}

	}

	@Override
	public void shutdown() {
		if (this.writer != null) {
			this.writer.flush();
			this.writer.close();
		}
	}

}
